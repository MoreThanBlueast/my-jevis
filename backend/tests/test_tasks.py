import uuid

import pytest


@pytest.mark.asyncio
async def test_create_is_idempotent_and_targets_qq_mail(client):
    key = f"test-{uuid.uuid4()}"
    payload = {
        "instruction": "使用 QQ 邮箱发送一封问候邮件",
        "recipient": "friend@example.com",
        "subject": "你好",
        "body": "你好，祝你今天愉快",
        "idempotency_key": key,
    }
    first = await client.post("/api/v1/tasks", json=payload)
    second = await client.post("/api/v1/tasks", json=payload)
    assert first.status_code == 201
    assert second.status_code == 201
    assert first.json()["id"] == second.json()["id"]
    assert first.json()["target_app"] == "com.tencent.androidqqmail"
    assert first.json()["status"] == "QUEUED"


@pytest.mark.asyncio
async def test_cancel_task(client):
    payload = {
        "instruction": "发送邮件",
        "recipient": "friend@example.com",
        "subject": "问候",
        "body": "今天好",
        "idempotency_key": f"test-{uuid.uuid4()}",
    }
    created = (await client.post("/api/v1/tasks", json=payload)).json()
    response = await client.post(f"/api/v1/tasks/{created['id']}/cancel")
    assert response.status_code == 200
    assert response.json()["status"] == "CANCELED"
    assert response.json()["events"][-1]["event_type"] == "TASK_CANCELED"


@pytest.mark.asyncio
async def test_reject_invalid_email(client):
    response = await client.post(
        "/api/v1/tasks",
        json={
            "instruction": "发送邮件",
            "recipient": "not-an-email",
            "subject": "问候",
            "body": "你好",
            "idempotency_key": "valid-key-001",
        },
    )
    assert response.status_code == 422
