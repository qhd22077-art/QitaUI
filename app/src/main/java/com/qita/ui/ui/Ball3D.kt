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

    // The bubble is a slightly inflated disc. It can turn about its vertical axis (yaw, like a flipping coin) and
    // tilt about its horizontal axis (pitch). Work out which point of the disc is under this pixel.
    float cy = cos(rot.x);
    float sy = sin(rot.x);
    float cp = cos(rot.y);
    float sp = sin(rot.y);
    if (abs(cy) < 0.03) { cy = cy < 0.0 ? -0.03 : 0.03; }
    if (abs(cp) < 0.03) { cp = 0.03; }
    float u = p.x / cy;
    float v = (p.y - u * sy * sp) / cp;
    float rr = sqrt(u * u + v * v);
    if (rr >= 1.04) { return half4(0.0); }
    float cov = clamp((1.0 - rr) * size.x * 0.5 * abs(cy) + 0.5, 0.0, 1.0);

    // Inflated profile: flat across most of the face, curving over quickly at the rim.
    float rc = min(rr, 1.0);
    float rp = rc * rc * sqrt(rc);
    float2 dir = rc > 0.0001 ? float2(u, v) / rc : float2(0.0);
    float3 n0 = float3(dir * rp, sqrt(max(1.0 - rp * rp, 0.0)));
    float nx1 = n0.x * cy + n0.z * sy;
    float nz1 = -n0.x * sy + n0.z * cy;
    float ny2 = n0.y * cp - nz1 * sp;
    float nz2 = n0.y * sp + nz1 * cp;
    float3 n = float3(nx1, ny2, nz2);
    if (n.z < 0.0) { n = -n; }

    // The art lies flat on the face, so it is not stretched. The dome bends it a little near the rim, like a lens.
    float R = 0.84;
    float2 d = float2(u, v) + n0.xy * 0.10;
    float2 uv = 0.5 + d / (2.0 * R);
    float3 base = body;
    if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
        half4 c = sampleIcon(uv * iconSize);
        float mask = 1.0 - smoothstep(0.92, 1.0, length(d) / R);
        base = body * (1.0 - float(c.a) * mask) + float3(c.rgb) * mask;
    }

    // Lighting from the upper left.
    float3 L = normalize(float3(-0.42, -0.58, 0.70));
    float ndl = max(dot(n, L), 0.0);
    float3 col = base * (0.55 + 0.62 * ndl);

    float f1 = 1.0 - n.z;
    float fres = f1 * f1 * f1;
    col *= 1.0 - 0.50 * fres;

    float3 B = normalize(float3(0.15, 0.85, 0.45));
    float nb = max(dot(n, B), 0.0);
    nb = nb * nb;
    nb = nb * nb;
    col += float3(0.75, 0.85, 1.0) * (0.30 * nb * (fres * 2.2 + 0.10));

    // Subtle highlights that follow the curve of the rim.
    float3 H = normalize(L + float3(0.0, 0.0, 1.0));
    float ndh = max(dot(n, H), 0.0);
    float spec = pow(ndh, 60.0) * 0.50;
    float3 R2 = 2.0 * n.z * n - float3(0.0, 0.0, 1.0);
    float3 W = normalize(float3(-0.35, -0.62, 0.70));
    float env = pow(max(dot(R2, W), 0.0), 6.0) * 0.12;
    col += float3(spec + env);

    // A fine lit edge around the disc, brighter on the side facing the light.
    float edgeLine = smoothstep(0.93, 0.985, rr) * (1.0 - smoothstep(0.985, 1.0, rr));
    float facing = 0.35 + 0.65 * max(dot(normalize(n.xy + float2(0.0001)), normalize(L.xy)), 0.0);
    col += float3(0.30 * edgeLine * facing);

    col = mix(col, float3(0.09, 0.88, 1.0), 0.34 * glow);
    col = clamp(col, 0.0, 1.0);
    return half4(half3(col * cov), half(cov));
}
"""
}
