<div align="center">

# ⚡ A Commander

**A dual-pane file explorer for Windows, inspired by Norton Commander and built with JavaFX.**

Fast. Keyboard-driven. Endlessly configurable.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](#)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010%2F11-0078D6?logo=windows&logoColor=white)](#)
[![Build](https://img.shields.io/badge/Build-Gradle-02303A?logo=gradle&logoColor=white)](#)
[![License](https://img.shields.io/badge/License-BSL%201.0-blue)](#license)

</div>

---

A Commander keeps file operations fast and keyboard-driven, and offloads specialized tasks — view, edit, copy, archive, convert, checksum — to proven external tools configured in `config/apps.json`.

---

## 📑 Table of Contents

- [Core Features](#-core-features)
- [File Operations](#-file-operations)
- [Search & Navigation](#-search--navigation)
- [Archive, PDF, Convert & Checksum](#-archive-pdf-convert--checksum)
- [Default Shortcuts](#-default-shortcuts)
- [External Tools Bundled](#-external-tools-bundled)
- [Configuration](#-configuration-configappsjson)
- [Build & Run](#-build--run)
- [Project Layout](#-project-layout)
- [License](#-license)

---

## ✨ Core Features

- **Dual-pane navigation** with keyboard-first workflow
- **Command Palette** (`Ctrl+Shift+P`) with fuzzy search and aliases
- **Data-driven action system** via `config/apps.json` — no recompilation needed for tool changes
- Built-in and external actions with selection/context rules
- External task **progress bar** with stop button
- **Persistent state** — left/right paths, theme mode, and bookmarks in `config/acommander.properties`
- Sort by Name / Size / Modified (header click or palette actions)
- Incremental **in-pane filtering** by typing letters/digits

---

## 📂 File Operations

| Operation | Details |
| :--- | :--- |
| **Rename** | Single or batch via Ant Renamer |
| **Copy / Move** | Between panes |
| **Create** | New directory or new file |
| **Delete** | With fallback unlock-delete for locked files |
| **Secure Wipe** | Via SDelete |
| **Attributes** | Change file/folder attributes |

---

## 🔍 Search & Navigation

| Feature | Shortcut |
| :--- | :--- |
| File search (wildcard-aware) | `F10` |
| Find-in-files text search (ripgrep) | `Alt+F10` |
| Path dropdowns | `Alt+F1` / `Alt+F2` |
| Open terminal here | `F9` |
| Open Explorer here | `Alt+F9` |
| Bookmark / Go to / Remove bookmark | via Command Palette |
| Sync other pane to current path | via Command Palette |

---

## 📦 Archive, PDF, Convert & Checksum

| Category | Actions |
| :--- | :--- |
| **Archive** | Pack to zip (`F11`) via 7-Zip GUI · Unpack (`F12`) via 7-Zip GUI · Extract anything (`Alt+F12`) via Universal Extractor · Split large file (`Alt+F11`) via 7z CLI |
| **PDF** | Merge PDF files · Extract PDF pages |
| **Convert** | Media conversion (`Alt+F5`) auto-routes to image or audio · Graphics via `caesiumclt.exe` · Audio via `sndfile-convert.exe` |
| **Checksum** | Single file or recursive folder checksum via `rhash.exe` |

---

## ⌨️ Default Shortcuts

| Key | Action | | Key | Action |
| :--- | :--- | :---: | :--- | :--- |
| `F1` | Help | | `F7` | Create Directory |
| `F2` / `Shift+F6` | Rename | | `Alt+F7` | Create File |
| `F3` | View | | `F8` / `Delete` | Delete |
| `F4` | Edit | | `Shift+F8` / `Shift+Del` | Delete & Wipe |
| `F5` | Copy | | `F9` | Open Terminal Here |
| `Alt+F5` | Convert Media | | `Alt+F9` | Open Explorer Here |
| `F6` | Move | | `F10` / `Ctrl+F` | Search for Files |
| `Alt+F10` | Find in Files | | `Ctrl+R` | Refresh Panels |
| `F11` | Pack to Zip | | `Ctrl+Shift+P` | Command Palette |
| `Alt+F11` | Split Large File | | `Alt+F1` / `Alt+F2` | Path Dropdown |
| `F12` | Unpack | | `Alt+Enter` | Change Attributes |
| `Alt+F12` | Extract Anything | | | |

> **Quick tips:** `Tab` switches active pane · `Enter` opens folder/file · `Backspace` goes to parent · `F3` on a folder calculates its size.

---

## 🧰 External Tools Bundled

| Tool | Location |
| :--- | :--- |
| Universal Viewer | `apps/view/UniversalViewer` |
| Notepad4 & TedNPad | `apps/edit` |
| FastCopy | `apps/copy` |
| 7-Zip GUI | `apps/pack_unpack/7zG.exe` |
| Universal Extractor | `apps/extract_all/UniExtract` |
| PDFtk | `apps/pdf/pdftk.exe` |
| Ant Renamer | `apps/multi_rename` |
| ThisIsMyFile & SDelete | `apps/delete` |
| ripgrep | `apps/search_in_files/rg.exe` |
| Caesium CLI | `apps/image_convert/caesiumclt.exe` |
| sndfile-convert | `apps/sound_convert/sndfile-convert.exe` |
| rhash | `apps/checksum/rhash.exe` |

---

## ⚙️ Configuration (`config/apps.json`)

Every action is **data-driven**. Add, remove, or reconfigure tools without touching the source code.

### Action Schema

```json
{
  "id": "openVSCode",
  "label": "Open in VS Code",
  "shortcut": "Ctrl+Alt+V",
  "aliases": ["code", "vscode"],
  "contexts": ["filePane", "commandPalette"],
  "selection": "single",
  "type": "external",
  "path": "C:/Program Files/Microsoft VS Code/Code.exe",
  "args": ["${selectedFile}"],
  "refreshAfter": false,
  "prompt": {
    "title": "Optional argument",
    "label": "Value",
    "defaultValue": "${selectedName}"
  }
}
```

### Fields

| Field | Required | Notes |
| :--- | :---: | :--- |
| `id` | ✅ | Unique action id |
| `label` | ✅ | Display name |
| `shortcut` | — | Keyboard shortcut string |
| `aliases` | — | Palette search aliases |
| `contexts` | — | `global` · `filePane` · `commandPalette` |
| `selection` | — | `none` · `single` · `multi` · `any` · `singleFile` |
| `type` | — | `builtin` (default) or `external` |
| `builtin` | — | Override builtin handler id |
| `path` | External | Executable path |
| `args` | — | Argument array |
| `refreshAfter` | — | Refresh panes after execution |
| `prompt` | — | Prompt config for external actions |

### Placeholders in `args`

| Placeholder | Meaning |
| :--- | :--- |
| `${selectedFile}` | First selected file path |
| `${selectedFileQuoted}` | First selected file path (quoted) |
| `${selectedFiles}` | All selected file paths as separate args |
| `${selectedFilesJoined}` | All selected file paths as quoted, comma-joined string |
| `${selectedName}` | Selected item name |
| `${focusedPath}` / `${focusedPathQuoted}` | Focused pane path |
| `${targetFolder}` / `${targetFolderQuoted}` | Opposite pane path |
| `${promptValue}` | Value entered from the `prompt` dialog |

> `ToolCommandBuilder` also creates quoted aliases for extra placeholders (e.g. `${archiveFileQuoted}` when `${archiveFile}` is provided by builtin flows).

---

## 🚀 Build & Run

```powershell
# Run the application
.\gradlew.bat run

# Run tests
.\gradlew.bat test

# Build fat JAR
.\gradlew.bat shadowJar

# Build Windows distribution (EXE + runtime + apps/config + zip)
.\gradlew.bat dist
```

**Output locations:**

| Artifact | Path |
| :--- | :--- |
| JAR + resources | `build/libs/` |
| EXE | `build/launch4j/` |
| Distribution | `dist/` |

---

## 🗂️ Project Layout

```text
acommander/
├── apps/                    Bundled external tools
├── config/                  apps.json, user properties, F1 help markdown
├── src/
│   ├── main/
│   │   ├── java/            Application source
│   │   └── resources/       FXML, styles, icons, logging config
│   └── test/
│       └── java/            Unit tests
├── build.gradle             Build, packaging, launch4j, dist tasks
└── LICENSE
```

---

## 📄 License

Released under the **Boost Software License 1.0** — see [`LICENSE`](LICENSE) for details.