package com.qita.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

class Photo(val id: Long, val uri: Uri, val album: String)

class Track(val id: Long, val uri: Uri, val title: String, val artist: String, val album: String, val ms: Long)

/**
 * The device's pictures and music, read from Android's media database (MediaStore). Needs the permission to read pictures or audio
 * (or the all-files switch the Folders screen asks for); nothing is copied or kept apart from small thumbnails in memory.
 */
object Media {
    /** The permission to ask for, for pictures or for audio. */
    fun permission(audio: Boolean): String = when {
        Build.VERSION.SDK_INT >= 33 -> if (audio) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_MEDIA_IMAGES
        else -> Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun allowed(c: Context, audio: Boolean): Boolean =
        c.checkSelfPermission(permission(audio)) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())

    /** Every picture, newest first. Blocks. */
    fun photos(c: Context): List<Photo> = runCatching {
        val out = ArrayList<Photo>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        c.contentResolver.query(
            uri, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cur ->
            val idCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cur.moveToNext()) {
                val id = cur.getLong(idCol)
                out.add(Photo(id, ContentUris.withAppendedId(uri, id), cur.getString(bucketCol).orEmpty().ifBlank { "Other" }))
            }
        }
        out
    }.getOrDefault(emptyList())

    /** Every music track, by title. Blocks. */
    fun tracks(c: Context): List<Track> = runCatching {
        val out = ArrayList<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        c.contentResolver.query(
            uri,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cur ->
            val idC = cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val tC = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val aC = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val alC = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dC = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cur.moveToNext()) {
                val id = cur.getLong(idC)
                val artist = cur.getString(aC).orEmpty().takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                out.add(Track(id, ContentUris.withAppendedId(uri, id), cur.getString(tC).orEmpty().ifBlank { "Untitled" }, artist, cur.getString(alC).orEmpty(), cur.getLong(dC)))
            }
        }
        out
    }.getOrDefault(emptyList())

    // Small pictures for the grid, kept in a cache of a few megabytes.
    private val thumbs = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().allocationByteCount
    }

    /** A thumbnail about [px] pixels wide. Blocks; call off the main thread. */
    fun thumb(c: Context, uri: Uri, px: Int): ImageBitmap? {
        val key = "$uri@$px"
        thumbs.get(key)?.let { return it }
        val bmp = runCatching {
            if (Build.VERSION.SDK_INT >= 29) c.contentResolver.loadThumbnail(uri, Size(px, px), null) else decode(c, uri, px)
        }.getOrNull() ?: decode(c, uri, px)
        return bmp?.asImageBitmap()?.also { thumbs.put(key, it) }
    }

    fun clearThumbs() = thumbs.evictAll()

    /** The picture upright and no longer than [maxPx] on its long side, or null. Blocks. */
    fun decode(c: Context, uri: Uri, maxPx: Int): Bitmap? = runCatching {
        val r = c.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val degrees = r.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (degrees == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
    }.getOrNull()
}
