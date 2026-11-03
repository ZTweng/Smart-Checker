$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing, System.Windows.Forms
New-Item -ItemType Directory -Path screenshots -Force | Out-Null
$exe = Join-Path $PWD 'desktopApp/build/compose/binaries/main/app/Smart Checker/Smart Checker.exe'
foreach ($theme in @("light", "dark")) {
    $value = if ($theme -eq "light") { 1 } else { 0 }
    $key = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize"
    New-Item -Path $key -Force | Out-Null
    Set-ItemProperty -Path $key -Name AppsUseLightTheme -Value $value
    $process = Start-Process -FilePath $exe -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 15
    if ($process.HasExited) { throw "Application exited before capture" }
    # Reveal only the application window inside the CI desktop session.
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class CaptureWindow$theme {
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int n);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
}
"@
    $process.Refresh()
    $type = "CaptureWindow$theme" -as [type]
    $type::ShowWindow($process.MainWindowHandle, 3) | Out-Null
    $type::SetForegroundWindow($process.MainWindowHandle) | Out-Null
    Start-Sleep -Seconds 5
    $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
    $bitmap = [System.Drawing.Bitmap]::new($bounds.Width, $bounds.Height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)
        $bitmap.Save((Join-Path $PWD "screenshots/windows-$theme.png"))
    } finally { $graphics.Dispose(); $bitmap.Dispose(); Stop-Process -Id $process.Id }
}
