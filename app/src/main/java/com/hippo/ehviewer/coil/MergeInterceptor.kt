/*
 * Copyright 2023 Tarsin Norbin
 *
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
package com.hippo.ehviewer.coil

import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.hippo.ehviewer.client.isNormalPreviewKey
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object MergeInterceptor : Interceptor {
    private val activeRequests = mutableMapOf<String, Deferred<ImageResult>>()

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult = coroutineScope {
        val req = chain.request
        val key = req.memoryCacheKey?.takeIf { it.isNormalPreviewKey } ?: return@coroutineScope chain.proceed()

        val deferred = synchronized(activeRequests) {
            activeRequests.getOrPut(key) {
                async {
                    try {
                        var result: ImageResult
                        var retryCount = 0
                        val maxRetries = 3
                        do {
                            result = chain.proceed()
                            if (result is SuccessResult) break
                            if (result is ErrorResult && retryCount < maxRetries) {
                                retryCount++
                            } else {
                                break
                            }
                        } while (true)
                        result
                    } finally {
                        synchronized(activeRequests) {
                            activeRequests.remove(key)
                        }
                    }
                }
            }
        }

        val result = deferred.await()
        when (result) {
            is SuccessResult -> result.copy(
                request = req,
                dataSource = if (result.request === req || result.dataSource == DataSource.MEMORY_CACHE) result.dataSource else DataSource.MEMORY,
            )

            is ErrorResult -> result.copy(
                request = req,
            )
        }
    }
}
