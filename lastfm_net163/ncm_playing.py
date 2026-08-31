r"""从网易云客户端本地存储读取当前播放的完整信息（含专辑）。

网易云客户端把当前播放信息（含 trackId、专辑名）加密后写入
%LOCALAPPDATA%\Netease\CloudMusic\webapp91x64\Local Storage\leveldb 的
playingInfo 键。这里解析 leveldb 的 WAL 日志并用 AES-ECB 解密，直接拿到
正在播放歌曲的准确专辑。这是唯一能区分"同名同歌手、不同专辑"的数据源，
因为 trackId 在网易云里唯一对应一个专辑版本。
"""
from __future__ import annotations

import base64
import json
import os
import struct
from dataclasses import dataclass
from pathlib import Path

from Crypto.Cipher import AES

# 网易云客户端前端加密 localStorage 的固定密钥（从客户端二进制中提取）。
_AES_KEY = b"(b)$@.a!mr+-<?\x60x"


@dataclass
class NowPlaying:
    title: str
    artist: str
    album: str
    duration_ms: int
    track_id: str


def _data_dir() -> Path | None:
    local = os.environ.get("LOCALAPPDATA", "")
    if not local:
        return None
    return (
        Path(local)
        / "Netease"
        / "CloudMusic"
        / "webapp91x64"
        / "Local Storage"
        / "leveldb"
    )


def _read_varint(data: bytes, pos: int) -> tuple[int, int] | None:
    shift = 0
    result = 0
    while pos < len(data):
        b = data[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            return result, pos
        shift += 7
    return None


def _parse_log_records(data: bytes) -> list[bytes]:
    """解析 leveldb WAL：7 字节头（CRC+len+type），重组跨记录的 WriteBatch。"""
    records: list[bytes] = []
    parts: list[bytes] = []
    pos = 0
    while pos + 7 <= len(data):
        _, length, rtype = struct.unpack_from("<IHB", data, pos)
        pos += 7
        if pos + length > len(data):
            break
        payload = data[pos : pos + length]
        pos += length
        if rtype == 1:
            records.append(payload)
        elif rtype == 2:
            parts = [payload]
        elif rtype == 3:
            parts.append(payload)
        elif rtype == 4:
            parts.append(payload)
            records.append(b"".join(parts))
            parts = []
        else:
            break
    return records


def _parse_batch(batch: bytes) -> list[tuple[bytes, bytes]]:
    """解析 WriteBatch：8 字节 seq + 4 字节 count + 若干 put 键值对。"""
    if len(batch) < 12:
        return []
    pos = 8
    count = struct.unpack_from("<I", batch, pos)[0]
    pos += 4
    pairs: list[tuple[bytes, bytes]] = []
    for _ in range(count):
        if pos >= len(batch):
            break
        op = batch[pos]
        pos += 1
        klen = _read_varint(batch, pos)
        if klen is None:
            break
        klen, pos = klen
        key = batch[pos : pos + klen]
        pos += klen
        if op == 1:
            vlen = _read_varint(batch, pos)
            if vlen is None:
                break
            vlen, pos = vlen
            value = batch[pos : pos + vlen]
            pos += vlen
            pairs.append((key, value))
    return pairs


def _decrypt_value(value: bytes) -> dict:
    """值格式：\x01 + base64(base64(AES-ECB(JSON)))。"""
    if value[:1] == b"\x01":
        value = value[1:]
    s1 = value.decode("utf-8").strip()
    d1 = base64.b64decode(s1 + "=" * (-len(s1) % 4))
    s2 = d1.decode("utf-8").strip()
    cipher = base64.b64decode(s2 + "=" * (-len(s2) % 4))
    cipher_obj = AES.new(_AES_KEY, AES.MODE_ECB)
    plain = cipher_obj.decrypt(cipher)
    pad = plain[-1]
    if 0 < pad <= 16 and plain[-pad:] == bytes([pad]) * pad:
        plain = plain[:-pad]
    return json.loads(plain.decode("utf-8"))


class NcmPlayingReader:
    """从 leveldb WAL 解密当前播放信息。"""

    def __init__(self, data_dir: Path | None = None) -> None:
        self.data_dir = data_dir or _data_dir()
        self._mtime: float = 0.0
        self._now: NowPlaying | None = None

    def _reload_if_changed(self) -> None:
        if self.data_dir is None:
            return
        logs = sorted(
            (p for p in self.data_dir.glob("*.log") if p.name != "LOG"),
            key=lambda p: p.stat().st_mtime,
            reverse=True,
        )
        if not logs:
            return
        try:
            mtime = logs[0].stat().st_mtime
        except OSError:
            return
        if mtime == self._mtime:
            return
        self._mtime = mtime
        self._now = self._read_now(logs)

    def _read_now(self, logs: list[Path]) -> NowPlaying | None:
        for path in logs:
            try:
                data = path.read_bytes()
            except OSError:
                continue
            playing_info: bytes | None = None
            for batch in _parse_log_records(data):
                for key, value in _parse_batch(batch):
                    if key.decode("utf-8", errors="replace").endswith("playingInfo"):
                        playing_info = value
            if playing_info is not None:
                try:
                    return self._parse_playing_info(playing_info)
                except (ValueError, KeyError, json.JSONDecodeError):
                    continue
        return None

    def _parse_playing_info(self, value: bytes) -> NowPlaying | None:
        data = _decrypt_value(value)
        track = (data.get("curPlaying") or {}).get("track") or {}
        title = (track.get("name") or "").strip()
        artists = track.get("artists") or []
        artist = (artists[0].get("name") or "").strip() if artists else ""
        album_obj = track.get("album") or {}
        album = (
            album_obj.get("name", "").strip()
            if isinstance(album_obj, dict)
            else str(album_obj or "").strip()
        )
        duration_ms = int(track.get("duration") or 0)
        track_id = str(track.get("id") or "")
        if not title or not artist:
            return None
        return NowPlaying(
            title=title,
            artist=artist,
            album=album,
            duration_ms=duration_ms,
            track_id=track_id,
        )

    def now(self) -> NowPlaying | None:
        self._reload_if_changed()
        return self._now
