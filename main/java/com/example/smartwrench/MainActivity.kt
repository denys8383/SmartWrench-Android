package com.example.smartwrench

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var currentHistoryList: List<DiagnosticRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        historyManager = HistoryManager(applicationContext)

        val etDtcCode = findViewById<EditText>(R.id.etDtcCode)
        val etMileage = findViewById<EditText>(R.id.etMileage)
        val btnAnalyze = findViewById<Button>(R.id.btnAnalyze)
        val btnClearHistory = findViewById<Button>(R.id.btnClearHistory)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvResult = findViewById<TextView>(R.id.tvResult)
        val tvHistory = findViewById<TextView>(R.id.tvHistory)

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        // Реактивне отримання списку історії у зворотному хронологічному порядку
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

        // Відкриття списку записів для перегляду деталей або видалення конкретного запису
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

        // Очищення всієї історії з підтвердженням дії
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

        btnAnalyze.setOnClickListener {
            val code = etDtcCode.text.toString().trim()
            val mileageStr = etMileage.text.toString().trim()
            val mileageInt = if (mileageStr.isNotBlank()) mileageStr.toIntOrNull() else null
            val request = DTCRequest(dtc_code = code, mileage_km = mileageInt)

            if (code.isNotBlank()) {
                progressBar.visibility = View.VISIBLE
                tvResult.text = ""
                btnAnalyze.isEnabled = false

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val response = NetworkClient.api.analyzeError(request)

                        // Валідація: перевіряємо, що відповідь не порожня
                        if (response.severity.isNotBlank() && response.recommendation.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                tvResult.text = "Рівень небезпеки: ${response.severity}\n\nРекомендація:\n${response.recommendation}"
                                progressBar.visibility = View.GONE
                                btnAnalyze.isEnabled = true
                            }

                            // Зберігаємо тільки успішний і повний результат
                            val record = DiagnosticRecord(
                                dtcCode = code,
                                mileage = mileageInt,
                                severity = response.severity,
                                recommendation = response.recommendation,
                                status = "готово"
                            )
                            historyManager.saveRecord(record)
                        } else {
                            throw IllegalStateException("Отримано неповну відповідь від сервера")
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            val errorMessage = if (e is java.net.ConnectException || e.localizedMessage?.contains("failed to connect") == true) {
                                "Помилка зв'язку: сервер недоступний. Перевірте, чи запущено сервер."
                            } else {
                                "Помилка обробки запиту. Спробуйте пізніше."
                            }
                            tvResult.text = errorMessage
                            progressBar.visibility = View.GONE
                            btnAnalyze.isEnabled = true
                        }
                    }
                }
            } else {
                tvResult.text = "Введіть код помилки!"
            }
        }
    }

    // Вікно деталей обраного запису з можливістю видалення
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
                // Підтвердження видалення одного запису
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
