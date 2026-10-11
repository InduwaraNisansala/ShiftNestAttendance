@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
 echo Install Node.js 22 LTS, close this window and retry. Read demo\DEMO-SETUP.md.
 pause
 exit /b 1
)
call npm install -g firebase-tools
if errorlevel 1 goto failed
pushd functions
call npm install
if errorlevel 1 (
 popd
 goto failed
)
popd
echo Demo dependencies installed. This script did not deploy or enable billing.
echo Next: run DEMO-START.bat. Read demo\DEMO-SETUP.md for Java and phone setup.
pause
exit /b 0
:failed
echo Setup failed. Copy the first error shown above.
pause
exit /b 1
