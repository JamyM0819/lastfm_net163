@echo off
rem NetEase -> last.fm bridge: run and append log to %APPDATA%\lastfm_net163\scrobbler.log
cd /d "F:\reasonix_project\lastfm_net163\dist\lastfm_net163"
"lastfm_net163.exe" >> "%APPDATA%\lastfm_net163\scrobbler.log" 2>&1
