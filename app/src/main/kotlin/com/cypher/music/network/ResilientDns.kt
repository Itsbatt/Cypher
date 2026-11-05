package com.cypher.music.network

import android.util.Log
import okhttp3.Dns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

object ResilientDns : Dns {
    private const val TAG = "ResilientDns"

    // Cloudflare Anycast IPs for dpaste.org (immune to DNS poisoning)
    private val DPASTE_FALLBACK_IPS = listOf(
        "172.66.152.175",
        "104.20.36.119",
        "188.114.97.0",
        "188.114.96.0"
    )

    // Fallback IP for paste.c-net.org
    private val CNET_FALLBACK_IPS = listOf(
        "20.100.184.134"
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val lower = hostname.lowercase()

        // 1. Try system DNS first
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            // Detect if DNS is hijacked by ISP (Orange Spain Ciberprotección returns 85.57.x.x, or local captive portals)
            val isHijacked = addresses.any { addr ->
                val ip = addr.hostAddress ?: ""
                ip.startsWith("85.57.") || ip.startsWith("10.") || ip.startsWith("192.168.") || ip == "127.0.0.1"
            }
            if (!isHijacked && addresses.isNotEmpty()) {
                return addresses
            }
            Log.w(TAG, "DNS resolution for $hostname was hijacked or filtered (${addresses.map { it.hostAddress }}). Using secure bypass.")
        } catch (e: Exception) {
            Log.w(TAG, "System DNS failed for $hostname: ${e.message}. Using secure fallback.")
        }

        // 2. Immediate direct Anycast routing for known services
        if (lower == "dpaste.org") {
            Log.d(TAG, "Using direct Cloudflare Anycast IPs for dpaste.org")
            return DPASTE_FALLBACK_IPS.map { InetAddress.getByName(it) }
        }
        if (lower == "paste.c-net.org") {
            Log.d(TAG, "Using direct fallback IP for paste.c-net.org")
            return CNET_FALLBACK_IPS.map { InetAddress.getByName(it) }
        }

        // 3. DNS-over-HTTPS via Google / Cloudflare
        val dohAddresses = resolveDoh(hostname)
        if (dohAddresses.isNotEmpty()) {
            return dohAddresses
        }

        // 4. Final attempt: standard DNS
        return Dns.SYSTEM.lookup(hostname)
    }

    private fun resolveDoh(hostname: String): List<InetAddress> {
        // Try Google DoH
        try {
            val url = URL("https://dns.google/resolve?name=$hostname&type=A")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val answer = json.optJSONArray("Answer")
                if (answer != null) {
                    val list = mutableListOf<InetAddress>()
                    for (i in 0 until answer.length()) {
                        val obj = answer.getJSONObject(i)
                        if (obj.optInt("type") == 1) { // Type A
                            val ip = obj.optString("data")
                            if (ip.isNotBlank()) {
                                list.add(InetAddress.getByName(ip))
                            }
                        }
                    }
                    if (list.isNotEmpty()) {
                        Log.d(TAG, "Resolved $hostname via Google DoH: $list")
                        return list
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google DoH lookup failed for $hostname: ${e.message}")
        }

        // Try Cloudflare DoH (via 1.1.1.1 IP directly)
        try {
            val url = URL("https://1.1.1.1/dns-query?name=$hostname&type=A")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/dns-json")
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val answer = json.optJSONArray("Answer")
                if (answer != null) {
                    val list = mutableListOf<InetAddress>()
                    for (i in 0 until answer.length()) {
                        val obj = answer.getJSONObject(i)
                        if (obj.optInt("type") == 1) { // Type A
                            val ip = obj.optString("data")
                            if (ip.isNotBlank()) {
                                list.add(InetAddress.getByName(ip))
                            }
                        }
                    }
                    if (list.isNotEmpty()) {
                        Log.d(TAG, "Resolved $hostname via Cloudflare DoH: $list")
                        return list
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloudflare DoH lookup failed for $hostname: ${e.message}")
        }

        return emptyList()
    }
}
