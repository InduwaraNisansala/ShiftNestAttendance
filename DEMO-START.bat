@echo off
setlocal
cd /d "%~dp0"
where firebase >nul 2>nul
if errorlevel 1 (
 echo Run DEMO-SETUP.bat first.
 pause
 exit /b 1
)
if not exist functions\node_modules\firebase-admin (
 echo Run DEMO-SETUP.bat first.
 pause
 exit /b 1
)
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="
for %%P in ("%ProgramFiles%\Android\Android Studio\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr") do if not defined JAVA_HOME if exist "%%~P\bin\java.exe" set "JAVA_HOME=%%~P"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
where java >nul 2>nul
if errorlevel 1 (
 echo Install Java 21 or set JAVA_HOME to a suitable Android Studio jbr. See demo\DEMO-SETUP.md.
 pause
 exit /b 1
)
echo TEST BACKEND ONLY: demo-shiftnest-attendance. No firebase login or deploy is required.
echo Keep this window open while testing. After All emulators ready, run DEMO-SEED.bat in another window.
echo Stop with Ctrl+C and allow export to finish. Use trusted private Wi-Fi; do not expose ports to the Internet.
echo Java version:
java -version
if exist demo-data\firebase-export-metadata.json (
 call firebase emulators:start --project demo-shiftnest-attendance --config firebase.demo.json --only auth,firestore,functions --import demo-data --export-on-exit demo-data
) else (
 call firebase emulators:start --project demo-shiftnest-attendance --config firebase.demo.json --only auth,firestore,functions --export-on-exit demo-data
)
echo If startup failed, send the FIRST error from above. Require Java 21 for current Firestore emulators.
pause
