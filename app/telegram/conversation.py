from datetime import date

from sqlalchemy.orm import Session

from app.models.enums import AccountType, CategoryType, DebtDirection
from app.services.account_service import AccountService, BusinessError, InsufficientBalanceError, NotFoundError
from app.services.category_service import CategoryService
from app.services.debt_service import DebtService
from app.services.expense_service import ExpenseService
from app.services.income_service import IncomeService
from app.telegram.client import TelegramClient
from app.telegram.keyboards import confirm_keyboard
from app.telegram.quick_replies import QuickReplies
from app.telegram.session import FlowContext, SessionService
from app.utils.money import fmt_money, parse_amount


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
        if ctx.step == "confirm":
            normalized = text.strip().upper()
            if normalized in ("SI", "S", "1"):
                self.handle_confirm(chat_id, "ok")
            elif normalized in ("NO", "N", "0"):
                self.handle_confirm(chat_id, "cancel")
            else:
                self.client.send_message(chat_id, "Usa ✅ Confirmar o ❌ Cancelar (o responde SI/NO).")
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

    def handle_confirm(self, chat_id: int, action: str) -> None:
        session = self.sessions.get_or_create(chat_id)
        ctx = self.sessions.read_context(session)
        if ctx.step != "confirm" or not ctx.flow:
            self.client.send_message(chat_id, "No hay confirmación pendiente.")
            return
        if action == "cancel":
            self.sessions.reset(session)
            self.client.send_message(chat_id, "Registro cancelado.")
            return
        try:
            message = self._commit_flow(ctx)
            self.sessions.reset(session)
            self.client.send_message(chat_id, message)
        except (BusinessError, NotFoundError, InsufficientBalanceError) as exc:
            self.client.send_message(chat_id, f"No se pudo completar: {exc}")
        except Exception:
            self.client.send_message(chat_id, "Error al registrar. Usa /cancelar e intenta de nuevo.")

    def _ask_confirm(self, chat_id: int, session, ctx: FlowContext, summary: str) -> None:
        ctx.step = "confirm"
        self.sessions.save_context(session, "CONVERSATION", ctx)
        self.client.send_message(chat_id, summary.strip(), confirm_keyboard())

    def _commit_flow(self, ctx: FlowContext) -> str:
        flow = ctx.flow
        if flow == "account_create":
            account = self.accounts.create(
                ctx.fields["name"],
                AccountType(ctx.fields["type"]),
                parse_amount(ctx.fields["balance"]),
            )
            return f"Cuenta creada: {account.name} (id {account.id})"
        if flow == "income":
            self.incomes.create(
                amount=parse_amount(ctx.fields["amount"]),
                account_id=int(ctx.fields["account_id"]),
                category_id=int(ctx.fields["category_id"]),
                origin=ctx.fields.get("origin") or None,
                description=ctx.fields.get("description") or None,
            )
            return "Ingreso registrado."
        if flow == "expense":
            self.expenses.create(
                amount=parse_amount(ctx.fields["amount"]),
                account_id=int(ctx.fields["account_id"]),
                category_id=int(ctx.fields["category_id"]),
                description=ctx.fields.get("description") or None,
            )
            return "Gasto registrado."
        if flow == "debt_register":
            direction = DebtDirection(ctx.fields["direction"])
            due = date.fromisoformat(ctx.fields["due"]) if ctx.fields.get("due") else None
            self.debts.create(
                name=ctx.fields["name"],
                direction=direction,
                total_amount=parse_amount(ctx.fields["total"]),
                pending_amount=parse_amount(ctx.fields["pending"]),
                counterparty=ctx.fields.get("counterparty") or None,
                due_date=due,
                notes=ctx.fields.get("notes") or None,
            )
            if direction == DebtDirection.POR_PAGAR:
                return "Deuda registrada (debes)."
            return "Cuenta por cobrar registrada."
        if flow == "debt_movement":
            direction = DebtDirection(ctx.fields["direction"])
            account_id = int(ctx.fields["account_id"]) if ctx.fields.get("account_id") else None
            self.debts.register_movement(
                debt_id=int(ctx.fields["debt_id"]),
                amount=parse_amount(ctx.fields["amount"]),
                account_id=account_id,
                payment_date=date.fromisoformat(ctx.fields["payment_date"]),
            )
            msg = "Abono registrado." if direction == DebtDirection.POR_PAGAR else "Cobro registrado."
            if account_id is None:
                msg += " (sin movimiento en cuenta)"
            return msg + f"\nFecha: {ctx.fields['payment_date']}"
        if flow == "debt_add":
            updated = self.debts.add_amount(int(ctx.fields["debt_id"]), parse_amount(ctx.fields["amount"]))
            return (
                "Monto agregado a la deuda.\n"
                f"- Total: {fmt_money(updated.total_amount)}\n"
                f"- Pendiente: {fmt_money(updated.pending_amount)}"
            )
        if flow == "debt_edit":
            due = date.fromisoformat(ctx.fields["due"]) if ctx.fields.get("due") else None
            updated = self.debts.update(
                int(ctx.fields["debt_id"]),
                name=ctx.fields["name"],
                counterparty=ctx.fields.get("counterparty") or None,
                notes=ctx.fields.get("notes") or None,
                due_date=due,
            )
            party_label = "Acreedor" if updated.direction == DebtDirection.POR_PAGAR else "Deudor"
            return (
                "Deuda actualizada:\n"
                f"- Referencia: {updated.name}\n"
                f"- {party_label}: {updated.counterparty or '(sin registrar)'}\n"
                f"- Vence: {updated.due_date.isoformat() if updated.due_date else 'sin fecha'}"
            )
        raise BusinessError("Flujo desconocido")

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
            ctx.fields["balance"] = str(amount)
            summary = (
                "Confirmar creación de cuenta:\n"
                f"- Nombre: {ctx.fields['name']}\n"
                f"- Tipo: {ctx.fields['type']}\n"
                f"- Saldo inicial: {fmt_money(amount)}"
            )
            self._ask_confirm(chat_id, session, ctx, summary)

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
            ctx.fields["category_name"] = cats[idx].name
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
            ctx.fields["account_name"] = accs[idx].name
            ctx.step = "origin"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Origen del ingreso (texto) o '-' para omitir:")
        elif ctx.step == "origin":
            ctx.fields["origin"] = "" if text == "-" else text
            ctx.step = "description"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Descripción opcional (o '-'):")
        elif ctx.step == "description":
            ctx.fields["description"] = "" if text == "-" else text
            summary = (
                "Confirmar ingreso:\n"
                f"- Monto: {fmt_money(parse_amount(ctx.fields['amount']))}\n"
                f"- Categoría: {ctx.fields.get('category_name', '-')}\n"
                f"- Cuenta: {ctx.fields.get('account_name', '-')}\n"
                f"- Origen: {ctx.fields.get('origin') or '(sin origen)'}\n"
                f"- Descripción: {ctx.fields.get('description') or '(sin descripción)'}"
            )
            self._ask_confirm(chat_id, session, ctx, summary)

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
            ctx.fields["category_name"] = cats[idx].name
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
            ctx.fields["account_name"] = accs[idx].name
            ctx.step = "description"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, "Descripción (o '-'):")
        elif ctx.step == "description":
            ctx.fields["description"] = "" if text == "-" else text
            summary = (
                "Confirmar gasto:\n"
                f"- Monto: {fmt_money(parse_amount(ctx.fields['amount']))}\n"
                f"- Categoría: {ctx.fields.get('category_name', '-')}\n"
                f"- Cuenta: {ctx.fields.get('account_name', '-')}\n"
                f"- Descripción: {ctx.fields.get('description') or '(sin descripción)'}"
            )
            self._ask_confirm(chat_id, session, ctx, summary)

    # --- debt register ---
    def _flow_debt_register(self, chat_id, session, ctx, text):
        direction = DebtDirection(ctx.fields.get("direction", DebtDirection.POR_PAGAR.value))
        party_label = "Acreedor" if direction == DebtDirection.POR_PAGAR else "Deudor"
        tipo = "Deuda (debo)" if direction == DebtDirection.POR_PAGAR else "Me deben"
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
            ctx.fields["notes"] = "" if text == "-" else text
            summary = (
                f"Confirmar registro — {tipo}:\n"
                f"- Referencia: {ctx.fields['name']}\n"
                f"- Total: {fmt_money(parse_amount(ctx.fields['total']))}\n"
                f"- Pendiente: {fmt_money(parse_amount(ctx.fields['pending']))}\n"
                f"- {party_label}: {ctx.fields.get('counterparty') or '(sin registrar)'}\n"
                f"- Vence: {ctx.fields.get('due') or 'sin fecha'}\n"
                f"- Notas: {ctx.fields.get('notes') or '(sin notas)'}"
            )
            self._ask_confirm(chat_id, session, ctx, summary)

    # --- debt movement ---
    def _flow_debt_movement(self, chat_id, session, ctx, text):
        direction = DebtDirection(ctx.fields["direction"])
        debts = self.debts.list_active(direction)
        verb = "abono" if direction == DebtDirection.POR_PAGAR else "cobro"
        if ctx.step == "debt":
            idx = self._parse_index(text, len(debts))
            if idx is None:
                self.client.send_message(chat_id, "Número inválido.")
                return
            selected = debts[idx]
            ctx.fields["debt_id"] = str(selected.id)
            ctx.fields["debt_name"] = selected.name
            ctx.step = "amount"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, f"Monto del {verb}:")
        elif ctx.step == "amount":
            amount = parse_amount(text)
            if amount is None:
                self.client.send_message(chat_id, "Monto inválido.")
                return
            ctx.fields["amount"] = str(amount)
            ctx.step = "payment_date"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(chat_id, f"Fecha del {verb} (YYYY-MM-DD) o '-' para hoy:")
        elif ctx.step == "payment_date":
            if text == "-":
                payment_date = date.today()
            else:
                payment_date = self._parse_date(text)
                if payment_date is None:
                    self.client.send_message(chat_id, "Fecha inválida. Usa YYYY-MM-DD o '-'.")
                    return
            ctx.fields["payment_date"] = payment_date.isoformat()
            ctx.step = "account"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            label = "Cuenta origen" if direction == DebtDirection.POR_PAGAR else "Cuenta destino"
            self.client.send_message(
                chat_id,
                f"{label} (número) o '-' sin cuenta:\n" + self.quick.format_accounts(self.accounts.list_active()),
            )
        elif ctx.step == "account":
            if text == "-":
                ctx.fields["account_id"] = ""
                ctx.fields["account_name"] = "(sin cuenta)"
            else:
                accs = self.accounts.list_active()
                if not accs:
                    self.client.send_message(chat_id, "No hay cuentas. Usa '-' para registrar sin cuenta.")
                    return
                idx = self._parse_index(text, len(accs))
                if idx is None:
                    self.client.send_message(chat_id, "Número inválido. Usa el número de cuenta o '-'.")
                    return
                ctx.fields["account_id"] = str(accs[idx].id)
                ctx.fields["account_name"] = accs[idx].name
            summary = (
                f"Confirmar {verb}:\n"
                f"- Deuda: {ctx.fields.get('debt_name', '-')}\n"
                f"- Monto: {fmt_money(parse_amount(ctx.fields['amount']))}\n"
                f"- Fecha: {ctx.fields['payment_date']}\n"
                f"- Cuenta: {ctx.fields.get('account_name', '(sin cuenta)')}"
            )
            self._ask_confirm(chat_id, session, ctx, summary)

    # --- debt add amount ---
    def _flow_debt_add(self, chat_id, session, ctx, text):
        debts = self.debts.list_active()
        if ctx.step == "debt":
            idx = self._parse_index(text, len(debts))
            if idx is None:
                self.client.send_message(chat_id, "Número inválido.")
                return
            selected = debts[idx]
            ctx.fields["debt_id"] = str(selected.id)
            ctx.fields["debt_name"] = selected.name
            ctx.fields["total_before"] = str(selected.total_amount)
            ctx.fields["pending_before"] = str(selected.pending_amount)
            ctx.step = "amount"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            self.client.send_message(
                chat_id,
                f"Deuda: {selected.name}\n"
                f"Total actual: {fmt_money(selected.total_amount)} | "
                f"Pendiente: {fmt_money(selected.pending_amount)}\n\n"
                "Monto a agregar:",
            )
        elif ctx.step == "amount":
            amount = parse_amount(text)
            if amount is None:
                self.client.send_message(chat_id, "Monto inválido.")
                return
            ctx.fields["amount"] = str(amount)
            new_total = parse_amount(ctx.fields["total_before"]) + amount
            new_pending = parse_amount(ctx.fields["pending_before"]) + amount
            summary = (
                "Confirmar agregar monto a deuda:\n"
                f"- Deuda: {ctx.fields.get('debt_name', '-')}\n"
                f"- Monto a agregar: {fmt_money(amount)}\n"
                f"- Nuevo total: {fmt_money(new_total)}\n"
                f"- Nuevo pendiente: {fmt_money(new_pending)}"
            )
            self._ask_confirm(chat_id, session, ctx, summary)

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

    # --- debt edit ---
    def _flow_debt_edit(self, chat_id, session, ctx, text):
        debts = self.debts.list_active()
        if ctx.step == "debt":
            idx = self._parse_index(text, len(debts))
            if idx is None:
                self.client.send_message(chat_id, "Número inválido.")
                return
            selected = debts[idx]
            ctx.fields["debt_id"] = str(selected.id)
            ctx.fields["name"] = selected.name
            ctx.fields["counterparty"] = selected.counterparty or ""
            ctx.fields["notes"] = selected.notes or ""
            ctx.fields["due"] = selected.due_date.isoformat() if selected.due_date else ""
            ctx.fields["direction"] = selected.direction.value
            ctx.step = "name"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            party_label = "Acreedor" if selected.direction == DebtDirection.POR_PAGAR else "Deudor"
            self.client.send_message(
                chat_id,
                f"Referencia actual: {selected.name}\n"
                f"{party_label} actual: {selected.counterparty or '(sin registrar)'}\n\n"
                "Nuevo nombre/referencia (o '-' para mantener):",
            )
        elif ctx.step == "name":
            if text != "-":
                ctx.fields["name"] = text.strip()
            ctx.step = "counterparty"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            party_label = "Acreedor" if ctx.fields["direction"] == DebtDirection.POR_PAGAR.value else "Deudor"
            current = ctx.fields.get("counterparty") or "(sin registrar)"
            self.client.send_message(
                chat_id,
                f"Nuevo {party_label.lower()} (o '-' para mantener):\nActual: {current}",
            )
        elif ctx.step == "counterparty":
            if text == "-":
                pass
            elif text == "0":
                ctx.fields["counterparty"] = ""
            else:
                ctx.fields["counterparty"] = text.strip()
            ctx.step = "notes"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            current = ctx.fields.get("notes") or "(sin notas)"
            self.client.send_message(
                chat_id,
                f"Nuevas notas/descripción (o '-' mantener, '0' limpiar):\nActual: {current}",
            )
        elif ctx.step == "notes":
            if text == "-":
                pass
            elif text == "0":
                ctx.fields["notes"] = ""
            else:
                ctx.fields["notes"] = text.strip()
            ctx.step = "due"
            self.sessions.save_context(session, "CONVERSATION", ctx)
            current = ctx.fields.get("due") or "(sin vencimiento)"
            self.client.send_message(
                chat_id,
                f"Nueva fecha de vencimiento YYYY-MM-DD (o '-' mantener, '0' limpiar):\nActual: {current}",
            )
        elif ctx.step == "due":
            if text == "-":
                due = date.fromisoformat(ctx.fields["due"]) if ctx.fields.get("due") else None
            elif text == "0":
                due = None
            else:
                due = self._parse_date(text)
                if due is None:
                    self.client.send_message(chat_id, "Fecha inválida. Usa YYYY-MM-DD.")
                    return
            ctx.fields["due"] = due.isoformat() if due else ""
            party_label = "Acreedor" if ctx.fields["direction"] == DebtDirection.POR_PAGAR.value else "Deudor"
            summary = (
                "Confirmar edición de deuda:\n"
                f"- Referencia: {ctx.fields['name']}\n"
                f"- {party_label}: {ctx.fields.get('counterparty') or '(sin registrar)'}\n"
                f"- Notas: {ctx.fields.get('notes') or '(sin notas)'}\n"
                f"- Vence: {ctx.fields.get('due') or 'sin fecha'}"
            )
            self._ask_confirm(chat_id, session, ctx, summary)

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
