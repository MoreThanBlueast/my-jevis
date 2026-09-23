import asyncio
import json
import uuid

from fastapi import (
    APIRouter,
    Depends,
    HTTPException,
    WebSocket,
    WebSocketDisconnect,
    status,
)
from redis.asyncio import Redis
from sqlalchemy.ext.asyncio import AsyncSession

from jevis.api.deps import get_redis
from jevis.db.session import get_session
from jevis.models.task import TaskStatus
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
