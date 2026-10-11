@echo off
setlocal
cd /d "%~dp0"
title ShiftNest Attendance - Demo Setup
if not exist "demo\Start-Demo.ps1" (
 echo Extract ALL ZIP files first, then run START-DEMO.bat from the extracted ShiftNestAttendance folder.
 pause
 exit /b 1
)
echo This starts the LOCAL TEST demo only. Internet is needed for first downloads.
echo It does not log in to Firebase, deploy cloud resources or enable billing.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0demo\Start-Demo.ps1"
set "demo_result=%ERRORLEVEL%"
echo.
echo Keep the separate LOCAL BACKEND window open while testing.
echo If setup failed, send demo-logs\setup.log and the error shown above.
pause
exit /b %demo_result%
