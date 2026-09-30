package com.qita.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Pictures for the Store's banner strip, which is 282 x 108 (saved at twice that):
 *  - in-game screenshots and title screens of the user's own games, from the libretro thumbnail server (the same place the cover
 *    art comes from);
 *  - pictures from a folder the user chooses, a different handful each week (or when asked to shuffle).
 */
object StoreBanners {
    private const val W = 564
    private const val H = 216

    private fun prefs(c: Context) = c.getSharedPreferences("qita_store", Context.MODE_PRIVATE)
    private fun dir(c: Context, name: String) = File(c.filesDir, name).apply { mkdirs() }

    fun snapFile(c: Context, id: String) = File(dir(c, "banner_snaps"), "$id.jpg")

    /** Cuts the middle of [src] to the banner's shape and size. */
    private fun fit(src: Bitmap): Bitmap {
        val ratio = W.toFloat() / H
        val wide = src.width.toFloat() / src.height > ratio
        val cw = if (wide) (src.height * ratio).toInt() else src.width
        val ch = if (wide) src.height else (src.width / ratio).toInt()
        val part = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw.coerceAtLeast(1), ch.coerceAtLeast(1))
        return Bitmap.createScaledBitmap(part, W, H, true)
    }

    private fun save(bmp: Bitmap, file: File) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ---- Screenshots of the user's own games ----

    /** Downloads a screenshot (or else the title screen) of [game]. Blocks. Returns whether it worked. */
    fun fetchSnap(context: Context, game: Game): Boolean = runCatching {
        val system = systemById(game.systemId) ?: return false
        val folder = system.thumbs ?: return false
        val name = game.raw.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
        for (kind in listOf("Named_Snaps", "Named_Titles")) {
            val conn = URL("https://thumbnails.libretro.com/${enc(folder)}/$kind/${enc(name)}.png").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            if (conn.responseCode != 200) { conn.disconnect(); continue }
            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            conn.disconnect()
            if (bmp != null) { save(fit(bmp), snapFile(context, game.id)); return true }
        }
        false
    }.getOrDefault(false)

    /** Gets screenshots for up to [limit] games that have none yet. Returns how many it got. A run that got nothing (offline) is not remembered. */
    suspend fun ensureSnaps(context: Context, limit: Int = 8): Int = withContext(Dispatchers.IO) {
        val tried = prefs(context).getStringSet("snap_tried", emptySet()).orEmpty().toMutableSet()
        val todo = GameLibrary.games(context).filter { !snapFile(context, it.id).exists() && it.id !in tried }.take(limit)
        var got = 0
        for (chunk in todo.chunked(3)) {
            val results = coroutineScope { chunk.map { g -> async { fetchSnap(context, g) } }.awaitAll() }
            got += results.count { it }
            tried.addAll(chunk.map { it.id })
        }
        if (got > 0) prefs(context).edit().putStringSet("snap_tried", tried).apply()
        got
    }

    // ---- Pictures from the user's folder ----

    fun folder(c: Context): Uri? = prefs(c).getString("pic_folder", null)?.let { Uri.parse(it) }

    fun setFolder(c: Context, uri: Uri) {
        runCatching { c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs(c).edit().putString("pic_folder", uri.toString()).putInt("pic_shuffle", 0).apply()
    }

    fun clearFolder(c: Context) {
        prefs(c).edit().remove("pic_folder").apply()
        dir(c, "banner_pics").listFiles()?.forEach { it.delete() }
    }

    /** Another handful of pictures than the ones showing now. */
    fun shuffle(c: Context) {
        prefs(c).edit().putInt("pic_shuffle", prefs(c).getInt("pic_shuffle", 0) + 1).apply()
    }

    private fun images(c: Context, tree: Uri): List<Pair<String, Uri>> = runCatching {
        val parent = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val out = ArrayList<Pair<String, Uri>>()
        c.contentResolver.query(children, cols, null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                if ((cur.getString(2) ?: "").startsWith("image/")) {
                    out.add((cur.getString(1) ?: "") to DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0)))
                }
            }
        }
        out
    }.getOrDefault(emptyList())

    /** How many pictures the chosen folder holds. Blocks. */
    fun count(c: Context): Int = folder(c)?.let { images(c, it).size } ?: 0

    private fun prepare(c: Context, uri: Uri): File? = runCatching {
        val file = File(dir(c, "banner_pics"), "${uri.toString().hashCode()}.jpg")
        if (file.exists()) return file
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > W * 2) sample *= 2
        val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        save(fit(bmp), file)
        file
    }.getOrNull()

    /**
     * Up to [count] pictures from the folder, as files ready to show. The choice is the same all week and changes by itself the
     * next week, or at once after [shuffle]. Blocks.
     */
    fun picks(c: Context, count: Int = 6): List<String> {
        val tree = folder(c) ?: return emptyList()
        val all = images(c, tree).sortedBy { it.first.lowercase() }
        if (all.isEmpty()) return emptyList()
        val week = System.currentTimeMillis() / 604_800_000L
        val seed = week * 31 + prefs(c).getInt("pic_shuffle", 0)
        val files = all.shuffled(Random(seed)).take(count).mapNotNull { prepare(c, it.second) }
        // Pictures no longer chosen are not kept.
        val keep = files.map { it.name }.toSet()
        dir(c, "banner_pics").listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        return files.map { it.path }
    }
}

/**
 * How the banner strip looks and what it shows.
 * [style]: 0 artwork (pictures, glare, shadows), 1 classic (plain colour cards with letters), 2 no banners.
 */
data class BannerCfg(
    val style: Int = 0,
    val vita: Boolean = true,
    val games: Boolean = true,
    val pics: Boolean = true,
    val emus: Boolean = true,
    val auto: Boolean = true,
    val seconds: Int = 4,
    val glare: Boolean = true,
    val picCount: Int = 6,
) {
    companion object {
        private fun prefs(c: Context) = c.getSharedPreferences("qita_store", Context.MODE_PRIVATE)
        fun load(c: Context): BannerCfg = prefs(c).let { p ->
            BannerCfg(
                p.getInt("bn_style", 0), p.getBoolean("bn_vita", true), p.getBoolean("bn_games", true), p.getBoolean("bn_pics", true),
                p.getBoolean("bn_emus", true), p.getBoolean("bn_auto", true), p.getInt("bn_seconds", 4), p.getBoolean("bn_glare", true), p.getInt("bn_count", 6),
            )
        }
        fun save(c: Context, b: BannerCfg) {
            prefs(c).edit().putInt("bn_style", b.style).putBoolean("bn_vita", b.vita).putBoolean("bn_games", b.games).putBoolean("bn_pics", b.pics)
                .putBoolean("bn_emus", b.emus).putBoolean("bn_auto", b.auto).putInt("bn_seconds", b.seconds).putBoolean("bn_glare", b.glare).putInt("bn_count", b.picCount).apply()
        }
    }
}
