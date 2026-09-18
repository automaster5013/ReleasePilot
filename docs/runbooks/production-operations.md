# Production operations

This runbook turns the production overlay into a controlled go-live. The files are safe defaults and intentionally retain placeholder domain, email and image values until an operator supplies organization-owned values.

## Readiness gates

Do not open production traffic until every gate is green:

1. Replace `releasepilot.example.com`, `ops@example.com`, and all `REPLACE_WITH_*_IMAGE` values in `deploy/overlays/production`.
2. Delegate the Route 53 zone and confirm the final record is managed by ExternalDNS.
3. Install ingress-nginx, cert-manager, External Secrets Operator (service account `external-secrets` in namespace `external-secrets`), metrics-server, Argo Rollouts, and kube-prometheus-stack.
4. Populate AWS Secrets Manager secret `releasepilot/production/runtime` with the required JSON keys below.
5. Register the exact HTTPS OIDC callback: `https://<domain>/login/oauth2/code/releasepilot`.
6. Render and review the overlay, then deploy through Argo CD. Never apply an unreviewed local render to production.
7. Confirm certificate Ready, all rollouts Healthy, alerts loaded, backups restorable, and the k6 thresholds pass.

## Runtime secret contract

Store values only in AWS Secrets Manager. The repository and Kubernetes manifests must contain references, never values.

Required keys:

- `DATABASE_PASSWORD`, `MYSQL_ROOT_PASSWORD`
- `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_ISSUER_URI`, `OIDC_ALLOWED_EMAIL_DOMAINS`

Optional integration keys include `GITHUB_CHECKS_APP_ID`, `GITHUB_CHECKS_INSTALLATION_ID`, and the private-key configuration used by the selected GitHub App delivery mode. Rotate database, OIDC, GitHub App, and webhook credentials at least every 90 days and immediately after suspected exposure. Update Secrets Manager, wait for External Secrets reconciliation, and restart only the affected rollout.

## Domain and TLS verification

```bash
kubectl -n releasepilot get ingress,certificate,externalsecret
kubectl -n releasepilot describe certificate releasepilot-production-tls
curl -fsS https://<domain>/actuator/health/readiness
curl -sSI http://<domain>/ | grep -i '^location: https://'
```

Certificate renewal alerts fire 14 days before expiry. HSTS, HTTPS redirect, secure/HTTP-only/SameSite cookies, restrictive browser policies, and ingress rate limits are enabled by the production bundle.

## Authentication checks

- Shared demo login and local password login return 404 in the production profile.
- OIDC identities are not auto-provisioned. Link users deliberately and assign least-privilege project roles.
- Require MFA and phishing-resistant authentication in the identity provider.
- Review active sessions after role changes and revoke all other sessions after account recovery.

## Monitoring and alert delivery

The overlay installs a ServiceMonitor and alerts for control-plane outage, 5xx ratio, p95 latency, certificate expiry, and rollout availability. Configure Alertmanager receivers outside Git and route `critical` alerts to the primary on-call channel; route `warning` alerts to the operations queue. Send one test alert before go-live and quarterly thereafter.

### Control plane down

1. Acknowledge the alert and inspect rollout, pods, events, and recent changes.
2. Check MySQL readiness, ExternalSecret synchronization, and OIDC/egress dependencies.
3. If a new revision caused the outage, abort or undo the Argo Rollout and verify readiness.
4. Record timestamps, customer impact, mitigation, and follow-up actions in the incident log.

## Load and capacity test

Run from outside the cluster against the final domain:

```bash
k6 run -e BASE_URL=https://<domain> tests/load/releasepilot.js
```

The default test ramps to 75 virtual users and requires under 1% request failures, p95 below 1 second, and p99 below 2 seconds. Use a short-lived test account/session only when authenticated coverage is required; never put its cookie in shell history or CI logs. Record date, image digest, cluster size, peak RPS, latency, errors, and scaling behavior.

## Backup, recovery, and change control

- Use managed production MySQL with point-in-time recovery; the in-cluster StatefulSet is retained for demo compatibility, not recommended as the production data tier.
- Run a restore drill at least quarterly and record recovery time and recovery point evidence.
- Require reviewed pull requests, passing CI, immutable image digests, signed/attested images, and Argo CD reconciliation for every production change.
- Keep audit archives immutable and verify the hash chain after restoration.

## Rollback and exit criteria

Rollback when the error ratio exceeds 1%, p95 exceeds 1 second for 15 minutes, readiness is unstable, or a security control fails. A go-live is complete only when DNS/TLS, authentication, secrets synchronization, alert delivery, restore drill, and load-test evidence are all documented and accepted by the service owner.
