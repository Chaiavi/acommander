# Draft release notes from a milestone's closed issues, grouped by label. Usage: notes.ps1 -Version 5.0 [-OutFile f] [-Title]
# -Title prints only the milestone title (the release title). -Verify f exits 1 unless f links every issue and no other.
# Needs gh, logged in or GH_TOKEN set.
# Write notes with -OutFile: a console pipe uses the OEM code page and turns the emoji into '??'.
param([Parameter(Mandatory)][string]$Version, [string]$OutFile, [switch]$Title, [string]$Verify)
$ErrorActionPreference = 'Stop'
$repo = 'Chaiavi/acommander'
$milestone = gh api "repos/$repo/milestones?state=all&per_page=100" | ConvertFrom-Json |
        Where-Object { $_.title -match "^v$([regex]::Escape($Version))(\s|$)" } | Select-Object -First 1
if (-not $milestone) { throw "No milestone whose title starts with v$Version" }
if ($Title) { $milestone.title; exit 0 }

$issues = gh issue list --repo $repo --milestone $milestone.title --state closed --limit 500 --json number,title,labels,url,stateReason |
        ConvertFrom-Json | Where-Object { $_.stateReason -ne 'NOT_PLANNED' } | Sort-Object number
if ($Verify) {
    $linked = [regex]::Matches((Get-Content $Verify -Raw -Encoding utf8), '\[#(\d+)\]') | ForEach-Object { [int]$_.Groups[1].Value }
    $missing = $issues.number | Where-Object { $_ -notin $linked }
    $extra = $linked | Sort-Object -Unique | Where-Object { $_ -notin $issues.number }
    "$($issues.Count) issues; missing: $($missing -join ', '); not in the milestone: $($extra -join ', ')"
    exit [int]($missing.Count + $extra.Count -gt 0)
}
$groups = [ordered]@{ '✨ New Features & Enhancements' = @(); '🐛 Bug Fixes' = @(); '📦 Other' = @() }
foreach ($issue in $issues) {
    $labels = $issue.labels.name
    $group = if ($labels -contains 'enhancement') { '✨ New Features & Enhancements' } elseif ($labels -contains 'bug') { '🐛 Bug Fixes' } else { '📦 Other' }
    $groups[$group] += "- $($issue.title) ([#$($issue.number)]($($issue.url)))"
}

$notes = & {
"# 🚀 aCommander v$Version — Release Notes"
''
"📅 $((Get-Date).ToString('MMMM d, yyyy', [Globalization.CultureInfo]::InvariantCulture))"
''
"$($groups[0].Count) new features, $($groups[1].Count) bug fixes, $($groups[2].Count) other."
foreach ($name in $groups.Keys) {
    if ($groups[$name].Count) { ''; "## $name"; ''; $groups[$name] }
}
''
'🎉 Thank you for using aCommander! Found a bug or have an idea? Use Report Bug / Contact in the app.'
}
if ($OutFile) { Set-Content -Path $OutFile -Value $notes -Encoding utf8NoBOM } else { $notes }
