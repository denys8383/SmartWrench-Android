# Архітектура мобільного застосунку (Оновлення ЛР №4)

## Розподіл відповідальності (Separation of Concerns)
Після додавання локального сховища архітектуру мобільного клієнта розділено на три незалежні шари:

1. **Шар представлення (UI Layer — `MainActivity.kt`, `activity_main.xml`):**
   - Збирає вхідні дані користувача та керує станом завантаження (`ProgressBar`).
   - Підписується на реактивний потік `historyFlow` у межах життєвого циклу активності (`lifecycleScope`).
   - Відповідає за відображення списку історії, модального вікна деталей запису (`showRecordDetailsDialog`) та діалогів підтвердження видалення. Не містить прямого коду роботи з файлами чи серіалізацією.

2. **Шар локальних даних (Persistence Layer — `HistoryManager.kt`, `DiagnosticRecord.kt`):**
   - Інкапсулює всю роботу з постійним сховищем `Jetpack DataStore (Preferences)`.
   - Забезпечує потокобезпечні асинхронні операції: додавання запису на початок списку (`saveRecord`), видалення за ідентифікатором (`deleteRecord`), повне очищення (`clearHistory`) та читання через `Flow`.
   - Фізично зберігає дані у внутрішній захищеній директорії додатка (`/data/data/com.example.smartwrench/files/datastore/smartwrench_history.preferences_pb`).

3. **Мережевий шар (Network Layer — `NetworkClient`, `SmartWrenchApi.kt`, `Models.kt`):**
   - Виконує HTTP-запити до AI-бекенду через `Retrofit2` та конвертує мережеві DTO (`DTCRequest`, `DTCResponse`).