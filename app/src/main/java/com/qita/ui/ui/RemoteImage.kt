package com.qita.ui.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Downloads small pictures (icons) and keeps the last few in memory. */
object RemoteImages {
    private val cache = LruCache<String, Bitmap>(48)
    private val failed = HashSet<String>()

    fun cached(url: String): Bitmap? = cache.get(url)

    /** Fetches and shrinks the picture at [url] (to at most 256 px). Null if it cannot be loaded. Blocks. */
    fun fetch(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        if (url in failed) return null
        val bmp = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "QitaUI")
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 512) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
        if (bmp != null) cache.put(url, bmp) else failed.add(url)
        return bmp
    }
}

/** A picture from the web, cropped to fill its box; [placeholder] shows until it arrives (or if it never does). */
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, placeholder: @Composable () -> Unit) {
    var bmp by remember(url) { mutableStateOf(url?.let { RemoteImages.cached(it) }) }
    LaunchedEffect(url) {
        if (url != null && bmp == null) bmp = withContext(Dispatchers.IO) { RemoteImages.fetch(url) }
    }
    Box(modifier) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else placeholder()
    }
}
