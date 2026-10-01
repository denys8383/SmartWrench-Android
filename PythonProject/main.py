from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import Optional
import uvicorn
import asyncio

app = FastAPI(title="SmartWrench API")


# 1. Описані моделі вхідних даних і відповіді (Pydantic)
class DTCRequest(BaseModel):
    dtc_code: str = Field(..., min_length=4, max_length=5, description="Код помилки, наприклад P0171")
    mileage_km: Optional[int] = Field(None, description="Пробіг авто")


class DTCResponse(BaseModel):
    status: str
    severity: str
    recommendation: str


# 2. GET /health для перевірки доступності сервісу
@app.get("/health")
def health_check():
    return {"status": "ok"}


# 3. AI компонент (Симуляція логіки для MVP)
async def analyze_dtc_with_ai(code: str, mileage: Optional[int] = None) -> dict:
    await asyncio.sleep(0.5)  # Час обробки запиту
    code = code.upper()

    # Контекст: специфіка 2.0L бензинового двигуна
    if code == "P0171":
        rec = "Занадто бідна суміш. Перевірте паливний фільтр та бензонасос, особливо якщо авто довго стояло в гаражі. Огляньте вакуумні шланги."
        if mileage and mileage > 100000:
            rec += " Враховуючи пробіг, ймовірно потрібна повна заміна бензонасоса."
        return {"severity": "Середньо", "recommendation": rec}

    elif code == "P0300":
        rec = "Пропуски запалювання. Рекомендується перевірити стан свічок запалювання та цілісність ізоляції високовольтних дротів."
        if mileage and mileage > 60000:
            rec += " Оскільки пробіг перевищує 60 000 км, свічки та дроти могли вичерпати свій ресурс. Рекомендується їх заміна."
        return {"severity": "Критично", "recommendation": rec}

    elif code == "P0102":
        rec = "Низький рівень сигналу датчика витрати повітря (MAF). Перевірте датчик MAF та його проводку."
        if mileage and mileage > 150000:
            rec += " При такому пробігу датчик часто забруднюється картерними газами. Спробуйте очистити його спеціальним спреєм перед тим, як купувати новий."
        return {"severity": "Середньо", "recommendation": rec}

    elif code == "P0420":
        rec = "Ефективність каталітичної системи нижче порогового значення. Перевірте стан каталізатора та лямбда-зонда."
        if mileage and mileage > 200000:
            rec += " Враховуючи значний пробіг (>200 тис. км), каталізатор найімовірніше фізично зруйнувався або забився і потребує видалення чи заміни."
        return {"severity": "Низько", "recommendation": rec}

    elif code == "P0113":
        rec = "Високий показник датчика температури повітря на впуску (IAT). Перевірте підключення датчика."
        if mileage and mileage > 100000:
            rec += " Від часу та вібрацій могла пересохнути ізоляція проводки або окислитися фішка контакту. Зверніть на це увагу."
        return {"severity": "Низько", "recommendation": rec}

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
        ai_result = await analyze_dtc_with_ai(request.dtc_code, request.mileage_km)

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
