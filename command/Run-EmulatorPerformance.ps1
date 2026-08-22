param(
    [ValidateSet('baseline', 'after')]
    [string]$Phase = 'baseline',
    [int[]]$VmIndex = @(0, 1),
    [ValidateRange(3, 20)]
    [int]$Runs = 7,
    [string]$ApkPath = 'D:\vibecoding\release\Androiddesktop\debug\Androiddesktop-debug.apk',
    [string]$OutputRoot = ''
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -Path (Join-Path $PSScriptRoot '..')).Path
$adb = 'D:\vibecoding\sdk\android\platform-tools\adb.exe'
$loopbackPortByVmIndex = @{
    0 = 16384
    1 = 16416
}
$packageName = 'io.github.androiddesktop.dev'
$activity = "$packageName/io.github.androiddesktop.MainActivity"

$benchmarkPackages = @(
    'com.dragon.read',
    'com.android.settings',
    'com.android.chrome',
    'com.google.android.documentsui',
    'com.google.android.apps.photos'
)

foreach ($required in @($adb, $ApkPath)) {

    if (!(Test-Path -Path $required)) { throw "Required path missing: $required" }
}

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputRoot = Join-Path $root "resource\test\performance-emulator-$Phase-$stamp"
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

function Invoke-Adb {
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string[]]$Arguments,
        [switch]$AllowFailure
    )
    $output = & $adb -s $Serial @Arguments 2>&1
    $exit = $LASTEXITCODE
    if ($exit -ne 0 -and !$AllowFailure) {
        throw "adb failed serial=$Serial exit=$exit args=$($Arguments -join ' ') output=$($output -join ' ')"
    }
    return @($output)
}

function Connect-Mumu {
    param([int]$Index)
    if (!$loopbackPortByVmIndex.ContainsKey($Index)) {
        throw "Unsupported MuMu vmindex=$Index. Safety gate only permits explicit loopback instances: $($loopbackPortByVmIndex.Keys -join ',')."
    }
    $serial = "127.0.0.1:$($loopbackPortByVmIndex[$Index])"
    if (!$serial.StartsWith('127.0.0.1:')) {
        throw "Refusing non-loopback ADB target: $serial"
    }
    $connect = & $adb connect $serial 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to connect MuMu vmindex=$Index at $serial output=$($connect -join ' ')"
    }
    $state = ((& $adb -s $serial get-state 2>&1 | Select-Object -First 1) -join '').Trim()
    if ($state -ne 'device') {
        throw "MuMu target is not ready: vmindex=$Index serial=$serial state=$state"
    }
    return [pscustomobject]@{ Index = $Index; Serial = $serial }
}


function Get-PropValue {
    param([string]$Serial, [string]$Name)
    return ((Invoke-Adb -Serial $Serial -Arguments @('shell', 'getprop', $Name)) -join '').Trim()
}

function Get-UiXml {
    param([string]$Serial)
    $remote = '/sdcard/androiddesktop-perf-ui.xml'
    Invoke-Adb -Serial $Serial -Arguments @('shell', 'uiautomator', 'dump', $remote) -AllowFailure | Out-Null
    $raw = (Invoke-Adb -Serial $Serial -Arguments @('exec-out', 'cat', $remote) -AllowFailure) -join "`n"
    if ([string]::IsNullOrWhiteSpace($raw) -or !$raw.TrimStart().StartsWith('<?xml')) { return $null }
    try { return [xml]$raw } catch { return $null }
}

function Tap-UiDescription {
    param([string]$Serial, [string]$Description)
    $xml = Get-UiXml -Serial $Serial
    if (!$xml) { return $false }
    $escaped = $Description.Replace("'", "&apos;")
    $node = @($xml.SelectNodes("//node[@content-desc='$escaped']")) | Where-Object { $_.bounds } | Select-Object -First 1
    if (!$node) { return $false }
    $match = [regex]::Match([string]$node.bounds, '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (!$match.Success) { return $false }
    $x = [int]( ([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2 )
    $y = [int]( ([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2 )
    Invoke-Adb -Serial $Serial -Arguments @('shell', 'input', 'tap', [string]$x, [string]$y) | Out-Null
    return $true
}


function Get-Percentile {
    param([double[]]$Values, [double]$Percentile)
    if (!$Values -or $Values.Count -eq 0) { return $null }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($Percentile * $sorted.Count) - 1
    $index = [Math]::Max(0, [Math]::Min($sorted.Count - 1, $index))
    return [double]$sorted[$index]
}

$apkHash = (Get-FileHash -Algorithm SHA256 -Path $ApkPath).Hash
$allSummaries = @()

foreach ($index in $VmIndex) {
    $target = Connect-Mumu -Index $index
    $serial = $target.Serial
    $deviceDir = Join-Path $OutputRoot "mumu-$index"
    New-Item -ItemType Directory -Force -Path $deviceDir | Out-Null

    $model = Get-PropValue -Serial $serial -Name 'ro.product.model'
    $manufacturer = Get-PropValue -Serial $serial -Name 'ro.product.manufacturer'
    $product = Get-PropValue -Serial $serial -Name 'ro.product.name'
    $hardware = Get-PropValue -Serial $serial -Name 'ro.hardware'
    @(
        "phase=$Phase",
        "vmIndex=$index",
        "serial=$serial",
        "model=$model",
        "manufacturer=$manufacturer",
        "product=$product",
        "hardware=$hardware",
        "apkSha256=$apkHash"
    ) | Set-Content -Encoding utf8 -Path (Join-Path $deviceDir 'device.txt')

    $install = Invoke-Adb -Serial $serial -Arguments @('install', '-r', '-t', $ApkPath)
    $install | Set-Content -Encoding utf8 -Path (Join-Path $deviceDir 'install.txt')

    $availability = foreach ($benchmarkPackage in $benchmarkPackages) {
        $pathOutput = Invoke-Adb -Serial $serial -Arguments @('shell', 'pm', 'path', $benchmarkPackage) -AllowFailure
        [pscustomobject]@{
            Package = $benchmarkPackage
            Installed = (($pathOutput -join "`n") -match '^package:')
        }
    }
    $availability | Export-Csv -NoTypeInformation -Encoding utf8 -Path (Join-Path $deviceDir 'benchmark-packages.csv')

    $startups = @()
    for ($run = 1; $run -le $Runs; $run++) {
        Invoke-Adb -Serial $serial -Arguments @('shell', 'am', 'force-stop', $packageName) | Out-Null
        Start-Sleep -Milliseconds 180
        $startOutput = Invoke-Adb -Serial $serial -Arguments @('shell', 'am', 'start', '-W', '-n', $activity)
        $joined = $startOutput -join "`n"
        $total = if ($joined -match '(?m)^TotalTime:\s*(\d+)') { [int]$Matches[1] } else { $null }
        $wait = if ($joined -match '(?m)^WaitTime:\s*(\d+)') { [int]$Matches[1] } else { $null }
        $thisTime = if ($joined -match '(?m)^ThisTime:\s*(\d+)') { [int]$Matches[1] } else { $null }
        if ($null -eq $total) { throw "Unable to parse startup TotalTime on vmindex=$index run=$run. Output: $joined" }
        $startups += [pscustomobject]@{ Run = $run; ThisTimeMs = $thisTime; TotalTimeMs = $total; WaitTimeMs = $wait }
        Start-Sleep -Milliseconds 240
    }
    $startups | Export-Csv -NoTypeInformation -Encoding utf8 -Path (Join-Path $deviceDir 'startup.csv')

    Invoke-Adb -Serial $serial -Arguments @('shell', 'dumpsys', 'gfxinfo', $packageName, 'reset') | Out-Null
    Invoke-Adb -Serial $serial -Arguments @('shell', 'am', 'force-stop', $packageName) | Out-Null
    Invoke-Adb -Serial $serial -Arguments @('shell', 'am', 'start', '-W', '-n', $activity) | Out-Null
    Start-Sleep -Milliseconds 650

            $interaction = [ordered]@{}
    # Use ASCII accessibility ids so PowerShell/ADB console encoding cannot corrupt selectors.
    $selectors = @(
        @{ Key = 'launcher'; Desc = 'androiddesktop-dock-launcher' },
        @{ Key = 'close-launcher'; Desc = 'androiddesktop-close' },
        @{ Key = 'tools-1'; Desc = 'androiddesktop-dock-tools' },
        @{ Key = 'wireless'; Desc = 'androiddesktop-tool-wireless' },
        @{ Key = 'close-guide'; Desc = 'androiddesktop-guide-close' },
        @{ Key = 'tools-2'; Desc = 'androiddesktop-dock-tools' },
        @{ Key = 'performance'; Desc = 'androiddesktop-tool-performance' },
        @{ Key = 'close-console'; Desc = 'androiddesktop-close' },
        @{ Key = 'next'; Desc = 'androiddesktop-dock-next' },
        @{ Key = 'previous'; Desc = 'androiddesktop-dock-prev' }
    )
    foreach ($selector in $selectors) {
        $ok = Tap-UiDescription -Serial $serial -Description $selector.Desc
        $interaction[$selector.Key] = $ok
        Start-Sleep -Milliseconds 320
    }


    $interaction.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" } |
        Set-Content -Encoding utf8 -Path (Join-Path $deviceDir 'interaction.txt')

    $gfx = Invoke-Adb -Serial $serial -Arguments @('shell', 'dumpsys', 'gfxinfo', $packageName)
    $mem = Invoke-Adb -Serial $serial -Arguments @('shell', 'dumpsys', 'meminfo', $packageName)
        $homeResolver = Invoke-Adb -Serial $serial -Arguments @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME') -AllowFailure
    $gfx | Set-Content -Encoding utf8 -Path (Join-Path $deviceDir 'gfxinfo.txt')
    $mem | Set-Content -Encoding utf8 -Path (Join-Path $deviceDir 'meminfo.txt')
    $homeResolver | Set-Content -Encoding utf8 -Path (Join-Path $deviceDir 'home-resolver.txt')

    $gfxText = $gfx -join "`n"
    $memText = $mem -join "`n"
    $frames = if ($gfxText -match 'Total frames rendered:\s*(\d+)') { [int]$Matches[1] } else { $null }
    $jankyFrames = if ($gfxText -match 'Janky frames:\s*(\d+)\s*\(([\d.]+)%\)') { [int]$Matches[1] } else { $null }
    $jankyPct = if ($gfxText -match 'Janky frames:\s*\d+\s*\(([\d.]+)%\)') { [double]$Matches[1] } else { $null }
    $frameP50 = if ($gfxText -match '50th percentile:\s*(\d+)ms') { [int]$Matches[1] } else { $null }
    $frameP95 = if ($gfxText -match '95th percentile:\s*(\d+)ms') { [int]$Matches[1] } else { $null }
    $frameP99 = if ($gfxText -match '99th percentile:\s*(\d+)ms') { [int]$Matches[1] } else { $null }
    $totalPssKb = if ($memText -match 'TOTAL PSS:\s*(\d+)') { [int]$Matches[1] } else { $null }
    $startupValues = @($startups | ForEach-Object { [double]$_.TotalTimeMs })
    $avg = [Math]::Round((($startupValues | Measure-Object -Average).Average), 1)
    $p95 = Get-Percentile -Values $startupValues -Percentile 0.95

    $summary = [ordered]@{
        Phase = $Phase
        VmIndex = $index
        Serial = $serial
        Model = $model
        Manufacturer = $manufacturer
        Product = $product
        ApkSha256 = $apkHash
        StartupRuns = $Runs
        StartupAvgMs = $avg
        StartupP95Ms = $p95
        Frames = $frames
        JankyFrames = $jankyFrames
        JankyPct = $jankyPct
        FrameP50Ms = $frameP50
        FrameP95Ms = $frameP95
        FrameP99Ms = $frameP99
        TotalPssKb = $totalPssKb
        FanqieInstalled = [bool](($availability | Where-Object Package -eq 'com.dragon.read').Installed)
        CapturedAt = (Get-Date).ToString('o')
    }
    $summary | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 -Path (Join-Path $deviceDir 'summary.json')
    $allSummaries += [pscustomobject]$summary
}

$aggregate = [ordered]@{
    Phase = $Phase
    ApkPath = $ApkPath
    ApkSha256 = $apkHash
    PhysicalDeviceCommandsIssued = $false
    MuMuVmIndexes = @($VmIndex)
    DeviceCount = $allSummaries.Count
    StartupAvgMs = if ($allSummaries.Count) { [Math]::Round((($allSummaries.StartupAvgMs | Measure-Object -Average).Average), 1) } else { $null }
    StartupP95WorstMs = if ($allSummaries.Count) { ($allSummaries.StartupP95Ms | Measure-Object -Maximum).Maximum } else { $null }
    JankyPctWorst = if ($allSummaries.Count) { ($allSummaries.JankyPct | Measure-Object -Maximum).Maximum } else { $null }
    FrameP95WorstMs = if ($allSummaries.Count) { ($allSummaries.FrameP95Ms | Measure-Object -Maximum).Maximum } else { $null }
    TotalPssAvgKb = if ($allSummaries.Count) { [Math]::Round((($allSummaries.TotalPssKb | Measure-Object -Average).Average), 0) } else { $null }
    Devices = $allSummaries
    CapturedAt = (Get-Date).ToString('o')
}
$aggregate | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 -Path (Join-Path $OutputRoot 'summary.json')
"PERF_OK phase=$Phase output=$OutputRoot devices=$($allSummaries.Count) startupAvgMs=$($aggregate.StartupAvgMs) frameP95WorstMs=$($aggregate.FrameP95WorstMs) jankyPctWorst=$($aggregate.JankyPctWorst) pssAvgKb=$($aggregate.TotalPssAvgKb)"
