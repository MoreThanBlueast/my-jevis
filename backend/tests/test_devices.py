import uuid

import pytest

from jevis.core.config import get_settings


def auth() -> dict[str, str]:
    return {"Authorization": f"Bearer {get_settings().device_gateway_token}"}


@pytest.mark.asyncio
async def test_device_cannot_claim_without_isolated_display(client):
    registration = {
        "device_id": "xiaomi-15-ultra-01",
        "model": "Xiaomi 15 Ultra",
        "android_version": "15",
        "virtual_display_ready": False,
        "directed_input_ready": False,
    }
    registration_response = await client.post(
        "/api/v1/devices/register", json=registration, headers=auth()
    )
    assert registration_response.status_code == 200
    response = await client.post("/api/v1/devices/xiaomi-15-ultra-01/next-task", headers=auth())
    assert response.status_code == 409


@pytest.mark.asyncio
async def test_executor_event_from_display_zero_is_rejected(client):
    response = await client.post(
        "/api/v1/devices/xiaomi-15-ultra-01/tasks/00000000-0000-0000-0000-000000000000/events",
        headers=auth(),
        json={
            "sequence": 1,
            "event_type": "ACTION_COMPLETED",
            "summary": "unsafe",
            "display_id": 0,
            "profile_user_id": 10,
        },
    )
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_ready_device_claims_qq_mail_task(client):
    device_id = "xiaomi-15-ultra-02"
    registration = {
        "device_id": device_id,
        "model": "Xiaomi 15 Ultra",
        "android_version": "15",
        "hyperos_version": "2",
        "display_id": 4,
        "profile_user_id": 10,
        "virtual_display_ready": True,
        "directed_input_ready": True,
    }
    await client.post("/api/v1/devices/register", json=registration, headers=auth())
    await client.post(
        "/api/v1/tasks",
        json={
            "instruction": "使用 QQ 邮箱发送问候",
            "recipient": "friend@example.com",
            "subject": "你好",
            "body": "祝你今天愉快",
            "device_id": device_id,
            "idempotency_key": f"claim-{uuid.uuid4()}",
        },
    )
    response = await client.post(f"/api/v1/devices/{device_id}/next-task", headers=auth())
    assert response.status_code == 200
    result = response.json()
    assert result["target_app"] == "com.tencent.androidqqmail"
    assert result["required_display_id"] == 4
    assert result["required_profile_user_id"] == 10
