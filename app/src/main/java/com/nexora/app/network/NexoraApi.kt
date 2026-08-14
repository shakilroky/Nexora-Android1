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

data class ApiResult(
    val ok: Boolean,
    val code: Int,
    val body: String
)

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

    private fun endpoint(baseUrl: String, path: String): URL {
        return URL(
            "${baseUrl.trimEnd('/')}$API_PREFIX$path"
        )
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream =
            if (conn.responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream
            }

        return stream?.let {
            BufferedReader(
                InputStreamReader(it, Charsets.UTF_8)
            ).use { reader ->
                reader.readText()
            }
        } ?: ""
    }

    private fun hmac(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")

        mac.init(
            SecretKeySpec(
                secret.toByteArray(Charsets.UTF_8),
                "HmacSHA256"
            )
        )

        return mac.doFinal(
            body.toByteArray(Charsets.UTF_8)
        ).joinToString("") {
            "%02x".format(it)
        }
    }

    /**
     * Initial Nexora handshake.
     *
     * IMPORTANT:
     * This request does NOT require device credentials.
     *
     * GET is used first because the Nexora plugin exposes
     * the handshake information through the ping endpoint.
     */
    fun handshake(baseUrl: String): HandshakeResult {

        return try {

            val url = endpoint(
                baseUrl,
                "/android/ping"
            )

            val conn =
                (url.openConnection() as HttpURLConnection).apply {

                    requestMethod = "GET"

                    connectTimeout = 15000
                    readTimeout = 20000

                    doInput = true
                    doOutput = false

                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    setRequestProperty(
                        "User-Agent",
                        "Nexora-Android/1.2.1"
                    )
                }

            val responseCode = conn.responseCode
            val text = readResponse(conn)

            if (responseCode !in 200..299) {

                return HandshakeResult(
                    ok = false,
                    code = responseCode,
                    status = "",
                    handshake = "",
                    ready = false,
                    siteId = "",
                    siteName = "",
                    siteUrl = "",
                    version = "",
                    pairEndpoint = "",
                    syncEndpoint = "",
                    pingEndpoint = "",
                    body = text.ifBlank {
                        "HTTP $responseCode"
                    }
                )
            }

            val json = org.json.JSONObject(text)

            val endpoints =
                json.optJSONObject("endpoints")

            HandshakeResult(
                ok =
                    json.optString("status")
                        .equals("success", true) &&
                    json.optString("handshake")
                        .equals("success", true) &&
                    json.optBoolean("ready", false),

                code = responseCode,

                status =
                    json.optString("status"),

                handshake =
                    json.optString("handshake"),

                ready =
                    json.optBoolean("ready", false),

                siteId =
                    json.optString("site_id"),

                siteName =
                    json.optString("site_name"),

                siteUrl =
                    json.optString(
                        "site_url",
                        baseUrl.trimEnd('/')
                    ),

                version =
                    json.optString("version"),

                pairEndpoint =
                    endpoints?.optString(
                        "pair",
                        ""
                    ) ?: "",

                syncEndpoint =
                    endpoints?.optString(
                        "sync",
                        ""
                    ) ?: "",

                pingEndpoint =
                    endpoints?.optString(
                        "ping",
                        ""
                    ) ?: "",

                body = text
            )

        } catch (e: Exception) {

            HandshakeResult(
                ok = false,
                code = -1,
                status = "",
                handshake = "",
                ready = false,
                siteId = "",
                siteName = "",
                siteUrl = "",
                version = "",
                pairEndpoint = "",
                syncEndpoint = "",
                pingEndpoint = "",
                body =
                    "${e.javaClass.simpleName}: ${
                        e.message ?: "Connection failed"
                    }"
            )
        }
    }

    /**
     * Pair a new Android device.
     *
     * Handshake should be completed before this method.
     */
    fun pair(
        baseUrl: String,
        pairingCode: String,
        deviceName: String,
        deviceModel: String,
        pairEndpointOverride: String = ""
    ): PairResult {

        return try {

            val url =
                if (pairEndpointOverride.isNotBlank()) {
                    URL(pairEndpointOverride)
                } else {
                    endpoint(
                        baseUrl,
                        "/android/pair"
                    )
                }

            val cleanCode =
                pairingCode
                    .trim()
                    .uppercase()
                    .removePrefix("NX-")

            val body =
                org.json.JSONObject().apply {

                    put(
                        "pairing_code",
                        cleanCode
                    )

                    put(
                        "device_name",
                        deviceName
                    )

                    put(
                        "device_model",
                        deviceModel
                    )

                }.toString()

            val conn =
                (url.openConnection() as HttpURLConnection).apply {

                    requestMethod = "POST"

                    connectTimeout = 15000
                    readTimeout = 20000

                    doInput = true
                    doOutput = true

                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                    )

                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    setRequestProperty(
                        "User-Agent",
                        "Nexora-Android/1.2.1"
                    )
                }

            conn.outputStream.use {
                it.write(
                    body.toByteArray(Charsets.UTF_8)
                )
            }

            val responseCode = conn.responseCode
            val text = readResponse(conn)

            if (responseCode !in 200..299) {

                return PairResult(
                    false,
                    responseCode,
                    "",
                    "",
                    "",
                    text.ifBlank {
                        "HTTP $responseCode"
                    }
                )
            }

            val json =
                org.json.JSONObject(text)

            val data =
                json.optJSONObject("data")
                    ?: json

            PairResult(
                ok = true,

                code = responseCode,

                siteId =
                    data.optString("site_id"),

                deviceId =
                    data.optString("device_id"),

                deviceSecret =
                    data.optString("device_secret"),

                body = text
            )

        } catch (e: Exception) {

            PairResult(
                false,
                -1,
                "",
                "",
                "",
                "${e.javaClass.simpleName}: ${
                    e.message ?: "Pairing failed"
                }"
            )
        }
    }

    /**
     * Authenticated SMS synchronization.
     */
    fun sync(
        site: SiteConfig,
        payment: PaymentSms
    ): ApiResult {

        val eventId =
            UUID.randomUUID().toString()

        val timestamp =
            System.currentTimeMillis() / 1000L

        val body =
            org.json.JSONObject().apply {

                put(
                    "site_id",
                    site.siteId
                )

                put(
                    "device_id",
                    site.deviceId
                )

                put(
                    "event_id",
                    eventId
                )

                put(
                    "timestamp",
                    timestamp
                )

                put(
                    "gateway",
                    payment.gateway
                )

                put(
                    "sender",
                    payment.sender
                )

                put(
                    "message",
                    payment.body
                )

                put(
                    "transaction_id",
                    payment.transactionId ?: ""
                )

                put(
                    "amount",
                    payment.amount ?: ""
                )

                put(
                    "received_at",
                    payment.receivedAt
                )

            }.toString()

        return request(
            site = site,
            path = "/android/sms-sync",
            body = body,

            extraHeaders = mapOf(

                "X-Nexora-Site-ID"
                    to site.siteId,

                "X-Nexora-Device-ID"
                    to site.deviceId,

                "X-Nexora-Timestamp"
                    to timestamp.toString(),

                "X-Nexora-Event-ID"
                    to eventId,

                "X-Nexora-Signature"
                    to hmac(
                        body,
                        site.deviceSecret
                    )
            )
        )
    }

    /**
     * Test an already paired device.
     *
     * Uses POST because this authenticated
     * endpoint can be used after pairing.
     */
    fun ping(site: SiteConfig): ApiResult {

        return request(
            site = site,
            path = "/android/ping",
            body = "{}",

            extraHeaders = mapOf(

                "X-Nexora-Site-ID"
                    to site.siteId,

                "X-Nexora-Device-ID"
                    to site.deviceId
            )
        )
    }

    private fun request(
        site: SiteConfig,
        path: String,
        body: String,
        extraHeaders: Map<String, String> =
            emptyMap()
    ): ApiResult {

        return try {

            val conn =
                endpoint(
                    site.baseUrl,
                    path
                ).openConnection()
                    as HttpURLConnection

            conn.requestMethod = "POST"

            conn.connectTimeout = 15000
            conn.readTimeout = 20000

            conn.doInput = true
            conn.doOutput = true

            conn.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            )

            conn.setRequestProperty(
                "Accept",
                "application/json"
            )

            conn.setRequestProperty(
                "User-Agent",
                "Nexora-Android/1.2.1"
            )

            extraHeaders.forEach {
                (key, value) ->
                conn.setRequestProperty(
                    key,
                    value
                )
            }

            conn.outputStream.use {
                it.write(
                    body.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            val responseCode =
                conn.responseCode

            val text =
                readResponse(conn)

            ApiResult(
                ok =
                    responseCode in 200..299,

                code =
                    responseCode,

                body =
                    text
            )

        } catch (e: Exception) {

            ApiResult(
                ok = false,
                code = -1,
                body =
                    "${e.javaClass.simpleName}: ${
                        e.message ?: "Network error"
                    }"
            )
        }
    }
}
