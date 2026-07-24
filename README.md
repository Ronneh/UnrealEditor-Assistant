# Unreal Editor 2 Assistant

A Java desktop application that brings practical Unreal Editor 2 mapping and
image utilities into one workspace. It is designed for Unreal Tournament (1999)
maps and requires no installation.

## Tools

- **Brush Optimizer**: paste T3D map data to move brush vertices onto the grid,
  align curved brushes, detect planarity issues, review every change, and copy
  the optimized result.
- **Double**: Analyze T3D code, preview the required changes for PlayerStarts,
  FlagBases, and Movers, then copy the updated map code.
- **Image Resizer**: Open, paste, or drag in an image; crop it to a square;
  adjust brightness, contrast, saturation, hue, and sharpness; then export a PNG
  to use it as a texture in your map or copy it to the clipboard.
- **Screenshot Maker**: Load or paste four screenshots of your map, choose each
  square crop, arrange their positions, add styled labels using your installed
  fonts, and export the finished PNG.

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
