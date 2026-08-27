# lastfm_net163

把网易云音乐 Windows 桌面客户端正在播放的歌曲，按 last.fm 标准自动 scrobble 到你的 last.fm 账号。

## 环境要求

- Windows 10/11
- Python 3.11
- 网易云音乐 Windows 桌面客户端

## 安装

```bash
py -3.11 -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements.txt
```

> CMD 用户注意：路径分隔符请用反斜杠，例如 `.venv\Scripts\python.exe`。

## 配置与首次授权

1. 在 https://www.last.fm/api/account/create 免费申请 `api_key` / `api_secret`。
2. 运行：

```bash
.venv/Scripts/python.exe -m lastfm_net163.main
```

3. 首次运行会提示配置文件位置 `%APPDATA%\lastfm_net163\config.toml`，并交互式询问 `api_key` / `api_secret`；粘贴后自动保存并继续。也可以手动编辑该文件填入后再运行。
4. 程序会打开浏览器让你授权 last.fm，授权成功后自动保存 `session_key`，之后无需重复登录。
5. 保持程序运行，打开网易云客户端正常听歌即可。播放达到曲目时长 50% 或 4 分钟（两者取先）后自动提交。

> 网易云客户端不通过系统媒体会话提供播放进度时，程序会自计时，并通过网易云公开搜索接口补全歌曲时长。

## 退出

在终端按 `Ctrl+C`。

## 测试

```bash
.venv/Scripts/python.exe -m pip install -r requirements-dev.txt
.venv/Scripts/python.exe -m pytest -v
```
