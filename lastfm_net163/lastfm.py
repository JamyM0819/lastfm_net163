from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass

import requests

API_ROOT = "https://ws.audioscrobbler.com/2.0/"
AUTH_URL = "https://www.last.fm/api/auth/"


class LastfmError(RuntimeError):
    pass


@dataclass
class SessionInfo:
    session_key: str
    username: str


class LastfmClient:
    def __init__(
        self,
        api_key: str,
        api_secret: str,
        session_key: str = "",
        timeout: float = 10.0,
    ) -> None:
        self.api_key = api_key
        self.api_secret = api_secret
        self.session_key = session_key
        self.timeout = timeout

    def sign(self, params: dict[str, str]) -> str:
        payload = "".join(f"{k}{params[k]}" for k in sorted(params))
        return hashlib.md5((payload + self.api_secret).encode("utf-8")).hexdigest()

    def _call(self, params: dict[str, str], method: str = "GET") -> dict:
        params = {**params, "api_key": self.api_key, "format": "json"}
        params["api_sig"] = self.sign(
            {k: v for k, v in params.items() if k not in ("api_sig", "format")}
        )
        if method == "GET":
            resp = requests.get(API_ROOT, params=params, timeout=self.timeout)
        else:
            resp = requests.post(API_ROOT, data=params, timeout=self.timeout)
        resp.raise_for_status()
        data = resp.json()
        if data.get("error"):
            raise LastfmError(
                f"last.fm error {data['error']}: {data.get('message', '')}"
            )
        return data

    def get_token(self) -> str:
        data = self._call({"method": "auth.gettoken"})
        return data["token"]

    def auth_url(self, token: str) -> str:
        return f"{AUTH_URL}?api_key={self.api_key}&token={token}"

    def get_session(self, token: str) -> SessionInfo:
        data = self._call({"method": "auth.getsession", "token": token})
        session = data["session"]
        return SessionInfo(session_key=session["key"], username=session["name"])

    def scrobble(
        self,
        artist: str,
        title: str,
        album: str = "",
        timestamp: int | None = None,
    ) -> dict:
        params = {
            "method": "track.scrobble",
            "artist": artist,
            "track": title,
            "timestamp": str(timestamp if timestamp is not None else int(time.time())),
            "sk": self.session_key,
        }
        if album:
            params["album"] = album
        return self._call(params, method="POST")
