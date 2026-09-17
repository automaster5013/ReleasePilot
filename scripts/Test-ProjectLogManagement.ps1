$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$moveScript = Join-Path $PSScriptRoot "Move-ProjectLogs.ps1"
$invokeScript = Join-Path $PSScriptRoot "Invoke-WithProjectLog.ps1"
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("releasepilot-log-test-" + [guid]::NewGuid())

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

try {
    New-Item -ItemType Directory -Path (Join-Path $testRoot "work\nested") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $testRoot "node_modules\package") -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $testRoot "root.log") -Value "root-log"
    Set-Content -LiteralPath (Join-Path $testRoot "work\nested\test.log") -Value "nested-log"
    Set-Content -LiteralPath (Join-Path $testRoot "node_modules\package\managed.log") -Value "managed-log"

    & $moveScript -RepositoryRoot $testRoot | Out-Host

    Assert-True (Test-Path -LiteralPath (Join-Path $testRoot "logs\root.log")) "Root log was not centralized."
    Assert-True (Test-Path -LiteralPath (Join-Path $testRoot "logs\work\nested\test.log")) "Nested log did not preserve its relative path."
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $testRoot "root.log"))) "Original root log remains."
    Assert-True (Test-Path -LiteralPath (Join-Path $testRoot "node_modules\package\managed.log")) "Tool-managed log should be excluded."

    $fixtureScript = Join-Path $testRoot "write-log.ps1"
    Set-Content -LiteralPath $fixtureScript -Value 'Write-Output "routed-log"'
    & pwsh -NoProfile -File $invokeScript -Name smoke -Category contract -RepositoryRoot $testRoot -Executable pwsh -ArgumentList $fixtureScript | Out-Host
    Assert-True ($LASTEXITCODE -eq 0) "Logged command failed."
    $generatedLog = Get-ChildItem -LiteralPath (Join-Path $testRoot "logs\contract") -File -Filter "*-smoke.log" | Select-Object -First 1
    Assert-True ($null -ne $generatedLog) "Logged command did not create a central log."
    Assert-True ((Get-Content -LiteralPath $generatedLog.FullName -Raw).Trim() -eq "routed-log") "Generated log content differs from command output."
    & pwsh -NoProfile -File $invokeScript -Name nested -Category contract -RepositoryRoot $testRoot -WorkingDirectory work -Executable pwsh -ArgumentList $fixtureScript | Out-Host
    Assert-True ($LASTEXITCODE -eq 0) "Logged command failed in a repository subdirectory."

    Write-Output "Project log management contract passed."
} finally {
    $resolvedTestRoot = [System.IO.Path]::GetFullPath($testRoot)
    $resolvedTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTestRoot.StartsWith($resolvedTempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $resolvedTestRoot)) {
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
    }
}
