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
        shader.setFloatUniform("t1", 1f, 1f, 1f, 3f)
        shader.setFloatUniform("acc", 0.09f, 0.88f, 1f)
        shader.setFloatUniform("t2", 1f, 1f, 1f, 0f)
        shader.setFloatUniform("hazy", if (app.action != null && app.action != com.qita.ui.SystemAction.SETTINGS) 0.55f else 1f)
        return Paint(shader)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun update(paint: Paint, width: Float, height: Float, yaw: Float, pitch: Float, glow: Float, look: Look, body: Int) {
        paint.shader.setFloatUniform("size", width, height)
        paint.shader.setFloatUniform(
            "body",
            ((body shr 16) and 0xFF) / 255f, ((body shr 8) and 0xFF) / 255f, (body and 0xFF) / 255f,
        )
        // Icon saturation, brightness, icon size, dome exponent; then rim width, highlight, thickness.
        paint.shader.setFloatUniform("t1", look.iconSat, look.iconBright, look.iconScale.coerceIn(0.6f, 1.1f), 3f / look.dome.coerceIn(0.5f, 2f))
        paint.shader.setFloatUniform("acc", look.accent.red, look.accent.green, look.accent.blue)
        paint.shader.setFloatUniform("t2", look.rimWidth.coerceIn(0.3f, 2.5f), look.highlight, look.thickness.coerceIn(0f, 1.8f), 0f)
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
uniform float4 t1;
uniform float4 t2;
uniform float3 acc;

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

// The slim side wall of the disc: what shows of its thickness when it is turned or tilted.
float4 wallPix(float ub, float vb, float rb, float cy, float sy, float cp, float sp) {
    if (rb >= 1.03) { return float4(0.0); }
    float wcov = clamp((1.0 - rb) * size.x * 0.5 * abs(cy) + 0.5, 0.0, 1.0);
    float2 wd = rb > 0.0001 ? float2(ub, vb) / rb : float2(0.0);
    float wnx = wd.x * cy;
    float wnz = -wd.x * sy;
    float wny = wd.y * cp - wnz * sp;
    float wnz2 = wd.y * sp + wnz * cp;
    float wl = max(dot(float3(wnx, wny, wnz2), L), 0.0);
    float3 wcol = (body * 0.55 + float3(0.75, 0.80, 0.92) * (0.45 * hazy)) * (0.55 + 0.6 * wl);
    wcol = clamp(wcol, 0.0, 1.0);
    return float4(wcol * wcov, wcov);
}

// The inflated front face: flat art under a gently domed glass, with a soft milky rim.
float4 frontPix(float u, float v, float rr, float cy, float sy, float cp, float sp) {
    if (rr >= 1.03) { return float4(0.0); }
    float cov = clamp((1.0 - rr) * size.x * 0.5 * abs(cy) + 0.5, 0.0, 1.0);

    // Inflated profile: a flat face that rolls over into a bevel at the rim.
    float rc = min(rr, 1.0);
    float rp = pow(max(rc, 0.0001), t1.w);
    float2 dir = rc > 0.0001 ? float2(u, v) / rc : float2(0.0);
    float3 n0 = float3(dir * rp, sqrt(max(1.0 - rp * rp, 0.0)));
    float nx1 = n0.x * cy + n0.z * sy;
    float nz1 = -n0.x * sy + n0.z * cy;
    float ny2 = n0.y * cp - nz1 * sp;
    float nz2 = n0.y * sp + nz1 * cp;
    float3 n = float3(nx1, ny2, nz2);
    if (n.z < 0.0) { n = -n; }

    // The art is rigid and flat: it is never stretched or bent. It sits a little below the glass, so it slides a touch as the disc turns.
    float2 sh = float2(clamp(0.06 * sy / cy, -0.4, 0.4), clamp(-0.06 * sp / (abs(cy) * cp), -0.4, 0.4));
    float R = 0.92 / t1.z;
    float2 d = float2(u, v) + sh;
    float2 uv = 0.5 + d / (2.0 * R);
    float3 base = body;
    if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
        half4 c = sampleIcon(uv * iconSize);
        float mask = 1.0 - smoothstep(0.93, 1.0, length(d) / R);
        float3 ic = float3(c.rgb);
        float lum = dot(ic, float3(0.299, 0.587, 0.114));
        ic = clamp((float3(lum) + (ic - float3(lum)) * t1.x) * t1.y, 0.0, 1.0);
        base = body * (1.0 - float(c.a) * mask) + ic * mask;
    }

    // Soft, bright lighting so the colours stay vivid.
    float ndl = max(dot(n, L), 0.0);
    float3 col = base * (0.74 + 0.36 * ndl);

    // Milky glass: a narrow rim that fades toward white, brighter on the lit upper-left side.
    float f = pow(max(1.0 - n.z, 0.0), 2.8 / t2.x);
    col = col * (1.0 - 0.5 * f) + float3(0.95, 0.96, 1.0) * (0.5 * f * hazy);
    float nl = length(n.xy) + 0.0001;
    float facing = max(dot(n.xy / nl, L.xy / 0.72), 0.0);
    col += float3(0.26 * t2.y * f * facing);
    // A fine lit edge marks the bevel.
    float edgeLine = smoothstep(0.955, 0.985, rr) * (1.0 - smoothstep(0.985, 1.0, rr));
    col += float3(0.22 * t2.y * edgeLine * facing);

    // Light bounced up from below, and gentle highlights that follow the dome.
    float nb = max(dot(n, Bn), 0.0);
    nb = nb * nb * nb;
    col += float3(0.8, 0.9, 1.0) * (0.40 * nb * (f * 2.0 + 0.05));
    float ndh = max(dot(n, Hh), 0.0);
    float3 Rv = 2.0 * n.z * n - float3(0.0, 0.0, 1.0);
    float env = pow(max(dot(Rv, W1), 0.0), 6.0) * 0.16;
    col += float3((pow(ndh, 80.0) * 0.20 + env) * t2.y);

    col = mix(col, acc, 0.34 * glow);
    col = clamp(col, 0.0, 1.0);
    return float4(col * cov, cov);
}

half4 main(float2 frag) {
    float2 p = (frag / size) * 2.0 - 1.0;

    // The bubble is an inflated disc seen from slightly above. It can turn about its vertical axis (yaw, like a
    // flipping coin) and tilt about its horizontal axis (pitch).
    float pitch = rot.y + 0.06 + 0.24 * min(t2.z, 1.2);
    float cy = cos(rot.x);
    float sy = sin(rot.x);
    float cp = cos(pitch);
    float sp = sin(pitch);
    if (abs(cy) < 0.03) { cy = cy < 0.0 ? -0.03 : 0.03; }
    if (abs(cp) < 0.03) { cp = 0.03; }

    // Front face: which point of the disc is under this pixel. Back face: the same disc pushed 0.22 back.
    float u = p.x / cy;
    float v = (p.y - u * sy * sp) / cp;
    float rr = sqrt(u * u + v * v);
    float T = 0.22 * t2.z;
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
