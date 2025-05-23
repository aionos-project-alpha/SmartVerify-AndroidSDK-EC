package com.aionos.smartverify.model

data class VerifyOtpRequest(
    val txnId: String,
    val otp: String
)
