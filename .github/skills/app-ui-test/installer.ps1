# Release checklist "Installer": installs a setup (default dist\acommander-setup-v*.exe) into a temp folder, starts it,
# reinstalls over the running app, uninstalls. Prints PASS/FAIL per step; exit 1 on any failure. Never touches a real install.
param([string]$SetupPath)
$ErrorActionPreference = 'Stop'
$key = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{1D54CF8C-10E1-4E2A-BD4D-5F4CFE658C1C}_is1'
if (Test-Path $key) { Write-Output 'SKIP: the user has a real install; a test install would take it over'; exit 0 }
$root = Split-Path (Split-Path (Split-Path $PSScriptRoot))
$setup = if ($SetupPath) { Get-Item $SetupPath } else { Get-ChildItem "$root\dist\acommander-setup-v*.exe" | Select-Object -First 1 }
$dir = "$env:TEMP\ac-install"
$setupLog = "$env:TEMP\ac-setup.log"
$failed = $false
function Check($name, $ok, $detail) {
    if ($ok) { Write-Output "PASS $name" } else { Write-Output "FAIL $name $detail"; $script:failed = $true }
}
function Install { (Start-Process $setup.FullName -Wait -PassThru -ArgumentList '/VERYSILENT', '/SUPPRESSMSGBOXES',
    '/NORESTART', '/TASKS=""', "/DIR=$dir", "/LOG=$setupLog").ExitCode }
function AppProcesses { Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -like "$dir\*" } }

# 1. Install
$code = Install
$lnk = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\ACommander.lnk"
$target = if (Test-Path $lnk) { (New-Object -ComObject WScript.Shell).CreateShortcut($lnk).TargetPath }
Check '1 install' ($code -eq 0 -and $target -like "$dir\*") "exit=$code lnk=$target"

# 2. Start from the Start Menu
Start-Process $lnk
$deadline = (Get-Date).AddSeconds(30)
while (-not (Test-Path "$dir\config\acommander.properties") -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
Start-Sleep 5
$errors = if (Test-Path "$dir\logs\ALL.log") { Select-String -Path "$dir\logs\ALL.log" -Pattern ' ERROR ' -CaseSensitive }
Check '2 start' ((AppProcesses) -and (Test-Path "$dir\logs") -and (Test-Path "$dir\config\acommander.properties") -and -not $errors) "$errors"

# 3. Reinstall over the running app keeps user settings
$ini = Get-ChildItem $dir -Recurse -Filter UniExtract.ini | Select-Object -First 1
Add-Content $ini.FullName 'acprobe=1'
Add-Content "$dir\config\acommander.properties" 'acprobe=1'
$code = Install
$closed = Select-String -Path $setupLog -Pattern 'Shutting down applications' -Quiet
$kept = (Select-String -Path $ini.FullName -Pattern acprobe -Quiet) -and (Select-String -Path "$dir\config\acommander.properties" -Pattern acprobe -Quiet)
Check '3 reinstall' ($code -eq 0 -and $closed -and $kept) "exit=$code closed=$closed kept=$kept"

# 4. Uninstall
AppProcesses | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
$code = (Start-Process "$dir\unins000.exe" -Wait -PassThru -ArgumentList '/VERYSILENT', '/SUPPRESSMSGBOXES').ExitCode
$deadline = (Get-Date).AddSeconds(15)
while ((Test-Path $dir) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
Check '4 uninstall' ($code -eq 0 -and -not (Test-Path $dir) -and -not (Test-Path $lnk) -and -not (Test-Path $key)) "exit=$code"
Remove-Item $setupLog -ErrorAction SilentlyContinue
if ($failed) { exit 1 }
