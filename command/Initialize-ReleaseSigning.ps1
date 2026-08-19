$ErrorActionPreference = 'Stop'

$root = (Resolve-Path -Path (Join-Path $PSScriptRoot '..')).Path
$javaHome = 'D:\vibecoding\sdk\jdk'
$keytool = Join-Path $javaHome 'bin\keytool.exe'
if (!(Test-Path -Path $keytool)) { throw "keytool missing: $keytool" }

$signingDir = Join-Path $root 'app\signing'
$keystore = Join-Path $signingDir 'androiddesktop-release.jks'
$properties = Join-Path $signingDir 'release.local.properties'
$alias = 'androiddesktop-release'

$keystoreExists = Test-Path -Path $keystore
$propertiesExists = Test-Path -Path $properties
if ($keystoreExists -xor $propertiesExists) {
    throw "Release signing state is incomplete. Refusing to generate a replacement key. Restore both $keystore and $properties from the secure backup."
}
if ($keystoreExists -and $propertiesExists) {
    "SIGNING_ALREADY_INITIALIZED keystore=$keystore properties=$properties alias=$alias"
    exit 0
}

New-Item -ItemType Directory -Force -Path $signingDir | Out-Null
$secretBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
$password = [Convert]::ToHexString($secretBytes)

& $keytool -genkeypair -v `
    -keystore $keystore `
    -storetype JKS `
    -storepass $password `
    -keypass $password `
    -alias $alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname 'CN=Androiddesktop,O=CleanRoom,L=Local,ST=Local,C=CN' | Out-Null
if (-not $?) { throw "keytool failed with exit code $LASTEXITCODE" }

@(
    'keystore=app/signing/androiddesktop-release.jks'
    "alias=$alias"
    "storePassword=$password"
    "keyPassword=$password"
) | Set-Content -Path $properties -Encoding utf8

"SIGNING_INITIALIZED keystore=$keystore properties=$properties alias=$alias"
"IMPORTANT: back up app/signing/androiddesktop-release.jks and release.local.properties together. Build-Release.ps1 will never auto-generate a replacement key."
