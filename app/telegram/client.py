import logging
from typing import Any

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)


class TelegramClient:
    def __init__(self) -> None:
        settings = get_settings()
        self._token = settings.telegram_bot_token
        self._base = f"https://api.telegram.org/bot{self._token}"

    def send_message(self, chat_id: int | str, text: str, reply_markup: dict | None = None) -> None:
        if not self._token:
            logger.warning("TELEGRAM_BOT_TOKEN vacío; no se envía mensaje")
            return
        payload: dict[str, Any] = {"chat_id": chat_id, "text": text}
        if reply_markup:
            payload["reply_markup"] = reply_markup
        with httpx.Client(timeout=30) as client:
            response = client.post(f"{self._base}/sendMessage", json=payload)
            response.raise_for_status()

    def answer_callback_query(self, callback_query_id: str, text: str | None = None) -> None:
        if not self._token or not callback_query_id:
            return
        payload: dict[str, Any] = {"callback_query_id": callback_query_id}
        if text:
            payload["text"] = text
        try:
            with httpx.Client(timeout=30) as client:
                response = client.post(f"{self._base}/answerCallbackQuery", json=payload)
                response.raise_for_status()
        except Exception:
            logger.exception("No se pudo responder callback_query %s", callback_query_id)

    def register_webhook(self, url: str, secret: str | None = None) -> None:
        if not self._token:
            return
        payload: dict[str, Any] = {
            "url": url,
            "allowed_updates": ["message", "callback_query"],
        }
        if secret:
            payload["secret_token"] = secret
        with httpx.Client(timeout=30) as client:
            response = client.post(f"{self._base}/setWebhook", json=payload)
            response.raise_for_status()
            logger.info("Webhook registrado: %s", response.json())
