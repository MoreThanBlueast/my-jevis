from datetime import datetime

from sqlalchemy import Boolean, DateTime, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from jevis.db.base import Base
from jevis.models.task import utcnow


class Device(Base):
    __tablename__ = "devices"

    id: Mapped[str] = mapped_column(String(100), primary_key=True)
    model: Mapped[str] = mapped_column(String(100))
    android_version: Mapped[str] = mapped_column(String(40))
    hyperos_version: Mapped[str | None] = mapped_column(String(80), nullable=True)
    display_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    profile_user_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    virtual_display_ready: Mapped[bool] = mapped_column(Boolean, default=False)
    directed_input_ready: Mapped[bool] = mapped_column(Boolean, default=False)
    online: Mapped[bool] = mapped_column(Boolean, default=False)
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
