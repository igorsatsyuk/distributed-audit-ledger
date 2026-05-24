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
  ganachePrivateKey: "0x..."
```

```bash
helm upgrade --install dal deploy/k8s/helm -n dal --create-namespace -f deploy/k8s/helm/values.override.yaml
```

If blockchain is not ready yet, disable audit writer and omit blockchain secrets:

```bash
helm upgrade --install dal deploy/k8s/helm -n dal --create-namespace -f deploy/k8s/helm/values.override.yaml --set components.auditWriter.enabled=false
```

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
- Raw manifest placeholders (`__SET_*__`) must be replaced before running traffic.
- `GANACHE_RPC_URL` points to an external endpoint by default; change it for your target cluster.

