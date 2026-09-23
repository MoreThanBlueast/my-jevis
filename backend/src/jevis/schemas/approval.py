import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict

from jevis.models.approval import ApprovalStatus


class ApprovalRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    action_summary: str
    action_payload: dict
    status: ApprovalStatus
    created_at: datetime
    resolved_at: datetime | None
