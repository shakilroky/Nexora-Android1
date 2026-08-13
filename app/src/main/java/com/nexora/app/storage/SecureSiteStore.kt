package com.nexora.android.storage

import android.content.Context
import android.util.Base64
import com.nexora.android.model.SiteConfig
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Lightweight encrypted-at-rest store for device secrets. */
class SecureSiteStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexora_secure_sites", Context.MODE_PRIVATE)
    private val key: SecretKeySpec
    init {
        val seed = (context.packageName + ":Nexora-v1").toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(seed)
        key = SecretKeySpec(digest, "AES")
    }
    private fun enc(value: String): String {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val out = c.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + out, Base64.NO_WRAP)
    }
    private fun dec(value: String): String {
        val raw = Base64.decode(value, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val data = raw.copyOfRange(12, raw.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(c.doFinal(data), Charsets.UTF_8)
    }
    fun all(): MutableList<SiteConfig> {
        val arr = JSONArray(prefs.getString("sites", "[]") ?: "[]")
        val out = mutableListOf<SiteConfig>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += SiteConfig(o.optString("id"),o.optString("name"),o.optString("baseUrl"),o.optString("siteId"),o.optString("deviceId"),dec(o.optString("deviceSecret")),o.optBoolean("enabled",true),o.optString("gatewayFilter","ALL"),o.optString("senderFilter",""))
        }
        return out
    }
    fun save(site: SiteConfig) {
        val arr = JSONArray()
        (all().filterNot { it.id == site.id } + site).forEach { o ->
            arr.put(JSONObject().apply {
                put("id",o.id); put("name",o.name); put("baseUrl",o.baseUrl); put("siteId",o.siteId); put("deviceId",o.deviceId); put("deviceSecret",enc(o.deviceSecret)); put("enabled",o.enabled); put("gatewayFilter",o.gatewayFilter); put("senderFilter",o.senderFilter)
            })
        }
        prefs.edit().putString("sites",arr.toString()).apply()
    }
    fun delete(id:String) { val arr=JSONArray(); all().filterNot{it.id==id}.forEach{saveTemp(arr,it)}; prefs.edit().putString("sites",arr.toString()).apply() }
    private fun saveTemp(arr:JSONArray,o:SiteConfig){arr.put(JSONObject().apply{put("id",o.id);put("name",o.name);put("baseUrl",o.baseUrl);put("siteId",o.siteId);put("deviceId",o.deviceId);put("deviceSecret",enc(o.deviceSecret));put("enabled",o.enabled);put("gatewayFilter",o.gatewayFilter);put("senderFilter",o.senderFilter)})}
}
