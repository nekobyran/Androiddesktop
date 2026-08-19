$ErrorActionPreference = 'Stop'

$root = (Resolve-Path -Path (Join-Path $PSScriptRoot '..')).Path
$env:JAVA_HOME = 'D:\vibecoding\sdk\jdk'
$env:ANDROID_HOME = 'D:\vibecoding\sdk\android'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\build-tools\36.1.0;$env:Path"

$releaseDir = 'D:\vibecoding\release\Androiddesktop\release'
$artifactDir = Join-Path $root 'artifacts'
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null

$gradle = Join-Path $root 'gradlew.bat'
if (!(Test-Path -Path $gradle)) { throw "Gradle wrapper missing: $gradle" }

& $gradle --offline ':app:assembleRelease' '--stacktrace'
if (-not $?) { throw "Gradle assembleRelease failed with exit code $LASTEXITCODE" }

$unsigned = Join-Path $root 'app\build\outputs\apk\release\app-release-unsigned.apk'
if (!(Test-Path -Path $unsigned)) { throw "Unsigned release APK missing: $unsigned" }

$zipalign = Join-Path $env:ANDROID_HOME 'build-tools\36.1.0\zipalign.exe'
$apksigner = Join-Path $env:ANDROID_HOME 'build-tools\36.1.0\apksigner.bat'
$aapt = Join-Path $env:ANDROID_HOME 'build-tools\36.1.0\aapt.exe'
foreach ($tool in @($zipalign, $apksigner, $aapt)) {
    if (!(Test-Path -Path $tool)) { throw "Required Android build tool missing: $tool" }
}

function Read-LocalProperties([string]$path) {
    $map = @{}
    foreach ($line in Get-Content -Path $path -Encoding utf8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $index = $trimmed.IndexOf('=')
        if ($index -le 0) { continue }
        $map[$trimmed.Substring(0, $index).Trim()] = $trimmed.Substring($index + 1).Trim()
    }
    return $map
}

$ks = $env:ANDROIDDESKTOP_KEYSTORE
$ksAlias = $env:ANDROIDDESKTOP_KEY_ALIAS
$ksPass = $env:ANDROIDDESKTOP_KEYSTORE_PASS
$keyPass = $env:ANDROIDDESKTOP_KEY_PASS
$usingEnv = -not [string]::IsNullOrWhiteSpace($ks) -and -not [string]::IsNullOrWhiteSpace($ksAlias) -and -not [string]::IsNullOrWhiteSpace($ksPass)

if ($usingEnv) {
    if ([string]::IsNullOrWhiteSpace($keyPass)) { $keyPass = $ksPass }
} else {
    $propertiesPath = Join-Path $root 'app\signing\release.local.properties'
    if (!(Test-Path -Path $propertiesPath)) {
        throw "Stable release signing is not initialized. Run .\command\Initialize-ReleaseSigning.ps1 once, back up the generated key/config securely, then rerun this build."
    }
    $properties = Read-LocalProperties $propertiesPath
    foreach ($required in @('keystore', 'alias', 'storePassword', 'keyPassword')) {
        if (!$properties.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($properties[$required])) {
            throw "Missing '$required' in $propertiesPath"
        }
    }
    $ks = $properties['keystore']
    if (![System.IO.Path]::IsPathRooted($ks)) { $ks = Join-Path $root $ks }
    $ksAlias = $properties['alias']
    $ksPass = $properties['storePassword']
    $keyPass = $properties['keyPassword']
}

if (!(Test-Path -Path $ks)) { throw "Release keystore missing: $ks. Refusing to generate a replacement key." }

$aligned = Join-Path $releaseDir 'Androiddesktop-release-aligned.apk'
$signed = Join-Path $releaseDir 'Androiddesktop-release.apk'
$signatureReport = Join-Path $releaseDir 'Androiddesktop-release-signature.txt'
$badgingReport = Join-Path $releaseDir 'Androiddesktop-release-badging.txt'
$hashReport = Join-Path $releaseDir 'Androiddesktop-release.sha256'

foreach ($path in @($aligned, $signed)) {
    if (Test-Path -Path $path) { Remove-Item -Path $path -Force }
}

try {
    & $zipalign -p -f 4 $unsigned $aligned
    if (-not $?) { throw "zipalign failed with exit code $LASTEXITCODE" }

    & $apksigner sign --ks $ks --ks-key-alias $ksAlias --ks-pass "pass:$ksPass" --key-pass "pass:$keyPass" --out $signed $aligned
    if (-not $?) { throw "apksigner sign failed with exit code $LASTEXITCODE" }

    (& $apksigner verify --verbose --print-certs $signed) | Set-Content -Encoding utf8 -Path $signatureReport
    if (-not $?) { throw "apksigner verify failed with exit code $LASTEXITCODE" }

    (& $aapt dump badging $signed) | Set-Content -Encoding utf8 -Path $badgingReport
    if (-not $?) { throw "aapt badging failed with exit code $LASTEXITCODE" }

    $hash = Get-FileHash -Algorithm SHA256 -Path $signed
    "$($hash.Hash)  Androiddesktop-release.apk" | Set-Content -Encoding ascii -Path $hashReport

    Copy-Item -Path $signed -Destination (Join-Path $artifactDir 'Androiddesktop-release.apk') -Force
    Copy-Item -Path $signatureReport -Destination (Join-Path $artifactDir 'Androiddesktop-release-signature.txt') -Force
    Copy-Item -Path $badgingReport -Destination (Join-Path $artifactDir 'Androiddesktop-release-badging.txt') -Force
    Copy-Item -Path $hashReport -Destination (Join-Path $artifactDir 'Androiddesktop-release.sha256') -Force

    "RELEASE_OK path=$signed sha256=$($hash.Hash) bytes=$((Get-Item -Path $signed).Length) signerSource=$(if($usingEnv){'environment'}else{'app/signing/release.local.properties'}) artifactDir=$artifactDir"
} finally {
    if (Test-Path -Path $aligned) { Remove-Item -Path $aligned -Force }
}
