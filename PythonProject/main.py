from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
import uvicorn
import asyncio

app = FastAPI(title="SmartWrench API")


# 1. Описані моделі вхідних даних і відповіді (Pydantic)
class DTCRequest(BaseModel):
    dtc_code: str = Field(..., min_length=4, max_length=5, description="Код помилки, наприклад P0171")


class DTCResponse(BaseModel):
    status: str
    severity: str
    recommendation: str


# 2. GET /health для перевірки доступності сервісу
@app.get("/health")
def health_check():
    return {"status": "ok"}


# 3. AI компонент (Симуляція логіки для MVP)
async def analyze_dtc_with_ai(code: str) -> dict:
    await asyncio.sleep(0.5)  # Час обробки запиту

    code = code.upper()
    # Контекст: специфіка 2.0L бензинового двигуна
    if code == "P0171":
        return {
            "severity": "Середньо",
            "recommendation": "Занадто бідна суміш. Перевірте паливний фільтр та бензонасос, особливо якщо авто довго стояло в гаражі. Огляньте вакуумні шланги."
        }
    elif code == "P0300":
        return {
            "severity": "Критично",
            "recommendation": "Пропуски запалювання. Рекомендується перевірити стан свічок запалювання та цілісність ізоляції високовольтних дротів."
        }
    elif code == "P0102":
        return {
            "severity": "Середньо",
            "recommendation": "Низький рівень сигналу датчика витрати повітря (MAF). Перевірте датчик MAF та його проводку."
        }
    elif code == "P0420":
        return {
            "severity": "Низько",
            "recommendation": "Ефективність каталітичної системи нижче порогового значення. Перевірте стан каталізатора та лямбда-зонда."
        }
    elif code == "P0113":
        return {
            "severity": "Низько",
            "recommendation": "Високий показник датчика температури повітря на впуску (IAT). Перевірте підключення датчика."
        }
    else:
        return {
            "severity": "Низько",
            "recommendation": f"Виявлено код {code}. Зверніться до технічної документації для детальнішої діагностики."
        }


# 4. Основний POST endpoint для функціонального сценарію
@app.post("/api/analyze", response_model=DTCResponse)
async def analyze_error(request: DTCRequest):
    # Валідація та зрозумілі HTTP-помилки
    if not request.dtc_code.strip():
        raise HTTPException(status_code=400, detail="Код помилки не може бути порожнім")

    try:
        # Виклик простої AI-функції
        ai_result = await analyze_dtc_with_ai(request.dtc_code)

        return DTCResponse(
            status="success",
            severity=ai_result["severity"],
            recommendation=ai_result["recommendation"]
        )
    except Exception as e:
        # Не показуємо користувачеві внутрішній stack trace
        raise HTTPException(status_code=500, detail="Помилка генерації AI-відповіді")


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)