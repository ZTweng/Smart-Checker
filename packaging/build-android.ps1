param([string]$JavaHome = "D:\java", [string]$GradlePath = "gradle.bat")
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = Join-Path $root "build-tools\android-sdk"
    $secrets = Join-Path $root ".signing"
    $key = Join-Path $secrets "smart-checker-release.jks"
    $credentialFile = Join-Path $secrets "android-signing.credential.xml"
    if (-not $env:SMART_CHECKER_KEYSTORE) {
        if (-not (Test-Path -LiteralPath $key) -or -not (Test-Path -LiteralPath $credentialFile)) {
            throw "Existing release signing credentials are required. Never generate a replacement key for an update."
        }
        $credential = Import-Clixml -LiteralPath $credentialFile
        $env:SMART_CHECKER_KEYSTORE = $key
        $env:SMART_CHECKER_STORE_PASSWORD = $credential.GetNetworkCredential().Password
        $env:SMART_CHECKER_KEY_PASSWORD = $env:SMART_CHECKER_STORE_PASSWORD
    }
    if (-not (Test-Path -LiteralPath $env:SMART_CHECKER_KEYSTORE) -or
        -not $env:SMART_CHECKER_STORE_PASSWORD -or -not $env:SMART_CHECKER_KEY_PASSWORD) {
        throw "Release keystore and both signing passwords must be supplied."
    }
    & $GradlePath :androidApp:testDebugUnitTest :androidApp:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Android build failed." }
    $release = Join-Path $root "releases\android"
    New-Item -ItemType Directory -Path $release -Force | Out-Null
    $destination = Join-Path $release "Smart Checker-6.26.4.apk"
    Copy-Item -LiteralPath "androidApp\build\outputs\apk\release\androidApp-release.apk" -Destination $destination -Force
    & "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs $destination
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed." }
    Write-Host "APK: $destination"
    Write-Host "Keep .signing backed up privately. The credential is protected for this Windows user."
} finally {
    Remove-Item Env:SMART_CHECKER_STORE_PASSWORD, Env:SMART_CHECKER_KEY_PASSWORD, Env:SMART_CHECKER_KEYSTORE -ErrorAction SilentlyContinue
    Pop-Location
}
