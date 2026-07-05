from datetime import date
from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.models.debt import Debt
from app.models.debt_payment import DebtPayment
from app.models.enums import CategoryType, DebtDirection, DebtStatus
from app.services.account_service import AccountService, BusinessError, NotFoundError
from app.services.category_service import CategoryService
from app.utils.money import normalize


class DebtService:
    def __init__(self, db: Session):
        self.db = db
        self.accounts = AccountService(db)
        self.categories = CategoryService(db)

    def _sync_overdue(self, debt: Debt) -> Debt:
        if debt.status in (DebtStatus.PAGADA, DebtStatus.CANCELADA):
            return debt
        if (
            debt.due_date
            and debt.pending_amount > 0
            and date.today() > debt.due_date
            and debt.status == DebtStatus.ACTIVA
        ):
            debt.status = DebtStatus.VENCIDA
            self.db.commit()
            self.db.refresh(debt)
        return debt

    def create(
        self,
        name: str,
        direction: DebtDirection,
        total_amount: Decimal,
        pending_amount: Decimal | None = None,
        counterparty: str | None = None,
        due_date: date | None = None,
        notes: str | None = None,
        category_id: int | None = None,
    ) -> Debt:
        total = normalize(total_amount)
        pending = normalize(pending_amount if pending_amount is not None else total)
        if pending < 0 or pending > total:
            raise BusinessError("El saldo pendiente debe estar entre 0 y el total")

        if category_id:
            self.categories.get_active(category_id, CategoryType.DEUDA)

        debt = Debt(
            name=name.strip(),
            direction=direction,
            total_amount=total,
            pending_amount=pending,
            start_date=date.today(),
            due_date=due_date,
            counterparty=counterparty.strip() if counterparty else None,
            notes=notes,
            status=DebtStatus.ACTIVA,
            category_id=category_id,
        )
        self.db.add(debt)
        self.db.commit()
        self.db.refresh(debt)
        return self._sync_overdue(debt)

    def list_active(self, direction: DebtDirection | None = None) -> list[Debt]:
        stmt = (
            select(Debt)
            .where(
                Debt.pending_amount > 0,
                Debt.status.in_([DebtStatus.ACTIVA, DebtStatus.VENCIDA]),
            )
            .order_by(Debt.due_date.nulls_last(), Debt.name)
        )
        if direction:
            stmt = stmt.where(Debt.direction == direction)
        debts = list(self.db.scalars(stmt))
        return [self._sync_overdue(d) for d in debts]

    def get_with_payments(self, debt_id: int) -> Debt:
        debt = self.db.scalar(
            select(Debt).options(selectinload(Debt.payments)).where(Debt.id == debt_id)
        )
        if not debt:
            raise NotFoundError("Deuda no encontrada")
        return self._sync_overdue(debt)

    def get(self, debt_id: int) -> Debt:
        debt = self.db.get(Debt, debt_id)
        if not debt:
            raise NotFoundError("Deuda no encontrada")
        return self._sync_overdue(debt)

    def update(
        self,
        debt_id: int,
        *,
        name: str,
        counterparty: str | None,
        notes: str | None,
        due_date: date | None,
    ) -> Debt:
        debt = self.get(debt_id)
        if debt.status in (DebtStatus.PAGADA, DebtStatus.CANCELADA):
            raise BusinessError("No se puede editar una deuda en estado " + debt.status.value)

        clean_name = name.strip()
        if not clean_name:
            raise BusinessError("El nombre no puede estar vacío")

        debt.name = clean_name
        debt.counterparty = counterparty.strip() if counterparty and counterparty.strip() else None
        debt.notes = notes.strip() if notes and notes.strip() else None
        debt.due_date = due_date

        self.db.commit()
        self.db.refresh(debt)
        return self._sync_overdue(debt)

    def register_movement(
        self,
        debt_id: int,
        amount: Decimal,
        account_id: int | None = None,
        payment_date: date | None = None,
        notes: str | None = None,
    ) -> DebtPayment:
        debt = self.get_with_payments(debt_id)
        if debt.status in (DebtStatus.PAGADA, DebtStatus.CANCELADA):
            raise BusinessError(f"No se puede mover una deuda en estado {debt.status.value}")

        move = normalize(amount)
        if move > debt.pending_amount:
            raise BusinessError("El monto supera el saldo pendiente")

        if account_id is not None:
            account = self.accounts.get(account_id)
            if not account.active:
                raise BusinessError("La cuenta no está activa")
            if debt.direction == DebtDirection.POR_PAGAR:
                self.accounts.debit(account, move)
            else:
                self.accounts.credit(account, move)

        debt.pending_amount = normalize(debt.pending_amount - move)
        if debt.pending_amount == 0:
            debt.status = DebtStatus.PAGADA

        payment = DebtPayment(
            amount=move,
            payment_date=payment_date or date.today(),
            notes=notes,
            account_id=account_id,
            debt_id=debt.id,
        )
        self.db.add(payment)
        self.db.commit()
        self.db.refresh(payment)
        return payment

    def summary(self) -> dict[str, Decimal]:
        payables = self.list_active(DebtDirection.POR_PAGAR)
        receivables = self.list_active(DebtDirection.POR_COBRAR)
        total_pay = sum((d.pending_amount for d in payables), Decimal("0"))
        total_receive = sum((d.pending_amount for d in receivables), Decimal("0"))
        return {
            "total_por_pagar": normalize(total_pay),
            "total_por_cobrar": normalize(total_receive),
            "neto": normalize(total_receive - total_pay),
        }

    @staticmethod
    def paid_amount(debt: Debt) -> Decimal:
        return normalize(debt.total_amount - debt.pending_amount)
