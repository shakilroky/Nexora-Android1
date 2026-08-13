package com.nexora.android.sms

import com.nexora.android.model.PaymentSms

object PaymentParser {
    private val trxPatterns = listOf(
        Regex("""(?:TrxID|TxnID|Transaction(?:\s*ID)?)\s*[:#-]?\s*([A-Za-z0-9_-]{6,40})""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Z0-9]{8,20})\b""")
    )

    private val amountPatterns = listOf(
        Regex("""(?:Tk|BDT|Amount|Received|Payment)\s*[:=]?\s*([0-9][0-9,]*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:Tk|BDT)""", RegexOption.IGNORE_CASE)
    )

    fun parse(sender: String, body: String, receivedAt: Long): PaymentSms? {
        val lower = (sender + " " + body).lowercase()
        val gateway = when {
            "bkash" in lower -> "BKASH"
            "nagad" in lower -> "NAGAD"
            "rocket" in lower || "dbbl" in lower -> "ROCKET"
            else -> return null
        }

        val trx = trxPatterns.asSequence()
            .mapNotNull { it.find(body)?.groupValues?.getOrNull(1) }
            .firstOrNull { it.length >= 6 }

        val amount = amountPatterns.asSequence()
            .mapNotNull { it.find(body)?.groupValues?.getOrNull(1) }
            .firstOrNull()

        if (trx == null && amount == null) return null

        return PaymentSms(
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            gateway = gateway,
            transactionId = trx,
            amount = amount
        )
    }
}
