from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.category import Category
from app.models.enums import CategoryType
from app.services.account_service import NotFoundError


class CategoryService:
    def __init__(self, db: Session):
        self.db = db

    def list_active_by_type(self, category_type: CategoryType) -> list[Category]:
        return list(
            self.db.scalars(
                select(Category)
                .where(Category.type == category_type, Category.active.is_(True))
                .order_by(Category.name)
            )
        )

    def get_active(self, category_id: int, category_type: CategoryType) -> Category:
        category = self.db.scalar(
            select(Category).where(
                Category.id == category_id,
                Category.type == category_type,
                Category.active.is_(True),
            )
        )
        if not category:
            raise NotFoundError("Categoría no encontrada")
        return category

    def create(self, name: str, category_type: CategoryType) -> Category:
        category = Category(name=name.strip(), type=category_type, active=True)
        self.db.add(category)
        self.db.commit()
        self.db.refresh(category)
        return category

    def ensure_defaults(self) -> None:
        defaults = [
            ("Salario", CategoryType.INGRESO),
            ("Otros ingresos", CategoryType.INGRESO),
            ("General", CategoryType.GASTO),
            ("Alimentación", CategoryType.GASTO),
            ("Préstamo", CategoryType.DEUDA),
            ("Favor", CategoryType.DEUDA),
        ]
        for name, ctype in defaults:
            exists = self.db.scalar(
                select(Category).where(Category.name == name, Category.type == ctype)
            )
            if not exists:
                self.db.add(Category(name=name, type=ctype, active=True))
        self.db.commit()
