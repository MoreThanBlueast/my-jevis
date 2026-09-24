"""Deterministic bounds around model decisions, not app-specific workflows."""


def completion_supported(arguments: dict, observation: dict, target_app: str) -> bool:
    """Require quoted UI evidence; this is grounding, not proof of remote delivery."""
    if observation.get("packageName") != target_app or observation.get("redacted"):
        return False
    evidence = arguments.get("evidence")
    if not isinstance(evidence, list) or not evidence or len(evidence) > 10:
        return False
    visible = [
        str(node.get(key, ""))
        for node in observation.get("nodes", [])
        for key in ("text", "description")
    ]
    return all(
        isinstance(quote, str) and len(quote.strip()) >= 2
        and any(quote in text for text in visible)
        for quote in evidence
    )


def recovery_allowed(action_type: str, consecutive_failures: int) -> bool:
    # A failed click/gesture may already have submitted something: never replay it.
    return action_type in {"OBSERVE", "WAIT", "SET_TEXT"} and consecutive_failures < 3
