package com.qita.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/** One entry of a folder listing. */
data class FileItem(val file: File, val isDir: Boolean, val size: Long, val modified: Long) {
    val name: String get() = file.name
    val path: String get() = file.path
}

enum class SortBy(val label: String) { NAME("Name"), SIZE("Size"), DATE("Date") }

/**
 * The file operations behind the Folders app. They use plain files, which needs "All files access" (the user switches it on
 * once in Android settings). Everything here blocks, so call it off the main thread.
 */
object FileManager {
    private fun prefs(c: Context) = c.getSharedPreferences("qita_folders", Context.MODE_PRIVATE)

    /** Whether the app may look at every file (Android 11 and later need the all-files switch; before that the normal permission is not asked for here). */
    fun hasAccess(): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    /** The places to start from: internal storage, Downloads, and any SD card or USB drive that is mounted. */
    fun roots(context: Context): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        @Suppress("DEPRECATION") val internal = Environment.getExternalStorageDirectory()
        out.add("Internal storage" to internal)
        @Suppress("DEPRECATION") out.add("Downloads" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                sm.storageVolumes.filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }.forEach { v ->
                    v.directory?.let { out.add((v.getDescription(context) ?: "SD card") to it) }
                }
            }
        }
        return out
    }

    /** The files and folders in [dir], folders first, in the chosen order. */
    fun list(dir: File, showHidden: Boolean, sort: SortBy): List<FileItem> {
        val files = dir.listFiles() ?: return emptyList()
        val items = files.filter { showHidden || !it.name.startsWith(".") }.map { FileItem(it, it.isDirectory, if (it.isDirectory) 0L else it.length(), it.lastModified()) }
        val order = when (sort) {
            SortBy.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortBy.SIZE -> compareByDescending<FileItem> { it.size }.thenBy { it.name.lowercase() }
            SortBy.DATE -> compareByDescending<FileItem> { it.modified }.thenBy { it.name.lowercase() }
        }
        return items.sortedWith(compareByDescending<FileItem> { it.isDir }.then(order))
    }

    /** Every file or folder under [root] whose name has [query] in it (up to [limit] of them). */
    fun search(root: File, query: String, showHidden: Boolean, limit: Int = 300): List<FileItem> {
        val out = ArrayList<FileItem>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty() && out.size < limit) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (!showHidden && f.name.startsWith(".")) continue
                if (f.name.contains(query, ignoreCase = true)) out.add(FileItem(f, f.isDirectory, if (f.isDirectory) 0L else f.length(), f.lastModified()))
                if (f.isDirectory && !java.nio.file.Files.isSymbolicLink(f.toPath())) stack.add(f)
                if (out.size >= limit) break
            }
        }
        return out.sortedWith(compareByDescending<FileItem> { it.isDir }.thenBy { it.name.lowercase() })
    }

    /** A name for [name] in [dir] that is free: "name", then "name (1)", "name (2)"... */
    fun uniqueTarget(dir: File, name: String): File {
        var f = File(dir, name)
        var n = 1
        val base = if (name.contains('.') && !File(dir, name).isDirectory) name.substringBeforeLast('.') else name
        val ext = if (base.length < name.length) "." + name.substringAfterLast('.') else ""
        while (f.exists()) f = File(dir, "$base ($n)$ext").also { n++ }
        return f
    }

    /** Copies a file or a whole folder into [dstDir]. Stops early (returning false) if [cancel] says so or something fails. */
    fun copy(src: File, dstDir: File, cancel: () -> Boolean = { false }): Boolean {
        if (cancel()) return false
        val target = uniqueTarget(dstDir, src.name)
        if (src.isDirectory) {
            // A folder cannot be copied into itself.
            if (target.canonicalPath.startsWith(src.canonicalPath + File.separator)) return false
            if (!target.mkdirs()) return false
            return (src.listFiles() ?: emptyArray()).all { copy(it, target, cancel) }
        }
        return runCatching { src.inputStream().use { i -> target.outputStream().use { o -> i.copyTo(o) } }; true }.getOrDefault(false)
    }

    /** Moves a file or folder into [dstDir]: a rename where that works, else a copy followed by deleting the original. */
    fun move(src: File, dstDir: File, cancel: () -> Boolean = { false }): Boolean {
        if (src.parentFile?.canonicalPath == dstDir.canonicalPath) return true
        if (src.isDirectory && dstDir.canonicalPath.startsWith(src.canonicalPath)) return false
        if (src.renameTo(uniqueTarget(dstDir, src.name))) return true
        return copy(src, dstDir, cancel) && delete(src)
    }

    fun delete(f: File): Boolean {
        if (f.isDirectory && !java.nio.file.Files.isSymbolicLink(f.toPath())) (f.listFiles() ?: emptyArray()).forEach { delete(it) }
        return f.delete()
    }

    fun rename(f: File, newName: String): File? {
        val clean = newName.trim().replace('/', '_')
        if (clean.isEmpty() || clean == f.name) return null
        val target = File(f.parentFile, clean)
        if (target.exists()) return null
        return if (f.renameTo(target)) target else null
    }

    fun mkdir(parent: File, name: String): File? {
        val clean = name.trim().replace('/', '_')
        if (clean.isEmpty()) return null
        val dir = File(parent, clean)
        return if (!dir.exists() && dir.mkdirs()) dir else null
    }

    /** The size of a file, or of everything inside a folder, and how many files that is. */
    fun measure(f: File): Pair<Long, Int> {
        if (!f.isDirectory) return f.length() to 1
        var size = 0L
        var count = 0
        val stack = ArrayDeque<File>()
        stack.add(f)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            for (c in dir.listFiles() ?: emptyArray()) {
                if (c.isDirectory) { if (!java.nio.file.Files.isSymbolicLink(c.toPath())) stack.add(c) } else { size += c.length(); count++ }
            }
        }
        return size to count
    }

    fun mime(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

    /** Opens [f] with another app. Returns a message if that could not be done. */
    fun open(context: Context, f: File): String? {
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", f) }.getOrNull() ?: return "That file cannot be shared with other apps"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime(f)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (runCatching { context.startActivity(intent) }.isSuccess) null else "No app can open that kind of file"
    }

    /** Offers [files] to the share sheet. */
    fun share(context: Context, files: List<File>): String? {
        val uris = files.filter { it.isFile }.mapNotNull { runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }.getOrNull() }
        if (uris.isEmpty()) return "Only files can be shared"
        val intent = (if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)))
            .setType(if (uris.size == 1) mime(files.first { it.isFile }) else "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (runCatching { context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) null else "Could not open the share sheet"
    }

    /**
     * Unpacks [archive] into [outDir], keeping its folders. Paths that try to leave [outDir] (".." or absolute) are skipped.
     * Returns how many files were written. Throws if the archive cannot be read.
     */
    fun extractTree(archive: File, outDir: File): Int {
        outDir.mkdirs()
        val root = outDir.canonicalPath
        var count = 0
        fun write(name: String, dir: Boolean, body: (OutputStream) -> Unit) {
            val clean = name.replace('\\', '/').trimStart('/')
            if (clean.isEmpty() || clean.split('/').any { it == ".." }) return
            val f = File(outDir, clean)
            if (!f.canonicalPath.startsWith(root + File.separator)) return
            if (dir) { f.mkdirs(); return }
            f.parentFile?.mkdirs()
            FileOutputStream(f).use(body)
            count++
        }
        val n = archive.name.lowercase()
        when {
            n.endsWith(".zip") -> DownloadPlacer.forEachZipEntry(archive) { name, dir, open ->
                write(name, dir) { o -> open().use { it.copyTo(o) } }
            }
            n.endsWith(".7z") -> org.apache.commons.compress.archivers.sevenz.SevenZFile(archive).use { sz ->
                while (true) {
                    val e = sz.nextEntry ?: break
                    write(e.name, e.isDirectory) { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) { val r = sz.read(buf); if (r < 0) break; o.write(buf, 0, r) }
                    }
                }
            }
            n.endsWith(".rar") -> com.github.junrar.Archive(archive).use { rar ->
                var h = rar.nextFileHeader()
                while (h != null) { val header = h; write(header.fileName, header.isDirectory) { o -> rar.extractFile(header, o) }; h = rar.nextFileHeader() }
            }
            n.endsWith(".tar") || n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tar.xz") || n.endsWith(".txz") ->
                org.apache.commons.compress.archivers.tar.TarArchiveInputStream(DownloadPlacer.tarStream(archive)).use { t ->
                    while (true) { val e = t.nextEntry ?: break; write(e.name, e.isDirectory) { o -> t.copyTo(o) } }
                }
            n.endsWith(".gz") -> org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(archive.inputStream().buffered()).use { g ->
                write(archive.name.dropLast(3), false) { o -> g.copyTo(o) }
            }
            else -> throw IOException("Not an archive this app can open")
        }
        return count
    }

    // ---- Shortcuts and recent folders ----

    fun pins(c: Context): List<String> = readList(c, "pins")
    fun recents(c: Context): List<String> = readList(c, "recent")

    fun togglePin(c: Context, path: String): List<String> {
        val list = pins(c).toMutableList()
        if (!list.remove(path)) list.add(path)
        writeList(c, "pins", list)
        return list
    }

    fun addRecent(c: Context, path: String) {
        val list = recents(c).toMutableList()
        list.remove(path)
        list.add(0, path)
        writeList(c, "recent", list.take(15))
    }

    private fun readList(c: Context, key: String): List<String> = runCatching {
        val arr = JSONArray(prefs(c).getString(key, "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun writeList(c: Context, key: String, list: List<String>) {
        prefs(c).edit().putString(key, JSONArray(list).toString()).apply()
    }
}
