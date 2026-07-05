from sqlalchemy.orm import Session

from app.models.enums import CategoryType, DebtDirection
from app.services.account_service import AccountService
from app.services.category_service import CategoryService
from app.telegram.client import TelegramClient
from app.telegram.conversation import ConversationService
from app.telegram.keyboards import accounts_menu_keyboard, main_menu_keyboard, movements_menu_keyboard
from app.telegram.quick_replies import QuickReplies
from app.telegram.session import FlowContext, SessionService


class UpdateHandler:
    WELCOME = (
        "¡Hola! Soy tu asistente financiero.\n"
        "Llevo deudas (debo / me deben), cuentas, ingresos y gastos."
    )

    def __init__(self, db: Session):
        self.db = db
        self.client = TelegramClient()
        self.sessions = SessionService(db)
        self.conversation = ConversationService(db, self.client)
        self.quick = QuickReplies(db)
        self.accounts = AccountService(db)
        self.categories = CategoryService(db)

    def process_update(self, update: dict) -> None:
        if callback := update.get("callback_query"):
            self._handle_callback(callback)
            return
        message = update.get("message") or {}
        text = (message.get("text") or "").strip()
        chat = message.get("chat") or {}
        chat_id = chat.get("id")
        if not chat_id or not text:
            return
        if text.startswith("/"):
            self._handle_command(chat_id, text)
            return
        session = self.sessions.get_or_create(chat_id)
        if session.state == "CONVERSATION" or self.sessions.has_active_flow(session):
            self.conversation.handle(chat_id, text)
        else:
            self.send_main_menu(chat_id)

    def _handle_command(self, chat_id: int, text: str) -> None:
        cmd = text.split()[0].split("@")[0].lower()
        session = self.sessions.get_or_create(chat_id)

        if cmd in ("/start", "/menu", "/ayuda"):
            self.send_main_menu(chat_id)
            return
        if cmd == "/cancelar":
            self.sessions.reset(session)
            self.client.send_message(chat_id, "Conversación cancelada.")
            return

        if session.state == "CONVERSATION":
            self.sessions.reset(session)

        routes = {
            "/registrar_gasto": self._start_expense,
            "/registrar_ingreso": self._start_income,
            "/cuenta_crear": self._start_account_create,
            "/ver_cuentas": lambda cid: self.client.send_message(cid, "Cuentas:\n" + self.quick.format_accounts(self.accounts.list_active())),
            "/balance": lambda cid: self.client.send_message(cid, self.quick.balance()),
            "/debo": lambda cid: self._show_debts(cid, DebtDirection.POR_PAGAR),
            "/me_deben": lambda cid: self._show_debts(cid, DebtDirection.POR_COBRAR),
            "/resumen_deudas": lambda cid: self.client.send_message(cid, self.quick.debt_summary()),
            "/editar_deuda": self._start_debt_edit,
        }
        handler = routes.get(cmd)
        if handler:
            handler(chat_id)
        else:
            self.client.send_message(chat_id, "Comando no reconocido. Usa /menu.")

    def _handle_callback(self, callback: dict) -> None:
        callback_id = callback.get("id")
        try:
            message = callback.get("message") or {}
            chat_id = (message.get("chat") or {}).get("id")
            data = (callback.get("data") or "").strip()
            if not chat_id:
                return
            session = self.sessions.get_or_create(chat_id)
            if data != "menu:cancel" and session.state == "CONVERSATION":
                self.sessions.reset(session)

            if data == "menu:main":
                self.send_main_menu(chat_id)
            elif data == "menu:movements":
                self.client.send_message(chat_id, "Movimientos:", movements_menu_keyboard())
            elif data == "menu:accounts":
                self.client.send_message(chat_id, "Cuentas:", accounts_menu_keyboard())
            elif data == "menu:cancel":
                self.sessions.reset(session)
                self.send_main_menu(chat_id)
            elif data == "debts:payable":
                self._show_debts(chat_id, DebtDirection.POR_PAGAR)
            elif data == "debts:receivable":
                self._show_debts(chat_id, DebtDirection.POR_COBRAR)
            elif data == "debts:summary":
                self.client.send_message(chat_id, self.quick.debt_summary())
            elif data == "action:balance":
                self.client.send_message(chat_id, self.quick.balance())
            elif data == "action:view_accounts":
                self.client.send_message(chat_id, "Cuentas:\n" + self.quick.format_accounts(self.accounts.list_active()))
            elif data == "action:account_create":
                self._start_account_create(chat_id)
            elif data == "action:expense":
                self._start_expense(chat_id)
            elif data == "action:income":
                self._start_income(chat_id)
            elif data == "action:debt_payable":
                self._start_debt_register(chat_id, DebtDirection.POR_PAGAR)
            elif data == "action:debt_receivable":
                self._start_debt_register(chat_id, DebtDirection.POR_COBRAR)
            elif data == "action:debt_pay":
                self._start_debt_movement(chat_id, DebtDirection.POR_PAGAR)
            elif data == "action:debt_collect":
                self._start_debt_movement(chat_id, DebtDirection.POR_COBRAR)
            elif data == "action:debt_detail":
                self._start_debt_detail(chat_id)
            elif data == "action:debt_edit":
                self._start_debt_edit(chat_id)
        finally:
            self.client.answer_callback_query(callback_id or "")

    def send_main_menu(self, chat_id: int) -> None:
        self.client.send_message(chat_id, self.WELCOME + "\n\nMenú principal:", main_menu_keyboard())

    def _show_debts(self, chat_id: int, direction: DebtDirection) -> None:
        from app.services.debt_service import DebtService

        debts = DebtService(self.db).list_active(direction)
        title = "Deudas que debo:" if direction == DebtDirection.POR_PAGAR else "Dinero que me deben:"
        self.client.send_message(chat_id, title + "\n\n" + self.quick.format_debts(debts, direction))

    def _start_account_create(self, chat_id: int) -> None:
        self.conversation.begin(chat_id, "account_create", "name", "Nombre de la cuenta:")

    def _start_income(self, chat_id: int) -> None:
        if not self.accounts.list_active():
            self.client.send_message(chat_id, "Crea una cuenta primero.")
            return
        if not self.categories.list_active_by_type(CategoryType.INGRESO):
            self.categories.ensure_defaults()
        self.conversation.begin(chat_id, "income", "amount", "Monto del ingreso:")

    def _start_expense(self, chat_id: int) -> None:
        from app.models.enums import CategoryType

        if not self.accounts.list_active():
            self.client.send_message(chat_id, "Crea una cuenta primero.")
            return
        if not self.categories.list_active_by_type(CategoryType.GASTO):
            self.categories.ensure_defaults()
        self.conversation.begin(chat_id, "expense", "amount", "Monto del gasto:")

    def _start_debt_register(self, chat_id: int, direction: DebtDirection) -> None:
        session = self.sessions.get_or_create(chat_id)
        ctx = FlowContext(flow="debt_register", step="name", fields={"direction": direction.value})
        self.sessions.save_context(session, "CONVERSATION", ctx)
        label = "Nombre de la deuda que debes:" if direction == DebtDirection.POR_PAGAR else "Nombre / referencia de lo que te deben:"
        self.client.send_message(chat_id, label)

    def _start_debt_movement(self, chat_id: int, direction: DebtDirection) -> None:
        from app.services.debt_service import DebtService

        debts = DebtService(self.db).list_active(direction)
        if not debts:
            self.client.send_message(chat_id, "No hay deudas activas para esta acción.")
            return
        session = self.sessions.get_or_create(chat_id)
        ctx = FlowContext(flow="debt_movement", step="debt", fields={"direction": direction.value})
        self.sessions.save_context(session, "CONVERSATION", ctx)
        title = "Elige la deuda a abonar (número):\n" if direction == DebtDirection.POR_PAGAR else "Elige lo que te deben (número):\n"
        self.client.send_message(chat_id, title + self.quick.format_debts(debts, direction))

    def _start_debt_detail(self, chat_id: int) -> None:
        from app.services.debt_service import DebtService

        debts = DebtService(self.db).list_active(DebtDirection.POR_PAGAR) + DebtService(self.db).list_active(
            DebtDirection.POR_COBRAR
        )
        if not debts:
            self.client.send_message(chat_id, "No hay deudas activas.")
            return
        session = self.sessions.get_or_create(chat_id)
        ctx = FlowContext(flow="debt_detail", step="pick")
        self.sessions.save_context(session, "CONVERSATION", ctx)
        lines = []
        for i, d in enumerate(debts, start=1):
            tag = "DEBO" if d.direction == DebtDirection.POR_PAGAR else "ME DEBEN"
            lines.append(f"{i}) [{tag}] {d.name} — pend. {d.pending_amount}")
        self.client.send_message(chat_id, "Elige deuda por número:\n" + "\n".join(lines))

    def _start_debt_edit(self, chat_id: int) -> None:
        from app.services.debt_service import DebtService

        debts = DebtService(self.db).list_active()
        if not debts:
            self.client.send_message(chat_id, "No hay deudas activas para editar.")
            return
        session = self.sessions.get_or_create(chat_id)
        ctx = FlowContext(flow="debt_edit", step="debt")
        self.sessions.save_context(session, "CONVERSATION", ctx)
        lines = []
        for i, d in enumerate(debts, start=1):
            tag = "DEBO" if d.direction == DebtDirection.POR_PAGAR else "ME DEBEN"
            party = d.counterparty or "(sin contraparte)"
            lines.append(f"{i}) [{tag}] {d.name} — {party}")
        self.client.send_message(
            chat_id,
            "Editar deuda — elige por número:\n" + "\n".join(lines),
        )
