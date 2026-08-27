import asyncio

from lastfm_net163.main import run_once
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
