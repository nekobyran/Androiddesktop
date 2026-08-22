$ErrorActionPreference = 'Stop'
$apk = 'D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk'
if (!(Test-Path -Path $apk)) { throw "APK missing: $apk" }

$hash = (Get-FileHash -Algorithm SHA256 -Path $apk).Hash
$bytes = (Get-Item -Path $apk).Length
$tag = 'v0.2.0'
$repo = 'nekobyran/Androiddesktop'
$notes = @"
Androiddesktop 0.2.0 signed release APK.

SHA-256: $hash
Size: $bytes bytes

Highlights:
- Desktop workspace no longer uses a giant rounded card; windows sit directly on the wallpaper.
- Added an unobtrusive desktop introduction with the current logical scale / effective DPI.
- App-local global UI scaling supports 50%-125%, defaults to 65%, and keeps the desktop shell and VirtualDisplay density aligned.
- Includes the wireless-debugging guide, HOME-role entry, performance diagnostics, niri-like scrollable columns, and unified motion tokens.

Boundary: real third-party app embedding still depends on the privileged shell/core path and Android platform permissions. The release does not bypass Android security prompts.
"@

& gh release view $tag --repo $repo --json url,tagName,assets | Out-Host
$viewExit = $LASTEXITCODE
"VIEW_EXIT=$viewExit"
if ($viewExit -eq 0) {
    & gh release upload $tag $apk --repo $repo --clobber
} else {
        & gh release create $tag $apk --repo $repo --target main --title 'Androiddesktop 0.2.0 desktop-scale release' --notes $notes
}
if ($LASTEXITCODE -ne 0) { throw "GitHub release publish failed with exit code $LASTEXITCODE" }

"---release---"
& gh release view $tag --repo $repo --json url,tagName,assets
"---artifact---"
"path=$apk"
"sha256=$hash"
"bytes=$bytes"
