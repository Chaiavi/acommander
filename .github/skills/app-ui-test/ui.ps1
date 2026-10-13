param([int]$procId, [string]$keys = '', [string]$shot = '', [int]$wait = 1500, [switch]$noActivate, [string]$title = '')
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
    public delegate bool EnumProc(IntPtr h, IntPtr l);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc f, IntPtr l);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, System.Text.StringBuilder s, int n);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern IntPtr GetKeyboardLayout(uint thread);
    [DllImport("user32.dll")] public static extern IntPtr LoadKeyboardLayout(string id, uint flags);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
}
"@
[UiProbe]::SetProcessDPIAware() | Out-Null
$sh = New-Object -ComObject WScript.Shell
# TEXT: steps would arrive upper-case
if ([Console]::CapsLock) { $sh.SendKeys('{CAPSLOCK}') }
if ($title) {
    $sh.SendKeys('%')
    $sh.AppActivate($title) | Out-Null
} elseif (-not $noActivate) {
    $sh.SendKeys('%')
    $sh.AppActivate($procId) | Out-Null
}
Start-Sleep -Milliseconds 400
# SendKeys can't type Latin letters under a Hebrew layout (Ctrl+Shift+P never arrives): use US English meanwhile
$front = [UiProbe]::GetForegroundWindow()
$frontPid = 0
$layout = [UiProbe]::GetKeyboardLayout([UiProbe]::GetWindowThreadProcessId($front, [ref]$frontPid))
$switched = $keys -and $frontPid -eq $procId -and ($layout.ToInt64() -band 0xFFFF) -ne 0x0409
if ($switched) {
    [UiProbe]::PostMessage($front, 0x50, [IntPtr]::Zero, [UiProbe]::LoadKeyboardLayout('00000409', 1)) | Out-Null
    Start-Sleep -Milliseconds 200
}
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
if ($switched) {
    [UiProbe]::PostMessage([UiProbe]::GetForegroundWindow(), 0x50, [IntPtr]::Zero, $layout) | Out-Null
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
$titles = [System.Collections.Generic.List[string]]::new()
[UiProbe]::EnumWindows({ param($h, $l)
    $p = 0; [UiProbe]::GetWindowThreadProcessId($h, [ref]$p) | Out-Null
    if ($p -eq $procId -and [UiProbe]::IsWindowVisible($h)) {
        $sb = [System.Text.StringBuilder]::new(256); [UiProbe]::GetWindowText($h, $sb, 256) | Out-Null; $titles.Add($sb.ToString())
    }
    $true }, [IntPtr]::Zero) | Out-Null
"app windows: $($titles -join ' | ')"
