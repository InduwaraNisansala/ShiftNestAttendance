$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$sdk = $null
foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
    if ($candidate -and (Test-Path (Join-Path $candidate 'platform-tools\adb.exe'))) { $sdk = $candidate; break }
}
if (!$sdk -and (Test-Path (Join-Path $root 'local.properties'))) {
    $line = Get-Content (Join-Path $root 'local.properties') | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($line) { $candidate = $line.Substring(8).Replace('\:', ':').Replace('\\', '\'); if (Test-Path (Join-Path $candidate 'platform-tools\adb.exe')) { $sdk = $candidate } }
}
if (!$sdk) { throw 'ADB not found. Open the project in Android Studio and set your SDK location.' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$devices = @(& $adb devices | Where-Object { $_ -match '^emulator-\d+\s+device$' })
if ($devices.Count -ne 1) { throw 'Start exactly one Android emulator, then run this file again.' }
$serial = ($devices[0] -split '\s+')[0]
foreach ($port in @(9099, 8180, 5001)) {
    & $adb -s $serial reverse "tcp:$port" "tcp:$port"
    if ($LASTEXITCODE -ne 0) { throw "Could not connect port $port." }
}
& $adb -s $serial shell am force-stop com.shiftnest.attendance.demo
Write-Host 'READY: Open ShiftNest Attendance Demo; use address 127.0.0.1 and tap Connect.' -ForegroundColor Green
Write-Host 'Keep LOCAL BACKEND running. Repeat after restarting the emulator.'
