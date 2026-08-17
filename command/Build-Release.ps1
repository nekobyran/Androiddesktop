$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -Path (Join-Path $PSScriptRoot '..')).Path
$env:JAVA_HOME = 'D:\vibecoding\sdk\jdk'
$env:ANDROID_HOME = 'D:\vibecoding\sdk\android'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\build-tools\36.1.0;$env:Path"

$releaseDir = 'D:\vibecoding\release\Androiddesktop\release'
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null

$gradle = Join-Path $root 'gradlew.bat'
if (!(Test-Path -Path $gradle)) { throw "Gradle wrapper missing: $gradle" }

& $gradle --offline ':app:assembleRelease' '--stacktrace'
if ($LASTEXITCODE -ne 0) { throw "Gradle assembleRelease failed with exit code $LASTEXITCODE" }

$unsigned = Join-Path $root 'app\build\outputs\apk\release\app-release-unsigned.apk'
if (!(Test-Path -Path $unsigned)) { throw "Unsigned release APK missing: $unsigned" }

$zipalign = Join-Path $env:ANDROID_HOME 'build-tools\36.1.0\zipalign.exe'
$apksigner = Join-Path $env:ANDROID_HOME 'build-tools\36.1.0\apksigner.bat'
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
foreach ($tool in @($zipalign, $apksigner, $keytool)) {
    if (!(Test-Path -Path $tool)) { throw "Required signing tool missing: $tool" }
}

$aligned = Join-Path $releaseDir 'Androiddesktop-release-aligned.apk'
$signed = Join-Path $releaseDir 'Androiddesktop-release.apk'
$signatureReport = Join-Path $releaseDir 'Androiddesktop-release-signature.txt'
$badgingReport = Join-Path $releaseDir 'Androiddesktop-release-badging.txt'

if (Test-Path -Path $aligned) { Remove-Item -Path $aligned -Force }
if (Test-Path -Path $signed) { Remove-Item -Path $signed -Force }

& $zipalign -p -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed with exit code $LASTEXITCODE" }

$ks = $env:ANDROIDDESKTOP_KEYSTORE
$ksAlias = $env:ANDROIDDESKTOP_KEY_ALIAS
$ksPass = $env:ANDROIDDESKTOP_KEYSTORE_PASS
$keyPass = $env:ANDROIDDESKTOP_KEY_PASS
$tempDir = $null
if ([string]::IsNullOrWhiteSpace($ks) -or [string]::IsNullOrWhiteSpace($ksAlias) -or [string]::IsNullOrWhiteSpace($ksPass)) {
    $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ('androiddesktop-sign-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
    $ks = Join-Path $tempDir 'androiddesktop-release.jks'
    $ksAlias = 'androiddesktop-release'
    $passBytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($passBytes)
    $ksPass = [Convert]::ToBase64String($passBytes).Replace('+', 'A').Replace('/', 'B')
    $keyPass = $ksPass
    & $keytool -genkeypair -v -keystore $ks -storepass $ksPass -keypass $keyPass -alias $ksAlias -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Androiddesktop,O=CleanRoom,L=Local,ST=Local,C=CN' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }
} elseif ([string]::IsNullOrWhiteSpace($keyPass)) {
    $keyPass = $ksPass
}

try {
    & $apksigner sign --ks $ks --ks-key-alias $ksAlias --ks-pass "pass:$ksPass" --key-pass "pass:$keyPass" --out $signed $aligned
    if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed with exit code $LASTEXITCODE" }

    (& $apksigner verify --verbose --print-certs $signed) | Set-Content -Encoding utf8 -Path $signatureReport
    if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed with exit code $LASTEXITCODE" }

    $aapt = Join-Path $env:ANDROID_HOME 'build-tools\36.1.0\aapt.exe'
    (& $aapt dump badging $signed) | Set-Content -Encoding utf8 -Path $badgingReport

    $hash = Get-FileHash -Algorithm SHA256 -Path $signed
    "RELEASE_OK path=$signed sha256=$($hash.Hash) bytes=$((Get-Item -Path $signed).Length)"
} finally {
    if ($tempDir -and (Test-Path -Path $tempDir)) {
        Remove-Item -Path $tempDir -Recurse -Force
    }
    if (Test-Path -Path $aligned) { Remove-Item -Path $aligned -Force }
}
