param([switch]$Publish)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$repo = "ZTweng/Smart-Checker"
$version = "6.26.2"
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "Install GitHub CLI and run 'gh auth login' first. Never put credentials in this script."
}
$assets = @(
    (Join-Path $root "releases\windows\Smart Checker-$version-Setup.exe"),
    (Join-Path $root "releases\android\Smart Checker-$version.apk")
)
foreach ($asset in $assets) {
    if (-not (Test-Path -LiteralPath $asset)) { throw "Missing artifact: $asset" }
}
$notes = Join-Path $PSScriptRoot "release-notes.md"
& gh release view "v$version" --repo $repo *> $null
if ($LASTEXITCODE -ne 0) {
    & gh release create "v$version" --repo $repo --title "Smart Checker $version" --notes-file $notes --draft
    if ($LASTEXITCODE -ne 0) { throw "Could not create draft release." }
}
& gh release upload "v$version" @assets --repo $repo --clobber
if ($LASTEXITCODE -ne 0) { throw "Upload failed." }
if ($Publish) {
    & gh release edit "v$version" --repo $repo --draft=false --latest
    if ($LASTEXITCODE -ne 0) { throw "Publish failed." }
}
Write-Host "Release prepared: https://github.com/$repo/releases/tag/v$version"
