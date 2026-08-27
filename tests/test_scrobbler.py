from lastfm_net163.scrobbler import ScrobbleTracker, Track


def make_track(
    title="Song",
    artist="Artist",
    album="Album",
    duration=200,
    position=100,
    playing=True,
):
    return Track(
        title=title,
        artist=artist,
        album=album,
        duration_seconds=duration,
        position_seconds=position,
        is_playing=playing,
    )


def test_short_track_not_scrobbled():
    assert ScrobbleTracker().on_track(make_track(duration=20, position=10)) is False


def test_half_played_scrobbles():
    assert ScrobbleTracker().on_track(make_track(duration=200, position=100)) is True


def test_four_minutes_scrobbles_even_before_half():
    assert ScrobbleTracker().on_track(make_track(duration=600, position=240)) is True


def test_under_half_not_scrobbled():
    assert ScrobbleTracker().on_track(make_track(duration=200, position=99)) is False


def test_same_track_only_scrobbled_once():
    tracker = ScrobbleTracker()
    assert tracker.on_track(make_track(duration=200, position=100)) is True
    assert tracker.on_track(make_track(duration=200, position=150)) is False


def test_track_change_resets():
    tracker = ScrobbleTracker()
    assert tracker.on_track(make_track(title="A", duration=200, position=100)) is True
    assert tracker.on_track(make_track(title="B", duration=200, position=100)) is True


def test_paused_does_not_scrobble():
    assert ScrobbleTracker().on_track(make_track(playing=False)) is False


def test_none_track_resets_state():
    tracker = ScrobbleTracker()
    tracker.on_track(make_track(duration=200, position=100))
    assert tracker.on_track(None) is False
    assert tracker.on_track(make_track(duration=200, position=100)) is True
