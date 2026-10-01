package com.qita.ui.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import com.qita.ui.DlKind
import com.qita.ui.DownloadEngine
import com.qita.ui.DownloadPrefs
import com.qita.ui.FormPost
import java.io.File
import java.io.FileOutputStream

/** The latest form a page submitted with POST, so that a download it starts can be asked for again the same way. */
internal object FormCapture {
    private class Snap(val action: String, val body: String, val type: String, val time: Long)
    @Volatile private var last: Snap? = null

    /** Listens for form submissions (and form.submit() calls) and reports the ones sent with POST as plain URL-encoded fields. */
    private const val SCRIPT = """(function(){if(window.__qitaForms)return;window.__qitaForms=1;
function snap(f,sub){try{var m=(f.method||'get').toLowerCase();if(m!=='post')return;
var t=(f.enctype||'application/x-www-form-urlencoded').toLowerCase();if(t.indexOf('multipart')>=0)return;
var d;try{d=sub?new FormData(f,sub):new FormData(f);}catch(e){d=new FormData(f);}
var p=new URLSearchParams();d.forEach(function(v,k){if(typeof v==='string')p.append(k,v);});
QitaBridge.form(new URL(f.action||location.href,location.href).href,p.toString());}catch(e){}}
document.addEventListener('submit',function(e){snap(e.target,e.submitter);},true);
var s=HTMLFormElement.prototype.submit;HTMLFormElement.prototype.submit=function(){snap(this,null);return s.apply(this,arguments);};})();"""

    fun inject(web: WebView) { web.evaluateJavascript(SCRIPT, null) }

    fun record(action: String, body: String) { last = Snap(action, body, "application/x-www-form-urlencoded", System.currentTimeMillis()) }

    /** The form to send again for a download from [url], if a POST form to that address was submitted a moment ago. */
    fun matching(url: String): FormPost? {
        val s = last ?: return null
        if (System.currentTimeMillis() - s.time > 20_000) return null
        fun bare(u: String) = u.substringBefore('#').substringBefore('?').trimEnd('/')
        return if (bare(s.action) == bare(url)) FormPost(s.body, s.type) else null
    }
}

/** What the page's script can call: report a form, or hand over a file the page built itself (a blob). */
internal class DownloadBridge(private val context: Context) {
    private var file: File? = null
    private var out: FileOutputStream? = null
    private var name = "download"

    @JavascriptInterface fun form(action: String, body: String) { FormCapture.record(action, body) }

    @JavascriptInterface fun blobStart(fileName: String) {
        runCatching { out?.close() }
        name = fileName
        val f = File(DownloadEngine.dir(context), "blob_${System.nanoTime()}.tmp")
        file = f
        out = FileOutputStream(f)
    }

    @JavascriptInterface fun blobChunk(base64: String) {
        runCatching { out?.write(android.util.Base64.decode(base64, android.util.Base64.DEFAULT)) }
    }

    @JavascriptInterface fun blobEnd() {
        val f = file ?: return
        runCatching { out?.close() }
        out = null
        file = null
        // Answers cannot be asked mid-script, so the last answers (or none) are used.
        DownloadEngine.addCompleted(f, name, DownloadPrefs.quietPlan(context))
    }

    @JavascriptInterface fun blobFail(message: String) {
        runCatching { out?.close() }
        file?.delete()
        file = null
        out = null
    }
}

/** Sends the page's own copy of a blob: file to the app, in slices, because only the page can read it. */
private fun fetchBlob(web: WebView, url: String, name: String) {
    val js = "(async function(u,n){try{var r=await fetch(u);var b=await r.blob();QitaBridge.blobStart(n);" +
        "for(var o=0;o<b.size;o+=1048576){var a=new Uint8Array(await b.slice(o,o+1048576).arrayBuffer());var s='';" +
        "for(var i=0;i<a.length;i+=32768)s+=String.fromCharCode.apply(null,a.subarray(i,i+32768));QitaBridge.blobChunk(btoa(s));}" +
        "QitaBridge.blobEnd();}catch(e){QitaBridge.blobFail(String(e));}})(" + org.json.JSONObject.quote(url) + "," + org.json.JSONObject.quote(name) + ")"
    web.post { web.evaluateJavascript(js, null) }
}

/**
 * Makes downloads from a web view go through the launcher's engine. Besides plain links this covers sites that start a download
 * by submitting a form (the form is sent again by the engine) and files a page builds itself (blob: links).
 */
internal fun installDownloadHandling(web: WebView, context: Context) {
    web.addJavascriptInterface(DownloadBridge(context.applicationContext), "QitaBridge")
    web.setDownloadListener { url, userAgent, disposition, mime, _ ->
        val name = URLUtil.guessFileName(url, disposition, mime)
        if (url.startsWith("blob:")) {
            fetchBlob(web, url, name)
        } else {
            val kind = if (name.endsWith(".apk", true)) DlKind.APK else DlKind.FILE
            DownloadEngine.request(url, name, kind, CookieManager.getInstance().getCookie(url), userAgent, web.url, post = FormCapture.matching(url))
        }
    }
}

/** What a browser tab tells the screen about its page. */
internal class BrowserHooks(
    val onPageStarted: (String) -> Unit,
    val onPageFinished: (url: String, title: String) -> Unit,
    val onProgress: (Int) -> Unit,
    /** A link or script asked for a new window or tab. */
    val onNewTab: (String) -> Unit,
)

/** A web view set up for browsing: scrolls and takes taps inside the launcher, and looks like a plain mobile (or desktop) Chrome. */
internal fun createBrowserWebView(context: Context, desktop: Boolean): WebView = WebView(context).apply {
    // "this.settings" is the web view's own; a bare "settings" could be something else.
    val ws = this.settings
    ws.javaScriptEnabled = true
    ws.domStorageEnabled = true
    ws.databaseEnabled = true
    ws.builtInZoomControls = true
    ws.displayZoomControls = false
    ws.javaScriptCanOpenWindowsAutomatically = true
    ws.setSupportMultipleWindows(true)
    ws.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    applyWebMode(context, this, desktop)
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
    overScrollMode = View.OVER_SCROLL_NEVER
    isNestedScrollingEnabled = true
    isFocusable = true
    isFocusableInTouchMode = true
    ws.setOffscreenPreRaster(true)
    setOnTouchListener { v, e ->
        if (e.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
        false
    }
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
}

/** Connects a browser web view to its screen: page events, certificate warnings, other-app links, new tabs and downloads. */
internal fun installBrowserClients(web: WebView, context: Context, allowInsecure: MutableSet<String>, hooks: BrowserHooks) {
    web.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { view?.let { FormCapture.inject(it) }; if (url != null) hooks.onPageStarted(url) }
        override fun onPageFinished(view: WebView?, url: String?) { view?.let { FormCapture.inject(it) }; if (url != null) hooks.onPageFinished(url, view?.title.orEmpty()) }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val u = request.url
            when (u.scheme?.lowercase()) {
                "http", "https", "about", "data", "blob" -> return false
                "qita-insecure" -> {
                    val target = Uri.decode(u.schemeSpecificPart.removePrefix("//"))
                    runCatching { allowInsecure.add(Uri.parse(target).host.orEmpty()) }
                    view.loadUrl(target)
                    return true
                }
                "file", "content", "javascript" -> return true
                "intent" -> {
                    runCatching {
                        val intent = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                        intent.getStringExtra("browser_fallback_url")?.let { view.loadUrl(it) }
                    }
                    return true
                }
                else -> {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    return true
                }
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) showPageError(view, request.url.toString(), error.description?.toString().orEmpty(), false)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            val host = runCatching { Uri.parse(error.url).host.orEmpty() }.getOrDefault("")
            if (host in allowInsecure) handler.proceed()
            else { handler.cancel(); showPageError(view, error.url, "The site's security certificate is not trusted", true) }
        }
    }
    web.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) { hooks.onProgress(newProgress) }

        // A link that opens a new window (target="_blank", window.open) becomes a new tab.
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val popup = WebView(view.context)
            popup.settings.javaScriptEnabled = true
            var done = false
            fun take(url: String?) {
                if (done || url.isNullOrBlank() || url == "about:blank") return
                done = true
                hooks.onNewTab(url)
                view.post { runCatching { popup.stopLoading(); popup.destroy() } }
            }
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean { take(r.url.toString()); return true }
                override fun onPageStarted(v: WebView?, url: String?, favicon: android.graphics.Bitmap?) { take(url) }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }
    installDownloadHandling(web, context)
}
