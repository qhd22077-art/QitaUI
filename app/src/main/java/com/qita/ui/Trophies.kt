package com.qita.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Calendar

/** How hard a trophy is: bronze, silver, gold, and the platinum for having them all. */
enum class Tier(val label: String, val points: Int) {
    BRONZE("Bronze", 15), SILVER("Silver", 30), GOLD("Gold", 90), PLATINUM("Platinum", 180),
}

/** A trophy the launcher gives for using it. A [secret] one shows no name or hint until it is earned. */
class Trophy(val id: String, val title: String, val text: String, val tier: Tier, val secret: Boolean = false)

/**
 * The launcher's own trophies, in the spirit of the Vita's: small goals met just by using the launcher. Earned ones are kept with the
 * time they were earned, a few counters are kept for the "do it N times" ones, and a banner slides down when one is earned.
 * Calls are cheap and safe from anywhere once [init] has run (the home screen does that first).
 */
object Trophies {
    val ALL: List<Trophy> = listOf(
        Trophy("first", "First steps", "Start an app or a game from the launcher.", Tier.BRONZE),
        Trophy("launch10", "Regular", "Start 10 apps or games.", Tier.BRONZE),
        Trophy("launch100", "Creature of habit", "Start 100 apps or games.", Tier.SILVER),
        Trophy("launch500", "Can't stop", "Start 500 apps or games.", Tier.GOLD),
        Trophy("game", "Player one", "Start a game from the Games bubble.", Tier.BRONZE),
        Trophy("flash", "Old school", "Play a Flash game.", Tier.BRONZE),
        Trophy("library10", "Collector", "Have 10 games in your library.", Tier.SILVER),
        Trophy("folder", "Tidy up", "Make a folder on the home screen.", Tier.BRONZE),
        Trophy("customise", "Dressed up", "Customise a system bubble.", Tier.BRONZE),
        Trophy("theme", "New look", "Import or apply a theme.", Tier.BRONZE),
        Trophy("wallpaper", "Backdrop", "Choose your own wallpaper.", Tier.BRONZE),
        Trophy("download", "Delivery", "Finish a download.", Tier.BRONZE),
        Trophy("sound", "Sound designer", "Give an interface sound a clip of your own.", Tier.BRONZE),
        Trophy("store", "Window shopping", "Open the Store.", Tier.BRONZE),
        Trophy("browser", "Surfer", "Open the Browser.", Tier.BRONZE),
        Trophy("pad", "Hands on", "Use a gamepad.", Tier.BRONZE),
        Trophy("notes", "Journal", "Write a note on a game.", Tier.BRONZE),
        Trophy("tags10", "Librarian", "Give tags to 5 games.", Tier.SILVER),
        Trophy("shots", "Photographer", "Add your own screenshot to a game.", Tier.BRONZE),
        Trophy("ra", "Achiever", "Log in to RetroAchievements.", Tier.SILVER),
        Trophy("week", "Moved in", "Use the launcher on 7 different days.", Tier.SILVER),
        Trophy("month", "Resident", "Use the launcher on 30 different days.", Tier.GOLD),
        Trophy("marathon", "Marathon", "Play for 10 hours in total.", Tier.GOLD),
        Trophy("night", "Night owl", "Open the launcher between 3 and 5 in the morning.", Tier.SILVER, secret = true),
        Trophy("reset", "Fresh start", "Reset a settings page to its defaults.", Tier.BRONZE, secret = true),
        Trophy("platinum", "Full set", "Earn every other trophy.", Tier.PLATINUM),
    )

    /** Goes up whenever a trophy is earned; screens read it to redraw. */
    var rev by mutableIntStateOf(0)
        private set

    /** The trophy whose banner is on screen (null when none). [lastShown] stays for the banner's slide-out. */
    var shown by mutableStateOf<Trophy?>(null)
        private set
    var lastShown: Trophy? = null
        private set

    private var ctx: Context? = null
    private var earned = HashMap<String, Long>()
    private val queue = ArrayDeque<Trophy>()

    private fun prefs(c: Context) = c.getSharedPreferences("qita_trophies", Context.MODE_PRIVATE)

    @Synchronized fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        runCatching {
            val o = JSONObject(prefs(c).getString("earned", "{}").orEmpty())
            for (k in o.keys()) earned[k] = o.getLong(k)
        }
    }

    fun earnedAt(id: String): Long? = earned[id]

    fun isEarned(id: String): Boolean = id in earned

    val earnedCount: Int get() = ALL.count { it.id in earned }

    val points: Int get() = ALL.filter { it.id in earned }.sumOf { it.tier.points }

    val totalPoints: Int get() = ALL.sumOf { it.tier.points }

    /** The level: one for every 90 points. */
    val level: Int get() = 1 + points / 90

    /** Earns [id] if it has not been earned yet: kept, announced with a banner and a sound. */
    @Synchronized fun award(id: String) {
        val c = ctx ?: return
        if (id in earned) return
        val t = ALL.firstOrNull { it.id == id } ?: return
        earned[id] = System.currentTimeMillis()
        val o = JSONObject()
        for ((k, v) in earned) o.put(k, v)
        prefs(c).edit().putString("earned", o.toString()).apply()
        rev++
        queue.addLast(t)
        if (shown == null) next()
        Sounds.play(Sound.TROPHY)
        // Everything but the platinum earns the platinum.
        if (t.tier != Tier.PLATINUM && ALL.filter { it.tier != Tier.PLATINUM }.all { it.id in earned }) award("platinum")
    }

    /** Shows the next waiting banner, if any. The banner calls this after its time is up (and a short gap). */
    @Synchronized fun next() {
        val t = queue.removeFirstOrNull()
        if (t != null) lastShown = t
        shown = t
    }

    @Synchronized fun hasQueued(): Boolean = queue.isNotEmpty()

    /** The banner's time is up. */
    fun finishShown() {
        shown = null
    }

    // --- The things the launcher reports ---

    private fun count(key: String, add: Int = 1): Int {
        val c = ctx ?: return 0
        val p = prefs(c)
        val n = p.getInt("n_$key", 0) + add
        p.edit().putInt("n_$key", n).apply()
        return n
    }

    /** An app or game was started. */
    fun launched(game: Boolean, flash: Boolean) {
        val n = count("launch")
        award("first")
        if (n >= 10) award("launch10")
        if (n >= 100) award("launch100")
        if (n >= 500) award("launch500")
        if (game) award("game")
        if (flash) award("flash")
    }

    /** The launcher came to the front: counts the day, and the night owl hours. */
    fun opened() {
        val c = ctx ?: return
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) in 3..4) award("night")
        val day = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        val p = prefs(c)
        val days = p.getStringSet("days", emptySet()).orEmpty()
        if (day in days) return
        val all = days + day
        p.edit().putStringSet("days", all).apply()
        if (all.size >= 7) award("week")
        if (all.size >= 30) award("month")
    }

    /** The number of games in the library, after a scan. */
    fun library(games: Int) {
        if (games >= 10) award("library10")
    }

    /** The total play time so far, in milliseconds. */
    fun played(totalMs: Long) {
        if (totalMs >= 10L * 3600_000L) award("marathon")
    }

    /** The number of games that have tags. */
    fun tagged(games: Int) {
        if (games >= 5) award("tags10")
    }
}
