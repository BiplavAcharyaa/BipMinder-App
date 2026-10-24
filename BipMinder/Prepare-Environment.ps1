# Installs JDK 17 + Android SDK (if missing) and writes local.properties.
# Run from this folder, dot-sourced so JAVA_HOME / ANDROID_HOME stay set in your session:
#   . .\Prepare-Environment.ps1
# Then build with:  .\gradlew.bat assembleDebug
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Here  = Split-Path -Parent $MyInvocation.MyCommand.Path
$Tools = Join-Path $HOME 'BipMinderTools'
$Sdk   = Join-Path $Tools 'android-sdk'
New-Item -ItemType Directory -Force -Path $Tools, $Sdk | Out-Null

function Test-Jdk($h) {
    if (-not $h) { return $false }
    $rel = Join-Path $h 'release'
    if (-not (Test-Path $rel)) { return $false }
    if ((Get-Content $rel -Raw) -match 'JAVA_VERSION="(\d+)') { $v = [int]$Matches[1]; return ($v -ge 17 -and $v -le 21) }
    return $false
}
if (Test-Jdk $env:JAVA_HOME) { $jdk = $env:JAVA_HOME }
else {
    $local = Get-ChildItem $Tools -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $local) {
        Write-Host 'Downloading Temurin JDK 17...'
        $z = Join-Path $Tools 'jdk17.zip'
        Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $z
        Expand-Archive $z $Tools -Force; Remove-Item $z -Force
        $local = Get-ChildItem $Tools -Directory -Filter 'jdk-17*' | Select-Object -First 1
    }
    $jdk = $local.FullName
}
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

$sdkm = Join-Path $Sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $sdkm)) {
    Write-Host 'Downloading Android command-line tools...'
    $z = Join-Path $Tools 'cmdline-tools.zip'
    Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $z
    $tmp = Join-Path $Tools 'cmdtmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive $z $tmp -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $Sdk 'cmdline-tools') | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $Sdk 'cmdline-tools\latest')
    Remove-Item $tmp -Recurse -Force; Remove-Item $z -Force
}
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk

$prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
(1..80 | ForEach-Object { 'y' }) | & $sdkm "--sdk_root=$Sdk" --licenses | Out-Null
& $sdkm "--sdk_root=$Sdk" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
$code = $LASTEXITCODE
$ErrorActionPreference = $prev
if ($code -ne 0) { throw 'sdkmanager failed' }

[IO.File]::WriteAllText((Join-Path $Here 'local.properties'), ("sdk.dir=" + ($Sdk -replace '\\', '/') + "`n"), (New-Object Text.UTF8Encoding $false))
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $Sdk, 'User')
Write-Host "Ready. JAVA_HOME=$jdk  ANDROID_HOME=$Sdk" -ForegroundColor Green
Write-Host 'Now run:  .\gradlew.bat assembleDebug' -ForegroundColor Green
