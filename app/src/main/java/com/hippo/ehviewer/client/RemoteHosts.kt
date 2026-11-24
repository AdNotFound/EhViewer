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
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.UnknownHostException
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
    const val GITHUB_HOST = "raw.githubusercontent.com"
    private const val CACHE_FILE_NAME = "remote_hosts.json"
    private const val MIN_UPDATE_INTERVAL_MS = 60_000L

    private val updateUrls = listOf(
        "https://raw.githubusercontent.com/AdNotFound/EhViewer/test/hosts.json",
        "https://cdn.jsdelivr.net/gh/AdNotFound/EhViewer@test/hosts.json",
        "https://fastly.jsdelivr.net/gh/AdNotFound/EhViewer@test/hosts.json",
        "https://cdn.statically.io/gh/AdNotFound/EhViewer@test/hosts.json",
    )

    private val fallbackDns = Dns { hostname ->
        val builtInIps = runCatching { lookup(hostname) }.getOrNull().orEmpty()
        val dohIps = runCatching { EhDns.lookup(hostname) }.getOrNull().orEmpty()
        val systemIps = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull().orEmpty()
        val combined = (builtInIps + dohIps + systemIps).distinct()
        combined.ifEmpty { throw UnknownHostException(hostname) }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(fallbackDns)
            .proxySelector(EhApplication.ehProxySelector)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val updating = AtomicBoolean(false)

    @Volatile
    private var loaded = false

    @Volatile
    private var lastFailureSyncTime = 0L

    data class Metadata(
        val hosts: Map<String, List<InetAddress>> = emptyMap(),
        val version: Int? = null,
        val updated: String? = null,
    )

    @Volatile
    private var current = Metadata()

    val version: Int?
        get() = current.version

    val updated: String?
        get() = current.updated

    private val cacheFile: File
        get() = File(EhApplication.application.cacheDir, CACHE_FILE_NAME)

    fun lookup(hostname: String): List<InetAddress>? {
        ensureLoaded()
        return current.hosts[hostname]
    }

    fun contains(hostname: String): Boolean {
        ensureLoaded()
        return current.hosts.containsKey(hostname)
    }

    fun getMetadata(): Pair<Int?, String?> {
        ensureLoaded()
        val curr = current
        return Pair(curr.version, curr.updated)
    }

    private data class ParsedHosts(
        val hosts: Map<String, List<InetAddress>>,
        val version: Int?,
        val updated: String?,
    )

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        var cachedMetadata: Metadata? = null

        if (cacheFile.exists()) {
            val content = runCatching { cacheFile.readText() }.getOrNull()
            val parsedCache = content?.let { runCatching { parse(it) }.getOrNull() }
            if (parsedCache != null && parsedCache.hosts.isNotEmpty()) {
                cachedMetadata = Metadata(parsedCache.hosts, parsedCache.version, parsedCache.updated)
            } else {
                runCatching { cacheFile.delete() }
            }
        }

        val assetContent = runCatching {
            EhApplication.application.assets.open("hosts.json").bufferedReader().use { it.readText() }
        }.getOrNull()

        if (assetContent != null) {
            val assetParsed = runCatching { parse(assetContent) }.getOrNull()
            if (assetParsed != null && assetParsed.hosts.isNotEmpty()) {
                val assetVersion = assetParsed.version
                val assetUpdated = assetParsed.updated
                val cachedVersion = cachedMetadata?.version
                val cachedUpdated = cachedMetadata?.updated
                val useAsset = cachedMetadata == null ||
                    (assetVersion ?: 0) > (cachedVersion ?: 0) ||
                    ((assetVersion ?: 0) == (cachedVersion ?: 0) && (assetUpdated ?: "") > (cachedUpdated ?: ""))

                if (useAsset) {
                    current = Metadata(assetParsed.hosts, assetVersion, assetUpdated)
                    runCatching { cacheFile.delete() }
                    return
                }
            }
        }

        if (cachedMetadata != null) {
            current = cachedMetadata
        }
    }

    /**
     * Called when all candidate IP addresses for [hostname] fail to connect.
     * Triggers a remote hosts refresh and returns true if new hosts were fetched.
     */
    fun onAllHostsFailed(hostname: String): Boolean = sync(force = false)

    /**
     * Synchronizes remote hosts table from remote repositories.
     * When [force] is true, ignores failure interval check and builtInHosts setting.
     */
    fun sync(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!force) {
            if (!Settings.builtInHosts) return false
            if (now - lastFailureSyncTime < MIN_UPDATE_INTERVAL_MS) {
                return false
            }
        }
        if (!isNetworkConnected()) {
            return false
        }
        if (!updating.compareAndSet(false, true)) {
            return false
        }
        try {
            lastFailureSyncTime = now
            for (url in updateUrls) {
                val body = runCatching {
                    httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (response.isSuccessful) response.body.string() else null
                    }
                }.getOrNull() ?: continue

                val parsed = runCatching { parse(body) }.getOrNull() ?: continue
                if (parsed.hosts.isEmpty()) continue

                writeAtomically(cacheFile, body)
                current = Metadata(parsed.hosts, parsed.version, parsed.updated)
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

    private fun parse(json: String): ParsedHosts {
        val rootObj = JSONObject(json)
        val hostsJson = rootObj.getJSONObject("hosts")
        val parsedVersion = rootObj.optInt("version", 0).takeIf { it > 0 }
        val parsedUpdated = rootObj.optString("updated").takeIf { it.isNotEmpty() }
        val result = mutableMapOf<String, List<InetAddress>>()
        val keys = hostsJson.keys()
        while (keys.hasNext()) {
            val host = keys.next()
            if (!EhDns.builtInHostnames.contains(host)) continue
            val array = hostsJson.getJSONArray(host)
            val addresses = (0 until array.length()).mapNotNull {
                val ipStr = array.optString(it)
                Hosts.toInetAddress(host, ipStr)?.takeIf { addr -> isSafePublicAddress(addr) }
            }
            if (addresses.isNotEmpty()) {
                result[host] = addresses
            }
        }
        return ParsedHosts(result, parsedVersion, parsedUpdated)
    }

    private fun isSafePublicAddress(address: InetAddress): Boolean = !address.isLoopbackAddress &&
        !address.isSiteLocalAddress &&
        !address.isLinkLocalAddress &&
        !address.isAnyLocalAddress &&
        !address.isMulticastAddress
}
