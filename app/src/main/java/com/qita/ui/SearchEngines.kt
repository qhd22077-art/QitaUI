package com.qita.ui

import java.net.URLEncoder

/** The web searches the Browser and the Store can use for words typed in the address bar. */
object SearchEngines {
    fun url(engine: Int, query: String): String {
        val q = URLEncoder.encode(query, "UTF-8")
        return when (engine) {
            1 -> "https://www.bing.com/search?q=$q"
            2 -> "https://duckduckgo.com/?q=$q"
            else -> "https://www.google.com/search?q=$q"
        }
    }
}
