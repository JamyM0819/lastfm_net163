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
