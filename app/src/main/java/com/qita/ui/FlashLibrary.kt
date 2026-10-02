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

    /** Asks for a page: its text (at most [limit] bytes), or the reason it could not be had. [who] names the other side in the reason. Blocks. */
    private fun getText(url: String, who: String = "The Archive", limit: Int = 2_500_000): Pair<String?, String?> = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 25000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "*/*")
        try {
            val code = conn.responseCode
            if (code != 200) null to "$who answered HTTP $code"
            else {
                val out = java.io.ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (out.size() < limit) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
                String(out.toByteArray(), Charsets.UTF_8) to null
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: java.net.SocketTimeoutException) {
        null to "$who did not answer in time"
    } catch (e: java.net.UnknownHostException) {
        null to "There is no connection to $who"
    } catch (e: Exception) {
        null to (e.message ?: e.javaClass.simpleName)
    }

    /** What an Archive search found, or why it could not be done; [note] says how many were checked. */
    class ArchiveResult(val entries: List<FlashEntry>?, val error: String?, val note: String? = null)

    private val NOT_GAMES = Regex("(?i)\\b(flash ?player|installer|plug-?in|macromedia|adobe|projector|authoring|animate|cs[0-9]|mx)\\b")

    private fun archiveQuery(q: String): ArchiveResult {
        val url = "https://archive.org/advancedsearch.php?q=" + enc(q) +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=description&rows=40&page=1&output=json&sort%5B%5D=downloads+desc"
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
                    .replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(120)
                val title = o.opt("title").let { t -> if (t is JSONArray) t.optString(0) else t?.toString().orEmpty() }.ifBlank { id }
                FlashEntry("ia:$id", title, desc, "", "https://archive.org/services/img/${enc(id)}", "Internet Archive")
            }
            // Flash Player and the authoring tools are not games; leave them out unless that would leave nothing.
            ArchiveResult(all.filter { !NOT_GAMES.containsMatchIn(it.title) }.ifEmpty { all }, null)
        }.getOrElse { ArchiveResult(null, "The Archive's answer could not be read") }
    }

    /** What the Archive's own file lists say, kept for the session so a game's Get does not ask again. */
    private val archiveCache = java.util.concurrent.ConcurrentHashMap<String, ArchiveFiles>()

    /**
     * Flash items on the Internet Archive matching [query]. The Archive's search cannot be trusted to know what a file is, so it is asked
     * broadly (anything that mentions Flash or .swf, and items with a Flash file format) and then each candidate's own file list is
     * checked: only items that really hold a .swf, or a small zip, are shown. Blocks (a few seconds).
     */
    fun searchArchive(query: String): ArchiveResult {
        // Characters that mean something in the Archive's query language would break the search.
        val terms = query.replace(Regex("[\\\\\"():\\[\\]{}^~*?!+/&|-]"), " ").replace(Regex("\\s+"), " ").trim().ifEmpty { "game" }
        val queries = listOf(
            "($terms) AND (format:Flash OR format:SWF)",
            "($terms) AND (flash OR swf) AND mediatype:(software OR movies OR data)",
        )
        val seen = LinkedHashMap<String, FlashEntry>()
        var error: String? = null
        var reached = false
        for (q in queries) {
            val r = archiveQuery(q)
            if (r.entries == null) { if (!reached) error = r.error; continue }
            reached = true
            r.entries.forEach { seen.putIfAbsent(it.id, it) }
            if (seen.size >= 30) break
        }
        if (!reached) return ArchiveResult(null, error)
        val candidates = seen.values.take(30)
        if (candidates.isEmpty()) return ArchiveResult(emptyList(), null, "The Archive has no items for that search.")
        // Look at each candidate's files, six at a time.
        val pool = java.util.concurrent.Executors.newFixedThreadPool(6)
        val checked = try {
            val tasks = candidates.map { e -> java.util.concurrent.Callable { e to resolveArchive(e.id.removePrefix("ia:")) } }
            pool.invokeAll(tasks, 45, java.util.concurrent.TimeUnit.SECONDS).mapNotNull { f -> runCatching { f.get() }.getOrNull() }
        } finally {
            pool.shutdownNow()
        }
        val good = checked.filter { (_, files) -> files.swf.isNotEmpty() || files.zips.isNotEmpty() }.map { (e, files) ->
            val what = when {
                files.swf.isNotEmpty() -> "${files.swf.size} .swf"
                else -> "zip, ${files.zips.first().size / 1048576} MB"
            }
            FlashEntry(e.id, e.title, what + if (e.blurb.isNotBlank()) "  ·  ${e.blurb}" else "", e.url, e.thumb, e.source)
        }
        return ArchiveResult(good, null, "Checked ${checked.size} results: ${good.size} hold Flash files.")
    }

    /** One file of an Archive item: its link, its name inside the item and its size in bytes. */
    class ArchiveFile(val url: String, val name: String, val size: Long = 0L)

    /** What an Archive item holds that can be played: loose .swf files, else small zips to unpack; [note] says why there is nothing. */
    class ArchiveFiles(val swf: List<ArchiveFile>, val zips: List<ArchiveFile>, val note: String?)

    /** Looks up the files of an Archive item by its identifier (without any prefix). Blocks. */
    fun resolveArchive(id: String): ArchiveFiles {
        archiveCache[id]?.let { return it }
        val (text, err) = getText("https://archive.org/metadata/${enc(id)}", limit = 8_000_000)
        if (text == null) return ArchiveFiles(emptyList(), emptyList(), err ?: "The Archive could not be reached")
        val result = runCatching {
            val root = JSONObject(text)
            if (root.optBoolean("is_dark") || root.optJSONObject("metadata")?.optString("access-restricted-item") == "true") {
                return@runCatching ArchiveFiles(emptyList(), emptyList(), "This item is restricted on the Archive (it needs an Archive account), so it cannot be downloaded here.")
            }
            val files = root.optJSONArray("files") ?: return@runCatching ArchiveFiles(emptyList(), emptyList(), "That item has no files.")
            val base = "https://archive.org/download/${enc(id)}/"
            val swf = ArrayList<ArchiveFile>()
            val zips = ArrayList<ArchiveFile>()
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val name = f.optString("name")
                val size = f.optString("size").toLongOrNull() ?: 0L
                val link = ArchiveFile(base + name.split('/').joinToString("/") { enc(it) }, name, size)
                when {
                    name.endsWith(".swf", true) -> if (swf.size < 40) swf.add(link)
                    name.endsWith(".zip", true) && size in 1..(150L * 1024 * 1024) -> zips.add(link)
                }
            }
            val smallest = zips.sortedBy { it.size }.take(3)
            ArchiveFiles(swf, if (swf.isEmpty()) smallest else emptyList(), if (swf.isEmpty() && smallest.isEmpty()) "Nothing playable in it: no .swf file and no zip under 150 MB (it may be an installer or a disk image)." else null)
        }.getOrElse { ArchiveFiles(emptyList(), emptyList(), "The Archive's answer could not be read") }
        archiveCache[id] = result
        return result
    }

    // --- Other sites: a page, a game-list file or a direct link that the user chose ---

    /** What looking at an address found. */
    class SiteResult(val entries: List<FlashEntry>, val note: String?)

    private val ANCHOR = Regex("""(?is)<a\s[^>]*?href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""")
    private val SWF_QUOTED = Regex("""(?i)["']([^"'\s<>]+?\.swf(?:\?[^"'\s<>]*)?)["']""")
    private val SWF_BARE = Regex("""(?i)(https?://[^\s"'<>]+?\.swf)(?![A-Za-z0-9])""")
    private val ASSETS = Regex("""(?i)\.(swf|zip|png|jpe?g|gif|webp|css|js|ico|pdf|svg|mp3|mp4|ogg|wav|rar|7z|exe|apk)$""")

    private fun absolute(base: String, ref: String): String? =
        runCatching { URL(URL(base), ref.replace("&amp;", "&").trim()).toString() }.getOrNull()?.takeIf { it.startsWith("http") }

    private fun titleFromUrl(url: String): String {
        val last = runCatching { java.net.URLDecoder.decode(url.substringBefore('?').substringBefore('#').substringAfterLast('/'), "UTF-8") }.getOrDefault("game")
        return GameScanner.cleanTitle(last.substringBeforeLast('.').ifEmpty { last }).ifBlank { "Game" }
    }

    private fun siteEntry(url: String, title: String, source: String) =
        FlashEntry("site:" + url.hashCode().toUInt().toString(16), title, "", url, null, source)

    /** Every .swf link in a page's text (links, embeds, quoted addresses), added to [found]. */
    private fun scanPage(base: String, html: String, source: String, found: LinkedHashMap<String, FlashEntry>) {
        for (m in ANCHOR.findAll(html)) {
            val link = absolute(base, m.groupValues[1]) ?: continue
            if (!link.substringBefore('?').endsWith(".swf", true)) continue
            val text = m.groupValues[2].replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            found.putIfAbsent(link, siteEntry(link, text.ifBlank { titleFromUrl(link) }.take(80), source))
        }
        for (m in SWF_QUOTED.findAll(html)) {
            val link = absolute(base, m.groupValues[1]) ?: continue
            found.putIfAbsent(link, siteEntry(link, titleFromUrl(link), source))
        }
        for (m in SWF_BARE.findAll(html)) {
            val link = m.groupValues[1]
            found.putIfAbsent(link, siteEntry(link, titleFromUrl(link), source))
        }
    }

    /** Links to other pages of the same site, in the order they appear. */
    private fun pageLinks(base: String, html: String): List<String> {
        val host = runCatching { URL(base).host }.getOrNull() ?: return emptyList()
        return ANCHOR.findAll(html).mapNotNull { m ->
            val ref = m.groupValues[1]
            if (ref.startsWith("#") || ref.startsWith("javascript:", true) || ref.startsWith("mailto:", true)) return@mapNotNull null
            val link = absolute(base, ref)?.substringBefore('#') ?: return@mapNotNull null
            val u = runCatching { URL(link) }.getOrNull() ?: return@mapNotNull null
            if (u.host != host || link == base || ASSETS.containsMatchIn(u.path.orEmpty())) null else link
        }.distinct().toList()
    }

    /** A game-list file: {"games": [...]}, or a plain list of objects with title and url, or of links. Relative links are made whole. */
    private fun parseAnyList(text: String, base: String, source: String): List<FlashEntry> = runCatching {
        val arr = if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text).optJSONArray("games") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val item = arr.opt(i)
            val (title, url, thumb, desc) = when (item) {
                is JSONObject -> listOf(item.optString("title"), item.optString("url"), item.optString("thumb"), item.optString("description").ifEmpty { item.optString("author") })
                is String -> listOf("", item, "", "")
                else -> return@mapNotNull null
            }
            val link = absolute(base, url) ?: return@mapNotNull null
            FlashEntry("site:" + link.hashCode().toUInt().toString(16), title.trim().ifEmpty { titleFromUrl(link) }, desc, link, thumb.takeIf { it.isNotBlank() }?.let { absolute(base, it) }, source)
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    /**
     * Looks at an address the user gave: an Internet Archive item page, a direct .swf or .zip link, a game-list file, or a web page (its
     * .swf links, then up to five of its other pages). Nothing is downloaded but the pages. Blocks.
     */
    fun scanAddress(address: String): SiteResult {
        var a = address.trim()
        if (a.isEmpty()) return SiteResult(emptyList(), "There is nothing to look at.")
        if (!a.contains("://")) a = "https://$a"
        val uri = runCatching { java.net.URI(a) }.getOrNull()
        val host = uri?.host ?: return SiteResult(emptyList(), "That is not a web address.")
        val path = uri.path.orEmpty()
        if (host.endsWith("archive.org") && path.startsWith("/details/")) {
            val id = path.removePrefix("/details/").substringBefore('/')
            if (id.isNotEmpty()) return SiteResult(listOf(FlashEntry("ia:$id", id.replace('_', ' '), "", "", "https://archive.org/services/img/${enc(id)}", "Internet Archive")), null)
        }
        val bare = a.substringBefore('?').lowercase()
        if (bare.endsWith(".swf") || bare.endsWith(".zip")) return SiteResult(listOf(siteEntry(a, titleFromUrl(a), host)), null)
        val (text, err) = getText(a, "The site")
        if (text == null) return SiteResult(emptyList(), err)
        val head = text.trimStart()
        if (head.startsWith("{") || head.startsWith("[")) {
            val list = parseAnyList(head, a, host)
            return SiteResult(list, if (list.isEmpty()) "That file holds no games (it needs a \"games\" list, each with a title and a url)." else null)
        }
        val found = LinkedHashMap<String, FlashEntry>()
        scanPage(a, text, host, found)
        var pages = 1
        for (s in pageLinks(a, text).take(5)) {
            if (found.size >= 200) break
            val (t, _) = getText(s, "The site")
            if (t != null) { scanPage(s, t, host, found); pages++ }
        }
        val list = found.values.take(200)
        return SiteResult(
            list,
            if (list.isEmpty()) "No .swf files found on $pages page${if (pages == 1) "" else "s"}. The site may load its games with scripts, or keep the file out of reach."
            else "Found ${list.size} .swf file${if (list.size == 1) "" else "s"} on $pages page${if (pages == 1) "" else "s"}.",
        )
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

/** A site the user added to the Flash tab: a name and the address of a page, a game-list file or a link. */
class FlashSite(val name: String, val address: String)

/** The sites the user added, kept in the app's storage. */
object FlashSiteStore {
    private fun prefs(c: Context) = c.getSharedPreferences("qita_flash_sites", Context.MODE_PRIVATE)

    fun list(c: Context): List<FlashSite> = runCatching {
        val arr = JSONArray(prefs(c).getString("sites", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> FlashSite(o.getString("name"), o.getString("address")) } }
    }.getOrDefault(emptyList())

    private fun save(c: Context, sites: List<FlashSite>) {
        val arr = JSONArray()
        sites.forEach { arr.put(JSONObject().put("name", it.name).put("address", it.address)) }
        prefs(c).edit().putString("sites", arr.toString()).apply()
    }

    /** Adds a site (or renames it if the address is already there) and returns the list. */
    fun add(c: Context, name: String, address: String): List<FlashSite> {
        val list = list(c).filter { it.address != address } + FlashSite(name.ifBlank { address }, address)
        save(c, list)
        return list
    }

    fun remove(c: Context, address: String): List<FlashSite> {
        val list = list(c).filter { it.address != address }
        save(c, list)
        return list
    }
}
