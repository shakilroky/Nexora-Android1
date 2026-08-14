package com.nexora.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import com.nexora.android.model.SiteConfig
import com.nexora.android.network.NexoraApi
import com.nexora.android.storage.SecureSiteStore
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var store: SecureSiteStore
    private lateinit var list: LinearLayout
    private val navy = Color.rgb(8, 18, 37)
    private val green = Color.rgb(18, 184, 134)
    private val white = Color.WHITE

    private fun getCardBackground() = GradientDrawable().apply {
        setColor(Color.rgb(16, 32, 64))
        cornerRadius = 24f
        setStroke(2, Color.rgb(26, 48, 90))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureSiteStore(this)
        requestSmsPermission()
        renderHome()
    }

    private fun requestSmsPermission() {
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1001)
        }
    }

    private fun renderHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            setPadding(28, 32, 28, 24)
        }
        root.addView(TextView(this).apply {
            text = "NEXORA"
            textSize = 28f
            setTextColor(white)
        })
        root.addView(TextView(this).apply {
            text = "SMS • VERIFY • AUTOMATE"
            textSize = 12f
            setTextColor(Color.rgb(150, 170, 195))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 })
        root.addView(TextView(this).apply {
            text = "● SMS bridge ready"
            textSize = 15f
            setTextColor(green)
            setPadding(24, 20, 24, 20)
            background = getCardBackground()
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 })

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Connected Websites"
            textSize = 19f
            setTextColor(white)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            text = "+ ADD"
            setTextColor(white)
            setBackgroundColor(green)
            setOnClickListener { showConnectDialog() }
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 })

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "Nexora v1.2.2"
            textSize = 12f
            setTextColor(Color.rgb(120, 140, 165))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        setContentView(root)
        refreshSites()
    }

    private fun refreshSites() {
        list.removeAllViews()
        val sites = store.all()
        if (sites.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No websites connected yet.\n\nTap + ADD to connect a Nexora website."
                textSize = 15f
                setTextColor(Color.rgb(170, 185, 205))
                setPadding(20, 30, 20, 30)
                gravity = Gravity.CENTER
            })
            return
        }
        sites.forEach { site ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
                background = getCardBackground()
            }
            row.addView(TextView(this).apply {
                text = "● ${site.name}"
                textSize = 17f
                setTextColor(green)
            })
            row.addView(TextView(this).apply {
                text = site.baseUrl
                textSize = 13f
                setTextColor(Color.rgb(165, 180, 205))
            })
            row.addView(TextView(this).apply {
                text = "Device: ${site.deviceId.takeLast(8)} • Gateway: ${site.gatewayFilter}"
                textSize = 12f
                setTextColor(Color.rgb(130, 150, 175))
            })
            val buttons = LinearLayout(this).apply { setPadding(0, 16, 0, 0) }
            buttons.addView(Button(this).apply {
                text = "TEST"
                setOnClickListener {
                    Thread {
                        val result = NexoraApi.ping(site)
                        runOnUiThread {
                            if (result.ok) Toast.makeText(this@MainActivity, "Connection OK", Toast.LENGTH_SHORT).show()
                            else showError("Connection failed", result.code, result.body)
                        }
                    }.start()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            buttons.addView(Button(this).apply {
                text = "REMOVE"
                setOnClickListener { store.delete(site.id); refreshSites() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(buttons)
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 })
        }
    }

    private fun showConnectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Connect Nexora")
            .setMessage("The app will verify the Nexora website first, then pair this device using the 6-digit pairing code.")
            .setPositiveButton("PAIR CODE") { _, _ -> showPairDialog() }
            .setNeutralButton("MANUAL") { _, _ -> showAddSiteDialog() }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showPairDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 0, 20, 0)
        }
        fun field(hint: String) = EditText(this).apply { this.hint = hint; textSize = 15f }
        val url = field("Website URL\nhttps://metroomaa.com")
        val code = field("Pairing Code\nNX-123456")
        val name = field("Device name\nExample: bKash Phone")
        box.addView(url); box.addView(code); box.addView(name)

        AlertDialog.Builder(this)
            .setTitle("Pair with Nexora")
            .setView(box)
            .setPositiveButton("CONNECT") { _, _ ->
                val baseUrl = url.text.toString().trim().removeSuffix("/")
                val pairingCode = code.text.toString().trim()
                val deviceName = name.text.toString().trim().ifBlank { "Nexora Android" }
                if (baseUrl.isBlank() || pairingCode.isBlank()) {
                    Toast.makeText(this, "Website URL and pairing code are required.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                Thread {
                    val handshake = NexoraApi.handshake(baseUrl)
                    if (!handshake.ok) {
                        runOnUiThread { showError("Nexora handshake failed", handshake.code, handshake.body) }
                        return@Thread
                    }
                    if (handshake.siteId.isBlank()) {
                        runOnUiThread { showError("Handshake failed", handshake.code, "Server returned success but no site_id.\n\n${handshake.body}") }
                        return@Thread
                    }
                    val pair = NexoraApi.pair(
                        baseUrl = handshake.siteUrl.ifBlank { baseUrl },
                        pairingCode = pairingCode,
                        deviceName = deviceName,
                        deviceModel = android.os.Build.MODEL,
                        pairEndpointOverride = handshake.pairEndpoint
                    )
                    runOnUiThread {
                        if (pair.ok && pair.deviceSecret.isNotBlank() && pair.deviceId.isNotBlank()) {
                            store.save(SiteConfig(
                                id = UUID.randomUUID().toString(),
                                name = handshake.siteName.ifBlank { baseUrl },
                                baseUrl = handshake.siteUrl.ifBlank { baseUrl }.removeSuffix("/"),
                                siteId = pair.siteId.ifBlank { handshake.siteId },
                                deviceId = pair.deviceId,
                                deviceSecret = pair.deviceSecret,
                                gatewayFilter = "ALL",
                                senderFilter = ""
                            ))
                            refreshSites()
                            Toast.makeText(this, "Nexora connected successfully!", Toast.LENGTH_LONG).show()
                        } else showError("Nexora pairing failed", pair.code, pair.body)
                    }
                }.start()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showError(title: String, code: Int, details: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("HTTP/Network code: $code\n\n${details.ifBlank { "No response received from server." }}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAddSiteDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 0, 20, 0)
        }
        fun field(hint: String, secret: Boolean = false) = EditText(this).apply {
            this.hint = hint
            textSize = 15f
            if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val name = field("Website name")
        val url = field("https://example.com")
        val siteId = field("Site ID (nx_site_...)")
        val deviceId = field("Device ID")
        val secret = field("Device Secret", true)
        val gateway = field("Gateway filter: ALL / BKASH / NAGAD / ROCKET")
        val sender = field("Optional sender filter")
        listOf(name, url, siteId, deviceId, secret, gateway, sender).forEach { box.addView(it) }
        AlertDialog.Builder(this)
            .setTitle("Connect Nexora Website")
            .setView(box)
            .setPositiveButton("SAVE") { _, _ ->
                store.save(SiteConfig(
                    id = UUID.randomUUID().toString(),
                    name = name.text.toString().trim().ifBlank { "Nexora Website" },
                    baseUrl = url.text.toString().trim().removeSuffix("/"),
                    siteId = siteId.text.toString().trim(),
                    deviceId = deviceId.text.toString().trim(),
                    deviceSecret = secret.text.toString(),
                    gatewayFilter = gateway.text.toString().trim().uppercase().ifBlank { "ALL" },
                    senderFilter = sender.text.toString().trim()
                ))
                refreshSites()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}
