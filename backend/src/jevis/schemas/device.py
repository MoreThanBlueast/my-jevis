import uuid

from pydantic import BaseModel, Field, model_validator

from jevis.models.task import TaskStatus


class DeviceRegistration(BaseModel):
    device_id: str = Field(min_length=3, max_length=100)
    model: str = Field(min_length=1, max_length=100)
    android_version: str
    hyperos_version: str | None = None
    display_id: int | None = None
    profile_user_id: int | None = None
    virtual_display_ready: bool = False
    directed_input_ready: bool = False

    @model_validator(mode="after")
    def reject_main_display(self) -> "DeviceRegistration":
        if self.virtual_display_ready and (self.display_id is None or self.display_id == 0):
            raise ValueError("虚拟显示就绪时 display_id 必须为非零值")
        return self


class DeviceRead(DeviceRegistration):
    online: bool


class ExecutorTask(BaseModel):
    task_id: uuid.UUID
    instruction: str
    target_app: str
    confirmation_policy: str
    recipient: str
    subject: str
    body: str
    idempotency_key: str
    required_display_id: int
    required_profile_user_id: int


class ExecutorEvent(BaseModel):
    sequence: int = Field(ge=1)
    event_type: str = Field(min_length=3, max_length=80)
    summary: str = Field(min_length=1, max_length=1000)
    display_id: int
    profile_user_id: int
    status: TaskStatus | None = None
    payload: dict = Field(default_factory=dict)

    @model_validator(mode="after")
    def reject_main_display(self) -> "ExecutorEvent":
        if self.display_id == 0:
            raise ValueError("拒绝来自物理主屏 Display 0 的执行事件")
        return self
