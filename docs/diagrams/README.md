# Documentation Diagrams

This directory stores source PlantUML files and generated PNG images used by the Markdown documentation.

## Regenerate PNG files

Preferred local generation:

```pwsh
pwsh docs/diagrams/generate_diagrams.ps1
```

By default, the script looks for PlantUML at `tools/plantuml/plantuml.jar` relative to the repository root. Download `plantuml.jar` there, or pass a custom jar path with `-PlantUmlJar`.

Fallback generation through PlantUML Server:

```pwsh
pwsh docs/diagrams/generate_diagrams.ps1 -UseServer
```

Keep both the `.puml` source and the generated `.png` image in version control so GitHub renders the documentation without external services.
