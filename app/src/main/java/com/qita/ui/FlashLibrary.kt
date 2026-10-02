package com.qita.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** A Flash game offered for download: a game-list entry, or a search result from the Internet Archive (whose file is looked up when it is chosen). */
class FlashEntry(
    val id: String,
    val title: String,
    val blurb: String,
    /** A direct link to the .swf file, or empty for an Internet Archive item (see [FlashSources.resolveArchive]). */
    val url: String,
    val thumb: String?,
    val source: String,
)

/** The Flash games kept inside the app (they play offline and need no folder or storage permission). */
object FlashLibrary {
    fun dir(c: Context): File = File(c.filesDir, "flash").apply { mkdirs() }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** The id a game file has in the library (the same wherever it came from, so a cover saved early finds it). */
    fun idFor(fileName: String): String = "fl" + sha1(fileName).take(10)

    /** A name made from a title, safe as a file name (no extension). */
    fun safeBase(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), " ").trim().take(80).ifEmpty { "game" }

    /** A file name made from a title. */
    fun fileNameFor(title: String): String = safeBase(title) + ".swf"

    private fun gameFor(f: File): Game {
        val base = f.nameWithoutExtension
        return Game(idFor(f.name), GameScanner.cleanTitle(base), base, "flash", Uri.fromFile(f).toString(), f.path, "swf")
    }

    /** The games in the app's own Flash folder. */
    fun scan(c: Context): List<Game> =
        (dir(c).listFiles() ?: emptyArray()).filter { it.isFile && it.extension.equals("swf", true) }.map { gameFor(it) }

    /** Puts a finished download in the library; null if it could not be copied. */
    fun add(c: Context, src: File, name: String): Game? = runCatching {
        val dest = File(dir(c), fileNameFor(name.removeSuffix(".swf").removeSuffix(".SWF")))
        src.copyTo(dest, overwrite = true)
        gameFor(dest)
    }.getOrNull()

    /** Copies every .swf in a zip into the library (at most 300, none over 60 MB); returns how many were added. Blocks. */
    fun addFromZip(c: Context, zip: File): Int = runCatching {
        var n = 0
        java.util.zip.ZipFile(zip).use { z ->
            for (e in z.entries()) {
                if (n >= 300) break
                if (e.isDirectory) continue
                val base = e.name.substringAfterLast('/')
                if (!base.endsWith(".swf", true) || e.name.contains("__MACOSX") || base.startsWith("._")) continue
                if (e.size == 0L || e.size > 60L * 1024 * 1024) continue
                val dest = File(dir(c), fileNameFor(base.dropLast(4)))
                z.getInputStream(e).use { input -> dest.outputStream().use { input.copyTo(it) } }
                n++
            }
        }
        n
    }.getOrDefault(0)

    /** Deletes the file of a game that lives in the app's own folder (a game from a folder the user added is left alone). */
    fun deleteFile(c: Context, game: Game) {
        val path = game.path ?: return
        val f = File(path)
        if (f.parentFile?.canonicalPath == dir(c).canonicalPath) f.delete()
    }
}

/** Where Flash games can be found: the game list (a JSON file also read by the website) and the Internet Archive. */
object FlashSources {
    private const val UA = "QitaUI"

    /** The game list kept in the QitaUI repository. A copy is bundled in the app and the last good download is cached, so it works offline. */
    const val LIST_URL = "https://raw.githubusercontent.com/qhd22077-art/QitaUI/claude/ps-vita-android-software-w7vqu2/flash/catalogue.json"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun get(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", UA)
        try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** The games in a list file's text: {"games": [{"title", "description", "url", "thumb"}]}. */
    fun parseList(text: String): List<FlashEntry> = runCatching {
        val arr = JSONObject(text).optJSONArray("games") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            val url = o.optString("url").trim()
            if (title.isEmpty() || !url.startsWith("http")) null
            else FlashEntry("list:" + url.hashCode().toUInt().toString(16), title, o.optString("description").ifEmpty { o.optString("author") }, url, o.optString("thumb").ifEmpty { null }, "Game list")
        }
    }.getOrDefault(emptyList())

    /** The game list: the copy in the app plus the one online (kept for offline use). Blocks. */
    fun catalogue(c: Context): List<FlashEntry> {
        val cache = File(c.filesDir, "flash_catalogue.json")
        val online = get(LIST_URL)?.takeIf { parseList(it).isNotEmpty() || it.contains("\"games\"") }
        if (online != null) runCatching { cache.writeText(online) }
        val remote = online ?: cache.takeIf { it.exists() }?.readText()
        val bundled = runCatching { c.assets.open("flash/catalogue.json").bufferedReader().use { it.readText() } }.getOrNull()
        return (parseList(bundled.orEmpty()) + parseList(remote.orEmpty())).distinctBy { it.url }
    }

    /** Asks for a page: its text, or the reason it could not be had. Blocks. */
    private fun getText(url: String): Pair<String?, String?> = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 25000
        conn.setRequestProperty("User-Agent", UA)
        try {
            val code = conn.responseCode
            if (code != 200) null to "The Archive answered HTTP $code" else conn.inputStream.bufferedReader().use { it.readText() } to null
        } finally {
            conn.disconnect()
        }
    } catch (e: java.net.SocketTimeoutException) {
        null to "The Archive did not answer in time"
    } catch (e: java.net.UnknownHostException) {
        null to "There is no connection to the Archive"
    } catch (e: Exception) {
        null to (e.message ?: e.javaClass.simpleName)
    }

    /** What an Archive search found, or why it could not be done. */
    class ArchiveResult(val entries: List<FlashEntry>?, val error: String?)

    private val NOT_GAMES = Regex("(?i)\\b(flash ?player|installer|plug-?in|macromedia|adobe|projector|authoring|animate|cs[0-9]|mx)\\b")

    private fun archiveQuery(q: String): ArchiveResult {
        val url = "https://archive.org/advancedsearch.php?q=" + enc(q) +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=description&rows=60&page=1&output=json&sort%5B%5D=downloads+desc"
        val (text, err) = getText(url)
        if (text == null) return ArchiveResult(null, err)
        return runCatching {
            val docs = JSONObject(text).getJSONObject("response").getJSONArray("docs")
            val all = (0 until docs.length()).mapNotNull { i ->
                val o = docs.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("identifier")
                if (id.isEmpty()) return@mapNotNull null
                val raw = o.opt("description")
                val desc = (if (raw is JSONArray) raw.optString(0) else raw?.toString().orEmpty())
                    .replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(140)
                val title = o.opt("title").let { t -> if (t is JSONArray) t.optString(0) else t?.toString().orEmpty() }.ifBlank { id }
                FlashEntry("ia:$id", title, desc, "", "https://archive.org/services/img/${enc(id)}", "Internet Archive")
            }
            // Flash Player and the authoring tools are not games; leave them out unless that would leave nothing.
            ArchiveResult(all.filter { !NOT_GAMES.containsMatchIn(it.title) }.ifEmpty { all }, null)
        }.getOrElse { ArchiveResult(null, "The Archive's answer could not be read") }
    }

    /**
     * Flash items on the Internet Archive matching [query] (the most downloaded first). The search first asks for items that hold .swf
     * files (the Archive's "Flash" file format), and if there are none, for anything that mentions Flash. Blocks.
     */
    fun searchArchive(query: String): ArchiveResult {
        // Characters that mean something in the Archive's query language would break the search.
        val terms = query.replace(Regex("[\\\\\"():\\[\\]{}^~*?!+/&|-]"), " ").replace(Regex("\\s+"), " ").trim().ifEmpty { "game" }
        val first = archiveQuery("($terms) AND mediatype:software AND format:Flash")
        if (!first.entries.isNullOrEmpty()) return first
        val second = archiveQuery("($terms) AND mediatype:software AND (flash OR swf)")
        return if (second.entries != null || first.entries == null) second else first
    }

    /** One file of an Archive item: its link and its name inside the item. */
    class ArchiveFile(val url: String, val name: String)

    /** What an Archive item holds that can be played: loose .swf files, else small zips to unpack; [note] says why there is nothing. */
    class ArchiveFiles(val swf: List<ArchiveFile>, val zips: List<ArchiveFile>, val note: String?)

    /** Looks up the files of an Archive item. Blocks. */
    fun resolveArchive(id: String): ArchiveFiles {
        val (text, err) = getText("https://archive.org/metadata/${enc(id)}")
        if (text == null) return ArchiveFiles(emptyList(), emptyList(), err ?: "The Archive could not be reached")
        return runCatching {
            val root = JSONObject(text)
            if (root.optBoolean("is_dark") || root.optJSONObject("metadata")?.optString("access-restricted-item") == "true") {
                return ArchiveFiles(emptyList(), emptyList(), "This item is restricted on the Archive (it needs an Archive account), so it cannot be downloaded here.")
            }
            val files = root.optJSONArray("files") ?: return ArchiveFiles(emptyList(), emptyList(), "That item has no files.")
            val base = "https://archive.org/download/${enc(id)}/"
            val swf = ArrayList<ArchiveFile>()
            val zips = ArrayList<Pair<Long, ArchiveFile>>()
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val name = f.optString("name")
                val link = ArchiveFile(base + name.split('/').joinToString("/") { enc(it) }, name)
                val size = f.optString("size").toLongOrNull() ?: 0L
                when {
                    name.endsWith(".swf", true) -> if (swf.size < 40) swf.add(link)
                    name.endsWith(".zip", true) && size in 1..(150L * 1024 * 1024) -> zips.add(size to link)
                }
            }
            val smallest = zips.sortedBy { it.first }.take(3).map { it.second }
            ArchiveFiles(swf, if (swf.isEmpty()) smallest else emptyList(), if (swf.isEmpty() && smallest.isEmpty()) "Nothing playable in it: no .swf file and no zip under 150 MB (it may be an installer or a disk image)." else null)
        }.getOrElse { ArchiveFiles(emptyList(), emptyList(), "The Archive's answer could not be read") }
    }

    /** A picture from a link, for a game's cover; null if it could not be fetched. Blocks. */
    fun picture(url: String): android.graphics.Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", UA)
        try {
            if (conn.responseCode != 200) null else conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
