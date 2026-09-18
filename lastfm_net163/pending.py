from __future__ import annotations

import json
import time
from dataclasses import asdict, dataclass
from pathlib import Path

MAX_PENDING = 500


@dataclass
class PendingScrobble:
    artist: str
    title: str
    album: str
    timestamp: int


class PendingQueue:
    """把提交失败的 scrobble 落到本地，网络恢复后再补交。"""

    def __init__(self, path: Path) -> None:
        self.path = path

    def load(self) -> list[PendingScrobble]:
        if not self.path.exists():
            return []
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
            return [
                PendingScrobble(
                    artist=item["artist"],
                    title=item["title"],
                    album=item.get("album", ""),
                    timestamp=int(item.get("timestamp", 0)),
                )
                for item in data
                if isinstance(item, dict)
            ]
        except (ValueError, OSError):
            return []

    def save(self, items: list[PendingScrobble]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = [asdict(item) for item in items]
        self.path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def add(self, item: PendingScrobble) -> None:
        items = self.load()
        items.append(item)
        # 防止积压过多；丢掉最老的记录。
        if len(items) > MAX_PENDING:
            items = items[-MAX_PENDING:]
        self.save(items)
