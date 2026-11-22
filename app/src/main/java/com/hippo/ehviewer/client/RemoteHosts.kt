/*
 * Copyright 2026 Hippo Seven
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

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Hosts
import com.hippo.ehviewer.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A hosts table maintained in the repository (hosts.json) and refreshed on-demand
 * when all known candidate IP addresses for a host fail to connect.
 *
 * Instead of relying on a hardcoded periodic interval, updates are triggered
 * only when connection exhaustion is detected, ensuring zero background traffic
 * while keeping resolution resilient.
 *
 * The last successfully fetched list is cached on disk atomically and used until
 * a failure triggers a successful update.
 */
object RemoteHosts {
    private const val CACHE_FILE_NAME = "remote_hosts.json"
    private const val MIN_UPDATE_INTERVAL_MS = 60_000L

    private val updateUrls = listOf(
        "https://raw.githubusercontent.com/AdNotFound/EhViewer/test/hosts.json",
        "https://cdn.jsdelivr.net/gh/AdNotFound/EhViewer@test/hosts.json",
        "https://cdn.statically.io/gh/AdNotFound/EhViewer/test/hosts.json",
    )

    private val updating = AtomicBoolean(false)

    @Volatile
    private var loaded = false

    @Volatile
    private var lastFailureSyncTime = 0L

    @Volatile
    private var remoteHosts: Map<String, List<InetAddress>> = emptyMap()

    private val cacheFile: File
        get() = File(EhApplication.application.cacheDir, CACHE_FILE_NAME)

    fun lookup(hostname: String): List<InetAddress>? {
        ensureLoaded()
        return remoteHosts[hostname]
    }

    fun contains(hostname: String): Boolean {
        ensureLoaded()
        return remoteHosts.containsKey(hostname)
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (cacheFile.exists()) {
            val parsed = runCatching { parse(cacheFile.readText()) }.getOrNull()
            if (!parsed.isNullOrEmpty()) {
                remoteHosts = parsed
            } else {
                runCatching { cacheFile.delete() }
            }
        }
    }

    /**
     * Called when all candidate IP addresses for [hostname] fail to connect.
     * Triggers a remote hosts refresh and returns true if new hosts were fetched.
     */
    fun onAllHostsFailed(hostname: String): Boolean {
        if (!Settings.builtInHosts) return false
        val now = System.currentTimeMillis()
        if (now - lastFailureSyncTime < MIN_UPDATE_INTERVAL_MS) {
            return false
        }
        if (!isNetworkConnected()) {
            return false
        }
        if (!updating.compareAndSet(false, true)) {
            return false
        }
        try {
            lastFailureSyncTime = now
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            for (url in updateUrls) {
                val body = runCatching {
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (response.isSuccessful) response.body.string() else null
                    }
                }.getOrNull() ?: continue

                val parsed = runCatching { parse(body) }.getOrNull()
                if (parsed.isNullOrEmpty()) continue

                writeAtomically(cacheFile, body)
                remoteHosts = parsed
                loaded = true
                return true
            }
        } finally {
            updating.set(false)
        }
        return false
    }

    private fun isNetworkConnected(): Boolean {
        val cm = EhApplication.application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun writeAtomically(file: File, content: String) {
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(tmpFile).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            if (!tmpFile.renameTo(file)) {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            }
        } catch (_: Exception) {
            tmpFile.delete()
        }
    }

    private fun parse(json: String): Map<String, List<InetAddress>> {
        val hostsJson = JSONObject(json).getJSONObject("hosts")
        val result = mutableMapOf<String, List<InetAddress>>()
        val keys = hostsJson.keys()
        while (keys.hasNext()) {
            val host = keys.next()
            if (!EhDns.isBuiltInHost(host)) continue
            val array = hostsJson.getJSONArray(host)
            val addresses = (0 until array.length()).mapNotNull {
                val ipStr = array.optString(it)
                Hosts.toInetAddress(host, ipStr)?.takeIf { addr -> isSafePublicAddress(addr) }
            }
            if (addresses.isNotEmpty()) {
                result[host] = addresses
            }
        }
        return result
    }

    private fun isSafePublicAddress(address: InetAddress): Boolean = !address.isLoopbackAddress &&
        !address.isSiteLocalAddress &&
        !address.isLinkLocalAddress &&
        !address.isAnyLocalAddress &&
        !address.isMulticastAddress
}
