$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
        & (Join-Path $PSScriptRoot 'Analyze-Apk.ps1')
    if (-not $?) { throw 'Analyze-Apk.ps1 failed.' }

    & (Join-Path $PSScriptRoot 'Build-Release.ps1')
    if (-not $?) { throw 'Build-Release.ps1 failed.' }

    $built = 'D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk'
    if (!(Test-Path -Path $built)) { throw "Release artifact missing: $built" }

    $aapt = Join-Path $env:ANDROID_HOME 'build-tools\36.1.0\aapt.exe'
    $apksigner = Join-Path $env:ANDROID_HOME 'build-tools\36.1.0\apksigner.bat'
    (& $aapt dump badging $built) | Set-Content -Encoding utf8 -Path (Join-Path $root 'resource\analysis\built-release-apk-badging.txt')
    (& $apksigner verify --verbose --print-certs $built) | Set-Content -Encoding utf8 -Path (Join-Path $root 'resource\analysis\built-release-apk-signature.txt')
        if (-not $?) { throw "apksigner verify failed with exit code $LASTEXITCODE" }

    $hash = Get-FileHash -Algorithm SHA256 -Path $built
    "VERIFY_RELEASE_OK path=$built sha256=$($hash.Hash) bytes=$((Get-Item -Path $built).Length)"
} finally {
    Pop-Location
}
