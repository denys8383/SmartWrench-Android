package com.example.smartwrench

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val BASE_URL = "http://10.0.10.163:8000/"

    // Обмежений час очікування для мережевих викликів (вимога ЛР №6)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    val api: SmartWrenchApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
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

        val realRepository = RealDiagnosticRepository(NetworkClient.api)
        val fakeRepository = FakeDiagnosticRepository()
        val initialUseCase = AnalyzeDtcUseCase(realRepository)

        val factory = DiagnosticViewModelFactory(initialUseCase) { prediction ->
            val record = DiagnosticRecord(
                dtcCode = "${prediction.dtcCode} (${prediction.engineType})",
                mileage = prediction.mileageKm,
                severity = prediction.severity,
                recommendation = "${prediction.recommendation}\nДозвіл на рух: ${prediction.canDrive}",
                status = "готово"
            )
            historyManager.saveRecord(record)
        }
        viewModel = ViewModelProvider(this, factory)[DiagnosticViewModel::class.java]

        val cbFakeMode = findViewById<CheckBox>(R.id.cbFakeMode)
        val etDtcCode = findViewById<EditText>(R.id.etDtcCode)
        val etMileage = findViewById<EditText>(R.id.etMileage)
        val spEngineType = findViewById<Spinner>(R.id.spEngineType)
        val etSymptoms = findViewById<EditText>(R.id.etSymptoms)
        val btnAnalyze = findViewById<Button>(R.id.btnAnalyze)
        val btnRetry = findViewById<Button>(R.id.btnRetry)
        val btnClearHistory = findViewById<Button>(R.id.btnClearHistory)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvResult = findViewById<TextView>(R.id.tvResult)
        val tvHistory = findViewById<TextView>(R.id.tvHistory)

        // Ініціалізація списку типів двигунів
        val engineTypes = arrayOf("Бензин", "Дизель", "Гібрид")
        spEngineType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, engineTypes)

        cbFakeMode.setOnCheckedChangeListener { _, isChecked ->
            val selectedRepo = if (isChecked) fakeRepository else realRepository
            viewModel.updateUseCase(AnalyzeDtcUseCase(selectedRepo))
        }

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
                        tvResult.setTextColor(Color.parseColor("#B00020"))
                        val p = state.prediction
                        tvResult.text = "Рівень небезпеки: ${p.severity}\n" +
                                "Тип двигуна: ${p.engineType}\n" +
                                "Статус руху: ${p.canDrive}\n\n" +
                                "Рекомендація AI:\n${p.recommendation}"
                    }
                    is DiagnosticUiState.Uncertain -> {
                        progressBar.visibility = View.GONE
                        btnAnalyze.isEnabled = true
                        btnRetry.visibility = View.VISIBLE
                        tvResult.setTextColor(Color.parseColor("#E65100"))
                        tvResult.text = "⚠️ Невпевнений результат (Uncertain)\n\n${state.reason}"
                    }
                    is DiagnosticUiState.Error -> {
                        progressBar.visibility = View.GONE
                        btnAnalyze.isEnabled = true
                        tvResult.setTextColor(Color.parseColor("#B00020"))
                        tvResult.text = state.message
                        btnRetry.visibility = if (state.canRetry) View.VISIBLE else View.GONE
                    }
                }
            }
        }

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
            viewModel.analyze(
                rawCode = etDtcCode.text.toString(),
                rawMileage = etMileage.text.toString(),
                rawEngineType = spEngineType.selectedItem.toString(),
                rawSymptoms = etSymptoms.text.toString()
            )
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
