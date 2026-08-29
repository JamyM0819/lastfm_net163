from __future__ import annotations

import requests

SEARCH_URL = "https://music.163.com/api/search/get/web"


class NetEaseClient:
    """查询网易云公开搜索接口，按歌名+歌手补全歌曲时长和专辑名。"""

    _COMPILATION_HINTS = (
        "greatest",
        "best of",
        "best-of",
        "collection",
        "compilation",
        "anthology",
        "essential",
        "ultimate",
        "singles",
        "hits",
        "精选",
    )

    def __init__(self, timeout: float = 5.0) -> None:
        self.timeout = timeout
        self._cache: dict[tuple[str, str], tuple[int, str]] = {}

    def _key(self, artist: str, title: str) -> tuple[str, str]:
        return (artist.strip().lower(), title.strip().lower())

    def _is_compilation(self, album_name: str) -> bool:
        lowered = album_name.lower()
        return any(hint in lowered for hint in self._COMPILATION_HINTS)

    def _best_match(self, artist: str, title: str, songs: list[dict]) -> tuple[int, str]:
        """返回 (duration_ms, album_name)；精选集专辑会被降权，优先正专。"""
        wanted_title = title.strip().lower()
        wanted_artist = artist.strip().lower()
        best_ms = 0
        best_album = ""
        best_score = 0
        for song in songs:
            name = (song.get("name") or "").strip().lower()
            album_name = (song.get("album") or {}).get("name") or ""
            artists = [
                (item.get("name") or "").strip().lower()
                for item in (song.get("artists") or [])
            ]
            score = 0
            if name == wanted_title:
                score += 3
            elif wanted_title and name and len(wanted_title) > 1 and (
                wanted_title in name or name in wanted_title
            ):
                score += 1
            if wanted_artist and any(
                wanted_artist in artist_name or artist_name in wanted_artist
                for artist_name in artists
            ):
                score += 2
            if self._is_compilation(album_name):
                score -= 1
            if score > best_score:
                best_score = score
                best_ms = int(song.get("duration") or 0)
                best_album = album_name
        return best_ms, best_album

    def _fetch(self, artist: str, title: str) -> tuple[int, str]:
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
            ms, album = self._best_match(artist, title, songs)
        except (requests.RequestException, ValueError):
            return 0, ""
        self._cache[key] = (ms, album)
        return ms, album

    def get_duration_ms(self, artist: str, title: str) -> int:
        ms, _ = self._fetch(artist, title)
        return ms

    def get_album(self, artist: str, title: str) -> str:
        _, album = self._fetch(artist, title)
        return album
