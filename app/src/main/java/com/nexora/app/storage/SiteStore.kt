package com.nexora.android.storage

import android.content.Context
import com.nexora.android.model.SiteConfig
import org.json.JSONArray
import org.json.JSONObject

class SiteStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexora_sites", Context.MODE_PRIVATE)

    fun all(): MutableList<SiteConfig> {
        val arr = JSONArray(prefs.getString("sites", "[]") ?: "[]")
        val out = mutableListOf<SiteConfig>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += SiteConfig(
                id = o.optString("id"),
                name = o.optString("name"),
                baseUrl = o.optString("baseUrl"),
                siteId = o.optString("siteId"),
                deviceId = o.optString("deviceId"),
                deviceSecret = o.optString("deviceSecret"),
                enabled = o.optBoolean("enabled", true),
                gatewayFilter = o.optString("gatewayFilter", "ALL"),
                senderFilter = o.optString("senderFilter", "")
            )
        }
        return out
    }

    fun save(site: SiteConfig) {
        val list = all().filterNot { it.id == site.id }.toMutableList()
        list += site
        val arr = JSONArray()
        list.forEach { o ->
            arr.put(JSONObject().apply {
                put("id", o.id)
                put("name", o.name)
                put("baseUrl", o.baseUrl)
                put("siteId", o.siteId)
                put("deviceId", o.deviceId)
                put("deviceSecret", o.deviceSecret)
                put("enabled", o.enabled)
                put("gatewayFilter", o.gatewayFilter)
                put("senderFilter", o.senderFilter)
            })
        }
        prefs.edit().putString("sites", arr.toString()).apply()
    }

    fun delete(id: String) {
        val arr = JSONArray()
        all().filterNot { it.id == id }.forEach { o ->
            arr.put(JSONObject().apply {
                put("id", o.id); put("name", o.name); put("baseUrl", o.baseUrl)
                put("siteId", o.siteId); put("deviceId", o.deviceId)
                put("deviceSecret", o.deviceSecret); put("enabled", o.enabled)
                put("gatewayFilter", o.gatewayFilter); put("senderFilter", o.senderFilter)
            })
        }
        prefs.edit().putString("sites", arr.toString()).apply()
    }
}
