import uuid

import pytest
from sqlalchemy import select

from jevis.db.session import SessionFactory
from jevis.models.approval import Approval
from jevis.models.command import DeviceCommand
from jevis.models.task import Task, TaskStatus


@pytest.mark.asyncio
async def test_approval_creates_exact_pending_device_command(client):
    created = (
        await client.post(
            "/api/v1/tasks",
            json={
                "instruction": "在便签中保存一条记录",
                "idempotency_key": f"approval-{uuid.uuid4()}",
            },
        )
    ).json()
    async with SessionFactory() as session:
        task = await session.get(Task, uuid.UUID(created["id"]))
        task.status = TaskStatus.WAITING_CONFIRMATION
        approval = Approval(
            task_id=task.id,
            action_summary="将内容保存到便签",
            action_payload={
                "action_type": "CLICK",
                "arguments": {"query": "保存"},
                "summary": "点击保存",
            },
        )
        session.add(approval)
        await session.commit()
        approval_id = approval.id

    response = await client.post(
        f"/api/v1/tasks/{created['id']}/approvals/{approval_id}/approve"
    )
    assert response.status_code == 200
    assert response.json()["status"] == "RUNNING"
    assert response.json()["approvals"][0]["status"] == "APPROVED"
    async with SessionFactory() as session:
        command = await session.scalar(select(DeviceCommand))
        assert command.action_type == "CLICK"
        assert command.arguments == {"query": "保存"}
