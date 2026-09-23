import pytest
from pydantic import ValidationError

from jevis.models.task import Task
from jevis.schemas.command import PlannedAction
from jevis.services.agent_runtime import QqMailRuleRuntime


def task() -> Task:
    return Task(
        instruction="发送问候邮件",
        target_app="com.tencent.androidqqmail",
        recipient="friend@example.com",
        subject="你好",
        body="祝你今天愉快",
        confirmation_policy="BEFORE_EXTERNAL_ACTION",
        idempotency_key="agent-runtime-test",
    )


@pytest.mark.asyncio
async def test_rule_runtime_opens_compose_menu():
    action = await QqMailRuleRuntime().plan(
        task(),
        {"nodes": [{"description": "写邮件和设置等功能", "text": "", "view_id": ""}]},
    )
    assert action.action_type == "CLICK"
    assert action.arguments == {"query": "写邮件和设置等功能"}


@pytest.mark.asyncio
async def test_rule_runtime_requests_confirmation_before_send():
    mail = task()
    action = await QqMailRuleRuntime().plan(
        mail,
        {
            "nodes": [
                {
                    "text": f"{mail.recipient} {mail.subject} {mail.body}",
                    "description": "",
                    "view_id": "",
                }
            ]
        },
    )
    assert action.action_type == "REQUEST_CONFIRMATION"
    assert action.arguments["action"]["action_type"] == "CLICK"
    assert action.arguments["action"]["arguments"] == {"query": "发送"}


@pytest.mark.asyncio
async def test_rule_runtime_sends_only_when_preauthorized():
    mail = task()
    mail.confirmation_policy = "PREAUTHORIZED"
    action = await QqMailRuleRuntime().plan(
        mail,
        {
            "nodes": [
                {
                    "text": f"{mail.recipient} {mail.subject} {mail.body}",
                    "description": "",
                    "view_id": "",
                }
            ]
        },
    )
    assert action.action_type == "CLICK"
    assert action.arguments == {"query": "发送"}


def test_model_cannot_choose_display():
    with pytest.raises(ValidationError):
        PlannedAction(
            action_type="TAP",
            arguments={"x": 10, "y": 20, "display_id": 0},
            summary="unsafe",
        )
