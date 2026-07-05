from datetime import date
from decimal import Decimal

from sqlalchemy.orm import Session

from app.services.account_service import AccountService, NotFoundError
from app.services.category_service import CategoryService
from app.utils.money import normalize


class IncomeService:
    def __init__(self, db: Session):
        self.db = db
        self.accounts = AccountService(db)
        self.categories = CategoryService(db)

    def create(
        self,
        amount: Decimal,
        account_id: int,
        category_id: int,
        income_date: date | None = None,
        origin: str | None = None,
        description: str | None = None,
    ):
        from app.models.enums import CategoryType
        from app.models.income import Income

        move = normalize(amount)
        account = self.accounts.get(account_id)
        if not account.active:
            raise NotFoundError("Cuenta no activa")
        self.categories.get_active(category_id, CategoryType.INGRESO)

        income = Income(
            amount=move,
            income_date=income_date or date.today(),
            origin=origin,
            description=description,
            category_id=category_id,
            account_id=account_id,
        )
        self.accounts.credit(account, move)
        self.db.add(income)
        self.db.commit()
        self.db.refresh(income)
        return income
