package com.example.smartwrench

data class DTCRequest(
    val dtc_code: String,
    val mileage_km: Int? = null
)

data class DTCResponse(
    val status: String,
    val severity: String,
    val recommendation: String
)
