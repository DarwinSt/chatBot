from datetime import date
from decimal import Decimal

from sqlalchemy import Date, ForeignKey, Numeric, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.models.base import TimestampMixin


class DebtPayment(Base, TimestampMixin):
    __tablename__ = "debt_payments"

    id: Mapped[int] = mapped_column(primary_key=True)
    amount: Mapped[Decimal] = mapped_column(Numeric(19, 2), nullable=False)
    payment_date: Mapped[date] = mapped_column(Date, nullable=False)
    notes: Mapped[str | None] = mapped_column(String(500))
    account_id: Mapped[int | None] = mapped_column(ForeignKey("accounts.id"))
    debt_id: Mapped[int] = mapped_column(ForeignKey("debts.id"), nullable=False)

    account = relationship("Account", back_populates="debt_payments")
    debt = relationship("Debt", back_populates="payments")
