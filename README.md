# Unreal Editor 2 Assistant

A Java desktop application that brings practical Unreal Editor 2 mapping and
image utilities into one workspace.
It is designed for Unreal Tournament 1999 maps.

## Tools

- **Brush Optimizer**: paste T3D map data to move brush vertices onto the grid,
  align curved brushes, detect planarity issues, review every change, and copy
  the optimized result.
- **Double** — analyze pasted T3D map data, preview the required team-property
  changes for PlayerStarts, FlagBases, and Movers, then copy the updated map
  data.
- **Image Resizer** — open, paste, or drag in an image; crop it to a square;
  adjust brightness, contrast, saturation, hue, and sharpness; then export an
  Unreal-friendly PNG or copy it to the clipboard.
- **Screenshot Maker** — load or paste four level screenshots, choose each
  square crop, arrange the 2×2 composition, add styled labels using installed
  fonts, and export the finished screenshot.
- **Scripting** — learn and write UnrealScript with BunnyTrack-oriented trigger,
  launch-pad, timer, event and mutator examples, then compile packages with UCC.

## Requirements

- JDK 17 or newer
- Apache Maven

## Build and run

Open PowerShell in the project directory and run:

```powershell
mvn package
java -cp target/classes UnrealEditor2Assistant
```

The first command builds the project. The second starts the application. Future
builds use the same commands.

## Editor Help

The importer converts an extracted CHM tree into a standalone content pack. It
does not embed help pages or assets in the application JAR. The application
provides an English-only Editor Help workspace with a contents tree, weighted
Lucene search, local tutorial rendering and Back/Forward/Home navigation.

```powershell
mvn exec:java -Dexec.mainClass=EditorHelpImporter `
  '-Dexec.args=C:\Users\ron_3\Desktop\UnrealHelp help-content-pack'
```

The generated English-only pack contains `manifest.json`, `catalog.json`,
cleaned pages, referenced local assets and `import-report.json`. Category paths
and normalized text remain separate fields for the local Lucene index and a
future source-grounded assistant.

Validate the pack and regenerate its local search index with:

```powershell
mvn exec:java -Dexec.mainClass=EditorHelpIndexTool `
  '-Dexec.args=help-content-pack help-content-pack/search-index'
```

Editor Help content, metadata, search analysis, and interface text use English
only. For a packaged application, place the pack in `help-content` next to the
application executable.

## Author

VRN|Ron
