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

```bash
helm upgrade --install dal deploy/k8s/helm -n dal --create-namespace
```

## Run manifest tests

```bash
cd deploy/k8s/tests
npm install
npm test
```

## Notes

- Image names are placeholders and should be replaced with real registry tags.
- Secrets are defined via `stringData` in manifests for local bootstrap convenience.
  For production, use external secret management (Sealed Secrets, Vault, etc.).
- `GANACHE_RPC_URL` points to an external endpoint by default; change it for your target cluster.

