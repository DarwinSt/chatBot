from decimal import Decimal

from sqlalchemy import Boolean, Enum, Numeric, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.models.base import TimestampMixin
from app.models.enums import AccountType


class Account(Base, TimestampMixin):
    __tablename__ = "accounts"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    type: Mapped[AccountType] = mapped_column(Enum(AccountType, name="account_type"), nullable=False)
    initial_balance: Mapped[Decimal] = mapped_column(Numeric(19, 2), nullable=False, default=Decimal("0"))
    current_balance: Mapped[Decimal] = mapped_column(Numeric(19, 2), nullable=False, default=Decimal("0"))
    active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    notes: Mapped[str | None] = mapped_column(String(500))

    incomes = relationship("Income", back_populates="account")
    expenses = relationship("Expense", back_populates="account")
    debt_payments = relationship("DebtPayment", back_populates="account")
