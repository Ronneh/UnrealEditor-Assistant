[CmdletBinding()]
param(
    [string]$AppVersion = '5.0.0',
    [string]$ReleaseName = 'Mapping Assistant v5',
    [string]$OutputDirectory = 'target\windows-release',
    [string]$JpackagePath = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot\bin\jpackage.exe'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$target = Join-Path $root 'target'
$jarName = 'mapping-assistant-0.1.0.jar'
$jar = Join-Path $target $jarName
$inputDirectory = Join-Path $target 'jpackage-input'
$iconPath = Join-Path $target 'app-icon.ico'
$output = [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))

function New-AppIcon([string]$source, [string]$destination) {
    Add-Type -AssemblyName System.Drawing
    $sizes = @(16, 24, 32, 48, 64, 128, 256)
    $sourceImage = [Drawing.Image]::FromFile($source)
    $images = @()
    try {
        foreach ($size in $sizes) {
            $bitmap = [Drawing.Bitmap]::new($size, $size, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
            $graphics = [Drawing.Graphics]::FromImage($bitmap)
            $stream = [IO.MemoryStream]::new()
            try {
                $graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
                $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.DrawImage($sourceImage, 0, 0, $size, $size)
                $bitmap.Save($stream, [Drawing.Imaging.ImageFormat]::Png)
                $images += ,$stream.ToArray()
            } finally { $stream.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
        }
    } finally { $sourceImage.Dispose() }

    $writer = [IO.BinaryWriter]::new([IO.File]::Create($destination))
    try {
        $writer.Write([UInt16]0); $writer.Write([UInt16]1); $writer.Write([UInt16]$sizes.Count)
        $offset = 6 + 16 * $sizes.Count
        for ($i = 0; $i -lt $sizes.Count; $i++) {
            $dimension = if ($sizes[$i] -eq 256) { 0 } else { $sizes[$i] }
            $writer.Write([Byte]$dimension); $writer.Write([Byte]$dimension)
            $writer.Write([Byte]0); $writer.Write([Byte]0)
            $writer.Write([UInt16]1); $writer.Write([UInt16]32)
            $writer.Write([UInt32]$images[$i].Length); $writer.Write([UInt32]$offset)
            $offset += $images[$i].Length
        }
        foreach ($image in $images) { $writer.Write($image) }
    } finally { $writer.Dispose() }
}

if (-not (Test-Path -LiteralPath $JpackagePath)) { throw "jpackage not found: $JpackagePath" }
Push-Location $root
try {
    & mvn --batch-mode clean package
    if ($LASTEXITCODE) { throw "Maven failed: $LASTEXITCODE" }
    $forbidden = & jar tf $jar | Select-String '(^|/)TutorialEditorExtension\.class$|META-INF/services/EditorHelpAuthoringExtension'
    if ($forbidden) { throw "Normal JAR contains tutorial-editor artifacts: $forbidden" }
    New-AppIcon (Join-Path $root 'app-icon.png') $iconPath
    New-Item -ItemType Directory -Path $inputDirectory -Force | Out-Null
    Copy-Item -LiteralPath $jar -Destination (Join-Path $inputDirectory $jarName) -Force
    if (Test-Path -LiteralPath $output) { throw "Output exists: $output" }
    & $JpackagePath --type app-image --name $ReleaseName --dest $output --input $inputDirectory `
        --main-jar $jarName --main-class MappingAssistant --app-version $AppVersion --icon $iconPath
    if ($LASTEXITCODE) { throw "jpackage failed: $LASTEXITCODE" }
    $launcher = Join-Path (Join-Path $output $ReleaseName) "$ReleaseName.exe"
    Add-Type -AssemblyName System.Drawing
    $icon = [Drawing.Icon]::ExtractAssociatedIcon($launcher)
    if (-not $icon) { throw 'Packaged launcher has no icon.' }
    $icon.Dispose()
    Write-Output "Windows release built: $(Split-Path -Parent $launcher)"
    Write-Output 'Verified: tutorial editor excluded and launcher icon embedded.'
} finally { Pop-Location }
