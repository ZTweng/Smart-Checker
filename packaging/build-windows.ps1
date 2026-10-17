param(
    [switch]$SkipBuild,
    [string]$IsccPath = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    if (-not $SkipBuild) {
        $gradle = if (Test-Path -LiteralPath ".\gradlew.bat") { ".\gradlew.bat" } else { "gradle.bat" }
        & $gradle :desktopApp:createDistributable
        if ($LASTEXITCODE -ne 0) { throw "Desktop build failed." }
    }
    if (-not $IsccPath) {
        $IsccPath = Join-Path $root "build-tools\inno-setup\ISCC.exe"
    }
    if (-not (Test-Path -LiteralPath $IsccPath)) {
        throw "Inno Setup compiler not found. Supply -IsccPath."
    }
    & $IsccPath (Join-Path $PSScriptRoot "smart-checker.iss")
    if ($LASTEXITCODE -ne 0) { throw "Windows installer build failed." }
    Write-Host "Installer: $root\releases\windows\Smart Checker-6.26.2-Setup.exe"
} finally {
    Pop-Location
}
