@echo off
setlocal
cd /d "%~dp0"
echo Building Open Quest Tuner debug APK...
call gradlew.bat :app:assembleDebug :app:testDebugUnitTest --max-workers=2
if errorlevel 1 exit /b %ERRORLEVEL%
echo.
echo APK: %CD%\app\build\outputs\apk\debug\app-debug.apk
