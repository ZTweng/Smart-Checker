$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$installer = Join-Path $root "releases\windows\Smart Checker-6.26.2-Setup.exe"
$testRoot = [IO.Path]::GetFullPath((Join-Path $root "build-tools\installer-smoke"))
$expected = Join-Path $testRoot "Smart Checker"

function Invoke-CheckedProcess([string]$File, [string[]]$Arguments) {
    $process = Start-Process -FilePath $File -ArgumentList $Arguments -WindowStyle Hidden -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "Process failed ($($process.ExitCode)): $File" }
}

# Test both a parent folder and an already suffixed folder, without creating shortcuts.
try {
    foreach ($destination in @($testRoot, $expected)) {
        Invoke-CheckedProcess $installer @(
            "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/NOICONS", "/TASKS=",
            ('/DIR="' + $destination + '"'),
            ('/LOG="' + (Join-Path $root "build-tools\installer-smoke.log") + '"')
        )
        if (-not (Test-Path -LiteralPath (Join-Path $expected "Smart Checker.exe"))) {
            throw "Application was not installed in the named subfolder."
        }
        if (Test-Path -LiteralPath (Join-Path $expected "Smart Checker\Smart Checker.exe")) {
            throw "Application folder was appended twice."
        }
        if (-not (Test-Path -LiteralPath (Join-Path $expected "runtime\bin\java.dll"))) {
            throw "Bundled runtime is missing."
        }
        Write-Host "PASS: $destination -> $expected"
    }
} finally {
    $uninstaller = Join-Path $expected "unins000.exe"
    if (Test-Path -LiteralPath $uninstaller) {
        Invoke-CheckedProcess $uninstaller @("/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART")
    }
}
if (Test-Path -LiteralPath (Join-Path $expected "Smart Checker.exe")) {
    throw "Uninstall did not remove the application."
}
Write-Host "PASS: uninstall removed the test application."
