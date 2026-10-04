# Code Map

Where everything lives, so you can jump straight to the right file and method. Method names are stable; line numbers
are not, so search for the name (`Ctrl+F` / grep). Update this file when you add, move, rename or delete a class,
an action, a bundled tool or a `Commander` feature method.

All Java paths below are under `src/main/java/org/chaiware/acommander/`.

## 1. How a Key Press Becomes an Action

```
Main.start()  →  loads Commander.fxml  →  Commander.initialize()  →  Commander.setupBindings()
key press on Scene
  → KeyBindingManager.handleKeyEvent()          picks handler by KeyContext (Commander.determineCurrentContext)
      FILE_PANE      → FilePaneKeyHandlerImpl   shortcuts with scope filePane, Enter, Backspace, type-to-filter, numpad +/-/*
      PATH_COMBO_BOX → ComboxKeyHandlerImpl
      COMMAND_PALETTE→ CommandPaletteKeyHandlerImpl
      not handled    → GlobalKeyHandlerImpl     shortcuts with scope global, Tab, Alt/Shift/Ctrl → bottom button labels
  → AppRegistry.matchShortcut(scope, event)     finds the ActionDefinition from config/apps.json
  → SelectionRule.isSatisfied(selection)        `selection` field: none/single/multi/any/singleFile
  → ActionExecutor.execute(action)
      1. FTP pane?        isActionSupportedOnFtp(id) — unlisted ids are rejected
      2. Read-only pane?  isReadOnlyWriteAttempt → ActionMutator sets + isConditionalWriteBlocked
      3. type external →  executeExternal: optional prompt → ToolCommandBuilder.buildCommand → commander.runExternal
         type builtin  →  executeBuiltin: switch on `builtin` (or `id`) → a public Commander method
  → Commander.<method>()  reads selection, shows dialogs
  → commands (ACommands → CommandsAdvancedImpl) or a helper/*Support class, or a bundled exe directly
```

Mouse on the bottom F-key buttons: `Commander.setupFunctionButtonActions()` maps each button to normal/Alt/Shift
`Runnable`s directly (it does not go through `apps.json`). `Commander.updateBottomButtons()` relabels them while a
modifier is held.

Command Palette (`Ctrl+Shift+P`): `palette/CommandPaletteController` lists `ActionRegistry.all()` (actions whose
`contexts` include `commandPalette`), ranks with `ActionMatcher.rank()` + `ActionPriorityEngine.priority()`
(`priority` / `priorityRules` in apps.json), hides disabled ones via `ActionRegistry.isSelectionAllowedForBuiltin()`,
and runs the chosen one through `ActionExecutor.execute()`.

## 2. Places That Hard-Code Action Ids

Adding or renaming an action id? Check each of these:

| Place | What it decides |
|---|---|
| `config/apps.json` | The action itself: label, shortcut, contexts, selection, tool `path` + `args` |
| `ActionExecutor.executeBuiltin()` | builtin id → `Commander` method |
| `ActionExecutor.isActionSupportedOnFtp()` | Allowed on FTP panes (also used by the palette) |
| `ActionExecutor.isConditionalWriteBlocked()` | Which pane must be writable (source, target, or both) |
| `helpers/ActionMutator` | Always-write / conditional-write / read-only sets for read-only archives |
| `ActionRegistry.isSelectionAllowedForBuiltin()` | Palette shows the action only for matching file types |
| `ActionRegistry.toAppAction()` | Dynamic palette labels (`fileProperties`, `duplicate`) |
| `FilePaneKeyHandlerImpl.handle()` | F3 on a folder runs `calculateDirSpace` instead of `view` |
| `Commander.setupFunctionButtonActions()` | Bottom F-key buttons (mouse) |
| `config/f1-help.html`, `README.md` shortcuts table | Manual docs for shortcuts |

## 3. Feature → Code

`Commander` method is the entry point. "Logic" is the testable class. Tools are under `apps/`.

| Action id (apps.json) | `Commander` method | Logic / runs via | Tool |
|---|---|---|---|
| `help` (F1) | `help` | — | `view/UniversalViewer/Viewer.exe` on `config/f1-help.html` |
| `settings` | `openSettings` | `commands.edit` | edits `config/acommander.properties` |
| `rename` (F2, Shift+F6) | `renameFile` | `ACommands.rename` → single: Java, many: `multiRename` | `multi_rename/Renamer.exe` |
| `view` (F3) | `viewFile` / `calculateDirSpace` (folder) | `CommandsAdvancedImpl.doView` | `view/UniversalViewer/Viewer.exe` |
| `edit` (F4) | `editFile` | `CommandsAdvancedImpl.doEdit` | `edit/Notepad4.exe` |
| `copy` (F5) | `copyFile`, `handleF5Button` | `CommandsAdvancedImpl.copyBatch` / VFS `copy` | `copy/fcp.exe` (FastCopy) |
| `move` (F6) | `moveFile`, `handleF6Button` | `CommandsAdvancedImpl.moveBatch` | `copy/fcp.exe` |
| `duplicate` (Alt+F6) | `duplicateFile`, `generateDuplicateName` | VFS `copy` | — |
| `copySelection` / `cutSelection` / `pasteSelection` | `copySelectionToClipboard`, `cutSelectionToClipboard`, `pasteClipboardSelection` | `ClipboardTransferState` record in Commander | — |
| `mkdir` (F7) / `mkfile` (Alt+F7) | `makeDirectory` / `makeFile` | VFS `makeDirectory` / `makeFile` | — |
| `delete` (F8, Del) | `deleteFile` | `CommandsAdvancedImpl.doDelete` / VFS `delete` | — |
| `deleteWipe` (Shift+F8) | `deleteWipe` | `doWipeDelete` | `delete/wipe/sdelete64.exe` |
| `unlockDelete`, `wipeDelete`, `multiRename` | — (type `external`) | `ActionExecutor.executeExternal` | `delete/unlock_delete/ThisIsMyFile.exe`, `sdelete64.exe`, `Renamer.exe` |
| `terminal` (F9) / `explorer` (Alt+F9) | `terminalHere` / `explorerHere` | `CommandsSimpleImpl.openTerminal` / `openExplorer` | PowerShell (cmd fallback), `explorer.exe` |
| `search` (F10, Ctrl+F) | `search` | `CommandsSimpleImpl.searchFiles` (PowerShell `Get-ChildItem`) | — |
| `findInFiles` (Alt+F10) | `findInFiles`, `runFindInFiles`, `showFileResultsDialog` | `FindInFilesOptions` record | `search_in_files/rg.exe` |
| `pack` (F11) | `pack` | `CommandsAdvancedImpl.doPack` | `pack_unpack/7zG.exe` |
| `splitLargeFile` (Alt+F11) | `splitLargeFile`, `promptSplitSize`, `parseSplitSize` | — | `extract_all/UniExtract/bin/x64/7z.exe` |
| `unpack` (F12) | `unpackFile` | `CommandsAdvancedImpl.doUnpack` | `pack_unpack/7zG.exe` |
| `extractAll` (Alt+F12) | `extractAll` | `doExtractAll` | `extract_all/UniExtract/UniExtract.exe` |
| `mergePdf` / `extractPdfPages` | `mergePDFFiles` / `extractPDFPages`, `promptPdfExtractOptions` | `doMergePDFs` / `doExtractPDFPages`, `PdfExtractOptions` | `pdf/pdftk.exe` |
| `convertMediaFile` (Alt+F5) | `convertMediaFile` → image or audio below | `ImageConversionSupport`, `AudioConversionSupport` | — |
| `convertGraphicsFiles` | `convertGraphicsFiles`, `promptImageConversionOptions` | `ImageConversionSupport` | `image_convert/caesiumclt.exe` |
| `convertAudioFiles` | `convertAudioFiles`, `promptAudioConversionOptions`, `audioEncodingOptionsFor` | `AudioConversionSupport` | `sound_convert/sndfile-convert.exe`, `faac.exe`, `faad.exe` |
| `checksumFile` / `checksumFolderContents` | `checksumFile` / `checksumFolderContents`, `buildChecksumCommand` | — | `checksum/rhash.exe` |
| `analyzeFile` | `analyzeFile`, `buildAnalyzeFileCommand` | — | `file_analysis/file.exe` + `magic.mgc` |
| `compareFiles` | `compareFiles`, `canCompareSelectedFiles`, `buildCompareCommand` | — | `file_compare/ExamDiff.exe` |
| `compareFolders` | `compareFolders`, `compareFolderTrees`, `applyFolderCompareStyle` | in-Java SHA-256 compare | — |
| `fileProperties` (Alt+Enter) | `fileProperties` | temp `.vbs` via `wscript.exe` (Windows Properties dialog) | — |
| `changeAttributes` | `changeAttributes`, `promptAttributes` | `helpers/FileAttributesHelper` | `attrib` |
| `editImageMetadata` / `removeImageMetadata` | `editImageMetadata` / `removeImageMetadata` | `dialog/ImageMetadataDialog`, `helpers/ImageMetadataSupport` | `image_metadata/exiv2.exe` |
| `editVideoMetadata` / `removeVideoMetadata` | `editVideoMetadata` / `removeVideoMetadata` | `dialog/VideoMetadataDialog`, `helpers/VideoMetadataSupport` | `video_metadata/AtomicParsley.exe` |
| `editAudioMetadata` / `removeAudioMetadata` | `editAudioMetadata` / `removeAudioMetadata` | `dialog/AudioMetadataDialog`, `helpers/AudioMetadataSupport` | `audio_metadata/id3.exe` |
| `compressExecutable` | `compressExecutable`, `promptExecutableCompressionOptions` | `helpers/ExecutableCompressionSupport` | `exe_compress/upx.exe` |
| `refresh` (Ctrl+R) | — | `FilesPanesHelper.refreshFileListViews` | — |
| `selectAll` / `unselectAll` / `invertSelection` / `selectByPattern` | same names, `selectByPatternWithDialog` | `FilesPanesHelper.selectAllItems` … `selectByPattern` | — |
| `sortByName` / `sortBySize` / `sortByDate` | same names, `onSortHeaderClicked` | `FilesPanesHelper.setSort`, `compareNaturalNames` | — |
| `setDarkMode` / `setLightMode` / `setRegularMode` / `toggleDarkMode` | same names, `applyTheme` | `ThemeMode` enum in Commander | `styles/app-theme.css` |
| `bookmarkThisPath` / `gotoBookmark` / `removeBookmark` | `bookmarkCurrentPath` / `gotoBookmark` / `removeBookmark`, `promptBookmarkSelection` | stored as `bookmark.*` properties | — |
| `ftpConnect` / `ftpDisconnect` | `ftpConnect` / `ftpDisconnect` | `vfs/FtpFileSystem`, `FtpConnectionOptions` | `remote_connectivity/curl.exe` |
| `openHostsFile` | `openHostsFile` | elevated `edit` action via PowerShell `Start-Process -Verb RunAs` | `edit/Notepad4.exe` |
| `syncToOtherPane` | `syncToOtherPane` | — | — |
| `leftPathCombo` / `rightPathCombo` (Alt+F1/F2) | `leftPathComboBox.show()` | `helpers/ComboBoxSetup`, `FolderComboBoxCell` | — |
| `reportBug` | `reportBug`, `submitBugReport` | opens a GitHub issue URL | `remote_connectivity/curl.exe` |
| `openCommandPalette` | `openCommandPalette` | `palette/CommandPaletteController` | — |

Not actions, but often asked for:

| Behaviour | Where |
|---|---|
| Enter on an item (open folder, archive, run exe/bat/ps1, open with default app) | `Commander.enterSelectedItem`, `handleArchiveEnter`, `openFileWithSystemDefault` |
| Backspace / go up (local, archive, FTP root disconnects) | `FilePaneKeyHandlerImpl.goUpOneFolder` |
| Type-to-filter popup | `Commander.filterByChar`, `backspaceCharFilter`, `applyIncrementalFilter` |
| Pane list cells, icons, colours | `Commander.configListViewLookAndBehavior`, `resolveIconSpec`, `is*Extension` |
| Pane footer (counts / sizes) | `Commander.updatePaneSummary` |
| Running-tool progress bar + Stop button | `Commander.buildExternalCommandListener`, `showExternalProgress`, `stopExternalTasks` |
| Error / info / toast | `Commander.showError`, `showInfo`, `showToast` |
| Text-input prompt | `Commander.promptUser` / `getUserFeedback` |
| Startup paths + persistence | `Commander.loadConfigFile`, `resolveInitialPath`, `persistCurrentPaths` (on window close in `Main`) |
| Read-only archive warning | `Commander.isInReadOnlyArchive`, `showReadOnlyLocationWarning` |

## 4. `Commander.java` Layout (top to bottom)

~7.4k lines. Sections in file order; search the first method name to land there.

1. Fields, `@FXML` controls, `initialize`, `setupFunctionButtonActions`.
2. External-tool progress UI: `configureExternalProgressUi` … `stopExternalTasks`.
3. Sort headers: `configSortHeaders` … `sortIndicator`; `onPathChanged`.
4. Key bindings + palette: `setupBindings`, `determineCurrentContext`, `openCommandPalette` … `selectPreviousCommandPaletteAction`.
5. Theme: `initializeTheme`, `setDarkMode` … `toggleDarkMode` (apply logic at file end: `applyTheme`).
6. Config/properties: `loadConfigFile`, `loadAppRegistry`, `saveConfigFile`, `persistCurrentPaths`.
7. List look and icons: `configMouseDoubleClick`, `configListViewLookAndBehavior`, `resolveIconSpec`, pane summary.
8. Navigation: `enterSelectedItem`, archive enter/exit, `openFileWithSystemDefault`.
9. F-key file operations: `help` → `renameFile` → `viewFile` → `editFile` → `copyFile` → `duplicateFile` → `moveFile`
   → `makeDirectory` → `makeFile` → `deleteFile` → `deleteWipe` → `terminalHere` → `explorerHere` → `search`
   → `findInFiles` → `pack` → `splitLargeFile`.
10. Conversion: `convertGraphicsFiles` (image), `convertMediaFile`, `convertAudioFiles` (audio, long).
11. `analyzeFile`, `checksumFile`, `checksumFolderContents`, `unpackFile`, `extractAll`, PDFs.
12. Compare: `compareFiles`, `compareFolders` + folder-tree helpers.
13. `fileProperties`, `changeAttributes`, image/video/audio metadata edit + remove, `compressExecutable`.
14. `syncToOtherPane`, bookmarks, selection, `ftpDisconnect`, `openHostsFile`, `ftpConnect`.
15. Type-to-filter popup, prompts (`getUserFeedback`, `promptUser`, `promptBookmarkSelection`).
16. `runExternal`, option dialogs (`promptSplitSize`, `promptPdfExtractOptions`, `promptCompareFilesOptions`,
    `promptChecksumOptions`), `showError`, `showInfo`.
17. Clipboard copy/cut/paste, toast.
18. Private records and enums (options for each dialog), `promptAttributes`, `updateBottomButtons`,
    read-only archive checks, `reportBug`, `applyTheme`, `ThemeMode`.

## 5. Every Class

### Root
| File | Role |
|---|---|
| `Launcher` | Real entry point; calls `Application.launch(Main.class)` (non-modular JavaFX jar). |
| `Main` | Loads `Commander.fxml` + `app-theme.css`, maximised stage, persists paths on close. |
| `Commander` | FXML controller for `Commander.fxml`; most UI behaviour (see §3, §4). |

### `actions/` — dispatch and palette ranking
| File | Role |
|---|---|
| `ActionExecutor` | Runs an `ActionDefinition`: FTP + read-only gates, builtin switch, external command. |
| `ActionRegistry` | Turns palette-scoped actions into `AppAction`s with enable rules and dynamic labels. |
| `AppAction` | Palette entry: id, title, shortcut, aliases, priority, enabled, run. |
| `ActionContext` | Wraps `Commander` for palette callbacks. |
| `ActionMatcher` | Fuzzy-ranks palette entries for the typed query. |
| `ActionPriorityEngine` | Scores actions from `priority` / `priorityRules` (extension, selection, clipboard). |
| `SelectionRule` | `none` / `single` / `multi` / `any` / `singleFile` check on the selection. |

### `commands/` — file operations behind the UI
| File | Role |
|---|---|
| `ACommands` | Base: filters `..`/invalid items, template methods (`rename` → `doRename`, …), `runExecutable`, stop running tools. |
| `CommandsAdvancedImpl` | Used by the app. Runs apps.json tool paths (`requireAction(id)`), batches copy/move, PDFs, pack/unpack; falls back to `CommandsSimpleImpl`. |
| `CommandsSimpleImpl` | Pure-Java/OS ops: single rename, copy/move, mkdir, terminal, explorer, PowerShell file search. Pack/PDF throw "not implemented". |
| `ExternalCommandListener` | Callback for tool start/finish (drives the progress bar). |
| `ExternalCommandException` | Non-zero exit with command + output tail. |
| `PdfExtractOptions` | Record: extract all / page expression / pages per PDF. |

### `config/` — `config/apps.json` model
| File | Role |
|---|---|
| `AppConfigLoader` | Jackson-loads `apps.json` into `AppConfig`. |
| `AppConfig` | Root: `actions` list. |
| `ActionDefinition` | One action (fields in README "Fields" table). |
| `PromptDefinition` / `PriorityRuleDefinition` | `prompt` and `priorityRules` sub-objects. |
| `ActionScope` | `global` / `filePane` / `commandPalette`; maps from `KeyContext`. |
| `AppRegistry` | Index by scope, `findAction(id)`, `matchShortcut(scope, event)`. |

### `dialog/` — metadata editors (each runs its own exe)
| File | Role |
|---|---|
| `ImageMetadataDialog` | EXIF/IPTC/XMP editor via `exiv2.exe`. |
| `VideoMetadataDialog` | MP4-family tags via `AtomicParsley.exe`. |
| `AudioMetadataDialog` | ID3 tags via `id3.exe`. |

Other dialogs are built inline in `Commander` (`prompt*` methods).

### `helpers/`
| File | Role |
|---|---|
| `FilesPanesHelper` | Two panes: focus side, current `VFileSystem` per side, path, listing, sorting (`compareNaturalNames`), selection, archive enter/exit. Inner `ArchiveFolder`, `ArchiveParentItem`, `FilePane`. |
| `ArchiveManager` | Opens an archive by extracting to a temp folder (`7z.exe`), repacks on close if modified. |
| `ArchiveService` | `isSupportedArchiveExtension` — extensions 7-Zip can unpack (enables unpack/extractAll). |
| `ActionMutator` | Action id sets: always-write, conditional-write, read-only. |
| `FileAttributesHelper` | Read/apply R/H/S/A attributes (NIO, `attrib` fallback). |
| `FileHelper` | `isTextFile` sniffing. |
| `ImageConversionSupport`, `AudioConversionSupport` | Which files convert, target formats. |
| `ImageMetadataSupport`, `VideoMetadataSupport`, `AudioMetadataSupport` | Which files the metadata editors accept. |
| `ExecutableCompressionSupport` | Which files UPX accepts. |
| `ComboBoxSetup`, `FolderComboBoxCell` | Path combo: drives (with type/free space), Desktop/Documents/Downloads. |

### `keybinding/`
| File | Role |
|---|---|
| `KeyBindingManager` | `KeyContext` enum; routes to the context handler, and to the global one only if that returns false. |
| `IKeyHandler` | `boolean handle(KeyEvent)`. |
| `GlobalKeyHandlerImpl` | Global shortcuts, Tab between panes, modifier tracking. |
| `FilePaneKeyHandlerImpl` | File-pane shortcuts, Enter, Backspace, numpad selection, type-to-filter. |
| `ComboxKeyHandlerImpl`, `CommandPaletteKeyHandlerImpl` | Keys inside the path combo / palette. |

### `model/`
| File | Role |
|---|---|
| `FileItem` | A row: `File`, display name, size, date, directory flag. |
| `Folder`, `Drive`, `WindowsFolder` | Path-combo entries. |
| `ArchiveMode` | Read-write vs read-only archive extensions. |
| `ArchiveSession` | Open archive: temp folder, mode, needs-repack, parent/child for nested dirs. |

### `palette/`
| File | Role |
|---|---|
| `CommandPaletteController` | Controller for `CommandPalette.fxml` (included in `Commander.fxml`). |

### `tools/`
| File | Role |
|---|---|
| `ToolCommandBuilder` | Expands `${...}` placeholders in apps.json `args`; resolves `path` against `user.dir`. |

### `vfs/` — pane file systems
| File | Role |
|---|---|
| `VFileSystem` | Interface: list, copy/move across FS, delete, rename, mkdir, virtual folders, repack. |
| `VfsManager` | Creates local/FTP FS; enters archives (`enterVirtualFolder`). |
| `LocalFileSystem` | Disk. Archive files are virtual folders. |
| `ArchiveFileSystem` | Inside an archive's temp folder; marks modified for repack. |
| `FtpFileSystem` | FTP/FTPS/SFTP through `curl.exe`; `autoDiscoverProtocol`, `sanitizePath`. |
| `FtpConnectionOptions` | Host, port, user, protocol, URL building. |

## 6. Resources and Runtime Files

| File | Role |
|---|---|
| `src/main/resources/Commander.fxml` | Main window: path combos, two `ListView`s, headers, footers, progress box, F-key buttons, palette include. |
| `src/main/resources/CommandPalette.fxml` | Palette overlay. |
| `src/main/resources/styles/app-theme.css` | All styling; dark/light via `theme-dark` / `theme-light` root classes. |
| `src/main/resources/logback.xml` | Logs to `logs/`. |
| `config/apps.json` | Actions, shortcuts, tool paths (read from `user.dir`). |
| `config/f1-help.html` | F1 help page. |
| `config/acommander.properties` | Per-user state (gitignored): `left_folder`, `right_folder`, `theme_mode`, `bookmark.*`, `ftp.*`, `last_selection_pattern`. |
| `apps/` | Bundled tools; table in README "External Tools Bundled". |
| `build.gradle` | Build, `shadowJar` (copies `config/` + `apps/` to `build/libs/`), launch4j, `dist`, `seedUniExtractIni`. |

## 7. Tests

Under `src/test/java/org/chaiware/acommander/`, same package as the class tested:

`actions/` ActionMatcher, ActionPriorityEngine, ActionRegistry · `commands/` CommandsAdvancedImpl, CommandsSimpleImpl,
PackVfs · `config/` ActionScope, AppConfigLoader, AppRegistryShortcutMatching · `helpers/` AudioConversionSupport,
FileAttributesHelper, FileHelper, FilesPanesHelperNaturalSort, ImageConversionSupport · `model/` ArchiveMode,
FileItem · `tools/` ToolCommandBuilder · `vfs/` FtpFileSystem · root: CommanderCopy, `CodeMapTest` (fails when a main
class or an apps.json action is missing from this file).
