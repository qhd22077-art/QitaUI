package com.qita.ui.ui

import android.annotation.SuppressLint
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.qita.ui.Controller
import com.qita.ui.FLASH_ACTIONS
import com.qita.ui.FLASH_BUTTONS
import com.qita.ui.FlashBindings
import com.qita.ui.FlashBindingsStore
import com.qita.ui.FlashInputHandler
import com.qita.ui.FlashPresets
import com.qita.ui.FlashWeb
import com.qita.ui.Game
import com.qita.ui.STICK_NAMES
import com.qita.ui.findMainActivity
import com.qita.ui.flashActionLabel
import kotlinx.coroutines.delay

/**
 * A Flash game, played by Ruffle in a web view that is served entirely from the app (so it works offline). The gamepad is turned
 * into keys and the mouse pointer by [FlashInputHandler]. Select and Start together, the round button in the corner, or Back open the
 * menu: resume, change the controls (kept per game), exit.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FlashScreen(game: Game, onClose: () -> Unit) {
    val context = LocalContext.current
    var bindings by remember { mutableStateOf(FlashBindingsStore.load(context, game.id)) }
    var menu by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(true) }
    val webHolder = remember { arrayOfNulls<WebView>(1) }

    val handler = remember {
        FlashInputHandler(
            bindings,
            send = { key, down -> webHolder[0]?.evaluateJavascript("qitaKey('${key.id}','${key.key}',${key.keyCode},$down)", null) },
            onMenu = { menu = true },
        )
    }
    SideEffect { handler.bindings = bindings }

    // While the game is playing it gets the gamepad (and, if the controls use the mouse, the pointer); in the menu the launcher does.
    val cursorBefore = remember { Controller.cursorMode }
    LaunchedEffect(menu, bindings) {
        if (menu) {
            handler.releaseAll()
            Controller.flashInput = null
            Controller.cursorMode = false
        } else {
            Controller.flashInput = handler
            Controller.cursorMode = bindings.usesMouse
        }
    }
    LaunchedEffect(Unit) { delay(5000); hint = false }

    // Pause the game's sound and work while the launcher is in the background.
    DisposableEffect(Unit) {
        val lifecycle = context.findMainActivity()?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) webHolder[0]?.onPause()
            if (event == Lifecycle.Event.ON_RESUME) webHolder[0]?.onResume()
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            handler.releaseAll()
            Controller.flashInput = null
            Controller.cursorMode = cursorBefore
            webHolder[0]?.let { runCatching { it.stopLoading(); it.destroy() } }
            webHolder[0] = null
        }
    }

    BackHandler(enabled = true) {
        if (controls) controls = false else menu = !menu
    }

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                            FlashWeb.serve(ctx, game, request.url)

                        // If the web view's own process dies the launcher must not go with it.
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = true
                    }
                    webHolder[0] = this
                    loadUrl(FlashWeb.START_URL)
                }
            },
        )

        // A round button for touch, in case there is no gamepad.
        if (!menu) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(interactionSource = NoRipple, indication = null) { menu = true },
                contentAlignment = Alignment.Center,
            ) { Text("☰", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp) }
            if (hint) {
                Text(
                    "Select + Start: menu",
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                )
            }
        }

        if (menu) {
            CompositionLocalProvider(LocalPadLayer provides 4) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).pointerInput(Unit) { detectTapGestures { menu = false } },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .padding(24.dp)
                            .fillMaxWidth(0.86f)
                            .background(Color(0xFF2B2B2B), RoundedCornerShape(16.dp))
                            .pointerInput(Unit) { detectTapGestures { } }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(game.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!controls) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BarButton("flash:resume", "Resume") { menu = false }
                                BarButton("flash:controls", "Controls") { controls = true }
                                BarButton("flash:exit", "Exit game") { onClose() }
                            }
                            Text(
                                "Select + Start together opens this menu while you play. Games that need the mouse can use a stick as the pointer (see Controls).",
                                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                            )
                        } else {
                            FlashControls(
                                bindings = bindings,
                                onChange = { bindings = it; FlashBindingsStore.save(context, game.id, it) },
                                onDefault = { FlashBindingsStore.saveDefault(context, bindings) },
                                onBack = { controls = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The pad inputs and what each does in this game: tap a row to go to the next choice (left or right on the gamepad steps either way). */
@Composable
private fun FlashControls(bindings: FlashBindings, onChange: (FlashBindings) -> Unit, onDefault: () -> Unit, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Start from", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlChip("flash:pre:arrows", "Arrows + Z X") { onChange(FlashPresets.ARROWS) }
            ControlChip("flash:pre:wasd", "WASD + mouse") { onChange(FlashPresets.WASD) }
            ControlChip("flash:pre:mouse", "Point and click") { onChange(FlashPresets.MOUSE) }
        }
        Column(
            Modifier
                .heightIn(max = 300.dp)
                .padScroller { scroll.animateScrollBy(it) }
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((code, name) in FLASH_BUTTONS) {
                val current = bindings.action(code)
                fun step(dir: Int) {
                    val n = FLASH_ACTIONS.size
                    val i = FLASH_ACTIONS.indexOf(current).coerceAtLeast(0)
                    onChange(bindings.with(code, FLASH_ACTIONS[(i + dir + n) % n]))
                }
                ControlRow("flash:b:$code", name, flashActionLabel(current), onAdjust = { step(it) }) { step(1) }
            }
            ControlRow(
                "flash:ls", "Left stick", STICK_NAMES[bindings.leftStick.coerceIn(STICK_NAMES.indices)],
                onAdjust = { d -> onChange(bindings.withSticks((bindings.leftStick + d + STICK_NAMES.size) % STICK_NAMES.size, bindings.rightStick)) },
            ) { onChange(bindings.withSticks((bindings.leftStick + 1) % STICK_NAMES.size, bindings.rightStick)) }
            ControlRow(
                "flash:rs", "Right stick", STICK_NAMES[bindings.rightStick.coerceIn(STICK_NAMES.indices)],
                onAdjust = { d -> onChange(bindings.withSticks(bindings.leftStick, (bindings.rightStick + d + STICK_NAMES.size) % STICK_NAMES.size)) },
            ) { onChange(bindings.withSticks(bindings.leftStick, (bindings.rightStick + 1) % STICK_NAMES.size)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlChip("flash:default", "Use for all games") { onDefault() }
            ControlChip("flash:back", "Done") { onBack() }
        }
        Text("Changes are saved for this game as you make them.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
private fun ControlRow(key: String, name: String, value: String, onAdjust: (Int) -> Unit, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Row(
        Modifier
            .fillMaxWidth()
            .padClickable(key, corner = 8.dp, pad = 2.dp, ring = false, onAdjust = onAdjust, onClick = onClick)
            .litEdge(lit, 8.dp)
            .background(Color.White.copy(alpha = if (lit) 0.22f else 0.10f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = if (lit) 0.9f else 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, Modifier.weight(1f), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Color(0xFF9CE0FF), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1)
    }
}

@Composable
private fun ControlChip(key: String, label: String, onClick: () -> Unit) {
    val lit = padHighlighted(key) || padHovered(key)
    Text(
        label,
        Modifier
            .padClickable(key, corner = 14.dp, pad = 2.dp, ring = false, onClick = onClick)
            .litEdge(lit, 14.dp)
            .background(Color.White.copy(alpha = if (lit) 0.30f else 0.14f), RoundedCornerShape(14.dp))
            .border(if (lit) 2.dp else 1.dp, Color.White.copy(alpha = if (lit) 0.95f else 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
    )
}
