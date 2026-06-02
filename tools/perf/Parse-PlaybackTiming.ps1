param(
    [Parameter(Mandatory = $true)]
    [string]$LogFile,
    [string[]]$Tags = @("PlaybackRuntimeTiming", "PlaybackSetupTiming")
)

if (-not (Test-Path -LiteralPath $LogFile)) {
    Write-Error "Log file not found: $LogFile"
    exit 1
}

$pattern = "^(?<prefix>.*?)(?<tag>$($Tags -join '|')).*phase=(?<phase>[A-Za-z0-9_]+)\s+elapsedMs=(?<elapsed>\d+)"
$samplesByPhase = @{}

Get-Content -LiteralPath $LogFile | ForEach-Object {
    $line = $_
    if ($line -match $pattern) {
        $phase = $Matches["phase"]
        $elapsed = [int]$Matches["elapsed"]

        if (-not $samplesByPhase.ContainsKey($phase)) {
            $samplesByPhase[$phase] = New-Object System.Collections.Generic.List[int]
        }

        $samplesByPhase[$phase].Add($elapsed)
    }
}

if ($samplesByPhase.Count -eq 0) {
    Write-Host "No playback timing samples found for tags: $($Tags -join ', ')"
    exit 0
}

function Get-PercentileValue {
    param(
        [int[]]$SortedValues,
        [double]$Percentile
    )

    if ($SortedValues.Count -eq 0) { return 0 }
    if ($SortedValues.Count -eq 1) { return $SortedValues[0] }

    $rank = [Math]::Ceiling(($Percentile / 100.0) * $SortedValues.Count)
    $index = [Math]::Max(1, [Math]::Min($SortedValues.Count, [int]$rank)) - 1
    return $SortedValues[$index]
}

$report = foreach ($phase in $samplesByPhase.Keys) {
    $values = $samplesByPhase[$phase].ToArray() | Sort-Object
    $count = $values.Count
    $sum = 0
    foreach ($v in $values) { $sum += $v }

    [PSCustomObject]@{
        phase = $phase
        count = $count
        avg_ms = [Math]::Round(($sum / [double]$count), 2)
        p50_ms = Get-PercentileValue -SortedValues $values -Percentile 50
        p95_ms = Get-PercentileValue -SortedValues $values -Percentile 95
        min_ms = $values[0]
        max_ms = $values[$values.Count - 1]
    }
}

$report |
    Sort-Object -Property @{ Expression = "p95_ms"; Descending = $true }, @{ Expression = "avg_ms"; Descending = $true } |
    Format-Table -AutoSize
