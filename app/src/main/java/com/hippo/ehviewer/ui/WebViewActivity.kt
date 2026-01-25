/*
 * Copyright 2026 AdNotFound *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.hippo.ehviewer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.util.setDefaultSettings

class WebViewActivity : EhActivity() {
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.extras?.getString(KEY_URL) ?: return
        webView = WebView(applicationContext).apply {
            setDefaultSettings()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val cloudflareBypassed = EhCookieStore.saveFromWebView(url) {
                        it.name == EhCookieStore.KEY_CLOUDFLARE
                    }
                    if (cloudflareBypassed) {
                        finish()
                    }
                }
            }
        }
        setContentView(webView)
        EhCookieStore.loadForWebView(url) {
            it.name != EhCookieStore.KEY_CLOUDFLARE
        }
        webView!!.loadUrl(url)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.destroy()
        webView = null
    }

    companion object {
        const val KEY_URL = "url"

        fun newIntent(context: Context, url: String): Intent = Intent(context, WebViewActivity::class.java).apply {
            putExtra(KEY_URL, url)
        }
    }
}
