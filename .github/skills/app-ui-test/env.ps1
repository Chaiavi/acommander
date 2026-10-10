param([ValidateSet('start', 'stop')][string]$action = 'start')
# An isolated app copy in %TEMP%\ac-apptest: own settings, apps/ as a junction, the same test files every run.
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot\..\..\..").Path
$t = Join-Path $env:TEMP 'ac-apptest'

function Stop-TestApp {
    if (Test-Path "$t\pid.txt") { Stop-Process -Id ([int](Get-Content "$t\pid.txt")) -Force -ErrorAction SilentlyContinue; Start-Sleep -Seconds 1 }
    # rmdir removes the junction only; Remove-Item -Recurse could follow it into the real apps folder
    if (Test-Path "$t\apps") { cmd /c rmdir "$t\apps" }
    if (-not (Test-Path "$repo\apps\pdf\qpdf.exe")) { throw "the repo's apps folder looks damaged: $repo\apps" }
    if (Test-Path $t) { Remove-Item $t -Recurse -Force }
}

function Write-TestPdf([string]$file, [int]$pages) {
    $objects = @('<< /Type /Catalog /Pages 2 0 R >>',
        "<< /Type /Pages /Kids [$((1..$pages | ForEach-Object { "$($_ + 2) 0 R" }) -join ' ')] /Count $pages >>") +
        (1..$pages | ForEach-Object { '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << >> >>' })
    $pdf = [Text.StringBuilder]::new("%PDF-1.4`n")
    $offsets = foreach ($i in 0..($objects.Count - 1)) { $pdf.Length; [void]$pdf.Append("$($i + 1) 0 obj`n$($objects[$i])`nendobj`n") }
    $xref = $pdf.Length
    [void]$pdf.Append("xref`n0 $($objects.Count + 1)`n0000000000 65535 f `n")
    foreach ($offset in $offsets) { [void]$pdf.Append(('{0:D10} 00000 n ' -f $offset) + "`n") }
    [void]$pdf.Append("trailer`n<< /Size $($objects.Count + 1) /Root 1 0 R >>`nstartxref`n$xref`n%%EOF`n")
    [IO.File]::WriteAllText($file, $pdf.ToString(), [Text.Encoding]::Latin1)
}

Stop-TestApp
if ($action -eq 'stop') { 'stopped and removed'; return }

New-Item -ItemType Directory -Force "$t\config", "$t\in\nested", "$t\out", "$t\shots" | Out-Null
Copy-Item "$repo\config\apps.json" "$t\config\"
$esc = { param($p) $p.Replace('\', '\\').Replace(':', '\:') }
[IO.File]::WriteAllText("$t\config\acommander.properties",
    "left_folder=$(& $esc "$t\in")`nright_folder=$(& $esc "$t\out")`ntool_updates_at_start=false`n")
New-Item -ItemType Junction -Path "$t\apps" -Target "$repo\apps" | Out-Null

$in = "$t\in"
Write-TestPdf "$in\ראשון.pdf" 3
Write-TestPdf "$in\second.pdf" 2
Set-Content "$in\notes.txt" 'the needle is here'
Set-Content "$in\שלום.txt" 'shalom'
Set-Content "$in\nested\deep.txt" 'deep'
Add-Type -AssemblyName System.Drawing
$bitmap = New-Object System.Drawing.Bitmap 64, 48
$bitmap.SetPixel(10, 10, [System.Drawing.Color]::Orange)
$bitmap.Save("$in\picture.png", [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
& "$repo\apps\media\ffmpeg.exe" -v error -f lavfi -i 'sine=frequency=440:duration=3' "$in\tone.wav"
Compress-Archive -Path "$in\notes.txt" -DestinationPath "$in\box.zip"
[IO.File]::WriteAllBytes("$in\big.bin", [byte[]]::new(3MB))

$app = Start-Process "$repo\build\runtime\bin\javaw.exe" -ArgumentList '--enable-native-access=ALL-UNNAMED', '-jar',
    "`"$repo\build\libs\acommander.jar`"" -WorkingDirectory $t -PassThru
Set-Content "$t\pid.txt" $app.Id
Start-Sleep -Seconds 6
"pid $($app.Id); folder $t"
