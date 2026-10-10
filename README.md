# ⚡ A Commander

**A Norton Commander-inspired, Total Commander-style dual-pane file manager for Windows, with a VS Code-style
command palette. Free and open source.**

[![Latest Release](https://img.shields.io/github/v/release/Chaiavi/acommander?label=Download&logo=windows)](https://github.com/Chaiavi/acommander/releases/latest)
[![CI](https://github.com/Chaiavi/acommander/actions/workflows/ci.yml/badge.svg)](https://github.com/Chaiavi/acommander/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](#)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010%2F11-0078D6?logo=windows&logoColor=white)](#)
[![License](https://img.shields.io/badge/License-BSL%201.0-blue)](#-license)

## 📥 Download

**[Download the latest release for Windows](https://github.com/Chaiavi/acommander/releases/latest)**

1. Windows 10 or 11. No Java install needed; the runtime is included.
2. **Installer** (`acommander-setup-v<version>.exe`): installs for your user only, no admin rights, into
   `%LOCALAPPDATA%\Programs\ACommander`. Adds a Start Menu entry, an optional desktop shortcut, and an uninstaller in
   Settings › Apps. Running a newer setup upgrades in place and keeps your settings; uninstall removes the folder,
   settings included.
3. **Portable** (`acommander-v<version>.zip`): unzip anywhere and run `acommander.exe`. Nothing is installed.
4. Both are about 300 MB because they bundle about 20 tools and their own Java runtime.

![A Commander out of the box](https://github.com/user-attachments/assets/5880e5c6-c1f4-4450-b169-b8bf8079a350)

## 💡 Why A Commander

- **Norton Commander at heart** — two panes, the classic F-key bar (`F3` view, `F5` copy, `F6` move, `F8` delete), and
  a Norton Commander theme, with dark and light themes too.
- **Command Palette** — press `Ctrl+Shift+P` and type to find any action, like in VS Code. No menus to dig through.
- **Tools included** — about 20 proven tools (7-Zip, FastCopy, ripgrep, Universal Viewer, qpdf and more) work out of
  the box. No setup.
- **Archives and servers as folders** — browse zip and 7z files, and FTP / SFTP / FTPS servers, like local folders.
- **Configurable without code** — every action, tool and shortcut lives in `config/apps.json`.

### A Commander workstation with several associated apps running (View Text File, View Image, Edit and Terminal)

![A Commander with associated apps running](https://github.com/user-attachments/assets/345ee06a-8edc-4302-9b1c-2f76124177d3)

---

## 📑 Table of Contents

- [Core Features](#-core-features)
- [File Operations](#-file-operations)
- [Virtual File System (VFS) & Remote Access](#-virtual-file-system-vfs--remote-access)
- [Metadata Editing](#-metadata-editing)
- [Search & Navigation](#-search--navigation)
- [Archive, PDF, Convert & Checksum](#-archive-pdf-convert--checksum)
- [Default Shortcuts](#-default-shortcuts)
- [External Tools Bundled](#-external-tools-bundled)
- [Configuration](#-configuration-configappsjson)
- [Release History](#-release-history)
- [Build from Source](#-build-from-source)
- [Project Layout](#-project-layout)
- [License](#-license)

---

## ✨ Core Features

- **Dual-pane navigation** with keyboard-first workflow
- **Command Palette** (`Ctrl+Shift+P`) with fuzzy search and aliases
- **Data-driven action system** via `config/apps.json` — no recompilation needed for tool changes
- Built-in and external actions with selection/context rules
- Background **progress bar** with a Stop button that ends the whole operation (no next item, no delete after a stopped copy)
- **Persistent state** — left/right paths, theme mode, bookmarks and saved FTP connections in
  `config/acommander.properties` (the Settings action opens it; edits apply when the editor closes)
- Sort by Name / Size / Modified (header click or palette actions)
- Incremental **in-pane filtering** by typing letters/digits
- **Dark / Light / Norton Commander themes** — switchable UI modes
- **About** popup (Command Palette or the F1 help) — version, license, project link and runtime versions

---

## 📂 File Operations

| Operation               | Details                                      |
|-------------------------|----------------------------------------------|
| **Rename**              | Single or batch via Ant Renamer              |
| **Copy / Move**         | Between panes. When names already exist, one dialog lists them and asks: Overwrite All, Skip Existing or Overwrite Older |
| **Copy / Paste**        | Clipboard-based copy and paste               |
| **Drag and Drop**       | Drag files into other apps (web uploads, Explorer, mail), between panes, onto a folder row (into that folder) or from Explorer into a pane. A drag copies; a right-button drag between panes asks Copy Here or Move Here. FTP files download at drag start |
| **Duplicate**           | Clone a file in the same directory           |
| **Create**              | New directory or new file                    |
| **Delete**              | A locked local file or folder: lists the programs holding it open (File Locksmith) and, if you agree, ends them and deletes it |
| **Secure Wipe**         | Via SDelete                                  |
| **Attributes**          | Change file/folder attributes                |
| **Properties**          | View detailed file/folder properties         |
| **Compare Files**       | Diff two files side by side via ExamDiff     |
| **Synchronize Folders** | Keep two directories in sync                 |
| **Advanced Selection**  | Select all, invert, select by mask           |

---

## 🌐 Virtual File System (VFS) & Remote Access

A Commander v4.0 introduces a virtual file system layer that lets you browse non-local locations as if they were regular
directories.

| Feature              | Details                                                   |
|----------------------|-----------------------------------------------------------|
| **Archive browsing** | Navigate inside zip, 7z, and other archives transparently, also an archive inside an archive |
| **FTP**              | Connect to FTP servers and browse files inline (via curl) |
| **SFTP / FTPS**      | Secure remote browsing over SSH or TLS (via curl). The server's certificate or SSH key (from `.ssh\known_hosts`) is checked; tick **Trust Any Certificate** in the connection dialog for a self-signed server you trust. Saved passwords are encrypted for your Windows account (DPAPI); another PC or user asks for them again |
| **FTP-to-FTP copy**  | Transfer files directly between two remote servers        |
| **Folder upload**    | Copy or move whole folders (with empty subfolders) from a local or archive pane to a server |
| **USB drives**       | Detect and browse USB disk keys; the drive list updates when you open it (Alt+F1/F2) or press Ctrl+R, so a disk plugged in later shows up |

---

## 🏷️ Metadata Editing

| Media Type      | Details                                                                              |
|-----------------|--------------------------------------------------------------------------------------|
| **Video**       | Edit tags (title, artist, year, description, etc.) of MP4, MOV, MKV and WebM via ffmpeg |
| **Images**      | View and modify EXIF/IPTC metadata via exiv2                                         |
| **Audio**       | Edit tags (title, artist, album, etc.) of MP3, M4A, FLAC, OGG and Opus via ffmpeg, in any language |

---

## 🔍 Search & Navigation

| Feature                             | Shortcut            |
|-------------------------------------|---------------------|
| File search (wildcard-aware)        | `F10`               |
| Find-in-files text search (ripgrep) | `Alt+F10`           |
| Path dropdowns                      | `Alt+F1` / `Alt+F2` |
| Open terminal here                  | `F9`                |
| Open Explorer here                  | `Alt+F9`            |
| Bookmark / Go to / Remove bookmark  | via Command Palette |
| Sync other pane to current path     | via Command Palette |
| Link / unlink panel navigation (Enter, Backspace and `..` move both panes into same-named folders; a Linked button shows while on) | via Command Palette |

---

## 📦 Archive, PDF, Convert & Checksum

| Category     | Actions                                                                                                                                                           |
|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Archive**  | Pack to zip (`F11`) via 7-Zip GUI · Unpack (`F12`) via 7-Zip GUI · Extract anything (`Alt+F12`) via Universal Extractor · Split large file (`Alt+F11`) via 7z CLI |
| **PDF**      | Merge PDF files · Extract PDF pages (all pages, specific pages/ranges, pages-per-output chunking)                                                                 |
| **Convert**  | Media conversion (`Alt+F5`) auto-routes to image, audio or video · Graphics via `caesiumclt.exe` · Audio to MP3, M4A, FLAC, WAV, OGG or Opus via ffmpeg (quality, sample rate, loudness normalize) · Video to MP4 (H.264 / H.265, optional downsizing), or to MP4/MKV without re-encoding, or extract its audio (MP3, M4A or the original track) |
| **Media**    | Trim Media (keep a start-to-end part, lossless by default) · Join Media (join files of one type without re-encoding) · Media Info (duration, bitrate, codecs, resolution, tags) · all via ffmpeg, from the Command Palette |
| **Checksum** | Single file or recursive folder checksum via `rhash.exe`                                                                                                          |
| **UPX**      | Compress/decompress EXE and DLL files via `upx.exe`                                                                                                               |

---

## 🎮 Games

Two bundled games start from the Command Palette: **LBreakoutHD** (Breakout) and **Mines-Perfect** (Minesweeper with
extra board shapes).

---

## ⌨️ Default Shortcuts

| Key               | Action           | | Key                      | Action             |
|-------------------|------------------|-|--------------------------|--------------------|
| `F1`              | Help             | | `F7`                     | Create Directory   |
| `F2` / `Shift+F6` | Rename           | | `Alt+F7`                 | Create File        |
| `F3`              | View             | | `F8` / `Delete`          | Delete             |
| `F4`              | Edit             | | `Shift+F8` / `Shift+Del` | Delete & Wipe      |
| `F5`              | Copy             | | `F9`                     | Open Terminal Here |
| `Alt+F5`          | Convert Media    | | `Alt+F9`                 | Open Explorer Here |
| `F6`              | Move             | | `F10` / `Ctrl+F`         | Search for Files   |
| `Alt+F10`         | Find in Files    | | `Ctrl+R`                 | Refresh Panels     |
| `F11`             | Pack to Zip      | | `Ctrl+Shift+P`           | Command Palette    |
| `Alt+F11`         | Split Large File | | `Alt+F1` / `Alt+F2`      | Path Dropdown      |
| `F12`             | Unpack           | | `Alt+Enter`              | Change Attributes  |
| `Alt+F12`         | Extract Anything | | `Alt+F6`                 | Duplicate          |
| `Ctrl+C`          | Copy to Clipboard | | `Ctrl+X`                | Cut to Clipboard   |
| `Ctrl+V`          | Paste            | | `Ctrl+A`                 | Select All         |
| `Ctrl+Shift+A`    | Unselect All     | | `Ctrl+I`                 | Invert Selection   |

> **Quick tips:** `Tab` switches active pane · `Enter` opens folder/file · `Backspace` goes to parent · `F3` on a folder calculates its size.

---

## 🧰 External Tools Bundled

| Tool                   | Location                                 |
|------------------------|------------------------------------------|
| Universal Viewer       | `apps/view/UniversalViewer`              |
| Notepad4               | `apps/edit/Notepad4.exe`                 |
| FastCopy               | `apps/copy`                              |
| 7-Zip GUI              | `apps/pack_unpack/7zG.exe`               |
| Universal Extractor    | `apps/extract_all/UniExtract`            |
| qpdf                   | `apps/pdf/qpdf.exe`                      |
| Ant Renamer            | `apps/multi_rename`                      |
| File Locksmith (PowerToys, MIT) & SDelete | `apps/delete`                 |
| ripgrep                | `apps/search_in_files/rg.exe`            |
| Caesium CLI            | `apps/image_convert/caesiumclt.exe`      |
| FFmpeg (GPLv3; the build downloads it) | `apps/media/ffmpeg.exe`  |
| rhash                  | `apps/checksum/rhash.exe`                |
| ExamDiff               | `apps/file_compare/ExamDiff.exe`         |
| exiv2                  | `apps/image_metadata/exiv2.exe`          |
| UPX                    | `apps/exe_compress/upx.exe`              |
| curl                   | `apps/remote_connectivity/curl.exe`      |
| LBreakoutHD (GPL)      | `apps/games/lbreakouthd`                 |
| Mines-Perfect (GPL)    | `apps/games/mines_perfect`               |

Each tool's full name, version and home page are in the F1 help ("Bundled Tools") and in `config/apps.json` (`tools`).

### Updating the Tools

- **Check Tool Updates** (Command Palette) lists every tool with its installed and available version. **Update** downloads
  the tool's changed files; **Update All** does every tool.
- Updates come **only from this project's GitHub**: the `main` branch, and a release of this project for ffmpeg. Every file
  must match the SHA-256 in `apps/tools.sha256`, or nothing changes. A file the update didn't change (a tool's own
  settings, say) is never reset; a missing file is downloaded again.
- With **Check at Start** on (the default), ACommander looks once a day and opens the list only when there is something new.
- For the developer, `gradlew build` prints every tool's current and latest version, with release dates where GitHub
  has them, and a direct download for the newer ones (once a day; `gradlew checkToolUpdates` any time).
  `gradlew upgradeTools -Ptools=<id>` downloads the newer version, replaces the tool's files and sets its version;
  build, then commit and push. Users get it from `main`.
- `BundledToolContractTest` runs every command-line tool the way the app does (search, checksum, metadata, image
  conversion, UPX, PDF, archives, wipe, locked files, FTP protocols), so a new version that changed its flags or output fails
  the build before it is pushed. The tools that open a window run in `gradlew guiToolTest` (not in `build`): FastCopy
  copy and move, 7-Zip GUI pack and unpack, Universal Extractor and Ant Renamer are checked for their result;
  Notepad4, Universal Viewer and ExamDiff are checked to open a file without crashing.

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

| Field          | Required | Notes                                              |
|----------------|----------|----------------------------------------------------|
| `id`           | ✅        | Unique action id                                   |
| `label`        | ✅        | Display name                                       |
| `description`  | —        | One sentence shown in the F1 help                  |
| `category`     | —        | F1 help section, one of `HelpTopics.CATEGORIES`    |
| `shortcut`     | —        | Keyboard shortcut string                           |
| `aliases`      | —        | Palette search aliases                             |
| `contexts`     | —        | `global` · `filePane` · `commandPalette`           |
| `selection`    | —        | `none` · `single` · `multi` · `any` · `singleFile` · `singleFolder` · `singleOrMultipleFiles`; otherwise the action is blocked with a message |
| `type`         | —        | `builtin` (default) or `external`                  |
| `builtin`      | —        | Override builtin handler id                        |
| `path`         | External | Executable path                                    |
| `args`         | —        | Argument array                                     |
| `refreshAfter` | —        | Refresh panes after execution                      |
| `prompt`       | —        | Prompt config for external actions                 |
| `ftp`          | —        | `true` = allowed on FTP panes (default: rejected)  |
| `writes`       | —        | Pane it writes to: `none` (default) · `source` (focused) · `target` (other) · `both`; blocked when that pane is a read-only archive |
| `fileTypes`    | —        | Palette offers it only when all selected items are one of: `convertibleImage` · `convertibleAudio` · `convertibleVideo` · `media` (audio or video) · `imageWithMetadata` · `videoWithMetadata` · `audioWithMetadata` · `executable` · `archive` · `pdf` |
| `requires`     | —        | Extra palette conditions: `clipboardHasFiles` · `focusedPaneIsFtp` · `textFileInEachPane` · `navigationLinked` · `navigationUnlinked` |

### Placeholders in `args`

| Placeholder                                 | Meaning                                                |
|---------------------------------------------|--------------------------------------------------------|
| `${selectedFile}`                           | First selected file path                               |
| `${selectedFileQuoted}`                     | First selected file path (quoted)                      |
| `${selectedFiles}`                          | All selected file paths as separate args               |
| `${selectedName}`                           | Selected item name                                     |
| `${focusedPath}` / `${focusedPathQuoted}`   | Focused pane path                                      |
| `${targetFolder}` / `${targetFolderQuoted}` | Opposite pane path                                     |
| `${promptValue}`                            | Value entered from the `prompt` dialog                 |

> `ToolCommandBuilder` also creates quoted aliases for extra placeholders (e.g. `${archiveFileQuoted}` when `${archiveFile}` is provided by builtin flows).

### Bundled Tools (`tools`)

```json
{"id": "ripgrep", "name": "ripgrep", "version": "15.1.0", "link": "https://github.com/BurntSushi/ripgrep",
 "paths": ["apps/search_in_files"], "upstream": {"github": "BurntSushi/ripgrep"}}
```

| Field           | Notes                                                                                     |
|-----------------|-------------------------------------------------------------------------------------------|
| `paths`         | Files or folders under `apps/` that belong to the tool; every shipped file needs one owner |
| `upstream`      | Where the build looks for a newer version: `github` (owner/repo) with optional `asset` (regex for the file to download), or `page` + `pattern` (regex, group 1 = version) with `url` (`{version}` filled in) or `downloadPattern` (regex, group 1 = link); `extract` (installer arguments, `{dir}`) when 7-Zip can't unpack it; `files` (names to take) when they differ from the tool's files; leave it out for tools no longer developed |
| `release`       | A release of this project holding the tool's files (ffmpeg, too big for git)              |
| `minAppVersion` | Users of an older ACommander don't get this tool version                                   |

---

## � Release History

Full notes and downloads for every version: [GitHub Releases](https://github.com/Chaiavi/acommander/releases).

<details>
<summary><b>v4.5</b> — quality update: small workflow features and day-to-day fixes</summary>

**New Features & Enhancements**

- **Open hosts file** — open the Windows hosts file directly from the Command Palette
- **Report Bug / Contact** — quickly open a bug/contact flow from inside the app
- **Analyze File** — identify file type/content details for a single selected file
- **Extract PDF Pages (upgraded)** — interactive extraction modes with page expressions (`,`, `:`, `-`) and chunking
  options
- **Natural numeric sort** — filename sorting now treats numbers naturally (`... 9, 10 ...`)

**Bug Fixes**

- Fixed Ant Renamer not opening/showing correctly
- Fixed multi-file move reliability
- Fixed PDF merge failures
- Fixed PDF page extraction failures
- Fixed Hebrew rename caret/navigation behavior with arrow keys
- Improved post-move focus to stay on the next item in the source pane

</details>

<details>
<summary><b>v4.0</b> — major release: virtual file system, metadata editing, folder sync</summary>

**Virtual File System (VFS) & Remote Access**

- **Virtual folders** — browse archives (zip, 7z, etc.) as if they were regular directories
- **FTP / SFTP / FTPS** — connect to remote servers and browse them inline, just like local paths
- **FTP-to-FTP copy** — transfer files directly between two remote FTP locations

**File Comparison & Synchronization**

- **Compare files** — diff two files side by side via ExamDiff
- **Synchronize folders** — keep two directories in sync across panes

**Metadata Editing**

- **Edit video metadata** — modify tags on MP4, MOV, MKV and WebM videos via ffmpeg
- **Edit image metadata** — view and change EXIF/IPTC data on images via exiv2
- **Edit audio metadata** — update tags on MP3, M4A, FLAC, OGG and Opus files via ffmpeg

**File Operations**

- **Duplicate file** — quickly clone a file in the same directory
- **Copy / Paste** — standard clipboard-based copy and paste support
- **View properties** — inspect detailed file and folder properties
- **Advanced selection** — select all, invert selection, select by mask with keyboard shortcuts
- **UPX compression** — compress/decompress EXE and DLL files via UPX

**UI & Usability**

- **Dark / Light / Norton Commander themes** — switch UI modes to match your preference
- **Improved default sorting** — date and size sort descending by default; name sorts ascending
- **USB drive support** — detect and browse USB disk keys seamlessly

**Infrastructure**

- **GitHub CI/CD** — automated compile and build via GitHub Actions

**Bug Fixes**

- Fixed empty bottom button bar triggering unintended actions when Alt-clicked
- Fixed `Alt+F1` / `Alt+F2` path dropdowns not changing the folder
- Fixed a bug in multi-file copying

</details>

---

## 🚀 Build from Source

You only need this to change the code. To use the app, [download a release](#-download).

```bash
# Run the application
.\gradlew.bat run

# Run tests
.\gradlew.bat test

# Build fat JAR
.\gradlew.bat shadowJar

# Build Windows distribution (EXE + runtime + apps/config + zip + setup)
.\gradlew.bat dist
```

**Output locations:**

| Artifact        | Path              |
|-----------------|-------------------|
| JAR + resources | `build/libs/`     |
| EXE             | `build/launch4j/` |
| Distribution    | `dist/`           |
| Installer       | `dist/acommander-setup-v<version>.exe` (script: `installer/acommander.iss`) |

---

## 🗂️ Project Layout

```
acommander/
├── apps/                    Bundled external tools
├── config/                  apps.json, user properties
├── docs/                    CODEMAP.md — where each feature lives in the code
├── installer/               Inno Setup script for the Windows installer
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
