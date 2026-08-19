param(
    [string]$Adb = "D:\vibecoding\sdk\android\platform-tools\adb.exe",
    [string]$Apk = "D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk",
    [string]$OutRoot = "D:\vibecoding\project\Androiddesktop\resource\test"
)

$ErrorActionPreference = "Stop"
$pkg = "io.github.androiddesktop"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$out = Join-Path $OutRoot "huawei-forwarding-$stamp"
New-Item -ItemType Directory -Force -Path $out | Out-Null

function Run-Adb {
    param([string[]]$ArgsList, [string]$Name)
    $stdout = Join-Path $out "$Name.stdout.txt"
    $stderr = Join-Path $out "$Name.stderr.txt"
    & $Adb @ArgsList > $stdout 2> $stderr
    [pscustomobject]@{ Name=$Name; ExitCode=$LASTEXITCODE; Stdout=$stdout; Stderr=$stderr }
}

function Dump-Adb {
    param([string[]]$ArgsList, [string]$Name)
    $path = Join-Path $out $Name
    & $Adb @ArgsList > $path 2>&1
    $path
}

function Pull-Remote {
    param([string]$Remote, [string]$Name)
    $path = Join-Path $out $Name
    & $Adb pull $Remote $path | Out-Null
    $path
}

function First-TaskIdForPackage {
    param([string]$Text, [string]$Package)
    $escaped = [regex]::Escape($Package)
    $pattern = "Task\{[^\n]+#(?<id>\d+)[^\n]+$escaped[^\n]+mode=freeform"
    $match = [regex]::Match($Text, $pattern)
    if ($match.Success) { return [int]$match.Groups['id'].Value }
    return $null
}

function Try-PullXmlAndPng {
    param([string]$BaseRemote, [string]$BaseName)
    Run-Adb -ArgsList @("shell", "uiautomator", "dump", "$BaseRemote.xml") -Name "$BaseName-dump" | Out-Null
    if ((Run-Adb -ArgsList @("shell", "ls", "$BaseRemote.xml") -Name "$BaseName-ls-xml").ExitCode -eq 0) {
        Pull-Remote "$BaseRemote.xml" "$BaseName.xml" | Out-Null
    }
    Run-Adb -ArgsList @("shell", "screencap", "-p", "$BaseRemote.png") -Name "$BaseName-screencap" | Out-Null
    if ((Run-Adb -ArgsList @("shell", "ls", "$BaseRemote.png") -Name "$BaseName-ls-png").ExitCode -eq 0) {
        Pull-Remote "$BaseRemote.png" "$BaseName.png" | Out-Null
    }
}

$steps = New-Object System.Collections.Generic.List[object]
$steps.Add((Run-Adb -ArgsList @("devices", "-l") -Name "00-devices"))
$steps.Add((Run-Adb -ArgsList @("uninstall", $pkg) -Name "01-uninstall-old"))
$steps.Add((Run-Adb -ArgsList @("install", $Apk) -Name "02-install"))
$steps.Add((Run-Adb -ArgsList @("shell", "appops", "set", $pkg, "SYSTEM_ALERT_WINDOW", "allow") -Name "03-appops-overlay"))
$steps.Add((Run-Adb -ArgsList @("shell", "appops", "set", $pkg, "GET_USAGE_STATS", "allow") -Name "04-appops-usage"))
$steps.Add((Run-Adb -ArgsList @("shell", "pm", "grant", $pkg, "android.permission.WRITE_SECURE_SETTINGS") -Name "05-grant-secure"))
$steps.Add((Run-Adb -ArgsList @("shell", "settings", "put", "global", "enable_freeform_support", "1") -Name "06-freeform"))
$steps.Add((Run-Adb -ArgsList @("shell", "settings", "put", "global", "force_resizable_activities", "1") -Name "07-resizable"))
$steps.Add((Run-Adb -ArgsList @("shell", "am", "force-stop", $pkg) -Name "08-force-stop-host"))
$steps.Add((Run-Adb -ArgsList @("shell", "am", "start", "-n", "$pkg/.MainActivity") -Name "09-start-host"))
Start-Sleep -Seconds 3

Try-PullXmlAndPng -BaseRemote "/sdcard/androiddesktop-host" -BaseName "host-landscape-default"
Dump-Adb -ArgsList @("shell", "dumpsys", "activity", "activities") -Name "host-dumpsys-activity.txt" | Out-Null

# Open wireless guide by looking for the clickable dock node that contains the guide label in the current default layout.
$hostXmlPath = Join-Path $out "host-landscape-default.xml"
$tapX = 1240
$tapY = 1065
if (Test-Path $hostXmlPath) {
    $hostXmlForTap = Get-Content $hostXmlPath -Raw -Encoding UTF8
    $match = [regex]::Match($hostXmlForTap, 'text="无线导引"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if ($match.Success) {
        $tapX = [int](([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2)
        $tapY = [int](([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2)
    }
}
$steps.Add((Run-Adb -ArgsList @("shell", "input", "tap", "$tapX", "$tapY") -Name "10-tap-wireless-guide"))
Start-Sleep -Seconds 1
Try-PullXmlAndPng -BaseRemote "/sdcard/androiddesktop-guide" -BaseName "host-wireless-guide"

# Huawei BRQ-AN00 accepts cmd activity start-activity --windowingMode 5; am start --bounds is not supported on this ROM.
$steps.Add((Run-Adb -ArgsList @("shell", "cmd", "activity", "start-activity", "--windowingMode", "5", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-p", "com.android.settings") -Name "11-start-settings-freeform"))
Start-Sleep -Seconds 1
$steps.Add((Run-Adb -ArgsList @("shell", "cmd", "activity", "start-activity", "--windowingMode", "5", "-n", "com.huawei.calculator/.Calculator") -Name "12-start-calculator-freeform"))
Start-Sleep -Seconds 1
$activityBeforeResize = Dump-Adb -ArgsList @("shell", "dumpsys", "activity", "activities") -Name "before-resize-dumpsys-activity.txt"
$activityText = Get-Content $activityBeforeResize -Raw -Encoding UTF8
$settingsTask = First-TaskIdForPackage -Text $activityText -Package "com.android.settings"
$calculatorTask = First-TaskIdForPackage -Text $activityText -Package "com.huawei.calculator"

if ($settingsTask) {
    $steps.Add((Run-Adb -ArgsList @("shell", "cmd", "activity", "task", "resize", "$settingsTask", "40", "90", "1280", "980") -Name "13-resize-settings"))
}
if ($calculatorTask) {
    $steps.Add((Run-Adb -ArgsList @("shell", "cmd", "activity", "task", "resize", "$calculatorTask", "1360", "90", "2600", "980") -Name "14-resize-calculator"))
}
Start-Sleep -Seconds 2

Try-PullXmlAndPng -BaseRemote "/sdcard/androiddesktop-freeform-two" -BaseName "freeform-two-resized"
$finalActivity = Dump-Adb -ArgsList @("shell", "dumpsys", "activity", "activities") -Name "freeform-two-dumpsys-activity.txt"
$finalWindow = Dump-Adb -ArgsList @("shell", "dumpsys", "window", "windows") -Name "freeform-two-dumpsys-window.txt"
Dump-Adb -ArgsList @("shell", "dumpsys", "display") -Name "freeform-two-dumpsys-display.txt" | Out-Null

$hostXml = if (Test-Path $hostXmlPath) { Get-Content $hostXmlPath -Raw -Encoding UTF8 } else { "" }
$guideXmlPath = Join-Path $out "host-wireless-guide.xml"
$guideXml = if (Test-Path $guideXmlPath) { Get-Content $guideXmlPath -Raw -Encoding UTF8 } else { "" }
$finalActivityText = Get-Content $finalActivity -Raw -Encoding UTF8
$finalWindowText = Get-Content $finalWindow -Raw -Encoding UTF8

$hasLandscape = $hostXml -match 'rotation="1"'
$hasNormalUi = $hostXml.Contains("Normal mode") -and ($hostXml.Contains("无线导引") -or $hostXml.Contains("adb pair"))
$hasGuide = $guideXml.Contains("adb pair") -and $guideXml.Contains("adb connect") -and $guideXml.Contains("WRITE_SECURE_SETTINGS")
$hasSettings = $finalActivityText.Contains("com.android.settings") -and $finalActivityText.Contains("mode=freeform")
$hasCalculator = $finalActivityText.Contains("com.huawei.calculator") -and $finalActivityText.Contains("mode=freeform")
$hasSettingsBounds = $finalActivityText.Contains("Rect(40, 90 - 1280, 980)")
$hasCalculatorBounds = $finalActivityText.Contains("Rect(1360, 90 - 2600, 980)")
$hasSurfaces = $finalWindowText.Contains("com.android.settings") -and $finalWindowText.Contains("com.huawei.calculator") -and $finalWindowText.Contains("mSurface=Surface")

$hashLines = @()
foreach ($fileName in @("host-landscape-default.png", "host-wireless-guide.png", "freeform-two-resized.png")) {
    $file = Join-Path $out $fileName
    if (Test-Path $file) {
        $hash = Get-FileHash -Algorithm SHA256 $file
        $bytes = (Get-Item $file).Length
        $hashLines += "| $fileName | $($hash.Hash) | $bytes |"
    }
}

$report = @"
# Huawei forwarding verification

Device: Huawei BRQ-AN00 / JXB0221819006346
Package: $pkg
APK: $Apk

## Checks

| Check | Result |
|---|---|
| Host default landscape | $hasLandscape |
| Normal UI contains wireless guide entry | $hasNormalUi |
| Wireless debugging guide content captured | $hasGuide |
| Settings freeform task | $hasSettings |
| Calculator freeform task | $hasCalculator |
| Settings resized bounds | $hasSettingsBounds |
| Calculator resized bounds | $hasCalculatorBounds |
| Window surfaces for both target apps | $hasSurfaces |

## Task IDs

- Settings task: $settingsTask
- Calculator task: $calculatorTask

## Screenshot hashes

| File | SHA-256 | Bytes |
|---|---|---|
$($hashLines -join "`n")

## Boundary

This validates Huawei system-level multi-window/freeform forwarding display through shell ActivityTaskManager. It does not by itself prove target apps are embedded inside Androiddesktop's own SurfaceView. In-host Surface forwarding requires a running privileged core and display/session/Surface binding evidence.
"@
$report | Set-Content -Encoding UTF8 (Join-Path $out "forwarding-report.md")
$steps | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $out "steps.json")

Write-Host "FORWARDING_TEST_OUT=$out"
Write-Host "LANDSCAPE=$hasLandscape NORMAL_UI=$hasNormalUi GUIDE=$hasGuide SETTINGS=$hasSettings CALCULATOR=$hasCalculator SETTINGS_BOUNDS=$hasSettingsBounds CALCULATOR_BOUNDS=$hasCalculatorBounds SURFACES=$hasSurfaces"
