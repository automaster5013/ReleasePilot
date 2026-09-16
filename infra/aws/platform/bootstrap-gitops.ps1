param(
  [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$ArgoDirectory = Join-Path $RepositoryRoot "deploy\argocd"
$Applications = @(
  "releasepilot-platform",
  "releasepilot-demo",
  "artifact-policy-controller",
  "artifact-trust-policies"
)

kubectl wait --for=condition=Established crd/applications.argoproj.io --timeout="${TimeoutSeconds}s"
if ($LASTEXITCODE -ne 0) {
  throw "Argo CD Application CRD did not become ready"
}

kubectl apply -k $ArgoDirectory
if ($LASTEXITCODE -ne 0) {
  throw "Failed to apply the ReleasePilot platform Application"
}

$Deadline = (Get-Date).AddSeconds($TimeoutSeconds)
foreach ($Application in $Applications) {
  do {
    $State = kubectl -n argocd get application $Application `
      -o jsonpath='{.status.sync.status},{.status.health.status}' 2>$null
    if ($State -eq "Synced,Healthy") {
      break
    }
    if ((Get-Date) -ge $Deadline) {
      throw "Argo CD Application $Application did not become Synced/Healthy (last state: $State)"
    }
    Start-Sleep -Seconds 5
  } while ($true)
  Write-Output "$Application Synced/Healthy"
}

Write-Output "ReleasePilot GitOps bootstrap completed."
