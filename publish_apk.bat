@echo off
rem Build and publish lastfm_net163 APK for in-app update
setlocal
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

cd android
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
  echo [build failed]
  pause
  exit /b 1
)
cd ..

if not exist "apk" mkdir apk
copy /y "androidppuild\outputspk\debugpp-debug.apk" "apkpp-debug.apk" > nul

git add -f "apkpp-debug.apk"
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "chore: 更新安卓 APK"
)
git push

echo [published] 手机 App 内点“检查更新”即可安装。
pause
