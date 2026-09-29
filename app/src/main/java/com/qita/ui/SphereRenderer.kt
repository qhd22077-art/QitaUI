package com.qita.ui

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.sqrt

/**
 * Renders an icon as a lit glass sphere, once, so a bubble is just an image at draw time.
 *
 * For every pixel of the disc the surface normal is (x, y, sqrt(1 - x^2 - y^2)). The icon is sampled through
 * a spherical mapping (asin of x and y), so it looks wrapped around the ball, and the result is lit from the
 * upper left: soft diffuse shading, a sharp highlight, a broad gloss, a darker rim with a thin light edge, light
 * bounced up from the bottom and a soft window reflection near the top. No powers are used in the inner loop.
 */
object SphereRenderer {
    /** How much of the sphere the icon spans (1 = the whole ball). */
    private const val ART_SCALE = 0.98f

    private val light = normalize(-0.42f, -0.58f, 0.70f)
    private val half = normalize(light[0], light[1], light[2] + 1f)
    private val bounceDir = normalize(0.15f, 0.85f, 0.45f)

    private fun normalize(x: Float, y: Float, z: Float): FloatArray {
        val n = sqrt(x * x + y * y + z * z)
        return floatArrayOf(x / n, y / n, z / n)
    }

    /** [backdrop] is the ARGB colour of the glass body that shows through transparent parts of the icon. */
    fun render(art: Bitmap, size: Int, backdrop: Int): Bitmap {
        val aw = art.width
        val ah = art.height
        val src = IntArray(aw * ah)
        art.getPixels(src, 0, aw, 0, 0, aw, ah)
        val bdR = ((backdrop shr 16) and 0xFF) / 255f
        val bdG = ((backdrop shr 8) and 0xFF) / 255f
        val bdB = (backdrop and 0xFF) / 255f

        val coord = FloatArray(size) { (it + 0.5f) / size * 2f - 1f }
        // asin(x) as a fraction of a quarter turn, for the spherical mapping.
        val wrap = FloatArray(size) { asin(coord[it].coerceIn(-1f, 1f)) / (PI.toFloat() / 2f) }
        val out = IntArray(size * size)

        for (py in 0 until size) {
            val y = coord[py]
            val uy = 0.5f + 0.5f * wrap[py] / ART_SCALE
            for (px in 0 until size) {
                val x = coord[px]
                val r2 = x * x + y * y
                if (r2 >= 1.04f) continue
                val rr = sqrt(r2)
                val cov = ((1f - rr) * size / 2f + 0.5f).coerceIn(0f, 1f)
                if (cov <= 0f) continue
                val nz = sqrt((1f - r2).coerceAtLeast(0f))

                // Sample the icon (bilinear) through the spherical mapping.
                val ux = 0.5f + 0.5f * wrap[px] / ART_SCALE
                var cr = 0f; var cg = 0f; var cb = 0f; var ca = 0f
                if (ux in 0f..1f && uy in 0f..1f) {
                    val sx = ux * (aw - 1)
                    val sy = uy * (ah - 1)
                    val x0 = sx.toInt(); val y0 = sy.toInt()
                    val x1 = minOf(x0 + 1, aw - 1); val y1 = minOf(y0 + 1, ah - 1)
                    val fx = sx - x0; val fy = sy - y0
                    val w00 = (1 - fx) * (1 - fy); val w10 = fx * (1 - fy); val w01 = (1 - fx) * fy; val w11 = fx * fy
                    val p00 = src[y0 * aw + x0]; val p10 = src[y0 * aw + x1]
                    val p01 = src[y1 * aw + x0]; val p11 = src[y1 * aw + x1]
                    ca = (((p00 ushr 24) * w00 + (p10 ushr 24) * w10 + (p01 ushr 24) * w01 + (p11 ushr 24) * w11)) / 255f
                    cr = ((((p00 shr 16) and 0xFF) * w00 + ((p10 shr 16) and 0xFF) * w10 + ((p01 shr 16) and 0xFF) * w01 + ((p11 shr 16) and 0xFF) * w11)) / 255f
                    cg = ((((p00 shr 8) and 0xFF) * w00 + ((p10 shr 8) and 0xFF) * w10 + ((p01 shr 8) and 0xFF) * w01 + ((p11 shr 8) and 0xFF) * w11)) / 255f
                    cb = (((p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 + (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11)) / 255f
                }
                var r = bdR * (1f - ca) + cr * ca
                var g = bdG * (1f - ca) + cg * ca
                var b = bdB * (1f - ca) + cb * ca

                // Diffuse light from the upper left.
                val ndl = (x * light[0] + y * light[1] + nz * light[2]).coerceAtLeast(0f)
                val shade = 0.55f + 0.62f * ndl
                r *= shade; g *= shade; b *= shade

                // Darker toward the rim.
                val f1 = 1f - nz
                val fres = f1 * f1 * f1
                val dim = 1f - 0.55f * fres
                r *= dim; g *= dim; b *= dim

                // Light bounced up from the floor, strongest near the lower rim.
                val nb = (x * bounceDir[0] + y * bounceDir[1] + nz * bounceDir[2]).coerceAtLeast(0f)
                val nb2 = nb * nb
                val bounce = nb2 * nb2 * 0.30f * (fres * 2.2f + 0.12f)
                r += 0.75f * bounce; g += 0.85f * bounce; b += bounce

                // Sharp highlight plus a broad faint gloss (powers by repeated squaring).
                val ndh = (x * half[0] + y * half[1] + nz * half[2]).coerceAtLeast(0f)
                val p2 = ndh * ndh; val p4 = p2 * p2; val p8 = p4 * p4; val p16 = p8 * p8; val p32 = p16 * p16; val p64 = p32 * p32
                val spec = p64 * p4 * p2 + p8 * p4 * p2 * 0.16f
                r += spec; g += spec; b += spec

                // A soft window reflection across the upper part of the ball.
                if (nz > 0.2f) {
                    val wx = x + 0.12f
                    val wy = y + 0.50f
                    val v = 1f - (wx * wx / 0.42f + wy * wy / 0.07f)
                    if (v > 0f) {
                        val win = v * sqrt(v) * 0.28f
                        r += win; g += win; b += win
                    }
                }

                val ir = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val ig = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val ib = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val ia = (cov * 255f + 0.5f).toInt()
                out[py * size + px] = (ia shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, size, 0, 0, size, size)
        return bmp
    }
}
