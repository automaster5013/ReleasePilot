param(
  [string]$OutputDirectory = (Join-Path ([IO.Path]::GetTempPath()) "ReleasePilot\platform-manifests")
)

$ErrorActionPreference = "Stop"
$Manifests = @(
  @{
    Name = "argo-rollouts.yaml"
    Uri = "https://github.com/argoproj/argo-rollouts/releases/download/v1.8.3/install.yaml"
    Sha256 = "6f4f581dab41450ca623a03d246156fa7fa79901f7f01693c5df66f7557fc145"
  },
  @{
    Name = "argocd.yaml"
    Uri = "https://raw.githubusercontent.com/argoproj/argo-cd/v3.1.7/manifests/install.yaml"
    Sha256 = "5b0f557831d0820ed935c05478f51206b787c735571a467fe803ec45bb17211f"
  },
  @{
    Name = "cert-manager.yaml"
    Uri = "https://github.com/cert-manager/cert-manager/releases/download/v1.18.2/cert-manager.yaml"
    Sha256 = "0546b64bc806237885ccc80e345bce1585eb2ec2f476a7af593f030abdb3a16f"
  },
  @{
    Name = "ingress-nginx.yaml"
    Uri = "https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.13.3/deploy/static/provider/aws/deploy.yaml"
    Sha256 = "131d1091fdc9fa9b30e1520935126a05819d95b6d3f6cede448ba8918afa9451"
  }
)

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
foreach ($Manifest in $Manifests) {
  $Destination = Join-Path $OutputDirectory $Manifest.Name
  $Download = "$Destination.download"
  Invoke-WebRequest -Uri $Manifest.Uri -OutFile $Download
  $Actual = (Get-FileHash -LiteralPath $Download -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($Actual -ne $Manifest.Sha256) {
    Remove-Item -LiteralPath $Download -Force
    throw "SHA-256 mismatch for $($Manifest.Name): expected $($Manifest.Sha256), got $Actual"
  }
  Move-Item -LiteralPath $Download -Destination $Destination -Force
}

(Resolve-Path $OutputDirectory).Path
