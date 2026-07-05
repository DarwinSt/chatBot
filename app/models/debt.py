from datetime import date
from decimal import Decimal

from sqlalchemy import Date, Enum, ForeignKey, Numeric, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.models.base import TimestampMixin
from app.models.enums import DebtDirection, DebtStatus


class Debt(Base, TimestampMixin):
    __tablename__ = "debts"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(150), nullable=False)
    direction: Mapped[DebtDirection] = mapped_column(
        Enum(DebtDirection, name="debt_direction"), nullable=False
    )
    total_amount: Mapped[Decimal] = mapped_column(Numeric(19, 2), nullable=False)
    pending_amount: Mapped[Decimal] = mapped_column(Numeric(19, 2), nullable=False)
    start_date: Mapped[date] = mapped_column(Date, nullable=False)
    due_date: Mapped[date | None] = mapped_column(Date)
    counterparty: Mapped[str | None] = mapped_column(String(150))
    notes: Mapped[str | None] = mapped_column(String(500))
    status: Mapped[DebtStatus] = mapped_column(
        Enum(DebtStatus, name="debt_status"), nullable=False, default=DebtStatus.ACTIVA
    )
    category_id: Mapped[int | None] = mapped_column(ForeignKey("categories.id"))

    category = relationship("Category", back_populates="debts")
    payments = relationship("DebtPayment", back_populates="debt", order_by="DebtPayment.payment_date")
