package com.example.smartwrench

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Вказуємо, який файл дизайну використовувати
        setContentView(R.layout.activity_main)

        // Знаходимо елементи інтерфейсу
        val etDtcCode = findViewById<EditText>(R.id.etDtcCode)
        val etMileage = findViewById<EditText>(R.id.etMileage)
        val btnAnalyze = findViewById<Button>(R.id.btnAnalyze)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        btnAnalyze.setOnClickListener {
            val code = etDtcCode.text.toString()
            val mileageStr = etMileage.text.toString()
            val mileageInt = if (mileageStr.isNotBlank()) mileageStr.toIntOrNull() else null
            val request = DTCRequest(dtc_code = code, mileage_km = mileageInt)

            if (code.isNotBlank()) {
                progressBar.visibility = View.VISIBLE
                tvResult.text = ""
                btnAnalyze.isEnabled = false

                // Запускаємо запит до сервера
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = NetworkClient.api.analyzeError(request)
                        withContext(Dispatchers.Main) {
                            tvResult.text = "Рівень небезпеки: ${response.severity}\n\nРекомендація:\n${response.recommendation}"
                            progressBar.visibility = View.GONE
                            btnAnalyze.isEnabled = true
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            // Перевіряємо, чи це помилка з'єднання (сервер вимкнений / недоступний)
                            val errorMessage = if (e is java.net.ConnectException || e.localizedMessage?.contains("failed to connect") == true) {
                                "Помилка зв'язку: сервер недоступний. Перевірте, чи запущено сервер у PyCharm."
                            } else {
                                "Помилка: ${e.localizedMessage ?: e.toString()}"
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
}
