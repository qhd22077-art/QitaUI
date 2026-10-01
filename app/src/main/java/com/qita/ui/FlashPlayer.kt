package com.qita.ui

import android.content.Context
import android.net.Uri
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebResourceResponse
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Flash games are played by Ruffle (an open-source Flash emulator, MIT or Apache licensed) running in a web view. Everything it needs,
 * the game included, is served from the app itself, so it works without a connection. The gamepad is turned into keyboard keys and
 * mouse movement, which is all a Flash game can read.
 */

/** A key a game can be given: [id] is how it is saved and named to the page ([code] and [key] as a keyboard event has them). */
class FlashKey(val id: String, val label: String, val key: String, val keyCode: Int)

val FLASH_KEYS: List<FlashKey> = listOf(
    FlashKey("ArrowUp", "Up arrow", "ArrowUp", 38),
    FlashKey("ArrowDown", "Down arrow", "ArrowDown", 40),
    FlashKey("ArrowLeft", "Left arrow", "ArrowLeft", 37),
    FlashKey("ArrowRight", "Right arrow", "ArrowRight", 39),
    FlashKey("KeyW", "W", "w", 87), FlashKey("KeyA", "A", "a", 65), FlashKey("KeyS", "S", "s", 83), FlashKey("KeyD", "D", "d", 68),
    FlashKey("KeyZ", "Z", "z", 90), FlashKey("KeyX", "X", "x", 88), FlashKey("KeyC", "C", "c", 67), FlashKey("KeyV", "V", "v", 86),
    FlashKey("KeyJ", "J", "j", 74), FlashKey("KeyK", "K", "k", 75), FlashKey("KeyL", "L", "l", 76),
    FlashKey("KeyQ", "Q", "q", 81), FlashKey("KeyE", "E", "e", 69), FlashKey("KeyR", "R", "r", 82), FlashKey("KeyF", "F", "f", 70),
    FlashKey("KeyP", "P", "p", 80), FlashKey("KeyM", "M", "m", 77), FlashKey("KeyN", "N", "n", 78), FlashKey("KeyB", "B", "b", 66),
    FlashKey("Space", "Space", " ", 32), FlashKey("Enter", "Enter", "Enter", 13), FlashKey("Escape", "Esc", "Escape", 27),
    FlashKey("ShiftLeft", "Shift", "Shift", 16), FlashKey("ControlLeft", "Ctrl", "Control", 17), FlashKey("Tab", "Tab", "Tab", 9),
    FlashKey("Digit1", "1", "1", 49), FlashKey("Digit2", "2", "2", 50), FlashKey("Digit3", "3", "3", 51), FlashKey("Digit4", "4", "4", 52),
)

/** What a pad input can do: nothing, press a key, or click the mouse (with the pointer from a stick). */
const val FLASH_NONE = ""
const val FLASH_CLICK = "Click"

val FLASH_ACTIONS: List<String> = listOf(FLASH_NONE) + FLASH_KEYS.map { it.id } + FLASH_CLICK

fun flashActionLabel(id: String): String = when (id) {
    FLASH_NONE -> "Nothing"
    FLASH_CLICK -> "Mouse click"
    else -> FLASH_KEYS.firstOrNull { it.id == id }?.label ?: id
}

/** The pad buttons a game can bind, in the order they are listed. */
val FLASH_BUTTONS: List<Pair<Int, String>> = listOf(
    KeyEvent.KEYCODE_DPAD_UP to "D-pad up", KeyEvent.KEYCODE_DPAD_DOWN to "D-pad down",
    KeyEvent.KEYCODE_DPAD_LEFT to "D-pad left", KeyEvent.KEYCODE_DPAD_RIGHT to "D-pad right",
    KeyEvent.KEYCODE_BUTTON_A to "A", KeyEvent.KEYCODE_BUTTON_B to "B", KeyEvent.KEYCODE_BUTTON_X to "X", KeyEvent.KEYCODE_BUTTON_Y to "Y",
    KeyEvent.KEYCODE_BUTTON_L1 to "L1", KeyEvent.KEYCODE_BUTTON_R1 to "R1", KeyEvent.KEYCODE_BUTTON_L2 to "L2", KeyEvent.KEYCODE_BUTTON_R2 to "R2",
    KeyEvent.KEYCODE_BUTTON_THUMBL to "L3 (left stick press)", KeyEvent.KEYCODE_BUTTON_THUMBR to "R3 (right stick press)",
    KeyEvent.KEYCODE_BUTTON_START to "Start", KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
)

/** What a stick does: nothing, the four arrow keys, W A S D, or the mouse pointer. */
const val STICK_NONE = 0
const val STICK_ARROWS = 1
const val STICK_WASD = 2
const val STICK_MOUSE = 3
val STICK_NAMES = listOf("Nothing", "Arrow keys", "W A S D", "Mouse pointer")

/** What every pad input does in one game. */
class FlashBindings(val buttons: Map<Int, String>, val leftStick: Int, val rightStick: Int) {
    fun action(code: Int): String = buttons[code] ?: FLASH_NONE
    fun with(code: Int, action: String) = FlashBindings(buttons + (code to action), leftStick, rightStick)
    fun withSticks(left: Int, right: Int) = FlashBindings(buttons, left, right)
    /** True if the pointer is used, so the launcher's cursor has to be on. */
    val usesMouse: Boolean get() = leftStick == STICK_MOUSE || rightStick == STICK_MOUSE || buttons.values.any { it == FLASH_CLICK }

    fun toJson(): JSONObject = JSONObject()
        .put("b", JSONObject().also { o -> buttons.forEach { (k, v) -> o.put(k.toString(), v) } })
        .put("l", leftStick).put("r", rightStick)

    companion object {
        fun fromJson(o: JSONObject): FlashBindings {
            val b = o.optJSONObject("b")
            val map = HashMap<Int, String>()
            for ((code, _) in FLASH_BUTTONS) map[code] = b?.optString(code.toString(), FLASH_NONE) ?: FLASH_NONE
            return FlashBindings(map, o.optInt("l", STICK_ARROWS), o.optInt("r", STICK_NONE))
        }
    }
}

/** Ready-made sets of bindings. */
object FlashPresets {
    private fun make(left: Int, right: Int, vararg pairs: Pair<Int, String>) = FlashBindings(
        FLASH_BUTTONS.associate { (code, _) -> code to FLASH_NONE } + pairs.toMap(), left, right,
    )

    /** Arrow keys for moving, Z X C V and the shoulders for actions: most Flash platformers and shooters. */
    val ARROWS = make(
        STICK_ARROWS, STICK_NONE,
        KeyEvent.KEYCODE_DPAD_UP to "ArrowUp", KeyEvent.KEYCODE_DPAD_DOWN to "ArrowDown", KeyEvent.KEYCODE_DPAD_LEFT to "ArrowLeft", KeyEvent.KEYCODE_DPAD_RIGHT to "ArrowRight",
        KeyEvent.KEYCODE_BUTTON_A to "KeyZ", KeyEvent.KEYCODE_BUTTON_B to "KeyX", KeyEvent.KEYCODE_BUTTON_X to "KeyC", KeyEvent.KEYCODE_BUTTON_Y to "KeyV",
        KeyEvent.KEYCODE_BUTTON_L1 to "KeyA", KeyEvent.KEYCODE_BUTTON_R1 to "KeyS", KeyEvent.KEYCODE_BUTTON_L2 to "ShiftLeft", KeyEvent.KEYCODE_BUTTON_R2 to "Space",
        KeyEvent.KEYCODE_BUTTON_START to "Enter", KeyEvent.KEYCODE_BUTTON_SELECT to "Escape",
    )

    /** W A S D for moving and the mouse pointer on the right stick: shooters and many newer games. */
    val WASD = make(
        STICK_WASD, STICK_MOUSE,
        KeyEvent.KEYCODE_DPAD_UP to "KeyW", KeyEvent.KEYCODE_DPAD_DOWN to "KeyS", KeyEvent.KEYCODE_DPAD_LEFT to "KeyA", KeyEvent.KEYCODE_DPAD_RIGHT to "KeyD",
        KeyEvent.KEYCODE_BUTTON_A to "Space", KeyEvent.KEYCODE_BUTTON_B to "KeyE", KeyEvent.KEYCODE_BUTTON_X to "KeyQ", KeyEvent.KEYCODE_BUTTON_Y to "KeyR",
        KeyEvent.KEYCODE_BUTTON_L1 to "ShiftLeft", KeyEvent.KEYCODE_BUTTON_R1 to "ControlLeft", KeyEvent.KEYCODE_BUTTON_L2 to "KeyF", KeyEvent.KEYCODE_BUTTON_R2 to FLASH_CLICK,
        KeyEvent.KEYCODE_BUTTON_START to "Enter", KeyEvent.KEYCODE_BUTTON_SELECT to "Escape",
    )

    /** Point and click games: the left stick is the mouse and A clicks. */
    val MOUSE = make(
        STICK_MOUSE, STICK_NONE,
        KeyEvent.KEYCODE_DPAD_UP to "ArrowUp", KeyEvent.KEYCODE_DPAD_DOWN to "ArrowDown", KeyEvent.KEYCODE_DPAD_LEFT to "ArrowLeft", KeyEvent.KEYCODE_DPAD_RIGHT to "ArrowRight",
        KeyEvent.KEYCODE_BUTTON_A to FLASH_CLICK, KeyEvent.KEYCODE_BUTTON_B to "Space", KeyEvent.KEYCODE_BUTTON_X to "KeyZ", KeyEvent.KEYCODE_BUTTON_Y to "KeyX",
        KeyEvent.KEYCODE_BUTTON_L1 to "KeyA", KeyEvent.KEYCODE_BUTTON_R1 to "KeyS", KeyEvent.KEYCODE_BUTTON_R2 to FLASH_CLICK,
        KeyEvent.KEYCODE_BUTTON_START to "Enter", KeyEvent.KEYCODE_BUTTON_SELECT to "Escape",
    )
}

/** Where each game's bindings are kept: one set per game, and a default for games without their own. */
object FlashBindingsStore {
    private fun prefs(c: Context) = c.getSharedPreferences("qita_flash", Context.MODE_PRIVATE)

    fun load(c: Context, gameId: String): FlashBindings {
        val p = prefs(c)
        val text = p.getString("g:$gameId", null) ?: p.getString("default", null)
        return runCatching { FlashBindings.fromJson(JSONObject(text!!)) }.getOrDefault(FlashPresets.ARROWS)
    }

    fun save(c: Context, gameId: String, b: FlashBindings) { prefs(c).edit().putString("g:$gameId", b.toJson().toString()).apply() }

    fun saveDefault(c: Context, b: FlashBindings) { prefs(c).edit().putString("default", b.toJson().toString()).apply() }
}

/**
 * Turns pad input into keys sent to the Flash page. [send] delivers a key going down or up; the cursor of the launcher is the mouse
 * (a stick moves it, [FLASH_CLICK] holds A). Several inputs can hold the same key at once (D-pad and stick); it goes up when all let go.
 */
class FlashInputHandler(
    var bindings: FlashBindings,
    private val send: (FlashKey, Boolean) -> Unit,
    private val onMenu: () -> Unit,
) : FlashInput {
    private val sources = HashMap<String, String>()
    private val counts = HashMap<String, Int>()
    private var startDown = false
    private var selectDown = false

    private fun press(id: String) {
        if (id == FLASH_CLICK) { Controller.aHeld = true; return }
        val n = (counts[id] ?: 0) + 1
        counts[id] = n
        if (n == 1) FLASH_KEYS.firstOrNull { it.id == id }?.let { send(it, true) }
    }

    private fun release(id: String) {
        if (id == FLASH_CLICK) { Controller.aHeld = false; return }
        val n = (counts[id] ?: 0) - 1
        if (n <= 0) {
            counts.remove(id)
            FLASH_KEYS.firstOrNull { it.id == id }?.let { send(it, false) }
        } else counts[id] = n
    }

    /** Input [source] now holds [id] (or nothing): the old key goes up and the new one down. */
    private fun hold(source: String, id: String?) {
        val old = sources[source]
        if (old == id) return
        if (old != null) { sources.remove(source); release(old) }
        if (id != null && id != FLASH_NONE) { sources[source] = id; press(id) }
    }

    /** Lets go of everything held (the menu opens, or the game closes), so no key stays down. */
    fun releaseAll() {
        sources.keys.toList().forEach { hold(it, null) }
        Controller.stickX = 0f
        Controller.stickY = 0f
        Controller.scrollY = 0f
    }

    override fun onKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        if (down && event.repeatCount > 0) return true
        if (code == KeyEvent.KEYCODE_BUTTON_START) startDown = down
        if (code == KeyEvent.KEYCODE_BUTTON_SELECT) selectDown = down
        // Start and Select together open the menu (each alone is a game button).
        if (down && startDown && selectDown) {
            startDown = false
            selectDown = false
            releaseAll()
            onMenu()
            return true
        }
        hold("key:$code", if (down) bindings.action(code) else null)
        return true
    }

    private fun strongest(a: Float, b: Float) = if (abs(a) >= abs(b)) a else b

    private fun stick(mode: Int, x: Float, y: Float, name: String) {
        if (mode != STICK_ARROWS && mode != STICK_WASD) {
            for (d in listOf("l", "r", "u", "d")) hold("$name:$d", null)
            return
        }
        val ids = if (mode == STICK_ARROWS) listOf("ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown") else listOf("KeyA", "KeyD", "KeyW", "KeyS")
        fun on(d: String) = sources.containsKey("$name:$d")
        fun t(d: String) = if (on(d)) 0.35f else 0.55f
        hold("$name:l", if (x < -t("l")) ids[0] else null)
        hold("$name:r", if (x > t("r")) ids[1] else null)
        hold("$name:u", if (y < -t("u")) ids[2] else null)
        hold("$name:d", if (y > t("d")) ids[3] else null)
    }

    override fun onMotion(event: MotionEvent): Boolean {
        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        val rx = strongest(event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RX))
        val ry = strongest(event.getAxisValue(MotionEvent.AXIS_RZ), event.getAxisValue(MotionEvent.AXIS_RY))
        stick(bindings.leftStick, lx, ly, "sl")
        stick(bindings.rightStick, rx, ry, "sr")
        // The pointer follows a stick set to Mouse (the left one if both are).
        when {
            bindings.leftStick == STICK_MOUSE -> { Controller.stickX = lx; Controller.stickY = ly }
            bindings.rightStick == STICK_MOUSE -> { Controller.stickX = rx; Controller.stickY = ry }
            else -> { Controller.stickX = 0f; Controller.stickY = 0f }
        }
        Controller.scrollY = 0f
        // Triggers that only report an axis count as L2 / R2.
        val lt = max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        hold("ax:l2", if (lt > 0.6f) bindings.action(KeyEvent.KEYCODE_BUTTON_L2) else null)
        hold("ax:r2", if (rt > 0.6f) bindings.action(KeyEvent.KEYCODE_BUTTON_R2) else null)
        // A D-pad reported as a hat axis.
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        hold("hat:l", if (hx < -0.5f) bindings.action(KeyEvent.KEYCODE_DPAD_LEFT) else null)
        hold("hat:r", if (hx > 0.5f) bindings.action(KeyEvent.KEYCODE_DPAD_RIGHT) else null)
        hold("hat:u", if (hy < -0.5f) bindings.action(KeyEvent.KEYCODE_DPAD_UP) else null)
        hold("hat:d", if (hy > 0.5f) bindings.action(KeyEvent.KEYCODE_DPAD_DOWN) else null)
        return true
    }
}

/** What the Flash page asks for, served from the app (no network): the page, Ruffle's files, and the game's own file. */
object FlashWeb {
    const val HOST = "appassets.local"
    const val START_URL = "https://$HOST/player.html"

    private val PAGE = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
        #c{position:fixed;inset:0}#m{position:fixed;left:0;right:0;top:40%;color:#9ab;font:16px sans-serif;text-align:center;padding:0 24px;white-space:pre-wrap}</style>
        <script>
        window.RufflePlayer = window.RufflePlayer || {};
        window.RufflePlayer.config = {
          publicPath: "/ruffle/", autoplay: "on", unmuteOverlay: "hidden", splashScreen: false, letterbox: "on",
          contextMenu: "off", warnOnUnsupportedContent: false, logLevel: "warn", showSwfDownload: false
        };
        </script>
        <script src="/ruffle/ruffle.js"></script></head>
        <body><div id="c"></div><div id="m">Starting Flash player...</div>
        <script>
        function say(t){var m=document.getElementById("m");m.style.display=t?"block":"none";m.textContent=t||"";}
        var player=null;
        function canvasOf(){return player&&player.shadowRoot&&player.shadowRoot.querySelector("canvas");}
        window.qitaKey=function(code,key,keyCode,down){
          var target=canvasOf()||player||document.body;
          var ev=new KeyboardEvent(down?"keydown":"keyup",{key:key,code:code,bubbles:true,cancelable:true,composed:true});
          try{Object.defineProperty(ev,"keyCode",{get:function(){return keyCode;}});Object.defineProperty(ev,"which",{get:function(){return keyCode;}});}catch(e){}
          target.dispatchEvent(ev);
        };
        window.qitaFocus=function(){try{if(player)player.focus();}catch(e){}};
        try{
          var rf=window.RufflePlayer.newest();
          player=rf.createPlayer();
          player.style.width="100%";player.style.height="100%";
          document.getElementById("c").appendChild(player);
          var p=player.load({url:"/game.swf"});
          if(p&&p.then){p.then(function(){say("");window.qitaFocus();},function(e){say("This game could not be started:\n"+e);});}
          else{say("");}
          setTimeout(window.qitaFocus,500);
        }catch(e){say("The Flash player could not start:\n"+e);}
        </script></body></html>
    """.trimIndent()

    private fun response(mime: String, stream: java.io.InputStream, encoding: String? = null) =
        WebResourceResponse(mime, encoding, 200, "OK", mapOf("Cache-Control" to "no-store"), stream)

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun mimeOf(path: String) = when (path.substringAfterLast('.', "").lowercase()) {
        "js" -> "text/javascript"
        "wasm" -> "application/wasm"
        "html" -> "text/html"
        else -> "application/octet-stream"
    }

    /** The answer to a request the page made. Anything that is not the app's own is refused, so a game cannot reach the network. */
    fun serve(context: Context, game: Game, uri: Uri): WebResourceResponse {
        if (uri.host != HOST) return notFound()
        val path = uri.path.orEmpty()
        return runCatching {
            when {
                path == "/player.html" -> response("text/html", ByteArrayInputStream(PAGE.toByteArray()), "utf-8")
                path == "/game.swf" -> response("application/x-shockwave-flash", context.contentResolver.openInputStream(Uri.parse(game.uri))!!)
                path.startsWith("/ruffle/") && !path.contains("..") -> response(mimeOf(path), context.assets.open("ruffle/" + path.removePrefix("/ruffle/")))
                else -> notFound()
            }
        }.getOrElse { notFound() }
    }
}
