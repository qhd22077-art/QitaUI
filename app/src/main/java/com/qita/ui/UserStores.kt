package com.qita.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** One entry of a scanned page: a file to download, a folder (another list to open) or an ordinary page. */
data class ScanItem(val name: String, val url: String, val section: String, val folder: Boolean = false, val page: Boolean = false)

/** A store the user added by its link. [mode]: 0 automatic, 1 never use AI, 2 always use AI. */
data class UserStore(val id: String, val name: String, val url: String, val mode: Int, val items: List<ScanItem>, val scannedAt: Long)

/** The user's own stores, and the settings of the AI scan, kept in the Store's preferences. */
object UserStores {
    const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
    private fun prefs(c: Context) = c.getSharedPreferences("qita_store", Context.MODE_PRIVATE)

    fun load(c: Context): List<UserStore> = runCatching {
        val arr = JSONArray(prefs(c).getString("user_stores", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val its = o.optJSONArray("items") ?: JSONArray()
            UserStore(
                o.getString("id"), o.optString("name"), o.getString("url"), o.optInt("mode", 0),
                (0 until its.length()).map { j ->
                    val it = its.getJSONObject(j)
                    ScanItem(it.optString("n"), it.optString("u"), it.optString("s"), it.optBoolean("f"), it.optBoolean("p"))
                },
                o.optLong("at"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(c: Context, list: List<UserStore>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().put("id", s.id).put("name", s.name).put("url", s.url).put("mode", s.mode).put("at", s.scannedAt)
                    .put("items", JSONArray().also { a -> s.items.take(400).forEach { a.put(JSONObject().put("n", it.name).put("u", it.url).put("s", it.section).put("f", it.folder).put("p", it.page)) } }),
            )
        }
        prefs(c).edit().putString("user_stores", arr.toString()).apply()
    }

    /** Models offered in the picker: the id and a plain description. */
    val MODELS: List<Pair<String, String>> = listOf(
        "claude-haiku-4-5-20251001" to "Haiku 4.5 (fast and cheap, good for this)",
        "claude-sonnet-5-5" to "Sonnet 5.5 (more careful)",
        "claude-opus-5-5" to "Opus 5.5 (most careful, slower)",
        "claude-fable-5-1" to "Fable 5.1",
    )
    fun modelName(id: String): String = MODELS.firstOrNull { it.first == id }?.second ?: id

    fun aiMaxLinks(c: Context): Int = prefs(c).getInt("ai_links", 250)
    fun aiDefaultMode(c: Context): Int = prefs(c).getInt("ai_mode", 0)
    fun aiExtra(c: Context): String = prefs(c).getString("ai_extra", "").orEmpty()
    fun aiTidy(c: Context): Boolean = prefs(c).getBoolean("ai_tidy", true)
    fun aiGroup(c: Context): Boolean = prefs(c).getBoolean("ai_group", true)
    fun setAiOptions(c: Context, links: Int, mode: Int, extra: String, tidy: Boolean, group: Boolean) {
        prefs(c).edit().putInt("ai_links", links).putInt("ai_mode", mode).putString("ai_extra", extra.trim()).putBoolean("ai_tidy", tidy).putBoolean("ai_group", group).apply()
    }

    fun aiKey(c: Context): String = prefs(c).getString("ai_key", "").orEmpty()
    fun aiModel(c: Context): String = prefs(c).getString("ai_model", DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
    fun setAi(c: Context, key: String, model: String) {
        prefs(c).edit().putString("ai_key", key.trim()).putString("ai_model", model.trim().ifBlank { DEFAULT_MODEL }).apply()
    }
}

/**
 * Reads a web page the user pointed a store at and lists what can be downloaded from it.
 *  - The plain scan needs no AI: it finds the links, tells files from folders from pages by their addresses, names them from the
 *    link text or the file name, and groups them by file type.
 *  - The AI scan hands the same links to an AI model (with the user's own key), which keeps the ones worth listing, tidies the names
 *    and groups them. It can only choose among links that are really on the page, so it cannot invent an address.
 */
object PageScanner {
    class Result(val items: List<ScanItem>, val note: String)
    private class Cand(val text: String, val url: String, val kind: Int) // kind: 0 file, 1 folder, 2 page

    private val FILE_EXT = setOf(
        "apk", "zip", "7z", "rar", "iso", "bin", "cue", "chd", "vpk", "pkg", "nsp", "xci", "gba", "gbc", "gb", "nes", "sfc", "smc", "n64", "z64", "v64",
        "nds", "3ds", "cia", "gen", "md", "sms", "gg", "pce", "a26", "cso", "pbp", "wad", "rvz", "gcz", "ngp", "tar", "gz", "exe", "msi", "dmg", "deb", "xapk", "apks",
    )
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun fetch(url: String): Pair<String, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 25000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "text/html,*/*")
        if (conn.responseCode !in 200..299) { conn.disconnect(); throw IOException("HTTP ${conn.responseCode}") }
        val base = conn.url.toString()
        // At most 2 MB of the page (InputStream.readNBytes needs Android 13, so read by hand).
        val body = conn.inputStream.use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (out.size() < 2_000_000) { val n = ins.read(buf); if (n < 0) break; out.write(buf, 0, n) }
            out.toString("UTF-8")
        }
        conn.disconnect()
        return base to body
    }

    private fun clean(s: String): String = s.replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun fileName(url: String): String = runCatching {
        URLDecoder.decode(URL(url).path.trimEnd('/').substringAfterLast('/'), "UTF-8")
    }.getOrDefault(url)

    private fun candidates(base: String, html: String): List<Cand> {
        val baseUrl = runCatching { URL(base) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, Cand>()
        val re = Regex("<a\\s[^>]*?href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in re.findAll(html)) {
            val href = (m.groups[1]?.value ?: m.groups[2]?.value ?: "").trim().replace("&amp;", "&")
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("?") || href.startsWith("javascript:", true) || href.startsWith("mailto:", true)) continue
            if (href.contains("?C=") || href.contains("?sort")) continue
            val resolved = runCatching { URL(baseUrl, href) }.getOrNull() ?: continue
            if (resolved.protocol != "http" && resolved.protocol != "https") continue
            val url = resolved.toString().substringBefore('#')
            if (url in out) continue
            val text = clean(m.groups[3]?.value ?: "")
            if (text.equals("parent directory", true) || text == ".." || href == "../") continue
            val path = resolved.path.lowercase()
            val last = path.trimEnd('/').substringAfterLast('/')
            val ext = if ('.' in last && !path.endsWith("/")) last.substringAfterLast('.') else ""
            val kind = when {
                ext in FILE_EXT -> 0
                path.endsWith("/") && resolved.host == baseUrl.host && path.length > baseUrl.path.length && path.startsWith(baseUrl.path.lowercase()) -> 1
                else -> 2
            }
            out[url] = Cand(text, url, kind)
        }
        return out.values.toList()
    }

    private fun nameOf(c: Cand): String {
        val generic = c.text.isBlank() || c.text.length < 3 || c.text.lowercase() in setOf("download", "here", "click here", "link", "get", "mirror")
        return if (generic) fileName(c.url).ifBlank { c.url } else c.text
    }

    private fun sectionOf(c: Cand): String = when (c.kind) {
        0 -> fileName(c.url).substringAfterLast('.', "").uppercase() + " files"
        1 -> "Folders"
        else -> "Links"
    }

    private fun plainItems(cands: List<Cand>): List<ScanItem> {
        val files = cands.filter { it.kind == 0 }
        val folders = cands.filter { it.kind == 1 }
        // Ordinary pages are only listed when the page offers nothing else, so a page of files is not buried in menu links.
        val pages = if (files.isEmpty() && folders.isEmpty()) cands.filter { it.kind == 2 }.take(80) else emptyList()
        return (folders + files.sortedBy { sectionOf(it) } + pages).map { ScanItem(nameOf(it), it.url, sectionOf(it), it.kind == 1, it.kind == 2) }
    }

    private fun aiItems(context: Context, title: String, cands: List<Cand>): List<ScanItem> {
        val key = UserStores.aiKey(context)
        if (key.isBlank()) throw IOException("no AI key is set")
        val list = (cands.filter { it.kind == 0 } + cands.filter { it.kind == 1 } + cands.filter { it.kind == 2 }).take(UserStores.aiMaxLinks(context))
        if (list.isEmpty()) return emptyList()
        val prompt = buildString {
            append("You are helping build a download menu from a web page titled \"").append(title.take(120)).append("\". Below are the links found on the page, each as: number | link text | address.\n")
            append("Choose the links that lead to downloadable files, to folders or lists of files, or to pages that clearly offer downloads. Drop navigation, login, social and advert links. ")
            append("For each link you keep, give ")
            append(if (UserStores.aiTidy(context)) "a clean short name" else "its name as it is")
            append(if (UserStores.aiGroup(context)) " and a short section title (for example a console, a type of file or a category), and group similar ones under the same section title.\n" else " and the section title \"Files\".\n")
            UserStores.aiExtra(context).takeIf { it.isNotBlank() }?.let { append("The user also asks: ").append(it.take(200)).append(". Follow that when choosing.\n") }
            append("Reply with ONLY a JSON array, no other words: [{\"n\": <number>, \"name\": \"...\", \"section\": \"...\"}]. Use only numbers from the list.\n\n")
            list.forEachIndexed { i, c -> append(i + 1).append(" | ").append(c.text.take(80)).append(" | ").append(c.url.take(160)).append('\n') }
        }
        val body = JSONObject()
            .put("model", UserStores.aiModel(context))
            .put("max_tokens", 4096)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 90000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-api-key", key)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code")
            throw IOException(msg)
        }
        val reply = JSONObject(text).getJSONArray("content").getJSONObject(0).getString("text")
        val arr = JSONArray(reply.substring(reply.indexOf('['), reply.lastIndexOf(']') + 1))
        val seen = HashSet<Int>()
        val out = ArrayList<ScanItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val n = o.optInt("n", -1)
            if (n < 1 || n > list.size || !seen.add(n)) continue
            val c = list[n - 1]
            out.add(ScanItem(o.optString("name").ifBlank { nameOf(c) }, c.url, o.optString("section").ifBlank { sectionOf(c) }, c.kind == 1, c.kind == 2))
        }
        // Folders first, then files and pages, each group in the AI's order of sections.
        return out.sortedBy { if (it.folder) 0 else if (it.page) 2 else 1 }
    }

    /** The model ids the user's key can use, newest first. Blocks. Throws with the API's message on failure. */
    fun listModels(context: Context): List<String> {
        val key = UserStores.aiKey(context)
        if (key.isBlank()) throw IOException("no AI key is set")
        val conn = URL("https://api.anthropic.com/v1/models?limit=50").openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 20000
        conn.setRequestProperty("x-api-key", key)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) throw IOException(runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code"))
        val arr = JSONObject(text).getJSONArray("data")
        return (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
    }

    /** One tiny request with the saved key and model; returns "It works." or what went wrong. Blocks. */
    fun testAi(context: Context): String {
        val key = UserStores.aiKey(context)
        if (key.isBlank()) return "Type your API key first, then Save."
        return runCatching {
            val body = JSONObject().put("model", UserStores.aiModel(context)).put("max_tokens", 8)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Reply with the word OK.")))
            val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 12000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", key)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (code in 200..299) "It works (${UserStores.aiModel(context)})."
            else runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code")
        }.getOrElse { "Could not reach the API (${it.message})" }
    }

    /** Scans [url]. Blocks. Throws with a readable message if the page cannot be read. */
    fun scan(context: Context, url: String, mode: Int): Result {
        val (base, html) = fetch(url)
        val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groups?.get(1)?.value?.let { clean(it) }.orEmpty()
        val cands = candidates(base, html)
        val plain = plainItems(cands)
        val useful = plain.count { !it.page }
        val hasKey = UserStores.aiKey(context).isNotBlank()
        val wantAi = mode == 2 || (mode == 0 && hasKey && useful == 0)
        if (wantAi) {
            try {
                val items = aiItems(context, title, cands)
                return Result(items, "AI scan: ${items.size} entr${if (items.size == 1) "y" else "ies"}")
            } catch (e: Exception) {
                return Result(plain, "The AI scan did not work (${e.message}); this is the plain scan")
            }
        }
        val files = plain.count { !it.folder && !it.page }
        val folders = plain.count { it.folder }
        return Result(plain, if (plain.isEmpty()) "No links found on that page" else "Plain scan: $files file${if (files == 1) "" else "s"}, $folders folder${if (folders == 1) "" else "s"}")
    }
}
