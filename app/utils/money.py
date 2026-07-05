from decimal import Decimal, InvalidOperation, ROUND_HALF_UP


def normalize(amount: Decimal) -> Decimal:
    return amount.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def parse_amount(raw: str) -> Decimal | None:
    text = raw.strip().replace(",", ".")
    if not text:
        return None
    try:
        value = Decimal(text)
    except InvalidOperation:
        return None
    if value <= 0:
        return None
    return normalize(value)


def fmt_money(value: Decimal | None) -> str:
    safe = value if value is not None else Decimal("0")
    text = f"{safe:,.2f}"
    return text.replace(",", "X").replace(".", ",").replace("X", ".")
