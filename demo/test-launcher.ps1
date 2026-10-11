$ErrorActionPreference='Stop'
Set-StrictMode -Version 2.0
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Start-Demo.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){$errors|Out-String|Write-Host;exit 1}
foreach($fn in $ast.FindAll({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst]},$true)){. ([scriptblock]::Create($fn.Extent.Text))}
$n=0
function Check($Condition,$Message){if(!$Condition){throw $Message};$script:n++}
# Test download checksum failures fail closed and remove partial downloads.
function Invoke-WebRequest {param($UseBasicParsing,$Uri,$OutFile,$TimeoutSec) [IO.File]::WriteAllText($OutFile,'publisher download')}
# Switch parameter binding needs explicit switch.
Remove-Item Function:\Invoke-WebRequest
function Invoke-WebRequest {param([switch]$UseBasicParsing,$Uri,$OutFile,$TimeoutSec) [IO.File]::WriteAllText($OutFile,'publisher download')}
$base=Join-Path ([IO.Path]::GetTempPath()) ('shiftnest-check-'+[guid]::NewGuid())
New-Item -ItemType Directory $base|Out-Null
try {
 $src=Join-Path $base 'src';[IO.File]::WriteAllText($src,'publisher download');$sha=(Get-FileHash $src -Algorithm SHA256).Hash
 $target=Join-Path $base 'download.zip'; Download-Checked 'https://nodejs.org/example.zip' $target $sha
 Check ((Get-Content $target -Raw) -eq 'publisher download') 'Verified download not saved'
 $bad=Join-Path $base 'bad.zip';$failed=$false;try{Download-Checked 'https://nodejs.org/example.zip' $bad ('0'*64)}catch{$failed=$true}
 Check $failed 'Checksum mismatch accepted';Check (!(Test-Path $bad)) 'Bad final download left';Check (!(Test-Path "$bad.part")) 'Partial bad download left'
 $failed=$false;try{Download-Checked 'http://nodejs.org/example.zip' $bad $sha}catch{$failed=$true};Check $failed 'Non-HTTPS accepted'
 $failed=$false;try{Download-Checked 'https://nodejs.org/example.zip' $bad 'not-a-checksum'}catch{$failed=$true};Check $failed 'Invalid publisher hash accepted'
 foreach($version in @('21.0.8','17.0.9')) {
  $jdk=Join-Path $base ('jdk-'+$version);New-Item -ItemType Directory (Join-Path $jdk 'bin')|Out-Null
  [IO.File]::WriteAllText((Join-Path $jdk 'bin/java.exe'),'test');[IO.File]::WriteAllText((Join-Path $jdk 'release'),('JAVA_VERSION="'+$version+'"'))
  Check ((Java21 $jdk) -eq $version.StartsWith('21.')) 'Wrong Java version selection'
 }
 Check (!(Java21 '')) 'Empty Java path accepted'
 # Test service health requires callable registration result and marker owner header.
 $script:mode='healthy';$script:seenOwner=$false
 function Invoke-RestMethod {param($Uri,$TimeoutSec,$Headers,$Method,$ContentType,$Body)
  if($script:mode -eq 'offline'){throw 'offline'}
  if($Uri -like '*8180*'){$script:seenOwner=$Headers.Authorization -eq 'Bearer owner';return @{name='demoSetup/initialAttendance'}}
  if($Uri -like '*5001*'){
   if($script:mode -eq 'bad'){return @{result=@{registered=$false}}}
   if($script:mode -eq 'archived'){$e=New-Object System.Management.Automation.ErrorRecord ([Exception]::new('404')), 'mock', ([System.Management.Automation.ErrorCategory]::ObjectNotFound), $null;$e.ErrorDetails=[System.Management.Automation.ErrorDetails]::new('{"error":{"status":"NOT_FOUND","message":"This phone number is not registered."}}');throw $e}
   return @{result=@{registered=$true}}
  };return @{verificationCodes=@()}
 }
 Check (Ready-Project) 'Healthy services rejected';Check $script:seenOwner 'Marker bypass missing (local only)'
 $script:mode='bad';Check (!(Ready-Project)) 'Unregistered/no success accepted'
 $script:mode='offline';Check (!(Ready-Project)) 'Offline backend accepted'
 $script:mode='archived';Check (Ready-Project) 'Archived demo sample should not prevent existing data reuse'
 Write-Host "$n launcher safety/service checks PASS; PowerShell syntax PASS. Windows processes not exercised."
} finally {Remove-Item -LiteralPath $base -Recurse -Force}
