from __future__ import annotations

import time


class PlaybackClock:
    """按曲目累计真实播放秒数（SMTC 没有进度时使用）。"""

    def __init__(self) -> None:
        self._key: tuple[str, str, str] | None = None
        self._accumulated = 0.0
        self._last_tick: float | None = None

    def reset(self) -> None:
        self._key = None
        self._accumulated = 0.0
        self._last_tick = None

    def tick(
        self,
        key: tuple[str, str, str],
        is_playing: bool,
        now: float | None = None,
    ) -> int:
        if now is None:
            now = time.monotonic()
        if key != self._key:
            self._key = key
            self._accumulated = 0.0
            self._last_tick = now
        if self._last_tick is None:
            self._last_tick = now
        if is_playing:
            self._accumulated += now - self._last_tick
        self._last_tick = now
        return int(self._accumulated)
