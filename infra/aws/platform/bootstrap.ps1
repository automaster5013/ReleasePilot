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
kubectl create namespace argo-rollouts --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/download/v1.8.3/install.yaml
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/v3.1.7/manifests/install.yaml
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.18.2/cert-manager.yaml
kubectl patch deployment cert-manager -n cert-manager --type=json --patch-file "$PSScriptRoot/cert-manager-dns-patch.json"
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.13.3/deploy/static/provider/aws/deploy.yaml
kubectl apply -f "$PSScriptRoot/ingress-nginx-service.yaml"
kubectl apply -f "$PSScriptRoot/external-dns.yaml"
kubectl -n ingress-nginx annotate service ingress-nginx-controller external-dns.alpha.kubernetes.io/hostname="$Domain" --overwrite
kubectl create namespace releasepilot --dry-run=client -o yaml | kubectl apply -f -
kubectl -n releasepilot create secret generic releasepilot-runtime --from-literal="DATABASE_PASSWORD=$DatabasePassword" --from-literal="MYSQL_ROOT_PASSWORD=$MysqlRootPassword" --dry-run=client -o yaml | kubectl apply -f -
Write-Output "Platform installed. Wait for the load balancer, DNS record and certificate, then apply the Argo CD Application."
