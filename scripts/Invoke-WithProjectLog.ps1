[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Name,

    [Parameter(Mandatory)]
    [string]$Executable,

    [string[]]$ArgumentList = @(),

    [string]$Category = "runs",

    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$repositoryPath = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$safeName = $Name -replace '[^A-Za-z0-9._-]', '-'
$safeCategory = $Category -replace '[^A-Za-z0-9._-]', '-'
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logDirectory = Join-Path $repositoryPath (Join-Path "logs" $safeCategory)
$logPath = Join-Path $logDirectory ("{0}-{1}.log" -f $timestamp, $safeName)

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

Push-Location $repositoryPath
try {
    & $Executable @ArgumentList 2>&1 | Tee-Object -FilePath $logPath
    $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
} finally {
    Pop-Location
}

Write-Output "Log: $logPath"
exit $exitCode
