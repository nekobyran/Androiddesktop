$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -Path (Join-Path $PSScriptRoot '..')).Path
$source = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path -Path $source)) { throw "Debug APK missing, run command/Build-Debug.ps1 first: $source" }
$destDir = 'D:\vibecoding\release\Androiddesktop\debug'
New-Item -ItemType Directory -Force -Path $destDir | Out-Null
$dest = Join-Path $destDir 'Androiddesktop-debug.apk'
Copy-Item -Path $source -Destination $dest -Force
$hash = Get-FileHash -Algorithm SHA256 -Path $dest
"COPY_OK path=$dest sha256=$($hash.Hash) bytes=$((Get-Item -Path $dest).Length)"
