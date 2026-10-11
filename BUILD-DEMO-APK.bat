@echo off
setlocal
cd /d "%~dp0"
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="
for %%P in ("%ProgramFiles%\Android\Android Studio\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr") do if not defined JAVA_HOME if exist "%%~P\bin\java.exe" set "JAVA_HOME=%%~P"
if not defined JAVA_HOME (
    where java >nul 2>nul
    if errorlevel 1 (
        echo Java was not found. Open this project in Android Studio and use its Terminal:
        echo .\gradlew.bat :app:assembleDemoDebug
        pause
        exit /b 1
    )
)
if not exist local.properties if not defined ANDROID_HOME if not defined ANDROID_SDK_ROOT (
    echo Open the INNER ShiftNestAttendance folder in Android Studio and complete Gradle Sync first.
    echo Android Studio will configure the Android SDK path in local.properties.
    pause
    exit /b 1
)
echo Building the isolated demo APK for local Firebase emulator testing...
call gradlew.bat :app:assembleDemoDebug --console=plain
if errorlevel 1 (
    echo BUILD FAILED. Copy the first error from above; no new APK was produced by this helper.
    pause
    exit /b 1
)
if not exist app\build\outputs\apk\demo\debug\app-demo-debug.apk (
    echo Build finished but the expected app-demo-debug.apk was not found.
    pause
    exit /b 1
)
copy /Y "app\build\outputs\apk\demo\debug\app-demo-debug.apk" "ShiftNestAttendance-Demo-v1.3.21.apk" >nul
if errorlevel 1 (
    echo Could not copy the APK. Use app\build\outputs\apk\demo\debug\app-demo-debug.apk directly.
    pause
    exit /b 1
)
echo APK READY: %CD%\ShiftNestAttendance-Demo-v1.3.21.apk
explorer.exe /select,"%CD%\ShiftNestAttendance-Demo-v1.3.21.apk"
pause
