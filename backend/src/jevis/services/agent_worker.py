import asyncio
import json
import uuid

from redis.asyncio import Redis
from sqlalchemy import func, select

from jevis.core.config import get_settings
from jevis.db.session import SessionFactory
from jevis.models.approval import Approval
from jevis.models.command import CommandStatus, DeviceCommand
from jevis.models.task import Task, TaskEvent, TaskStatus
from jevis.services.agent_runtime import get_agent_runtime


async def plan_task(task_id: uuid.UUID) -> None:
    async with SessionFactory() as session:
        task = await session.get(Task, task_id)
        if task is None or task.status not in {TaskStatus.RUNNING, TaskStatus.NEEDS_REVIEW}:
            return
        pending = await session.scalar(
            select(DeviceCommand).where(
                DeviceCommand.task_id == task_id,
                DeviceCommand.status.in_([CommandStatus.PENDING, CommandStatus.RUNNING]),
            )
        )
        if pending is not None:
            return
        observation_event = await session.scalar(
            select(TaskEvent)
            .where(
                TaskEvent.task_id == task_id,
                TaskEvent.event_type.in_(["OBSERVATION", "COMMAND_RESULT"]),
            )
            .order_by(TaskEvent.sequence.desc())
            .limit(1)
        )
        if observation_event is None:
            return
        observation = observation_event.payload.get("observation", observation_event.payload)
        if observation_event.event_type == "COMMAND_RESULT":
            observation = {
                **observation,
                "_last_action_type": observation_event.payload.get("actionType"),
                "_last_action_summary": observation_event.summary,
                "_last_action_success": observation_event.payload.get("success"),
            }
        action = await get_agent_runtime().plan(task, observation)
        if action.action_type == "REQUEST_CONFIRMATION":
            nested = action.arguments.get("action")
            if not isinstance(nested, dict):
                raise ValueError("确认请求缺少 action")
            session.add(
                Approval(
                    task_id=task_id,
                    action_summary=action.summary,
                    action_payload=nested,
                )
            )
            task.status = TaskStatus.WAITING_CONFIRMATION
            next_event_sequence = await session.scalar(
                select(func.coalesce(func.max(TaskEvent.sequence), 0)).where(
                    TaskEvent.task_id == task_id
                )
            )
            session.add(
                TaskEvent(
                    task_id=task_id,
                    sequence=int(next_event_sequence or 0) + 1,
                    event_type="APPROVAL_REQUIRED",
                    summary=action.summary,
                    payload={"action": nested},
                )
            )
            await session.commit()
            return
        next_sequence = await session.scalar(
            select(func.coalesce(func.max(DeviceCommand.sequence), 0)).where(
                DeviceCommand.task_id == task_id
            )
        )
        command = DeviceCommand(
            task_id=task_id,
            sequence=int(next_sequence or 0) + 1,
            action_type=action.action_type,
            arguments=action.arguments,
            summary=action.summary,
        )
        session.add(command)
        task.status = TaskStatus.RUNNING
        await session.commit()


async def run() -> None:
    settings = get_settings()
    redis = Redis.from_url(settings.redis_url, decode_responses=True, socket_timeout=None)
    try:
        while True:
            item = await redis.blpop("jevis:agent_queue", timeout=10)
            if item is None:
                continue
            _, raw = item
            try:
                message = json.loads(raw)
                await plan_task(uuid.UUID(message["task_id"]))
            except Exception as error:
                dead_letter = json.dumps({"raw": raw, "error": str(error)})
                await redis.rpush("jevis:agent_dead_letter", dead_letter)
    finally:
        await redis.aclose()


if __name__ == "__main__":
    asyncio.run(run())
