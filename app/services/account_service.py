from datetime import date
from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.account import Account
from app.models.enums import AccountType
from app.utils.money import normalize


class BusinessError(Exception):
    pass


class NotFoundError(Exception):
    pass


class InsufficientBalanceError(BusinessError):
    pass


class AccountService:
    def __init__(self, db: Session):
        self.db = db

    def list_active(self) -> list[Account]:
        return list(self.db.scalars(select(Account).where(Account.active.is_(True)).order_by(Account.name)))

    def list_all(self) -> list[Account]:
        return list(self.db.scalars(select(Account).order_by(Account.name)))

    def get(self, account_id: int) -> Account:
        account = self.db.get(Account, account_id)
        if not account:
            raise NotFoundError("Cuenta no encontrada")
        return account

    def create(
        self,
        name: str,
        account_type: AccountType,
        initial_balance: Decimal,
        notes: str | None = None,
    ) -> Account:
        balance = normalize(initial_balance)
        account = Account(
            name=name.strip(),
            type=account_type,
            initial_balance=balance,
            current_balance=balance,
            notes=notes,
            active=True,
        )
        self.db.add(account)
        self.db.commit()
        self.db.refresh(account)
        return account

    def credit(self, account: Account, amount: Decimal) -> None:
        account.current_balance = normalize(account.current_balance + amount)

    def debit(self, account: Account, amount: Decimal) -> None:
        amount = normalize(amount)
        if account.current_balance < amount:
            raise InsufficientBalanceError("Saldo insuficiente en la cuenta")
        account.current_balance = normalize(account.current_balance - amount)
