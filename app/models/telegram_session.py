from sqlalchemy import BigInteger, Boolean, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base
from app.models.base import TimestampMixin


class TelegramSession(Base, TimestampMixin):
    __tablename__ = "telegram_sessions"

    id: Mapped[int] = mapped_column(primary_key=True)
    chat_id: Mapped[int] = mapped_column(BigInteger, unique=True, nullable=False)
    state: Mapped[str] = mapped_column(String(50), nullable=False, default="IDLE")
    pending_command: Mapped[str | None] = mapped_column(String(100))
    context_data: Mapped[str | None] = mapped_column(Text)
    active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
