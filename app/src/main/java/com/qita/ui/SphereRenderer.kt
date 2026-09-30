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
    private const val ART_SCALE = 0.66f

    private val light = normalize(-0.42f, -0.58f, 0.70f)
    private val half = normalize(light[0], light[1], light[2] + 1f)
    private val bounceDir = normalize(0.15f, 0.85f, 0.45f)
    private val window = normalize(-0.35f, -0.62f, 0.70f)

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
                    val dx = ux - 0.5f
                    val dy = uy - 0.5f
                    val dist = sqrt(dx * dx + dy * dy) * 2f
                    val edgeT = ((dist - 0.90f) / 0.10f).coerceIn(0f, 1f)
                    val mask = 1f - edgeT * edgeT * (3f - 2f * edgeT)
                    ca = mask * (((p00 ushr 24) * w00 + (p10 ushr 24) * w10 + (p01 ushr 24) * w01 + (p11 ushr 24) * w11)) / 255f
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

                // Subtle highlights that follow the curve: a small glint, and a broad soft reflection of a window
                // from the reflected view direction, so it stretches and bends with the surface.
                val ndh = (x * half[0] + y * half[1] + nz * half[2]).coerceAtLeast(0f)
                val p2 = ndh * ndh;
                val q4 = p2 * p2; val q8 = q4 * q4; val q16 = q8 * q8; val q32 = q16 * q16; val q64 = q32 * q32
                val spec = q64 * q16 * q8 * p2 * 0.55f
                val rx = 2f * nz * x
                val ry = 2f * nz * y
                val rz = 2f * nz * nz - 1f
                val rd = (rx * window[0] + ry * window[1] + rz * window[2]).coerceAtLeast(0f)
                val e2 = rd * rd; val e4 = e2 * e2
                val env = e4 * e2 * 0.16f
                r += spec + env; g += spec + env; b += spec + env

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
