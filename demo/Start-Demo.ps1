# Windows x64 local-demo bootstrap. No machine-wide installs or policy/firewall changes.
param([switch]$BackendWindow, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$tools = Join-Path $root '.demo-tools'
$logs = Join-Path $root 'demo-logs'
New-Item -ItemType Directory -Force -Path $tools,$logs | Out-Null
$transcript = if ($BackendWindow) { 'backend.log' } else { 'setup.log' }
Start-Transcript -Path (Join-Path $logs $transcript) -Force | Out-Null
$stage = 'checking the extracted project'
function Say([string]$Text) { Write-Host "`n$Text" -ForegroundColor Cyan }
function Download-Checked([string]$Url,[string]$Destination,[string]$Checksum) {
    if ($Checksum -notmatch '^[a-fA-F0-9]{64}$') { throw 'The publisher did not provide a valid SHA-256 checksum.' }
    $uri = [uri]$Url
    if ($uri.Scheme -ne 'https') { throw 'Only HTTPS tool downloads are allowed.' }
    $part = "$Destination.part"
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $part -TimeoutSec 1200
        if ((Get-FileHash -LiteralPath $part -Algorithm SHA256).Hash -ne $Checksum) { throw 'Tool download checksum mismatch. Retry; do not use this download.' }
        Move-Item -LiteralPath $part -Destination $Destination -Force
    } finally { if (Test-Path -LiteralPath $part) { Remove-Item -LiteralPath $part -Force } }
}
function Java21([string]$Directory) {
    if (!$Directory -or !(Test-Path -LiteralPath (Join-Path $Directory 'bin\java.exe'))) { return $false }
    $release = Join-Path $Directory 'release'
    return (Test-Path -LiteralPath $release) -and ((Get-Content -LiteralPath $release -Raw) -match 'JAVA_VERSION="21[.+"]')
}
function Port-Open([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync('127.0.0.1',$Port)
        if (!$task.Wait(500)) { return $false }
        return $client.Connected
    } catch { return $false } finally { $client.Dispose() }
}
function Ready-Project {
    try {
        $auth = Invoke-RestMethod -Uri 'http://127.0.0.1:9099/emulator/v1/projects/demo-shiftnest-attendance/verificationCodes' -TimeoutSec 4
        # Emulator-only owner token inspects the seed marker without weakening app rules.
        $fire = Invoke-RestMethod -Uri 'http://127.0.0.1:8180/v1/projects/demo-shiftnest-attendance/databases/(default)/documents/demoSetup/initialAttendance' -Headers @{Authorization='Bearer owner'} -TimeoutSec 4
        try {
            $lookup = Invoke-RestMethod -Uri 'http://127.0.0.1:5001/demo-shiftnest-attendance/asia-south1/lookupEmployee' -Method Post -ContentType 'application/json' -Body '{"data":{"phone":"+94770000001"}}' -TimeoutSec 8
            return ($lookup.result.registered -eq $true)
        } catch {
            # An archived sample phone still proves that the callable exists; preserve user edits.
            $body = $_.ErrorDetails.Message
            if (!$body -and $_.Exception.Response) {
                $response = $_.Exception.Response
                if ($response -is [System.Net.HttpWebResponse]) {
                    $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
                    try { $body = $reader.ReadToEnd() } finally { $reader.Dispose() }
                }
            }
            if (!$body) { return $false }
            try { $errorBody = $body | ConvertFrom-Json; return ($errorBody.error.status -eq 'NOT_FOUND' -and $errorBody.error.message -match 'not registered') } catch { return $false }
        }
    } catch { return $false }
}
try {
    if (![Environment]::Is64BitOperatingSystem -or $env:PROCESSOR_ARCHITECTURE -eq 'ARM64' -or $env:PROCESSOR_ARCHITEW6432 -eq 'ARM64') { throw 'This helper supports Windows x64. Use the manual guide for other architectures.' }
    if (!(Test-Path 'firebase.demo.json') -or !(Test-Path 'functions\demo-seed.js')) { throw 'Extract the entire combined ZIP before running this launcher.' }
    if ($root -match '[\r\n"]') { throw 'Move the extracted folder to a simple path such as F:\ShiftNestAttendance-v1.3.24\ShiftNestAttendance.' }
    $config = Get-Content 'firebase.demo.json' -Raw | ConvertFrom-Json
    if ($config.emulators.auth.port -ne 9099 -or $config.emulators.firestore.port -ne 8180 -or $config.emulators.functions.port -ne 5001) { throw 'Demo ports were changed. Restore the supplied demo configuration.' }
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue'
    if (!$BackendWindow) {
        $stage = 'finding/downloading Node.js 22'
        Say '[1/6] Preparing Node.js 22 (first download may take time)...'
        $nodePath = $null
        $nodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
        if ($nodeCommand) {
            $installed = & $nodeCommand.Source --version
            if ($LASTEXITCODE -eq 0 -and $installed -match '^v22\.') { $nodePath = $nodeCommand.Source }
        }
        if (!$nodePath) {
            $savedNode = @(Get-ChildItem -LiteralPath $tools -Directory -Filter 'node-v22.*-win-x64' | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'node.exe') })
            if ($savedNode.Count) { $nodePath = Join-Path $savedNode[0].FullName 'node.exe' }
        }
        if (!$nodePath) {
            $base = 'https://nodejs.org/download/release/latest-v22.x/'
            $sumText = (Invoke-WebRequest -UseBasicParsing -Uri ($base+'SHASUMS256.txt') -TimeoutSec 60).Content
            $match = [regex]::Match($sumText,'(?m)^([a-fA-F0-9]{64})\s+(node-v22\.\d+\.\d+-win-x64\.zip)\s*$')
            if (!$match.Success) { throw 'Could not resolve the official Node.js 22 Windows ZIP. Check internet and retry.' }
            $zip = Join-Path $tools $match.Groups[2].Value
            Download-Checked ($base+$match.Groups[2].Value) $zip $match.Groups[1].Value
            Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
            $nodePath = Join-Path $tools ($match.Groups[2].Value.Replace('.zip','')+'\node.exe')
        }
        if (!(Test-Path -LiteralPath $nodePath)) { throw 'Node.js extraction did not produce node.exe.' }
        $env:PATH = (Split-Path $nodePath) + ';' + $env:PATH
        $npmPath = Join-Path (Split-Path $nodePath) 'npm.cmd'
        if (!(Test-Path -LiteralPath $npmPath)) { throw 'npm.cmd is missing from Node.js. Repair its installation and retry.' }
        & $nodePath --version | Out-Host
        $stage = 'finding/downloading Java 21'
        Say '[2/6] Preparing Java 21...'
        $jdk = $null
        $candidates = @($env:JAVA_HOME,(Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),(Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'))
        $candidates += @(Get-ChildItem -LiteralPath $tools -Directory -Filter 'jdk-21*' | ForEach-Object { $_.FullName })
        foreach ($candidate in $candidates) { if (Java21 $candidate) { $jdk = $candidate; break } }
        if (!$jdk) {
            $assets = @(Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse' -TimeoutSec 60)
            if (!$assets.Count) { throw 'Could not resolve the official Temurin Java 21 download.' }
            $package = $assets[0].binary.package
            $downloadUri = [uri]$package.link
            if ($downloadUri.Host -ne 'github.com' -or $downloadUri.AbsolutePath -notlike '/adoptium/temurin21-binaries/*' -or $package.name -notmatch '^[a-zA-Z0-9_.+-]+\.zip$') { throw 'Unexpected Java download metadata; setup stopped.' }
            $zip = Join-Path $tools $package.name
            Download-Checked $package.link $zip $package.checksum
            Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
            foreach ($candidate in @(Get-ChildItem -LiteralPath $tools -Directory -Filter 'jdk-21*')) { if (Java21 $candidate.FullName) { $jdk = $candidate.FullName; break } }
        }
        if (!$jdk) { throw 'Java 21 was not found after extraction.' }
        $env:JAVA_HOME = $jdk
        $env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH
        $stage = 'installing Firebase CLI and backend packages'
        Say '[3/6] Installing local Firebase tools and backend packages...'
        $cliFolder = Join-Path $tools 'firebase-cli'
        New-Item -ItemType Directory -Force -Path $cliFolder | Out-Null
        $firebaseJs = Join-Path $cliFolder 'node_modules\firebase-tools\lib\bin\firebase.js'
        if (!(Test-Path -LiteralPath $firebaseJs)) {
            & $npmPath install --prefix $cliFolder firebase-tools --no-audit --no-fund | Out-Host
            if ($LASTEXITCODE -ne 0) { throw 'Firebase CLI install failed. Check the npm error above and internet access.' }
        }
        if (!(Test-Path 'functions\node_modules\firebase-admin') -or !(Test-Path 'functions\node_modules\firebase-functions')) {
            Push-Location 'functions'
            try { & $npmPath install --no-audit --no-fund | Out-Host; if ($LASTEXITCODE -ne 0) { throw 'Backend dependency installation failed.' } } finally { Pop-Location }
        }
        if (!(Test-Path -LiteralPath $firebaseJs)) { throw 'Firebase CLI entry point is missing after install.' }
        $env:SHIFTNEST_DEMO_NODE = $nodePath
        $env:SHIFTNEST_DEMO_FIREBASE = $firebaseJs
        & $nodePath $firebaseJs --version | Out-Host
        $stage = 'starting the local backend'
        Say '[4/6] Starting local Auth, Firestore and Functions...'
        $occupied = @(9099,8180,5001 | Where-Object { Port-Open $_ })
        if ($occupied.Count -gt 0 -and $occupied.Count -ne 3) { throw 'Some demo ports are already occupied. Stop the previous demo backend with Ctrl+C and retry. No process was killed.' }
        if ($occupied.Count -eq 3) {
            if (!(Ready-Project)) { throw 'Ports are occupied by an unverified backend. Stop it yourself with Ctrl+C, then retry this launcher.' }
            Write-Host 'Existing seeded local demo backend is ready; reusing it.'
        } else {
            $script = Join-Path $PSScriptRoot 'Start-Demo.ps1'
            $backend = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$script+'"'),'-BackendWindow') -WorkingDirectory $root -PassThru
            $deadline = (Get-Date).AddMinutes(10)
            $portsReady = $false
            while ((Get-Date) -lt $deadline) {
                $backend.Refresh()
                if ($backend.HasExited) { throw 'Backend window closed before startup completed. Read demo-logs\backend.log.' }
                if ((Port-Open 9099) -and (Port-Open 8180) -and (Port-Open 5001)) { $portsReady = $true; break }
                Write-Host 'Waiting for emulator downloads/startup; keep both windows open...'
                Start-Sleep -Seconds 3
            }
            if (!$portsReady) { throw 'Backend did not become ready within 10 minutes. Check its window and backend.log.' }
        }
        $stage = 'seeding and checking local test accounts'
        Say '[5/6] Creating test accounts and checking all three services...'
        $env:GCLOUD_PROJECT = 'demo-shiftnest-attendance'
        $env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8180'
        $env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099'
        & $nodePath 'functions\demo-seed.js' | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'Test account seeding failed. Check backend.log and the seed error above.' }
        $deadline = (Get-Date).AddMinutes(2)
        $verified = $false
        while ((Get-Date) -lt $deadline) { if (Ready-Project) { $verified = $true; break }; Start-Sleep -Seconds 2 }
        if (!$verified) { throw 'Auth/Firestore/lookupEmployee verification failed. Do not retry app login yet; send setup.log and backend.log.' }
        $stage = 'building the demo APK'
        Say '[6/6] Local backend verified. Preparing the demo APK...'
        $sdk = $null
        foreach ($candidate in @($env:ANDROID_HOME,$env:ANDROID_SDK_ROOT,(Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
            if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'platform-tools'))) { $sdk = $candidate; break }
        }
        if (!$sdk -and (Test-Path 'local.properties')) {
            $line = Get-Content 'local.properties' | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
            if ($line) { $candidate = $line.Substring(8).Replace('\:',':').Replace('\\','\'); if (Test-Path -LiteralPath $candidate) { $sdk = $candidate } }
        }
        if ($sdk) { $env:ANDROID_HOME = $sdk; $env:ANDROID_SDK_ROOT = $sdk }
        if ($SkipBuild) { Write-Host 'APK build skipped. Use the already-installed Demo app.' }
        elseif (!$sdk) { Write-Host 'Backend is ready, but Android SDK was not found. Open this project in Android Studio, complete Sync and run demoDebug. APK was not built.' -ForegroundColor Yellow }
        else {
            & '.\gradlew.bat' ':app:assembleDemoDebug' '--console=plain' | Out-Host
            if ($LASTEXITCODE -ne 0) { throw 'APK build failed. Backend remains running. Read the FIRST Gradle error above.' }
            $apk = Join-Path $root 'app\build\outputs\apk\demo\debug\app-demo-debug.apk'
            if (!(Test-Path -LiteralPath $apk)) { throw 'Build returned without the expected APK.' }
            $copy = Join-Path $root 'ShiftNestAttendance-Demo-v1.3.24.apk'
            Copy-Item -LiteralPath $apk -Destination $copy -Force
            Write-Host "APK READY: $copy" -ForegroundColor Green
            $adb = Join-Path $sdk 'platform-tools\adb.exe'
            if (Test-Path -LiteralPath $adb) {
                $listing = & $adb devices
                if ($LASTEXITCODE -ne 0) { Write-Host 'ADB check failed; install the APK manually.' }
                else {
                    $devices = @($listing | Where-Object { $_ -match '^\S+\s+device$' })
                    if ($devices.Count -eq 1) { $serial = ($devices[0] -split '\s+')[0]; & $adb -s $serial install -r $copy | Out-Host; if ($LASTEXITCODE -ne 0) { Write-Host 'APK auto-install failed. Use Android Studio or the APK file; existing app data was not cleared.' -ForegroundColor Yellow } }
                    else { Write-Host 'Select a device in Android Studio or install the APK manually. No device was selected automatically.' }
                }
            }
        }
        Say 'READY: Keep the LOCAL BACKEND window open. OTP codes appear there.'
        Write-Host 'Virtual phone IP: 10.0.2.2. Real phone: your PC Wi-Fi IPv4 (same trusted Wi-Fi).'
        Write-Host 'Admin: admin@shiftnest.test / ShiftNestDemo123!'
        Write-Host 'Test employee phones: 0770000001, 0770000002, 0770000003.'
        Write-Host 'No real SMS, real push, live project deployment or billing changes.'
        Stop-Transcript | Out-Null
        exit 0
    } else {
        $stage = 'running local emulators'
        if (!$env:SHIFTNEST_DEMO_NODE -or !$env:SHIFTNEST_DEMO_FIREBASE -or !(Java21 $env:JAVA_HOME)) { throw 'Start this backend with START-DEMO.bat so its tools are prepared.' }
        $Host.UI.RawUI.WindowTitle = 'ShiftNest Attendance LOCAL BACKEND - KEEP OPEN'
        Say 'LOCAL TEST BACKEND. Keep open. OTP codes appear here. Stop with Ctrl+C and wait for data export.'
        $argsList = @($env:SHIFTNEST_DEMO_FIREBASE,'emulators:start','--project','demo-shiftnest-attendance','--config','firebase.demo.json','--only','auth,firestore,functions','--export-on-exit','demo-data')
        if (Test-Path 'demo-data\firebase-export-metadata.json') { $argsList += @('--import','demo-data') }
        & $env:SHIFTNEST_DEMO_NODE @argsList | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'Firebase emulator startup/run failed. Read the first error in this window.' }
        Stop-Transcript | Out-Null
        exit 0
    }
} catch {
    Write-Host "`nFAILED while $stage" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host "Log: $(Join-Path $logs $transcript)"
    if ($BackendWindow) { Read-Host 'Press Enter after noting this error' | Out-Null }
    try { Stop-Transcript | Out-Null } catch {}
    exit 1
}
