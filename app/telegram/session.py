import json
from dataclasses import dataclass, field

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.telegram_session import TelegramSession


@dataclass
class FlowContext:
    flow: str | None = None
    step: str | None = None
    fields: dict[str, str] = field(default_factory=dict)


class SessionService:
    def __init__(self, db: Session):
        self.db = db

    def get_or_create(self, chat_id: int) -> TelegramSession:
        session = self.db.scalar(select(TelegramSession).where(TelegramSession.chat_id == chat_id))
        if not session:
            session = TelegramSession(chat_id=chat_id, state="IDLE")
            self.db.add(session)
            self.db.commit()
            self.db.refresh(session)
        return session

    def read_context(self, session: TelegramSession) -> FlowContext:
        if not session.context_data:
            return FlowContext()
        data = json.loads(session.context_data)
        return FlowContext(
            flow=data.get("flow"),
            step=data.get("step"),
            fields=data.get("fields", {}),
        )

    def save_context(self, session: TelegramSession, state: str, ctx: FlowContext, pending: str | None = None) -> None:
        session.state = state
        session.pending_command = pending
        session.context_data = json.dumps(
            {"flow": ctx.flow, "step": ctx.step, "fields": ctx.fields},
            ensure_ascii=False,
        )
        self.db.commit()

    def reset(self, session: TelegramSession) -> None:
        session.state = "IDLE"
        session.pending_command = None
        session.context_data = None
        self.db.commit()

    def has_active_flow(self, session: TelegramSession) -> bool:
        ctx = self.read_context(session)
        return bool(ctx.flow and ctx.step)
