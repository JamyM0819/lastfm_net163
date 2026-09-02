@echo off
rem NetEase -> last.fm bridge launcher
chcp 65001 > nul
title lastfm_net163 scrobbler
cd /d "%~dp0"
".venv\Scripts\python.exe" -m lastfm_net163.main
