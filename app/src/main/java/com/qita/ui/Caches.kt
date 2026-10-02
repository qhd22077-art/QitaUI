package com.qita.ui

import android.content.Context
import java.io.File

/**
 * The things the launcher keeps only to save a download next time: cover name lists, the Flash and Vita game lists, RetroAchievements
 * game lists and badge pictures, and temporary files. Clearing them frees space and loses nothing of yours; they are fetched again when needed.
 */
object Caches {
    private fun targets(c: Context): List<File> = buildList {
        add(File(c.filesDir, "cover_index"))
        add(File(c.filesDir, "flash_catalogue.json"))
        add(File(c.filesDir, "vitadb.json"))
        File(c.filesDir, "ra").listFiles()?.filter { it.name.startsWith("games_") || it.name == "badges" }?.let { addAll(it) }
        // The cache folder, except the interface sounds (made once and loaded straight away).
        c.cacheDir.listFiles()?.filter { it.name != "sounds" }?.let { addAll(it) }
    }

    private fun size(f: File): Long = if (f.isDirectory) f.listFiles()?.sumOf { size(it) } ?: 0L else f.length()

    fun bytes(c: Context): Long = targets(c).sumOf { size(it) }

    fun clear(c: Context) {
        targets(c).forEach { runCatching { it.deleteRecursively() } }
    }

    fun format(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / 1048576.0)
    }
}

/** The last things typed into the Store's search, newest first, so they can be tapped again. */
object SearchHistory {
    private fun prefs(c: Context) = c.getSharedPreferences("qita_search_history", Context.MODE_PRIVATE)

    fun get(c: Context): List<String> = prefs(c).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun add(c: Context, query: String): List<String> {
        val list = (listOf(query) + get(c).filter { !it.equals(query, true) }).take(8)
        prefs(c).edit().putString("list", list.joinToString("\n")).apply()
        return list
    }

    fun clear(c: Context) { prefs(c).edit().clear().apply() }
}
