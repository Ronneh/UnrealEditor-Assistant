# Unreal Editor 2 Assistant

Single-window desktop workspace for Unreal Editor 2 utilities. Its first included
tool analyzes and optimizes Unreal Tournament 99 T3D brush vertices.

## Included tools

- **Brush Optimizer** — grid-based correction, curved-brush alignment,
  planarity warnings, optimization log and clipboard output
- **Double** — previews and applies PlayerStart, FlagBase and Mover team
  property changes required after doubling a map
- **Image Resizer** — square PNG export at Unreal-friendly sizes with
  brightness, contrast, saturation, hue and sharpness controls
- **Screenshot Maker** — interactive crops from four level screenshots,
  rearrangeable 2×2 composition and draggable labels using installed fonts

The launcher and every tool use one dark-themed application window.

## Build and run

Requires JDK 17 or newer.

```powershell
mvn package
java -cp target/classes UnrealEditor2Assistant
```

## Author

VRN|Ron
