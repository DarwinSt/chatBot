import enum


class AccountType(str, enum.Enum):
    CORRIENTE = "CORRIENTE"
    AHORROS = "AHORROS"
    EFECTIVO = "EFECTIVO"
    BILLETERA_DIGITAL = "BILLETERA_DIGITAL"


class CategoryType(str, enum.Enum):
    INGRESO = "INGRESO"
    GASTO = "GASTO"
    DEUDA = "DEUDA"


class DebtDirection(str, enum.Enum):
    POR_PAGAR = "POR_PAGAR"
    POR_COBRAR = "POR_COBRAR"


class DebtStatus(str, enum.Enum):
    ACTIVA = "ACTIVA"
    PAGADA = "PAGADA"
    VENCIDA = "VENCIDA"
    CANCELADA = "CANCELADA"
