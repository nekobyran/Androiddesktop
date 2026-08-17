param(
    [string]$ApkPath = "resource/apk/VoyageOS_demo.apk",
    [string]$OutDir = "resource/analysis"
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -Path '.').Path
$apk = if ([System.IO.Path]::IsPathRooted($ApkPath)) { $ApkPath } else { Join-Path $root $ApkPath }
$out = if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $root $OutDir }
New-Item -ItemType Directory -Force -Path $out | Out-Null

if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) { $env:ANDROID_HOME = 'D:\vibecoding\sdk\android' }
if ([string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) { $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }
$aapt = Join-Path $env:ANDROID_HOME 'build-tools/36.1.0/aapt.exe'
$dexdump = Join-Path $env:ANDROID_HOME 'build-tools/36.1.0/dexdump.exe'
if (!(Test-Path -Path $aapt)) { throw "aapt.exe not found: $aapt" }
if (!(Test-Path -Path $dexdump)) { throw "dexdump.exe not found: $dexdump" }
if (!(Test-Path -Path $apk)) { throw "APK not found: $apk" }

(& $aapt dump badging $apk) | Set-Content -Encoding utf8 -Path (Join-Path $out 'aapt-badging.txt')
(& $aapt dump permissions $apk) | Set-Content -Encoding utf8 -Path (Join-Path $out 'aapt-permissions.txt')
(& $aapt dump xmltree $apk AndroidManifest.xml) | Set-Content -Encoding utf8 -Path (Join-Path $out 'manifest-xmltree.txt')

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
try {
    $zip.Entries |
        Sort-Object FullName |
        ForEach-Object { "{0}`t{1}`t{2}" -f $_.FullName, $_.Length, $_.CompressedLength } |
        Set-Content -Encoding utf8 -Path (Join-Path $out 'zip-entries.tsv')

    $dexDir = Join-Path $out 'dex'
    New-Item -ItemType Directory -Force -Path $dexDir | Out-Null
    foreach ($entry in $zip.Entries | Where-Object { $_.FullName -match '^classes.*\.dex$' }) {
        $target = Join-Path $dexDir ([System.IO.Path]::GetFileName($entry.FullName))
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
    }
} finally {
    $zip.Dispose()
}

$dexFiles = Get-ChildItem -Path (Join-Path $out 'dex') -Filter '*.dex' -ErrorAction SilentlyContinue
foreach ($dexFile in $dexFiles) {
    $base = [System.IO.Path]::GetFileNameWithoutExtension($dexFile.Name)
    (& $dexdump -f $dexFile.FullName) | Set-Content -Encoding utf8 -Path (Join-Path $out "$base-dexdump-file.txt")
}

$keywordPattern = '(?i)(desktop|launcher|display|freeform|window|presentation|external|overlay|accessibility|input|mouse|keyboard|taskbar|wallpaper|activityoptions|windowing|virtual|voyage|seewo|pinco|cast|projection|ime|navigation|recent|home|huawei|adb|multi.window)'
$allStrings = New-Object System.Collections.Generic.List[string]
foreach ($dexFile in $dexFiles) {
    $bytes = [System.IO.File]::ReadAllBytes($dexFile.FullName)
    $ascii = [regex]::Matches([System.Text.Encoding]::ASCII.GetString($bytes), '[\x20-\x7E]{4,}') | ForEach-Object { $_.Value }
    $unicode = [regex]::Matches([System.Text.Encoding]::Unicode.GetString($bytes), '[\x20-\x7E]{4,}') | ForEach-Object { $_.Value }
    foreach ($s in @($ascii + $unicode)) {
        if ($s.Length -le 260) { $allStrings.Add($s) }
    }
}
$allStrings | Sort-Object -Unique | Set-Content -Encoding utf8 -Path (Join-Path $out 'dex-strings.txt')
$allStrings | Where-Object { $_ -match $keywordPattern } | Sort-Object -Unique | Set-Content -Encoding utf8 -Path (Join-Path $out 'dex-keyword-strings.txt')

$summary = [ordered]@{
    apk = $apk
    sha256 = (Get-FileHash -Algorithm SHA256 -Path $apk).Hash
    bytes = (Get-Item -Path $apk).Length
    dexCount = @($dexFiles).Count
    generatedAt = (Get-Date).ToString('s')
    aapt = $aapt
    dexdump = $dexdump
}
$summary.GetEnumerator() | ForEach-Object { "{0}: {1}" -f $_.Key, $_.Value } | Set-Content -Encoding utf8 -Path (Join-Path $out 'analysis-summary.txt')
Get-Content -Path (Join-Path $out 'analysis-summary.txt')
