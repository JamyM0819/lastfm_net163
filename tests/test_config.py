from lastfm_net163.config import Config, ensure_config, load_config, save_config


def test_ensure_config_creates_default_file(tmp_path):
    path = tmp_path / "config.toml"
    assert not path.exists()
    result = ensure_config(path)
    assert result == path
    assert path.exists()


def test_load_config_reads_values(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text(
        'api_key = "key1"\n'
        'api_secret = "secret1"\n'
        'session_key = "sk1"\n'
        'match_keywords = ["cloudmusic"]\n',
        encoding="utf-8",
    )
    config = load_config(path)
    assert config.api_key == "key1"
    assert config.api_secret == "secret1"
    assert config.session_key == "sk1"
    assert config.match_keywords == ("cloudmusic",)


def test_save_then_load_roundtrip(tmp_path):
    path = tmp_path / "config.toml"
    config = Config(
        api_key="key2",
        api_secret="secret2",
        session_key="sk2",
        match_keywords=("netease", "cloudmusic"),
    )
    save_config(config, path)
    loaded = load_config(path)
    assert loaded == config
