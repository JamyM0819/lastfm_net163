import requests

from lastfm_net163.net163 import NetEaseClient


class FakeResp:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


def test_get_duration_ms_parses_best_match(monkeypatch):
    client = NetEaseClient()

    def fake_get(url, params=None, headers=None, timeout=None):
        assert params["type"] == 1
        return FakeResp(
            {
                "result": {
                    "songs": [
                        {"name": "Mean", "artists": [{"name": "Taylor Swift"}], "duration": 231000},
                        {"name": "Mean (Live)", "artists": [{"name": "Taylor Swift"}], "duration": 200000},
                    ]
                }
            }
        )

    monkeypatch.setattr(requests, "get", fake_get)
    assert client.get_duration_ms("Taylor Swift", "Mean") == 231000


def test_get_duration_ms_caches(monkeypatch):
    client = NetEaseClient()
    calls = []

    def fake_get(url, params=None, headers=None, timeout=None):
        calls.append(params)
        return FakeResp(
            {"result": {"songs": [{"name": "T", "artists": [{"name": "A"}], "duration": 100000}]}}
        )

    monkeypatch.setattr(requests, "get", fake_get)
    assert client.get_duration_ms("A", "T") == 100000
    assert client.get_duration_ms("A", "T") == 100000
    assert len(calls) == 1


def test_get_duration_ms_returns_zero_on_error(monkeypatch):
    client = NetEaseClient()

    def fake_get(url, params=None, headers=None, timeout=None):
        raise requests.RequestException("boom")

    monkeypatch.setattr(requests, "get", fake_get)
    assert client.get_duration_ms("A", "T") == 0
