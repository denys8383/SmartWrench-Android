package com.example.smartwrench

data class DTCRequest(
    val dtc_code: String
)

data class DTCResponse(
    val status: String,
    val severity: String,
    val recommendation: String
)