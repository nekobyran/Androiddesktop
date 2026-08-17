$ErrorActionPreference = 'Stop'
$apk = 'D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk'
if (!(Test-Path -Path $apk)) { throw "APK missing: $apk" }

$hash = (Get-FileHash -Algorithm SHA256 -Path $apk).Hash
$bytes = (Get-Item -Path $apk).Length
$tag = 'v0.1.0-desktop-container'
$repo = 'nekobyran/Androiddesktop'
$notes = @"
Androiddesktop signed release APK.

SHA-256: $hash
Size: $bytes bytes

Material 3 style Android desktop container shell with Dock, launcher, draggable windows, and wireless-debug/privileged-core command planning.

Boundary: this build contains placeholder Surface/VirtualDisplay slots. Real third-party app content embedding still requires a privileged core that creates/manages display sessions and forwards input.
"@

& gh release view $tag --repo $repo --json url,tagName,assets | Out-Host
$viewExit = $LASTEXITCODE
"VIEW_EXIT=$viewExit"
if ($viewExit -eq 0) {
    & gh release upload $tag $apk --repo $repo --clobber
} else {
    & gh release create $tag $apk --repo $repo --target main --title 'Androiddesktop 0.1.0 desktop container release' --notes $notes
}
if ($LASTEXITCODE -ne 0) { throw "GitHub release publish failed with exit code $LASTEXITCODE" }

"---release---"
& gh release view $tag --repo $repo --json url,tagName,assets
"---artifact---"
"path=$apk"
"sha256=$hash"
"bytes=$bytes"
