package com.example.smartwrench

data class DTCRequest(
    val dtc_code: String,
    val mileage_km: Int? = null,
    val engine_type: String = "Бензин",
    val symptoms: String? = null
)

data class DTCResponse(
    val status: String = "success",
    val severity: String,
    val recommendation: String,
    val can_drive: String? = null,
    val is_uncertain: Boolean? = false
)
