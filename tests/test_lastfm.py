import hashlib

import requests

from lastfm_net163.lastfm import LastfmClient, LastfmError


class FakeResp:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


def test_sign_is_md5_of_sorted_params_plus_secret():
    client = LastfmClient(api_key="key", api_secret="secret")
    assert client.sign({"b": "2", "a": "1"}) == hashlib.md5(
        b"a1b2secret"
    ).hexdigest()


def test_auth_url_contains_key_and_token():
    client = LastfmClient(api_key="key", api_secret="secret")
    url = client.auth_url("tok123")
    assert "api_key=key" in url
    assert "token=tok123" in url


def test_get_token(monkeypatch):
    client = LastfmClient(api_key="key", api_secret="secret")

    def fake_get(url, params=None, timeout=None):
        assert params["method"] == "auth.gettoken"
        assert params["api_key"] == "key"
        assert "api_sig" in params
        return FakeResp({"token": "tok123"})

    monkeypatch.setattr(requests, "get", fake_get)
    assert client.get_token() == "tok123"


def test_get_session(monkeypatch):
    client = LastfmClient(api_key="key", api_secret="secret")

    def fake_get(url, params=None, timeout=None):
        assert params["method"] == "auth.getsession"
        assert params["token"] == "tok123"
        return FakeResp({"session": {"name": "jamy", "key": "sk123"}})

    monkeypatch.setattr(requests, "get", fake_get)
    session = client.get_session("tok123")
    assert session.session_key == "sk123"
    assert session.username == "jamy"


def test_scrobble_posts_signed_params(monkeypatch):
    client = LastfmClient(api_key="key", api_secret="secret", session_key="sk1")
    captured = {}

    def fake_post(url, data=None, timeout=None):
        captured["data"] = dict(data)
        return FakeResp({"scrobbles": {"@attr": {"accepted": 1}}})

    monkeypatch.setattr(requests, "post", fake_post)
    client.scrobble("Artist", "Title", album="Album", timestamp=1700000000)

    data = captured["data"]
    assert data["method"] == "track.scrobble"
    assert data["artist"] == "Artist"
    assert data["track"] == "Title"
    assert data["album"] == "Album"
    assert data["timestamp"] == "1700000000"
    assert data["sk"] == "sk1"
    expected_sig = client.sign(
        {k: v for k, v in data.items() if k not in ("api_sig", "format")}
    )
    assert data["api_sig"] == expected_sig


def test_error_response_raises(monkeypatch):
    client = LastfmClient(api_key="key", api_secret="secret")

    def fake_get(url, params=None, timeout=None):
        return FakeResp({"error": 8, "message": "Operation failed"})

    monkeypatch.setattr(requests, "get", fake_get)
    try:
        client.get_token()
    except LastfmError as exc:
        assert "8" in str(exc)
    else:
        raise AssertionError("expected LastfmError")
