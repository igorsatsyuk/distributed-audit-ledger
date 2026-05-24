const path = require("node:path");
const {
  parseYamlDocuments,
  loadManifestDocuments,
  indexByKindAndName,
  getResource,
  getResourcesByKind,
} = require("./manifest-utils");

const manifestPath = path.resolve(__dirname, "..", "manifests", "platform.yaml");

describe("platform manifests", () => {
  test("parseYamlDocuments ignores empty docs and parses valid docs", () => {
    const docs = parseYamlDocuments("---\nkind: ConfigMap\nmetadata:\n  name: a\n---\n");
    expect(docs).toHaveLength(1);
    expect(docs[0].kind).toBe("ConfigMap");
  });

  test("loadManifestDocuments loads all DAL resources", () => {
    const docs = loadManifestDocuments(manifestPath);
    expect(docs.length).toBeGreaterThan(10);

    const namespace = getResource(docs, "Namespace", "dal", "");
    expect(namespace).not.toBeNull();
  });

  test("indexByKindAndName rejects duplicates", () => {
    const duplicated = [
      { kind: "Service", metadata: { name: "same", namespace: "dal" } },
      { kind: "Service", metadata: { name: "same", namespace: "dal" } },
    ];

    expect(() => indexByKindAndName(duplicated)).toThrow("Duplicate resource key");
  });

  test("required backend and frontend deployments exist", () => {
    const docs = loadManifestDocuments(manifestPath);
    const deployments = getResourcesByKind(docs, "Deployment");
    const names = deployments.map((d) => d.metadata.name);

    expect(names).toEqual(
      expect.arrayContaining([
        "command-service",
        "event-store-service",
        "audit-writer-service",
        "query-service",
        "audit-ui",
      ]),
    );
  });

  test("kafka and postgres statefulsets have persistent storage", () => {
    const docs = loadManifestDocuments(manifestPath);
    const postgres = getResource(docs, "StatefulSet", "dal-postgres");
    const kafka = getResource(docs, "StatefulSet", "dal-kafka");

    expect(postgres).not.toBeNull();
    expect(kafka).not.toBeNull();
    expect(postgres.spec.volumeClaimTemplates.length).toBeGreaterThan(0);
    expect(kafka.spec.volumeClaimTemplates.length).toBeGreaterThan(0);
  });

  test("ingress routes api traffic to command/query services", () => {
    const docs = loadManifestDocuments(manifestPath);
    const ingress = getResource(docs, "Ingress", "dal-ingress");

    expect(ingress).not.toBeNull();
    const paths = ingress.spec.rules[0].http.paths;
    const backendServiceNames = paths.map((p) => p.backend.service.name);

    expect(backendServiceNames).toEqual(
      expect.arrayContaining(["audit-ui", "command-service", "query-service"]),
    );
  });
});

