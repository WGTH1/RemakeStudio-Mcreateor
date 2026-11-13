@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0platform\windows\run-remake-studio.ps1" -Root "%~dp0."
set "RS_EXIT=%ERRORLEVEL%"
if not "%RS_EXIT%"=="0" pause
exit /b %RS_EXIT%
