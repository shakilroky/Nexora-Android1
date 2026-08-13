package com.nexora.android.model

data class SiteConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val siteId: String,
    val deviceId: String,
    val deviceSecret: String,
    val enabled: Boolean = true,
    val gatewayFilter: String = "ALL",
    val senderFilter: String = ""
)
