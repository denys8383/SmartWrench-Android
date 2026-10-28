package com.example.smartwrench

import kotlinx.coroutines.delay
import java.io.IOException

// 1. Mapper: перетворення між моделями мережі (DTO) та доменними моделями
object DiagnosticMapper {
    fun toDto(input: DiagnosticInput): DTCRequest {
        return DTCRequest(
            dtc_code = input.dtcCode,
            mileage_km = input.mileageKm
        )
    }

    fun toDomain(input: DiagnosticInput, dto: DTCResponse): DiagnosticPrediction {
        return DiagnosticPrediction(
            dtcCode = input.dtcCode,
            mileageKm = input.mileageKm,
            severity = dto.severity,
            recommendation = dto.recommendation
        )
    }
}

// 2. Реальна реалізація репозиторію (працює з FastAPI через Retrofit)
class RealDiagnosticRepository(
    private val api: SmartWrenchApi
) : DiagnosticRepository {

    override suspend fun analyze(input: DiagnosticInput): Result<DiagnosticPrediction> {
        return try {
            val requestDto = DiagnosticMapper.toDto(input)
            val responseDto = api.analyzeError(requestDto)

            if (responseDto.severity.isBlank() || responseDto.recommendation.isBlank()) {
                Result.failure(DomainError.ServerIncompleteDataError)
            } else {
                Result.success(DiagnosticMapper.toDomain(input, responseDto))
            }
        } catch (e: IOException) {
            // ConnectException, SocketTimeoutException тощо
            Result.failure(DomainError.NetworkUnavailableError)
        } catch (e: Exception) {
            Result.failure(DomainError.UnknownError(e.localizedMessage ?: "Невідома помилка"))
        }
    }
}

// 3. Взаємозамінна Fake-реалізація (працює без реального сервера для тестів та демо)
class FakeDiagnosticRepository(
    var shouldSimulateNetworkError: Boolean = false
) : DiagnosticRepository {

    override suspend fun analyze(input: DiagnosticInput): Result<DiagnosticPrediction> {
        delay(300) // Імітація затримки мережі
        if (shouldSimulateNetworkError) {
            return Result.failure(DomainError.NetworkUnavailableError)
        }

        val fakePrediction = when (input.dtcCode) {
            "P0300" -> DiagnosticPrediction(
                dtcCode = input.dtcCode,
                mileageKm = input.mileageKm,
                severity = "Критично (Fake)",
                recommendation = "[FAKE РЕЖИМ] Виявлено численні пропуски запалювання. Перевірте свічки та котушки."
            )
            else -> DiagnosticPrediction(
                dtcCode = input.dtcCode,
                mileageKm = input.mileageKm,
                severity = "Середньо (Fake)",
                recommendation = "[FAKE РЕЖИМ] Тестова рекомендація для коду ${input.dtcCode} без звернення до сервера."
            )
        }
        return Result.success(fakePrediction)
    }
}