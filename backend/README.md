# open-jevis backend

FastAPI service for task lifecycle, event streaming, device registration and safe dispatch.

```bash
uv sync --dev
uv run uvicorn jevis.main:app --reload
```

OpenAPI: <http://localhost:8000/docs>. Infrastructure is started from the repository root with `docker compose up -d`.
