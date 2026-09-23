import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field, model_validator

from jevis.models.task import TaskStatus
from jevis.schemas.approval import ApprovalRead
from jevis.services.app_registry import ALLOWED_TARGET_APPS


class TaskCreate(BaseModel):
    instruction: str = Field(min_length=3, max_length=2000)
    target_app: str | None = Field(default=None, max_length=160)
    confirmation_policy: str = Field(default="BEFORE_EXTERNAL_ACTION", max_length=40)
    recipient: EmailStr | None = None
    subject: str | None = Field(default=None, max_length=240)
    body: str | None = Field(default=None, max_length=10_000)
    device_id: str | None = Field(default=None, max_length=100)
    idempotency_key: str = Field(min_length=8, max_length=100)

    @model_validator(mode="after")
    def validate_policy(self) -> "TaskCreate":
        if self.confirmation_policy not in {"BEFORE_EXTERNAL_ACTION", "PREAUTHORIZED"}:
            raise ValueError("不支持的确认策略")
        if self.target_app is not None and self.target_app not in ALLOWED_TARGET_APPS:
            raise ValueError("目标应用不在受控能力列表中")
        return self


class TaskEventRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    sequence: int
    event_type: str
    summary: str
    payload: dict
    created_at: datetime


class TaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    instruction: str
    task_type: str
    target_app: str
    confirmation_policy: str
    recipient: str
    subject: str
    body: str
    device_id: str | None
    idempotency_key: str
    status: TaskStatus
    error_code: str | None
    error_message: str | None
    created_at: datetime
    updated_at: datetime
    events: list[TaskEventRead] = []
    approvals: list[ApprovalRead] = []
