const fs = require("node:fs");
const path = require("node:path");
const yaml = require("js-yaml");

function parseYamlDocuments(rawContent) {
  return yaml
    .loadAll(rawContent)
    .filter((doc) => doc && typeof doc === "object" && doc.kind);
}

function loadManifestDocuments(manifestPath) {
  const absolutePath = path.resolve(manifestPath);
  const content = fs.readFileSync(absolutePath, "utf8");
  return parseYamlDocuments(content);
}

function indexByKindAndName(documents) {
  const index = new Map();

  for (const document of documents) {
    if (!document?.kind) {
      throw new Error("Invalid resource: missing kind");
    }
    if (!document?.metadata?.name) {
      throw new Error(`Invalid resource ${document.kind}: missing metadata.name`);
    }

    const namespace = document?.metadata?.namespace || "";
    const key = `${document.kind}/${namespace}/${document.metadata.name}`;

    if (index.has(key)) {
      throw new Error(`Duplicate resource key: ${key}`);
    }

    index.set(key, document);
  }

  return index;
}

function getResource(documents, kind, name, namespace = "dal") {
  return (
    documents.find(
      (doc) =>
        doc.kind === kind &&
        doc?.metadata?.name === name &&
        (doc?.metadata?.namespace || "") === namespace,
    ) || null
  );
}

function getResourcesByKind(documents, kind) {
  return documents.filter((doc) => doc.kind === kind);
}

module.exports = {
  parseYamlDocuments,
  loadManifestDocuments,
  indexByKindAndName,
  getResource,
  getResourcesByKind,
};

