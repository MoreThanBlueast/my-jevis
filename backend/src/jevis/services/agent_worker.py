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
from jevis.schemas.command import PlannedAction
from jevis.services.agent_runtime import get_agent_runtime
from jevis.services.execution_policy import completion_supported


async def plan_task(task_id: uuid.UUID, queued_observation: dict | None = None) -> None:
    async with SessionFactory() as session:
        task = await session.get(Task, task_id)
        if task is None or task.status != TaskStatus.RUNNING:
            return
        pending = await session.scalar(
            select(DeviceCommand).where(
                DeviceCommand.task_id == task_id,
                DeviceCommand.status.in_([CommandStatus.PENDING, CommandStatus.RUNNING]),
            )
        )
        if pending is not None:
            return
        observation_event = None
        if queued_observation is None:
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
        else:
            observation = queued_observation
        if observation_event is not None and observation_event.event_type == "COMMAND_RESULT":
            observation = {
                **observation,
                "_last_action_type": observation_event.payload.get("actionType"),
                "_last_action_summary": observation_event.summary,
                "_last_action_success": observation_event.payload.get("success"),
            }
        history = list((await session.scalars(
            select(DeviceCommand).where(DeviceCommand.task_id == task_id)
            .order_by(DeviceCommand.sequence.desc()).limit(20)
        )).all())
        if history and history[0].sequence >= get_settings().max_task_steps:
            task.status = TaskStatus.NEEDS_REVIEW
            await session.commit()
            return
        observation = {**observation, "_history": [
            {"sequence": item.sequence, "action_type": item.action_type,
             "arguments": item.arguments, "summary": item.summary, "status": item.status}
            for item in reversed(history)
        ]}
        action = await asyncio.wait_for(get_agent_runtime().plan(task, observation), timeout=120)
        await session.refresh(task)
        if task.status != TaskStatus.RUNNING:
            return
        if action.action_type == "FINISH" and not completion_supported(
            action.arguments, observation, task.target_app
        ):
            action = PlannedAction(
                action_type="OBSERVE", arguments={},
                summary="完成证据不足，重新观察并核验任务结果",
            )
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
                await plan_task(uuid.UUID(message["task_id"]), message.get("observation"))
            except Exception as error:
                try:
                    task_id = json.loads(raw).get("task_id")
                except (json.JSONDecodeError, AttributeError):
                    task_id = None
                dead_letter = json.dumps(
                    {"task_id": task_id, "error": str(error)[:500]},
                    ensure_ascii=False,
                )
                await redis.rpush("jevis:agent_dead_letter", dead_letter)
                if task_id:
                    async with SessionFactory() as session:
                        task = await session.get(Task, uuid.UUID(task_id))
                        if task is not None and task.status == TaskStatus.RUNNING:
                            task.status = TaskStatus.NEEDS_REVIEW
                            sequence = await session.scalar(select(
                                func.coalesce(func.max(TaskEvent.sequence), 0)
                            ).where(TaskEvent.task_id == task.id))
                            session.add(TaskEvent(
                                task_id=task.id, sequence=int(sequence or 0) + 1,
                                event_type="PLANNER_FAILED",
                                summary="模型规划失败或超时，任务已暂停；请检查后端日志后重试",
                                payload={},
                            ))
                            await session.commit()
    finally:
        await redis.aclose()


if __name__ == "__main__":
    asyncio.run(run())
