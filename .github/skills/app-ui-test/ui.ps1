param([int]$procId, [string]$keys = '', [string]$shot = '', [int]$wait = 1500, [switch]$noActivate)
# Steps are split by "||": SendKeys codes ({ENTER}, ^a, ^+p), TEXT:<ascii text>, CLICK:x,y (pixels from the front window's corner).
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class UiProbe {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, int x, int y, uint d, IntPtr e);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
[UiProbe]::SetProcessDPIAware() | Out-Null
$sh = New-Object -ComObject WScript.Shell
if (-not $noActivate) {
    $sh.SendKeys('%')
    $sh.AppActivate($procId) | Out-Null
}
Start-Sleep -Milliseconds 400
foreach ($k in ($keys -split '\|\|' | Where-Object { $_ -ne '' })) {
    if ($k -like 'CLICK:*') {
        $xy = $k.Substring(6) -split ','
        $r0 = New-Object UiProbe+RECT; [UiProbe]::GetWindowRect([UiProbe]::GetForegroundWindow(), [ref]$r0) | Out-Null
        [UiProbe]::SetCursorPos($r0.Left + [int]$xy[0], $r0.Top + [int]$xy[1]) | Out-Null
        [UiProbe]::mouse_event(2, 0, 0, 0, [IntPtr]::Zero); [UiProbe]::mouse_event(4, 0, 0, 0, [IntPtr]::Zero)
    } elseif ($k -like 'TEXT:*') {
        foreach ($c in $k.Substring(5).ToCharArray()) { $sh.SendKeys(($c -replace '([+^%~(){}\[\]])', '{$1}')); Start-Sleep -Milliseconds 30 }
    } else { $sh.SendKeys($k) }
    Start-Sleep -Milliseconds 500
}
Start-Sleep -Milliseconds $wait
if ($shot) {
    $fg = [UiProbe]::GetForegroundWindow()
    $owner = 0; [UiProbe]::GetWindowThreadProcessId($fg, [ref]$owner) | Out-Null
    $r = New-Object UiProbe+RECT; [UiProbe]::GetWindowRect($fg, [ref]$r) | Out-Null
    $w = $r.Right - $r.Left; $h = $r.Bottom - $r.Top
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
    $bmp.Save($shot, [System.Drawing.Imaging.ImageFormat]::Png)
    "foreground pid=$owner size=${w}x$h"
}
