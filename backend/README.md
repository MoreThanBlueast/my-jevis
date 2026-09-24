# open-jevis backend

FastAPI service for task lifecycle, event streaming, device registration and safe dispatch.

```bash
uv sync --dev
ADB_LAUNCH_ENABLED=true ADB_DEVICE_SERIAL=your-usb-serial ADB_DEVICE_ID=your-registered-device-id uv run uvicorn jevis.main:app --host 127.0.0.1 --port 18001
```

OpenAPI: <http://localhost:18001/docs>. Infrastructure is started from the repository root with `docker compose up -d`.

The local USB deployment requires `adb` on the API host and an authorized phone.
`ADB_LAUNCH_ENABLED=true` enables the authenticated, allowlisted display-launch bridge;
it accepts a task ID, never arbitrary commands. It refuses a target app currently used
on the physical screen. Keep this debug backend bound to localhost.

Create subsequent tasks from the phone's task list (+ → create and execute). Do not
requeue previous email tests manually. Keep AccessibilityService and the executor
running to receive non-focusable task-result overlays on the physical display.
