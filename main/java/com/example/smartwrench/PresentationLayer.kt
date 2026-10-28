package com.example.smartwrench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Стани UI: очікування, завантаження, успіх, невпевнений результат (Uncertain) та помилка
sealed class DiagnosticUiState {
    object Idle : DiagnosticUiState()
    object Loading : DiagnosticUiState()
    data class Success(val prediction: DiagnosticPrediction) : DiagnosticUiState()
    data class Uncertain(val prediction: DiagnosticPrediction, val reason: String) : DiagnosticUiState()
    data class Error(val message: String, val canRetry: Boolean) : DiagnosticUiState()
}

class DiagnosticViewModel(
    private var analyzeDtcUseCase: AnalyzeDtcUseCase,
    private val onSuccessSaveHistory: (suspend (DiagnosticPrediction) -> Unit)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiagnosticUiState>(DiagnosticUiState.Idle)
    val uiState: StateFlow<DiagnosticUiState> = _uiState.asStateFlow()

    private var lastRawCode: String = ""
    private var lastRawMileage: String = ""
    private var lastEngineType: String = "Бензин"
    private var lastSymptoms: String = ""

    fun analyze(
        rawCode: String,
        rawMileage: String,
        rawEngineType: String = "Бензин",
        rawSymptoms: String = ""
    ) {
        lastRawCode = rawCode
        lastRawMileage = rawMileage
        lastEngineType = rawEngineType
        lastSymptoms = rawSymptoms

        _uiState.value = DiagnosticUiState.Loading

        viewModelScope.launch {
            val result = analyzeDtcUseCase(rawCode, rawMileage, rawEngineType, rawSymptoms)
            result.fold(
                onSuccess = { prediction ->
                    if (prediction.isUncertain) {
                        _uiState.value = DiagnosticUiState.Uncertain(
                            prediction = prediction,
                            reason = "Недостатньо даних для точного AI-висновку за рідкісним кодом ${prediction.dtcCode}. Будь ласка, опишіть симптоми авто у полі вище і повторіть запит."
                        )
                    } else {
                        onSuccessSaveHistory?.invoke(prediction)
                        _uiState.value = DiagnosticUiState.Success(prediction)
                    }
                },
                onFailure = { throwable ->
                    val (msg, canRetry) = mapErrorToUiMessage(throwable)
                    _uiState.value = DiagnosticUiState.Error(msg, canRetry)
                }
            )
        }
    }

    fun retryLastRequest() {
        if (lastRawCode.isNotBlank()) {
            analyze(lastRawCode, lastRawMileage, lastEngineType, lastSymptoms)
        }
    }

    fun updateUseCase(newUseCase: AnalyzeDtcUseCase) {
        this.analyzeDtcUseCase = newUseCase
    }

    private fun mapErrorToUiMessage(error: Throwable): Pair<String, Boolean> {
        return when (error) {
            is DomainError.EmptyCodeError -> "Введіть код помилки!" to false
            is DomainError.InvalidCodeFormatError -> "Невірний формат коду (має починатися з P, C, B або U, напр. P0300)." to false
            is DomainError.InvalidMileageRangeError -> "Некоректний пробіг (допустимо від 0 до 2 000 000 км)." to false
            is DomainError.SymptomsTooLongError -> "Опис симптомів занадто довгий (максимум 300 символів)." to false
            is DomainError.NetworkUnavailableError -> "Помилка зв'язку або перевищено час очікування сервера. Перевірте підключення або натисніть «Повторити»." to true
            is DomainError.ServerIncompleteDataError -> "Сервер повернув неповні дані. Спробуйте ще раз." to true
            else -> "Помилка обробки запиту: ${error.message}" to true
        }
    }
}

class DiagnosticViewModelFactory(
    private val analyzeDtcUseCase: AnalyzeDtcUseCase,
    private val onSuccessSaveHistory: suspend (DiagnosticPrediction) -> Unit
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DiagnosticViewModel(analyzeDtcUseCase, onSuccessSaveHistory) as T
    }
}
