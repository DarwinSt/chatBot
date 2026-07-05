"""initial schema

Revision ID: 001
Revises:
Create Date: 2026-07-05
"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

account_type = postgresql.ENUM("CORRIENTE", "AHORROS", "EFECTIVO", "BILLETERA_DIGITAL", name="account_type", create_type=False)
category_type = postgresql.ENUM("INGRESO", "GASTO", "DEUDA", name="category_type", create_type=False)
debt_direction = postgresql.ENUM("POR_PAGAR", "POR_COBRAR", name="debt_direction", create_type=False)
debt_status = postgresql.ENUM("ACTIVA", "PAGADA", "VENCIDA", "CANCELADA", name="debt_status", create_type=False)


def upgrade() -> None:
    op.execute("""
        DO $$ BEGIN
            CREATE TYPE account_type AS ENUM ('CORRIENTE', 'AHORROS', 'EFECTIVO', 'BILLETERA_DIGITAL');
        EXCEPTION WHEN duplicate_object THEN NULL; END $$
    """)
    op.execute("""
        DO $$ BEGIN
            CREATE TYPE category_type AS ENUM ('INGRESO', 'GASTO', 'DEUDA');
        EXCEPTION WHEN duplicate_object THEN NULL; END $$
    """)
    op.execute("""
        DO $$ BEGIN
            CREATE TYPE debt_direction AS ENUM ('POR_PAGAR', 'POR_COBRAR');
        EXCEPTION WHEN duplicate_object THEN NULL; END $$
    """)
    op.execute("""
        DO $$ BEGIN
            CREATE TYPE debt_status AS ENUM ('ACTIVA', 'PAGADA', 'VENCIDA', 'CANCELADA');
        EXCEPTION WHEN duplicate_object THEN NULL; END $$
    """)

    op.create_table(
        "accounts",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("name", sa.String(100), nullable=False),
        sa.Column("type", account_type, nullable=False),
        sa.Column("initial_balance", sa.Numeric(19, 2), nullable=False, server_default="0"),
        sa.Column("current_balance", sa.Numeric(19, 2), nullable=False, server_default="0"),
        sa.Column("active", sa.Boolean(), nullable=False, server_default=sa.text("true")),
        sa.Column("notes", sa.String(500)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )

    op.create_table(
        "categories",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("name", sa.String(100), nullable=False),
        sa.Column("type", category_type, nullable=False),
        sa.Column("active", sa.Boolean(), nullable=False, server_default=sa.text("true")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.UniqueConstraint("name", "type", name="uk_categories_name_type"),
    )

    op.create_table(
        "debts",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("name", sa.String(150), nullable=False),
        sa.Column("direction", debt_direction, nullable=False),
        sa.Column("total_amount", sa.Numeric(19, 2), nullable=False),
        sa.Column("pending_amount", sa.Numeric(19, 2), nullable=False),
        sa.Column("start_date", sa.Date(), nullable=False),
        sa.Column("due_date", sa.Date()),
        sa.Column("counterparty", sa.String(150)),
        sa.Column("notes", sa.String(500)),
        sa.Column("status", debt_status, nullable=False),
        sa.Column("category_id", sa.Integer(), sa.ForeignKey("categories.id")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )

    op.create_table(
        "incomes",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("amount", sa.Numeric(19, 2), nullable=False),
        sa.Column("income_date", sa.Date(), nullable=False),
        sa.Column("origin", sa.String(150)),
        sa.Column("description", sa.String(500)),
        sa.Column("category_id", sa.Integer(), sa.ForeignKey("categories.id"), nullable=False),
        sa.Column("account_id", sa.Integer(), sa.ForeignKey("accounts.id"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )

    op.create_table(
        "expenses",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("amount", sa.Numeric(19, 2), nullable=False),
        sa.Column("expense_date", sa.Date(), nullable=False),
        sa.Column("description", sa.String(500)),
        sa.Column("category_id", sa.Integer(), sa.ForeignKey("categories.id"), nullable=False),
        sa.Column("account_id", sa.Integer(), sa.ForeignKey("accounts.id"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )

    op.create_table(
        "debt_payments",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("amount", sa.Numeric(19, 2), nullable=False),
        sa.Column("payment_date", sa.Date(), nullable=False),
        sa.Column("notes", sa.String(500)),
        sa.Column("account_id", sa.Integer(), sa.ForeignKey("accounts.id"), nullable=False),
        sa.Column("debt_id", sa.Integer(), sa.ForeignKey("debts.id"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )

    op.create_table(
        "telegram_sessions",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("chat_id", sa.BigInteger(), nullable=False, unique=True),
        sa.Column("state", sa.String(50), nullable=False, server_default="IDLE"),
        sa.Column("pending_command", sa.String(100)),
        sa.Column("context_data", sa.Text()),
        sa.Column("active", sa.Boolean(), nullable=False, server_default=sa.text("true")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )


def downgrade() -> None:
    op.drop_table("telegram_sessions")
    op.drop_table("debt_payments")
    op.drop_table("expenses")
    op.drop_table("incomes")
    op.drop_table("debts")
    op.drop_table("categories")
    op.drop_table("accounts")
    op.execute("DROP TYPE IF EXISTS debt_status")
    op.execute("DROP TYPE IF EXISTS debt_direction")
    op.execute("DROP TYPE IF EXISTS category_type")
    op.execute("DROP TYPE IF EXISTS account_type")
