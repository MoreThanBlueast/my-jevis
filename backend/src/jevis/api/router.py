from fastapi import APIRouter

from jevis.api.routes import devices, health, tasks

router = APIRouter()
router.include_router(health.router)
router.include_router(tasks.router)
router.include_router(devices.router)
