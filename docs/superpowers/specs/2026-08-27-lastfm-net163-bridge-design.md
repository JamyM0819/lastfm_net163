# 网易云音乐 → last.fm 桥接程序设计

日期：2026-08-27
状态：已确认，待实施

## 目标

在 Windows 上常驻运行一个命令行程序，把网易云音乐 Windows 桌面客户端正在播放的歌曲，在达到 last.fm 标准后自动 scrobble 到用户的 last.fm 账号。

MVP 范围：

- 自动 scrobble（唯一勾选的功能）。
- 内建 last.fm 标准达标规则：曲目时长 ≥ 30 秒，且（已播放 ≥ 50% 或已播放 ≥ 4 分钟）时提交一次。
- 不做 Now Playing 实时通知、不做失败重试队列、不做系统托盘界面（后续可扩展）。

## 读取方案

使用 Windows 系统媒体会话（SMTC，`GlobalSystemMediaTransportControlsSessionManager`）：

- 网易云 Windows 桌面客户端已接入系统媒体控制，会暴露歌名、歌手、专辑、播放状态、位置、时长。
- 程序不注入、不读内存、不碰网易云进程。
- 通过会话的 `SourceAppUserModelId` 过滤网易云会话：默认匹配包含 `cloudmusic` 或 `netease` 的 AUMID。

## 架构与模块

Python 3.11+，命令行程序，模块划分如下：

| 模块 | 职责 |
|---|---|
| `config.py` | 读写 `%APPDATA%\lastfm_net163\config.toml`：`api_key`、`api_secret`、`session_key`、网易云 AUMID 匹配规则 |
| `smtc.py` | 用 `winsdk` 监听 Windows 媒体会话，过滤网易云会话，产出当前曲目（title/artist/album/duration/position）与播放状态 |
| `lastfm.py` | last.fm 授权（浏览器授权 + 轮询拿 `session_key`）和 `track.scrobble` 提交 |
| `scrobbler.py` | 状态机：跟踪同一首歌的累计播放进度，判断是否达标，达标后提交并去重，切歌重置 |
| `main.py` | 组合以上模块，常驻运行，Ctrl+C 退出 |

依赖（`requirements.txt`）：

- `winsdk`（读取 Windows 媒体会话）
- `requests`（调用 last.fm API）
- 标准库：`tomllib`

## 数据流

```
SMTC 事件
  → smtc.py 解析 title/artist/album/duration/position/status
  → scrobbler.py 状态机：
       - 切歌：重置状态
       - 播放中：累计进度
       - 达标：调用 lastfm.py 提交 track.scrobble
       - 已提交：同一首歌不再重复提交，直到切歌
  → lastfm.py 调 last.fm API 提交
```

## 配置与首次授权

- 配置文件：`%APPDATA%\lastfm_net163\config.toml`。
- 首次运行：
  1. 用户在 last.fm（https://www.last.fm/api/account/create）免费申请 `api_key` / `api_secret`，填入配置或按提示输入。
  2. 程序用 `api_key` 请求 token，打开浏览器授权页。
  3. 用户在浏览器中授权 last.fm。
  4. 程序轮询 `auth.getSession` 拿到 `session_key`，保存到 `config.toml`。
- 之后运行直接使用已保存的 `session_key`，无需重复登录。

## 达标规则

对每一首歌只提交一次，条件同时满足：

1. 曲目时长 ≥ 30 秒；
2. `已播放位置 / 总时长 ≥ 0.5` 或 `已播放位置 ≥ 240 秒`。

切歌（曲目标识变化）时重置状态机。

## 错误处理

- SMTC 会话暂时消失（客户端退出/暂停）：打印日志并继续轮询，不崩溃。
- last.fm 返回错误：打印 API 错误信息，当前曲目放弃提交（重试队列不在 MVP）。
- 网络异常：打印错误后继续运行。
- 元数据缺失（无标题/歌手）：跳过，避免提交脏数据。

## 测试

- 单元测试：
  - `scrobbler.py` 状态机：达标判断、切歌重置、同一首歌去重、不足 30 秒过滤。
  - `lastfm.py`：请求签名、参数组装（mock 网络）。
- 集成验证：
  - 手工运行，确认 `smtc.py` 能读到网易云正在播放的歌曲（用本机网易云客户端验证）。
  - 用一个测试账号/测试 API key 验证 `track.scrobble` 真实提交。

## 非目标（MVP 不做）

- 系统托盘、GUI、开机自启。
- Now Playing 实时通知。
- 失败重试队列。
- 手机端、网页版网易云。
- 历史播放记录补录。
