package com.qita.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One PS Vita homebrew from VitaDB, a public database of homebrew that its authors released for anyone to download. */
class VitaHb(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val date: String,
    val description: String,
    /** 1 original game, 2 port, 3 emulator, 4 tool. */
    val type: Int,
    val download: String,
    val iconUrl: String?,
)

/**
 * The list of Vita homebrew, fetched when the Store asks for it and kept on the device for a day. The data format is read
 * tolerantly (several possible field names), and an entry with no download link is skipped, so a change on the site cannot crash the app.
 */
object VitaDb {
    const val SITE = "https://vitadb.rinnegatamante.it/"
    private const val API = "https://vitadb.rinnegatamante.it/get_hbs_json.php"
    private const val ICONS = "https://rinnegatamante.eu/vitadb/icons/"
    private const val MAX_AGE = 24L * 60 * 60 * 1000

    private fun cache(context: Context) = File(context.filesDir, "vitadb.json")

    /** Returns the list, from the cache when it is fresh (or when the network fails), else from the site. Blocks; throws if there is nothing. */
    fun load(context: Context, force: Boolean = false): List<VitaHb> {
        val file = cache(context)
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE
        if (fresh && !force) runCatching { parse(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        try {
            val text = fetch()
            val list = parse(text)
            if (list.isEmpty()) throw IOException("The list was empty")
            file.writeText(text)
            return list
        } catch (e: Exception) {
            // Offline or the site changed: an older copy is better than nothing.
            if (file.exists()) runCatching { parse(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
            throw e
        }
    }

    private fun fetch(): String {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 25000
        conn.setRequestProperty("User-Agent", "QitaUI")
        if (conn.responseCode != 200) { conn.disconnect(); throw IOException("VitaDB said ${conn.responseCode}") }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun JSONObject.text(vararg keys: String): String {
        for (k in keys) {
            val v = optString(k, "")
            if (v.isNotBlank() && v != "null") return v
        }
        return ""
    }

    fun parse(text: String): List<VitaHb> {
        val trimmed = text.trimStart()
        val arr = if (trimmed.startsWith("[")) JSONArray(text) else JSONObject(text).let { o ->
            o.optJSONArray("data") ?: o.optJSONArray("homebrews") ?: o.optJSONArray("list") ?: JSONArray()
        }
        val out = ArrayList<VitaHb>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val link = o.text("data", "download", "download_url", "vpk", "link")
            val name = o.text("name", "title")
            if (link.isEmpty() || name.isEmpty() || !link.startsWith("http")) continue
            val icon = o.text("icon", "icon_url", "image")
            out.add(
                VitaHb(
                    id = o.text("id").ifEmpty { i.toString() },
                    name = name,
                    author = o.text("author", "developer"),
                    version = o.text("version"),
                    date = o.text("date", "updated", "release_date").take(10),
                    description = stripHtml(o.text("description", "desc", "long_description")),
                    type = o.text("type", "category").toIntOrNull() ?: 1,
                    download = link,
                    iconUrl = if (icon.isEmpty()) null else if (icon.startsWith("http")) icon else ICONS + icon,
                ),
            )
        }
        // Newest first.
        return out.sortedByDescending { it.date }
    }

    private fun stripHtml(s: String): String =
        s.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").trim()
}
