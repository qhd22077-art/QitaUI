package com.qita.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** A launchable app shown as a bubble on the home screen. */
data class LaunchableApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val installTime: Long = 0L,
    val version: String = "",
    val category: Int = -1,
    val isSystem: Boolean = false,
    /** Average colour of the icon, used to colour its glossy sphere. */
    val tint: Color = Color(0xFF4A78D0),
    /** Set for the built-in bubbles (Settings, Store, Desktop), which are not real Android apps. */
    val action: SystemAction? = null,
)

object AppRepository {
    fun load(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                val info = runCatching { pm.getPackageInfo(it.activityInfo.packageName, 0) }.getOrNull()
                val bitmap = it.loadIcon(pm).toBitmap(192)
                LaunchableApp(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = bitmap.asImageBitmap(),
                    tint = averageColor(bitmap),
                    installTime = info?.firstInstallTime ?: 0L,
                    version = info?.versionName.orEmpty(),
                    category = it.activityInfo.applicationInfo.category,
                    isSystem = it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
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

    fun showInfo(context: Context, app: LaunchableApp) {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun uninstall(context: Context, app: LaunchableApp) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * Ends the app's background processes. Android does not let a launcher force-stop an app
     * that is in the foreground, so this takes effect once the app is no longer on screen.
     */
    fun close(context: Context, app: LaunchableApp) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(app.packageName)
    }

    /** Mean colour of the opaque pixels, sampled on a coarse grid. */
    private fun averageColor(bmp: Bitmap): Color {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        val step = maxOf(1, bmp.width / 16)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val p = bmp.getPixel(x, y)
                if ((p ushr 24) and 0xFF > 200) {
                    r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
                }
                x += step
            }
            y += step
        }
        if (n == 0L) return Color(0xFF4A78D0)
        return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
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
