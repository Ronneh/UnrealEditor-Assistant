# Unreal Editor 2 Assistant

Unreal Editor 2 Assistant is a Windows desktop toolkit for creating and
maintaining Unreal Tournament 1999 maps. It brings frequently used mapping,
image, scripting, documentation, and planning tools together in
one easy-to-navigate application.

## Download

[Download the latest Windows release](https://github.com/Ronneh/UnrealEditor-Assistant/releases/latest)

Open the release page, download **Unreal Editor 2 Assistant v1.zip** from its
**Assets** section, **extract** the complete archive and run **Unreal Editor 2 Assistant.exe**.

## Features

### Map building and duplicating

- **Brush Generator** creates grid-aligned polygon cylinder brushes for common CSG
  shapes and exports them as T3D data.
- **Brush Optimizer** checks pasted T3D brushes for off-grid vertices,
  planarity problems, and alignment issues. Every proposed change can be
  reviewed before the corrected map data is copied.
- **Double** prepares duplicated team-based map content for the opposite
  team by updating FlagBases, PlayerStarts, Tags, and Events.

### Screenshots and Textures

- **Screenshot Maker** imports 4 map screenshots, lets you choose each
  square crop, arrange the four panels, add styled labels, and export a PNG file.
- **Image Resizer** opens images and turns them into square, Unreal-friendly
  PNG textures. Brightness, contrast, saturation, hue, and sharpness can be
  adjusted before export.
- **Seamless Texture** creates a mirrored, tileable texture from a source image.

### UnrealScript and Editor Guide

- **Scripting** provides an UnrealScript editor, reusable examples, basic
  checks, and UCC package compilation support.
- **Editor Guide** contains a big collection of Unreal Editor 2
  reference pages and community tutorials, with contents and search.

### Planning and daily information

- **To-Do List** organizes notes and tasks in folders, with rich-text editing
  and automatic local storage.
- **Weather** shows a seven-day forecast.

## Notes

- The application is intended for the game Unreal Tournament 1999 and Unreal Editor 2.
- Weather information requires an internet connection.

## Third-party services and acknowledgements

Unreal Editor 2 Assistant is made possible by several external services,
open-source projects, and community resources:

- **Open-Meteo** provides the geocoding and forecast data used by the Weather
  feature. Thank you for making accessible weather APIs available without
  requiring an API key.
- **Apache Lucene** powers the local full-text search in Editor Help.
- **jsoup** is used to import, clean, and present the historical HTML help
  pages.
- **Jackson** reads and writes the Editor Guide content-pack metadata.
- **Java Native Access (JNA)** enables Windows-specific desktop integration.
- **The Unreal and Unreal Tournament editing community** created the reference
  guides and tutorials preserved in Editor Help. Thanks to the original
  authors, archivists, and community sites that shared this knowledge.
- **Epic Games** created Unreal, Unreal Tournament, UnrealEd, and the technology
  this companion application is designed to support.

These services, libraries, games, trademarks, tutorials, and their associated
content remain the property of their respective owners. Unreal Editor 2
Assistant is an independent community tool and is not affiliated with or
endorsed by Epic Games or the third-party projects listed above.

## Author

Developed by VRN|Ron.