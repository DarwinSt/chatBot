from datetime import date
from decimal import Decimal

from sqlalchemy import Date, ForeignKey, Numeric, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.models.base import TimestampMixin


class Income(Base, TimestampMixin):
    __tablename__ = "incomes"

    id: Mapped[int] = mapped_column(primary_key=True)
    amount: Mapped[Decimal] = mapped_column(Numeric(19, 2), nullable=False)
    income_date: Mapped[date] = mapped_column(Date, nullable=False)
    origin: Mapped[str | None] = mapped_column(String(150))
    description: Mapped[str | None] = mapped_column(String(500))
    category_id: Mapped[int] = mapped_column(ForeignKey("categories.id"), nullable=False)
    account_id: Mapped[int] = mapped_column(ForeignKey("accounts.id"), nullable=False)

    category = relationship("Category", back_populates="incomes")
    account = relationship("Account", back_populates="incomes")
