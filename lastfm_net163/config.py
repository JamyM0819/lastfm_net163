from __future__ import annotations

import json
import os
import tomllib
from dataclasses import dataclass
from pathlib import Path

DEFAULT_DIR = (
    Path(os.environ.get("APPDATA", str(Path.home() / "AppData" / "Roaming")))
    / "lastfm_net163"
)
DEFAULT_PATH = DEFAULT_DIR / "config.toml"
DEFAULT_MATCH_KEYWORDS = ("cloudmusic", "netease")


@dataclass
class Config:
    api_key: str = ""
    api_secret: str = ""
    session_key: str = ""
    match_keywords: tuple[str, ...] = DEFAULT_MATCH_KEYWORDS


def _default_toml() -> str:
    return (
        "# 网易云 → last.fm 桥接配置\n"
        "# 在 https://www.last.fm/api/account/create 免费申请 api_key / api_secret\n"
        'api_key = ""\n'
        'api_secret = ""\n'
        'session_key = ""\n'
        'match_keywords = ["cloudmusic", "netease"]\n'
    )


def ensure_config(path: Path = DEFAULT_PATH) -> Path:
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(_default_toml(), encoding="utf-8")
    return path


def load_config(path: Path = DEFAULT_PATH) -> Config:
    path = ensure_config(path)
    with path.open("rb") as f:
        data = tomllib.load(f)
    return Config(
        api_key=data.get("api_key", ""),
        api_secret=data.get("api_secret", ""),
        session_key=data.get("session_key", ""),
        match_keywords=tuple(data.get("match_keywords", DEFAULT_MATCH_KEYWORDS)),
    )


def save_config(config: Config, path: Path = DEFAULT_PATH) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        f"api_key = {json.dumps(config.api_key)}",
        f"api_secret = {json.dumps(config.api_secret)}",
        f"session_key = {json.dumps(config.session_key)}",
        f"match_keywords = {json.dumps(list(config.match_keywords))}",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
