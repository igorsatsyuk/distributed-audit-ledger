# Documentation Diagrams

This directory stores source PlantUML files and generated PNG images used by the Markdown documentation.

## Regenerate PNG files

Preferred local generation:

```pwsh
.\docs\diagrams\generate_diagrams.ps1 -PlantUmlJar .\tools\plantuml\plantuml.jar
```

Fallback generation through PlantUML Server:

```pwsh
.\docs\diagrams\generate_diagrams.ps1 -UseServer
```

Keep both the `.puml` source and the generated `.png` image in version control so GitHub renders the documentation without external services.
