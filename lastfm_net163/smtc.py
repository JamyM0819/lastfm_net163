from __future__ import annotations

import asyncio

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
        for attempt in range(3):
            try:
                return await SessionManager.request_async()
            except OSError:
                if attempt == 2:
                    raise
                await asyncio.sleep(0.5 * (attempt + 1))

    def find_session(self, manager):
        for session in manager.get_sessions():
            if self._matches(session.source_app_user_model_id):
                return session
        return None

    async def read_track(self, session) -> Track | None:
        for attempt in range(3):
            try:
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
            except OSError:
                if attempt == 2:
                    return None
                await asyncio.sleep(0.5)
        return None
