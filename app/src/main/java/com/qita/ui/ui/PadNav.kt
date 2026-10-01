package com.qita.ui.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qita.ui.Controller
import com.qita.ui.LaunchableApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Which stack level a screen belongs to. Gamepad navigation only moves between items on the
 * topmost level that has any, so windows on top never let the highlight wander onto what is under them.
 */
val LocalPadLayer = compositionLocalOf { 0 }

/**
 * False for content that is on screen but should not take the gamepad highlight: the neighbouring pages that peek in beside
 * the LiveArea in the middle. Their buttons are left out of navigation, so the highlight never jumps onto (or over) them.
 */
val LocalPadEnabled = compositionLocalOf { true }

class PadTarget(val key: Any) {
    var layer = 0
    var bounds: Rect = Rect.Zero
    /** Corner radius of the highlight ring; null means fully rounded (circle or pill). */
    var corner: Dp? = 10.dp
    var pad: Dp = 5.dp
    /** False for items that show their own selection look (such as the Settings rows). */
    var ring = true
    /** True for buttons that sit on top of other things (the tiles hanging over a LiveArea): other items' highlights are not drawn over them. */
    var overlay = false
    var app: LaunchableApp? = null
    var onClick: () -> Unit = {}
    /** Left/right adjusts instead of moving (sliders). */
    var onAdjust: ((Int) -> Unit)? = null
}

class PadScroller {
    var layer = 0
    var bounds: Rect = Rect.Zero
    var scrollBy: suspend (Float) -> Unit = {}
}

/**
 * Gamepad/keyboard navigation that does not depend on Android's focus system. Every button on
 * screen registers itself here with its bounds; the D-pad moves the highlight to the nearest
 * item in that direction and A calls the highlighted item's click action directly.
 */
object PadNav {
    var current by mutableStateOf<Any?>(null)
        private set
    var currentBounds by mutableStateOf(Rect.Zero)
        private set
    var currentCorner by mutableStateOf<Dp?>(10.dp)
        private set
    var currentPad by mutableStateOf(5.dp)
        private set
    var currentRing by mutableStateOf(true)
        private set

    /** The item under the on-screen cursor (cursor mode). */
    var hover by mutableStateOf<Any?>(null)
        private set
    /** The button a finger is on right now (or was a moment ago), so touching shows the same highlight as the gamepad or cursor. */
    var touched by mutableStateOf<Any?>(null)
    var hoverBounds by mutableStateOf(Rect.Zero)
        private set
    var hoverCorner by mutableStateOf<Dp?>(10.dp)
        private set
    var hoverPad by mutableStateOf(5.dp)
        private set
    var hoverRing by mutableStateOf(true)
        private set

    val targets = LinkedHashMap<Any, PadTarget>()
    val scrollers = ArrayList<PadScroller>()
    var viewport: Rect = Rect.Zero
    var scope: CoroutineScope? = null
    var onMoved: () -> Unit = {}

    private var currentLayer = 0
    private var missingSince = 0L
    private val memory = HashMap<Int, Any>()

    private fun placed(t: PadTarget) = t.bounds != Rect.Zero
    private fun onScreen(r: Rect) = viewport == Rect.Zero || r.overlaps(viewport)
    private fun topLayer(): Int? = targets.values.filter { placed(it) }.maxOfOrNull { it.layer }

    private fun setCurrent(t: PadTarget) {
        val changed = current != t.key
        current = t.key
        currentBounds = t.bounds
        currentCorner = t.corner
        currentPad = t.pad
        currentRing = t.ring
        currentLayer = t.layer
        memory[t.layer] = t.key
        missingSince = 0L
        if (changed) onMoved()
    }

    fun select(key: Any) {
        targets[key]?.let { setCurrent(it) }
    }

    fun boundsChanged(t: PadTarget) {
        if (current == t.key) currentBounds = t.bounds
    }

    fun currentApp(): LaunchableApp? = current?.let { targets[it]?.app }

    private fun best(layer: Int): PadTarget? =
        targets.values
            .filter { it.layer == layer && placed(it) && onScreen(it.bounds) }
            .minWithOrNull(compareBy<PadTarget>({ (it.bounds.top / 48f).toInt() }, { it.bounds.left }))

    /**
     * Makes sure the highlight is on something in the topmost layer, remembering where it was in
     * each layer so closing a window puts it back. Returns true if it had to pick a new item.
     */
    fun ensure(): Boolean {
        val top = topLayer() ?: return false
        val layers = targets.values.filter { placed(it) }.map { it.layer }.toSet()
        memory.keys.retainAll(layers)
        val cur = current
        var valid = cur != null && currentLayer == top
        if (valid && cur != null && !targets.containsKey(cur)) {
            // Scrolled out of the composed range for a moment is fine; gone for good is not.
            val now = SystemClock.uptimeMillis()
            if (missingSince == 0L) missingSince = now
            if (now - missingSince > 1200) valid = false
        } else {
            missingSince = 0L
        }
        if (valid) return false
        val pick = memory[top]?.let { targets[it] } ?: best(top)
        if (pick != null) {
            setCurrent(pick)
            return true
        }
        return false
    }

    /** Moves the highlight one step; dx/dy are -1, 0 or 1. The first press after a pause only shows the highlight. */
    fun navigate(dx: Int, dy: Int, retry: Boolean = false) {
        if (ensure()) return
        val curKey = current ?: return
        val cur = targets[curKey]
        if (dy == 0 && cur?.onAdjust != null) {
            cur?.onAdjust?.invoke(dx)
            return
        }
        val from = currentBounds.center
        val layer = currentLayer
        var best: PadTarget? = null
        var bestScore = Float.MAX_VALUE
        for (t in targets.values) {
            if (t === cur || t.layer != layer || !placed(t)) continue
            if (!onScreen(t.bounds)) continue
            val c = t.bounds.center
            val along = if (dx != 0) (c.x - from.x) * dx else (c.y - from.y) * dy
            val across = abs(if (dx != 0) c.y - from.y else c.x - from.x)
            if (along <= 4f) continue
            val score = along + across * 2.5f
            if (score < bestScore) { best = t; bestScore = score }
        }
        if (best != null) {
            setCurrent(best)
            return
        }
        // Nothing that way: if a list is under the highlight, scroll it and try again.
        if (!retry && dy != 0) {
            val sc = scrollers.firstOrNull { it.layer == layer && it.bounds.contains(from) }
            val s = scope
            if (sc != null && s != null) {
                s.launch {
                    sc.scrollBy(dy * sc.bounds.height * 0.5f)
                    delay(140)
                    navigate(dx, dy, true)
                }
            }
        }
    }

    fun activate() {
        if (ensure()) return
        current?.let { targets[it] }?.onClick?.invoke()
    }

    fun updateHover(pos: Offset) {
        val top = topLayer()
        val t = targets.values.lastOrNull { it.layer == top && placed(it) && it.bounds.contains(pos) }
        if (t == null) {
            if (hover != null) hover = null
        } else {
            hover = t.key
            hoverBounds = t.bounds
            hoverCorner = t.corner
            hoverPad = t.pad
            hoverRing = t.ring
        }
    }
}

/** True while the on-screen cursor is over [key]. */
@Composable
fun padHovered(key: Any?): Boolean = key != null && ((Controller.cursorMode && PadNav.hover == key) || PadNav.touched == key)

/** True while the gamepad highlight is on [key]. */
@Composable
fun padHighlighted(key: Any?): Boolean =
    key != null && Controller.padActive && !Controller.cursorMode && PadNav.current == key

/**
 * Registers this element as a gamepad target. Place it early in the modifier chain (before
 * background and padding) so the recorded bounds include the whole button.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.padTarget(
    key: Any?,
    corner: Dp? = 10.dp,
    app: LaunchableApp? = null,
    pad: Dp = 5.dp,
    bring: Boolean = true,
    ring: Boolean = true,
    onAdjust: ((Int) -> Unit)? = null,
    overlay: Boolean = false,
    onClick: () -> Unit = {},
): Modifier = composed {
    if (key == null || !LocalPadEnabled.current) {
        Modifier
    } else {
        val layer = LocalPadLayer.current
        val target = remember(key) { PadTarget(key) }
        target.layer = layer
        target.corner = corner
        target.app = app
        target.pad = pad
        target.ring = ring
        target.overlay = overlay
        target.onClick = onClick
        target.onAdjust = onAdjust
        DisposableEffect(target) {
            PadNav.targets[key] = target
            onDispose { if (PadNav.targets[key] === target) PadNav.targets.remove(key) }
        }
        val requester = remember { BringIntoViewRequester() }
        val isCurrent = PadNav.current == key
        LaunchedEffect(isCurrent) { if (isCurrent && bring) runCatching { requester.bringIntoView() } }
        Modifier
            .bringIntoViewRequester(requester)
            .onGloballyPositioned { coords ->
                target.bounds = coords.boundsInRoot()
                PadNav.boundsChanged(target)
            }
    }
}

/** A touch-clickable element that the gamepad can also highlight and activate. */
fun Modifier.padClickable(
    key: Any?,
    corner: Dp? = 10.dp,
    app: LaunchableApp? = null,
    pad: Dp = 5.dp,
    ring: Boolean = true,
    onAdjust: ((Int) -> Unit)? = null,
    overlay: Boolean = false,
    onClick: () -> Unit,
): Modifier = this
    .padTarget(key = key, corner = corner, app = app, pad = pad, ring = ring, onAdjust = onAdjust, overlay = overlay, onClick = onClick)
    .touchLit(key)
    .clickable(onClick = onClick)

/**
 * Watches the raw touches on this element (without taking them from the click or from a scrolling parent) and marks [key] as
 * touched while a finger is down on it. Dragging away (a scroll) cancels it, and a quick tap stays lit for a moment after.
 */
internal fun Modifier.touchLit(key: Any?): Modifier = if (key == null) this else composed {
    DisposableEffect(key) { onDispose { if (PadNav.touched == key) PadNav.touched = null } }
    Modifier.pointerInput(key) {
        coroutineScope {
            var releaseJob: Job? = null
            awaitPointerEventScope {
                var downAt: Offset? = null
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull() ?: continue
                    if (c.pressed && !c.previousPressed) {
                        releaseJob?.cancel()
                        downAt = c.position
                        PadNav.touched = key
                    } else if (c.pressed) {
                        val start = downAt
                        if (start != null && (c.position - start).getDistance() > viewConfiguration.touchSlop * 1.5f) {
                            downAt = null
                            PadNav.touched = null
                        }
                    } else if (c.previousPressed) {
                        if (downAt != null) {
                            releaseJob?.cancel()
                            releaseJob = launch { delay(150); if (PadNav.touched == key) PadNav.touched = null }
                        }
                        downAt = null
                    }
                }
            }
        }
    }
}

/** Lets the gamepad scroll this list when the highlight reaches its edge. Place it before the scroll modifier. */
fun Modifier.padScroller(scrollBy: suspend (Float) -> Unit): Modifier = composed {
    val layer = LocalPadLayer.current
    val scroller = remember { PadScroller() }
    scroller.layer = layer
    scroller.scrollBy = scrollBy
    DisposableEffect(scroller) {
        PadNav.scrollers.add(scroller)
        onDispose { PadNav.scrollers.remove(scroller) }
    }
    Modifier.onGloballyPositioned { scroller.bounds = it.boundsInRoot() }
}

/**
 * The highlight itself: one ring that glides between items with a spring, drawn above everything
 * so it is never clipped. White while the on-screen cursor hovers something.
 */
@Composable
fun PadRing() {
    val padShown = Controller.padActive && !Controller.cursorMode && Controller.movingPackage == null &&
        PadNav.current != null && PadNav.currentBounds != Rect.Zero && PadNav.currentRing
    val hoverShown = !padShown && Controller.cursorMode && PadNav.hover != null && PadNav.hoverBounds != Rect.Zero && PadNav.hoverRing
    val visible = padShown || hoverShown
    val target = if (hoverShown) PadNav.hoverBounds else PadNav.currentBounds
    val corner = if (hoverShown) PadNav.hoverCorner else PadNav.currentCorner
    val pad = if (hoverShown) PadNav.hoverPad else PadNav.currentPad

    val anim = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    LaunchedEffect(target) {
        if (target != Rect.Zero) {
            if (anim.value == Rect.Zero) anim.snapTo(target)
            else anim.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 480f))
        }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(160), label = "ringAlpha")
    val pulse = rememberPulse(visible)
    val color = if (hoverShown) Color.White else FocusYellow
    val rect = anim.value
    if (alpha > 0.01f && rect != Rect.Zero) {
        Canvas(Modifier.fillMaxSize()) {
            val p = pad.toPx()
            val topLeft = Offset(rect.left - p, rect.top - p)
            val size = Size(rect.width + 2 * p, rect.height + 2 * p)
            val radius = corner?.toPx()?.plus(p) ?: (min(size.width, size.height) / 2f)
            val cr = CornerRadius(radius, radius)
            // Buttons that sit on top of others (the tiles over a LiveArea) stay clear of everyone else's highlight.
            val cur = PadNav.current
            val covers = if (hoverShown) emptyList() else PadNav.targets.values.filter { it.overlay && it.key != cur && it.bounds != Rect.Zero }.map { it.bounds }
            clipOutRects(covers, 0) {
                drawRoundRect(color.copy(alpha = 0.20f * pulse * alpha), topLeft, size, cr, style = Stroke(width = (8.dp + pad * 2f).toPx()))
                drawRoundRect(color.copy(alpha = 0.45f * pulse * alpha), topLeft, size, cr, style = Stroke(width = (4.dp + pad * 1.2f).toPx()))
                drawRoundRect(color.copy(alpha = alpha), topLeft, size, cr, style = Stroke(width = 4.dp.toPx()))
            }
        }
    }
}

/** Draws [block] with every rectangle in [rects] (from [index] on) cut out of the drawing area. */
private fun DrawScope.clipOutRects(rects: List<Rect>, index: Int, block: DrawScope.() -> Unit) {
    if (index >= rects.size) { block(); return }
    val r = rects[index]
    clipRect(r.left, r.top, r.right, r.bottom, ClipOp.Difference) { clipOutRects(rects, index + 1, block) }
}
