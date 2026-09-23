from fastapi import APIRouter, Request
from sqlalchemy import text

from jevis.db.session import SessionFactory

router = APIRouter(tags=["health"])


@router.get("/health")
async def health(request: Request) -> dict:
    database = "down"
    redis_status = "disabled"
    try:
        async with SessionFactory() as session:
            await session.execute(text("select 1"))
        database = "up"
    except Exception:
        pass
    if request.app.state.redis is not None:
        try:
            await request.app.state.redis.ping()
            redis_status = "up"
        except Exception:
            redis_status = "down"
    return {
        "status": "ok" if database == "up" else "degraded",
        "database": database,
        "redis": redis_status,
    }
