# Архітектура
1. **Mobile (Android):** Kotlin, XML UI, Retrofit для HTTP-запитів, Coroutines для асинхронності.
2. **Backend (Server):** Python, FastAPI, Pydantic для валідації.
3. **AI-компонент:** Rule-based логіка всередині FastAPI (імітація LLM з `asyncio.sleep`).
**Потік даних:** UI -> Retrofit -> POST /api/analyze -> FastAPI -> AI Function -> JSON Response -> UI.