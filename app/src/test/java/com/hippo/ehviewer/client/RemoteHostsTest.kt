package com.hippo.ehviewer.client

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class RemoteHostsTest {
    private fun parseHosts(json: String): Any {
        val method = RemoteHosts::class.java.getDeclaredMethod("parse", String::class.java)
        method.isAccessible = true
        return method.invoke(RemoteHosts, json)!!
    }

    private fun getHosts(parsed: Any): Map<String, List<InetAddress>> {
        val method = parsed::class.java.getDeclaredMethod("getHosts")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(parsed) as Map<String, List<InetAddress>>
    }

    private fun getVersion(parsed: Any): Int? {
        val method = parsed::class.java.getDeclaredMethod("getVersion")
        method.isAccessible = true
        return method.invoke(parsed) as? Int
    }

    private fun getUpdated(parsed: Any): String? {
        val method = parsed::class.java.getDeclaredMethod("getUpdated")
        method.isAccessible = true
        return method.invoke(parsed) as? String
    }

    private fun getFallbackDns(): Dns {
        val field = RemoteHosts::class.java.getDeclaredField("fallbackDns")
        field.isAccessible = true
        return field.get(RemoteHosts) as Dns
    }

    @Test
    fun testParseValidHostsJson() {
        val json = """
        {
          "version": 42,
          "updated": "2026-03-31",
          "hosts": {
            "e-hentai.org": ["104.20.134.21", "104.20.135.21"],
            "exhentai.org": ["178.175.129.252", "178.175.129.254"]
          }
        }
        """.trimIndent()

        val parsed = parseHosts(json)
        assertEquals(42, getVersion(parsed))
        assertEquals("2026-03-31", getUpdated(parsed))
        val hosts = getHosts(parsed)
        assertTrue(hosts.containsKey("e-hentai.org"))
        assertEquals(2, hosts["e-hentai.org"]?.size)
        assertTrue(hosts.containsKey("exhentai.org"))
        assertEquals(2, hosts["exhentai.org"]?.size)
    }

    @Test
    fun testParseFiltersUnknownHostnames() {
        val json = """
        {
          "version": 1,
          "hosts": {
            "malicious.attacker.com": ["1.2.3.4"],
            "google.com": ["8.8.8.8"]
          }
        }
        """.trimIndent()

        val parsed = parseHosts(json)
        val hosts = getHosts(parsed)
        assertFalse(hosts.containsKey("malicious.attacker.com"))
        assertFalse(hosts.containsKey("google.com"))
        assertTrue(hosts.isEmpty())
    }

    @Test
    fun testParseFiltersPrivateAndLoopbackIps() {
        val json = """
        {
          "version": 1,
          "hosts": {
            "e-hentai.org": [
              "127.0.0.1",
              "192.168.1.1",
              "10.0.0.1",
              "104.20.134.21"
            ]
          }
        }
        """.trimIndent()

        val parsed = parseHosts(json)
        val addresses = getHosts(parsed)["e-hentai.org"].orEmpty()
        assertEquals(1, addresses.size)
        assertEquals("104.20.134.21", addresses[0].hostAddress)
    }

    @Test
    fun testFallbackDnsThrowsUnknownHostExceptionForNonexistentDomain() {
        try {
            getFallbackDns().lookup("nonexistent.invalid.domain.strictly.local")
            fail("Expected UnknownHostException")
        } catch (_: UnknownHostException) {
            // Expected
        }
    }
}
