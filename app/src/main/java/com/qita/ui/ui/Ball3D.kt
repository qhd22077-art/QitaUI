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

const float3 L = float3(-0.4194, -0.5792, 0.6990);
const float3 Hh = float3(-0.2275, -0.3142, 0.9217);
const float3 Bn = float3(0.1541, 0.8732, 0.4623);
const float3 W1 = float3(-0.3505, -0.6209, 0.7011);
const float3 W2 = float3(0.4505, 0.6006, 0.6607);

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

// The side wall of the puck: what is seen of its thickness when it is turned or tilted.
float4 wallPix(float ub, float vb, float rb, float cy, float sy, float cp, float sp) {
    if (rb >= 1.03) { return float4(0.0); }
    float wcov = clamp((1.0 - rb) * size.x * 0.5 * abs(cy) + 0.5, 0.0, 1.0);
    float2 wd = rb > 0.0001 ? float2(ub, vb) / rb : float2(0.0);
    float wnx = wd.x * cy;
    float wnz = -wd.x * sy;
    float wny = wd.y * cp - wnz * sp;
    float wnz2 = wd.y * sp + wnz * cp;
    float3 wn = float3(wnx, wny, wnz2);
    float wl = max(dot(wn, L), 0.0);
    float3 glass = float3(0.55, 0.70, 0.90);
    float3 wcol = (body * 0.6 + glass * 0.35) * (0.30 + 0.9 * wl);
    wcol += float3(pow(max(dot(wn, Hh), 0.0), 20.0) * 0.5);
    wcol = clamp(wcol, 0.0, 1.0);
    return float4(wcol * wcov, wcov);
}

// The domed front face with the art seen through the glass.
float4 frontPix(float u, float v, float rr, float cy, float sy, float cp, float sp) {
    if (rr >= 1.03) { return float4(0.0); }
    float cov = clamp((1.0 - rr) * size.x * 0.5 * abs(cy) + 0.5, 0.0, 1.0);

    // A convex lens: the normal tilts steadily from the middle out to a steep bevel at the rim.
    float rc = min(rr, 1.0);
    float rp = pow(rc, 1.6);
    float2 dir = rc > 0.0001 ? float2(u, v) / rc : float2(0.0);
    float3 n0 = float3(dir * rp, sqrt(max(1.0 - rp * rp, 0.0)));
    float nx1 = n0.x * cy + n0.z * sy;
    float nz1 = -n0.x * sy + n0.z * cy;
    float ny2 = n0.y * cp - nz1 * sp;
    float nz2 = n0.y * sp + nz1 * cp;
    float3 n = float3(nx1, ny2, nz2);
    if (n.z < 0.0) { n = -n; }

    // The art sits 0.14 below the surface, so it slides against the rim as the bubble turns, and the dome bends it a little.
    float2 sh = float2(clamp(0.14 * sy / cy, -0.4, 0.4), clamp(-0.14 * sp / (abs(cy) * cp), -0.4, 0.4));
    float R = 0.84;
    float2 d = float2(u, v) + sh + n0.xy * 0.10;
    float2 uv = 0.5 + d / (2.0 * R);
    float3 base = body;
    if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
        half4 c = sampleIcon(uv * iconSize);
        float mask = 1.0 - smoothstep(0.92, 1.0, length(d) / R);
        base = body * (1.0 - float(c.a) * mask) + float3(c.rgb) * mask;
    }

    float ndl = max(dot(n, L), 0.0);
    float3 col = base * (0.50 + 0.70 * ndl);

    float f1 = 1.0 - n.z;
    float fres = f1 * f1 * f1;
    col *= 1.0 - 0.50 * fres;

    float nb = max(dot(n, Bn), 0.0);
    nb = nb * nb;
    nb = nb * nb;
    col += float3(0.75, 0.85, 1.0) * (0.40 * nb * (fres * 2.2 + 0.10));

    // Gentle highlights that follow the curve: a small glint and a soft reflection on the upper-left rim, with a faint one opposite.
    float ndh = max(dot(n, Hh), 0.0);
    float spec = pow(ndh, 80.0) * 0.28;
    float3 Rv = 2.0 * n.z * n - float3(0.0, 0.0, 1.0);
    float env = pow(max(dot(Rv, W1), 0.0), 14.0) * 0.11;
    float env2 = pow(max(dot(Rv, W2), 0.0), 14.0) * 0.03;
    col += float3(spec + env + env2);

    // The bevel: a darker groove just inside a thin lit edge.
    float groove = smoothstep(0.87, 0.92, rr) * (1.0 - smoothstep(0.92, 0.96, rr));
    col *= 1.0 - 0.40 * groove;
    float edgeLine = smoothstep(0.955, 0.985, rr) * (1.0 - smoothstep(0.985, 1.0, rr));
    float nl = length(n.xy) + 0.0001;
    float facing = 0.35 + 0.65 * max(dot(n.xy / nl, L.xy / 0.72), 0.0);
    col += float3(0.24 * edgeLine * facing);

    col = mix(col, float3(0.09, 0.88, 1.0), 0.34 * glow);
    col = clamp(col, 0.0, 1.0);
    return float4(col * cov, cov);
}

half4 main(float2 frag) {
    float2 p = (frag / size) * 2.0 - 1.0;

    // The bubble is a thick glass puck seen from slightly above. It can turn about its vertical axis (yaw, like a
    // flipping coin) and tilt about its horizontal axis (pitch).
    float pitch = rot.y + 0.30;
    float cy = cos(rot.x);
    float sy = sin(rot.x);
    float cp = cos(pitch);
    float sp = sin(pitch);
    if (abs(cy) < 0.03) { cy = cy < 0.0 ? -0.03 : 0.03; }
    if (abs(cp) < 0.03) { cp = 0.03; }

    // Front face: which point of the disc is under this pixel. Back face: the same disc pushed 0.20 back.
    float u = p.x / cy;
    float v = (p.y - u * sy * sp) / cp;
    float rr = sqrt(u * u + v * v);
    float T = 0.20;
    float ub = (p.x + T * sy) / cy;
    float vb = (p.y - ub * sy * sp - T * cy * sp) / cp;
    float rb = sqrt(ub * ub + vb * vb);

    float4 f = frontPix(u, v, rr, cy, sy, cp, sp);
    float4 w = wallPix(ub, vb, rb, cy, sy, cp, sp);
    float3 rgb = f.rgb + w.rgb * (1.0 - f.a);
    float a = f.a + w.a * (1.0 - f.a);
    if (a <= 0.0) { return half4(0.0); }
    return half4(half3(rgb), half(a));
}
"""
}
