from lastfm_net163.playback_clock import PlaybackClock


def test_accumulates_while_playing():
    clock = PlaybackClock()
    assert clock.tick(("t", "a", ""), True, now=100.0) == 0
    assert clock.tick(("t", "a", ""), True, now=110.0) == 10
    assert clock.tick(("t", "a", ""), True, now=115.0) == 15


def test_pause_does_not_accumulate():
    clock = PlaybackClock()
    clock.tick(("t", "a", ""), True, now=100.0)
    assert clock.tick(("t", "a", ""), False, now=130.0) == 0
    assert clock.tick(("t", "a", ""), True, now=140.0) == 10


def test_key_change_resets():
    clock = PlaybackClock()
    clock.tick(("t", "a", ""), True, now=100.0)
    clock.tick(("t", "a", ""), True, now=120.0)
    assert clock.tick(("u", "a", ""), True, now=125.0) == 0


def test_reset():
    clock = PlaybackClock()
    clock.tick(("t", "a", ""), True, now=100.0)
    clock.tick(("t", "a", ""), True, now=110.0)
    clock.reset()
    assert clock.tick(("t", "a", ""), True, now=120.0) == 0
