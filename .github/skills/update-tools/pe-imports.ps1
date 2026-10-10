param([string]$Exe)
# Lists a PE file's static DLL imports (import directory), to tell them from LoadLibrary names.
$b = [IO.File]::ReadAllBytes($Exe)
$pe = [BitConverter]::ToInt32($b, 0x3C)
$magic = [BitConverter]::ToUInt16($b, $pe + 24)
$numSec = [BitConverter]::ToUInt16($b, $pe + 6)
$optSize = [BitConverter]::ToUInt16($b, $pe + 20)
$dd = $pe + 24 + $(if ($magic -eq 0x20b) { 112 } else { 96 })
$impRva = [BitConverter]::ToInt32($b, $dd + 8)
$secs = $pe + 24 + $optSize
function RvaToOff([int]$rva) {
    for ($i = 0; $i -lt $numSec; $i++) {
        $s = $secs + 40 * $i
        $va = [BitConverter]::ToInt32($b, $s + 12); $vs = [BitConverter]::ToInt32($b, $s + 8)
        $raw = [BitConverter]::ToInt32($b, $s + 20); $rs = [BitConverter]::ToInt32($b, $s + 16)
        if ($rva -ge $va -and $rva -lt $va + [Math]::Max($vs, $rs)) { return $rva - $va + $raw }
    }
    return -1
}
$o = RvaToOff $impRva
while ($true) {
    $nameRva = [BitConverter]::ToInt32($b, $o + 12)
    if ($nameRva -eq 0) { break }
    $n = RvaToOff $nameRva; $e = $n
    while ($b[$e] -ne 0) { $e++ }
    [Text.Encoding]::ASCII.GetString($b, $n, $e - $n)
    $o += 20
}
