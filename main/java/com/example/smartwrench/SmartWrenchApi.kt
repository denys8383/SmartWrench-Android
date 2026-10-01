package com.example.smartwrench

import retrofit2.http.Body
import retrofit2.http.POST

interface SmartWrenchApi {
    @POST("api/analyze")
    suspend fun analyzeError(@Body request: DTCRequest): DTCResponse
}
