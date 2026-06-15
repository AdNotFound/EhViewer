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

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhDns
import okhttp3.Request

object EhWebViewClientHelper {
    fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        val urlString = url.toString()
        val host = url.host
        if (Settings.dF && "GET" == request.method && host != null && EhDns.isInHosts(host)) {
            try {
                val okHttpRequest = Request.Builder()
                    .url(urlString)
                    .apply {
                        request.requestHeaders.forEach { (name, value) ->
                            addHeader(name, value)
                        }
                    }
                    .build()
                val response = EhApplication.okHttpClient.newCall(okHttpRequest).execute()
                if (response.isSuccessful) {
                    response.body.let { body ->
                        val contentType = body.contentType()
                        return WebResourceResponse(
                            contentType?.toString()?.substringBefore(";"),
                            contentType?.charset()?.name(),
                            response.code,
                            response.message,
                            response.headers.toMap(),
                            body.byteStream(),
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    fun injectJavascript(view: WebView) {
        if (Settings.dF) {
            val js = """
                (function() {
                    function proxySubmit(form, submitter) {
                        var formData = new FormData(form);
                        if (submitter && submitter.name) {
                            formData.append(submitter.name, submitter.value || "");
                        }
                        var data = new URLSearchParams(formData).toString();
                        window.SniBridge.postForm(form.action, data);
                    }

                    // Intercept programmatic submit()
                    var originalSubmit = HTMLFormElement.prototype.submit;
                    HTMLFormElement.prototype.submit = function() {
                        proxySubmit(this);
                    };

                    // Intercept standard submit events
                    window.addEventListener('submit', function(e) {
                        if (e.target.tagName === 'FORM') {
                            e.preventDefault();
                            proxySubmit(e.target, e.submitter);
                        }
                    }, true);
                })();
            """.trimIndent()
            view.evaluateJavascript(js, null)
        }
    }
}
