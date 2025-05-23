package com.aionos.smartverify.model

data class AuthRequest(
    val brand: String,
    val workflow: List<Workflow>,
    val wifiEnabled: Boolean,
    val cellularNetworkEnabled: Boolean,
    val cellularNetwork: String
)

data class Workflow(
    val channel: String,
    val mobileNumberTo: String
)
