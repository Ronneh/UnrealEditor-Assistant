# Unreal Editor 2 Assistant

Unreal Editor 2 Assistant is a Windows desktop toolkit for creating and
maintaining Unreal Tournament 1999 maps. It brings frequently used mapping,
image, scripting, documentation, and planning tools together in one dark,
easy-to-navigate application.

## Download

[Download Unreal Editor 2 Assistant v1 for Windows (.zip)](./Unreal%20Editor%202%20Assistant%20v1.zip)

Extract the complete ZIP archive and start **Unreal Editor 2 Assistant**. Keep
the extracted folders together so the application can find its runtime and
offline Editor Help files.

## Features

### Map building and maintenance

- **Brush Generator** creates grid-aligned polygon brushes for common CSG
  shapes and exports them as T3D data.
- **Brush Optimizer** checks pasted T3D brushes for off-grid vertices,
  planarity problems, and alignment issues. Every proposed change can be
  reviewed before the corrected map data is copied.
- **Double** prepares duplicated CTF or team-based map content for the opposite
  team by updating PlayerStarts, FlagBases, Movers, tags, and events.

### Screenshots and textures

- **Screenshot Maker** imports four map screenshots, lets you choose each
  square crop, arrange the four panels, add styled labels, and export a PNG up
  to 2048 × 2048 pixels.
- **Image Resizer** opens, pastes, or accepts dropped images and turns them into
  square, Unreal-friendly PNG textures. Brightness, contrast, saturation, hue,
  and sharpness can be adjusted before export.
- **Seamless Texture** creates a mirrored, tileable texture from a source image
  and supports opening, pasting, and drag-and-drop.

### UnrealScript and reference material

- **Scripting** provides an UnrealScript editor, reusable examples, basic
  checks, and UCC package compilation support.
- **Editor Guide** contains a searchable offline collection of Unreal Editor 2
  reference pages and community tutorials, with contents, search results, and
  Back/Forward/Home navigation.

### Planning and daily information

- **To-Do List** organizes map notes and tasks in folders, with rich-text
  editing and automatic local storage.
- **Weather** shows a seven-day forecast, hourly temperature graph, humidity,
  wind, weather conditions, and Celsius/Fahrenheit switching for a chosen
  city. Weather and date labels use the same supported language; English is
  used when the system language cannot be identified.

## Notes

- The application is intended for Unreal Tournament 1999 and Unreal Editor 2.
- Editor Help works offline after the included content pack has been installed.
- Weather information requires an internet connection.
- This repository is published as an application project and is not currently
  seeking external contributions.

## Third-party services and acknowledgements

Unreal Editor 2 Assistant is made possible by several external services,
open-source projects, and community resources:

- **Open-Meteo** provides the geocoding and forecast data used by the Weather
  feature. Thank you for making accessible weather APIs available without
  requiring an API key.
- **Apache Lucene** powers the local full-text search in Editor Help.
- **jsoup** is used to import, clean, and present the historical HTML help
  pages.
- **Jackson** reads and writes the Editor Help content-pack metadata.
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

VRN|Ron
