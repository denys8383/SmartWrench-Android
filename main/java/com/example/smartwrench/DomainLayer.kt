package com.example.smartwrench

// 1. Доменні моделі (не залежать від JSON чи Retrofit)
data class DiagnosticInput(
    val dtcCode: String,
    val mileageKm: Int?
)

data class DiagnosticPrediction(
    val dtcCode: String,
    val mileageKm: Int?,
    val severity: String,
    val recommendation: String
)

// 2. Визначені категорії доменних помилок
sealed class DomainError : Exception() {
    object EmptyCodeError : DomainError()
    object InvalidCodeFormatError : DomainError()
    object NetworkUnavailableError : DomainError()
    object ServerIncompleteDataError : DomainError()
    data class UnknownError(val details: String) : DomainError()
}

// 3. Мінімальний інтерфейс репозиторію (контракт для Real та Fake реалізацій)
interface DiagnosticRepository {
    suspend fun analyze(input: DiagnosticInput): Result<DiagnosticPrediction>
}

// 4. Use Case: прикладна логіка та валідація перед викликом репозиторію
class AnalyzeDtcUseCase(
    private val repository: DiagnosticRepository
) {
    suspend operator fun invoke(rawCode: String, rawMileage: String): Result<DiagnosticPrediction> {
        val cleanCode = rawCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            return Result.failure(DomainError.EmptyCodeError)
        }
        // Базова перевірка формату коду OBD-II (наприклад, P0171, P0300, P023)
        if (!cleanCode.matches(Regex("^[PCBU][0-9A-Z]{3,4}$"))) {
            return Result.failure(DomainError.InvalidCodeFormatError)
        }

        val mileage = rawMileage.trim().toIntOrNull()
        val input = DiagnosticInput(dtcCode = cleanCode, mileageKm = mileage)

        return repository.analyze(input)
    }
}