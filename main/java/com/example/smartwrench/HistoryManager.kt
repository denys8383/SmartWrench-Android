package com.example.smartwrench

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Ініціалізація DataStore на рівні файлу
val Context.dataStore by preferencesDataStore(name = "smartwrench_history")

class HistoryManager(private val context: Context) {
    private val HISTORY_KEY = stringPreferencesKey("history_list_json")
    private val gson = Gson()

    // Отримання всієї історії (Flow автоматично оновлює UI при зміні даних)
    val historyFlow: Flow<List<DiagnosticRecord>> = context.dataStore.data.map { preferences ->
        val json = preferences[HISTORY_KEY] ?: "[]"
        val type = object : TypeToken<List<DiagnosticRecord>>() {}.type
        gson.fromJson(json, type)
    }

    // Збереження нового успішного запиту
    suspend fun saveRecord(record: DiagnosticRecord) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[HISTORY_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<DiagnosticRecord>>() {}.type
            val currentList: MutableList<DiagnosticRecord> = gson.fromJson(currentJson, type)

            currentList.add(0, record) // Додаємо найновіший запис нагору списку
            preferences[HISTORY_KEY] = gson.toJson(currentList)
        }
    }

    // Видалення одного запису за його ID
    suspend fun deleteRecord(id: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[HISTORY_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<DiagnosticRecord>>() {}.type
            val currentList: MutableList<DiagnosticRecord> = gson.fromJson(currentJson, type)

            currentList.removeAll { it.id == id }
            preferences[HISTORY_KEY] = gson.toJson(currentList)
        }
    }

    // Повне очищення історії
    suspend fun clearHistory() {
        context.dataStore.edit { preferences ->
            preferences.remove(HISTORY_KEY)
        }
    }
}