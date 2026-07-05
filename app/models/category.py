from sqlalchemy import Boolean, Enum, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.models.base import TimestampMixin
from app.models.enums import CategoryType


class Category(Base, TimestampMixin):
    __tablename__ = "categories"
    __table_args__ = (UniqueConstraint("name", "type", name="uk_categories_name_type"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    type: Mapped[CategoryType] = mapped_column(Enum(CategoryType, name="category_type"), nullable=False)
    active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)

    debts = relationship("Debt", back_populates="category")
    incomes = relationship("Income", back_populates="category")
    expenses = relationship("Expense", back_populates="category")
