import os

os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///./test.db"
os.environ["REDIS_URL"] = "redis://localhost:6399/15"

import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from jevis.db.base import Base
from jevis.db.session import engine
from jevis.main import app


@pytest_asyncio.fixture(autouse=True)
async def clean_database():
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.drop_all)
        await connection.run_sync(Base.metadata.create_all)
    yield


@pytest_asyncio.fixture
async def client():
    app.state.redis = None
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as value:
        yield value
