package com.qita.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

enum class DlState { RUNNING, PAUSED, DONE, FAILED }

/** What to do with a file when it has finished: [APK] offers to install it, [FILE] puts it with the games if it is one. */
enum class DlKind { FILE, APK }

/** What the user chose before the download began: unzip it, which game folder it goes to (null: leave it in Downloads), and what to do with the zip. */
class DownloadPlan(val unzip: Boolean, val folderUri: String?, val deleteZip: Boolean)

/** A download waiting for the user's answers; [onItem] gets the download once it has started. */
class DownloadRequest(
    val url: String,
    val name: String,
    val kind: DlKind,
    val cookie: String?,
    val userAgent: String?,
    val referer: String?,
    val onItem: (DownloadItem) -> Unit,
)

/** The user's standing choices about downloads, and the last answers (used when the questions are switched off). */
object DownloadPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("qita_downloads", Context.MODE_PRIVATE)
    fun ask(c: Context): Boolean = prefs(c).getBoolean("ask", true)
    fun setAsk(c: Context, on: Boolean) { prefs(c).edit().putBoolean("ask", on).apply() }

    fun remember(c: Context, plan: DownloadPlan, wasZip: Boolean) {
        prefs(c).edit().apply {
            if (wasZip) { putBoolean("last_unzip", plan.unzip); putBoolean("last_delzip", plan.deleteZip) }
            putString("last_folder", plan.folderUri ?: "")
        }.apply()
    }

    fun lastUnzip(c: Context) = prefs(c).getBoolean("last_unzip", true)
    fun lastDeleteZip(c: Context) = prefs(c).getBoolean("last_delzip", true)
    fun lastFolder(c: Context): String? = prefs(c).getString("last_folder", null)?.ifEmpty { null }

    /** The answers to use without asking: the last ones, or null if there are none yet (the old ask-afterwards menus then apply). */
    fun quietPlan(c: Context): DownloadPlan? {
        val folder = lastFolder(c) ?: return null
        return DownloadPlan(lastUnzip(c), folder, lastDeleteZip(c))
    }
}

/** One download. The fields that change while it runs are Compose state, so the lists showing it update by themselves. */
class DownloadItem(
    val id: String,
    val url: String,
    val name: String,
    val kind: DlKind,
    val cookie: String?,
    val userAgent: String?,
    val startedAt: Long,
    val partFile: File,
) {
    var state by mutableStateOf(DlState.RUNNING)
    var bytes by mutableStateOf(0L)
    var total by mutableStateOf(-1L)
    var error by mutableStateOf<String?>(null)
    /** Bytes per second, smoothed. */
    var speed by mutableStateOf(0f)
    var finalPath by mutableStateOf<String?>(null)
    internal var job: Job? = null
    /** The user's answers, if they were asked before it began. */
    var plan: DownloadPlan? = null
    /** The page the download was started from; some servers refuse a request without it. */
    var referer: String? = null

    val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else -1f

    /** "7 Minutes Left (2998 MB / 3402 MB)" while running, "Paused", "Failed: ..." or "Done", as in the Vita's download list. */
    fun status(): String = when (state) {
        DlState.PAUSED -> "Paused" + if (total > 0) "  (${mb(bytes)} / ${mb(total)})" else ""
        DlState.FAILED -> "Failed: ${error ?: "unknown error"}"
        DlState.DONE -> "Done  (${mb(bytes)})"
        DlState.RUNNING -> {
            val left = if (speed > 1f && total > 0) ((total - bytes) / speed).toLong() else -1L
            val eta = when {
                left < 0 -> "Downloading"
                left < 60 -> "$left Second${if (left == 1L) "" else "s"} Left"
                left < 3600 -> "${left / 60} Minute${if (left / 60 == 1L) "" else "s"} Left"
                else -> "${left / 3600} Hour${if (left / 3600 == 1L) "" else "s"} Left"
            }
            eta + if (total > 0) "  (${mb(bytes)} / ${mb(total)})" else "  (${mb(bytes)})"
        }
    }

    private fun mb(n: Long): String = if (n >= 10L * 1024 * 1024 * 1024 / 10) "%.1f GB".format(n / 1073741824.0) else "${n / 1048576} MB"
}

/**
 * Downloads files in the app. Resumable (HTTP ranges), so pausing keeps what was fetched. Runs inside the launcher's own process:
 * it carries on while the launcher is in the background, but Android can stop it if it needs the memory, and then a download
 * comes back as paused the next time.
 */
object DownloadEngine {
    val items = mutableStateListOf<DownloadItem>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var app: Context? = null

    /** Called on the main thread when a download has finished. */
    var onFinished: (DownloadItem) -> Unit = {}

    private fun prefs() = app!!.getSharedPreferences("qita_downloads", Context.MODE_PRIVATE)
    fun dir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        runCatching {
            val arr = JSONArray(prefs().getString("items", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val item = DownloadItem(
                    o.getString("id"), o.getString("url"), o.getString("name"),
                    if (o.optString("kind") == "APK") DlKind.APK else DlKind.FILE,
                    o.optString("cookie").ifEmpty { null }, o.optString("ua").ifEmpty { null },
                    o.optLong("started"), File(dir(app!!), o.getString("id") + ".part"),
                )
                item.referer = o.optString("ref").ifEmpty { null }
                if (o.has("plan_folder") || o.has("plan_unzip")) item.plan = DownloadPlan(o.optBoolean("plan_unzip"), o.optString("plan_folder").ifEmpty { null }, o.optBoolean("plan_del", true))
                item.total = o.optLong("total", -1)
                item.finalPath = o.optString("final").ifEmpty { null }
                val done = o.optString("state") == "DONE" && item.finalPath != null && File(item.finalPath!!).exists()
                item.state = if (done) DlState.DONE else if (o.optString("state") == "FAILED") DlState.FAILED else DlState.PAUSED
                item.bytes = if (done) File(item.finalPath!!).length() else item.partFile.length()
                if (item.state == DlState.FAILED) item.error = o.optString("error").ifEmpty { "failed" }
                if (done || item.state == DlState.PAUSED || item.state == DlState.FAILED) items.add(item)
            }
        }
    }

    private fun persist() {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("url", it.url).put("name", it.name).put("kind", it.kind.name)
                    .put("cookie", it.cookie ?: "").put("ua", it.userAgent ?: "").put("started", it.startedAt)
                    .put("total", it.total).put("final", it.finalPath ?: "").put("state", it.state.name).put("error", it.error ?: "").put("ref", it.referer ?: "")
                    .also { o -> it.plan?.let { p -> o.put("plan_unzip", p.unzip).put("plan_folder", p.folderUri ?: "").put("plan_del", p.deleteZip) } },
            )
        }
        prefs().edit().putString("items", arr.toString()).apply()
    }

    /** Starts a download and returns it. [name] is cleaned of characters a file name cannot hold. */
    fun enqueue(url: String, name: String, kind: DlKind = DlKind.FILE, cookie: String? = null, userAgent: String? = null, plan: DownloadPlan? = null, referer: String? = null): DownloadItem {
        val context = app ?: error("DownloadEngine.init was not called")
        val id = java.lang.Long.toHexString(System.nanoTime())
        val item = DownloadItem(id, url, cleanName(name), kind, cookie, userAgent, System.currentTimeMillis(), File(dir(context), "$id.part"))
        item.plan = plan
        item.referer = referer
        items.add(0, item)
        persist()
        start(item)
        return item
    }

    /** Shows the questions (set by the home screen, which can draw them over the Store); null means there is nobody to ask. */
    var asker: ((DownloadRequest) -> Unit)? = null

    /**
     * The way the Store starts a download. APKs go straight to the installer's route. Anything else first asks the user (unzip?
     * which folder? keep the zip?), unless they switched the questions off, when their last answers are used.
     */
    fun request(url: String, name: String, kind: DlKind = DlKind.FILE, cookie: String? = null, userAgent: String? = null, referer: String? = null, onItem: (DownloadItem) -> Unit = {}) {
        val context = app ?: error("DownloadEngine.init was not called")
        val apk = kind == DlKind.APK || name.endsWith(".apk", true)
        val ask = asker
        if (apk || ask == null || !DownloadPrefs.ask(context)) {
            onItem(enqueue(url, name, kind, cookie, userAgent, if (apk) null else DownloadPrefs.quietPlan(context), referer))
        } else {
            ask(DownloadRequest(url, name, kind, cookie, userAgent, referer, onItem))
        }
    }

    private fun cleanName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "download" }

    private fun start(item: DownloadItem) {
        item.state = DlState.RUNNING
        item.error = null
        item.job = scope.launch { run(item) }
    }

    fun pause(item: DownloadItem) {
        item.job?.cancel()
        if (item.state == DlState.RUNNING) item.state = DlState.PAUSED
        persist()
    }

    fun resume(item: DownloadItem) { if (item.state != DlState.RUNNING && item.state != DlState.DONE) start(item) }

    /** Stops the download and throws away what was fetched. */
    fun cancel(item: DownloadItem) {
        item.job?.cancel()
        item.partFile.delete()
        items.remove(item)
        persist()
    }

    /** Takes a finished download off the list (the file stays). */
    fun remove(item: DownloadItem) {
        items.remove(item)
        persist()
    }

    private suspend fun run(item: DownloadItem) {
        try {
            var url = fixUrl(item.url)
            var redirects = 0
            var existing = item.partFile.length()
            var useCookie = true
            var useRange = true
            while (true) {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                // A browser's headers: servers turn away the plain "Java" identity and requests with no Accept.
                conn.setRequestProperty("User-Agent", item.userAgent ?: BROWSER_UA)
                conn.setRequestProperty("Accept", "*/*")
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                (item.referer ?: originOf(item.url))?.let { conn.setRequestProperty("Referer", it) }
                if (useCookie) item.cookie?.let { conn.setRequestProperty("Cookie", it) }
                if (existing > 0 && useRange) conn.setRequestProperty("Range", "bytes=$existing-")
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location") ?: throw IOException("Bad redirect")
                    conn.disconnect()
                    url = fixUrl(URL(URL(url), next).toString())
                    if (++redirects > 6) throw IOException("Too many redirects")
                    continue
                }
                // A refused request is tried again without the parts that most often cause it: a stale partial file's Range,
                // then the cookie.
                if ((code == 400 || code == 416) && existing > 0 && useRange) {
                    conn.disconnect(); useRange = false; item.partFile.delete(); existing = 0; continue
                }
                if (code == 400 && useCookie && item.cookie != null) { conn.disconnect(); useCookie = false; continue }
                if (code != 200 && code != 206) {
                    val note = runCatching { conn.responseMessage }.getOrNull().orEmpty()
                    val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText().take(400) } }.getOrNull().orEmpty()
                        .replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(90)
                    conn.disconnect()
                    throw IOException("Server said $code" + (if (note.isNotBlank()) " ($note)" else "") + (if (body.isNotBlank()) ": $body" else ""))
                }
                val resumed = code == 206 && existing > 0
                val length = conn.contentLengthLong
                item.total = if (length >= 0) length + (if (resumed) existing else 0L) else -1L
                if (!resumed) item.partFile.delete()
                var done = if (resumed) existing else 0L
                item.bytes = done
                var tickTime = System.currentTimeMillis()
                var tickBytes = done
                conn.inputStream.use { input ->
                    FileOutputStream(item.partFile, resumed).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - tickTime >= 400) {
                                val rate = (done - tickBytes) * 1000f / (now - tickTime)
                                item.speed = if (item.speed == 0f) rate else item.speed * 0.6f + rate * 0.4f
                                item.bytes = done
                                tickTime = now
                                tickBytes = done
                            }
                        }
                    }
                }
                item.bytes = done
                conn.disconnect()
                break
            }
            val out = unique(dir(app!!), item.name)
            if (!item.partFile.renameTo(out)) item.partFile.copyTo(out, overwrite = true).also { item.partFile.delete() }
            item.finalPath = out.path
            item.bytes = out.length()
            item.state = DlState.DONE
            persist()
            withContext(Dispatchers.Main) { onFinished(item) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            item.state = DlState.FAILED
            item.error = e.message ?: "failed"
            persist()
        }
    }

    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun originOf(url: String): String? = runCatching { URL(url).let { it.protocol + "://" + it.host + "/" } }.getOrNull()

    /**
     * Makes a link safe to send: spaces, brackets, quotes and non-ASCII letters in it (a file name such as "Game (USA) [!].zip"
     * straight from a page) are percent-encoded, which servers insist on and answer "400 Bad Request" to otherwise. Characters that
     * are already encoded are left alone.
     */
    fun fixUrl(url: String): String {
        val keep = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#@!$&'()*+,;=%"
        val sb = StringBuilder()
        for (ch in url.trim()) {
            if (keep.indexOf(ch) >= 0) sb.append(ch)
            else ch.toString().toByteArray(Charsets.UTF_8).forEach { sb.append('%').append("%02X".format(it.toInt() and 0xFF)) }
        }
        return sb.toString()
    }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        var n = 1
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        while (f.exists()) f = File(dir, "$base ($n)$ext").also { n++ }
        return f
    }
}

/** Finds the newest APK of a project on GitHub's release list, for the Store's Install buttons. */
object ReleaseResolver {
    /** The download link and file name of the best APK of [repo]'s newest release, or null when it has none. Blocks. */
    fun latestApk(repo: String): Pair<String, String>? {
        for (path in listOf("releases/latest", "releases?per_page=6")) {
            val text = runCatching { get("https://api.github.com/repos/$repo/$path") }.getOrNull() ?: continue
            val releases = runCatching { if (text.trimStart().startsWith("[")) JSONArray(text) else JSONArray().put(JSONObject(text)) }.getOrNull() ?: continue
            for (r in 0 until releases.length()) {
                val assets = releases.getJSONObject(r).optJSONArray("assets") ?: continue
                val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name").endsWith(".apk", true) }
                if (apks.isEmpty()) continue
                // A 64-bit build if there is one, else a universal one, else whatever there is.
                val best = apks.firstOrNull { val n = it.optString("name").lowercase(); "arm64" in n || "aarch64" in n }
                    ?: apks.firstOrNull { "universal" in it.optString("name").lowercase() }
                    ?: apks.first()
                return best.getString("browser_download_url") to best.getString("name")
            }
        }
        return null
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "QitaUI")
        if (conn.responseCode != 200) { conn.disconnect(); throw IOException("HTTP ${conn.responseCode}") }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }
}

/** Opens the installer for a downloaded APK. */
object ApkInstaller {
    /** Returns a message when the user has to do something first (allow installs from this app), else null. */
    fun install(context: Context, file: File): String? {
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return "Allow QitaUI to install apps, then download again or tap the download to install"
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (runCatching { context.startActivity(intent) }.isSuccess) null else "Android could not open the installer"
    }
}

/** Puts finished downloads with the games: into the folder of the console the file belongs to, then the library rescans. */
object DownloadPlacer {
    /** Consoles a file type can belong to (several for shared types like iso). */
    fun candidatesFor(ext: String): List<GameSystem> = SYSTEMS.filter { ext.lowercase() in it.exts }

    /**
     * The folder a download most likely belongs in: by its file type, then by the names in its name (a console's name or a folder's
     * label), then the folder used last time. Returns an index into [folders], or -1 when there is none.
     */
    fun suggest(context: Context, folders: List<GameFolder>, name: String): Int {
        if (folders.isEmpty()) return -1
        val ext = name.substringAfterLast('.', "").lowercase()
        val byExt = candidatesFor(ext)
        if (byExt.size == 1) folders.indexOfFirst { it.systemId == byExt[0].id }.takeIf { it >= 0 }?.let { return it }
        val tokens = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        folders.indexOfFirst { f -> f.label.lowercase().split(Regex("[^a-z0-9]+")).any { it.isNotEmpty() && it in tokens } }.takeIf { it >= 0 }?.let { return it }
        GameScanner.systemFromName(name)?.let { sys -> folders.indexOfFirst { it.systemId == sys.id }.takeIf { it >= 0 }?.let { return it } }
        if (byExt.size > 1) folders.indexOfFirst { f -> byExt.any { it.id == f.systemId } }.takeIf { it >= 0 }?.let { return it }
        val last = DownloadPrefs.lastFolder(context)
        return folders.indexOfFirst { it.uri == last }.takeIf { it >= 0 } ?: 0
    }

    /** A message for the user: what happened to the file. */
    class Result(val ok: Boolean, val message: String)

    /** Copies [file] into the phone's shared Download folder, where other apps (such as Vita3K's file picker) can see it. Blocks. */
    fun saveToPublicDownloads(context: Context, file: File, name: String): Result = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return Result(false, "Could not save to the Download folder")
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: return Result(false, "Could not save to the Download folder")
        } else {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
            file.copyTo(File(dir, name), overwrite = true)
        }
        file.delete()
        Result(true, "Saved $name to your Download folder. In Vita3K choose File, then Install .vpk")
    }.getOrElse { Result(false, "Could not save the file: ${it.message}") }

    /** Copies [file] into the game folder for [system]. Blocks. */
    fun place(context: Context, file: File, name: String, system: GameSystem?, chosen: GameFolder? = null): Result = runCatching {
        val folders = GameLibrary.folders(context)
        val folder = chosen ?: folders.firstOrNull { it.systemId == system?.id } ?: folders.firstOrNull { it.systemId == "auto" }
            ?: return Result(false, "Add a game folder in Settings → Games & Emulators first. The file is in Downloads.")
        val resolver = context.contentResolver
        val tree = Uri.parse(folder.uri)
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        // An auto folder keeps each console in its own sub-folder, named so the scanner can tell them apart.
        val parentId = if (folder.systemId == "auto" && system != null) subFolder(context, tree, rootId, system) else rootId
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
            ?: return Result(false, "Could not write into the game folder")
        resolver.openOutputStream(doc)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: return Result(false, "Could not write into the game folder")
        file.delete()
        Result(true, "Added $name to ${folder.label.ifBlank { system?.name ?: "your game folder" }}")
    }.getOrElse { Result(false, "Could not save the file: ${it.message}") }

    /** Unzips [zip] (no folders, no paths: only the files) and places each one. Blocks. */
    fun unzipAndPlace(context: Context, zip: File, system: GameSystem?, chosen: GameFolder? = null, deleteZip: Boolean = true): Result = runCatching {
        val temp = File(zip.parentFile, "unzip_${System.nanoTime()}").apply { mkdirs() }
        val files = ArrayList<File>()
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                if (entry.isDirectory) continue
                // Only the file name is used, never a path from inside the archive.
                val leaf = File(entry.name).name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                if (leaf.isEmpty()) continue
                val out = File(temp, leaf)
                FileOutputStream(out).use { zin.copyTo(it) }
                files.add(out)
            }
        }
        var placed = 0
        var last: Result? = null
        for (f in files) {
            val r = place(context, f, f.name, system ?: candidatesFor(f.extension).singleOrNull() ?: GameScanner.systemFromName(f.name), chosen)
            last = r
            if (r.ok) placed++ else break
        }
        temp.deleteRecursively()
        if (placed > 0) { if (deleteZip) zip.delete(); Result(true, "Unzipped $placed file${if (placed == 1) "" else "s"} into ${chosen?.label?.ifBlank { null } ?: system?.name ?: "your game folder"}") }
        else last ?: Result(false, "The zip was empty")
    }.getOrElse { Result(false, "Could not unzip: ${it.message}") }

    /** The consoles the files in [zip] could be, from the file types inside it. */
    fun zipCandidates(zip: File): List<GameSystem> = runCatching {
        val seen = LinkedHashSet<String>()
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                if (!entry.isDirectory) seen.add(entry.name.substringAfterLast('.', "").lowercase())
            }
        }
        val lists = seen.map { candidatesFor(it) }.filter { it.isNotEmpty() }
        // A file type that only one console uses decides it; otherwise the user chooses among the first file's consoles.
        lists.firstOrNull { it.size == 1 }?.let { listOf(it[0]) } ?: lists.firstOrNull().orEmpty()
    }.getOrDefault(emptyList())

    private fun subFolder(context: Context, tree: Uri, parentId: String, system: GameSystem): String {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        resolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                val tokens = (c.getString(1) ?: "").lowercase().split(Regex("[^a-z0-9]+"))
                if (isDir && system.aliases.any { it in tokens }) return c.getString(0)
            }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val dir = DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, system.id)
        return DocumentsContract.getDocumentId(dir!!)
    }
}
