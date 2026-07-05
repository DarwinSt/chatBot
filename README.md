# FinanceBot — Python + PostgreSQL + Telegram

Bot financiero personal desplegado en Render (plan free).

## Stack

- Python 3.11+, FastAPI, SQLAlchemy, Alembic
- PostgreSQL (Render)
- Telegram webhook

## Variables de entorno

| Variable | Descripción |
|----------|-------------|
| `DATABASE_URL` | Render la inyecta al vincular la BD |
| `TELEGRAM_BOT_TOKEN` | Token de [@BotFather](https://t.me/BotFather) |
| `TELEGRAM_WEBHOOK_SECRET` | Opcional; valida el webhook |
| `APP_BASE_URL` | URL pública HTTPS del servicio, ej. `https://financebot.onrender.com` |
| `PORT` | Lo define Render automáticamente |

Local: copia `.env.example` → `.env`

## Local

```bash
python -m venv .venv
.venv\Scripts\activate        # Windows
pip install -r requirements.txt
alembic upgrade head
uvicorn app.main:app --reload --port 8000
```

Health: `GET http://localhost:8000/health`

## Deploy en Render (free)

1. Sube el repo a GitHub.
2. En Render: **New → Blueprint** y selecciona `render.yaml`, o crea manualmente:
   - **PostgreSQL** (free)
   - **Web Service** (Python, free)
3. Variables manuales en el Web Service:
   - `TELEGRAM_BOT_TOKEN`
   - `APP_BASE_URL` = URL del servicio Render (HTTPS)
   - `TELEGRAM_WEBHOOK_SECRET` (recomendado)
4. `DATABASE_URL` se vincula sola si usas el blueprint.
5. Tras el deploy, abre el bot en Telegram: `/start`

**Nota plan free:** el servicio se duerme tras inactividad; el primer mensaje puede tardar ~30–60 s.

## Funcionalidades

- **Cuentas** con saldo para ingresos y gastos
- **Deudas que debo** (`POR_PAGAR`) — abonos salen de cuenta
- **Dinero que me deben** (`POR_COBRAR`) — cobros entran a cuenta
- Detalle con historial de movimientos, resumen y balance

## Comandos Telegram

`/start` `/menu` `/cancelar` `/balance` `/debo` `/me_deben` `/resumen_deudas` `/ver_cuentas` `/registrar_gasto` `/registrar_ingreso` `/cuenta_crear`
