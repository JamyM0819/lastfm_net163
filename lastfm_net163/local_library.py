r"""读取网易云 Windows 客户端本地播放队列，获取歌曲所属专辑。

网易云客户端不通过 SMTC 提供 album_title，但会把当前播放队列（含每首歌的
专辑信息）写到 %LOCALAPPDATA%\Netease\CloudMusic\webdata\file\playingList。
这里直接读该文件，按 SMTC 提供的 title/artist 定位歌曲，返回其在网易云里
所属的专辑。同一歌名+歌手若在队列里对应多张专辑，优先返回正专（精选集
会被降权），以贴合"网易云里听的是正专"这一常见场景。
"""
from __future__ import annotations

import json
import os
from pathlib import Path

from .net163 import is_compilation_album


class LocalLibrary:
    """按 title+artist 从网易云本地播放队列读取专辑名。"""

    def __init__(
        self,
        data_dir: Path | None = None,
        prefer_albums: tuple[str, ...] = (),
    ) -> None:
        local = os.environ.get("LOCALAPPDATA", "")
        self.data_dir = data_dir or (
            Path(local) / "Netease" / "CloudMusic" / "webdata" / "file"
            if local
            else None
        )
        self.prefer_albums = tuple(
            name.strip().lower() for name in prefer_albums if name.strip()
        )
        self._mtime: float = 0.0
        self._index: dict[tuple[str, str], list[str]] = {}

    def _track_entries(self, data: dict) -> list[dict]:
        """兼容 playingList（list）和 fmPlay（queue）两种文件结构。"""
        entries: list[dict] = []
        for key in ("list", "queue"):
            for item in data.get(key) or []:
                if not isinstance(item, dict):
                    continue
                track = item.get("track") if isinstance(item.get("track"), dict) else item
                if isinstance(track, dict):
                    entries.append(track)
        return entries

    def _reload_if_changed(self) -> None:
        if self.data_dir is None:
            return
        paths = [self.data_dir / "playingList", self.data_dir / "fmPlay"]
        mtimes: list[float] = []
        for path in paths:
            try:
                mtimes.append(path.stat().st_mtime)
            except OSError:
                mtimes.append(0.0)
        mtime = max(mtimes)
        if mtime == self._mtime:
            return
        self._mtime = mtime
        self._index = {}
        for path in paths:
            try:
                with path.open("r", encoding="utf-8") as f:
                    data = json.load(f)
            except (OSError, ValueError):
                continue
            for track in self._track_entries(data):
                title = (track.get("name") or "").strip().lower()
                album = (track.get("album") or {}).get("name") or ""
                if not title or not album:
                    continue
                for artist in track.get("artists") or []:
                    artist_name = (artist.get("name") or "").strip().lower()
                    if artist_name:
                        albums = self._index.setdefault((title, artist_name), [])
                        if album not in albums:
                            albums.append(album)

    def _pick(self, candidates: list[str]) -> str:
        """多张候选专辑时：优先用户配置的正专，其次按关键词避开精选集，最后取队列里先出现的那张。"""
        for album in candidates:
            if album.lower() in self.prefer_albums:
                return album
        for album in candidates:
            if not is_compilation_album(album):
                return album
        return candidates[0]

    def get_album(self, artist: str, title: str) -> str:
        self._reload_if_changed()
        title_key = (title or "").strip().lower()
        artist_key = (artist or "").strip().lower()
        if not title_key or not artist_key:
            return ""
        candidates = list(self._index.get((title_key, artist_key), []))
        if not candidates:
            for (idx_title, idx_artist), idx_albums in self._index.items():
                if idx_title == title_key and (
                    artist_key in idx_artist or idx_artist in artist_key
                ):
                    candidates.extend(idx_albums)
        if not candidates:
            return ""
        return self._pick(candidates)
