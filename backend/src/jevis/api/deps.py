from fastapi import Request
from redis.asyncio import Redis


def get_redis(request: Request) -> Redis | None:
    return request.app.state.redis
