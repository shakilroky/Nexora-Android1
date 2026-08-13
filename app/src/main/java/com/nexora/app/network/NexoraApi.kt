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
data class PairResult(val ok: Boolean, val code: Int, val siteId: String, val deviceId: String, val deviceSecret: String, val body: String)

object NexoraApi {
    private fun endpoint(baseUrl: String, path: String): URL =
        URL("${baseUrl.trimEnd('/')}/wp-json/wpct/v1$path")

    private fun hmac(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun ping(site: SiteConfig): ApiResult = request(site, "/android/ping", "{}", mapOf(
        "X-Nexora-Site-ID" to site.siteId,
        "X-Nexora-Device-ID" to site.deviceId
    ))

    fun pair(baseUrl: String, pairingCode: String, deviceName: String, deviceModel: String): PairResult {
        return try {
            val url = endpoint(baseUrl, "/android/pair")
            val body = org.json.JSONObject().apply {
                put("pairing_code", pairingCode.trim().uppercase().removePrefix("NX-"))
                put("device_name", deviceName)
                put("device_model", deviceModel)
            }.toString()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it)).use { br -> br.readText() } } ?: ""
            if (conn.responseCode !in 200..299) return PairResult(false, conn.responseCode, "", "", "", text)
            val o = org.json.JSONObject(text)
            val data = o.optJSONObject("data") ?: o
            PairResult(
                true, conn.responseCode,
                data.optString("site_id"),
                data.optString("device_id"),
                data.optString("device_secret"),
                text
            )
        } catch (e: Exception) {
            PairResult(false, -1, "", "", "", e.message ?: "Pairing failed")
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

        return request(
            site,
            "/android/sms-sync",
            body,
            mapOf(
                "X-Nexora-Site-ID" to site.siteId,
                "X-Nexora-Device-ID" to site.deviceId,
                "X-Nexora-Timestamp" to timestamp.toString(),
                "X-Nexora-Event-ID" to eventId,
                "X-Nexora-Signature" to hmac(body, site.deviceSecret)
            )
        )
    }

    private fun request(
        site: SiteConfig,
        path: String,
        body: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): ApiResult {
        return try {
            val conn = endpoint(site.baseUrl, path).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it)).use { br -> br.readText() }
            } ?: ""
            ApiResult(conn.responseCode in 200..299, conn.responseCode, text)
        } catch (e: Exception) {
            ApiResult(false, -1, e.message ?: "Network error")
        }
    }
}
