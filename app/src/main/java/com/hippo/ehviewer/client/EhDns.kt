/*
 * Copyright 2018 Hippo Seven
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

import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Hosts
import com.hippo.ehviewer.Settings
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okio.FileSystem
import java.net.InetAddress
import java.net.UnknownHostException

object EhDns : Dns {
    private val hosts = EhApplication.hosts
    val builtInHostnames = setOf(
        "e-hentai.org",
        "forums.e-hentai.org",
        "repo.e-hentai.org",
        "api.e-hentai.org",
        "ehgt.org",
        "gt0.ehgt.org",
        "gt1.ehgt.org",
        "gt2.ehgt.org",
        "gt3.ehgt.org",
        "ul.ehgt.org",
        "exhentai.org",
        "s.exhentai.org",
        RemoteHosts.GITHUB_HOST,
    )
    private val appCache = Cache(FileSystem.SYSTEM, EhApplication.cacheDir / "dns_cache", 5L * 1024 * 1024)
    private val bootstrapClient = OkHttpClient.Builder().cache(appCache)
        .hostnameVerifier { _, _ -> true }
        .build()

    @Volatile
    private var cachedDoh: DnsOverHttps? = null

    @Volatile
    private var cachedServerUrl: String? = null

    private fun getDoh(): DnsOverHttps? {
        if (!Settings.dOH) return null
        val currentServer = Settings.doHServer
        if (currentServer.isNullOrEmpty() || !currentServer.startsWith("https://")) {
            return null
        }
        if (cachedServerUrl == currentServer && cachedDoh != null) {
            return cachedDoh
        }
        synchronized(this) {
            if (cachedServerUrl == currentServer && cachedDoh != null) {
                return cachedDoh
            }
            return try {
                val newDoh = DnsOverHttps.Builder().client(bootstrapClient)
                    .url(currentServer.toHttpUrl())
                    .build()
                cachedServerUrl = currentServer
                cachedDoh = newDoh
                newDoh
            } catch (e: Exception) {
                null
            }
        }
    }

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        hosts[hostname]?.let { return it }
        if (Settings.builtInHosts || hostname == RemoteHosts.GITHUB_HOST) {
            val ips = RemoteHosts.lookup(hostname)
            if (!ips.isNullOrEmpty()) {
                return ips
            }
        }
        val dns = getDoh() ?: Dns.SYSTEM
        return dns.lookup(hostname)
    }

    fun isBuiltInHost(hostname: String): Boolean = builtInHostnames.contains(hostname) || RemoteHosts.contains(hostname)

    fun isInHosts(hostname: String): Boolean = hosts.contains(hostname) || isBuiltInHost(hostname)
}
