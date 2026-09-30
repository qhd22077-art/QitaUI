package com.qita.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume
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
    private const val ICONS = "https://rinnegatamante.eu/vitadb/icons/"
    private const val MAX_AGE = 24L * 60 * 60 * 1000

    private fun cache(context: Context) = File(context.filesDir, "vitadb.json")

    /** Places the list has been published at, tried in turn; an answer that is a web page (not JSON) counts as a failure. */
    private val ENDPOINTS = listOf(
        "https://www.rinnegatamante.eu/vitadb/list_hbs_json.php",
        "https://vitadb.rinnegatamante.it/get_hbs_json.php",
        "https://www.rinnegatamante.eu/vitadb/get_hbs_json.php",
    )
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun looksJson(text: String): Boolean = text.trimStart().let { it.startsWith("[") || it.startsWith("{") }

    /**
     * Returns the list: from the cache when it is fresh, else from the site (plain request first, then through a hidden web view,
     * which can get past a "checking your browser" page), else from an older cached copy. Throws with what went wrong if there is nothing.
     */
    suspend fun load(context: Context, force: Boolean = false): List<VitaHb> {
        val file = cache(context)
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE
        if (fresh && !force) {
            withContext(Dispatchers.IO) { runCatching { parse(file.readText()) }.getOrNull() }?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val problems = ArrayList<String>()
        suspend fun accept(text: String, where: String): List<VitaHb>? {
            if (!looksJson(text)) { problems.add("$where answered with a web page: ${text.trim().replace(Regex("\\s+"), " ").take(70)}"); return null }
            val list = runCatching { parse(text) }.onFailure { problems.add("$where: ${it.message}") }.getOrNull() ?: return null
            if (list.isEmpty()) { problems.add("$where: no entries"); return null }
            withContext(Dispatchers.IO) { file.writeText(text) }
            return list
        }
        for (url in ENDPOINTS) {
            val text = withContext(Dispatchers.IO) { runCatching { fetch(url) }.onFailure { problems.add("${host(url)}: ${it.message}") }.getOrNull() } ?: continue
            accept(text, host(url))?.let { return it }
        }
        for (url in ENDPOINTS) {
            val text = runCatching { viaWebView(context, url) }.getOrNull() ?: continue
            accept(text, "web view ${host(url)}")?.let { return it }
        }
        // Offline, or the site changed: an older copy is better than nothing.
        withContext(Dispatchers.IO) { if (file.exists()) runCatching { parse(file.readText()) }.getOrNull() else null }
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        throw IOException(problems.joinToString("; ").take(320).ifEmpty { "no answer" })
    }

    private fun host(url: String) = runCatching { URL(url).host }.getOrDefault(url)

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 25000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
        if (conn.responseCode != 200) { conn.disconnect(); throw IOException("HTTP ${conn.responseCode}") }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    /** Loads [url] in a hidden web view and reads the page text, which for a JSON address is the JSON itself. Null if it never shows JSON. */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun viaWebView(context: Context, url: String): String? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(25_000) {
            suspendCancellableCoroutine<String?> { cont ->
                val web = WebView(context.applicationContext)
                web.settings.javaScriptEnabled = true
                web.settings.userAgentString = UA
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String?) {
                        view.evaluateJavascript("(function(){return document.body?document.body.innerText:''})()") { raw ->
                            val text = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
                            if (text != null && looksJson(text) && cont.isActive) {
                                runCatching { web.stopLoading(); web.destroy() }
                                cont.resume(text)
                            }
                        }
                    }
                }
                cont.invokeOnCancellation { runCatching { web.stopLoading(); web.destroy() } }
                web.loadUrl(url)
            }
        }
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
