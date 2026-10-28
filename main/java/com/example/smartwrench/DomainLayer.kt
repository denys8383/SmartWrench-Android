package com.example.smartwrench

// Розширена вхідна доменна модель (Варіант А: нові ознаки)
data class DiagnosticInput(
    val dtcCode: String,
    val mileageKm: Int?,
    val engineType: String = "Бензин",
    val symptoms: String? = null,
    val hasCriticalSymptoms: Boolean = false
)

// Розширений результат прогнозу
data class DiagnosticPrediction(
    val dtcCode: String,
    val mileageKm: Int?,
    val engineType: String = "Бензин",
    val symptoms: String? = null,
    val severity: String,
    val recommendation: String,
    val canDrive: String = "Потребує огляду",
    val isUncertain: Boolean = false
)

// Категорії помилок (додано перевірку меж пробігу та довжини тексту)
sealed class DomainError : Exception() {
    object EmptyCodeError : DomainError()
    object InvalidCodeFormatError : DomainError()
    object InvalidMileageRangeError : DomainError()
    object SymptomsTooLongError : DomainError()
    object NetworkUnavailableError : DomainError()
    object ServerIncompleteDataError : DomainError()
    data class UnknownError(val details: String) : DomainError()
}

interface DiagnosticRepository {
    suspend fun analyze(input: DiagnosticInput): Result<DiagnosticPrediction>
}

// МОДУЛЬ 1: Підготовка та перевірка вхідних даних (Data Preparation)
object DiagnosticPreprocessor {
    private val criticalKeywords = listOf("дим", "глохне", "стук", "гальма", "перегрів", "бензин", "олива")

    fun prepare(
        rawCode: String,
        rawMileage: String,
        rawEngineType: String = "Бензин",
        rawSymptoms: String = ""
    ): Result<DiagnosticInput> {
        val cleanCode = rawCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            return Result.failure(DomainError.EmptyCodeError)
        }
        if (!cleanCode.matches(Regex("^[PCBU][0-9A-Z]{3,4}$"))) {
            return Result.failure(DomainError.InvalidCodeFormatError)
        }

        val mileage: Int? = if (rawMileage.isBlank()) {
            null
        } else {
            val parsed = rawMileage.trim().toIntOrNull()
            if (parsed == null || parsed < 0 || parsed > 2_000_000) {
                return Result.failure(DomainError.InvalidMileageRangeError)
            }
            parsed
        }

        val cleanSymptoms = rawSymptoms.trim()
        if (cleanSymptoms.length > 300) {
            return Result.failure(DomainError.SymptomsTooLongError)
        }

        val hasCritical = criticalKeywords.any { cleanSymptoms.lowercase().contains(it) }

        return Result.success(
            DiagnosticInput(
                dtcCode = cleanCode,
                mileageKm = mileage,
                engineType = rawEngineType.ifBlank { "Бензин" },
                symptoms = cleanSymptoms.ifBlank { null },
                hasCriticalSymptoms = hasCritical
            )
        )
    }
}

// Use Case координує підготовку даних і виклик AI-репозиторію
class AnalyzeDtcUseCase(
    private val repository: DiagnosticRepository
) {
    suspend operator fun invoke(
        rawCode: String,
        rawMileage: String,
        rawEngineType: String = "Бензин",
        rawSymptoms: String = ""
    ): Result<DiagnosticPrediction> {
        val preparedResult = DiagnosticPreprocessor.prepare(rawCode, rawMileage, rawEngineType, rawSymptoms)
        val input = preparedResult.getOrElse { return Result.failure(it) }
        return repository.analyze(input)
    }
}
