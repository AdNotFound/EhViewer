/*
 * Copyright 2025 Hippo Seven
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

package com.hippo.ehviewer.client

import com.hippo.ehviewer.Settings
import com.hippo.util.LowSpeedException
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException

object SpeedInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val body = response.body
        if (body == null || Settings.timeoutSpeed <= 0) {
            return response
        }

        return response.newBuilder()
            .body(object : ForwardingResponseBody(body) {
                override fun source(): okio.BufferedSource = SpeedMonitorSource(delegate.source(), request.url.toString()).buffer()
            })
            .build()
    }

    private class SpeedMonitorSource(
        delegate: Source,
        private val url: String,
    ) : ForwardingSource(delegate) {
        private var lastTime = System.currentTimeMillis()
        private var bytesReadSinceLastCheck = 0L

        @Throws(IOException::class)
        override fun read(sink: Buffer, byteCount: Long): Long {
            val bytesRead = super.read(sink, byteCount)
            val currentTime = System.currentTimeMillis()
            val interval = currentTime - lastTime

            if (bytesRead != -1L) {
                bytesReadSinceLastCheck += bytesRead
            }

            // Check speed every 1 second
            if (interval >= 1000) {
                val speed = bytesReadSinceLastCheck * 1000 / interval
                val minSpeed = Settings.timeoutSpeed.toLong() * 1024

                if (speed < minSpeed && bytesRead != -1L) {
                    throw LowSpeedException(url, speed)
                }

                lastTime = currentTime
                bytesReadSinceLastCheck = 0
            }

            return bytesRead
        }
    }
}

private abstract class ForwardingResponseBody(
    protected val delegate: okhttp3.ResponseBody,
) : okhttp3.ResponseBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun source() = delegate.source()
    override fun close() = delegate.close()
}
