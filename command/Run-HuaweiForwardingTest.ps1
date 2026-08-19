param(
    [string]$Apk = 'app\build\outputs\apk\debug\app-debug.apk',
    [string]$HostPackage = 'io.github.androiddesktop.dev',
    [string]$SecondLauncherLabel = '文件',
    [string]$SecondPackage = 'com.huawei.filemanager',
    [string]$OutputDir = 'resource\test\huawei-forwarding-script'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -Path (Join-Path $PSScriptRoot '..')).Path
$adb = 'D:\vibecoding\sdk\android\platform-tools\adb.exe'
if (!(Test-Path $adb)) { throw "adb missing: $adb" }

if (![System.IO.Path]::IsPathRooted($Apk)) { $Apk = Join-Path $root $Apk }
if (![System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir = Join-Path $root $OutputDir }
if (!(Test-Path $Apk)) { throw "APK missing: $Apk" }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

function Invoke-Adb {
    $argumentList = @($args)
    $result = & $adb @argumentList
    if ($LASTEXITCODE -ne 0) { throw "adb failed ($LASTEXITCODE): adb $($argumentList -join ' ')`n$result" }
    return $result
}

function Save-AdbText([string]$Path, [string[]]$AdbArgs) {
    $content = & $adb @AdbArgs
    if ($LASTEXITCODE -ne 0) { throw "adb failed ($LASTEXITCODE): adb $($AdbArgs -join ' ')`n$content" }
    @($content) | Set-Content -Path $Path -Encoding utf8
}

$devices = @(Invoke-Adb devices -l | Where-Object { $_ -match '\sdevice\s' })
if ($devices.Count -ne 1) { throw "Expected exactly one authorized Android device, got $($devices.Count)." }
$devices | Set-Content (Join-Path $OutputDir '00-device.txt') -Encoding utf8
$model = (Invoke-Adb shell getprop ro.product.model | Select-Object -First 1).Trim()
$model | Set-Content (Join-Path $OutputDir '01-model.txt') -Encoding utf8
if ($model -notmatch 'BRQ|HUAWEI|HONOR') {
    Write-Warning "Connected model '$model' is not the BRQ Huawei reference device; continuing because the caller explicitly selected this device."
}

$install = Invoke-Adb install -r $Apk
$install | Set-Content (Join-Path $OutputDir '02-install.txt') -Encoding utf8
Invoke-Adb shell pm grant $HostPackage android.permission.WRITE_SECURE_SETTINGS | Out-Null
Invoke-Adb shell appops set $HostPackage SYSTEM_ALERT_WINDOW allow | Out-Null
Invoke-Adb shell appops set $HostPackage GET_USAGE_STATS allow | Out-Null

# Launch once so CoreAuthTokenStore provisions the per-install token in app-scoped external storage.
Invoke-Adb shell am force-stop $HostPackage | Out-Null
Invoke-Adb shell am start -n "$HostPackage/io.github.androiddesktop.MainActivity" | Out-Null
Start-Sleep -Seconds 2
$tokenPath = "/sdcard/Android/data/$HostPackage/files/androiddesktop-core.token"
$tokenStat = Invoke-Adb shell ls -l $tokenPath
$tokenStat | Set-Content (Join-Path $OutputDir '03-token-stat.txt') -Encoding utf8

Invoke-Adb shell pkill -f io.github.androiddesktop.PrivilegedShellCore | Out-Null
$coreCommand = "APK=`$(pm path $HostPackage | head -n 1 | cut -d: -f2); TOKEN=`$(cat $tokenPath); CLASSPATH=`$APK nohup app_process /system/bin io.github.androiddesktop.PrivilegedShellCore `$TOKEN >/data/local/tmp/androiddesktop-core.log 2>&1 </dev/null &"
Invoke-Adb shell $coreCommand | Out-Null
Start-Sleep -Milliseconds 800
$coreLog = @(Invoke-Adb shell cat /data/local/tmp/androiddesktop-core.log)
$coreLog | Set-Content (Join-Path $OutputDir '04-core-log.txt') -Encoding utf8
if (($coreLog -join "`n") -notmatch 'ANDROIDDESKTOP_CORE_READY host=127\.0\.0\.1 port=38388') {
    throw 'Privileged shell core did not reach READY state.'
}

Invoke-Adb logcat -c | Out-Null
Invoke-Adb shell am force-stop $HostPackage | Out-Null
Invoke-Adb shell am start -n "$HostPackage/io.github.androiddesktop.MainActivity" | Out-Null
Start-Sleep -Seconds 5

# Trigger a second host window through Androiddesktop's own Dock/launcher UI, not by shell-starting the target app.
Invoke-Adb shell uiautomator dump /sdcard/androiddesktop-test-before.xml | Out-Null
Invoke-Adb pull /sdcard/androiddesktop-test-before.xml (Join-Path $OutputDir '10-before.xml') | Out-Null
[xml]$before = Get-Content (Join-Path $OutputDir '10-before.xml') -Raw -Encoding utf8
$labelNodes = @($before.SelectNodes("//node[@text='$SecondLauncherLabel']"))
$clickTarget = $labelNodes | Where-Object { $_.ParentNode.clickable -eq 'true' } | Select-Object -First 1
if ($null -eq $clickTarget) { throw "Could not find clickable '$SecondLauncherLabel' target in Androiddesktop UI." }
$bounds = $clickTarget.ParentNode.bounds
if ($bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { throw "Invalid UI bounds: $bounds" }
$x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
$y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
"label=$SecondLauncherLabel bounds=$bounds tap=$x,$y" | Set-Content (Join-Path $OutputDir '11-second-window-click.txt') -Encoding utf8
Invoke-Adb shell input tap $x $y | Out-Null
Start-Sleep -Seconds 6

Save-AdbText (Join-Path $OutputDir '20-display.txt') @('shell', 'dumpsys', 'display')
Save-AdbText (Join-Path $OutputDir '21-activity.txt') @('shell', 'dumpsys', 'activity', 'activities')
Save-AdbText (Join-Path $OutputDir '22-window.txt') @('shell', 'dumpsys', 'window', 'windows')
$embedLog = @(Invoke-Adb logcat -d -s AndroiddesktopEmbed:I '*:S')
$embedLog | Set-Content (Join-Path $OutputDir '23-embed-log.txt') -Encoding utf8

$displayRaw = Get-Content (Join-Path $OutputDir '20-display.txt') -Raw -Encoding utf8
$activityRaw = Get-Content (Join-Path $OutputDir '21-activity.txt') -Raw -Encoding utf8
$settingsMatch = [regex]::Match($displayRaw, 'mBaseDisplayInfo=DisplayInfo\{"Androiddesktop-com-android-settings-[^"]+", displayId (\d+)')
$secondName = $SecondPackage.Replace('.', '-')
$secondPattern = 'mBaseDisplayInfo=DisplayInfo\{"Androiddesktop-' + [regex]::Escape($secondName) + '-[^"]+", displayId (\d+)'
$secondMatch = [regex]::Match($displayRaw, $secondPattern)
if (!$settingsMatch.Success) { throw 'Settings VirtualDisplay was not found.' }
if (!$secondMatch.Success) { throw "$SecondPackage VirtualDisplay was not found." }
$settingsDisplay = [int]$settingsMatch.Groups[1].Value
$secondDisplay = [int]$secondMatch.Groups[1].Value
if ($settingsDisplay -eq 0 -or $secondDisplay -eq 0 -or $settingsDisplay -eq $secondDisplay) {
    throw "Invalid display mapping: settings=$settingsDisplay second=$secondDisplay"
}

$settingsTaskPattern = "Display: mDisplayId=$settingsDisplay[\s\S]{0,6000}com\.android\.settings/.HWSettings"
$secondTaskPattern = "Display: mDisplayId=$secondDisplay[\s\S]{0,6000}$([regex]::Escape($SecondPackage))/"
if ($activityRaw -notmatch $settingsTaskPattern) { throw "Settings task was not found on display $settingsDisplay." }
if ($activityRaw -notmatch $secondTaskPattern) { throw "$SecondPackage task was not found on display $secondDisplay." }

Invoke-Adb shell screencap -p /sdcard/androiddesktop-host.png | Out-Null
Invoke-Adb pull /sdcard/androiddesktop-host.png (Join-Path $OutputDir '30-host.png') | Out-Null
Invoke-Adb shell screencap -d $settingsDisplay -p /sdcard/androiddesktop-settings.png | Out-Null
Invoke-Adb pull /sdcard/androiddesktop-settings.png (Join-Path $OutputDir '31-settings-display.png') | Out-Null
Invoke-Adb shell screencap -d $secondDisplay -p /sdcard/androiddesktop-second.png | Out-Null
Invoke-Adb pull /sdcard/androiddesktop-second.png (Join-Path $OutputDir '32-second-display.png') | Out-Null

$images = @('30-host.png', '31-settings-display.png', '32-second-display.png')
$hashLines = foreach ($name in $images) {
    $path = Join-Path $OutputDir $name
    if ((Get-Item $path).Length -lt 1024) { throw "Screenshot is unexpectedly small: $path" }
    $hash = Get-FileHash $path -Algorithm SHA256
    "$($hash.Hash)  $name"
}
$hashLines | Set-Content (Join-Path $OutputDir '33-screenshot-sha256.txt') -Encoding ascii

$report = @(
    'RESULT=PASS'
    "MODEL=$model"
    "HOST_PACKAGE=$HostPackage"
    "SETTINGS_DISPLAY=$settingsDisplay"
    "SECOND_PACKAGE=$SecondPackage"
    "SECOND_DISPLAY=$secondDisplay"
    'CORE=READY_AUTHENTICATED_LOOPBACK'
    'ASSERTION=two distinct Androiddesktop VirtualDisplays each contain a real launcher Activity task'
    "EVIDENCE_DIR=$OutputDir"
)
$report | Set-Content (Join-Path $OutputDir '40-report.txt') -Encoding utf8
$report
