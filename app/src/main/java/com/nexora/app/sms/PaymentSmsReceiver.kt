package com.nexora.android.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.nexora.android.network.NexoraApi
import com.nexora.android.storage.SiteStore
import java.util.concurrent.Executors

class PaymentSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pending = goAsync()
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return@execute

                val sender = messages.first().originatingAddress ?: ""
                val body = messages.joinToString("") { it.messageBody ?: "" }
                val payment = PaymentParser.parse(sender, body, System.currentTimeMillis())
                    ?: return@execute

                SiteStore(context).all().filter { it.enabled }.forEach { site ->
                    val gatewayOk = site.gatewayFilter == "ALL" ||
                        site.gatewayFilter.equals(payment.gateway, ignoreCase = true)
                    val senderOk = site.senderFilter.isBlank() ||
                        sender.contains(site.senderFilter, ignoreCase = true)

                    if (gatewayOk && senderOk) {
                        NexoraApi.sync(site, payment)
                    }
                }
            } finally {
                executor.shutdown()
                pending.finish()
            }
        }
    }
}
