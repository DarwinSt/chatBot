def confirm_keyboard() -> dict:
    return {
        "inline_keyboard": [
            [
                {"text": "✅ Confirmar", "callback_data": "cfm:ok"},
                {"text": "❌ Cancelar", "callback_data": "cfm:cancel"},
            ]
        ]
    }


def main_menu_keyboard() -> dict:
    return {
        "inline_keyboard": [
            [
                {"text": "📤 Debo", "callback_data": "debts:payable"},
                {"text": "📥 Me deben", "callback_data": "debts:receivable"},
            ],
            [
                {"text": "➕ Movimientos", "callback_data": "menu:movements"},
                {"text": "💼 Cuentas", "callback_data": "menu:accounts"},
            ],
            [
                {"text": "📊 Resumen deudas", "callback_data": "debts:summary"},
                {"text": "⚖️ Balance", "callback_data": "action:balance"},
            ],
            [{"text": "❌ Cancelar", "callback_data": "menu:cancel"}],
        ]
    }


def movements_menu_keyboard() -> dict:
    return {
        "inline_keyboard": [
            [
                {"text": "➕ Registrar gasto", "callback_data": "action:expense"},
                {"text": "💰 Registrar ingreso", "callback_data": "action:income"},
            ],
            [
                {"text": "📝 Registrar deuda (debo)", "callback_data": "action:debt_payable"},
                {"text": "📝 Registrar me deben", "callback_data": "action:debt_receivable"},
            ],
            [
                {"text": "💸 Abonar deuda", "callback_data": "action:debt_pay"},
                {"text": "💵 Registrar cobro", "callback_data": "action:debt_collect"},
            ],
            [
                {"text": "➕ Agregar a deuda", "callback_data": "action:debt_add"},
                {"text": "🔍 Detalle deuda", "callback_data": "action:debt_detail"},
            ],
            [
                {"text": "✏️ Editar deuda", "callback_data": "action:debt_edit"},
                {"text": "⬅️ Menú", "callback_data": "menu:main"},
            ],
        ]
    }


def accounts_menu_keyboard() -> dict:
    return {
        "inline_keyboard": [
            [
                {"text": "➕ Crear cuenta", "callback_data": "action:account_create"},
                {"text": "👀 Ver cuentas", "callback_data": "action:view_accounts"},
            ],
            [{"text": "⬅️ Menú", "callback_data": "menu:main"}],
        ]
    }
