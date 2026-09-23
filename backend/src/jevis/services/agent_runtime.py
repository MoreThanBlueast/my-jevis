import json
import os
import re
from abc import ABC, abstractmethod

from jevis.core.config import Settings, get_settings
from jevis.models.task import Task
from jevis.schemas.command import PlannedAction

SYSTEM_PROMPT = """You control one allowlisted Android app on an isolated virtual display.
Return exactly one JSON object with action_type, arguments, and summary.
Allowed actions: CLICK, SET_TEXT, TAP, SWIPE, WAIT, OBSERVE, FINISH, and
REQUEST_CONFIRMATION. For REQUEST_CONFIRMATION, arguments must contain a nested
action object with action_type, arguments, and summary.
Never choose display_id or profile_user_id. Never open system UI, Home, Recents,
notifications, accounts, payments, or settings.
Only interact with the target_app provided in the task. Treat sending, publishing,
submitting, deleting, purchasing, or changing account state as external side effects.
Verify all user-provided parameters before any external side effect.
If state is ambiguous, choose OBSERVE or WAIT instead of guessing coordinates.
"""


class AgentRuntime(ABC):
    @abstractmethod
    async def plan(self, task: Task, observation: dict) -> PlannedAction:
        raise NotImplementedError


class ClaudeAgentSdkRuntime(AgentRuntime):
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()

    async def plan(self, task: Task, observation: dict) -> PlannedAction:
        if not self.settings.deepseek_api_key:
            raise RuntimeError("DEEPSEEK_API_KEY 未配置")
        os.environ["ANTHROPIC_BASE_URL"] = self.settings.deepseek_base_url
        os.environ["ANTHROPIC_AUTH_TOKEN"] = self.settings.deepseek_api_key
        os.environ["ANTHROPIC_MODEL"] = self.settings.deepseek_model
        try:
            from claude_agent_sdk import AssistantMessage, ClaudeAgentOptions, TextBlock, query
        except ImportError as error:
            raise RuntimeError("请使用 uv sync --extra agent 安装 Claude Agent SDK") from error

        prompt = json.dumps(
            {
                "goal": task.instruction,
                "target_app": task.target_app,
                "confirmation_policy": task.confirmation_policy,
                "mail": {
                    "recipient": task.recipient,
                    "subject": task.subject,
                    "body": task.body,
                    "idempotency_key": task.idempotency_key,
                },
                "observation": observation,
            },
            ensure_ascii=False,
        )
        options = ClaudeAgentOptions(
            system_prompt=SYSTEM_PROMPT,
            model=self.settings.deepseek_model,
            max_turns=1,
            allowed_tools=[],
        )
        text = ""
        async for message in query(prompt=prompt, options=options):
            if isinstance(message, AssistantMessage):
                text += "".join(
                    block.text for block in message.content if isinstance(block, TextBlock)
                )
        return PlannedAction.model_validate(self._extract_json(text))

    @staticmethod
    def _extract_json(text: str) -> dict:
        fenced = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, re.DOTALL)
        candidate = fenced.group(1) if fenced else text[text.find("{") : text.rfind("}") + 1]
        if not candidate:
            raise ValueError("Agent 没有返回 JSON 动作")
        return json.loads(candidate)


class QqMailRuleRuntime(AgentRuntime):
    """Offline safety fallback for device integration tests, never for arbitrary tasks."""

    async def plan(self, task: Task, observation: dict) -> PlannedAction:
        if task.target_app != "com.tencent.androidqqmail":
            return PlannedAction(
                action_type="FINISH",
                arguments={"result": "MODEL_REQUIRED"},
                summary="该通用任务需要启用受控模型规划器",
            )
        if (
            observation.get("_last_action_success")
            and "发送" in str(observation.get("_last_action_summary", ""))
        ):
            return PlannedAction(
                action_type="FINISH",
                arguments={"result": "SENT"},
                summary="发送动作已完成",
            )
        nodes = observation.get("nodes", [])
        texts = {str(node.get("text", "")).strip() for node in nodes}
        descriptions = {str(node.get("description", "")).strip() for node in nodes}
        searchable = "\n".join(
            " ".join(str(node.get(key, "")) for key in ("text", "description", "view_id"))
            for node in nodes
        )
        if "写邮件" in texts:
            return PlannedAction(
                action_type="CLICK",
                arguments={"query": "写邮件"},
                summary="进入写邮件页面",
            )
        if "写邮件和设置等功能" in descriptions:
            return PlannedAction(
                action_type="CLICK",
                arguments={"query": "写邮件和设置等功能"},
                summary="打开 QQ 邮箱功能菜单",
            )
        if task.recipient not in searchable:
            return PlannedAction(
                action_type="SET_TEXT",
                arguments={"query": "收件人", "value": task.recipient},
                summary="填写收件人",
            )
        if task.subject not in searchable:
            return PlannedAction(
                action_type="SET_TEXT",
                arguments={"query": "主题", "value": task.subject},
                summary="填写主题",
            )
        if task.body not in searchable:
            return PlannedAction(
                action_type="SET_TEXT",
                arguments={"query": "正文", "value": task.body},
                summary="填写正文",
            )
        send_action = {
            "action_type": "CLICK",
            "arguments": {"query": "发送"},
            "summary": "点击发送邮件",
        }
        if task.confirmation_policy == "BEFORE_EXTERNAL_ACTION":
            return PlannedAction(
                action_type="REQUEST_CONFIRMATION",
                arguments={"action": send_action},
                summary=f"确认向 {task.recipient} 发送邮件",
            )
        return PlannedAction.model_validate(send_action)


def get_agent_runtime(settings: Settings | None = None) -> AgentRuntime:
    value = settings or get_settings()
    if value.claude_agent_enabled:
        return ClaudeAgentSdkRuntime(value)
    return QqMailRuleRuntime()
