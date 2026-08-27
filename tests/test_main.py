import asyncio

from lastfm_net163.main import _enrich, _read_credentials, run_once
from lastfm_net163.scrobbler import ScrobbleTracker, Track


class FakeListener:
    def __init__(self, session, track):
        self._session = session
        self._track = track

    async def get_manager(self):
        return object()

    def find_session(self, manager):
        return self._session

    async def read_track(self, session):
        return self._track


class FakeClient:
    def __init__(self):
        self.scrobbles = []

    def scrobble(self, artist, title, album, timestamp=None):
        self.scrobbles.append((artist, title, album))


def test_run_once_scrobbles_eligible_track():
    track = Track(
        title="Title",
        artist="Artist",
        album="Album",
        duration_seconds=200,
        position_seconds=100,
        is_playing=True,
    )
    listener = FakeListener(object(), track)
    tracker = ScrobbleTracker()
    client = FakeClient()

    asyncio.run(run_once(listener, tracker, client))

    assert client.scrobbles == [("Artist", "Title", "Album")]


def test_run_once_without_session_resets_tracker():
    listener = FakeListener(None, None)
    tracker = ScrobbleTracker()
    client = FakeClient()

    asyncio.run(run_once(listener, tracker, client))

    assert client.scrobbles == []


def test_run_once_does_not_scrobble_before_threshold():
    track = Track(
        title="Title",
        artist="Artist",
        album="Album",
        duration_seconds=200,
        position_seconds=99,
        is_playing=True,
    )
    listener = FakeListener(object(), track)
    tracker = ScrobbleTracker()
    client = FakeClient()

    asyncio.run(run_once(listener, tracker, client))

    assert client.scrobbles == []


class FakeClock:
    def __init__(self, seconds):
        self.seconds = seconds

    def tick(self, key, is_playing, now=None):
        return self.seconds

    def reset(self):
        pass


class FakeDurations:
    def __init__(self, ms):
        self.ms = ms

    def get_duration_ms(self, artist, title):
        return self.ms


def test_enrich_falls_back_to_clock_and_duration():
    track = Track(
        title="T",
        artist="A",
        album="",
        duration_seconds=0,
        position_seconds=0,
        is_playing=True,
    )
    out = _enrich(track, FakeClock(90), FakeDurations(200000))
    assert out.position_seconds == 90
    assert out.duration_seconds == 200


def test_enrich_keeps_smtc_values_when_present():
    track = Track(
        title="T",
        artist="A",
        album="",
        duration_seconds=200,
        position_seconds=100,
        is_playing=True,
    )
    out = _enrich(track, FakeClock(90), FakeDurations(999000))
    assert out.duration_seconds == 200
    assert out.position_seconds == 100


def test_read_credentials_returns_trimmed_values(monkeypatch):
    responses = iter(["  key1  ", " secret1 "])
    monkeypatch.setattr("builtins.input", lambda prompt="": next(responses))
    assert _read_credentials() == ("key1", "secret1")


def test_read_credentials_returns_empty_strings():
    assert _read_credentials(lambda prompt="": "") == ("", "")


