param(
    [switch]$NoOffline
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -Path (Join-Path $PSScriptRoot '..')).Path
$env:JAVA_HOME = 'D:\vibecoding\sdk\jdk'
$env:ANDROID_HOME = 'D:\vibecoding\sdk\android'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\build-tools\36.1.0;$env:Path"
$logDir = Join-Path $root 'resource\analysis'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Resolve-GradleCommand {
    $cmd = Get-Command gradle -ErrorAction SilentlyContinue
    if ($cmd) { return [string]$cmd.Source }

    $candidates = @(
        (Join-Path $root 'gradlew.bat'),
        'D:\vibecoding\project\flhanime1\gradlew.bat',
        'D:\vibecoding\project\flhanime1_v116_compare\gradlew.bat'
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -Path $candidate) { return [string]$candidate }
    }
    throw 'No gradle command or reusable gradlew.bat found under D:\vibecoding\project.'
}

function Invoke-GradleBuild {
    param(
        [string]$GradleCommand,
        [string[]]$Arguments,
        [string]$LogName
    )

    $stdoutPath = Join-Path $logDir "$LogName.stdout.log"
    $stderrPath = Join-Path $logDir "$LogName.stderr.log"
    $joinedArgs = ($Arguments | ForEach-Object {
        if ($_ -match '\s') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
    }) -join ' '

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    if ($GradleCommand.EndsWith('.bat', [System.StringComparison]::OrdinalIgnoreCase) -or $GradleCommand.EndsWith('.cmd', [System.StringComparison]::OrdinalIgnoreCase)) {
        $psi.FileName = 'cmd.exe'
        $psi.Arguments = "/d /c `"$GradleCommand`" $joinedArgs"
    } else {
        $psi.FileName = $GradleCommand
        $psi.Arguments = $joinedArgs
    }
    $psi.WorkingDirectory = $root
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.Environment['JAVA_HOME'] = $env:JAVA_HOME
    $psi.Environment['ANDROID_HOME'] = $env:ANDROID_HOME
    $psi.Environment['ANDROID_SDK_ROOT'] = $env:ANDROID_SDK_ROOT
    $psi.Environment['Path'] = $env:Path

    $process = [System.Diagnostics.Process]::Start($psi)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $stdout | Set-Content -Encoding utf8 -Path $stdoutPath
    $stderr | Set-Content -Encoding utf8 -Path $stderrPath
    Write-Host "GRADLE_LOG stdout=$stdoutPath stderr=$stderrPath exit=$($process.ExitCode)"
    return [int]$process.ExitCode
}

$gradle = Resolve-GradleCommand
"GRADLE_COMMAND=$gradle"
$baseArgs = @('-p', $root, ':app:assembleDebug', '--stacktrace')
$exitCode = 1
if (!$NoOffline) {
    $exitCode = Invoke-GradleBuild -GradleCommand $gradle -Arguments (@('-p', $root, '--offline', ':app:assembleDebug', '--stacktrace')) -LogName 'gradle-debug-offline'
    if ($exitCode -ne 0) {
        Write-Warning 'Offline Gradle build failed; retrying without --offline.'
        $exitCode = Invoke-GradleBuild -GradleCommand $gradle -Arguments $baseArgs -LogName 'gradle-debug-online'
    }
} else {
    $exitCode = Invoke-GradleBuild -GradleCommand $gradle -Arguments $baseArgs -LogName 'gradle-debug-online'
}
if ($exitCode -ne 0) { throw "Gradle build failed with exit code $exitCode" }

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path -Path $apk)) { throw "Debug APK missing: $apk" }
$releaseDir = 'D:\vibecoding\release\Androiddesktop\debug'
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
$dest = Join-Path $releaseDir 'Androiddesktop-debug.apk'
Copy-Item -Path $apk -Destination $dest -Force
$hash = Get-FileHash -Algorithm SHA256 -Path $dest
"BUILD_OK path=$dest sha256=$($hash.Hash) bytes=$((Get-Item -Path $dest).Length)"
