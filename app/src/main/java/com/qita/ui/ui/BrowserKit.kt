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
import com.qita.ui.DlKind
import com.qita.ui.DownloadEngine

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
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { if (url != null) hooks.onPageStarted(url) }
        override fun onPageFinished(view: WebView?, url: String?) { if (url != null) hooks.onPageFinished(url, view?.title.orEmpty()) }

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
    web.setDownloadListener { url, userAgent, disposition, mime, _ ->
        val name = URLUtil.guessFileName(url, disposition, mime)
        val kind = if (name.endsWith(".apk", true)) DlKind.APK else DlKind.FILE
        DownloadEngine.request(url, name, kind, CookieManager.getInstance().getCookie(url), userAgent, web.url)
    }
}
