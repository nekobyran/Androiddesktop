$ErrorActionPreference = 'Continue'
$root = (Resolve-Path -Path (Join-Path $PSScriptRoot '..')).Path
$env:ANDROID_HOME = 'D:\vibecoding\sdk\android'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\build-tools\36.1.0;$env:Path"
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if (!(Test-Path -Path $adb)) { throw "adb missing: $adb" }
$device = $env:ANDROIDDESKTOP_TEST_DEVICE
if ([string]::IsNullOrWhiteSpace($device)) {
    $deviceOutput = & $adb devices -l
    $deviceLine = ($deviceOutput | Select-String -Pattern '\sdevice\s' | Select-Object -First 1).Line
    if ($deviceLine) { $device = ($deviceLine -split '\s+')[0] }
}
if ([string]::IsNullOrWhiteSpace($device)) { throw 'No authorized ADB device found.' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$out = Join-Path $root "resource\test\huawei-$stamp"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$original = Join-Path $root 'resource\apk\VoyageOS_demo.apk'
$release = 'D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk'
$pkgOriginal = 'com.voyageos.app'
$pkgHost = 'io.github.androiddesktop'
function Invoke-Adb([string[]]$Arguments, [string]$LogName = $null) {
    $all = @('-s', $device) + $Arguments
    $result = & $adb @all 2>&1
    if ($LogName) { $result | Set-Content -Encoding utf8 -Path (Join-Path $out $LogName) }
    $result
}
function Capture-State([string]$Name) {
    Invoke-Adb @('shell','screencap','-p',"/sdcard/$Name.png") "$Name.screencap.log" | Out-Null
    Invoke-Adb @('shell','uiautomator','dump',"/sdcard/$Name.xml") "$Name.uiautomator.log" | Out-Null
    Invoke-Adb @('pull',"/sdcard/$Name.png",(Join-Path $out "$Name.png")) "$Name.png.pull.log" | Out-Null
    Invoke-Adb @('pull',"/sdcard/$Name.xml",(Join-Path $out "$Name.xml")) "$Name.xml.pull.log" | Out-Null
    Invoke-Adb @('shell','rm',"/sdcard/$Name.png", "/sdcard/$Name.xml") "$Name.cleanup.log" | Out-Null
}
function Dump-System([string]$Prefix) {
    Invoke-Adb @('shell','dumpsys','display') "$Prefix.dumpsys-display.txt" | Out-Null
    Invoke-Adb @('shell','dumpsys','activity','activities') "$Prefix.dumpsys-activity.txt" | Out-Null
    Invoke-Adb @('shell','dumpsys','window') "$Prefix.dumpsys-window.txt" | Out-Null
    Invoke-Adb @('shell','dumpsys','input') "$Prefix.dumpsys-input.txt" | Out-Null
    Invoke-Adb @('shell','settings','get','global','force_resizable_activities') "$Prefix.setting-force-resizable.txt" | Out-Null
    Invoke-Adb @('shell','settings','get','global','enable_freeform_support') "$Prefix.setting-freeform-support.txt" | Out-Null
    Invoke-Adb @('shell','settings','get','global','freeform_window_management') "$Prefix.setting-freeform-window-management.txt" | Out-Null
}
"OUT=$out" | Set-Content -Encoding utf8 (Join-Path $out 'summary.txt')
Invoke-Adb @('shell','getprop','ro.product.manufacturer') 'prop-manufacturer.txt' | Out-Null
Invoke-Adb @('shell','getprop','ro.product.brand') 'prop-brand.txt' | Out-Null
Invoke-Adb @('shell','getprop','ro.product.model') 'prop-model.txt' | Out-Null
Invoke-Adb @('shell','getprop','ro.product.name') 'prop-name.txt' | Out-Null
Invoke-Adb @('shell','getprop','ro.build.version.release') 'prop-android-release.txt' | Out-Null
Invoke-Adb @('shell','getprop','ro.build.version.sdk') 'prop-sdk.txt' | Out-Null
Invoke-Adb @('shell','wm','size') 'wm-size.txt' | Out-Null
Invoke-Adb @('shell','wm','density') 'wm-density.txt' | Out-Null
Invoke-Adb @('install','-r','-d',$original) 'install-original.log' | Out-Null
Invoke-Adb @('install','-r','-d',$release) 'install-host.log' | Out-Null
foreach ($pkg in @($pkgOriginal, $pkgHost)) {
    Invoke-Adb @('shell','appops','set',$pkg,'SYSTEM_ALERT_WINDOW','allow') "appops-$pkg-overlay.log" | Out-Null
    Invoke-Adb @('shell','appops','set',$pkg,'GET_USAGE_STATS','allow') "appops-$pkg-usage.log" | Out-Null
    Invoke-Adb @('shell','pm','grant',$pkg,'android.permission.WRITE_SECURE_SETTINGS') "grant-$pkg-write-secure.log" | Out-Null
}
Invoke-Adb @('shell','settings','put','global','force_resizable_activities','1') 'set-force-resizable.log' | Out-Null
Invoke-Adb @('shell','settings','put','global','enable_freeform_support','1') 'set-freeform-support.log' | Out-Null
Invoke-Adb @('shell','settings','put','global','freeform_window_management','1') 'set-freeform-window-management.log' | Out-Null
Dump-System 'before'
Invoke-Adb @('shell','am','force-stop',$pkgOriginal) 'force-original.log' | Out-Null
Invoke-Adb @('shell','monkey','-p',$pkgOriginal,'-c','android.intent.category.LAUNCHER','1') 'launch-original.log' | Out-Null
Start-Sleep -Seconds 5
Capture-State 'original-home'
Dump-System 'original-home'
Invoke-Adb @('shell','am','force-stop',$pkgHost) 'force-host.log' | Out-Null
Invoke-Adb @('shell','monkey','-p',$pkgHost,'-c','android.intent.category.LAUNCHER','1') 'launch-host.log' | Out-Null
Start-Sleep -Seconds 4
Capture-State 'host-home'
Invoke-Adb @('shell','input','tap','112','2260') 'host-tap-launcher.log' | Out-Null
Start-Sleep -Seconds 1
Capture-State 'host-launcher'
Invoke-Adb @('shell','input','tap','220','2260') 'host-tap-core.log' | Out-Null
Start-Sleep -Seconds 1
Capture-State 'host-core'
Invoke-Adb @('shell','input','tap','300','2260') 'host-tap-session.log' | Out-Null
Start-Sleep -Seconds 1
Capture-State 'host-session'
Dump-System 'host-session'
$rows = @()
Get-ChildItem $out -Filter '*.xml' | ForEach-Object {
    try {
        [xml]$xml = Get-Content $_.FullName -Raw
        $nodes = $xml.SelectNodes('//node')
        $texts = @()
        $clickable = 0
        foreach ($node in $nodes) {
            $t = $node.GetAttribute('text')
            if (![string]::IsNullOrWhiteSpace($t)) { $texts += $t }
            if ($node.GetAttribute('clickable') -eq 'true') { $clickable++ }
        }
        $rows += [pscustomobject]@{ file=$_.Name; nodes=$nodes.Count; clickable=$clickable; texts=($texts -join ' | ') }
    } catch {
        $rows += [pscustomobject]@{ file=$_.Name; nodes=-1; clickable=-1; texts=$_.Exception.Message }
    }
}
$rows | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $out 'ui-xml-summary.json')
Get-ChildItem $out -Filter '*.png' | ForEach-Object {
    $hash = Get-FileHash -Algorithm SHA256 -Path $_.FullName
    "$($_.Name)`t$($_.Length)`t$($hash.Hash)"
} | Set-Content -Encoding utf8 (Join-Path $out 'screenshot-hashes.tsv')
$report = @()
$report += '# Huawei comparison test report'
$report += ''
$report += "Device: $device"
$report += "Output: $out"
$report += ''
$report += '## Screenshot hashes'
$report += Get-Content (Join-Path $out 'screenshot-hashes.tsv')
$report += ''
$report += '## UI XML summary'
$report += Get-Content (Join-Path $out 'ui-xml-summary.json')
$report | Set-Content -Encoding utf8 (Join-Path $out 'comparison-report.md')
Write-Output '---TEST_OUT---'
Write-Output $out
Write-Output '---SCREENSHOTS---'
Get-Content (Join-Path $out 'screenshot-hashes.tsv')
Write-Output '---UI_XML_SUMMARY---'
Get-Content (Join-Path $out 'ui-xml-summary.json')
