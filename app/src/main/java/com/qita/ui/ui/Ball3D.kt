package com.qita.ui.ui

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import com.qita.ui.LaunchableApp

/** Whether bubbles are shaded live as rolling 3D balls (needs Android 13+). */
val LocalBall3D = compositionLocalOf { true }

/** A shared clock (radians, looping) that drives the idle sway of every bubble, so only one animation runs. */
val LocalBallClock = compositionLocalOf<State<Float>> {
    object : State<Float> { override val value: Float = 0f }
}

/**
 * A bubble drawn as a real 3D ball by a GPU shader (AGSL). The icon is looked up on the sphere after the surface
 * is rotated by the bubble's yaw and pitch, while the lighting stays fixed, so the picture rolls under the highlight
 * like a printed ball. [paint] is created once per bubble; if the shader fails to build it is null and the caller
 * falls back to the pre-rendered ball.
 */
object Ball3D {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    class Paint @RequiresApi(Build.VERSION_CODES.TIRAMISU) constructor(val shader: RuntimeShader) {
        val brush = ShaderBrush(shader)
    }

    /** [body] is the ARGB glass colour that shows where the icon is not. */
    fun create(app: LaunchableApp, body: Int): Paint? {
        if (!supported) return null
        return runCatching { build(app, body) }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun build(app: LaunchableApp, body: Int): Paint {
        val bitmap = app.icon.asAndroidBitmap()
        val shader = RuntimeShader(AGSL)
        shader.setInputShader("icon", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        shader.setFloatUniform("iconSize", bitmap.width.toFloat(), bitmap.height.toFloat())
        shader.setFloatUniform(
            "body",
            ((body shr 16) and 0xFF) / 255f, ((body shr 8) and 0xFF) / 255f, (body and 0xFF) / 255f,
        )
        shader.setFloatUniform("size", 1f, 1f)
        shader.setFloatUniform("rot", 0f, 0f)
        shader.setFloatUniform("glow", 0f)
        shader.setFloatUniform("hazy", if (app.action != null && app.action != com.qita.ui.SystemAction.SETTINGS) 0.55f else 1f)
        return Paint(shader)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun update(paint: Paint, width: Float, height: Float, yaw: Float, pitch: Float, glow: Float) {
        paint.shader.setFloatUniform("size", width, height)
        paint.shader.setFloatUniform("rot", yaw, pitch)
        paint.shader.setFloatUniform("glow", glow)
    }

    private const val AGSL = """
uniform shader icon;
uniform float2 iconSize;
uniform float2 size;
uniform float2 rot;
uniform float3 body;
uniform float glow;
uniform float hazy;

const float3 L = float3(-0.4194, -0.5792, 0.6990);
const float3 Hh = float3(-0.2275, -0.3142, 0.9217);
const float3 Bn = float3(0.1541, 0.8732, 0.4623);
const float3 W1 = float3(-0.3505, -0.6209, 0.7011);

half4 sampleIcon(float2 px) {
    float2 q = px - 0.5;
    half2 f = half2(fract(q));
    float2 b = floor(q) + 0.5;
    half4 c00 = icon.eval(b);
    half4 c10 = icon.eval(b + float2(1.0, 0.0));
    half4 c01 = icon.eval(b + float2(0.0, 1.0));
    half4 c11 = icon.eval(b + float2(1.0, 1.0));
    return mix(mix(c00, c10, f.x), mix(c01, c11, f.x), f.y);
}

half4 main(float2 frag) {
    float2 p = (frag / size) * 2.0 - 1.0;
    float r2 = dot(p, p);
    if (r2 >= 1.04) { return half4(0.0); }
    float rr = sqrt(r2);
    float cov = clamp((1.0 - rr) * size.x * 0.5 + 0.5, 0.0, 1.0);
    float nz = sqrt(max(1.0 - r2, 0.0));
    float3 n = float3(p, nz);

    // The bubble is a glass ball with the art printed on it. Turn the ball (yaw, pitch) and look the art up where it now is.
    float cy = cos(rot.x);
    float sy = sin(rot.x);
    float cp = cos(rot.y);
    float sp = sin(rot.y);
    float y1 = n.y * cp - n.z * sp;
    float z1 = n.y * sp + n.z * cp;
    float x2 = n.x * cy + z1 * sy;
    float z2 = -n.x * sy + z1 * cy;
    float lon = atan(x2, z2);
    float lat = asin(clamp(y1, -1.0, 1.0));
    // The art fills most of the ball (about 80 degrees each way), wrapping and squeezing toward the edge, as on the real icons.
    float TM = 1.40;
    float2 uv = 0.5 + float2(lon, lat) / (2.0 * TM);
    float ang = acos(clamp(z2, -1.0, 1.0));
    float mask = 1.0 - smoothstep(0.80, 1.0, ang / TM);
    float3 base = body;
    if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
        half4 c = sampleIcon(uv * iconSize);
        base = body * (1.0 - float(c.a) * mask) + float3(c.rgb) * mask;
    }

    // Soft, bright lighting so the colours stay vivid; the light is fixed while the art rolls.
    float ndl = max(dot(n, L), 0.0);
    float3 col = base * (0.74 + 0.36 * ndl);

    // Milky glass: the edge fades toward white, with a brighter lit rim on the upper-left.
    float f1 = 1.0 - nz;
    float f = pow(f1, 2.2);
    col = col * (1.0 - 0.5 * f) + float3(0.95, 0.96, 1.0) * (0.5 * f * hazy);
    float nl = length(p) + 0.0001;
    float facing = max(dot(p / nl, L.xy / 0.72), 0.0);
    col += float3(0.30 * f * facing);

    // Light bounced up from below, and gentle highlights: a soft wide reflection and a small glint.
    float nb = max(dot(n, Bn), 0.0);
    nb = nb * nb * nb;
    col += float3(0.8, 0.9, 1.0) * (0.45 * nb * (f * 2.0 + 0.05));
    float ndh = max(dot(n, Hh), 0.0);
    float3 Rv = 2.0 * n.z * n - float3(0.0, 0.0, 1.0);
    float env = pow(max(dot(Rv, W1), 0.0), 5.0) * 0.24;
    col += float3(pow(ndh, 70.0) * 0.30 + env);

    col = mix(col, float3(0.09, 0.88, 1.0), 0.34 * glow);
    col = clamp(col, 0.0, 1.0);
    return half4(half3(col * cov), half(cov));
}
"""
}
