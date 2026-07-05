from datetime import date

from sqlalchemy.orm import Session

from app.models.enums import AccountType, CategoryType, DebtDirection
from app.services.account_service import AccountService, BusinessError, InsufficientBalanceError, NotFoundError
from app.services.category_service import CategoryService
from app.services.debt_service import DebtService
from app.services.expense_service import ExpenseService
from app.services.income_service import IncomeService
from app.telegram.client import TelegramClient
from app.telegram.quick_replies import QuickReplies
from app.telegram.session import FlowContext, SessionService
from app.utils.money import parse_amount


class ConversationService:
    def __init__(self, db: Session, client: TelegramClient):
        self.db = db
        self.client = client
        self.sessions = SessionService(db)
        self.quick = QuickReplies(db)
        self.accounts = AccountService(db)
        self.categories = CategoryService(db)
        self.debts = DebtService(db)
        self.incomes = IncomeService(db)
        self.expenses = ExpenseService(db)

    def begin(self, chat_id: int, flow: str, first_step: str, message: str) -> None:
        session = self.sessions.get_or_create(chat_id)
        ctx = FlowContext(flow=flow, step=first_step)
        self.sessions.save_context(session, "CONVERSATION", ctx, flow)
        self.client.send_message(chat_id, message)

    def handle(self, chat_id: int, text: str) -> None:
        session = self.sessions.get_or_create(chat_id)
        ctx = self.sessions.read_context(session)
        if not ctx.flow or not ctx.step:
            self.sessions.reset(session)
            self.client.send_message(chat_id, "No hay flujo activo. Usa /menu.")
            return
        try:
            handler = getattr(self, f"_flow_{ctx.flow}", None)
            if not handler:
                raise BusinessError("Flujo desconocido")
            handler(chat_id, session, ctx, text.strip())
        except (BusinessError, NotFoundError, InsufficientBalanceError) as exc:
            self.client.send_message(chat_id, f"No se pudo completar: {exc}")
        except Exception:
            self.client.send_message(chat_id, "Error al procesar. Revisa el formato o usa /cancelar.")

  # --- account create ---
    def _flow_account_create(self, chat_id, session, ctx, text):
        if ctx.step == "name":
            if not text:
                self.client.send_message(chat_id, "El nombre no puede estar vacío.")
                return
            ctx.fields["name"] = text
            ctx.step = "type"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(
                chat_id,
                "Tipo de cuenta:\n1) CORRIENTE\n2) AHORROS\n3) EFECTIVO\n4) BILLETERA_DIGITAL",
            )
        elif ctx.step == "type":
            mapping = {"1": AccountType.CORRIENTE, "2": AccountType.AHORROS, "3": AccountType.EFECTIVO, "4": AccountType.BILLETERA_DIGITAL}
            acc_type = mapping.get(text) or AccountType.__members__.get(text.upper())
            if not acc_type:
                self.client.send_message(chat_id, "Tipo inválido. Responde 1-4.")
                return
            ctx.fields["type"] = acc_type.value
            ctx.step = "balance"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Saldo inicial (ej: 1500.00):")
        elif ctx.step == "balance":
            amount = parse_amount(text)
            if amount is None:
                self.client.send_message(chat_id, "Monto inválido.")
                return
            account = self.accounts.create(
                ctx.fields["name"],
                AccountType(ctx.fields["type"]),
                amount,
            )
            self.sessions.reset(session)
            self.client.send_message(chat_id, f"Cuenta creada: {account.name} (id {account.id})")

  # --- income ---
    def _flow_income(self, chat_id, session, ctx, text):
        if ctx.step == "amount":
            amount = parse_amount(text)
            if amount is None:
                self.client.send_message(chat_id, "Monto inválido.")
                return
            ctx.fields["amount"] = str(amount)
            ctx.step = "category"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            cats = self.categories.list_active_by_type(CategoryType.INGRESO)
            self.client.send_message(chat_id, "Categoría (número):\n" + self._format_categories(cats))
        elif ctx.step == "category":
            cats = self.categories.list_active_by_type(CategoryType.INGRESO)
            idx = self._parse_index(text, len(cats))
            if idx is None:
                self.client.send_message(chat_id, "Número inválido.")
                return
            ctx.fields["category_id"] = str(cats[idx].id)
            ctx.step = "account"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            accs = self.accounts.list_active()
            self.client.send_message(chat_id, "Cuenta destino (número):\n" + self.quick.format_accounts(accs))
        elif ctx.step == "account":
            accs = self.accounts.list_active()
            idx = self._parse_index(text, len(accs))
            if idx is None:
                self.client.send_message(chat_id, "Número inválido.")
                return
            ctx.fields["account_id"] = str(accs[idx].id)
            ctx.step = "origin"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Origen del ingreso (texto) o '-' para omitir:")
        elif ctx.step == "origin":
            ctx.fields["origin"] = "" if text == "-" else text
            ctx.step = "description"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Descripción opcional (o '-'):")
        elif ctx.step == "description":
            desc = None if text == "-" else text
            self.incomes.create(
                amount=parse_amount(ctx.fields["amount"]),
                account_id=int(ctx.fields["account_id"]),
                category_id=int(ctx.fields["category_id"]),
                origin=ctx.fields.get("origin") or None,
                description=desc,
            )
            self.sessions.reset(session)
            self.client.send_message(chat_id, "Ingreso registrado.")

  # --- expense ---
    def _flow_expense(self, chat_id, session, ctx, text):
        if ctx.step == "amount":
            amount = parse_amount(text)
            if amount is None:
                self.client.send_message(chat_id, "Monto inválido.")
                return
            ctx.fields["amount"] = str(amount)
            ctx.step = "category"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            cats = self.categories.list_active_by_type(CategoryType.GASTO)
            self.client.send_message(chat_id, "Categoría (número):\n" + self._format_categories(cats))
        elif ctx.step == "category":
            cats = self.categories.list_active_by_type(CategoryType.GASTO)
            idx = self._parse_index(text, len(cats))
            if idx is None:
                self.client.send_message(chat_id, "Número inválido.")
                return
            ctx.fields["category_id"] = str(cats[idx].id)
            ctx.step = "account"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            accs = self.accounts.list_active()
            self.client.send_message(chat_id, "Cuenta origen (número):\n" + self.quick.format_accounts(accs))
        elif ctx.step == "account":
            accs = self.accounts.list_active()
            idx = self._parse_index(text, len(accs))
            if idx is None:
                self.client.send_message(chat_id, "Número inválido.")
                return
            ctx.fields["account_id"] = str(accs[idx].id)
            ctx.step = "description"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Descripción (o '-'):")
        elif ctx.step == "description":
            desc = None if text == "-" else text
            self.expenses.create(
                amount=parse_amount(ctx.fields["amount"]),
                account_id=int(ctx.fields["account_id"]),
                category_id=int(ctx.fields["category_id"]),
                description=desc,
            )
            self.sessions.reset(session)
            self.client.send_message(chat_id, "Gasto registrado.")

  # --- debt register ---
    def _flow_debt_register(self, chat_id, session, ctx, text):
        direction = DebtDirection(ctx.fields.get("direction", DebtDirection.POR_PAGAR.value))
        party_label = "Acreedor" if direction == DebtDirection.POR_PAGAR else "Deudor"
        if ctx.step == "name":
            ctx.fields["name"] = text
            ctx.step = "total"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Monto total:")
        elif ctx.step == "total":
            amount = parse_amount(text)
            if amount is None:
                self.client.send_message(chat_id, "Monto inválido.")
                return
            ctx.fields["total"] = str(amount)
            ctx.step = "pending"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Saldo pendiente (o '-' para igual al total):")
        elif ctx.step == "pending":
            total = parse_amount(ctx.fields["total"])
            pending = total if text == "-" else parse_amount(text)
            if pending is None:
                self.client.send_message(chat_id, "Monto pendiente inválido.")
                return
            ctx.fields["pending"] = str(pending)
            ctx.step = "counterparty"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, f"{party_label} (texto) o '-' para omitir:")
        elif ctx.step == "counterparty":
            ctx.fields["counterparty"] = "" if text == "-" else text
            ctx.step = "due"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Fecha de vencimiento (YYYY-MM-DD) o '-':")
        elif ctx.step == "due":
            due = None if text == "-" else self._parse_date(text)
            if text != "-" and due is None:
                self.client.send_message(chat_id, "Fecha inválida.")
                return
            ctx.fields["due"] = due.isoformat() if due else ""
            ctx.step = "notes"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Notas (motivo/descripción) o '-':")
        elif ctx.step == "notes":
            notes = None if text == "-" else text
            due = date.fromisoformat(ctx.fields["due"]) if ctx.fields.get("due") else None
            self.debts.create(
                name=ctx.fields["name"],
                direction=direction,
                total_amount=parse_amount(ctx.fields["total"]),
                pending_amount=parse_amount(ctx.fields["pending"]),
                counterparty=ctx.fields.get("counterparty") or None,
                due_date=due,
                notes=notes,
            )
            self.sessions.reset(session)
            label = "Deuda registrada (debes)." if direction == DebtDirection.POR_PAGAR else "Cuenta por cobrar registrada."
            self.client.send_message(chat_id, label)

  # --- debt movement (pay or collect) ---
    def _flow_debt_movement(self, chat_id, session, ctx, text):
        direction = DebtDirection(ctx.fields["direction"])
        debts = self.debts.list_active(direction)
        if ctx.step == "debt":
            idx = self._parse_index(text, len(debts))
            if idx is None:
                self.client.send_message(chat_id, "Número inválido.")
                return
            ctx.fields["debt_id"] = str(debts[idx].id)
            ctx.step = "amount"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            verb = "abono" if direction == DebtDirection.POR_PAGAR else "cobro"
            self.client.send_message(chat_id, f"Monto del {verb}:")
        elif ctx.step == "amount":
            amount = parse_amount(text)
            if amount is None:
                self.client.send_message(chat_id, "Monto inválido.")
                return
            ctx.fields["amount"] = str(amount)
            ctx.step = "account"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            accs = self.accounts.list_active()
            label = "Cuenta origen" if direction == DebtDirection.POR_PAGAR else "Cuenta destino"
            self.client.send_message(chat_id, f"{label} (número):\n" + self.quick.format_accounts(accs))
        elif ctx.step == "account":
            accs = self.accounts.list_active()
            idx = self._parse_index(text, len(accs))
            if idx is None:
                self.client.send_message(chat_id, "Número inválido.")
                return
            self.debts.register_movement(
                debt_id=int(ctx.fields["debt_id"]),
                amount=parse_amount(ctx.fields["amount"]),
                account_id=accs[idx].id,
            )
            self.sessions.reset(session)
            msg = "Abono registrado." if direction == DebtDirection.POR_PAGAR else "Cobro registrado."
            self.client.send_message(chat_id, msg)

  # --- debt detail ---
    def _flow_debt_detail(self, chat_id, session, ctx, text):
        stmt_debts = self.debts.list_active(DebtDirection.POR_PAGAR) + self.debts.list_active(
            DebtDirection.POR_COBRAR
        )
        idx = self._parse_index(text, len(stmt_debts))
        if idx is None:
            self.client.send_message(chat_id, "Número inválido.")
            return
        debt = self.debts.get_with_payments(stmt_debts[idx].id)
        self.sessions.reset(session)
        self.client.send_message(chat_id, self.quick.debt_detail(debt))

    @staticmethod
    def _format_categories(categories) -> str:
        return "\n".join(f"{i}) {c.name}" for i, c in enumerate(categories, start=1))

    @staticmethod
    def _parse_index(text: str, size: int) -> int | None:
        if not text.isdigit():
            return None
        idx = int(text) - 1
        if idx < 0 or idx >= size:
            return None
        return idx

    @staticmethod
    def _parse_date(text: str) -> date | None:
        try:
            return date.fromisoformat(text.strip())
        except ValueError:
            return None
