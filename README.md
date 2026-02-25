# ⚡ A Commander

### (A)nother Dual Pane File Explorer

A modern take on Norton Commander for Windows — streamlined, opinionated, and built around the tools you already love.

[![Windows](https://img.shields.io/badge/platform-Windows-0078D4?logo=windows&logoColor=white)](https://github.com/Chaiavi/acommander)
[![Java](https://img.shields.io/badge/language-Java-ED8B00?logo=openjdk&logoColor=white)](https://github.com/Chaiavi/acommander)
[![License](https://img.shields.io/badge/license-BSL--1.0-blue)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Chaiavi/acommander?include_prereleases)](https://github.com/Chaiavi/acommander/releases)

---
# Out of the box, open ACommander
<img width="3826" height="2054" alt="image" src="https://github.com/user-attachments/assets/5880e5c6-c1f4-4450-b169-b8bf8079a350" />

# A Commander workstation with multiple associated apps running (View Text File, View Image, Edit and Terminal)
<img width="3840" height="2060" alt="image" src="https://github.com/user-attachments/assets/345ee06a-8edc-4302-9b1c-2f76124177d3" />

---

## 🎯 Philosophy

A Commander is **not** a pure Norton Commander clone. It keeps what works, drops what doesn't, and delegates specialized tasks to best-in-class external tools.

> **Core principle:** Files are the focus. Everything else gets out of the way.

### Design Decisions

- ✅ Classic NC keyboard shortcuts preserved (`F1`–`F12`)
- ✅ **Command Palette** — modern discoverability with fuzzy search
- ✅ **Fully configurable actions** — customize tools, shortcuts, and arguments via JSON
- ✅ External tools handle what they do best (see below)
- ❌ No NCD — removed features that didn't add value
- ❌ Minimal configuration — opinionated defaults over endless settings

---

## ✨ Features

### Command Palette

Press `Ctrl+Shift+P` to open the Command Palette — a modern, searchable interface for all available actions. Type to filter by command name or alias, then press Enter to execute. No more memorizing obscure shortcuts.

### Configurable Actions

Every action in A Commander is defined in `config/apps.json`. Want to swap Notepad4 for VS Code? Change the compression tool? Add your own custom actions? Just edit the JSON — no recompilation needed.

---

## 🔧 External Tool Integration

A Commander delegates specialized operations to dedicated tools, giving you professional-grade functionality without reinventing the wheel:

| Key | Action | Tool |
|-----|--------|------|
| `F3` | View | [Universal Viewer](https://www.uvviewsoft.com/) |
| `F4` | Edit | [Notepad4](https://github.com/zufuliu/notepad4) |
| `F5` | Copy | [FastCopy](https://fastcopy.jp/) |
| `F6` | Move | [FastCopy](https://fastcopy.jp/) |
| `F9` | Terminal | PowerShell / Windows CMD |
| `Alt+F9` | Open Explorer | Windows Explorer |
| `F10` | Search Files | Built-in search |
| `F11` | Pack | [7-Zip](https://www.7-zip.org/) |
| `F12` | Unpack | [7-Zip](https://www.7-zip.org/) |
| `Alt+F12` | Extract All | [Universal Extractor](https://github.com/Bioruebe/UniExtract2) |
| `Shift+F1` | Merge PDFs | [PDFtk](https://www.pdflabs.com/tools/pdftk-the-pdf-toolkit/) |
| `Shift+F2` | Extract PDF Pages | [PDFtk](https://www.pdflabs.com/tools/pdftk-the-pdf-toolkit/) |

---

## ⌨️ Keyboard Shortcuts

### File Operations

| Key | Action |
|-----|--------|
| `F2` / `Shift+F6` | Rename |
| `F3` | View |
| `F4` | Edit |
| `F5` | Copy |
| `F6` | Move |
| `F7` | Create Directory |
| `Alt+F7` | Create File |
| `F8` / `Delete` | Delete |
| `Shift+F8` / `Shift+Delete` | Secure Delete & Wipe |

### Navigation & Tools

| Key | Action |
|-----|--------|
| `F1` | Help |
| `F9` | Open Terminal |
| `Alt+F9` | Open Explorer Here |
| `F10` / `Ctrl+F` | Search Files |
| `Ctrl+R` | Refresh Panels |
| `Alt+F1` | Left Path Dropdown |
| `Alt+F2` | Right Path Dropdown |
| `Ctrl+Shift+P` | Command Palette |

### Archive Operations

| Key | Action |
|-----|--------|
| `F11` | Pack to Zip |
| `F12` | Unpack |
| `Alt+F12` | Extract All (Universal Extractor) |

### PDF Operations

| Key | Action |
|-----|--------|
| `Shift+F1` | Merge PDF Files |
| `Shift+F2` | Extract PDF Pages |

---

## 💡 Smart Behaviors

A Commander is opinionated — it makes decisions so you don't have to:

| Scenario | What Happens |
|----------|--------------|
| 🔒 File can't be deleted | Automatically sent to [ThisIsMyFile](https://www.yourownnet.net/en/thisismyfile/) for unlocking + deletion |
| 📝 Batch rename files | Handed off to [Ant Renamer](https://www.antp.be/software/renamer) |
| 🗑️ Secure delete needed | Uses [SDelete](https://learn.microsoft.com/en-us/sysinternals/downloads/sdelete) for military-grade wiping |
| 👁️ Hidden files | Always visible — no toggle needed |

---

## 🔌 Adding Custom Tools

A Commander's power lies in its configurability. All actions are defined in `config/apps.json`.

### Action Schema

```json
{
  "id": "myTool",
  "label": "My Custom Tool",
  "shortcut": "Ctrl+Alt+M",
  "aliases": ["custom", "mytool"],
  "contexts": ["filePane", "commandPalette"],
  "selection": "single",
  "type": "builtin",
  "path": "apps/mytool/tool.exe",
  "args": ["--input", "${selectedFile}"],
  "refreshAfter": true
}
```

### Field Reference

| Field | Required | Description |
|-------|----------|-------------|
| `id` | ✅ | Unique identifier for the action |
| `label` | ✅ | Display name shown in UI and Command Palette |
| `shortcut` | ❌ | Keyboard shortcut (e.g., `F5`, `Ctrl+Alt+M`, `Shift+F8`) |
| `aliases` | ❌ | Alternative names for Command Palette search |
| `contexts` | ❌ | Where action is available: `global`, `filePane`, `commandPalette` |
| `selection` | ❌ | Required selection: `none`, `single`, `multi`, `any`, `singleFile` |
| `type` | ✅ | `builtin` (internal handling) or `external` (launch external app) |
| `path` | ❌ | Path to executable (relative to A Commander root) |
| `args` | ❌ | Command-line arguments array |
| `builtin` | ❌ | Reference another action's handler |
| `refreshAfter` | ❌ | Refresh file panels after execution (`true`/`false`) |

### Available Variables

Use these placeholders in the `args` array:

| Variable | Description |
|----------|-------------|
| `${selectedFile}` | Full path to the currently selected file |
| `${selectedFileQuoted}` | Selected file path wrapped in quotes |
| `${selectedFilesJoined}` | All selected files as a quoted, comma-separated list |
| `${targetFolder}` | Path of the opposite pane (destination) |
| `${targetFolderQuoted}` | Target folder path wrapped in quotes |
| `${focusedPathQuoted}` | Focused pane path wrapped in quotes |
| `${archiveFile}` | Output archive path (for pack operations) |
| `${destinationPath}` | Extraction destination folder |
| `${destinationPathQuoted}` | Extraction destination path wrapped in quotes |
| `${outputPdf}` | Output PDF path (for merge operations) |
| `${outputPattern}` | Output filename pattern (for split operations) |

### Example: Adding a Custom Image Viewer

```json
{
  "id": "imageView",
  "label": "View Image",
  "shortcut": "Ctrl+I",
  "aliases": ["picture", "photo", "img"],
  "contexts": ["filePane", "commandPalette"],
  "selection": "single",
  "type": "builtin",
  "path": "apps/imageviewer/viewer.exe",
  "args": ["${selectedFile}"]
}
```

### Example: Adding a Git Status Tool

```json
{
  "id": "gitStatus",
  "label": "Git Status",
  "shortcut": "Ctrl+G",
  "aliases": ["git", "status", "vcs"],
  "contexts": ["global", "commandPalette"],
  "selection": "none",
  "type": "external",
  "path": "C:/Program Files/Git/bin/git.exe",
  "args": ["status"]
}
```

### Tips

1. **Portable apps**: Place tools in the `apps/` folder for a self-contained installation
2. **Context matters**: Use `global` for actions that don't need file selection, `filePane` for file-specific operations
3. **Aliases help**: Add common synonyms so users can find actions in Command Palette
4. **Refresh when needed**: Set `refreshAfter: true` for any action that modifies files

---

## 🏗️ Development Guidelines

| Guideline | Rationale |
|-----------|-----------|
| Keep the code **super simple** | Maintainability over cleverness |
| Don't worry about storage | This isn't Volkov Commander 😄 |
| **Windows only** | No cross-platform compromises |
| **Files first** | Every feature serves file management |

---

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Windows 10/11

### Installation

```bash
git clone https://github.com/Chaiavi/acommander.git
cd acommander
```

### Running

```bash
./gradlew run
```

### Building

```bash
./gradlew build
```

The built application will be in `build/`.

---

## 📁 Project Structure

```
acommander/
├── apps/                    # Bundled external tools
│   ├── copy/               # FastCopy
│   ├── delete/             # ThisIsMyFile, SDelete
│   ├── edit/               # Notepad4
│   ├── extract_all/        # Universal Extractor
│   ├── multi_rename/       # Ant Renamer
│   ├── pack_unpack/        # 7-Zip
│   ├── pdf/                # PDFtk
│   └── view/               # Universal Viewer
├── config/
│   └── apps.json           # Action configuration
├── src/main/               # Java source code
├── build.gradle            # Gradle build configuration
└── README.md
```

---

## 📜 License

This project is licensed under the [Boost Software License 1.0](LICENSE).

---

## 🙏 Acknowledgments

A Commander stands on the shoulders of giants:

- [Norton Commander](https://en.wikipedia.org/wiki/Norton_Commander) — the original inspiration
- [FastCopy](https://fastcopy.jp/) — blazing fast file operations
- [7-Zip](https://www.7-zip.org/) — universal archive handling
- [Universal Viewer](https://www.uvviewsoft.com/) — view anything
- [Notepad4](https://github.com/zufuliu/notepad4) — lightweight editing
- [PDFtk](https://www.pdflabs.com/tools/pdftk-the-pdf-toolkit/) — PDF manipulation
- [Ant Renamer](https://www.antp.be/software/renamer) — batch renaming
- [ThisIsMyFile](https://www.yourownnet.net/en/thisismyfile/) — unlock stubborn files
- [SDelete](https://learn.microsoft.com/en-us/sysinternals/downloads/sdelete) — secure deletion
- [Universal Extractor](https://github.com/Bioruebe/UniExtract2) — extract anything

---

**[⭐ Star this repo](https://github.com/Chaiavi/acommander)** if you find it useful!

