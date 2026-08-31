from __future__ import annotations

import asyncio
import sys
import time
import webbrowser

import requests

from .config import ensure_config, load_config, save_config
from .lastfm import LastfmClient, LastfmError
from .local_library import LocalLibrary
from .ncm_playing import NcmPlayingReader
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
    library: LocalLibrary | None = None,
    ncm: NcmPlayingReader | None = None,
    album_memo: dict[tuple[str, str], str] | None = None,
) -> Track | None:
    if track is None:
        if clock is not None:
            clock.reset()
        return None

    position = track.position_seconds
    duration = track.duration_seconds
    album = track.album

    ncm_now = ncm.now() if ncm is not None else None
    ncm_match = ncm_now is not None and (
        ncm_now.title.strip().lower() == track.title.strip().lower()
        and (
            ncm_now.artist.strip().lower() in track.artist.strip().lower()
            or track.artist.strip().lower() in ncm_now.artist.strip().lower()
        )
    )

    # 先补时长（不参与 key，顺序无关紧要，但保持补全先于计时）
    if duration <= 0:
        if ncm_match and ncm_now.duration_ms > 0:
            duration = ncm_now.duration_ms // 1000
        elif durations is not None:
            ms = durations.get_duration_ms(track.artist, track.title)
            if ms > 0:
                duration = ms // 1000

    # 再补专辑，并缓存结果：同一首歌内专辑值稳定，避免 key 跳变导致
    # ScrobbleTracker 反复重置、重复提交。
    if not album:
        memo_key = (track.artist.strip().lower(), track.title.strip().lower())
        if ncm_match and ncm_now.album:
            album = ncm_now.album
        elif album_memo is not None:
            album = album_memo.get(memo_key, "")
        if not album and library is not None:
            album = library.get_album(track.artist, track.title)
        if not album and durations is not None:
            album = durations.get_album(track.artist, track.title)
        if album and album_memo is not None:
            album_memo[memo_key] = album

    # 用补全后的 key 计时：同名同歌手但不同专辑的版本不会再串计时。
    enriched = Track(
        title=track.title,
        artist=track.artist,
        album=album,
        duration_seconds=duration,
        position_seconds=position,
        is_playing=track.is_playing,
    )
    if position <= 0 and clock is not None:
        position = clock.tick(enriched.key, track.is_playing)

    return Track(
        title=track.title,
        artist=track.artist,
        album=album,
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
    library: LocalLibrary | None = None,
    ncm: NcmPlayingReader | None = None,
    album_memo: dict[tuple[str, str], str] | None = None,
) -> Track | None:
    manager = await listener.get_manager()
    session = listener.find_session(manager)
    if session is None:
        if clock is not None:
            clock.reset()
        tracker.on_track(None)
        return None

    track = _enrich(
        await listener.read_track(session),
        clock,
        durations,
        library,
        ncm,
        album_memo,
    )
    if tracker.on_track(track):
        assert track is not None
        client.scrobble(track.artist, track.title, track.album)
        print(f"已 scrobble：{track.artist} - {track.title}")
    return track


def _read_credentials(input_fn=None) -> tuple[str, str]:
    """交互式读取 last.fm api_key / api_secret。"""
    if input_fn is None:
        input_fn = input
    print("在 https://www.last.fm/api/account/create 申请 api_key / api_secret。")
    api_key = input_fn("api_key: ").strip()
    api_secret = input_fn("api_secret: ").strip()
    return api_key, api_secret


async def amain() -> int:
    config_path = ensure_config()
    config = load_config(config_path)
    if not config.api_key or not config.api_secret:
        print(f"未检测到 last.fm 凭据（配置文件：{config_path}）")
        try:
            api_key, api_secret = _read_credentials()
        except EOFError:
            print("未输入，退出。")
            return 1
        if not api_key or not api_secret:
            print("api_key / api_secret 不能为空。")
            return 1
        config.api_key = api_key
        config.api_secret = api_secret
        save_config(config, config_path)
        print("凭据已保存。")

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
    library = LocalLibrary(prefer_albums=config.prefer_albums)
    ncm = NcmPlayingReader()
    album_memo: dict[tuple[str, str], str] = {}
    print("开始监听网易云音乐（Ctrl+C 退出）…")

    while True:
        try:
            await run_once(listener, tracker, client, clock, durations, library, ncm, album_memo)
        except LastfmError as exc:
            print(f"last.fm 错误：{exc}", file=sys.stderr)
        except Exception as exc:  # noqa: BLE001 - 常驻进程，任何异常都只记录不退出
            print(f"监听异常：{exc!r}", file=sys.stderr)
        await asyncio.sleep(POLL_SECONDS)


def _enable_line_buffering() -> None:
    """后台运行时 stdout/stderr 被重定向到文件后默认是块缓冲，日志会迟迟不落盘；
    这里改成行缓冲，保证 print 实时写入日志文件，方便开机后台运行时排查状态。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(line_buffering=True)  # type: ignore[attr-defined]
        except (AttributeError, ValueError, OSError):
            pass


def main() -> None:
    _enable_line_buffering()
    try:
        raise SystemExit(asyncio.run(amain()))
    except KeyboardInterrupt:
        print("已退出")
    except LastfmError as exc:
        print(f"错误：{exc}", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
