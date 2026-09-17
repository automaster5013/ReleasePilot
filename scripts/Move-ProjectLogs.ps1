[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$repositoryPath = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$logRoot = Join-Path $repositoryPath "logs"
$excludedSegments = @(
    ".git",
    "logs",
    "node_modules",
    ".next",
    "target",
    ".venv",
    "pyenv",
    "npm-cache",
    ".terraform"
)

$logFiles = Get-ChildItem -LiteralPath $repositoryPath -Recurse -File -Filter "*.log" -ErrorAction SilentlyContinue |
    Where-Object {
        $relativePath = [System.IO.Path]::GetRelativePath($repositoryPath, $_.FullName)
        $segments = $relativePath -split '[\\/]'
        -not ($segments | Where-Object { $excludedSegments -contains $_ })
    }

foreach ($logFile in $logFiles) {
    $relativePath = [System.IO.Path]::GetRelativePath($repositoryPath, $logFile.FullName)
    $destination = Join-Path $logRoot $relativePath
    $destinationDirectory = Split-Path -Parent $destination

    if ($PSCmdlet.ShouldProcess($logFile.FullName, "Move to $destination")) {
        New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
        Move-Item -LiteralPath $logFile.FullName -Destination $destination -Force
    }
}

Write-Output ("Organized {0} project log file(s) under {1}." -f $logFiles.Count, $logRoot)
