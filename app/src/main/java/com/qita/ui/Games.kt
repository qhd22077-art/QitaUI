package com.qita.ui

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.qita.ui.ui.Ball3D
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** An emulator app the launcher knows how to find. [packages] lists every package name it is published under. */
class Emulator(val id: String, val name: String, val packages: List<String>)

/** A console or platform. [exts] are the file types of its games, [aliases] the folder names that mean it. */
class GameSystem(
    val id: String,
    val name: String,
    val short: String,
    val exts: Set<String>,
    val aliases: List<String>,
    /** The folder name on thumbnails.libretro.com that holds this system's cover art, if it has one. */
    val thumbs: String?,
    /** The RetroArch core file that plays it, if RetroArch can. */
    val core: String?,
    /** Emulators that can play it, best first (ids from [EMULATORS]). */
    val emulators: List<String>,
    val color: Long,
)

/** A game file found in a folder the user added. [uri] is its document URI, [path] its file path if that could be worked out. */
class Game(
    val id: String,
    val title: String,
    /** The file name without its extension, which is also the name the cover art is stored under online. */
    val raw: String,
    val systemId: String,
    val uri: String,
    val path: String?,
    val ext: String,
)

/** A folder the user pointed the launcher at; [systemId] is a system id, or "auto" to tell by file type and folder names. */
data class GameFolder(val uri: String, val systemId: String, val label: String = "")

val EMULATORS: List<Emulator> = listOf(
    Emulator("retroarch", "RetroArch", listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32")),
    Emulator("ppsspp", "PPSSPP", listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")),
    Emulator("dolphin", "Dolphin", listOf("org.dolphinemu.dolphinemu")),
    Emulator("dolphinmmjr", "Dolphin MMJR", listOf("org.mm.jr", "org.dolphinemu.mmjr")),
    Emulator("vita3k", "Vita3K", listOf("org.vita3k.emulator", "org.vita3k.emulator.ikhoeyZX")),
    Emulator("nethersx2", "NetherSX2 / AetherSX2", listOf("xyz.aethersx2.android")),
    Emulator("duckstation", "DuckStation", listOf("com.github.stenzek.duckstation")),
    Emulator("citra", "Citra / Lime3DS / Azahar", listOf("org.citra.citra_emu", "org.citra.citra_emu.canary", "io.github.lime3ds.android", "org.azahar_emu.azahar", "io.github.borked3ds.android")),
    Emulator("switch", "Switch (Eden, Yuzu, Skyline)", listOf("dev.eden.eden_emulator", "dev.legacy.eden_emulator", "dev.eden.eden_emu", "org.yuzu.yuzu_emu", "org.sudachi.sudachi_emu", "dev.suyu.suyu_emu", "skyline.emu")),
    Emulator("kenjinx", "Kenji-NX", listOf("org.kenjinx.android")),
    Emulator("flycast", "Flycast", listOf("com.flycast.emulator")),
    Emulator("melonds", "melonDS", listOf("me.magnum.melonds", "me.magnum.melondualds")),
    Emulator("drastic", "DraStic", listOf("com.dsemu.drastic")),
    Emulator("mupen", "Mupen64Plus FZ", listOf("org.mupen64plusae.v3.fzurita", "org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita.amazon")),
    Emulator("gamenative", "GameNative", listOf("app.gamenative")),
    Emulator("winlator", "Winlator", listOf("com.winlator.cmod", "com.winlator", "com.winlator.vanilla")),
)

val SYSTEMS: List<GameSystem> = listOf(
    GameSystem("nes", "Nintendo Entertainment System", "NES", setOf("nes", "unf", "unif"), listOf("nes", "famicom", "fc"), "Nintendo - Nintendo Entertainment System", "fceumm_libretro_android.so", listOf("retroarch"), 0xFFB03030),
    GameSystem("snes", "Super Nintendo", "SNES", setOf("sfc", "smc", "fig", "swc"), listOf("snes", "sfc", "superfamicom", "supernintendo"), "Nintendo - Super Nintendo Entertainment System", "snes9x_libretro_android.so", listOf("retroarch"), 0xFF6A4FB0),
    GameSystem("gb", "Game Boy", "GB", setOf("gb"), listOf("gb", "gameboy"), "Nintendo - Game Boy", "gambatte_libretro_android.so", listOf("retroarch"), 0xFF5E7A3A),
    GameSystem("gbc", "Game Boy Color", "GBC", setOf("gbc"), listOf("gbc", "gameboycolor"), "Nintendo - Game Boy Color", "gambatte_libretro_android.so", listOf("retroarch"), 0xFF8A4FA8),
    GameSystem("gba", "Game Boy Advance", "GBA", setOf("gba"), listOf("gba", "gameboyadvance"), "Nintendo - Game Boy Advance", "mgba_libretro_android.so", listOf("retroarch"), 0xFF3A4FA8),
    GameSystem("n64", "Nintendo 64", "N64", setOf("n64", "z64", "v64"), listOf("n64", "nintendo64"), "Nintendo - Nintendo 64", "mupen64plus_next_gles3_libretro_android.so", listOf("retroarch", "mupen"), 0xFF2E8A4F),
    GameSystem("nds", "Nintendo DS", "NDS", setOf("nds", "dsi"), listOf("nds", "ds", "nintendods"), "Nintendo - Nintendo DS", "melonds_libretro_android.so", listOf("melonds", "drastic", "retroarch"), 0xFF7A7F88),
    GameSystem("3ds", "Nintendo 3DS", "3DS", setOf("3ds", "cci", "cxi", "3dsx"), listOf("3ds", "nintendo3ds", "n3ds"), "Nintendo - Nintendo 3DS", null, listOf("citra"), 0xFFC03A3A),
    GameSystem("gc", "GameCube", "GC", setOf("gcm", "gcz", "rvz", "iso", "ciso"), listOf("gc", "gamecube", "ngc"), "Nintendo - GameCube", "dolphin_libretro_android.so", listOf("dolphin", "dolphinmmjr", "retroarch"), 0xFF5A3A9A),
    GameSystem("wii", "Wii", "WII", setOf("wbfs", "wad", "iso", "rvz"), listOf("wii"), "Nintendo - Wii", "dolphin_libretro_android.so", listOf("dolphin", "dolphinmmjr", "retroarch"), 0xFF3AA0C0),
    GameSystem("switch", "Nintendo Switch", "NSW", setOf("nsp", "xci", "nca"), listOf("switch", "nsw"), "Nintendo - Nintendo Switch", null, listOf("switch", "kenjinx"), 0xFFD03A3A),
    GameSystem("ps1", "PlayStation", "PS1", setOf("cue", "chd", "pbp", "bin", "iso", "img", "ecm", "mdf"), listOf("ps1", "psx", "psone", "playstation"), "Sony - PlayStation", "pcsx_rearmed_libretro_android.so", listOf("duckstation", "retroarch"), 0xFF6A6F78),
    GameSystem("ps2", "PlayStation 2", "PS2", setOf("iso", "chd", "cso", "gz", "bin"), listOf("ps2", "playstation2"), "Sony - PlayStation 2", null, listOf("nethersx2"), 0xFF2A4AA0),
    GameSystem("psp", "PlayStation Portable", "PSP", setOf("iso", "cso", "pbp", "elf"), listOf("psp", "playstationportable"), "Sony - PlayStation Portable", "ppsspp_libretro_android.so", listOf("ppsspp", "retroarch"), 0xFF1E3A70),
    // Each Vita game is a small text file named after the game that holds its title id (as in ES-DE), e.g. "Gravity Rush.psvita".
    GameSystem("psvita", "PlayStation Vita", "VITA", setOf("psvita"), listOf("psvita", "vita", "psv"), "Sony - PlayStation Vita", null, listOf("vita3k"), 0xFF1A5FB4),
    GameSystem("genesis", "Mega Drive / Genesis", "MD", setOf("md", "smd", "gen", "bin"), listOf("genesis", "megadrive", "md", "gen"), "Sega - Mega Drive - Genesis", "genesis_plus_gx_libretro_android.so", listOf("retroarch"), 0xFF2A2A30),
    GameSystem("dreamcast", "Dreamcast", "DC", setOf("cdi", "gdi", "chd"), listOf("dreamcast", "dc"), "Sega - Dreamcast", "flycast_libretro_android.so", listOf("flycast", "retroarch"), 0xFFD0702A),
    // PC games run through GameNative (.steam, .epic, .gog, .amazon, .pcgame files holding the game's id) or Winlator (.desktop shortcuts).
    // Flash games (.swf) are played inside the launcher (see FlashScreen), so no emulator is needed.
    GameSystem("flash", "Flash", "FLASH", setOf("swf"), listOf("flash", "swf", "flashgames"), null, null, emptyList(), 0xFFD0501E),
    GameSystem("pc", "PC (Windows)", "PC", setOf("steam", "epic", "gog", "amazon", "pcgame", "desktop"), listOf("pc", "windows", "steam", "gamenative", "winlator"), null, null, listOf("gamenative", "winlator"), 0xFF2A3A5A),
)

fun systemById(id: String): GameSystem? = SYSTEMS.firstOrNull { it.id == id }

/** The package name an emulator is installed under, or null if it is not on this device. */
fun installedPackage(context: Context, emulator: Emulator): String? =
    emulator.packages.firstOrNull { pkg -> runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false) }

/** Emulators from [EMULATORS] that are installed, with their package names. */
fun installedEmulators(context: Context): List<Pair<Emulator, String>> =
    EMULATORS.mapNotNull { e -> installedPackage(context, e)?.let { e to it } }

/** Games, the folders they come from and which emulator plays what, kept on the device. */
object GameLibrary {
    private fun prefs(c: Context) = c.getSharedPreferences("qita_games", Context.MODE_PRIVATE)
    private fun gamesFile(c: Context) = File(c.filesDir, "games.json")

    fun folders(c: Context): List<GameFolder> = runCatching {
        val arr = JSONArray(prefs(c).getString("folders", "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); GameFolder(o.getString("uri"), o.optString("system", "auto"), o.optString("label")) }
    }.getOrDefault(emptyList())

    fun saveFolders(c: Context, list: List<GameFolder>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("uri", it.uri).put("system", it.systemId).put("label", it.label)) }
        prefs(c).edit().putString("folders", arr.toString()).apply()
    }

    /** The package chosen to play [systemId], if the user chose one. */
    fun emulatorChoice(c: Context, systemId: String): String? = prefs(c).getString("emu_$systemId", null)

    fun setEmulatorChoice(c: Context, systemId: String, pkg: String?) {
        prefs(c).edit().apply { if (pkg == null) remove("emu_$systemId") else putString("emu_$systemId", pkg) }.apply()
    }

    fun games(c: Context): List<Game> = runCatching {
        val f = gamesFile(c)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Game(o.getString("id"), o.getString("title"), o.getString("raw"), o.getString("system"), o.getString("uri"), o.optString("path").ifEmpty { null }, o.optString("ext"))
        }
    }.getOrDefault(emptyList())

    fun saveGames(c: Context, list: List<Game>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("raw", it.raw).put("system", it.systemId).put("uri", it.uri).put("path", it.path ?: "").put("ext", it.ext))
        }
        gamesFile(c).writeText(arr.toString())
    }

    /** Removes one game from the library (its file is not touched). It comes back if the folder is scanned again. */
    fun remove(c: Context, id: String) {
        saveGames(c, games(c).filter { it.id != id })
        coverFile(c, id).delete()
    }

    /** Ids of games the launcher has already seen, so a game the user took off the home screen is not put back by the next scan. */
    fun known(c: Context): Set<String> = prefs(c).getStringSet("known_games", emptySet()).orEmpty().toSet()

    fun addKnown(c: Context, ids: Collection<String>) {
        prefs(c).edit().putStringSet("known_games", known(c) + ids).apply()
    }

    /** Games the user starred, to be listed first and under their own tab. */
    fun favourites(c: Context): Set<String> = prefs(c).getStringSet("fav_games", emptySet()).orEmpty().toSet()

    fun toggleFavourite(c: Context, id: String): Set<String> {
        val set = favourites(c).toMutableSet()
        if (!set.add(id)) set.remove(id)
        prefs(c).edit().putStringSet("fav_games", set).apply()
        return set
    }

    /** When each game was last started (game id to time in milliseconds). */
    fun lastPlayed(c: Context): Map<String, Long> = runCatching {
        val o = JSONObject(prefs(c).getString("played", "{}").orEmpty())
        o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())

    fun markPlayed(c: Context, id: String): Map<String, Long> {
        val map = lastPlayed(c) + (id to System.currentTimeMillis())
        prefs(c).edit().putString("played", JSONObject(map).toString()).apply()
        return map
    }

    fun coverFile(c: Context, id: String): File = File(File(c.filesDir, "covers").apply { mkdirs() }, "$id.png")

    /** Every game as a bubble, built off the main thread. */
    fun apps(c: Context): List<LaunchableApp> = games(c).map { gameApp(c, it) }.sortedBy { it.label.lowercase() }
}

/** Looks through the folders the user added for game files. Slow on big folders, so call it off the main thread. */
object GameScanner {
    fun scan(context: Context, folders: List<GameFolder>): List<Game> {
        val out = LinkedHashMap<String, Game>()
        for (f in folders) {
            val tree = runCatching { Uri.parse(f.uri) }.getOrNull() ?: continue
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
            val rootName = displayName(context, tree, rootId) ?: ""
            walk(context, tree, rootId, listOf(rootName), 0, f, out)
        }
        return out.values.toList()
    }

    /** The console a name (of a folder or a file) points to, from the names each console goes by, or null. */
    fun systemFromName(name: String): GameSystem? {
        val lower = name.lowercase()
        val tokens = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        val squashed = lower.replace(Regex("[^a-z0-9]"), "")
        return SYSTEMS.firstOrNull { s -> s.aliases.any { a -> a in tokens || squashed == a } || s.short.lowercase() in tokens }
    }

    /** What a scan of the folder [tree] suggests: the console (or null) and whether that is certain enough to fix the folder to it. Blocks. */
    class Detected(val system: GameSystem?, val sure: Boolean)

    fun detect(context: Context, tree: Uri): Detected = runCatching {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        systemFromName(displayName(context, tree, rootId) ?: "")?.let { return Detected(it, true) }
        val counts = HashMap<String, Int>()
        var files = 0
        fun visit(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
            val entries = ArrayList<Triple<String, String, String>>()
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) entries.add(Triple(c.getString(0) ?: continue, c.getString(1) ?: continue, c.getString(2) ?: ""))
            }
            for ((id, name, mime) in entries) {
                if (files > 400) return
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { if (depth < 2) visit(id, depth + 1); continue }
                val byExt = SYSTEMS.filter { name.substringAfterLast('.', "").lowercase() in it.exts }
                if (byExt.isEmpty()) continue
                files++
                val sys = if (byExt.size == 1) byExt[0] else systemFromName(name)
                if (sys != null) counts[sys.id] = (counts[sys.id] ?: 0) + 1
            }
        }
        visit(rootId, 0)
        val best = counts.maxByOrNull { it.value } ?: return Detected(null, false)
        val total = counts.values.sum()
        Detected(systemById(best.key), best.value >= 3 && best.value * 10 >= total * 9)
    }.getOrDefault(Detected(null, false))

    private fun displayName(context: Context, tree: Uri, docId: String): String? = runCatching {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun walk(context: Context, tree: Uri, docId: String, names: List<String>, depth: Int, folder: GameFolder, out: MutableMap<String, Game>) {
        if (depth > 4 || out.size > 6000) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        // Read the listing first, so the cursor is closed before going into sub-folders.
        val entries = ArrayList<Triple<String, String, String>>()
        runCatching {
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) entries.add(Triple(c.getString(0) ?: continue, c.getString(1) ?: continue, c.getString(2) ?: ""))
            }
        }
        for ((id, name, mime) in entries) {
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                walk(context, tree, id, names + name, depth + 1, folder, out)
                continue
            }
            val ext = name.substringAfterLast('.', "").lowercase()
            val system = resolve(folder.systemId, ext, names) ?: continue
            val base = name.substringBeforeLast('.')
            val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            val gid = sha1(docUri.toString()).take(12)
            out[gid] = Game(gid, cleanTitle(base), base, system.id, docUri.toString(), pathOf(id), ext)
        }
    }

    /** Which system a file belongs to: the folder's own choice, else its file type, else the names of the folders it is in. */
    private fun resolve(folderSystem: String, ext: String, names: List<String>): GameSystem? {
        if (folderSystem != "auto") {
            val s = systemById(folderSystem) ?: return null
            return if (ext in s.exts) s else null
        }
        val byExt = SYSTEMS.filter { ext in it.exts }
        if (byExt.isEmpty()) return null
        if (byExt.size == 1) return byExt[0]
        // Several systems share this file type (iso, bin, chd...): the nearest folder name that matches one of them decides.
        for (n in names.reversed()) {
            val tokens = n.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            for (s in byExt) if (s.aliases.any { a -> a in tokens || n.lowercase().replace(Regex("[^a-z0-9]"), "") == a }) return s
        }
        return null
    }

    /** The file path a storage document id stands for, when it is on internal storage or an SD card. */
    private fun pathOf(docId: String): String? {
        val volume = docId.substringBefore(':', "")
        val rest = docId.substringAfter(':', "")
        return when {
            volume.isEmpty() || rest.isEmpty() -> null
            volume == "primary" -> "/storage/emulated/0/$rest"
            volume.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) -> "/storage/$volume/$rest"
            else -> null
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** "Super Mario (USA) [!]" becomes "Super Mario". */
    fun cleanTitle(base: String): String {
        val t = base.replace(Regex("\\([^)]*\\)"), " ").replace(Regex("\\[[^]]*]"), " ").replace('_', ' ')
            .replace(Regex("\\s+"), " ").trim()
        return t.ifEmpty { base }
    }
}

/** Opens a game in the emulator that plays its system, the way other frontends (ES-DE, Daijisho) do: one explicit activity per emulator. */
object GameLauncher {
    /** The emulator (and its installed package) that will play [system]: the user's choice if it is installed, else the first that is. */
    fun emulatorFor(context: Context, system: GameSystem): Pair<Emulator, String>? {
        val chosen = GameLibrary.emulatorChoice(context, system.id)
        if (chosen != null) {
            EMULATORS.firstOrNull { chosen in it.packages }?.let { e ->
                if (runCatching { context.packageManager.getPackageInfo(chosen, 0); true }.getOrDefault(false)) return e to chosen
            }
        }
        for (id in system.emulators) {
            val e = EMULATORS.firstOrNull { it.id == id } ?: continue
            installedPackage(context, e)?.let { return e to it }
        }
        return null
    }

    /** The first line of a game's file, for systems where the file only holds an id (Vita title ids, GameNative app ids). */
    private fun firstLine(context: Context, uri: Uri): String =
        runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLine() }?.trim().orEmpty() }.getOrDefault("")

    /** A launch for each activity name the emulator is known under, or a message saying why the game cannot be started. */
    private fun intents(context: Context, emu: Emulator, pkg: String, system: GameSystem, game: Game): Pair<List<Intent>, String?> {
        val data = Uri.parse(game.uri)
        val uriText = game.uri
        val clear = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        fun make(
            classes: List<String>,
            action: String? = null,
            category: String? = null,
            asData: Boolean = false,
            flags: Int = 0,
            extras: Intent.() -> Unit = {},
        ): Pair<List<Intent>, String?> = classes.map { cls ->
            Intent().also { i ->
                i.component = ComponentName(pkg, cls)
                if (action != null) i.action = action
                if (category != null) i.addCategory(category)
                if (asData) i.data = data
                i.addFlags(flags)
                i.extras()
            }
        } to null
        return when (emu.id) {
            "retroarch" -> {
                val core = system.core ?: return emptyList<Intent>() to "RetroArch has no core set for ${system.name}"
                val path = game.path ?: return emptyList<Intent>() to "RetroArch needs the game on internal storage or an SD card"
                make(listOf("com.retroarch.browser.retroactivity.RetroActivityFuture")) {
                    putExtra("CONFIGFILE", "/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg")
                    putExtra("LIBRETRO", "/data/data/$pkg/cores/$core")
                    putExtra("ROM", path)
                }
            }
            "ppsspp" -> make(listOf("org.ppsspp.ppsspp.PpssppActivity"), Intent.ACTION_VIEW, Intent.CATEGORY_DEFAULT, asData = true)
            "dolphin" -> make(listOf("org.dolphinemu.dolphinemu.ui.main.TvMainActivity"), Intent.ACTION_MAIN, "android.intent.category.LEANBACK_LAUNCHER") { putExtra("AutoStartFile", uriText) }
            "dolphinmmjr" -> make(listOf("org.dolphinemu.dolphinemu.ui.main.MainActivity"), Intent.ACTION_VIEW) { putExtra("AutoStartFile", uriText) }
            "nethersx2" -> make(listOf("xyz.aethersx2.android.EmulationActivity"), Intent.ACTION_MAIN, flags = clear) { putExtra("bootPath", uriText) }
            "duckstation" -> make(listOf("com.github.stenzek.duckstation.EmulationActivity"), flags = clear) {
                putExtra("resumeState", false)
                putExtra("bootPath", uriText)
            }
            "citra" -> make(listOf("$pkg.activities.EmulationActivity", "org.citra.citra_emu.activities.EmulationActivity"), asData = true, flags = clear)
            "switch" ->
                if (pkg == "skyline.emu") make(listOf("emu.skyline.EmulationActivity"), Intent.ACTION_VIEW, asData = true)
                else make(listOf("org.yuzu.yuzu_emu.activities.EmulationActivity"), "android.nfc.action.TECH_DISCOVERED", asData = true)
            "kenjinx" -> make(listOf("org.kenjinx.android.MainActivity"), "org.kenjinx.android.LAUNCH_GAME") { putExtra("bootPath", uriText) }
            "flycast" -> make(listOf("com.flycast.emulator.MainActivity", "com.reicast.emulator.MainActivity"), Intent.ACTION_VIEW, asData = true)
            "melonds" -> make(listOf("me.magnum.melonds.ui.emulator.EmulatorActivity"), "me.magnum.melonds.LAUNCH_ROM") { putExtra("uri", uriText) }
            "drastic" -> make(listOf("com.dsemu.drastic.DraSticActivity"), asData = true, flags = clear)
            "mupen" -> make(listOf("paulscode.android.mupen64plusae.SplashActivity"), Intent.ACTION_VIEW, asData = true)
            "vita3k" -> {
                val id = firstLine(context, data)
                if (id.isEmpty()) return emptyList<Intent>() to "The file ${game.raw}.psvita is empty. Put the game's title id (like PCSE00120) inside it"
                make(listOf("org.vita3k.emulator.Emulator")) { putExtra("AppStartParameters", arrayOf("-r", id)) }
            }
            "gamenative" -> {
                val source = when (game.ext) { "steam" -> "STEAM"; "epic" -> "EPIC"; "gog" -> "GOG"; "amazon" -> "AMAZON"; else -> "CUSTOM_GAME" }
                val id = firstLine(context, data).toIntOrNull()
                    ?: return emptyList<Intent>() to "The file ${game.raw}.${game.ext} should contain the game's id number"
                make(listOf("app.gamenative.MainActivity"), "app.gamenative.LAUNCH_GAME") {
                    putExtra("game_source", source)
                    putExtra("app_id", id)
                }
            }
            "winlator" -> {
                val path = game.path ?: return emptyList<Intent>() to "Winlator needs the shortcut on internal storage or an SD card"
                make(listOf("com.winlator.cmod.XServerDisplayActivity"), flags = clear) { putExtra("shortcut_path", path) }
            }
            else -> make(emptyList())
        }
    }

    /** Starts the game. Returns a message to show the user when something went wrong, or null when it started. */
    fun launch(context: Context, game: Game): String? {
        val system = systemById(game.systemId) ?: return "Unknown system"
        val (emu, pkg) = emulatorFor(context, system) ?: return "No emulator for ${system.name} is installed"
        val (list, problem) = intents(context, emu, pkg, system, game)
        if (problem != null) return problem
        val data = Uri.parse(game.uri)
        for (intent in list) {
            // The emulator gets read access to the game file through the intent, without needing its own storage permission.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("game", data)
            if (runCatching { context.startActivity(intent) }.isSuccess) return null
        }
        // None of the activity names worked (a version of the emulator that names it differently): open the emulator itself.
        val open = context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (open != null && runCatching { context.startActivity(open) }.isSuccess) "${emu.name} would not open this game directly, so it was opened instead"
        else "Could not start ${emu.name}"
    }
}

/** Cover art: downloaded from the libretro thumbnail server, or picked from the device, stored as one small PNG per game. */
object Covers {
    fun load(context: Context, id: String): Bitmap? {
        val f = GameLibrary.coverFile(context, id)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    /**
     * Downloads the cover art for [game]. Blocks, so call it off the main thread. Returns whether it worked.
     * The file name is tried as it is, then tidied (underscores, scene tags), then matched against the names the cover server
     * really has for that console (ignoring tags, word order of "Name, The" and small spelling differences), and last the
     * title-screen picture is used if there is no box art.
     */
    fun fetch(context: Context, game: Game): Boolean = runCatching {
        val system = systemById(game.systemId) ?: return false
        val folder = system.thumbs ?: return false
        fun get(kind: String, name: String): Bitmap? = runCatching {
            val safe = name.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
            val conn = URL("https://thumbnails.libretro.com/${enc(folder)}/$kind/${enc(safe)}.png").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            if (conn.responseCode != 200) { conn.disconnect(); return@runCatching null }
            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            conn.disconnect()
            bmp
        }.getOrNull()
        val tidy = CoverMatch.tidy(game.raw)
        var bmp = get("Named_Boxarts", game.raw) ?: if (tidy != game.raw) get("Named_Boxarts", tidy) else null
        if (bmp == null) {
            val match = CoverMatch.best(game.raw, CoverIndex.names(context, system))
            if (match != null) bmp = get("Named_Boxarts", match)
        }
        if (bmp == null) bmp = get("Named_Titles", game.raw) ?: get("Named_Titles", tidy)
        if (bmp == null) return false
        save(context, game.id, bmp)
        true
    }.getOrDefault(false)

    /** Uses a picture from the device as [id]'s cover. */
    fun pick(context: Context, id: String, uri: Uri): Boolean = runCatching {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return false
        save(context, id, bmp)
        true
    }.getOrDefault(false)

    fun save(context: Context, id: String, source: Bitmap) {
        val longest = maxOf(source.width, source.height)
        val bmp = if (longest > 512) Bitmap.createScaledBitmap(source, source.width * 512 / longest, source.height * 512 / longest, true) else source
        GameLibrary.coverFile(context, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** The cover names the cover server has for a console, fetched once a week, so a game can be matched when its file name differs a little. */
object CoverIndex {
    private val memory = HashMap<String, List<String>>()

    @Synchronized fun names(context: Context, system: GameSystem): List<String> {
        val folder = system.thumbs ?: return emptyList()
        memory[folder]?.let { return it }
        val dir = File(context.filesDir, "cover_index").apply { mkdirs() }
        val file = File(dir, "${folder.hashCode()}.txt")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 7L * 24 * 3600 * 1000
        if (fresh) return file.readLines().also { memory[folder] = it }
        val fetched = runCatching { listing(folder) }.getOrNull()
        if (!fetched.isNullOrEmpty()) {
            file.writeText(fetched.joinToString("\n"))
            memory[folder] = fetched
            return fetched
        }
        return if (file.exists()) file.readLines().also { memory[folder] = it } else emptyList()
    }

    private fun listing(folder: String): List<String> {
        val conn = URL("https://thumbnails.libretro.com/${URLEncoder.encode(folder, "UTF-8").replace("+", "%20")}/Named_Boxarts/").openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) QitaUI")
        if (conn.responseCode != 200) { conn.disconnect(); return emptyList() }
        // The page is a plain list of links, a megabyte or two for a big console; read it with a limit.
        val text = conn.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (out.size() < 16 * 1024 * 1024) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
            out.toString("UTF-8")
        }
        conn.disconnect()
        return Regex("href=\"([^\"]+?)\\.png\"").findAll(text).map {
            runCatching { java.net.URLDecoder.decode(it.groupValues[1].replace("+", "%2B"), "UTF-8") }.getOrDefault(it.groupValues[1])
        }.map { it.replace("&amp;", "&") }.distinct().toList()
    }
}

/** Matching a game's file name with the cover server's names. */
object CoverMatch {
    /** A file name with the clutter taken off: underscores, dots between words, scene tags in brackets and version marks. */
    fun tidy(raw: String): String = raw.replace('_', ' ').replace(Regex("\\[[^]]*]"), " ")
        .replace(Regex("(?i)\\bv\\d+(\\.\\d+)*\\b"), " ").replace(Regex("\\s+"), " ").trim().ifEmpty { raw }

    /** The comparable form: lowercase words with the tags in brackets dropped and "Name, The" turned into "The Name". */
    fun key(name: String): String {
        var t = name.replace('_', ' ').replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ").trim()
        Regex("^(.*?),\\s*(the|a|an)(\\s+-\\s+.*)?$", RegexOption.IGNORE_CASE).find(t)?.let { m ->
            t = m.groupValues[2] + " " + m.groupValues[1] + m.groupValues[3]
        }
        return t.lowercase().replace('&', ' ').replace(Regex("[^a-z0-9]+"), " ").replace(Regex("\\b(and)\\b"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /** Prefer the release most people want: USA, then World, then Europe; avoid demos and prototypes. */
    private fun rank(name: String): Int {
        val n = name.lowercase()
        var s = 0
        if ("(usa" in n) s += 4 else if ("(world" in n) s += 3 else if ("(europe" in n) s += 2 else if ("(japan" in n) s += 1
        if ("(demo" in n || "(beta" in n || "(proto" in n || "(sample" in n || "(unl" in n || "(pirate" in n) s -= 6
        if ("(rev" in n) s -= 0
        return s
    }

    /** The server name that best fits [raw], or null if nothing fits well enough. */
    fun best(raw: String, names: List<String>): String? {
        if (names.isEmpty()) return null
        val want = key(raw)
        if (want.isEmpty()) return null
        val exact = names.filter { key(it) == want }
        if (exact.isNotEmpty()) return exact.maxByOrNull { rank(it) }
        val wantWords = want.split(' ').toSet()
        var best: String? = null
        var bestScore = 0.0
        for (n in names) {
            val k = key(n)
            if (k.isEmpty() || k[0] != want[0]) continue
            val words = k.split(' ').toSet()
            val common = wantWords.intersect(words).size.toDouble()
            val score = common / (wantWords.size + words.size - common)
            val total = score + rank(n) * 0.01
            if (score >= 0.8 && total > bestScore) { bestScore = total; best = n }
        }
        return best
    }
}

/** The picture a game gets until it has cover art: the system's colour with its short name. */
private fun placeholder(system: GameSystem?, size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val base = ((system?.color ?: 0xFF4A78D0L)).toInt()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), lighter(base), darker(base), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    val text = Paint(Paint.ANTI_ALIAS_FLAG)
    text.color = 0xFFFFFFFF.toInt()
    text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    text.textAlign = Paint.Align.CENTER
    val label = system?.short ?: "GAME"
    text.textSize = size * (if (label.length > 3) 0.26f else 0.34f)
    canvas.drawText(label, size / 2f, size / 2f + text.textSize * 0.35f, text)
    return bmp
}

private fun lighter(c: Int): Int = blend(c, 0xFFFFFFFF.toInt(), 0.30f)
private fun darker(c: Int): Int = blend(c, 0xFF000000.toInt(), 0.35f)
private fun blend(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Centre-crops [source] to a square of [size] pixels, as bubble art. */
private fun squareArt(source: Bitmap, size: Int): Bitmap {
    val side = minOf(source.width, source.height)
    val x = (source.width - side) / 2
    val y = (source.height - side) / 2
    return Bitmap.createScaledBitmap(Bitmap.createBitmap(source, x, y, side, side), size, size, true)
}

private fun averageColor(bmp: Bitmap): Color {
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    val step = maxOf(1, bmp.width / 16)
    var y = 0
    while (y < bmp.height) {
        var x = 0
        while (x < bmp.width) {
            val p = bmp.getPixel(x, y)
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
            x += step
        }
        y += step
    }
    if (n == 0L) return Color(0xFF4A78D0)
    return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

/** A game as a bubble: its cover (or a coloured placeholder) cropped square. */
fun gameApp(context: Context, game: Game): LaunchableApp {
    val system = systemById(game.systemId)
    val art = squareArt(Covers.load(context, game.id) ?: placeholder(system, 256), 192)
    val tint = averageColor(art)
    return LaunchableApp(
        label = game.title,
        packageName = "qita.game.${game.id}",
        icon = art.asImageBitmap(),
        tint = tint,
        // The live shader draws the bubble on Android 13 and newer; the prerendered ball is only for older phones.
        ball = if (Ball3D.supported) null else SphereRenderer.render(art, 224, lightBody(tint)).asImageBitmap(),
        game = game,
    )
}
