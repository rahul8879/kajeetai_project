"""Application configuration helpers."""

from functools import lru_cache
from typing import Optional

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime settings loaded from environment variables or .env file."""

    app_name: str = Field(default="Kajeet AI Learning Buddy")
    environment: str = Field(default="local")
    llm_provider: str = Field(default="stub")
    llm_model: str = Field(default="gpt-4o-mini")
    enable_metrics: bool = Field(default=True)
    max_history_messages: int = Field(default=6)

    openai_api_key: Optional[str] = Field(default=None, env="OPENAI_API_KEY")
    openai_base_url: Optional[str] = Field(default=None, env="OPENAI_BASE_URL")
    openai_organization: Optional[str] = Field(default=None, env="OPENAI_ORGANIZATION")
    openai_temperature: float = Field(default=0.2, env="OPENAI_TEMPERATURE")

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
    )


@lru_cache()
def get_settings() -> Settings:
    """Return cached settings instance."""
    return Settings()
