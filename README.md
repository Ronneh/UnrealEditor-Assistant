# Unreal Editor 2 Assistant

A Java desktop application that brings practical Unreal Editor 2 mapping and
image utilities into one workspace. It is designed for Unreal Tournament (1999)
maps and requires no installation.

## Tools

- **Brush Optimizer** — paste T3D map data to move brush vertices onto the grid,
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

## Author

VRN|Ron
