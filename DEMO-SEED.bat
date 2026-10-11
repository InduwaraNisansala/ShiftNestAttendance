@echo off
setlocal
cd /d "%~dp0"
set "GCLOUD_PROJECT=demo-shiftnest-attendance"
set "FIRESTORE_EMULATOR_HOST=127.0.0.1:8180"
set "FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099"
node functions\demo-seed.js
if errorlevel 1 (
 echo Start DEMO-START.bat first and wait for all emulators to be ready.
 pause
 exit /b 1
)
pause
