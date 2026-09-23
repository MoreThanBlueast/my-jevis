import uuid

from redis.asyncio import Redis
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from jevis.core.config import get_settings
from jevis.models.task import Task, TaskEvent, TaskStatus
from jevis.schemas.task import TaskCreate
from jevis.services.events import publish_event

TERMINAL_STATUSES = {
    TaskStatus.SUCCEEDED,
    TaskStatus.FAILED,
    TaskStatus.CANCELED,
    TaskStatus.EXPIRED,
}


class TaskService:
    def __init__(self, session: AsyncSession, redis: Redis | None) -> None:
        self.session = session
        self.redis = redis
        self.settings = get_settings()

    async def create(self, payload: TaskCreate) -> Task:
        existing = await self.session.scalar(
            select(Task)
            .where(Task.idempotency_key == payload.idempotency_key)
            .options(selectinload(Task.events))
        )
        if existing:
            return existing

        task = Task(
            instruction=payload.instruction,
            target_app=self.settings.qq_mail_package,
            recipient=str(payload.recipient),
            subject=payload.subject,
            body=payload.body,
            device_id=payload.device_id,
            idempotency_key=payload.idempotency_key,
            status=TaskStatus.QUEUED,
        )
        self.session.add(task)
        await self.session.flush()
        event = TaskEvent(
            task_id=task.id,
            sequence=1,
            event_type="TASK_QUEUED",
            summary="QQ 邮箱任务已进入队列",
            payload={"targetApp": task.target_app},
        )
        self.session.add(event)
        await self.session.commit()
        if self.redis is not None:
            await self.redis.rpush("jevis:task_queue", str(task.id))
        await self.session.refresh(task, attribute_names=["events"])
        await publish_event(self.redis, task.id, self._event_dict(event))
        return task

    async def list(self, status: TaskStatus | None = None) -> list[Task]:
        query = select(Task).options(selectinload(Task.events)).order_by(Task.created_at.desc())
        if status:
            query = query.where(Task.status == status)
        return list((await self.session.scalars(query)).unique().all())

    async def get(self, task_id: uuid.UUID) -> Task | None:
        return await self.session.scalar(
            select(Task).where(Task.id == task_id).options(selectinload(Task.events))
        )

    async def cancel(self, task: Task) -> Task:
        if task.status in TERMINAL_STATUSES:
            return task
        task.status = TaskStatus.CANCELED
        sequence = await self._next_sequence(task.id)
        event = TaskEvent(
            task_id=task.id,
            sequence=sequence,
            event_type="TASK_CANCELED",
            summary="用户已取消任务，本地执行器必须停止接受新动作",
            payload={},
        )
        self.session.add(event)
        await self.session.commit()
        await self.session.refresh(task, attribute_names=["events"])
        await publish_event(self.redis, task.id, self._event_dict(event))
        if self.redis is not None:
            message = f'{{"type":"cancel","taskId":"{task.id}"}}'
            await self.redis.publish("jevis:device:control", message)
        return task

    async def _next_sequence(self, task_id: uuid.UUID) -> int:
        value = await self.session.scalar(
            select(func.coalesce(func.max(TaskEvent.sequence), 0)).where(
                TaskEvent.task_id == task_id
            )
        )
        return int(value or 0) + 1

    @staticmethod
    def _event_dict(event: TaskEvent) -> dict:
        return {
            "id": str(event.id),
            "sequence": event.sequence,
            "eventType": event.event_type,
            "summary": event.summary,
            "payload": event.payload,
            "createdAt": event.created_at,
        }
