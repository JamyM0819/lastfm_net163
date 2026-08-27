from __future__ import annotations

import asyncio
import sys
import time
import webbrowser

import requests

from .config import ensure_config, load_config, save_config
from .lastfm import LastfmClient, LastfmError
from .net163 import NetEaseClient
from .playback_clock import PlaybackClock
from .scrobbler import ScrobbleTracker, Track
from .smtc import SmtcListener

POLL_SECONDS = 2.0
AUTH_POLL_SECONDS = 2.0
AUTH_TIMEOUT_SECONDS = 300


async def authorize(client: LastfmClient) -> str:
    deadline = time.monotonic() + AUTH_TIMEOUT_SECONDS

    token: str | None = None
    while time.monotonic() < deadline:
        try:
            token = client.get_token()
            break
        except requests.RequestException as exc:
            print(f"获取授权 token 失败：{exc}", file=sys.stderr)
            await asyncio.sleep(AUTH_POLL_SECONDS)
    if token is None:
        raise LastfmError("等待 last.fm 授权超时")

    url = client.auth_url(token)
    print(f"请在浏览器中授权 last.fm：\n{url}")
    webbrowser.open(url)

    while time.monotonic() < deadline:
        await asyncio.sleep(AUTH_POLL_SECONDS)
        try:
            session = client.get_session(token)
            print(f"授权成功：{session.username}")
            return session.session_key
        except LastfmError:
            continue
        except requests.RequestException as exc:
            print(f"查询授权状态失败：{exc}", file=sys.stderr)
            continue
    raise LastfmError("等待 last.fm 授权超时")


def _enrich(
    track: Track | None,
    clock: PlaybackClock | None,
    durations: NetEaseClient | None,
) -> Track | None:
    if track is None:
        if clock is not None:
            clock.reset()
        return None

    position = track.position_seconds
    duration = track.duration_seconds

    if position <= 0 and clock is not None:
        position = clock.tick(track.key, track.is_playing)
    if duration <= 0 and durations is not None:
        ms = durations.get_duration_ms(track.artist, track.title)
        if ms > 0:
            duration = ms // 1000

    return Track(
        title=track.title,
        artist=track.artist,
        album=track.album,
        duration_seconds=duration,
        position_seconds=position,
        is_playing=track.is_playing,
    )


async def run_once(
    listener: SmtcListener,
    tracker: ScrobbleTracker,
    client: LastfmClient,
    clock: PlaybackClock | None = None,
    durations: NetEaseClient | None = None,
) -> Track | None:
    manager = await listener.get_manager()
    session = listener.find_session(manager)
    if session is None:
        if clock is not None:
            clock.reset()
        tracker.on_track(None)
        return None

    track = _enrich(await listener.read_track(session), clock, durations)
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
    clock = PlaybackClock()
    durations = NetEaseClient()
    print("开始监听网易云音乐（Ctrl+C 退出）…")

    while True:
        try:
            await run_once(listener, tracker, client, clock, durations)
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
    except LastfmError as exc:
        print(f"错误：{exc}", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
