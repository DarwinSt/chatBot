from datetime import date

from sqlalchemy.orm import Session

from app.models.account import Account
from app.models.debt import Debt
from app.models.enums import DebtDirection
from app.services.account_service import AccountService
from app.services.debt_service import DebtService
from app.utils.money import fmt_money


class QuickReplies:
    def __init__(self, db: Session):
        self.db = db
        self.accounts = AccountService(db)
        self.debts = DebtService(db)

    def format_accounts(self, accounts: list[Account]) -> str:
        if not accounts:
            return "(Sin cuentas activas)"
        lines = []
        for i, acc in enumerate(accounts, start=1):
            lines.append(f"{i}) {acc.name} — {fmt_money(acc.current_balance)}")
        return "\n".join(lines)

    def format_debts(self, debts: list[Debt], direction: DebtDirection) -> str:
        if not debts:
            label = "debo" if direction == DebtDirection.POR_PAGAR else "me deben"
            return f"(No hay deudas activas que {label})"
        lines = []
        for i, debt in enumerate(debts, start=1):
            party = debt.counterparty or "(sin contraparte)"
            paid = self.debts.paid_amount(debt)
            pct = int((paid / debt.total_amount) * 100) if debt.total_amount else 0
            due = debt.due_date.isoformat() if debt.due_date else "sin vencimiento"
            lines.append(
                f"{i}) {debt.name}\n"
                f"   Pendiente: {fmt_money(debt.pending_amount)} | Pagado: {fmt_money(paid)} ({pct}%)\n"
                f"   Contraparte: {party} | Vence: {due}"
            )
        return "\n\n".join(lines)

    def debt_detail(self, debt: Debt) -> str:
        paid = self.debts.paid_amount(debt)
        pct = int((paid / debt.total_amount) * 100) if debt.total_amount else 0
        direction = "Debo" if debt.direction == DebtDirection.POR_PAGAR else "Me deben"
        party_label = "Acreedor" if debt.direction == DebtDirection.POR_PAGAR else "Deudor"
        lines = [
            f"*{debt.name}* ({direction})",
            f"- Total: {fmt_money(debt.total_amount)}",
            f"- Pagado/cobrado: {fmt_money(paid)} ({pct}%)",
            f"- Pendiente: {fmt_money(debt.pending_amount)}",
            f"- {party_label}: {debt.counterparty or '(sin registrar)'}",
            f"- Estado: {debt.status.value}",
            f"- Vence: {debt.due_date.isoformat() if debt.due_date else 'sin fecha'}",
        ]
        if debt.notes:
            lines.append(f"- Notas: {debt.notes}")
        if debt.payments:
            lines.append("\nMovimientos:")
            for idx, payment in enumerate(debt.payments, start=1):
                lines.append(
                    f"{idx}) {payment.payment_date} — {fmt_money(payment.amount)} "
                    f"(cuenta #{payment.account_id})"
                )
        else:
            lines.append("\n(Sin movimientos registrados)")
        return "\n".join(lines)

    def debt_summary(self) -> str:
        summary = self.debts.summary()
        return (
            "Resumen de deudas:\n"
            f"- Total que debo: {fmt_money(summary['total_por_pagar'])}\n"
            f"- Total que me deben: {fmt_money(summary['total_por_cobrar'])}\n"
            f"- Posición neta: {fmt_money(summary['neto'])}"
        )

    def balance(self) -> str:
        accounts = self.accounts.list_active()
        lines = ["Saldos por cuenta:"]
        for acc in accounts:
            lines.append(f"- {acc.name}: {fmt_money(acc.current_balance)}")
        lines.append("")
        lines.append(self.debt_summary())
        return "\n".join(lines)
