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

    /** A file name made from a title. */
    fun fileNameFor(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), " ").trim().take(80).ifEmpty { "game" } + ".swf"

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
    const val LIST_URL = "https://raw.githubusercontent.com/qhd22077-art/QitaUI/main/flash/catalogue.json"

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

    /** Flash items on the Internet Archive matching [query] (the most downloaded first); null if it could not be reached. Blocks. */
    fun searchArchive(query: String): List<FlashEntry>? {
        val terms = query.trim().ifEmpty { "flash" }.replace("\"", " ")
        val q = "($terms) AND mediatype:software AND (flash OR swf)"
        val url = "https://archive.org/advancedsearch.php?q=" + enc(q) +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=description&rows=40&page=1&output=json&sort%5B%5D=downloads+desc"
        val text = get(url) ?: return null
        return runCatching {
            val docs = JSONObject(text).getJSONObject("response").getJSONArray("docs")
            (0 until docs.length()).mapNotNull { i ->
                val o = docs.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("identifier")
                if (id.isEmpty()) return@mapNotNull null
                val raw = o.opt("description")
                val desc = (if (raw is JSONArray) raw.optString(0) else raw?.toString().orEmpty())
                    .replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(140)
                val title = o.opt("title").let { t -> if (t is JSONArray) t.optString(0) else t?.toString().orEmpty() }.ifBlank { id }
                FlashEntry("ia:$id", title, desc, "", "https://archive.org/services/img/${enc(id)}", "Internet Archive")
            }
        }.getOrNull()
    }

    /** The link to the first .swf file of an Internet Archive item, or null if it has none (or could not be looked up). Blocks. */
    fun resolveArchive(id: String): String? {
        val text = get("https://archive.org/metadata/${enc(id)}") ?: return null
        return runCatching {
            val files = JSONObject(text).optJSONArray("files") ?: return null
            for (i in 0 until files.length()) {
                val name = files.optJSONObject(i)?.optString("name").orEmpty()
                if (name.endsWith(".swf", true)) return "https://archive.org/download/${enc(id)}/" + name.split('/').joinToString("/") { enc(it) }
            }
            null
        }.getOrNull()
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
