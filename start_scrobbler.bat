@echo off
rem NetEase -> last.fm bridge: autostart launcher (window stays open after exit)
chcp 65001 > nul
title lastfm_net163 scrobbler
cd /d "%~dp0"
".venv\Scripts\python.exe" -m lastfm_net163.main
echo.
echo [scrobbler 已退出] 按任意键关闭窗口...
pause > nul
