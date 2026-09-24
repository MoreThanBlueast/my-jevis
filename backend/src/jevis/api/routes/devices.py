import json
import uuid
from datetime import UTC, datetime

from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response, status
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from jevis.core.config import get_settings
from jevis.db.session import get_session
from jevis.models.command import CommandStatus, DeviceCommand
from jevis.models.device import Device
from jevis.models.task import Task, TaskEvent, TaskStatus
from jevis.schemas.command import CommandResult, DeviceCommandRead
from jevis.schemas.device import DeviceRead, DeviceRegistration, ExecutorEvent, ExecutorTask
from jevis.services.display_launcher import prepare_display
from jevis.services.execution_policy import completion_supported, recovery_allowed

router = APIRouter(prefix="/devices", tags=["devices"])


@router.post("/{device_id}/tasks/{task_id}/prepare-display")
async def prepare_task_display(
    device_id: str, task_id: uuid.UUID,
    authorization: str | None = Header(default=None),
    session: AsyncSession = Depends(get_session),
) -> dict[str, bool]:
    verify_token(authorization)
    task = await session.get(Task, task_id)
    device = await session.get(Device, device_id)
    if task is None or device is None or task.device_id != device_id:
        raise HTTPException(status_code=404, detail="设备或任务不匹配")
    if task.status not in {TaskStatus.PREPARING_DEVICE, TaskStatus.RUNNING}:
        raise HTTPException(status_code=409, detail="任务不可执行")
    if not get_settings().adb_launch_enabled:
        raise HTTPException(status_code=409, detail="本机未启用 ADB 隔离启动桥")
    if device.profile_user_id != 0 or not device.display_id or device.display_id < 1:
        raise HTTPException(status_code=409, detail="隔离显示或 Android 用户不匹配")
    try:
        await prepare_display(device_id, device.display_id, task.target_app)
    except (RuntimeError, ValueError, TimeoutError) as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    return {"ready": True}


@router.get("/{device_id}/task-statuses")
async def task_statuses(
    device_id: str, authorization: str | None = Header(default=None),
    session: AsyncSession = Depends(get_session),
) -> list[dict[str, str]]:
    verify_token(authorization)
    tasks = await session.scalars(select(Task).where(Task.device_id == device_id)
                                 .order_by(Task.updated_at))
    return [{"task_id": str(task.id), "status": task.status,
             "title": task.instruction[:60]} for task in tasks]


def verify_token(authorization: str | None) -> None:
    expected = f"Bearer {get_settings().device_gateway_token}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="设备凭据无效")


@router.post("/register", response_model=DeviceRead)
async def register_device(
    payload: DeviceRegistration,
    authorization: str | None = Header(default=None),
    session: AsyncSession = Depends(get_session),
) -> DeviceRead:
    verify_token(authorization)
    device = await session.get(Device, payload.device_id)
    values = payload.model_dump(exclude={"device_id"})
    if device is None:
        device = Device(id=payload.device_id, **values)
        session.add(device)
    else:
        for key, value in values.items():
            setattr(device, key, value)
    device.online = True
    device.last_seen_at = datetime.now(UTC)
    await session.commit()
    return DeviceRead(**payload.model_dump(), online=True)


@router.post("/{device_id}/next-task", response_model=ExecutorTask | None)
async def claim_next_task(
    device_id: str,
    response: Response,
    authorization: str | None = Header(default=None),
    session: AsyncSession = Depends(get_session),
) -> ExecutorTask | None:
    verify_token(authorization)
    device = await session.get(Device, device_id)
    if device is None:
        raise HTTPException(status_code=404, detail="设备尚未注册")
    if not device.virtual_display_ready or not device.directed_input_ready:
        raise HTTPException(status_code=409, detail="设备隔离能力未通过")
    if device.display_id in (None, 0) or device.profile_user_id is None:
        raise HTTPException(status_code=409, detail="缺少安全的 displayId 或 profileUserId")

    active_task = await session.scalar(
        select(Task)
        .where(
            Task.device_id == device_id,
            Task.status.in_([TaskStatus.PREPARING_DEVICE, TaskStatus.RUNNING]),
        )
        .order_by(Task.created_at)
        .limit(1)
    )
    task = active_task
    query = (
        select(Task)
        .where(
            Task.status == TaskStatus.QUEUED,
            or_(Task.device_id.is_(None), Task.device_id == device_id),
            Task.target_app != "AUTO",
        )
        .order_by(Task.created_at)
        .with_for_update(skip_locked=True)
        .limit(1)
    )
    if task is None:
        task = await session.scalar(query)
    if task is None:
        response.status_code = status.HTTP_204_NO_CONTENT
        return None
    next_event_sequence = await session.scalar(
        select(func.coalesce(func.max(TaskEvent.sequence), 0)).where(TaskEvent.task_id == task.id)
    )
    next_event_sequence = int(next_event_sequence or 0) + 1
    if active_task is None:
        task.device_id = device_id
        task.status = TaskStatus.PREPARING_DEVICE
        session.add(
            TaskEvent(
                task_id=task.id,
                sequence=next_event_sequence,
                event_type="DEVICE_CLAIMED",
                summary=f"设备 {device_id} 已领取任务，等待创建隔离会话",
                payload={"displayId": device.display_id, "profileUserId": device.profile_user_id},
            )
        )
        next_event_sequence += 1
    await session.commit()
    return ExecutorTask(
        task_id=task.id,
        instruction=task.instruction,
        target_app=task.target_app,
        confirmation_policy=task.confirmation_policy,
        recipient=task.recipient,
        subject=task.subject,
        body=task.body,
        idempotency_key=task.idempotency_key,
        required_display_id=device.display_id,
        required_profile_user_id=device.profile_user_id,
        next_event_sequence=next_event_sequence,
        resumed=active_task is not None,
    )


@router.post("/{device_id}/tasks/{task_id}/events", status_code=status.HTTP_202_ACCEPTED)
async def append_executor_event(
    device_id: str,
    task_id: uuid.UUID,
    payload: ExecutorEvent,
    request: Request,
    authorization: str | None = Header(default=None),
    session: AsyncSession = Depends(get_session),
) -> dict[str, str]:
    verify_token(authorization)
    device = await session.get(Device, device_id)
    task = await session.scalar(
        select(Task).where(Task.id == task_id).options(selectinload(Task.events))
    )
    if device is None or task is None or task.device_id != device_id:
        raise HTTPException(status_code=404, detail="设备或任务不匹配")
    if payload.display_id != device.display_id or payload.profile_user_id != device.profile_user_id:
        raise HTTPException(status_code=409, detail="执行事件越过了已注册的显示或资料边界")
    expected_sequence = (task.events[-1].sequence if task.events else 0) + 1
    if payload.sequence != expected_sequence:
        raise HTTPException(status_code=409, detail=f"事件序号必须为 {expected_sequence}")
    allowed_statuses = {
        TaskStatus.RUNNING,
        TaskStatus.WAITING_CONFIRMATION,
        TaskStatus.VERIFYING,
        TaskStatus.SUCCEEDED,
        TaskStatus.FAILED,
        TaskStatus.NEEDS_REVIEW,
        TaskStatus.CANCELED,
    }
    if payload.status is not None and payload.status not in allowed_statuses:
        raise HTTPException(status_code=422, detail="执行器不能设置该任务状态")
    event_payload = {
        **payload.payload,
        "displayId": payload.display_id,
        "profileUserId": payload.profile_user_id,
    }
    queued_observation = payload.payload.get("observation")
    if payload.event_type == "OBSERVATION" and isinstance(queued_observation, dict):
        event_payload["observation"] = {
            "displayId": queued_observation.get("displayId"),
            "packageName": queued_observation.get("packageName"),
            "nodeCount": len(queued_observation.get("nodes", [])),
            "redacted": True,
        }
    session.add(
        TaskEvent(
            task_id=task.id,
            sequence=payload.sequence,
            event_type=payload.event_type,
            summary=payload.summary,
            payload=event_payload,
        )
    )
    if payload.status is not None:
        task.status = payload.status
    await session.commit()
    if payload.event_type == "OBSERVATION" and request.app.state.redis is not None:
        await request.app.state.redis.rpush(
            "jevis:agent_queue",
            json.dumps({"task_id": str(task.id), "observation": queued_observation}),
        )
    return {"result": "accepted"}


@router.post(
    "/{device_id}/tasks/{task_id}/next-action",
    response_model=DeviceCommandRead | None,
)
async def claim_next_action(
    device_id: str,
    task_id: uuid.UUID,
    response: Response,
    authorization: str | None = Header(default=None),
    session: AsyncSession = Depends(get_session),
) -> DeviceCommandRead | None:
    verify_token(authorization)
    device = await session.get(Device, device_id)
    task = await session.get(Task, task_id)
    if device is None or task is None or task.device_id != device_id:
        raise HTTPException(status_code=404, detail="设备或任务不匹配")
    if device.display_id in (None, 0) or not device.directed_input_ready:
        raise HTTPException(status_code=409, detail="设备定向输入能力已失效")
    if task.status != TaskStatus.RUNNING:
        response.status_code = status.HTTP_204_NO_CONTENT
        return None
    command = await session.scalar(
        select(DeviceCommand)
        .where(
            DeviceCommand.task_id == task_id,
            DeviceCommand.status == CommandStatus.PENDING,
        )
        .order_by(DeviceCommand.sequence)
        .with_for_update(skip_locked=True)
        .limit(1)
    )
    if command is None:
        response.status_code = status.HTTP_204_NO_CONTENT
        return None
    command.status = CommandStatus.RUNNING
    await session.commit()
    await session.refresh(command)
    return DeviceCommandRead.model_validate(command)


@router.post("/{device_id}/commands/{command_id}/result", status_code=status.HTTP_202_ACCEPTED)
async def complete_action(
    device_id: str,
    command_id: uuid.UUID,
    payload: CommandResult,
    request: Request,
    authorization: str | None = Header(default=None),
    session: AsyncSession = Depends(get_session),
) -> dict[str, str]:
    verify_token(authorization)
    device = await session.get(Device, device_id)
    command = await session.get(DeviceCommand, command_id)
    if device is None or command is None:
        raise HTTPException(status_code=404, detail="设备或动作不存在")
    task = await session.get(Task, command.task_id)
    if task is None or task.device_id != device_id:
        raise HTTPException(status_code=404, detail="动作不属于该设备")
    if task.status != TaskStatus.RUNNING:
        raise HTTPException(status_code=409, detail="任务已暂停或终止，拒绝迟到的动作结果")
    if payload.display_id != device.display_id or payload.profile_user_id != device.profile_user_id:
        raise HTTPException(status_code=409, detail="动作结果越过显示或资料边界")
    if command.status != CommandStatus.RUNNING:
        raise HTTPException(status_code=409, detail="动作不在执行状态")
    command.status = CommandStatus.SUCCEEDED if payload.success else CommandStatus.FAILED
    command.completed_at = datetime.now(UTC)
    next_event_sequence = await session.scalar(
        select(func.coalesce(func.max(TaskEvent.sequence), 0)).where(TaskEvent.task_id == task.id)
    )
    session.add(
        TaskEvent(
            task_id=task.id,
            sequence=int(next_event_sequence or 0) + 1,
            event_type="COMMAND_RESULT",
            summary=payload.summary,
            payload={
                "commandId": str(command.id),
                "actionType": command.action_type,
                "success": payload.success,
                "displayId": payload.display_id,
                "profileUserId": payload.profile_user_id,
                "observation": {
                    "displayId": payload.observation.get("displayId"),
                    "packageName": payload.observation.get("packageName"),
                    "nodeCount": len(payload.observation.get("nodes", [])),
                    "redacted": True,
                },
            },
        )
    )
    if command.action_type == "FINISH" and payload.success:
        result = command.arguments.get("result")
        review_results = {"MODEL_REQUIRED", "READY_TO_SEND", "NEEDS_REVIEW"}
        verified = completion_supported(command.arguments, payload.observation, task.target_app)
        task.status = (
            TaskStatus.SUCCEEDED if verified and result not in review_results
            else TaskStatus.NEEDS_REVIEW
        )
    elif not payload.success:
        recent = list((await session.scalars(
            select(DeviceCommand).where(DeviceCommand.task_id == task.id)
            .order_by(DeviceCommand.sequence.desc()).limit(3)
        )).all())
        failures = 0
        for item in recent:
            if item.status != CommandStatus.FAILED:
                break
            failures += 1
        task.status = (
            TaskStatus.RUNNING if recovery_allowed(command.action_type, failures)
            else TaskStatus.NEEDS_REVIEW
        )
    await session.commit()
    if (task.status == TaskStatus.RUNNING and command.action_type != "FINISH"
            and request.app.state.redis is not None):
        await request.app.state.redis.rpush(
            "jevis:agent_queue",
            json.dumps(
                {
                    "task_id": str(task.id),
                    "observation": {
                        **payload.observation,
                        "_last_action_type": command.action_type,
                        "_last_action_summary": payload.summary,
                        "_last_action_success": payload.success,
                    },
                }
            ),
        )
    return {"result": "accepted"}
