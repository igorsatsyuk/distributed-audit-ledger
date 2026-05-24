# Kubernetes Deployment (Issue #19)

This directory contains:

- `manifests/platform.yaml` - plain Kubernetes manifests for a quick apply
- `helm/` - Helm chart for parametrized installs
- `tests/` - manifest validation tests with coverage

## Quick apply (raw manifests)

```bash
kubectl apply -f deploy/k8s/manifests/platform.yaml
```

## Helm install

The chart requires secret values. Create an override file first:

```yaml
# deploy/k8s/helm/values.override.yaml
secrets:
  dbUsername: postgres
  dbPassword: postgres
  jwtSecret: your-jwt-secret
  authAdminUsername: admin
  authAdminPassword: change-me
  authAuditorUsername: auditor
  authAuditorPassword: change-me
  authUserUsername: user
  authUserPassword: change-me
  auditLedgerContractAddress: "0x..."
  auditLedgerContractDeploymentBlock: "12345"
  ganachePrivateKey: "0x..."
```

```bash
helm upgrade --install dal deploy/k8s/helm -n dal --create-namespace -f deploy/k8s/helm/values.override.yaml
```

For production, prefer an existing Kubernetes Secret (so sensitive values are not passed via Helm values):

```bash
helm upgrade --install dal deploy/k8s/helm -n dal --create-namespace \
  --set secrets.create=false \
  --set secrets.existingSecretName=dal-runtime-secrets
```

The existing Secret must contain keys used by services (`DB_USERNAME`, `DB_PASSWORD`, `AUTH_JWT_SECRET`, auth user/password keys, blockchain keys).

If blockchain is not ready yet, disable audit writer:

```bash
helm upgrade --install dal deploy/k8s/helm -n dal --create-namespace -f deploy/k8s/helm/values.override.yaml --set components.auditWriter.enabled=false
```

Important: disabling `audit-writer-service` does **not** disable query-service blockchain integrity features.
`/api/audit-logs/{id}/integrity-check` and reconciliation endpoints still require
`auditLedgerContractAddress` and `auditLedgerContractDeploymentBlock` to be set.

## Run manifest tests

```bash
cd deploy/k8s/tests
npm ci
npm test
```

## CI validation

Kubernetes manifest tests are executed in GitHub Actions (`.github/workflows/ci.yml`) in job `k8s-manifests`.

## Notes

- Image names are placeholders and should be replaced with real registry tags.
- Secrets are defined via `stringData` in manifests for local bootstrap convenience.
  For production, use external secret management (Sealed Secrets, Vault, etc.).
- Helm resource names use the pattern `<release>-<chart>-<component>` and are truncated to 63 chars when needed.
- Raw manifest placeholders (`__SET_*__`) must be replaced before running traffic.
- For non-local RPC endpoints, set `AUDIT_LEDGER_CONTRACT_DEPLOYMENT_BLOCK` explicitly (do not keep it at `0`).
- `GANACHE_RPC_URL` points to an external endpoint by default; change it for your target cluster.

