"""debt payment account optional

Revision ID: 002
Revises: 001
Create Date: 2026-07-05
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "002"
down_revision: Union[str, None] = "001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.alter_column("debt_payments", "account_id", existing_type=sa.Integer(), nullable=True)


def downgrade() -> None:
    op.alter_column("debt_payments", "account_id", existing_type=sa.Integer(), nullable=False)
