package com.example.smartwrench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. Чіткий UI State зі станами Idle, Loading, Success і Error
sealed class DiagnosticUiState {
    object Idle : DiagnosticUiState()
    object Loading : DiagnosticUiState()
    data class Success(val prediction: DiagnosticPrediction) : DiagnosticUiState()
    data class Error(val message: String, val canRetry: Boolean) : DiagnosticUiState()
}

// 2. ViewModel, яка отримує залежності ззовні через Constructor Injection (DI)
class DiagnosticViewModel(
    private var analyzeDtcUseCase: AnalyzeDtcUseCase,
    private val onSuccessSaveHistory: (suspend (DiagnosticPrediction) -> Unit)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiagnosticUiState>(DiagnosticUiState.Idle)
    val uiState: StateFlow<DiagnosticUiState> = _uiState.asStateFlow()

    // Збереження останніх введених даних для функції контрольного розширення (Retry)
    private var lastRawCode: String = ""
    private var lastRawMileage: String = ""

    fun analyze(rawCode: String, rawMileage: String) {
        lastRawCode = rawCode
        lastRawMileage = rawMileage

        _uiState.value = DiagnosticUiState.Loading

        viewModelScope.launch {
            val result = analyzeDtcUseCase(rawCode, rawMileage)
            result.fold(
                onSuccess = { prediction ->
                    onSuccessSaveHistory?.invoke(prediction)
                    _uiState.value = DiagnosticUiState.Success(prediction)
                },
                onFailure = { throwable ->
                    val (msg, canRetry) = mapErrorToUiMessage(throwable)
                    _uiState.value = DiagnosticUiState.Error(msg, canRetry)
                }
            )
        }
    }

    // КОНТРОЛЬНЕ РОЗШИРЕННЯ: Повтор останнього невдалого запиту без втрати введених даних
    fun retryLastRequest() {
        if (lastRawCode.isNotBlank()) {
            analyze(lastRawCode, lastRawMileage)
        }
    }

    // Дозволяє перемикати Real / Fake UseCase на льоту (Debug-перемикач)
    fun updateUseCase(newUseCase: AnalyzeDtcUseCase) {
        this.analyzeDtcUseCase = newUseCase
    }

    private fun mapErrorToUiMessage(error: Throwable): Pair<String, Boolean> {
        return when (error) {
            is DomainError.EmptyCodeError -> "Введіть код помилки!" to false
            is DomainError.InvalidCodeFormatError -> "Невірний формат коду (має починатися з P, C, B або U, напр. P0300)." to false
            is DomainError.NetworkUnavailableError -> "Помилка зв'язку: сервер недоступний. Перевірте підключення або натисніть «Повторити»." to true
            is DomainError.ServerIncompleteDataError -> "Сервер повернув неповні дані. Спробуйте ще раз." to true
            else -> "Помилка обробки запиту: ${error.message}" to true
        }
    }
}

// Фабрика для Dependency Injection у ViewModel
class DiagnosticViewModelFactory(
    private val analyzeDtcUseCase: AnalyzeDtcUseCase,
    private val onSuccessSaveHistory: suspend (DiagnosticPrediction) -> Unit
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DiagnosticViewModel(analyzeDtcUseCase, onSuccessSaveHistory) as T
    }
}