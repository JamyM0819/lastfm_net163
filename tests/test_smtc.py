import asyncio
from datetime import timedelta

from lastfm_net163.scrobbler import Track
from lastfm_net163.smtc import SmtcListener


class FakeTimeline:
    def __init__(self, position_seconds, end_seconds):
        self.position = timedelta(seconds=position_seconds)
        self.end_time = timedelta(seconds=end_seconds)


class FakeInfo:
    def __init__(self, status):
        self.playback_status = status


class FakeMedia:
    def __init__(self, title, artist, album):
        self.title = title
        self.artist = artist
        self.album_title = album


class FakeSession:
    def __init__(self, aumid, media, info, timeline):
        self.source_app_user_model_id = aumid
        self._media = media
        self._info = info
        self._timeline = timeline

    async def try_get_media_properties_async(self):
        return self._media

    def get_playback_info(self):
        return self._info

    def get_timeline_properties(self):
        return self._timeline


class FakeManager:
    def __init__(self, sessions):
        self._sessions = sessions

    def get_sessions(self):
        return self._sessions


def make_session(aumid="CloudMusic.exe"):
    return FakeSession(
        aumid,
        FakeMedia("Song", "Artist", "Album"),
        FakeInfo(4),  # 4 = PLAYING in the real enum
        FakeTimeline(75, 200),
    )


def test_find_session_matches_netease_keyword():
    listener = SmtcListener(match_keywords=("cloudmusic", "netease"))
    netease = make_session("CloudMusic.exe")
    other = make_session("Spotify.exe")
    manager = FakeManager([other, netease])
    assert listener.find_session(manager) is netease


def test_find_session_ignores_non_matching():
    listener = SmtcListener(match_keywords=("cloudmusic", "netease"))
    manager = FakeManager([make_session("Spotify.exe")])
    assert listener.find_session(manager) is None


def test_read_track_extracts_metadata():
    session = make_session("CloudMusic.exe")
    track = asyncio.run(SmtcListener().read_track(session))
    assert track == Track(
        title="Song",
        artist="Artist",
        album="Album",
        duration_seconds=200,
        position_seconds=75,
        is_playing=True,
    )


def test_read_track_returns_none_without_metadata():
    session = FakeSession(
        "CloudMusic.exe",
        FakeMedia("", "", ""),
        FakeInfo(4),
        FakeTimeline(0, 0),
    )
    assert asyncio.run(SmtcListener().read_track(session)) is None
