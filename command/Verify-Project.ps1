$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    & (Join-Path $PSScriptRoot 'Analyze-Apk.ps1')
    if ($LASTEXITCODE -ne 0) { throw "Analyze-Apk.ps1 failed with exit code $LASTEXITCODE" }

    & (Join-Path $PSScriptRoot 'Build-Debug.ps1')
    if ($LASTEXITCODE -ne 0) { throw "Build-Debug.ps1 failed with exit code $LASTEXITCODE" }

    $built = 'D:\vibecoding\release\Androiddesktop\debug\Androiddesktop-debug.apk'
    if (!(Test-Path -Path $built)) { throw "Release artifact missing: $built" }

    $aapt = Join-Path $env:ANDROID_HOME 'build-tools/36.1.0/aapt.exe'
    (& $aapt dump badging $built) | Set-Content -Encoding utf8 -Path (Join-Path $root 'resource\analysis\built-apk-badging.txt')
    $hash = Get-FileHash -Algorithm SHA256 -Path $built
    "VERIFY_OK path=$built sha256=$($hash.Hash) bytes=$((Get-Item -Path $built).Length)"
} finally {
    Pop-Location
}
