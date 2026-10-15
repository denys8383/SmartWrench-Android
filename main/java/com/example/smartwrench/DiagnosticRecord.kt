package com.example.smartwrench

import java.util.UUID

data class DiagnosticRecord(
    val id: String = UUID.randomUUID().toString(),
    val dtcCode: String,
    val mileage: Int?,
    val severity: String,
    val recommendation: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "готово"
)
