from __future__ import annotations

import requests

SEARCH_URL = "https://music.163.com/api/search/get/web"


class NetEaseClient:
    """查询网易云公开搜索接口，按歌名+歌手估算歌曲时长。"""

    def __init__(self, timeout: float = 5.0) -> None:
        self.timeout = timeout
        self._cache: dict[tuple[str, str], int] = {}

    def _key(self, artist: str, title: str) -> tuple[str, str]:
        return (artist.strip().lower(), title.strip().lower())

    def _best_match_ms(self, artist: str, title: str, songs: list[dict]) -> int:
        wanted_title = title.strip().lower()
        wanted_artist = artist.strip().lower()
        best_ms = 0
        best_score = -1
        for song in songs:
            name = (song.get("name") or "").strip().lower()
            artists = [
                (item.get("name") or "").strip().lower()
                for item in (song.get("artists") or [])
            ]
            score = 0
            if name == wanted_title:
                score += 3
            elif wanted_title and name and (wanted_title in name or name in wanted_title):
                score += 1
            if wanted_artist and any(
                wanted_artist in artist_name or artist_name in wanted_artist
                for artist_name in artists
            ):
                score += 2
            if score > best_score:
                best_score = score
                best_ms = int(song.get("duration") or 0)
        return best_ms

    def get_duration_ms(self, artist: str, title: str) -> int:
        key = self._key(artist, title)
        if key in self._cache:
            return self._cache[key]
        try:
            resp = requests.get(
                SEARCH_URL,
                params={"s": f"{title} {artist}", "type": 1, "limit": 5, "offset": 0},
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                    "Referer": "https://music.163.com/",
                },
                timeout=self.timeout,
            )
            resp.raise_for_status()
            data = resp.json()
            songs = (data.get("result") or {}).get("songs") or []
            ms = self._best_match_ms(artist, title, songs)
        except (requests.RequestException, ValueError):
            ms = 0
        self._cache[key] = ms
        return ms
