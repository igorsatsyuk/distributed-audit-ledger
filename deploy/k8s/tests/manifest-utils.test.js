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

  test("platform manifest has no duplicate kind/namespace/name resources", () => {
    const docs = loadManifestDocuments(manifestPath);
    expect(() => indexByKindAndName(docs)).not.toThrow();
  });

  test("indexByKindAndName rejects duplicates", () => {
    const duplicated = [
      { apiVersion: "v1", kind: "Service", metadata: { name: "same", namespace: "dal" } },
      { apiVersion: "v1", kind: "Service", metadata: { name: "same", namespace: "dal" } },
    ];

    expect(() => indexByKindAndName(duplicated)).toThrow("Duplicate resource key");
  });

  test("indexByKindAndName allows same kind/name across different apiVersion", () => {
    const differentApiVersions = [
      { apiVersion: "group1/v1", kind: "Widget", metadata: { name: "same", namespace: "dal" } },
      { apiVersion: "group2/v1", kind: "Widget", metadata: { name: "same", namespace: "dal" } },
    ];

    expect(() => indexByKindAndName(differentApiVersions)).not.toThrow();
  });

  test("indexByKindAndName fails with descriptive error on missing metadata.name", () => {
    const invalid = [{ kind: "Service", metadata: { namespace: "dal" } }];
    expect(() => indexByKindAndName(invalid)).toThrow("missing metadata.name");
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

  test("statefulset governing services are headless", () => {
    const docs = loadManifestDocuments(manifestPath);
    const headlessServiceNames = ["dal-postgres-headless", "dal-zookeeper-headless", "dal-kafka-headless"];
    for (const name of headlessServiceNames) {
      const svc = getResource(docs, "Service", name);
      expect(svc).not.toBeNull();
      expect(svc.spec.clusterIP).toBe("None");
    }
  });

  test("statefulsets use their governing headless service names", () => {
    const docs = loadManifestDocuments(manifestPath);
    const expectations = [
      ["dal-postgres", "dal-postgres-headless"],
      ["dal-zookeeper", "dal-zookeeper-headless"],
      ["dal-kafka", "dal-kafka-headless"],
    ];

    for (const [statefulSetName, serviceName] of expectations) {
      const statefulSet = getResource(docs, "StatefulSet", statefulSetName);
      expect(statefulSet).not.toBeNull();
      expect(statefulSet.spec.serviceName).toBe(serviceName);
    }
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

    const routePaths = ingress.spec.rules[0].http.paths.map((p) => p.path);
    expect(routePaths).toEqual(expect.arrayContaining(["/auth"]));
  });
});

