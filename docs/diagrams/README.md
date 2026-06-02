# Documentation Diagrams

This directory stores source PlantUML files and generated PNG images used by the Markdown documentation.

## Regenerate PNG files

Preferred local generation:

```pwsh
pwsh docs/diagrams/generate_diagrams.ps1
```

Local generation requires Java on `PATH`.

By default, the script looks for PlantUML at `tools/plantuml/plantuml.jar` relative to the repository root. Download `plantuml.jar` there, or pass a custom jar path with `-PlantUmlJar`.

Explicit public PlantUML Server generation:

Warning: this mode sends the full `.puml` diagram source to the configured PlantUML server, which is a third-party service when using the default public endpoint.

```pwsh
pwsh docs/diagrams/generate_diagrams.ps1 -UseServer
```

Keep both the `.puml` source and the generated `.png` image in version control so GitHub renders the documentation without external services.
