package com.qita.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One visited page. */
data class HistoryEntry(val title: String, val url: String, val time: Long)

/** A tab as remembered between runs: only where it was, not the page's own state. */
data class SavedTab(val id: String, val title: String, val url: String)

/** The Browser app's history and its open tabs, kept on the device. (Bookmarks are shared with the Store's browser.) */
object BrowserData {
    private const val MAX_HISTORY = 500
    private fun file(c: Context) = File(c.filesDir, "browser_history.json")
    private fun prefs(c: Context) = c.getSharedPreferences("qita_browser", Context.MODE_PRIVATE)

    fun history(c: Context): List<HistoryEntry> = runCatching {
        val arr = JSONArray(file(c).takeIf { it.exists() }?.readText() ?: "[]")
        (0 until arr.length()).map { val o = arr.getJSONObject(it); HistoryEntry(o.optString("t"), o.getString("u"), o.optLong("at")) }
    }.getOrDefault(emptyList())

    private fun save(c: Context, list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.take(MAX_HISTORY).forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url).put("at", it.time)) }
        file(c).writeText(arr.toString())
    }

    /** Adds a visited page at the top; a page visited again moves to the top. Pages that are not real (blank, data) are skipped. */
    @Synchronized
    fun addHistory(c: Context, title: String, url: String) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
        val list = history(c).toMutableList()
        // A page appears once, at its latest visit.
        list.removeAll { it.url == url }
        list.add(0, HistoryEntry(title.ifBlank { url }, url, System.currentTimeMillis()))
        save(c, list)
    }

    @Synchronized
    fun removeHistory(c: Context, url: String) { save(c, history(c).filter { it.url != url }) }

    @Synchronized
    fun clearHistory(c: Context) { file(c).delete() }

    fun loadTabs(c: Context): Pair<List<SavedTab>, Int> = runCatching {
        val arr = JSONArray(prefs(c).getString("tabs", "[]"))
        val tabs = (0 until arr.length()).map { val o = arr.getJSONObject(it); SavedTab(o.getString("id"), o.optString("t"), o.optString("u")) }
        tabs to prefs(c).getInt("active", 0).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    }.getOrDefault(emptyList<SavedTab>() to 0)

    fun saveTabs(c: Context, tabs: List<SavedTab>, active: Int) {
        val arr = JSONArray()
        tabs.forEach { arr.put(JSONObject().put("id", it.id).put("t", it.title).put("u", it.url)) }
        prefs(c).edit().putString("tabs", arr.toString()).putInt("active", active).apply()
    }
}
