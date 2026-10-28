package com.example.smartwrench

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArchitectureUnitTest {

    // Тест 1: Успішний сценарій аналізу через FakeRepository (без мережі)
    @Test
    fun `analyze valid code P0300 returns Critical severity from FakeRepository`() = runBlocking {
        val fakeRepo = FakeDiagnosticRepository(shouldSimulateNetworkError = false)
        val useCase = AnalyzeDtcUseCase(fakeRepo)

        val result = useCase(rawCode = "p0300", rawMileage = "12369")

        assertTrue(result.isSuccess)
        val prediction = result.getOrNull()!!
        assertEquals("P0300", prediction.dtcCode)
        assertEquals(12369, prediction.mileageKm)
        assertTrue(prediction.severity.contains("Критично"))
    }

    // Тест 2: Валідація порожнього коду повертає EmptyCodeError без звернення до мережі
    @Test
    fun `analyze blank code returns EmptyCodeError`() = runBlocking {
        val fakeRepo = FakeDiagnosticRepository()
        val useCase = AnalyzeDtcUseCase(fakeRepo)

        val result = useCase(rawCode = "   ", rawMileage = "10000")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DomainError.EmptyCodeError)
    }

    // Тест 3: Валідація некоректного формату коду повертає InvalidCodeFormatError
    @Test
    fun `analyze invalid format code returns InvalidCodeFormatError`() = runBlocking {
        val fakeRepo = FakeDiagnosticRepository()
        val useCase = AnalyzeDtcUseCase(fakeRepo)

        val result = useCase(rawCode = "12345", rawMileage = "50000")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DomainError.InvalidCodeFormatError)
    }

    // Тест 4: Контрольована обробка мережевої помилки через FakeRepository
    @Test
    fun `analyze when network is down returns NetworkUnavailableError`() = runBlocking {
        val fakeRepo = FakeDiagnosticRepository(shouldSimulateNetworkError = true)
        val useCase = AnalyzeDtcUseCase(fakeRepo)

        val result = useCase(rawCode = "P0171", rawMileage = "100000")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DomainError.NetworkUnavailableError)
    }

    // Тест 5: Перевірка коректності роботи DiagnosticMapper (DTO <-> Domain)
    @Test
    fun `mapper correctly converts Domain input to DTO and Response DTO to Domain`() {
        val input = DiagnosticInput(dtcCode = "P0420", mileageKm = 258965)
        val dtoRequest = DiagnosticMapper.toDto(input)

        assertEquals("P0420", dtoRequest.dtc_code)
        assertEquals(258965, dtoRequest.mileage_km)

        val dtoResponse = DTCResponse(
            status = "success",
            severity = "Низько",
            recommendation = "Перевірте каталізатор"
        )
        val domainPrediction = DiagnosticMapper.toDomain(input, dtoResponse)

        assertEquals("P0420", domainPrediction.dtcCode)
        assertEquals("Низько", domainPrediction.severity)
        assertEquals("Перевірте каталізатор", domainPrediction.recommendation)
    }

    // Тест 6: Тест сценарію повторного запиту (Retry) після відновлення зв'язку
    @Test
    fun `retry after network failure succeeds when connection is restored`() = runBlocking {
        val fakeRepo = FakeDiagnosticRepository(shouldSimulateNetworkError = true)
        val useCase = AnalyzeDtcUseCase(fakeRepo)

        // Перша спроба — помилка мережі
        val firstAttempt = useCase("P0113", "852698")
        assertTrue(firstAttempt.isFailure)

        // Відновлення мережі та повтор того самого запиту
        fakeRepo.shouldSimulateNetworkError = false
        val retryAttempt = useCase("P0113", "852698")

        assertTrue(retryAttempt.isSuccess)
        assertEquals("P0113", retryAttempt.getOrNull()?.dtcCode)
    }
}