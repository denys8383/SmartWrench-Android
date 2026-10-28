package com.example.smartwrench

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NetworkClient {
    private const val BASE_URL = "http://10.0.10.163:8000/"
    val api: SmartWrenchApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SmartWrenchApi::class.java)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var historyManager: HistoryManager
    private lateinit var viewModel: DiagnosticViewModel
    private var currentHistoryList: List<DiagnosticRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        historyManager = HistoryManager(applicationContext)

        // Composition Root (Dependency Injection)
        val realRepository = RealDiagnosticRepository(NetworkClient.api)
        val fakeRepository = FakeDiagnosticRepository()
        val initialUseCase = AnalyzeDtcUseCase(realRepository)

        val factory = DiagnosticViewModelFactory(initialUseCase) { prediction ->
            val record = DiagnosticRecord(
                dtcCode = prediction.dtcCode,
                mileage = prediction.mileageKm,
                severity = prediction.severity,
                recommendation = prediction.recommendation,
                status = "готово"
            )
            historyManager.saveRecord(record)
        }
        viewModel = ViewModelProvider(this, factory)[DiagnosticViewModel::class.java]

        val cbFakeMode = findViewById<CheckBox>(R.id.cbFakeMode)
        val etDtcCode = findViewById<EditText>(R.id.etDtcCode)
        val etMileage = findViewById<EditText>(R.id.etMileage)
        val btnAnalyze = findViewById<Button>(R.id.btnAnalyze)
        val btnRetry = findViewById<Button>(R.id.btnRetry)
        val btnClearHistory = findViewById<Button>(R.id.btnClearHistory)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvResult = findViewById<TextView>(R.id.tvResult)
        val tvHistory = findViewById<TextView>(R.id.tvHistory)

        // Перемикання між Real та Fake репозиторієм через DI
        cbFakeMode.setOnCheckedChangeListener { _, isChecked ->
            val selectedRepo = if (isChecked) fakeRepository else realRepository
            viewModel.updateUseCase(AnalyzeDtcUseCase(selectedRepo))
        }

        // Спостереження за UI State (Idle, Loading, Success, Error)
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is DiagnosticUiState.Idle -> {
                        progressBar.visibility = View.GONE
                        btnAnalyze.isEnabled = true
                        btnRetry.visibility = View.GONE
                    }
                    is DiagnosticUiState.Loading -> {
                        progressBar.visibility = View.VISIBLE
                        btnAnalyze.isEnabled = false
                        btnRetry.visibility = View.GONE
                        tvResult.text = ""
                    }
                    is DiagnosticUiState.Success -> {
                        progressBar.visibility = View.GONE
                        btnAnalyze.isEnabled = true
                        btnRetry.visibility = View.GONE
                        tvResult.text = "Рівень небезпеки: ${state.prediction.severity}\n\nРекомендація:\n${state.prediction.recommendation}"
                    }
                    is DiagnosticUiState.Error -> {
                        progressBar.visibility = View.GONE
                        btnAnalyze.isEnabled = true
                        tvResult.text = state.message
                        btnRetry.visibility = if (state.canRetry) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        // Спостереження за локальною історією (збережено сценарій ЛР 4)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        lifecycleScope.launch {
            historyManager.historyFlow.collect { historyList ->
                currentHistoryList = historyList
                if (historyList.isEmpty()) {
                    tvHistory.text = "Історія порожня. Зробіть свій перший запит."
                    btnClearHistory.visibility = View.GONE
                } else {
                    btnClearHistory.visibility = View.VISIBLE
                    val historyText = StringBuilder("--- Історія запитів (натисніть для деталей або видалення) ---\n\n")
                    for ((index, record) in historyList.withIndex()) {
                        val dateString = dateFormat.format(Date(record.createdAt))
                        historyText.append("${index + 1}. [$dateString] Статус: ${record.status}\n")
                        historyText.append("Вхід: Код ${record.dtcCode} (Пробіг: ${record.mileage ?: "не вказано"})\n")
                        historyText.append("Результат: ${record.severity}\n")
                        historyText.append("-----------------------------------------\n")
                    }
                    tvHistory.text = historyText.toString()
                }
            }
        }

        btnAnalyze.setOnClickListener {
            viewModel.analyze(etDtcCode.text.toString(), etMileage.text.toString())
        }

        btnRetry.setOnClickListener {
            viewModel.retryLastRequest()
        }

        tvHistory.setOnClickListener {
            if (currentHistoryList.isNotEmpty()) {
                val items = currentHistoryList.mapIndexed { index, record ->
                    val dateStr = dateFormat.format(Date(record.createdAt))
                    "${index + 1}. ${record.dtcCode} ($dateStr) — ${record.severity}"
                }.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("Оберіть запис для перегляду деталей")
                    .setItems(items) { _, which ->
                        val selected = currentHistoryList[which]
                        showRecordDetailsDialog(selected, dateFormat.format(Date(selected.createdAt)))
                    }
                    .setNegativeButton("Закрити", null)
                    .show()
            }
        }

        btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Підтвердження очищення")
                .setMessage("Ви дійсно хочете видалити всю історію діагностики? Цю дію неможливо скасувати.")
                .setPositiveButton("Очистити") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        historyManager.clearHistory()
                    }
                }
                .setNegativeButton("Скасувати", null)
                .show()
        }
    }

    private fun showRecordDetailsDialog(record: DiagnosticRecord, dateStr: String) {
        val detailsMessage = """
            ID запису: ${record.id}
            Дата і час: $dateStr
            Статус: ${record.status}
            
            --- Вхідні дані ---
            Код помилки (DTC): ${record.dtcCode}
            Пробіг авто: ${record.mileage?.let { "$it км" } ?: "Не вказано"}
            
            --- Результат AI ---
            Рівень небезпеки: ${record.severity}
            
            Рекомендація:
            ${record.recommendation}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Деталі запису: ${record.dtcCode}")
            .setMessage(detailsMessage)
            .setPositiveButton("Закрити", null)
            .setNeutralButton("Видалити запис") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Підтвердження видалення")
                    .setMessage("Видалити запис для коду ${record.dtcCode}?")
                    .setPositiveButton("Видалити") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            historyManager.deleteRecord(record.id)
                        }
                    }
                    .setNegativeButton("Скасувати", null)
                    .show()
            }
            .show()
    }
}
