package com.qita.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** A launchable app shown as a bubble on the home screen. */
data class LaunchableApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
)

object AppRepository {
    fun load(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm).toBitmap(128).asImageBitmap(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, app: LaunchableApp) {
        context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
    }

    /**
     * Ends the app's background processes. Android does not let a launcher force-stop an app
     * that is in the foreground, so this takes effect once the app is no longer on screen.
     */
    fun close(context: Context, app: LaunchableApp) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(app.packageName)
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null && bitmap.width == size) return bitmap
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bmp
    }
}
