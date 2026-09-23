from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=("../.env", ".env"), extra="ignore")

    app_name: str = "open-jevis Task Platform"
    environment: str = "development"
    api_prefix: str = "/api/v1"
    database_url: str = "postgresql+asyncpg://jevis:change-me@127.0.0.1:55432/jevis"
    redis_url: str = "redis://127.0.0.1:56379/0"
    device_gateway_token: str = "replace-with-a-long-random-token"
    qq_mail_package: str = "com.tencent.androidqqmail"
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com/anthropic"
    deepseek_model: str = "deepseek-flash"
    claude_agent_enabled: bool = False
    cors_origins: str = "http://localhost:3000"
    max_task_steps: int = Field(default=50, ge=1, le=200)

    @property
    def cors_origin_list(self) -> list[str]:
        return [item.strip() for item in self.cors_origins.split(",") if item.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
