param(
    [string]$PlantUmlJar,
    [string]$PlantUmlServer = "https://www.plantuml.com/plantuml",
    [switch]$UseServer
)

$ErrorActionPreference = "Stop"
$DiagramDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $DiagramDir "..\..")

if (-not $PlantUmlJar) {
    $PlantUmlJar = Join-Path $RepoRoot "tools/plantuml/plantuml.jar"
}

$PumlFiles = Get-ChildItem -Path $DiagramDir -Filter "*.puml" | Sort-Object Name

function Encode-PlantUmlText {
    param([string]$Text)

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    $output = New-Object System.IO.MemoryStream
    $deflater = New-Object System.IO.Compression.DeflateStream(
        $output,
        [System.IO.Compression.CompressionLevel]::Optimal,
        $true
    )
    $deflater.Write($bytes, 0, $bytes.Length)
    $deflater.Close()

    $compressed = $output.ToArray()
    $alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"
    $result = New-Object System.Text.StringBuilder

    for ($i = 0; $i -lt $compressed.Length; $i += 3) {
        $b1 = [int]$compressed[$i]
        $b2 = if ($i + 1 -lt $compressed.Length) { [int]$compressed[$i + 1] } else { 0 }
        $b3 = if ($i + 2 -lt $compressed.Length) { [int]$compressed[$i + 2] } else { 0 }

        [void]$result.Append($alphabet[($b1 -shr 2) -band 0x3F])
        [void]$result.Append($alphabet[((($b1 -band 0x3) -shl 4) -bor ($b2 -shr 4)) -band 0x3F])
        [void]$result.Append($alphabet[((($b2 -band 0xF) -shl 2) -bor ($b3 -shr 6)) -band 0x3F])
        [void]$result.Append($alphabet[$b3 -band 0x3F])
    }

    $result.ToString()
}

if (-not $UseServer -and (Test-Path $PlantUmlJar)) {
    Write-Host "Generating diagrams with local PlantUML jar: $PlantUmlJar"
    & java -jar $PlantUmlJar -tpng $PumlFiles.FullName
    exit $LASTEXITCODE
}

if (-not $UseServer) {
    throw "Local PlantUML jar not found at $PlantUmlJar. Download plantuml.jar there, pass -PlantUmlJar, or explicitly use -UseServer."
}

foreach ($file in $PumlFiles) {
    $source = Get-Content -LiteralPath $file.FullName -Raw
    $encoded = Encode-PlantUmlText -Text $source
    $url = "$PlantUmlServer/png/$encoded"
    $output = [System.IO.Path]::ChangeExtension($file.FullName, ".png")

    Write-Host "Generating $([System.IO.Path]::GetFileName($output))"
    Invoke-WebRequest -Uri $url -OutFile $output
}
