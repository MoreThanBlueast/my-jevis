import json
import uuid

from redis.asyncio import Redis


def task_channel(task_id: uuid.UUID | str) -> str:
    return f"task:{task_id}:events"


async def publish_event(redis: Redis | None, task_id: uuid.UUID, event: dict) -> None:
    if redis is not None:
        await redis.publish(task_channel(task_id), json.dumps(event, default=str))
