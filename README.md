# EasyBlockEditor

EasyBlockEditor is a building editor mod for Minecraft NeoForge 1.21.1, inspired by [Litematica](https://github.com/maruohon/litematica), developed with the [LDLib2](https://github.com/Low-Drag-MC/LDLib2) framework.

It serves as both a **building editor** and a **building management platform**. The core concept: provide an in-game full-screen 3D UI editor, allowing players to design, edit, manage, and share buildings in a non-destructive way.

## Features

- **3D UI Editor** — Full-screen in-game editor with FlexBox layout, powered by LDLib2
- **Non-destructive Editing** — Edit buildings virtually before applying to the real world
- **Multi-format Support** — Compatible with `.litematic`, `.schem` (Sponge), `.nbt` (Vanilla Structure), `.schematic` (Schematica), and native `.ebe` format
- **Layer System** — Organize buildings with layers (foundation, walls, roof, etc.)
- **Workgroup Collaboration** — Real-time collaboration with permission management
- **Projection System** — Preview buildings as holographic projections in the world
- **Printer Mode** — Auto or manual block placement consuming player inventory
- **Version History** — Full undo/redo with version snapshots
- **Material Statistics** — Real-time material cost calculation
- **Block Heatmap** — Visualize block type distribution with colors
- **NBT Editing** — Full block entity NBT editing and filtering
- **Theming** — Customizable UI themes via LSS stylesheets
- **i18n** — Supports English and Chinese (中文)

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- [LDLib2](https://github.com/Low-Drag-MC/LDLib2) (required)

## Installation

1. Download the latest release from [Releases](https://github.com/huangzilu/EasyBlockEditor/releases)
2. Place the `.jar` file in your Minecraft `mods/` directory
3. Launch the game

## Usage

- Use `/ebe` command or right-click the **Architect Toolbox** item to open the editor
- Place schematic files in `config/ebe/client/schematics/` for the editor to detect

## Building from Source

```bash
git clone https://github.com/huangzilu/EasyBlockEditor.git
cd EasyBlockEditor
./gradlew build
```

The built jar will be in `build/libs/`.

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

## Credits

- **Author:** L1ghT
- **Inspired by:** [Litematica](https://github.com/maruohon/litematica) by masa
- **Powered by:** [LDLib2](https://github.com/Low-Drag-MC/LDLib2) by Low-Drag-MC
