package com.example.smartwrench

import kotlinx.coroutines.delay
import java.io.IOException

// МОДУЛЬ 2: Обробка та інтерпретація результату AI (Post-processing)
object DiagnosticPostprocessor {
    fun process(input: DiagnosticInput, dto: DTCResponse): DiagnosticPrediction {
        // Визначаємо, чи є результат невпевненим (Uncertain)
        val isGenericFallback = dto.recommendation.contains("Зверніться до технічної документації", ignoreCase = true)
        val isUncertain = (dto.is_uncertain == true) || (isGenericFallback && input.symptoms.isNullOrBlank())

        // Якщо водій вказав критичні симптоми, підвищуємо пріоритет безпеки
        val finalSeverity = if (input.hasCriticalSymptoms && dto.severity == "Низько") {
            "Середньо (підвищено за симптомами)"
        } else {
            dto.severity
        }

        // Формуємо вердикт щодо дозволу на рух
        val canDriveText = dto.can_drive ?: when {
            isUncertain -> "Невизначено (уточніть симптоми авто)"
            finalSeverity.contains("Критично", ignoreCase = true) || input.hasCriticalSymptoms ->
                "Рух заборонено або вкрай небезпечно (рекомендовано евакуатор)"
            finalSeverity.contains("Середньо", ignoreCase = true) ->
                "Обмежений рух (обережно прямуйте до СТО)"
            else -> "Рух дозволено (заплануйте візит на СТО)"
        }

        // Доповнюємо рекомендацію контекстом двигуна, якщо сервер повернув базову відповідь
        var finalRecommendation = dto.recommendation
        if (input.engineType == "Дизель" && finalRecommendation.contains("свічок запалювання", ignoreCase = true)) {
            finalRecommendation = "Для дизельного двигуна: пропуски займання у циліндрах. Перевірте паливні форсунки, компресію та свічки розжарювання."
        }
        if (!input.symptoms.isNullOrBlank() && !finalRecommendation.contains("симптом", ignoreCase = true)) {
            finalRecommendation += "\n\nВраховано симптоми («${input.symptoms}»): рекомендується першочергова перевірка вузлів під навантаженням."
        }

        return DiagnosticPrediction(
            dtcCode = input.dtcCode,
            mileageKm = input.mileageKm,
            engineType = input.engineType,
            symptoms = input.symptoms,
            severity = if (isUncertain) "Невизначено" else finalSeverity,
            recommendation = finalRecommendation,
            canDrive = canDriveText,
            isUncertain = isUncertain
        )
    }
}

object DiagnosticMapper {
    fun toDto(input: DiagnosticInput): DTCRequest =
        DTCRequest(
            dtc_code = input.dtcCode,
            mileage_km = input.mileageKm,
            engine_type = input.engineType,
            symptoms = input.symptoms
        )

    fun toDomain(input: DiagnosticInput, dto: DTCResponse): DiagnosticPrediction =
        DiagnosticPostprocessor.process(input, dto)
}

class RealDiagnosticRepository(
    private val api: SmartWrenchApi
) : DiagnosticRepository {

    override suspend fun analyze(input: DiagnosticInput): Result<DiagnosticPrediction> {
        return try {
            val responseDto = api.analyzeError(DiagnosticMapper.toDto(input))
            if (responseDto.severity.isBlank() || responseDto.recommendation.isBlank()) {
                Result.failure(DomainError.ServerIncompleteDataError)
            } else {
                Result.success(DiagnosticMapper.toDomain(input, responseDto))
            }
        } catch (e: IOException) {
            Result.failure(DomainError.NetworkUnavailableError)
        } catch (e: Exception) {
            Result.failure(DomainError.UnknownError(e.localizedMessage ?: "Невідома помилка"))
        }
    }
}

class FakeDiagnosticRepository(
    var shouldSimulateNetworkError: Boolean = false
) : DiagnosticRepository {

    override suspend fun analyze(input: DiagnosticInput): Result<DiagnosticPrediction> {
        delay(300)
        if (shouldSimulateNetworkError) {
            return Result.failure(DomainError.NetworkUnavailableError)
        }

        val dto = when (input.dtcCode) {
            "P0300" -> DTCResponse(
                severity = "Критично",
                recommendation = "Пропуски запалювання. Рекомендується перевірити стан свічок запалювання та цілісність ізоляції високовольтних дротів."
            )
            "P0171" -> DTCResponse(
                severity = "Середньо",
                recommendation = "Занадто бідна суміш. Перевірте паливний фільтр, підсос повітря та бензонасос."
            )
            "P0420", "P0421" -> DTCResponse(
                severity = "Низько",
                recommendation = "Знижена ефективність роботи каталітичного нейтралізатора."
            )
            "P0113" -> DTCResponse(
                severity = "Низько",
                recommendation = "Високий рівень сигналу датчика температури повітря на впуску (IAT)."
            )
            else -> DTCResponse(
                severity = "Низько",
                recommendation = "Виявлено код ${input.dtcCode}. Зверніться до технічної документації для детальнішої діагностики.",
                is_uncertain = input.symptoms.isNullOrBlank()
            )
        }
        return Result.success(DiagnosticMapper.toDomain(input, dto))
    }
}
