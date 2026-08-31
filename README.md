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

> 网易云客户端不通过系统媒体会话提供播放进度和专辑信息。程序会从网易云客户端本地存储（`%LOCALAPPDATA%\Netease\CloudMusic\webapp91x64\Local Storage\leveldb`）中解密出当前播放的完整信息（含专辑、时长、trackId），同名单曲也能准确区分专辑；本地存储不可用时才回退到本地播放队列和公开搜索接口。

## 退出

在终端按 `Ctrl+C`。

## 安卓端自研 App（本仓库 android/）

如果 Pano Scrobbler 读不到网易云，可用本仓库自带的安卓 App：

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

安装后打开 App：填 api_key / api_secret → 保存凭据 → 授权 last.fm → 开启通知使用权 → 在网易云放歌即可。凭据与 last.fm 会话会保存在本地，之后重新打开 App 会自动读取，无需再次输入；授权成功后主界面会隐藏凭据输入框，只显示登录状态。

> 安卓端计时只认 `PlaybackState.STATE_PLAYING`，缓冲（`STATE_BUFFERING`）不计入播放时长；`mediaToken=false` 的通知（如“听歌识曲正在运行”）会被忽略，不会干扰当前曲目的计时。
>
> 主界面的“last.fm 同步列表”会显示本机成功 scrobble 的记录，点“清除记录”可随时清空；后台调试信息默认折叠在“后台信息”按钮里，点按钮展开/收起，且列表与调试信息都会每秒自动刷新，无需切换页面。

## 安卓端同步（推荐 Pano Scrobbler）

安卓手机上的网易云音乐用现成 app **Pano Scrobbler** 同步，无需本仓库代码：

1. 安装 Pano Scrobbler：Google Play 搜索 `Pano Scrobbler`（包名 `com.arn.scrobble`），或从 https://github.com/kawaiiDango/pano-scrobbler/releases 下载 APK。
2. 打开后在设置里登录同一个 last.fm 账号。
3. 授予"通知使用权"：安卓设置 → 应用 → 特殊权限 → 通知使用权 → 打开 Pano Scrobbler（注意不是普通通知权限）。
4. 打开网易云音乐放歌测试，Pano 显示正在播放即成功；播放达标后自动 scrobble。
5. 国产手机（小米/华为/三星等）需要把 Pano Scrobbler 加入后台白名单、自启动、电池无限制，避免切到后台后被系统杀掉。

注意：同一台设备不要同时运行多个 scrobbler，否则可能重复记录。

## 测试

```bash
.venv/Scripts/python.exe -m pip install -r requirements-dev.txt
.venv/Scripts/python.exe -m pytest -v
```
