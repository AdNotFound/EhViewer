/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.okhttp

import androidx.webkit.WebViewCompat
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import okhttp3.Request

object UAPresets {
    private val version by lazy {
        runCatching {
            WebViewCompat.getCurrentWebViewPackage(EhApplication.application)
                ?.versionName?.substringBefore('.')?.toInt()
        }.getOrNull() ?: 127
    }

    private fun android(v: String = "") = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) ${v}Chrome/$version.0.0.0 Mobile Safari/537.36"

    const val CHROME_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val FIREFOX_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0"
    const val SAFARI_PC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
    val CHROME_ANDROID get() = android()
    val WEBVIEW_ANDROID get() = android("Version/4.0 ")
}

private const val CHROME_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
private const val CHROME_ACCEPT_LANGUAGE = "en-US,en;q=0.9"

open class ChromeRequestBuilder(url: String, ua: String = Settings.userAgent) : Request.Builder() {
    init {
        this.url(url)
        this.addHeader("User-Agent", ua)
        this.addHeader("Accept", CHROME_ACCEPT)
        this.addHeader("Accept-Language", CHROME_ACCEPT_LANGUAGE)
    }
}
