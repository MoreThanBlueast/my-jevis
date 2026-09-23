import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field, model_validator

from jevis.models.task import TaskStatus


class TaskCreate(BaseModel):
    instruction: str = Field(min_length=3, max_length=2000)
    recipient: EmailStr
    subject: str = Field(min_length=1, max_length=240)
    body: str = Field(min_length=1, max_length=10_000)
    device_id: str | None = Field(default=None, max_length=100)
    idempotency_key: str = Field(min_length=8, max_length=100)

    @model_validator(mode="after")
    def ensure_email_intent(self) -> "TaskCreate":
        if not self.recipient or not self.subject or not self.body:
            raise ValueError("QQ 邮箱任务必须包含收件人、主题和正文")
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
