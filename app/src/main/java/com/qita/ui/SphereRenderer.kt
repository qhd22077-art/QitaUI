package com.qita.ui

import android.graphics.Bitmap
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Renders an icon as a milky glass ball, once, for devices that cannot run the live shader (before Android 13) or when
 * live 3D is switched off. It mirrors the shader without the rolling: the art is wrapped around the ball (filling most of
 * it, squeezing toward the edge), lit softly from the upper left so colours stay vivid, with a rim that fades to white,
 * a brighter lit rim on the upper left, light bounced up from below, and gentle highlights.
 */
object SphereRenderer {
    /** How far the art reaches from the centre of the ball, in radians (about 80 degrees). */
    private const val REACH = 1.40f

    private val light = normalize(-0.42f, -0.58f, 0.70f)
    private val half = normalize(light[0], light[1], light[2] + 1f)
    private val bounceDir = normalize(0.15f, 0.85f, 0.45f)
    private val window = normalize(-0.35f, -0.62f, 0.70f)

    private fun normalize(x: Float, y: Float, z: Float): FloatArray {
        val n = sqrt(x * x + y * y + z * z)
        return floatArrayOf(x / n, y / n, z / n)
    }

    /**
     * [backdrop] is the ARGB colour of the glass body that shows where the icon is not. [hazy] scales how white the rim
     * goes (1 normally, less for dark glass).
     */
    fun render(art: Bitmap, size: Int, backdrop: Int, hazy: Float = 1f): Bitmap {
        val aw = art.width
        val ah = art.height
        val src = IntArray(aw * ah)
        art.getPixels(src, 0, aw, 0, 0, aw, ah)
        val bdR = ((backdrop shr 16) and 0xFF) / 255f
        val bdG = ((backdrop shr 8) and 0xFF) / 255f
        val bdB = (backdrop and 0xFF) / 255f

        val coord = FloatArray(size) { (it + 0.5f) / size * 2f - 1f }
        val out = IntArray(size * size)
        val lxy = sqrt(light[0] * light[0] + light[1] * light[1])

        for (py in 0 until size) {
            val y = coord[py]
            for (px in 0 until size) {
                val x = coord[px]
                val r2 = x * x + y * y
                if (r2 >= 1.04f) continue
                val rr = sqrt(r2)
                val cov = ((1f - rr) * size / 2f + 0.5f).coerceIn(0f, 1f)
                if (cov <= 0f) continue
                val nz = sqrt((1f - r2).coerceAtLeast(0f))

                // Wrap the art: longitude and latitude of this point on the ball.
                val lon = atan2(x, nz)
                val lat = asin(y.coerceIn(-1f, 1f))
                val ux = 0.5f + lon / (2f * REACH)
                val uy = 0.5f + lat / (2f * REACH)
                val ang = acos(nz.coerceIn(-1f, 1f))
                val edgeT = ((ang / REACH - 0.80f) / 0.20f).coerceIn(0f, 1f)
                val mask = 1f - edgeT * edgeT * (3f - 2f * edgeT)
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
                    ca = mask * (((p00 ushr 24) * w00 + (p10 ushr 24) * w10 + (p01 ushr 24) * w01 + (p11 ushr 24) * w11)) / 255f
                    cr = ((((p00 shr 16) and 0xFF) * w00 + ((p10 shr 16) and 0xFF) * w10 + ((p01 shr 16) and 0xFF) * w01 + ((p11 shr 16) and 0xFF) * w11)) / 255f
                    cg = ((((p00 shr 8) and 0xFF) * w00 + ((p10 shr 8) and 0xFF) * w10 + ((p01 shr 8) and 0xFF) * w01 + ((p11 shr 8) and 0xFF) * w11)) / 255f
                    cb = (((p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 + (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11)) / 255f
                }
                var r = bdR * (1f - ca) + cr * ca
                var g = bdG * (1f - ca) + cg * ca
                var b = bdB * (1f - ca) + cb * ca

                // Soft, bright lighting so colours stay vivid.
                val ndl = (x * light[0] + y * light[1] + nz * light[2]).coerceAtLeast(0f)
                val shade = 0.74f + 0.36f * ndl
                r *= shade; g *= shade; b *= shade

                // Milky glass: the edge fades toward white, brighter on the lit upper-left side.
                val f1 = 1f - nz
                val f = f1 * f1 * sqrt(f1) // f1^2.5, close to the shader's f1^2.2
                val haze = 0.5f * f * hazy
                r = r * (1f - 0.5f * f) + 0.95f * haze
                g = g * (1f - 0.5f * f) + 0.96f * haze
                b = b * (1f - 0.5f * f) + 1.00f * haze
                val nl = rr + 0.0001f
                val facing = ((x / nl) * light[0] / lxy + (y / nl) * light[1] / lxy).coerceAtLeast(0f)
                val lit = 0.30f * f * facing
                r += lit; g += lit; b += lit

                // Light bounced up from below.
                val nb = (x * bounceDir[0] + y * bounceDir[1] + nz * bounceDir[2]).coerceAtLeast(0f)
                val bounce = nb * nb * nb * 0.45f * (f * 2f + 0.05f)
                r += 0.8f * bounce; g += 0.9f * bounce; b += bounce

                // Gentle highlights: a small glint and a soft wide reflection.
                val ndh = (x * half[0] + y * half[1] + nz * half[2]).coerceAtLeast(0f)
                val q2 = ndh * ndh; val q4 = q2 * q2; val q8 = q4 * q4; val q16 = q8 * q8; val q32 = q16 * q16; val q64 = q32 * q32
                val spec = q64 * q4 * q2 * 0.30f
                val rx = 2f * nz * x
                val ry = 2f * nz * y
                val rz = 2f * nz * nz - 1f
                val rd = (rx * window[0] + ry * window[1] + rz * window[2]).coerceAtLeast(0f)
                val e2 = rd * rd
                val env = e2 * e2 * rd * 0.24f
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
