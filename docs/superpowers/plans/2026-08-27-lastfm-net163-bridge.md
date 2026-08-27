# 网易云音乐 → last.fm 桥接程序 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个 Windows 命令行常驻程序，读取网易云音乐桌面客户端的正在播放信息，按 last.fm 标准自动 scrobble 到用户账号。

**Architecture:** 程序分 5 个模块：`config.py` 管理 `%APPDATA%\lastfm_net163\config.toml`；`smtc.py` 通过 Windows 系统媒体会话（SMTC）读取网易云当前曲目；`scrobbler.py` 是达标状态机；`lastfm.py` 负责 last.fm 授权与提交；`main.py` 组合成常驻轮询循环（每 2 秒一次）。

**Tech Stack:** Python 3.11、`winsdk`（Windows Runtime 媒体会话）、`requests`（last.fm API）、`pytest`（测试）、TOML（`tomllib` 标准库）。

---

### Task 1: 项目脚手架与依赖

**Files:**
- Create: `requirements.txt`
- Create: `requirements-dev.txt`
- Create: `.gitignore`
- Create: `lastfm_net163/__init__.py`
- Create: `tests/__init__.py`

- [ ] **Step 1: 创建 `requirements.txt`**

```text
winsdk
requests
```

- [ ] **Step 2: 创建 `requirements-dev.txt`**

```text
-r requirements.txt
pytest
```

- [ ] **Step 3: 创建 `.gitignore`**

```text
.venv/
__pycache__/
*.pyc
.pytest_cache/
```

- [ ] **Step 4: 创建包和测试目录的 `__init__.py`**

创建 `lastfm_net163/__init__.py`：

```python
"""网易云音乐 → last.fm 桥接程序。"""
```

创建 `tests/__init__.py`（空文件即可）。

- [ ] **Step 5: 创建虚拟环境并安装依赖**

Run:

```bash
py -3.11 -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements-dev.txt
```

Expected: 安装成功，无报错。

- [ ] **Step 6: 验证依赖可导入**

Run:

```bash
.venv/Scripts/python.exe -c "import winsdk, requests; print('ok')"
```

Expected: 输出 `ok`。

- [ ] **Step 7: Commit**

```bash
git add requirements.txt requirements-dev.txt .gitignore lastfm_net163/__init__.py tests/__init__.py
git commit -m "chore: 初始化项目脚手架与依赖"
```

---

### Task 2: 配置模块 `config.py`

**Files:**
- Create: `tests/test_config.py`
- Create: `lastfm_net163/config.py`

- [ ] **Step 1: 写失败测试 `tests/test_config.py`**

```python
from lastfm_net163.config import Config, ensure_config, load_config, save_config


def test_ensure_config_creates_default_file(tmp_path):
    path = tmp_path / "config.toml"
    assert not path.exists()
    result = ensure_config(path)
    assert result == path
    assert path.exists()


def test_load_config_reads_values(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text(
        'api_key = "key1"\n'
        'api_secret = "secret1"\n'
        'session_key = "sk1"\n'
        'match_keywords = ["cloudmusic"]\n',
        encoding="utf-8",
    )
    config = load_config(path)
    assert config.api_key == "key1"
    assert config.api_secret == "secret1"
    assert config.session_key == "sk1"
    assert config.match_keywords == ("cloudmusic",)


def test_save_then_load_roundtrip(tmp_path):
    path = tmp_path / "config.toml"
    config = Config(
        api_key="key2",
        api_secret="secret2",
        session_key="sk2",
        match_keywords=("netease", "cloudmusic"),
    )
    save_config(config, path)
    loaded = load_config(path)
    assert loaded == config
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
.venv/Scripts/python.exe -m pytest tests/test_config.py -v
```

Expected: FAIL，报 `ModuleNotFoundError: No module named 'lastfm_net163.config'`。

- [ ] **Step 3: 实现 `lastfm_net163/config.py`**

```python
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
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
.venv/Scripts/python.exe -m pytest tests/test_config.py -v
```

Expected: 3 passed。

- [ ] **Step 5: Commit**

```bash
git add tests/test_config.py lastfm_net163/config.py
git commit -m "feat: 新增配置读写模块"
```

---

### Task 3: 达标状态机 `scrobbler.py`

**Files:**
- Create: `tests/test_scrobbler.py`
- Create: `lastfm_net163/scrobbler.py`

- [ ] **Step 1: 写失败测试 `tests/test_scrobbler.py`**

```python
from lastfm_net163.scrobbler import ScrobbleTracker, Track


def make_track(
    title="Song",
    artist="Artist",
    album="Album",
    duration=200,
    position=100,
    playing=True,
):
    return Track(
        title=title,
        artist=artist,
        album=album,
        duration_seconds=duration,
        position_seconds=position,
        is_playing=playing,
    )


def test_short_track_not_scrobbled():
    assert ScrobbleTracker().on_track(make_track(duration=20, position=10)) is False


def test_half_played_scrobbles():
    assert ScrobbleTracker().on_track(make_track(duration=200, position=100)) is True


def test_four_minutes_scrobbles_even_before_half():
    assert ScrobbleTracker().on_track(make_track(duration=600, position=240)) is True


def test_under_half_not_scrobbled():
    assert ScrobbleTracker().on_track(make_track(duration=200, position=99)) is False


def test_same_track_only_scrobbled_once():
    tracker = ScrobbleTracker()
    assert tracker.on_track(make_track(duration=200, position=100)) is True
    assert tracker.on_track(make_track(duration=200, position=150)) is False


def test_track_change_resets():
    tracker = ScrobbleTracker()
    assert tracker.on_track(make_track(title="A", duration=200, position=100)) is True
    assert tracker.on_track(make_track(title="B", duration=200, position=100)) is True


def test_paused_does_not_scrobble():
    assert ScrobbleTracker().on_track(make_track(playing=False)) is False


def test_none_track_resets_state():
    tracker = ScrobbleTracker()
    tracker.on_track(make_track(duration=200, position=100))
    assert tracker.on_track(None) is False
    assert tracker.on_track(make_track(duration=200, position=100)) is True
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
.venv/Scripts/python.exe -m pytest tests/test_scrobbler.py -v
```

Expected: FAIL，报 `ModuleNotFoundError: No module named 'lastfm_net163.scrobbler'`。

- [ ] **Step 3: 实现 `lastfm_net163/scrobbler.py`**

```python
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Track:
    title: str
    artist: str
    album: str
    duration_seconds: int
    position_seconds: int
    is_playing: bool

    @property
    def key(self) -> tuple[str, str, str]:
        return (
            self.title.strip().lower(),
            self.artist.strip().lower(),
            self.album.strip().lower(),
        )


class ScrobbleTracker:
    def __init__(
        self,
        min_duration_seconds: int = 30,
        min_ratio: float = 0.5,
        min_position_seconds: int = 240,
    ) -> None:
        self.min_duration_seconds = min_duration_seconds
        self.min_ratio = min_ratio
        self.min_position_seconds = min_position_seconds
        self._current_key: tuple[str, str, str] | None = None
        self._scrobbled = False

    def on_track(self, track: Track | None) -> bool:
        if track is None:
            self._current_key = None
            self._scrobbled = False
            return False

        if track.key != self._current_key:
            self._current_key = track.key
            self._scrobbled = False

        if self._scrobbled or not track.is_playing:
            return False

        if track.duration_seconds > 0 and track.duration_seconds < self.min_duration_seconds:
            return False

        if track.duration_seconds > 0:
            eligible = (
                track.position_seconds >= track.duration_seconds * self.min_ratio
                or track.position_seconds >= self.min_position_seconds
            )
        else:
            eligible = track.position_seconds >= self.min_position_seconds

        if not eligible:
            return False

        self._scrobbled = True
        return True
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
.venv/Scripts/python.exe -m pytest tests/test_scrobbler.py -v
```

Expected: 8 passed。

- [ ] **Step 5: Commit**

```bash
git add tests/test_scrobbler.py lastfm_net163/scrobbler.py
git commit -m "feat: 新增 scrobble 达标状态机"
```

---

### Task 4: last.fm 客户端 `lastfm.py`

**Files:**
- Create: `tests/test_lastfm.py`
- Create: `lastfm_net163/lastfm.py`

- [ ] **Step 1: 写失败测试 `tests/test_lastfm.py`**

```python
import hashlib

import requests

from lastfm_net163.lastfm import LastfmClient, LastfmError


class FakeResp:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


def test_sign_is_md5_of_sorted_params_plus_secret():
    client = LastfmClient(api_key="key", api_secret="secret")
    assert client.sign({"b": "2", "a": "1"}) == hashlib.md5(
        b"a1b2secret"
    ).hexdigest()


def test_auth_url_contains_key_and_token():
    client = LastfmClient(api_key="key", api_secret="secret")
    url = client.auth_url("tok123")
    assert "api_key=key" in url
    assert "token=tok123" in url


def test_get_token(monkeypatch):
    client = LastfmClient(api_key="key", api_secret="secret")

    def fake_get(url, params=None, timeout=None):
        assert params["method"] == "auth.gettoken"
        assert params["api_key"] == "key"
        assert "api_sig" in params
        return FakeResp({"token": "tok123"})

    monkeypatch.setattr(requests, "get", fake_get)
    assert client.get_token() == "tok123"


def test_get_session(monkeypatch):
    client = LastfmClient(api_key="key", api_secret="secret")

    def fake_get(url, params=None, timeout=None):
        assert params["method"] == "auth.getsession"
        assert params["token"] == "tok123"
        return FakeResp({"session": {"name": "jamy", "key": "sk123"}})

    monkeypatch.setattr(requests, "get", fake_get)
    session = client.get_session("tok123")
    assert session.session_key == "sk123"
    assert session.username == "jamy"


def test_scrobble_posts_signed_params(monkeypatch):
    client = LastfmClient(api_key="key", api_secret="secret", session_key="sk1")
    captured = {}

    def fake_post(url, data=None, timeout=None):
        captured["data"] = dict(data)
        return FakeResp({"scrobbles": {"@attr": {"accepted": 1}}})

    monkeypatch.setattr(requests, "post", fake_post)
    client.scrobble("Artist", "Title", album="Album", timestamp=1700000000)

    data = captured["data"]
    assert data["method"] == "track.scrobble"
    assert data["artist"] == "Artist"
    assert data["track"] == "Title"
    assert data["album"] == "Album"
    assert data["timestamp"] == "1700000000"
    assert data["sk"] == "sk1"
    expected_sig = client.sign(
        {k: v for k, v in data.items() if k not in ("api_sig", "format")}
    )
    assert data["api_sig"] == expected_sig


def test_error_response_raises(monkeypatch):
    client = LastfmClient(api_key="key", api_secret="secret")

    def fake_get(url, params=None, timeout=None):
        return FakeResp({"error": 8, "message": "Operation failed"})

    monkeypatch.setattr(requests, "get", fake_get)
    try:
        client.get_token()
    except LastfmError as exc:
        assert "8" in str(exc)
    else:
        raise AssertionError("expected LastfmError")
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
.venv/Scripts/python.exe -m pytest tests/test_lastfm.py -v
```

Expected: FAIL，报 `ModuleNotFoundError: No module named 'lastfm_net163.lastfm'`。

- [ ] **Step 3: 实现 `lastfm_net163/lastfm.py`**

```python
from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass

import requests

API_ROOT = "https://ws.audioscrobbler.com/2.0/"
AUTH_URL = "https://www.last.fm/api/auth/"


class LastfmError(RuntimeError):
    pass


@dataclass
class SessionInfo:
    session_key: str
    username: str


class LastfmClient:
    def __init__(
        self,
        api_key: str,
        api_secret: str,
        session_key: str = "",
        timeout: float = 10.0,
    ) -> None:
        self.api_key = api_key
        self.api_secret = api_secret
        self.session_key = session_key
        self.timeout = timeout

    def sign(self, params: dict[str, str]) -> str:
        payload = "".join(f"{k}{params[k]}" for k in sorted(params))
        return hashlib.md5((payload + self.api_secret).encode("utf-8")).hexdigest()

    def _call(self, params: dict[str, str], method: str = "GET") -> dict:
        params = {**params, "api_key": self.api_key, "format": "json"}
        params["api_sig"] = self.sign(
            {k: v for k, v in params.items() if k not in ("api_sig", "format")}
        )
        if method == "GET":
            resp = requests.get(API_ROOT, params=params, timeout=self.timeout)
        else:
            resp = requests.post(API_ROOT, data=params, timeout=self.timeout)
        resp.raise_for_status()
        data = resp.json()
        if data.get("error"):
            raise LastfmError(
                f"last.fm error {data['error']}: {data.get('message', '')}"
            )
        return data

    def get_token(self) -> str:
        data = self._call({"method": "auth.gettoken"})
        return data["token"]

    def auth_url(self, token: str) -> str:
        return f"{AUTH_URL}?api_key={self.api_key}&token={token}"

    def get_session(self, token: str) -> SessionInfo:
        data = self._call({"method": "auth.getsession", "token": token})
        session = data["session"]
        return SessionInfo(session_key=session["key"], username=session["name"])

    def scrobble(
        self,
        artist: str,
        title: str,
        album: str = "",
        timestamp: int | None = None,
    ) -> dict:
        params = {
            "method": "track.scrobble",
            "artist": artist,
            "track": title,
            "timestamp": str(timestamp if timestamp is not None else int(time.time())),
            "sk": self.session_key,
        }
        if album:
            params["album"] = album
        return self._call(params, method="POST")
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
.venv/Scripts/python.exe -m pytest tests/test_lastfm.py -v
```

Expected: 6 passed。

- [ ] **Step 5: Commit**

```bash
git add tests/test_lastfm.py lastfm_net163/lastfm.py
git commit -m "feat: 新增 last.fm 客户端与签名逻辑"
```

---

### Task 5: SMTC 读取模块 `smtc.py`

**Files:**
- Create: `tests/test_smtc.py`
- Create: `lastfm_net163/smtc.py`

- [ ] **Step 1: 写失败测试 `tests/test_smtc.py`**

```python
import asyncio
from datetime import timedelta

from lastfm_net163.scrobbler import Track
from lastfm_net163.smtc import SmtcListener


class FakeTimeline:
    def __init__(self, position_seconds, end_seconds):
        self.position = timedelta(seconds=position_seconds)
        self.end_time = timedelta(seconds=end_seconds)


class FakeInfo:
    def __init__(self, status):
        self.playback_status = status


class FakeMedia:
    def __init__(self, title, artist, album):
        self.title = title
        self.artist = artist
        self.album_title = album


class FakeSession:
    def __init__(self, aumid, media, info, timeline):
        self.source_app_user_model_id = aumid
        self._media = media
        self._info = info
        self._timeline = timeline

    async def try_get_media_properties_async(self):
        return self._media

    def get_playback_info(self):
        return self._info

    def get_timeline_properties(self):
        return self._timeline


class FakeManager:
    def __init__(self, sessions):
        self._sessions = sessions

    def get_sessions(self):
        return self._sessions


def make_session(aumid="CloudMusic.exe"):
    return FakeSession(
        aumid,
        FakeMedia("Song", "Artist", "Album"),
        FakeInfo(4),  # 4 = PLAYING in the real enum
        FakeTimeline(75, 200),
    )


def test_find_session_matches_netease_keyword():
    listener = SmtcListener(match_keywords=("cloudmusic", "netease"))
    netease = make_session("CloudMusic.exe")
    other = make_session("Spotify.exe")
    manager = FakeManager([other, netease])
    assert listener.find_session(manager) is netease


def test_find_session_ignores_non_matching():
    listener = SmtcListener(match_keywords=("cloudmusic", "netease"))
    manager = FakeManager([make_session("Spotify.exe")])
    assert listener.find_session(manager) is None


def test_read_track_extracts_metadata():
    session = make_session("CloudMusic.exe")
    track = asyncio.run(SmtcListener().read_track(session))
    assert track == Track(
        title="Song",
        artist="Artist",
        album="Album",
        duration_seconds=200,
        position_seconds=75,
        is_playing=True,
    )


def test_read_track_returns_none_without_metadata():
    session = FakeSession(
        "CloudMusic.exe",
        FakeMedia("", "", ""),
        FakeInfo(4),
        FakeTimeline(0, 0),
    )
    assert asyncio.run(SmtcListener().read_track(session)) is None
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
.venv/Scripts/python.exe -m pytest tests/test_smtc.py -v
```

Expected: FAIL，报 `ModuleNotFoundError: No module named 'lastfm_net163.smtc'`。

- [ ] **Step 3: 实现 `lastfm_net163/smtc.py`**

```python
from __future__ import annotations

from winsdk.windows.media.control import (
    GlobalSystemMediaTransportControlsSessionManager as SessionManager,
)
from winsdk.windows.media.control import (
    GlobalSystemMediaTransportControlsSessionPlaybackStatus as PlaybackStatus,
)

from .scrobbler import Track


def _timedelta_to_seconds(td) -> int:
    if td is None:
        return 0
    return int(td.total_seconds())


class SmtcListener:
    def __init__(self, match_keywords: tuple[str, ...] = ("cloudmusic", "netease")) -> None:
        self.match_keywords = tuple(keyword.lower() for keyword in match_keywords)

    def _matches(self, aumid: str) -> bool:
        lowered = (aumid or "").lower()
        return any(keyword in lowered for keyword in self.match_keywords)

    async def get_manager(self):
        return await SessionManager.request_async()

    def find_session(self, manager):
        for session in manager.get_sessions():
            if self._matches(session.source_app_user_model_id):
                return session
        return None

    async def read_track(self, session) -> Track | None:
        media = await session.try_get_media_properties_async()
        info = session.get_playback_info()
        timeline = session.get_timeline_properties()

        title = media.title or ""
        artist = media.artist or ""
        album = media.album_title or ""
        if not title and not artist:
            return None

        return Track(
            title=title,
            artist=artist,
            album=album,
            duration_seconds=_timedelta_to_seconds(timeline.end_time),
            position_seconds=_timedelta_to_seconds(timeline.position),
            is_playing=info.playback_status == PlaybackStatus.PLAYING,
        )
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
.venv/Scripts/python.exe -m pytest tests/test_smtc.py -v
```

Expected: 4 passed。

- [ ] **Step 5: Commit**

```bash
git add tests/test_smtc.py lastfm_net163/smtc.py
git commit -m "feat: 新增 Windows SMTC 正在播放读取模块"
```

---

### Task 6: 主程序 `main.py`

**Files:**
- Create: `tests/test_main.py`
- Create: `lastfm_net163/main.py`

- [ ] **Step 1: 写失败测试 `tests/test_main.py`**

```python
import asyncio

from lastfm_net163.main import run_once
from lastfm_net163.scrobbler import ScrobbleTracker, Track


class FakeListener:
    def __init__(self, session, track):
        self._session = session
        self._track = track

    async def get_manager(self):
        return object()

    def find_session(self, manager):
        return self._session

    async def read_track(self, session):
        return self._track


class FakeClient:
    def __init__(self):
        self.scrobbles = []

    def scrobble(self, artist, title, album, timestamp=None):
        self.scrobbles.append((artist, title, album))


def test_run_once_scrobbles_eligible_track():
    track = Track(
        title="Title",
        artist="Artist",
        album="Album",
        duration_seconds=200,
        position_seconds=100,
        is_playing=True,
    )
    listener = FakeListener(object(), track)
    tracker = ScrobbleTracker()
    client = FakeClient()

    asyncio.run(run_once(listener, tracker, client))

    assert client.scrobbles == [("Artist", "Title", "Album")]


def test_run_once_without_session_resets_tracker():
    listener = FakeListener(None, None)
    tracker = ScrobbleTracker()
    client = FakeClient()

    asyncio.run(run_once(listener, tracker, client))

    assert client.scrobbles == []


def test_run_once_does_not_scrobble_before_threshold():
    track = Track(
        title="Title",
        artist="Artist",
        album="Album",
        duration_seconds=200,
        position_seconds=99,
        is_playing=True,
    )
    listener = FakeListener(object(), track)
    tracker = ScrobbleTracker()
    client = FakeClient()

    asyncio.run(run_once(listener, tracker, client))

    assert client.scrobbles == []
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
.venv/Scripts/python.exe -m pytest tests/test_main.py -v
```

Expected: FAIL，报 `ModuleNotFoundError: No module named 'lastfm_net163.main'`。

- [ ] **Step 3: 实现 `lastfm_net163/main.py`**

```python
from __future__ import annotations

import asyncio
import sys
import time
import webbrowser

from .config import Config, ensure_config, load_config, save_config
from .lastfm import LastfmClient, LastfmError
from .scrobbler import ScrobbleTracker, Track
from .smtc import SmtcListener

POLL_SECONDS = 2.0
AUTH_POLL_SECONDS = 2.0
AUTH_TIMEOUT_SECONDS = 300


async def authorize(client: LastfmClient) -> str:
    token = client.get_token()
    url = client.auth_url(token)
    print(f"请在浏览器中授权 last.fm：\n{url}")
    webbrowser.open(url)
    deadline = time.monotonic() + AUTH_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        await asyncio.sleep(AUTH_POLL_SECONDS)
        try:
            session = client.get_session(token)
            print(f"授权成功：{session.username}")
            return session.session_key
        except LastfmError:
            continue
    raise LastfmError("等待 last.fm 授权超时")


async def run_once(
    listener: SmtcListener,
    tracker: ScrobbleTracker,
    client: LastfmClient,
) -> Track | None:
    manager = await listener.get_manager()
    session = listener.find_session(manager)
    if session is None:
        tracker.on_track(None)
        return None

    track = await listener.read_track(session)
    if tracker.on_track(track):
        assert track is not None
        client.scrobble(track.artist, track.title, track.album)
        print(f"已 scrobble：{track.artist} - {track.title}")
    return track


async def amain() -> int:
    config_path = ensure_config()
    config = load_config(config_path)
    if not config.api_key or not config.api_secret:
        print(f"请先填写配置：{config_path}")
        print("在 https://www.last.fm/api/account/create 申请 api_key / api_secret 后填入。")
        return 1

    client = LastfmClient(config.api_key, config.api_secret, config.session_key)
    if not client.session_key:
        print("首次使用，需要浏览器授权 last.fm。")
        client.session_key = await authorize(client)
        config.session_key = client.session_key
        save_config(config, config_path)
        print("session_key 已保存。")

    listener = SmtcListener(config.match_keywords)
    tracker = ScrobbleTracker()
    print("开始监听网易云音乐（Ctrl+C 退出）…")

    while True:
        try:
            await run_once(listener, tracker, client)
        except LastfmError as exc:
            print(f"last.fm 错误：{exc}", file=sys.stderr)
        except Exception as exc:  # noqa: BLE001 - 常驻进程，任何异常都只记录不退出
            print(f"监听异常：{exc!r}", file=sys.stderr)
        await asyncio.sleep(POLL_SECONDS)


def main() -> None:
    try:
        raise SystemExit(asyncio.run(amain()))
    except KeyboardInterrupt:
        print("已退出")
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
.venv/Scripts/python.exe -m pytest tests/test_main.py -v
```

Expected: 3 passed。

- [ ] **Step 5: Commit**

```bash
git add tests/test_main.py lastfm_net163/main.py
git commit -m "feat: 新增主程序轮询循环与首次授权流程"
```

---

### Task 7: README 与手工集成验证

**Files:**
- Create: `README.md`

- [ ] **Step 1: 写 `README.md`**

```markdown
# lastfm_net163

把网易云音乐 Windows 桌面客户端正在播放的歌曲，按 last.fm 标准自动 scrobble 到你的 last.fm 账号。

## 环境要求

- Windows 10/11
- Python 3.11
- 网易云音乐 Windows 桌面客户端

## 安装

```bash
py -3.11 -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements.txt
```

## 配置与首次授权

1. 在 https://www.last.fm/api/account/create 免费申请 `api_key` / `api_secret`。
2. 运行：

```bash
.venv/Scripts/python.exe -m lastfm_net163.main
```

3. 首次运行会提示配置文件位置 `%APPDATA%\lastfm_net163\config.toml`，把 `api_key` / `api_secret` 填进去后重新运行。
4. 程序会打开浏览器让你授权 last.fm，授权成功后自动保存 `session_key`，之后无需重复登录。
5. 保持程序运行，打开网易云客户端正常听歌即可。播放达到曲目时长 50% 或 4 分钟（两者取先）后自动提交。

## 退出

在终端按 `Ctrl+C`。

## 测试

```bash
.venv/Scripts/python.exe -m pip install -r requirements-dev.txt
.venv/Scripts/python.exe -m pytest -v
```
```

- [ ] **Step 2: 手工集成验证**

前提：网易云客户端正在播放歌曲。

Run:

```bash
.venv/Scripts/python.exe -c "import asyncio; from lastfm_net163.smtc import SmtcListener; from lastfm_net163.scrobbler import ScrobbleTracker; l = SmtcListener(); m = asyncio.run(l.get_manager()); s = l.find_session(m); print('session:', s is not None); t = asyncio.run(l.read_track(s)); print(t)"
```

Expected: 输出 `session: True` 且 `Track(title=..., artist=..., ...)` 里的歌名与网易云正在播放的一致。

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: 新增 README 使用说明"
```

---

### Task 8: 全量测试与收尾

**Files:**
- 无新增文件。

- [ ] **Step 1: 运行全量测试**

Run:

```bash
.venv/Scripts/python.exe -m pytest -v
```

Expected: 全部通过（共 24 个测试：3 config + 8 scrobbler + 6 lastfm + 4 smtc + 3 main）。

- [ ] **Step 2: 检查工作区无遗留文件**

Run:

```bash
git status --short
```

Expected: 无未提交改动（或只有本次计划允许的文件）。

- [ ] **Step 3: 如有遗漏提交则提交**

```bash
git add -A
git commit -m "chore: 收尾提交"
```

Expected: 若 Step 2 已经干净，此步提示 nothing to commit，可跳过。

---

## Self-Review

**1. Spec coverage:**
- 设计文档 5 个模块 → Task 2/3/4/5/6 全部覆盖。
- SMTC 过滤网易云会话 → Task 5（`find_session` + `_matches`）。
- 30 秒过滤 + 50%/4 分钟达标 → Task 3（`ScrobbleTracker`）。
- config.toml + 首次浏览器授权 → Task 2（config）+ Task 6（`authorize`）。
- 错误处理（SMTC 消失、last.fm 错误、网络异常）→ Task 6 主循环捕获。
- 测试（状态机、签名、SMTC 提取）→ Task 2-6 均有测试，Task 7 手工集成。

**2. Placeholder scan:** 无 TBD/TODO，所有代码步骤均含完整代码。

**3. Type consistency:**
- `Track` 字段在 Task 3 定义：`title/artist/album/duration_seconds/position_seconds/is_playing`；Task 5 `read_track` 与 Task 6 测试均使用一致字段名。
- `SmtcListener` 方法 `get_manager` / `find_session` / `read_track` 在 Task 5 定义，Task 6 `run_once` 按同样签名调用。
- `LastfmClient.scrobble(artist, title, album, timestamp)` 在 Task 4 定义，Task 6 按 `client.scrobble(track.artist, track.title, track.album)` 调用。
- `load_config` / `save_config` / `ensure_config` 签名在 Task 2 定义，Task 6 按 `ensure_config()` / `load_config(config_path)` / `save_config(config, config_path)` 调用。
