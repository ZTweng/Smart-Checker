param([string]$JavaHome = "D:\java", [string]$GradlePath = "gradle.bat")
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = Join-Path $root "build-tools\android-sdk"
    $secrets = Join-Path $root ".signing"
    New-Item -ItemType Directory -Path $secrets -Force | Out-Null
    $key = Join-Path $secrets "smart-checker-release.jks"
    $credentialFile = Join-Path $secrets "android-signing.credential.xml"
    if (-not (Test-Path -LiteralPath $key)) {
        if (-not (Test-Path -LiteralPath $credentialFile)) {
            $bytes = New-Object byte[] 32
            $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
            $password = [Convert]::ToBase64String($bytes)
            $credential = [PSCredential]::new("smart-checker", (ConvertTo-SecureString $password -AsPlainText -Force))
            $credential | Export-Clixml -LiteralPath $credentialFile
        }
        $credential = Import-Clixml -LiteralPath $credentialFile
        $env:SMART_CHECKER_STORE_PASSWORD = $credential.GetNetworkCredential().Password
        $env:SMART_CHECKER_KEY_PASSWORD = $env:SMART_CHECKER_STORE_PASSWORD
        & "$JavaHome\bin\keytool.exe" -genkeypair -keystore $key -storetype JKS -alias smart-checker `
            -storepass:env SMART_CHECKER_STORE_PASSWORD -keypass:env SMART_CHECKER_KEY_PASSWORD `
            -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Smart Checker, O=SAMC" -noprompt
        if ($LASTEXITCODE -ne 0) { throw "Signing key generation failed." }
    }
    $credential = Import-Clixml -LiteralPath $credentialFile
    $env:SMART_CHECKER_KEYSTORE = $key
    $env:SMART_CHECKER_STORE_PASSWORD = $credential.GetNetworkCredential().Password
    $env:SMART_CHECKER_KEY_PASSWORD = $env:SMART_CHECKER_STORE_PASSWORD
    & $GradlePath :androidApp:testDebugUnitTest :androidApp:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Android build failed." }
    $release = Join-Path $root "releases\android"
    New-Item -ItemType Directory -Path $release -Force | Out-Null
    $destination = Join-Path $release "Smart Checker-6.26.2.apk"
    Copy-Item -LiteralPath "androidApp\build\outputs\apk\release\androidApp-release.apk" -Destination $destination -Force
    & "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs $destination
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed." }
    Write-Host "APK: $destination"
    Write-Host "Keep .signing backed up privately. The credential is protected for this Windows user."
} finally {
    Remove-Item Env:SMART_CHECKER_STORE_PASSWORD, Env:SMART_CHECKER_KEY_PASSWORD, Env:SMART_CHECKER_KEYSTORE -ErrorAction SilentlyContinue
    Pop-Location
}
