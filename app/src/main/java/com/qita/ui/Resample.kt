package com.qita.ui

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/**
 * A proper (Lanczos) resampler. Android's own scaling is bilinear, which makes small pictures look soft and blocky when they are
 * enlarged; this is run once, off the main thread, on a picture that is about to be saved (a theme's backgrounds and icons).
 */
object Resample {
    private fun lanczos(x: Double): Double {
        val a = abs(x)
        if (a < 1e-9) return 1.0
        if (a >= 3.0) return 0.0
        val px = PI * a
        return 3.0 * sin(px) * sin(px / 3.0) / (px * px)
    }

    /** For each destination index, the first source index and the weights of the source pixels that make it. */
    private class Taps(val start: IntArray, val weights: Array<FloatArray>)

    private fun taps(srcLen: Int, dstLen: Int): Taps {
        val scale = srcLen.toDouble() / dstLen
        val filterScale = maxOf(1.0, scale)
        val support = 3.0 * filterScale
        val start = IntArray(dstLen)
        val weights = Array(dstLen) { FloatArray(0) }
        for (d in 0 until dstLen) {
            val center = (d + 0.5) * scale
            val lo = maxOf(0, floor(center - support).toInt())
            val hi = minOf(srcLen - 1, ceil(center + support).toInt())
            val raw = DoubleArray(hi - lo + 1)
            var sum = 0.0
            for (s in lo..hi) {
                val k = lanczos((s + 0.5 - center) / filterScale)
                raw[s - lo] = k
                sum += k
            }
            start[d] = lo
            weights[d] = FloatArray(raw.size) { i -> if (sum != 0.0) (raw[i] / sum).toFloat() else 0f }
        }
        return Taps(start, weights)
    }

    private fun clamp8(v: Float): Int = if (v < 0f) 0 else if (v > 255f) 255 else (v + 0.5f).toInt()

    /**
     * [src] at [dw] x [dh]. With [keepAlpha] false the picture is treated as opaque (a wallpaper); with it true transparent edges are
     * kept (an icon). [sharpen] (0 = none, about 0.4 = light) gives back some of the crispness enlarging takes away.
     */
    fun scaleTo(src: Bitmap, dw: Int, dh: Int, keepAlpha: Boolean = false, sharpen: Float = 0f): Bitmap {
        val sw = src.width
        val sh = src.height
        val px = IntArray(sw * sh)
        src.getPixels(px, 0, sw, 0, 0, sw, sh)
        var out = if (keepAlpha) withAlpha(px, sw, sh, dw, dh) else opaque(px, sw, sh, dw, dh)
        if (sharpen > 0f && !keepAlpha) out = unsharp(out, dw, dh, sharpen)
        val bmp = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, dw, 0, 0, dw, dh)
        return bmp
    }

    private fun opaque(px: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        val tx = taps(sw, dw)
        val ty = taps(sh, dh)
        val mid = IntArray(dw * sh)
        for (y in 0 until sh) {
            val row = y * sw
            for (x in 0 until dw) {
                val w = tx.weights[x]
                val s0 = tx.start[x]
                var r = 0f
                var g = 0f
                var b = 0f
                for (i in w.indices) {
                    val p = px[row + s0 + i]
                    val k = w[i]
                    r += k * ((p shr 16) and 0xFF)
                    g += k * ((p shr 8) and 0xFF)
                    b += k * (p and 0xFF)
                }
                mid[y * dw + x] = (0xFF shl 24) or (clamp8(r) shl 16) or (clamp8(g) shl 8) or clamp8(b)
            }
        }
        val out = IntArray(dw * dh)
        for (y in 0 until dh) {
            val w = ty.weights[y]
            val s0 = ty.start[y]
            for (x in 0 until dw) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (i in w.indices) {
                    val p = mid[(s0 + i) * dw + x]
                    val k = w[i]
                    r += k * ((p shr 16) and 0xFF)
                    g += k * ((p shr 8) and 0xFF)
                    b += k * (p and 0xFF)
                }
                out[y * dw + x] = (0xFF shl 24) or (clamp8(r) shl 16) or (clamp8(g) shl 8) or clamp8(b)
            }
        }
        return out
    }

    /** The same with transparency: colours are weighted by their alpha, so a transparent edge does not darken the picture. */
    private fun withAlpha(px: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        val tx = taps(sw, dw)
        val ty = taps(sh, dh)
        val ma = FloatArray(dw * sh)
        val mr = FloatArray(dw * sh)
        val mg = FloatArray(dw * sh)
        val mb = FloatArray(dw * sh)
        for (y in 0 until sh) {
            for (x in 0 until dw) {
                val w = tx.weights[x]
                val s0 = tx.start[x]
                var a = 0f
                var r = 0f
                var g = 0f
                var b = 0f
                for (i in w.indices) {
                    val p = px[y * sw + s0 + i]
                    val k = w[i] * ((p ushr 24) / 255f)
                    a += k
                    r += k * ((p shr 16) and 0xFF)
                    g += k * ((p shr 8) and 0xFF)
                    b += k * (p and 0xFF)
                }
                val o = y * dw + x
                ma[o] = a; mr[o] = r; mg[o] = g; mb[o] = b
            }
        }
        val out = IntArray(dw * dh)
        for (y in 0 until dh) {
            val w = ty.weights[y]
            val s0 = ty.start[y]
            for (x in 0 until dw) {
                var a = 0f
                var r = 0f
                var g = 0f
                var b = 0f
                for (i in w.indices) {
                    val o = (s0 + i) * dw + x
                    val k = w[i]
                    a += k * ma[o]
                    r += k * mr[o]
                    g += k * mg[o]
                    b += k * mb[o]
                }
                val ac = if (a < 0f) 0f else if (a > 1f) 1f else a
                out[y * dw + x] = if (ac <= 0.002f) 0 else (clamp8(ac * 255f) shl 24) or (clamp8(r / ac) shl 16) or (clamp8(g / ac) shl 8) or clamp8(b / ac)
            }
        }
        return out
    }

    /** Adds back [amount] of what a light 3x3 blur takes away (an unsharp mask). */
    private fun unsharp(px: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val out = IntArray(px.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var br = 0
                var bg = 0
                var bb = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, h - 1)
                    for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, w - 1)
                        val k = (2 - abs(dx)) * (2 - abs(dy))
                        val p = px[yy * w + xx]
                        br += k * ((p shr 16) and 0xFF)
                        bg += k * ((p shr 8) and 0xFF)
                        bb += k * (p and 0xFF)
                    }
                }
                val c = px[y * w + x]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                val r = cr + amount * (cr - br / 16f)
                val g = cg + amount * (cg - bg / 16f)
                val b = cb + amount * (cb - bb / 16f)
                out[y * w + x] = (0xFF shl 24) or (clamp8(r) shl 16) or (clamp8(g) shl 8) or clamp8(b)
            }
        }
        return out
    }
}
