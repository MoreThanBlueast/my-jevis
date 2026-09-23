import uuid
from datetime import datetime
from enum import StrEnum

from sqlalchemy import JSON, DateTime, Enum, ForeignKey, Text
from sqlalchemy.orm import Mapped, mapped_column

from jevis.db.base import Base
from jevis.models.task import utcnow


class ApprovalStatus(StrEnum):
    PENDING = "PENDING"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"
    EXPIRED = "EXPIRED"


class Approval(Base):
    __tablename__ = "approvals"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    task_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("tasks.id", ondelete="CASCADE"), index=True
    )
    action_summary: Mapped[str] = mapped_column(Text)
    action_payload: Mapped[dict] = mapped_column(JSON)
    status: Mapped[ApprovalStatus] = mapped_column(
        Enum(ApprovalStatus), default=ApprovalStatus.PENDING
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
