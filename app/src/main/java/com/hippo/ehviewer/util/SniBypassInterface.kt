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
package com.hippo.ehviewer.util

import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import kotlinx.coroutines.DelicateCoroutinesApi
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.net.URLDecoder

class SniBypassInterface(private val webView: WebView) {
    companion object {
        private val ALLOWED_HOSTS = setOf(
            "e-hentai.org",
            "exhentai.org",
            "lofi.e-hentai.org",
            "forums.e-hentai.org",
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    @JavascriptInterface
    fun postForm(url: String, formData: String) {
        launchIO {
            try {
                val httpUrl = url.toHttpUrl()
                if (httpUrl.host !in ALLOWED_HOSTS) return@launchIO

                // Sync cookies from WebView to OkHttp
                val cookieManager = CookieManager.getInstance()
                val webViewCookies = cookieManager.getCookie(url)
                if (!webViewCookies.isNullOrEmpty()) {
                    webViewCookies.split(";").forEach {
                        Cookie.parse(httpUrl, it.trim())?.let { cookie ->
                            EhCookieStore.addCookie(cookie)
                        }
                    }
                }

                val formBodyBuilder = FormBody.Builder()
                formData.split("&").forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = URLDecoder.decode(parts[0], "UTF-8")
                        val value = URLDecoder.decode(parts[1], "UTF-8")
                        formBodyBuilder.add(key, value)
                    }
                }

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", Settings.userAgent)
                    .header("Referer", url)
                    .post(formBodyBuilder.build())
                    .build()

                val response = EhApplication.okHttpClient.newCall(request).execute()
                val finalUrl = response.request.url.toString()

                // Sync cookies from OkHttp CookieJar to WebView CookieManager
                val finalHttpUrl = finalUrl.toHttpUrl()
                val cookies = EhCookieStore.getCookies(finalHttpUrl)
                cookies.forEach { cookie ->
                    cookieManager.setCookie(finalUrl, cookie.toString())
                }
                cookieManager.flush()

                val bodyString = response.body?.string() ?: ""

                withUIContext {
                    webView.loadDataWithBaseURL(finalUrl, bodyString, "text/html", "UTF-8", finalUrl)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
