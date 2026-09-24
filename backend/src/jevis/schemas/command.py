import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, model_validator

from jevis.models.command import CommandStatus

ALLOWED_ACTIONS = {
    "CLICK",
    "SET_TEXT",
    "TAP",
    "SWIPE",
    "WAIT",
    "OBSERVE",
    "FINISH",
    "REQUEST_CONFIRMATION",
}


class DeviceCommandRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    task_id: uuid.UUID
    sequence: int
    action_type: str
    arguments: dict
    summary: str
    status: CommandStatus
    created_at: datetime


class CommandResult(BaseModel):
    display_id: int
    profile_user_id: int
    success: bool
    summary: str = Field(min_length=1, max_length=1000)
    observation: dict = Field(default_factory=dict)

    @model_validator(mode="after")
    def reject_main_display(self) -> "CommandResult":
        if self.display_id == 0:
            raise ValueError("拒绝来自物理主屏 Display 0 的动作结果")
        return self


class PlannedAction(BaseModel):
    action_type: str
    arguments: dict = Field(default_factory=dict)
    summary: str = Field(min_length=1, max_length=500)

    @model_validator(mode="after")
    def validate_action(self) -> "PlannedAction":
        self.action_type = self.action_type.upper()
        if self.action_type == "WAIT" and "duration_ms" in self.arguments:
            self.arguments["milliseconds"] = self.arguments.pop("duration_ms")
        if self.action_type not in ALLOWED_ACTIONS:
            raise ValueError("模型返回了未授权动作")
        if "display_id" in self.arguments or "profile_user_id" in self.arguments:
            raise ValueError("模型不得选择显示或 Android 用户")
        return self
