param(
  [string]$Domain = "releasepilot.kr",
  [string]$DatabasePassword = "",
  [string]$MysqlRootPassword = ""
)
$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($DatabasePassword)) {
  $DatabasePassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
}
if ([string]::IsNullOrWhiteSpace($MysqlRootPassword)) {
  $MysqlRootPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
}
$VerifiedManifestDirectory = & "$PSScriptRoot/get-platform-manifests.ps1"
kubectl create namespace argo-rollouts --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argo-rollouts -f (Join-Path $VerifiedManifestDirectory "argo-rollouts.yaml")
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd -f (Join-Path $VerifiedManifestDirectory "argocd.yaml")
kubectl apply -f (Join-Path $VerifiedManifestDirectory "cert-manager.yaml")
kubectl patch deployment cert-manager -n cert-manager --type=json --patch-file "$PSScriptRoot/cert-manager-dns-patch.json"
kubectl apply -f (Join-Path $VerifiedManifestDirectory "ingress-nginx.yaml")
kubectl apply -f "$PSScriptRoot/ingress-nginx-service.yaml"
kubectl apply -f "$PSScriptRoot/external-dns.yaml"
kubectl -n ingress-nginx annotate service ingress-nginx-controller external-dns.alpha.kubernetes.io/hostname="$Domain" --overwrite
kubectl create namespace releasepilot --dry-run=client -o yaml | kubectl apply -f -
kubectl -n releasepilot create secret generic releasepilot-runtime --from-literal="DATABASE_PASSWORD=$DatabasePassword" --from-literal="MYSQL_ROOT_PASSWORD=$MysqlRootPassword" --dry-run=client -o yaml | kubectl apply -f -
& "$PSScriptRoot/bootstrap-gitops.ps1"
Write-Output "Platform and ReleasePilot GitOps applications installed. Wait for the load balancer, DNS record and certificate."
