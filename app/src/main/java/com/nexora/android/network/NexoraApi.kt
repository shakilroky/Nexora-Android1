package com.nexora.android.network

import com.nexora.android.model.PaymentSms
import com.nexora.android.model.SiteConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ApiResult(val ok: Boolean, val code: Int, val body: String)

data class HandshakeResult(
    val ok: Boolean,
    val code: Int,
    val status: String,
    val handshake: String,
    val ready: Boolean,
    val siteId: String,
    val siteName: String,
    val siteUrl: String,
    val version: String,
    val deviceId: String,
    val deviceStatus: String,
    val pairEndpoint: String,
    val syncEndpoint: String,
    val pingEndpoint: String,
    val body: String
)

data class PairResult(
    val ok: Boolean,
    val code: Int,
    val siteId: String,
    val deviceId: String,
    val deviceSecret: String,
    val body: String
)

object NexoraApi {
    private const val API_PREFIX = "/wp-json/wpct/v1"
    private const val USER_AGENT = "Nexora-Android/1.2.2"

    private fun endpoint(baseUrl: String, path: String): URL =
        URL("${baseUrl.trimEnd('/')}$API_PREFIX$path")

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.let {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
        } ?: ""
    }

    private fun errorText(e: Throwable): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = e
        while (current != null && parts.size < 8) {
            parts += "${current.javaClass.name}: ${current.message ?: ""}".trim()
            current = current.cause
        }
        return parts.joinToString("\nCaused by: ")
    }

    private fun hmac(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** Initial public verification: GET /android/ping. */
    fun handshake(baseUrl: String): HandshakeResult {
        return try {
            val conn = (endpoint(baseUrl, "/android/ping").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
                doInput = true
                doOutput = false
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val responseCode = conn.responseCode
            val text = readResponse(conn)
            conn.disconnect()

            if (responseCode !in 200..299) return failedHandshake(responseCode, text.ifBlank { "HTTP $responseCode" })

            val json = org.json.JSONObject(text)
            val endpoints = json.optJSONObject("endpoints")
            val success = json.optBoolean("success", false)
            val legacyOk = json.optString("status").equals("success", true) &&
                    json.optString("handshake").equals("success", true) &&
                    json.optBoolean("ready", false)

            HandshakeResult(
                ok = success || legacyOk,
                code = responseCode,
                status = json.optString("status", if (success) "success" else ""),
                handshake = json.optString("handshake", if (success) "success" else ""),
                ready = json.optBoolean("ready", success),
                siteId = json.optString("site_id"),
                siteName = json.optString("site_name"),
                siteUrl = json.optString("site_url", baseUrl.trimEnd('/')),
                version = json.optString("version"),
                deviceId = json.optString("device_id"),
                deviceStatus = json.optString("device_status", "unknown"),
                pairEndpoint = endpoints?.optString("pair", "") ?: "",
                syncEndpoint = endpoints?.optString("sync", "") ?: "",
                pingEndpoint = endpoints?.optString("ping", "") ?: "",
                body = text
            )
        } catch (e: Exception) {
            failedHandshake(-1, errorText(e))
        }
    }

    private fun failedHandshake(code: Int, body: String) = HandshakeResult(
        ok = false, code = code, status = "", handshake = "", ready = false,
        siteId = "", siteName = "", siteUrl = "", version = "", deviceId = "",
        deviceStatus = "", pairEndpoint = "", syncEndpoint = "", pingEndpoint = "", body = body
    )

    fun pair(baseUrl: String, pairingCode: String, deviceName: String, deviceModel: String, pairEndpointOverride: String = ""): PairResult {
        return try {
            val url = if (pairEndpointOverride.isNotBlank()) URL(pairEndpointOverride) else endpoint(baseUrl, "/android/pair")
            val cleanCode = pairingCode.trim().uppercase().removePrefix("NX-")
            val body = org.json.JSONObject().apply {
                put("pairing_code", cleanCode)
                put("device_name", deviceName)
                put("device_model", deviceModel)
            }.toString()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doInput = true
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val responseCode = conn.responseCode
            val text = readResponse(conn)
            conn.disconnect()
            if (responseCode !in 200..299) return PairResult(false, responseCode, "", "", "", text.ifBlank { "HTTP $responseCode" })
            val json = org.json.JSONObject(text)
            val data = json.optJSONObject("data") ?: json
            PairResult(json.optBoolean("success", true), responseCode, data.optString("site_id"), data.optString("device_id"), data.optString("device_secret"), text)
        } catch (e: Exception) {
            PairResult(false, -1, "", "", "", errorText(e))
        }
    }

    fun sync(site: SiteConfig, payment: PaymentSms): ApiResult {
        val eventId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis() / 1000L
        val body = org.json.JSONObject().apply {
            put("site_id", site.siteId)
            put("device_id", site.deviceId)
            put("event_id", eventId)
            put("timestamp", timestamp)
            put("gateway", payment.gateway)
            put("sender", payment.sender)
            put("message", payment.body)
            put("transaction_id", payment.transactionId ?: "")
            put("amount", payment.amount ?: "")
            put("received_at", payment.receivedAt)
        }.toString()
        return request(site, "/android/sms-sync", body, mapOf(
            "X-Nexora-Site-ID" to site.siteId,
            "X-Nexora-Device-ID" to site.deviceId,
            "X-Nexora-Timestamp" to timestamp.toString(),
            "X-Nexora-Event-ID" to eventId,
            "X-Nexora-Signature" to hmac(body, site.deviceSecret)
        ))
    }

    fun ping(site: SiteConfig): ApiResult = request(site, "/android/ping", "{}", mapOf(
        "X-Nexora-Site-ID" to site.siteId,
        "X-Nexora-Device-ID" to site.deviceId
    ))

    private fun request(site: SiteConfig, path: String, body: String, extraHeaders: Map<String, String> = emptyMap()): ApiResult {
        return try {
            val conn = (endpoint(site.baseUrl, path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doInput = true
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val responseCode = conn.responseCode
            val text = readResponse(conn)
            conn.disconnect()
            ApiResult(responseCode in 200..299, responseCode, text)
        } catch (e: Exception) {
            ApiResult(false, -1, errorText(e))
        }
    }
}
