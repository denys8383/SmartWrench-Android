# API Контракт
**POST /api/analyze**
*Request:*
{
  "dtc_code": "P0171",
  "mileage_km": 150000 // Нове необов'язкове поле
}
*Response (200 OK):*
{
  "status": "success",
  "severity": "Середньо",
  "recommendation": "Перевірте паливний фільтр."
}
*Errors:* 400 Bad Request (порожній код), 500 Internal Error.