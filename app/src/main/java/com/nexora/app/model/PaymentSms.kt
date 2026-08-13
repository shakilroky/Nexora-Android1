package com.nexora.android.model

data class PaymentSms(
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val gateway: String,
    val transactionId: String?,
    val amount: String?
)
