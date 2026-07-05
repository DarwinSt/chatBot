import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from sqlalchemy.orm import Session

from app.config import get_settings
from app.database import get_db
from app.services.category_service import CategoryService
from app.telegram.client import TelegramClient
from app.telegram.handler import UpdateHandler

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    db = next(get_db())
    try:
        CategoryService(db).ensure_defaults()
    finally:
        db.close()

    base = (settings.app_base_url or "").rstrip("/")
    if base.startswith("https://") and settings.telegram_bot_token:
        webhook_url = f"{base}/api/telegram/webhook"
        try:
            TelegramClient().register_webhook(webhook_url, settings.telegram_webhook_secret or None)
        except Exception:
            logger.exception("No se pudo registrar el webhook de Telegram")
    elif base:
        logger.warning("APP_BASE_URL debe ser HTTPS para el webhook de Telegram")
    yield


app = FastAPI(title="FinanceBot", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/telegram/webhook")
async def telegram_webhook(
    request: Request,
    db: Session = Depends(get_db),
    x_telegram_bot_api_secret_token: str | None = Header(default=None),
):
    settings = get_settings()
    if settings.telegram_webhook_secret:
        if x_telegram_bot_api_secret_token != settings.telegram_webhook_secret:
            raise HTTPException(status_code=403, detail="Invalid webhook secret")

    update = await request.json()
    UpdateHandler(db).process_update(update)
    return {"ok": True}
