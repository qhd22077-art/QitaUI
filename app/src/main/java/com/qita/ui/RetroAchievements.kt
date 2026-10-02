package com.qita.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One achievement of a game on RetroAchievements and whether the user has it. */
class RaAchievement(val id: Long, val title: String, val description: String, val points: Int, val badge: String, val earned: Boolean)

/** A game's achievements and the user's progress in them. */
class RaGame(val id: Long, val title: String, val consoleName: String, val achievements: List<RaAchievement>) {
    val earnedCount: Int get() = achievements.count { it.earned }
    val earnedPoints: Int get() = achievements.filter { it.earned }.sumOf { it.points }
    val totalPoints: Int get() = achievements.sumOf { it.points }
}

/** The user's profile: points, rank and the games played lately. */
class RaProfile(val user: String, val points: Long, val rank: Long, val recent: List<String>)

/**
 * RetroAchievements (retroachievements.org): shows a game's achievements and what the user has earned. Unlocking still happens in
 * the emulator (RetroArch and others log in on their own). This talks to the site's Web API with the user's name and Web API key
 * (from their profile page on the site), matches a launcher game to a site game by its title, and keeps the last answers on the
 * device so they still show offline. Written from the site's public API description; it could not be tried from where it was written.
 */
object RetroAchievements {
    /** Goes up when the account or a cached answer changes. */
    var rev by mutableIntStateOf(0)
        private set

    private const val BASE = "https://retroachievements.org/API/"
    private const val WEEK = 7L * 24 * 3600_000

    /** The site's console numbers for the consoles it supports, by the launcher's console ids. */
    private val CONSOLES = mapOf(
        "genesis" to 1, "n64" to 2, "snes" to 3, "gb" to 4, "gba" to 5, "gbc" to 6, "nes" to 7, "ps1" to 12,
        "gc" to 16, "nds" to 18, "wii" to 19, "ps2" to 21, "dreamcast" to 40, "psp" to 41,
    )

    fun supported(systemId: String): Boolean = systemId in CONSOLES

    private fun prefs(c: Context) = c.getSharedPreferences("qita_ra", Context.MODE_PRIVATE)

    fun user(c: Context): String = prefs(c).getString("user", "").orEmpty()

    fun key(c: Context): String = prefs(c).getString("key", "").orEmpty()

    fun configured(c: Context): Boolean = user(c).isNotBlank() && key(c).isNotBlank()

    fun setUser(c: Context, v: String) { prefs(c).edit().putString("user", v.trim()).apply(); rev++ }

    fun setKey(c: Context, v: String) { prefs(c).edit().putString("key", v.trim()).apply(); rev++ }

    /** Forgets the account and everything kept from it. */
    fun signOut(c: Context) {
        prefs(c).edit().clear().apply()
        File(c.filesDir, "ra").deleteRecursively()
        rev++
    }

    private fun dir(c: Context) = File(c.filesDir, "ra").apply { mkdirs() }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Asks the site; returns the body, or null on any trouble. Blocks. */
    private fun call(c: Context, endpoint: String, vararg params: Pair<String, String>): String? = runCatching {
        val q = (listOf("z" to user(c), "y" to key(c)) + params).joinToString("&") { "${it.first}=${enc(it.second)}" }
        val conn = URL("$BASE$endpoint?$q").openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "QitaUI")
        try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** The result of checking the account: the profile, or why it failed. */
    class Check(val profile: RaProfile?, val error: String?)

    /** Looks up the user's own profile. Blocks. A good answer is kept and earns the "Achiever" trophy. */
    fun check(c: Context): Check {
        if (!configured(c)) return Check(null, "Enter your user name and Web API key first.")
        val body = call(c, "API_GetUserSummary.php", "u" to user(c), "g" to "5", "a" to "0")
            ?: return Check(null, "Could not reach the site, or it did not accept the key.")
        return runCatching {
            val o = JSONObject(body)
            // A wrong key answers with a message instead of a profile.
            if (!o.has("TotalPoints") && !o.has("Rank")) return Check(null, o.optString("message", o.optString("Error", "The site did not accept this name and key.")))
            val recent = o.optJSONArray("RecentlyPlayed")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("Title") } }.orEmpty().filter { it.isNotBlank() }
            val profile = RaProfile(user(c), o.optLong("TotalPoints"), o.optLong("Rank"), recent)
            File(dir(c), "profile.json").writeText(body)
            Trophies.award("ra")
            rev++
            Check(profile, null)
        }.getOrElse { Check(null, "The site's answer could not be read.") }
    }

    /** The last profile kept on the device, if any. */
    fun cachedProfile(c: Context): RaProfile? = runCatching {
        val o = JSONObject(File(dir(c), "profile.json").readText())
        val recent = o.optJSONArray("RecentlyPlayed")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("Title") } }.orEmpty().filter { it.isNotBlank() }
        RaProfile(user(c), o.optLong("TotalPoints"), o.optLong("Rank"), recent)
    }.getOrNull()

    /** The site's game number for [game], found by title among the console's games that have achievements, or null. Blocks. */
    private fun siteId(c: Context, game: Game): Long? {
        val console = CONSOLES[game.systemId] ?: return null
        val p = prefs(c)
        if (p.contains("map_${game.id}")) return p.getLong("map_${game.id}", 0L).takeIf { it > 0 }
        val list = File(dir(c), "games_$console.json")
        if (!list.exists() || System.currentTimeMillis() - list.lastModified() > WEEK) {
            // f=1: only the games that have achievements.
            val body = call(c, "API_GetGameList.php", "i" to console.toString(), "f" to "1")
            if (body != null && body.trimStart().startsWith("[")) list.writeText(body)
        }
        if (!list.exists()) return null
        val titles = HashMap<String, Long>()
        runCatching {
            val a = JSONArray(list.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                titles.putIfAbsent(o.optString("Title"), o.optLong("ID"))
            }
        }
        val match = CoverMatch.best(game.raw, titles.keys.toList())
        val id = match?.let { titles[it] }
        // Remembered either way, so a game with no match is not looked up again every time its page opens.
        p.edit().putLong("map_${game.id}", id ?: 0L).apply()
        return id
    }

    private fun parse(body: String): RaGame? = runCatching {
        val o = JSONObject(body)
        val ach = o.optJSONObject("Achievements") ?: return null
        val list = ach.keys().asSequence().map { k ->
            val a = ach.getJSONObject(k)
            RaAchievement(
                a.optLong("ID"), a.optString("Title"), a.optString("Description"), a.optInt("Points"), a.optString("BadgeName"),
                a.optString("DateEarned").isNotBlank() || a.optString("DateEarnedHardcore").isNotBlank(),
            )
        }.sortedWith(compareByDescending<RaAchievement> { it.earned }.thenBy { it.points }).toList()
        RaGame(o.optLong("ID"), o.optString("Title"), o.optString("ConsoleName"), list)
    }.getOrNull()

    /** The kept answer for [game], if there is one (works offline). */
    fun cached(c: Context, game: Game): RaGame? {
        val id = prefs(c).getLong("map_${game.id}", 0L)
        if (id <= 0) return null
        return runCatching { parse(File(dir(c), "game_$id.json").readText()) }.getOrNull()
    }

    /** Asks the site for [game]'s achievements and the user's progress and keeps the answer. Returns null if none. Blocks. */
    fun load(c: Context, game: Game): RaGame? {
        if (!configured(c) || !supported(game.systemId)) return null
        val id = siteId(c, game) ?: return null
        val body = call(c, "API_GetGameInfoAndUserProgress.php", "g" to id.toString(), "u" to user(c))
        if (body != null) {
            val parsed = parse(body)
            if (parsed != null) {
                File(dir(c), "game_$id.json").writeText(body)
                rev++
                return parsed
            }
        }
        return cached(c, game)
    }

    /** An achievement's badge picture, from the site or the copy kept on the device. Blocks. */
    fun badge(c: Context, name: String, earned: Boolean): Bitmap? {
        if (name.isBlank()) return null
        val file = File(File(dir(c), "badges").apply { mkdirs() }, "$name${if (earned) "" else "_lock"}.png")
        if (!file.exists()) runCatching {
            val conn = URL("https://media.retroachievements.org/Badge/$name${if (earned) "" else "_lock"}.png").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            try {
                if (conn.responseCode == 200) file.writeBytes(conn.inputStream.use { it.readBytes() })
            } finally {
                conn.disconnect()
            }
        }
        return if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
    }
}
