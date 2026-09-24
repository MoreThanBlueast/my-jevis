import asyncio
import json
import uuid
from datetime import UTC, datetime

from fastapi import (
    APIRouter,
    Depends,
    HTTPException,
    WebSocket,
    WebSocketDisconnect,
    status,
)
from redis.asyncio import Redis
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from jevis.api.deps import get_redis
from jevis.db.session import get_session
from jevis.models.approval import Approval, ApprovalStatus
from jevis.models.command import CommandStatus, DeviceCommand
from jevis.models.task import Task, TaskEvent, TaskStatus
from jevis.schemas.command import PlannedAction
from jevis.schemas.task import TaskCreate, TaskRead
from jevis.services.events import task_channel
from jevis.services.tasks import TaskService

router = APIRouter(prefix="/tasks", tags=["tasks"])


@router.post("", response_model=TaskRead, status_code=status.HTTP_201_CREATED)
async def create_task(
    payload: TaskCreate,
    session: AsyncSession = Depends(get_session),
    redis: Redis | None = Depends(get_redis),
) -> TaskRead:
    return TaskRead.model_validate(await TaskService(session, redis).create(payload))


@router.get("", response_model=list[TaskRead])
async def list_tasks(
    task_status: TaskStatus | None = None,
    session: AsyncSession = Depends(get_session),
    redis: Redis | None = Depends(get_redis),
) -> list[TaskRead]:
    tasks = await TaskService(session, redis).list(task_status)
    return [TaskRead.model_validate(task) for task in tasks]


@router.get("/{task_id}", response_model=TaskRead)
async def get_task(
    task_id: uuid.UUID,
    session: AsyncSession = Depends(get_session),
    redis: Redis | None = Depends(get_redis),
) -> TaskRead:
    task = await TaskService(session, redis).get(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="任务不存在")
    return TaskRead.model_validate(task)


@router.post("/{task_id}/cancel", response_model=TaskRead)
async def cancel_task(
    task_id: uuid.UUID,
    session: AsyncSession = Depends(get_session),
    redis: Redis | None = Depends(get_redis),
) -> TaskRead:
    service = TaskService(session, redis)
    task = await service.get(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="任务不存在")
    return TaskRead.model_validate(await service.cancel(task))


@router.post("/{task_id}/resume", response_model=TaskRead)
async def resume_task(
    task_id: uuid.UUID,
    session: AsyncSession = Depends(get_session),
    redis: Redis | None = Depends(get_redis),
) -> TaskRead:
    task = await session.scalar(select(Task).where(Task.id == task_id).with_for_update())
    if task is None:
        raise HTTPException(status_code=404, detail="任务不存在")
    if task.status != TaskStatus.NEEDS_REVIEW:
        raise HTTPException(status_code=409, detail="仅可继续已暂停的任务")
    commands = list((await session.scalars(
        select(DeviceCommand).where(DeviceCommand.task_id == task_id)
    )).all())
    # Never replay an in-flight command: its external effect may be unknown.
    if any(command.status in (CommandStatus.PENDING, CommandStatus.RUNNING)
           for command in commands):
        raise HTTPException(status_code=409, detail="存在未确认的动作，不能自动继续")
    sequence = await session.scalar(select(func.coalesce(func.max(TaskEvent.sequence), 0))
                                    .where(TaskEvent.task_id == task_id))
    task.status = TaskStatus.QUEUED
    task.error_code = None
    task.error_message = None
    session.add(TaskEvent(
        task_id=task_id, sequence=int(sequence or 0) + 1,
        event_type="TASK_RESUMED",
        summary="用户从任务列表继续任务；重新观察，保留历史，不重放旧动作",
        payload={},
    ))
    await session.commit()
    return TaskRead.model_validate(await TaskService(session, redis).get(task_id))


@router.post("/{task_id}/approvals/{approval_id}/approve", response_model=TaskRead)
async def approve_action(
    task_id: uuid.UUID,
    approval_id: uuid.UUID,
    session: AsyncSession = Depends(get_session),
    redis: Redis | None = Depends(get_redis),
) -> TaskRead:
    approval = await session.get(Approval, approval_id)
    task = await session.get(Task, task_id)
    if task is None or approval is None or approval.task_id != task_id:
        raise HTTPException(status_code=404, detail="确认请求不存在")
    if approval.status != ApprovalStatus.PENDING or task.status != TaskStatus.WAITING_CONFIRMATION:
        raise HTTPException(status_code=409, detail="确认请求已处理或任务状态不匹配")
    action = PlannedAction.model_validate(approval.action_payload)
    if action.action_type == "REQUEST_CONFIRMATION":
        raise HTTPException(status_code=422, detail="确认动作不能嵌套确认请求")
    next_command_sequence = await session.scalar(
        select(func.coalesce(func.max(DeviceCommand.sequence), 0)).where(
            DeviceCommand.task_id == task_id
        )
    )
    next_event_sequence = await session.scalar(
        select(func.coalesce(func.max(TaskEvent.sequence), 0)).where(TaskEvent.task_id == task_id)
    )
    session.add(
        DeviceCommand(
            task_id=task_id,
            sequence=int(next_command_sequence or 0) + 1,
            action_type=action.action_type,
            arguments=action.arguments,
            summary=action.summary,
        )
    )
    session.add(
        TaskEvent(
            task_id=task_id,
            sequence=int(next_event_sequence or 0) + 1,
            event_type="APPROVAL_GRANTED",
            summary="用户已批准外部操作",
            payload={"approvalId": str(approval.id)},
        )
    )
    approval.status = ApprovalStatus.APPROVED
    approval.resolved_at = datetime.now(UTC)
    task.status = TaskStatus.RUNNING
    await session.commit()
    resolved = await TaskService(session, redis).get(task_id)
    return TaskRead.model_validate(resolved)


@router.post("/{task_id}/approvals/{approval_id}/reject", response_model=TaskRead)
async def reject_action(
    task_id: uuid.UUID,
    approval_id: uuid.UUID,
    session: AsyncSession = Depends(get_session),
    redis: Redis | None = Depends(get_redis),
) -> TaskRead:
    approval = await session.get(Approval, approval_id)
    task = await session.get(Task, task_id)
    if task is None or approval is None or approval.task_id != task_id:
        raise HTTPException(status_code=404, detail="确认请求不存在")
    if approval.status != ApprovalStatus.PENDING:
        raise HTTPException(status_code=409, detail="确认请求已处理")
    next_sequence = await session.scalar(
        select(func.coalesce(func.max(TaskEvent.sequence), 0)).where(TaskEvent.task_id == task_id)
    )
    approval.status = ApprovalStatus.REJECTED
    approval.resolved_at = datetime.now(UTC)
    task.status = TaskStatus.CANCELED
    session.add(
        TaskEvent(
            task_id=task_id,
            sequence=int(next_sequence or 0) + 1,
            event_type="APPROVAL_REJECTED",
            summary="用户拒绝了外部操作，任务已停止",
            payload={"approvalId": str(approval.id)},
        )
    )
    await session.commit()
    resolved = await TaskService(session, redis).get(task_id)
    return TaskRead.model_validate(resolved)


@router.websocket("/{task_id}/events")
async def task_events(websocket: WebSocket, task_id: uuid.UUID) -> None:
    await websocket.accept()
    redis: Redis | None = websocket.app.state.redis
    if redis is None:
        await websocket.send_json({"type": "ERROR", "message": "事件总线不可用"})
        await websocket.close(code=1013)
        return
    pubsub = redis.pubsub()
    await pubsub.subscribe(task_channel(task_id))
    try:
        while True:
            message = await pubsub.get_message(ignore_subscribe_messages=True, timeout=20.0)
            if message:
                data = message["data"]
                await websocket.send_json(json.loads(data))
            else:
                await websocket.send_json({"type": "KEEPALIVE"})
            await asyncio.sleep(0.1)
    except WebSocketDisconnect:
        pass
    finally:
        await pubsub.unsubscribe(task_channel(task_id))
        await pubsub.aclose()
