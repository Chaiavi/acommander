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
  → ActionExecutor.execute(action)
      1. FTP pane?        apps.json `ftp: true` or rejected
      2. Read-only pane?  apps.json `writes`: source = focused pane, target = other pane, both
      3. Selection?       apps.json `selection` (SelectionRule.blockedMessage) or a toast says why it was blocked
      4. type external →  executeExternal: optional prompt → ToolCommandBuilder.buildCommand → commander.runExternal
         type builtin  →  BuiltinAction.fromId(`builtin` or `id`) → ActionExecutor.handler: exhaustive switch → a public Commander method
  → Commander.<method>()  reads selection, shows dialogs
  → services/FileOperations, ArchiveOperations, PdfOperations, another service or helper/*Support class, or a bundled exe
```

Mouse on the bottom F-key buttons: `Commander.setupFunctionButtonActions()` looks up the apps.json action whose
`shortcut` is the button's key (`Alt+`/`Shift+` while held, else the plain key) via `AppRegistry.findByShortcut` and
runs it through `ActionExecutor.execute()`, so the same gates apply. `Commander.updateBottomButtons()` relabels the
buttons while a modifier is held (labels are still hard-coded there).

Command Palette (`Ctrl+Shift+P`): `palette/CommandPaletteController` lists `ActionRegistry.all()` (actions whose
`contexts` include `commandPalette`), ranks with `ActionMatcher.rank()` + `ActionPriorityEngine.priority()`
(`priority` / `priorityRules` in apps.json), hides disabled ones via `ActionRegistry.isSelectionAllowed()` (apps.json
`ftp`, `requires`, `fileTypes`), and runs the chosen one through `ActionExecutor.execute()`.

## 2. Places That Hard-Code Action Ids

Adding or renaming an action id? Check each of these:

| Place | What it decides |
|---|---|
| `config/apps.json` | The action itself: label, shortcut, contexts, selection, tool `path` + `args`, and its rules: `ftp`, `writes`, `fileTypes`, `requires` |
| `src/test/resources/action-rules-snapshot.txt` | Expected rules per action; `ActionRulesSnapshotTest` writes the actual table to `build/` when they differ |
| `actions/BuiltinAction` + `ActionExecutor.handler()` | builtin id → `Commander` method (a missing case does not compile) |
| `ActionRegistry.toAppAction()` | Dynamic palette labels (`fileProperties`, `duplicate`) |
| `Commander.updateBottomButtons()` | Bottom F-key button labels (actions come from apps.json shortcuts) |
| `README.md` shortcuts table | Manual docs for shortcuts (the F1 help is built from apps.json) |

## 3. Feature → Code

`Commander` method is the entry point. "Logic" is the testable class. Tools are under `apps/`.

| Action id (apps.json) | `Commander` method | Logic / runs via | Tool |
|---|---|---|---|
| `help` (F1) | `help` | `dialog/HelpDialog`, `helpers/HelpTopics` (rows from apps.json `label` / `shortcut` / `description`, sections from `category`) | |
| `about` | `about`, `openInBrowser` | `dialog/AboutDialog` (also the About button in F1 help) | browser |
| `settings` | `openSettings` | `FileOperations.edit` | edits `config/acommander.properties` |
| `rename` (F2, Shift+F6) | `renameFile` | `FileOperations.rename` → single: VFS, many: `multiRename` | `multi_rename/Renamer.exe` |
| `view` (F3) | `viewFile` / `calculateDirSpace` (folder) | `FileOperations.view` / `FileHelper.folderSize` | `view/UniversalViewer/Viewer.exe` |
| `edit` (F4) | `editFile` | `FileOperations.edit` | `edit/Notepad4.exe` |
| `copy` (F5) | `copyFile` → `transfer` → `runTransfer` | `ClipboardTransfer.capture`, `TransferConflicts.find` → `dialog/OverwriteDialog` when names clash, `FileOperations.transfer` (local: one FastCopy run, `/cmd=${copyMode}` from the policy, + arrival check, else VFS `copy`) | `copy/fcp.exe` (FastCopy) |
| `move` (F6) | `moveFile` → `transfer` → `runTransfer` | `TransferConflicts.find` / `keep`, `FileOperations.transfer` (same drive: rename, or FastCopy into an existing folder; across drives: FastCopy per item; VFS otherwise) | `copy/fcp.exe` |
| `duplicate` (Alt+F6) | `duplicateFile` | `ClipboardTransfer.duplicateName`, VFS `copy` | — |
| `copySelection` / `cutSelection` / `pasteSelection` | `copySelectionToClipboard`, `cutSelectionToClipboard`, `pasteClipboardSelection` | `services/ClipboardTransfer` (state, paste loop, duplicate names, target paths) | — |
| `mkdir` (F7) / `mkfile` (Alt+F7) | `makeDirectory` / `makeFile` | `FileOperations.mkdir` / `mkFile` (VFS) | — |
| `delete` (F8, Del) | `deleteFile` | `FileOperations.delete` (VFS; locked local files → `unlockDelete`) | — |
| `deleteWipe` (Shift+F8) | `deleteWipe` | `FileOperations.wipeDelete` | `delete/wipe/sdelete64.exe` |
| `unlockDelete`, `wipeDelete`, `multiRename` | — (type `external`) | `ActionExecutor.executeExternal` | `delete/unlock_delete/ThisIsMyFile.exe`, `sdelete64.exe`, `Renamer.exe` |
| `terminal` (F9) / `explorer` (Alt+F9) | `terminalHere` / `explorerHere` | `FileOperations.openTerminal` / `openExplorer` | PowerShell `Start-Process` (folder = working directory), `explorer.exe` |
| `search` (F10, Ctrl+F) | `search` → `pickFoundFile` | `tools/BundledToolCommands.findByName`, `foundFiles`; `FoundFilesDialog` | `search_in_files/rg.exe` |
| `findInFiles` (Alt+F10) | `findInFiles` → `pickFoundFile` | `dialog/FindInFilesDialog`, `FoundFilesDialog`; `tools/BundledToolCommands.findInFiles`, `foundFiles` | `search_in_files/rg.exe` |
| `pack` (F11) | `pack` | `ArchiveOperations.pack` | `pack_unpack/7zG.exe` |
| `splitLargeFile` (Alt+F11) | `splitLargeFile` | `dialog/SplitSizeDialog`, `tools/BundledToolCommands.parseSplitSize` | `extract_all/UniExtract/bin/x64/7z.exe` |
| `unpack` (F12) | `unpackFile` | `ArchiveOperations.unpack` → `unpackWith` | `pack_unpack/7zG.exe` |
| `extractAll` (Alt+F12) | `extractAll` | `ArchiveOperations.extractAll` → `unpackWith` | `extract_all/UniExtract/UniExtract.exe` |
| `mergePdf` / `extractPdfPages` | `mergePDFFiles` / `extractPDFPages` | `dialog/PdfExtractDialog`, `services/PdfOperations.merge` / `extractPages` / `pageCount`, `PdfExtractOptions` | `pdf/pdftk.exe` |
| `convertMediaFile` (Alt+F5) | `convertMediaFile` → image, audio or video below | `ImageConversionSupport`, `MediaFiles` | — |
| `convertGraphicsFiles` | `convertGraphicsFiles` | `dialog/ImageConversionDialog`, `services/ImageConversionService` (command, output lookup), `ImageConversionSupport` | `image_convert/caesiumclt.exe` |
| `convertAudioFiles` | `convertAudioFiles`, `mediaConverter`, `showConversionResult` | `dialog/AudioConversionDialog`, `services/MediaConversionService.convertAudio` | `media/ffmpeg.exe` |
| `convertVideoFiles` (also Extract Audio) | `convertVideoFiles` | `dialog/VideoConversionDialog`, `MediaConversionService.convertVideo` (`VideoFormat`; Original Audio probes the codec first) | `media/ffmpeg.exe` |
| `trimMedia` / `joinMedia` | `trimMedia` / `joinMedia` | `dialog/TrimMediaDialog`, `MediaConversionService.trim` / `join` (concat list), `parseTime`; `MediaFiles.joinProblem`, `withExtension` | `media/ffmpeg.exe` |
| `mediaInfo` | `mediaInfo` | `MediaConversionService.probe` + `mediaInfo` (input part of `ffmpeg -i`) | `media/ffmpeg.exe` |
| `checksumFile` / `checksumFolderContents` | `checksumFile` / `checksumFolderContents` | `dialog/ChecksumOptionsDialog`, `ChecksumResultDialog`; `tools/BundledToolCommands.checksum`, `checksumDigest`, `checksumOutputPath` | `checksum/rhash.exe` |
| `analyzeFile` | `analyzeFile` | `tools/BundledToolCommands.analyzeFile` | `file_analysis/file.exe` + `magic.mgc` |
| `compareFiles` | `compareFiles`, `canCompareSelectedFiles` | `dialog/CompareFilesDialog`, `tools/BundledToolCommands.compareFiles` | `file_compare/ExamDiff.exe` |
| `compareFolders` | `compareFolders`, `applyFolderCompareStyle` | `dialog/CompareFoldersDialog`, `services/FolderComparer` (byte comparison optional) | — |
| `fileProperties` (Alt+Enter) | `fileProperties` | `tools/FilePropertiesLauncher` (temp `.vbs` via `wscript.exe`, Windows Properties dialog) | — |
| `changeAttributes` | `changeAttributes` | `dialog/AttributesDialog`, `helpers/FileAttributesHelper` | `attrib` |
| `editImageMetadata` / `removeImageMetadata` | `editImageMetadata` → `editMetadata` (shared with video/audio) / `removeImageMetadata` → `removeMetadata` (shared confirm + background run) | `dialog/ImageMetadataDialog`, `helpers/ImageMetadataSupport` | `image_metadata/exiv2.exe` |
| `editVideoMetadata` / `removeVideoMetadata` | `editVideoMetadata` / `removeVideoMetadata` | `dialog/MetadataFormDialog.editVideo`, `helpers/MediaTagSupport` | `media/ffmpeg.exe` |
| `editAudioMetadata` / `removeAudioMetadata` | `editAudioMetadata` / `removeAudioMetadata` | `dialog/MetadataFormDialog.editAudio`, `helpers/MediaTagSupport` | `media/ffmpeg.exe` |
| `compressExecutable` | `compressExecutable` | `dialog/ExecutableCompressionDialog`, `helpers/ExecutableCompressionSupport` (`UpxAction`, `upxCommand`, `percentChange`) | `exe_compress/upx.exe` |
| `refresh` (Ctrl+R) | `refreshPanesAndDrives` | `FilesPanesHelper.refreshFileListViews`, `ComboBoxSetup.refreshDrives` | — |
| `selectAll` / `unselectAll` / `invertSelection` / `selectByPattern` | same names | `dialog/SelectByPatternDialog`, `FilesPanesHelper.selectAllItems` … `selectByPattern` | — |
| `sortByName` / `sortBySize` / `sortByDate` | same names, `onSortHeaderClicked` | `FilesPanesHelper.toggleSort` → `helpers/PaneSorter` | — |
| `toggleDarkMode` | same name, `applyTheme` | `dialog/DialogTheme` (`ThemeMode`, `apply(scene)`) | `styles/app-theme.css` |
| `bookmarkThisPath` / `gotoBookmark` / `removeBookmark` | `bookmarkCurrentPath` / `gotoBookmark` / `removeBookmark`, `pickBookmark` | `dialog/BookmarkPickerDialog`; stored as `bookmark.*` properties | — |
| `ftpConnect` / `ftpDisconnect` | `ftpConnect` / `ftpDisconnect` | `dialog/FtpConnectDialog`, `vfs/FtpFileSystem`, `FtpConnectionOptions` | `remote_connectivity/curl.exe` |
| `openHostsFile` | `openHostsFile` | `FileOperations.openHostsFile`: elevated `edit` action via PowerShell `Start-Process -Verb RunAs` | `edit/Notepad4.exe` |
| `syncToOtherPane` | `syncToOtherPane` | — | — |
| `linkNavigation` / `unlinkNavigation` | same names, `followInOtherPane` (called by `enterSelectedItem` and `goUpOneFolder`), `linkIndicator` button | `services/LinkedNavigation` | — |
| `leftPathCombo` / `rightPathCombo` (Alt+F1/F2) | `leftPathComboBox.show()` | `helpers/ComboBoxSetup`, `FolderComboBoxCell` | — |
| `reportBug` | `reportBug`, `submitBugReport` | `dialog/ReportBugDialog`, `helpers/BugReportUrl` (prefilled issue URL + label) | browser |
| `checkToolUpdates` | `checkToolUpdates`, `checkToolUpdatesAtStart` (from `initialize`, once a day), `showToolUpdates` | `services/ToolUpdateService` (`check`, `update`), `dialog/ToolUpdatesDialog`; tool list = apps.json `tools` | files from this project's GitHub (raw `main`, ffmpeg from its release) |
| `openCommandPalette` | `openCommandPalette` | `palette/CommandPaletteController` | — |

Not actions, but often asked for:

| Behaviour | Where |
|---|---|
| Enter on an item (open folder, archive (also one inside an archive), run exe/bat/ps1, open with default app) | `Commander.enterSelectedItem`, `handleArchiveEnter`, `enterArchiveFile`, `openFileWithSystemDefault` |
| Backspace / go up (local, archive, FTP root disconnects) | `Commander.goUpOneFolder` (also `..` in `enterSelectedItem`); inside an archive `Commander.goUpInArchive` → `FilesPanesHelper.goUpInArchive` / `exitArchive` |
| Type-to-filter popup | `Commander.filterByChar`, `backspaceCharFilter`, `clearCharFilter`; logic in `helpers/IncrementalFilter` (one per pane) |
| Pane list cells, icons, colours | `Commander.configListViewLookAndBehavior`, `helpers/FileIcons` |
| Drag and drop (out to other apps, between panes, onto folder rows, in from Explorer; right-drag Copy / Move menu) | `Commander.configDragAndDrop`, `startPaneDrag`, `dropTarget`, `dropOnPane` (cell handlers in `configListViewLookAndBehavior`) → `transfer`; logic in `services/PaneDragDrop` |
| Pane footer (counts / sizes) | `Commander.updatePaneSummary` |
| Running-tool progress bar + Stop button | `helpers/ExternalProgressController` (count, show/hide, `run` for background work as an `Operation`); `Commander.buildExternalCommandListener` feeds it, `stopExternalTasks` → `Operation.stopAll`, `runWithProgress`, `runFileOperation` |
| Error / info / toast | `Commander.showError`, `showInfo`, `showToast` |
| Text-input prompt | `Commander.promptUser` / `getUserFeedback` → `dialog/TextPromptDialog` |
| Startup paths + persistence | `Commander.loadConfigFile`, `resolveInitialPath`, `persistCurrentPaths` (on window close in `Main`) |
| Read-only archive warning | `Commander.isInReadOnlyArchive`, `showReadOnlyLocationWarning` |

## 4. `Commander.java` Layout (top to bottom)

~3.6k lines. Sections in file order; search the first method name to land there. Dialogs live in `dialog/`.

1. Fields, `@FXML` controls, `initialize`, `setupFunctionButtonActions`.
2. External-tool progress: `buildExternalCommandListener`, `stopExternalTasks` (UI in `ExternalProgressController`).
3. Sort headers: `configSortHeaders` … `sortIndicator`; `onPathChanged`.
4. Key bindings + palette: `setupBindings`, `determineCurrentContext`, `openCommandPalette` … `selectPreviousCommandPaletteAction`.
5. Theme: `initializeTheme`, `toggleDarkMode` (apply logic at file end: `applyTheme`).
6. Config/properties: `loadConfigFile`, `loadAppRegistry`, `saveConfigFile`, `persistCurrentPaths`.
7. List look and icons: `configMouseDoubleClick`, `configListViewLookAndBehavior` (icons from `FileIcons`), drag and drop
   (`configDragAndDrop` … `dropOnPane`), pane summary.
8. Navigation: `enterSelectedItem`, archive enter/exit, `goUpOneFolder`, linked navigation (`linkNavigation` …
   `followInOtherPane`), `openFileWithSystemDefault`.
9. F-key file operations: `help` → `renameFile` → `viewFile` → `editFile` → `copyFile` → `duplicateFile` → `moveFile`
   → `makeDirectory` → `makeFile` → `deleteFile` → `deleteWipe` → `terminalHere` → `explorerHere` → `search`
   → `findInFiles` → `pack` → `splitLargeFile`.
10. Conversion: `convertGraphicsFiles` (image), `convertMediaFile`, `convertAudioFiles`, `convertVideoFiles`, `trimMedia`,
    `joinMedia`, `mediaInfo`.
11. `analyzeFile`, `checksumFile`, `checksumFolderContents`, `unpackFile`, `extractAll`, PDFs.
12. Compare: `compareFiles`, `compareFolders` (logic in `services/FolderComparer`).
13. `fileProperties`, `changeAttributes`, image/video/audio metadata edit + remove, `compressExecutable`.
14. `syncToOtherPane`, `showLocalFolder` (shows a local folder, first leaving an archive/FTP pane), bookmarks,
    selection, `ftpDisconnect`, `openHostsFile`, `ftpConnect`.
15. Type-to-filter popup, prompts (`getUserFeedback`, `promptUser`, `pickBookmark`).
16. `runExternal`, `showError`, `showInfo`.
17. Clipboard copy/cut/paste, toast.
18. `updateBottomButtons`, read-only archive checks, `reportBug`, `checkToolUpdates`, `applyTheme`, `dialogOwner`.

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
| `ActionExecutor` | Runs an `ActionDefinition`: FTP + read-only gates from its `ftp` / `writes`, builtin dispatch, external command. |
| `BuiltinAction` | Enum of builtin ids; `BuiltinActionTest` checks it matches apps.json both ways. |
| `ActionRegistry` | Turns palette-scoped actions into `AppAction`s with enable rules (`ftp`, `requires`, `fileTypes`) and dynamic labels. |
| `AppAction` | Palette entry: id, title, shortcut, aliases, priority, enabled, run. |
| `ActionContext` | Wraps `Commander` for palette callbacks. |
| `ActionMatcher` | Fuzzy-ranks palette entries for the typed query. |
| `ActionPriorityEngine` | Scores actions from `priority` / `priorityRules` (extension, selection, clipboard). |
| `SelectionRule` | `none` / `single` / `multi` / `any` / `singleFile` check on the selection. |

### `commands/` — running external tools
| File | Role |
|---|---|
| `ExternalToolRunner` | One per app: `runExecutable` (background run, listener events, accepted exit codes, a callback when a tool changed files; the run belongs to the current `Operation` or its own), `reportFailure` (shows the failure of a run nobody waits on, never a stop; every fire-and-forget run must use it, or `Commander.runExternalReported`). No JavaFX. |
| `Operation` | One thing the user started. `ExternalProgressController.run` and every tool run start one; Stop (`Operation.stopAll`) kills its processes (`ProcessRunner.Tracker`) and makes its next `checkNotStopped` / process start throw, so a batch never reaches its next item, a fallback, or the delete after a stopped copy. |
| `OperationStoppedException` | What a stopped operation throws; `Operation.rethrowIfStopped` keeps catch-alls from turning it into an item failure. |
| `ExternalCommandListener` | Callback for tool start/finish (drives the progress bar) and `onFailure` (error dialog). |
| `ExternalCommandException` | Non-zero exit with command + output tail. |

### `config/` — `config/apps.json` model
| File | Role |
|---|---|
| `AppConfigLoader` | Jackson-loads `apps.json` into `AppConfig`. |
| `AppConfig` | Root: `actions` and `tools` lists. |
| `ActionDefinition` | One action (fields in README "Fields" table); nested enums `WriteTarget`, `FileType`, `Requirement`. |
| `ToolDefinition` | One bundled tool (apps.json `tools`): id, name, version, link, `paths` it owns under apps/ (`owns`), `minAppVersion`, `release` (ffmpeg), `upstream` (GitHub repo or page + pattern, for the build's check). |
| `PromptDefinition` / `PriorityRuleDefinition` | `prompt` and `priorityRules` sub-objects. |
| `ActionScope` | `global` / `filePane` / `commandPalette`; maps from `KeyContext`. |
| `AppRegistry` | Index by scope, `findAction(id)`, `requireAction(id)` (throws when missing), `matchShortcut(scope, event)`, `tools()`. |

### `dialog/` — dialogs (each takes owner window + theme class, not `Commander`)
| File | Role |
|---|---|
| `OptionsDialog` | Shared options-dialog shell: heading, rows, OK (Enter) / Cancel (Escape), theme, owner; `tip(control, text)` sets a tooltip inline. |
| `CompareFoldersDialog` | Compare Folders options → `FolderComparer.Options`. |
| `SplitSizeDialog` | Part size for Alt+F11 split → 7-Zip `-v` argument. |
| `OverwriteDialog` | Names that already exist in the target (size/date each side) → `TransferConflicts.Policy` for the whole copy/move/paste. |
| `ChecksumOptionsDialog` / `ChecksumResultDialog` | Hash type + output format; the result with Copy and Save. |
| `CompareFilesDialog` | ExamDiff options → `CompareFilesOptions`. |
| `PdfExtractDialog` | Which pages / pages per PDF → `PdfExtractOptions`. |
| `AttributesDialog` | Read-only / hidden / system / archive → `AttributeChangeRequest`. |
| `SelectByPatternDialog` | Wildcard or regex pattern (rejects an invalid regex). |
| `TextPromptDialog` | One-line text prompt with a preselected range (rename selects the name without its extension). |
| `FindInFilesDialog` / `FoundFilesDialog` | Find in Files options; the found files list (Enter / double-click goes to one). |
| `BookmarkPickerDialog` | Pick a bookmark by name (Go / Remove). |
| `HelpDialog` | F1: collapsible section per category of every key and action (`HelpTopics`); the filter opens matching sections; tips; About button. |
| `AboutDialog` | About popup: version, copyright, license, project link (`BugReportUrl.PROJECT_URL`), Java / JavaFX versions. |
| `ReportBugDialog` | Bug report form → prefilled GitHub issue URL. |
| `ToolUpdatesDialog` | Tool Updates: per tool name (home page link), installed / available version, status, Update; Update All; Check at Start. |
| `ImageConversionDialog` / `AudioConversionDialog` / `VideoConversionDialog` | Conversion options → `ImageConversionRequest` / `MediaConversionService.AudioRequest` / `VideoRequest`. |
| `TrimMediaDialog` | Start / end time (validated with `parseTime`), Exact Cut for video → `MediaConversionService.TrimRequest`. |
| `ExecutableCompressionDialog` | UPX compress level or decompress → `UpxAction`. |
| `FtpConnectDialog` | Host, port, user, protocol, saved connections → `FtpConnectionOptions` + save flag. |
| `ImageMetadataDialog` | EXIF/IPTC/XMP editor via `exiv2.exe` (tree table, own layout). |
| `MetadataFormDialog` | Audio and video tag editor (`editAudio` / `editVideo`, a field list each): a text field per `Field`, Preserve File Time, Reload/Save, status; `MediaTagSupport` reads and writes in the background. `changes` = edited tag key → value. |
| `DialogTheme` | The dark/light theme: `ThemeMode` (settings value ↔ style class), `apply(scene, mode)` on the window root, `apply(dialog, owner, themeClass)` for dialogs (theme class + the owner's stylesheets). The metadata dialogs take owner + theme class, not `Commander`. |

`Commander` shows a dialog with `XxxDialog.show(dialogOwner(), currentThemeMode.styleClass, …)` and acts on the
`Optional` result. Plain errors and notices use `Commander.showError` / `showInfo`.

### `helpers/`
| File | Role |
|---|---|
| `FilesPanesHelper` | Two panes: focus side, current `VFileSystem` per side, path (`getPath(side)`), listing, sort state, selection, archive enter/exit. `refreshFileListView` lists and sorts in the background; `Loads` (per pane) shows only the newest listing and runs selections (`selectNames`, `selectIndex`, `selectFileItem`, `ensureFirstEntrySelected`) once it is shown, only while the pane still shows that folder. Inner `ArchiveFolder` (combo entry for archive/FTP panes), `FilePane`. |
| `PaneSorter` | Pane order: `..` first, folders first, then the column (`SortColumn`, `SortState.toggle`), then `compareNaturalNames` (file2 before file10). |
| `ArchiveManager` | Opens an archive by extracting to a temp folder (`7z.exe`), repacks on close if modified. A failed repack copies the edits to a new `<archive>.recovered-<stamp>` folder and throws a message naming it; if that fails too, the extracted folder stays (retained) and the message names it. |
| `FileAttributesHelper` | Read/apply R/H/S/A attributes (NIO, `attrib` fallback). |
| `FileHelper` | `isTextFile` sniffing; `folderSize` (skips unreadable entries); `copyTree`; `deleteQuietly` (best-effort temp tree delete). |
| `BackgroundTasks` | The one background executor (virtual threads): `run`, `supply`. Never use `CompletableFuture.runAsync` without it. |
| `AppTempDir` | Every temp file/folder goes under `%TEMP%/acommander-<pid>`: `createTempFile`, `createTempDirectory`. Deleted on exit; `deleteStaleRoots` (run at startup by `Main`) removes roots of dead runs. `retain` / `release`: a root holding unsaved edits (a changed archive, an F4 copy of an FTP/archive file) gets a `KEEP-unsaved-edits.txt` marker and survives both. |
| `AppPaths` | The app root (`user.dir`), `config(name)`, `resolve(relative)`. The only reader of `user.dir`. |
| `SettingsStore` | `config/acommander.properties`: typed get/set (pane folders, theme, last selection pattern, bookmarks, FTP connections), save via temp file + atomic move; an unreadable file is moved to `.unreadable`. FTP passwords are stored DPAPI-encrypted (`passwordDpapi`), decrypted by `unlockFtpPasswords` when the FTP dialog opens. `Commander.loadSettings` / `saveSettings`; reloaded when the Settings editor closes. |
| `AppVersion` | Running version from `app-version.properties`, the only place it is written (`build.gradle` reads it too). |
| `HelpTopics` | F1 help rows: fixed keys, apps.json actions (same handler's shortcuts merged, F-key order), palette-only actions; `byCategory` sections in `CATEGORIES` order; `matches` filter; tips. |
| `ImageConversionSupport` | Which images convert, target formats. |
| `MediaFiles` | Which files the ffmpeg features take, by extension: audio, video, media (either), taggable audio / video; `joinProblem`, `withExtension` for Join Media. |
| `ImageMetadataSupport` | Which images the metadata editor accepts; `remove(file)` strips all metadata; exiv2 output parser `parsePrintAll` + `groupName`. |
| `MediaTagSupport` | Audio and video tags with ffmpeg: `read` (`-f ffmetadata`, `parseFfmetadata`), `write` and `remove` copy the streams into a temp file next to the original, then move it over the original. |
| `ExecutableCompressionSupport` | Which files UPX accepts; `UpxAction` (level or decompress → flag), `upxCommand`, `percentChange`. |
| `BugReportUrl` | Report Bug: prefilled GitHub new-issue URL with title prefix and label per report type; cuts the body to keep the URL under 8,000 chars. |
| `FileIcons` | Glyph + colour per pane item (folder, archive, PDF, text, image, audio, video, executable, other). |
| `IncrementalFilter` | Type-to-filter state of one pane: typed prefix, the unfiltered list, the matching items; starts over when the pane changed. |
| `ExternalProgressController` | Progress bar + Stop button: one running count over tool runs (listener) and background work (`run`); `toolName`, `isFailedExit` (ExamDiff 27 and Explorer 1 are not failures). |
| `ComboBoxSetup`, `FolderComboBoxCell` | Path combo: drives (with type/free space), Desktop/Documents/Downloads. `refreshDrives` re-reads them each time the dropdown opens and on Ctrl+R. |

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
| `FileItem` | A row: `Path` (null on FTP), display name, size, date, directory flag. `extension()` (lower case, no dot) and `allFilesWithExtension` back every file-type check. |
| `Folder`, `Drive`, `WindowsFolder` | Path-combo entries. |
| `ArchiveMode` | Read-write vs read-only archive extensions (browsable); `isUnpackable` adds the unpack-only ones (enables unpack). |
| `ArchiveSession` | Open archive: temp folder, mode, needs-repack, parent/child for nested dirs; `acquire` / `release` by running operations, `awaitIdle` before a repack. |

### `palette/`
| File | Role |
|---|---|
| `CommandPaletteController` | Controller for `CommandPalette.fxml` (included in `Commander.fxml`). |

### `services/` — feature logic moved out of `Commander` (no JavaFX)
| File | Role |
|---|---|
| `FolderComparer` | Compare Folders: only-left / only-right / different (size, date, contents via `Files.mismatch`) marks per top-level item; `key(path)` looks a pane item up. |
| `ImageConversionService` | caesiumclt commands from an `ImageConversionRequest` (`buildCommands`: files already in the target format get a second run with `--format original`); `failures` reads its error lines (it exits 0 anyway); finds the first output file to select. |
| `MediaConversionService` | ffmpeg work through an injected runner: `convertAudio`, `convertVideo`, `trim` (one file at a time via `convertAll`), `join` (concat demuxer), `probe` + `mediaInfo`; output naming, collision policy (never the source itself), deletes a failed run's partial output. |
| `ClipboardTransfer` | Copy/cut/paste between panes on any VFS: `capture` (the selection with full paths, read on the FX thread), clipboard `State`, `paste` (move or copy, per-item failures), `duplicateName` (`_copy`, `_copy_2`, …), `isSameFolder`, `targetInternalPath`. |
| `FileOperations` | Takes the captured file system and paths, never the panes: `transfer` (F5, F6, Alt+F6, Ctrl+V; FastCopy local to local with `toolTarget` = target folder + `\`), rename, delete / wipe / unlock, new folder / file, view / edit (temp copy for archive/FTP, saved back), terminal, explorer; `filterValidItems` drops "..". `Commander.runFileOperation` runs it in the background and refreshes once. |
| `TransferConflicts` | Names of a copy/move already in the target: `find` (lists both folders via VFS; ignores case except FTP), `Policy` (Overwrite / Skip / Overwrite Older → FastCopy `force_copy` / `noexist_only` / `update`), `keep` (filters whole items for moves and VFS paths). |
| `PaneDragDrop` | Drag and drop: `filesOnDisk` (local / archive items), `download` (FTP items to a temp folder at drag start), `allowsMoveOut`, `fromDroppedFiles` (other apps' files as a copy), `folderOf` (folder row → target folder), `isDraggedFolder`. |
| `ArchiveOperations` | Pack (non-local items staged under their own names), Unpack and Extract All (`unpackWith`; remote sides through temp copies). |
| `PdfOperations` | Merge, extract pages, page count with pdftk on ASCII temp copies; results saved to any pane type. Pages per PDF cuts each chunk with one `cat start-end`; other modes burst, falling back to page by page. `parsePageExpression`, `validateExtractRequest`. |
| `PdfExtractOptions` | Record: extract all / page expression / pages per PDF. |
| `LinkedNavigation` | Linked panel navigation: `target` = where the other pane goes for a folder step (same-named subfolder or parent). |
| `ToolUpdateService` | Tool Updates: `check` reads `config/apps.json` and `apps/tools.sha256` from raw `main` and compares them with this copy (`plan`: missing files, files main changed, files main dropped; a file main didn't change is never reset); `update` downloads to `AppTempDir`, checks each SHA-256, swaps the files (old ones moved aside, put back on failure) and rewrites the local `tools.sha256`. `https()` allows only this project's GitHub hosts; `resolve` refuses paths outside apps/. |

### `tools/`
| File | Role |
|---|---|
| `ToolCommandBuilder` | Expands `${...}` placeholders in apps.json `args`; resolves `path` with `AppPaths`. |
| `BundledTool` | Every tool under `apps/` the code runs directly (7z, curl, exiv2, rg, rhash, …) → `path()`, plus `TOOL_HASHES` (`apps/tools.sha256`). Tools of apps.json actions are listed there instead. `BundledToolTest` checks both lists are on disk. |
| `ToolUpstreamCheck` | Developer only, run by the build's `checkToolUpdates` task: asks each apps.json tool's `upstream` (GitHub release list, or a page + pattern) and prints every tool's current and latest version with GitHub release dates, and the `asset` download for newer ones. |
| `BundledToolCommands` | Argument lists + option types for rhash (checksum), file (analyze), ExamDiff (compare files), ripgrep (find by name / in files, `foundFiles`) and the 7-Zip split size. |
| `Dpapi` | Windows DPAPI (current user) through one hidden PowerShell run per batch, base64 lines on stdin/stdout. Encrypts the saved FTP passwords for `SettingsStore`. |
| `FilePropertiesLauncher` | Opens the Windows Properties dialog of a path: writes a VBS script to the temp dir, runs it with `wscript.exe`; the script quits when the app's process is gone. |
| `ProcessRunner` | The one way to start a process: `run()` drains stdout/stderr (merged or apart) and returns `Result`; `launch()` for GUI tools; `trackIn(Tracker)` hands the process to its `Operation` for the Stop button; `stdin` keeps secrets off the command line. |

### `vfs/` — pane file systems
| File | Role |
|---|---|
| `VFileSystem` | Interface: list, copy/move across FS, delete, rename, mkdir, `close` (an archive repacks there). |
| `VfsManager` | Creates local/FTP FS; opens archives (`openArchive`). |
| `LocalFileSystem` | Disk. Archive files are virtual folders. `addEntries` lists through a `DirectoryStream`, so bad-media names still list. `copy` is the one disk-to-anywhere copy (archives reuse it); to FTP it calls `FtpFileSystem.upload`. |
| `ArchiveFileSystem` | Inside an archive's temp folder; the first change marks it for repack and retains the folder (`AppTempDir.retain`). `outer` = the archive a nested one was opened from: leaving it (`closeOwn`) returns there and marks it changed; `close` closes the whole chain. |
| `FtpFileSystem` | FTP/FTPS/SFTP through `curl.exe`; `upload(path, target)` is the only upload (files and folder trees); SFTP gets curl's SFTP quote commands; `autoDiscoverProtocol`, `sanitizePath`. |
| `FtpConnectionOptions` | Host, port, user, protocol, URL building. |

## 6. Resources and Runtime Files

| File | Role |
|---|---|
| `src/main/resources/Commander.fxml` | Main window: path combos, two `ListView`s, headers, footers, progress box, F-key buttons, palette include. |
| `src/main/resources/CommandPalette.fxml` | Palette overlay. |
| `src/main/resources/styles/app-theme.css` | All styling; dark/light via `theme-dark` / `theme-light` root classes. |
| `src/main/resources/logback.xml` | Logs to `logs/`. |
| `config/apps.json` | Actions, shortcuts, tool paths (read from `user.dir`); `tools`: every bundled tool's name, version, link, files, upstream. |
| `config/acommander.properties` | Per-user state (gitignored), read and written only by `SettingsStore`: `left_folder`, `right_folder`, `theme_mode`, `bookmark.*`, `ftp.*`, `last_selection_pattern`, `tool_updates_*`. |
| `apps/` | Bundled tools; table in README "External Tools Bundled". |
| `apps/tools.sha256` | SHA-256 of every shipped file under apps/, written by the `toolHashes` task (commit it). Tool Updates compares the user's copy with main's. |
| `build.gradle` | Build, `shadowJar` (copies the release files to `build/libs/`), launch4j, `dist` (`releaseResources` = what ships from `config/` and `apps/`; `verifyDistribution` checks the ZIP), `seedToolSettings`, `fetchFfmpeg` (downloads `apps/media/ffmpeg.exe`, gitignored, pinned by SHA-256; version from apps.json `tools`; skipped while `apps/media/source.sha256` holds the pinned hash, which lets CI restore the folder from cache; `test`, `run` and `dist` depend on it), `toolHashes` (writes `apps/tools.sha256`), `checkToolUpdates` (runs `ToolUpstreamCheck` after `build`, once a day, not in CI), `publishFfmpeg` (`dist`: creates the `ffmpeg-<version>` release with `gh` if missing). |

## 7. Tests

Under `src/test/java/org/chaiware/acommander/`, same package as the class tested:

`actions/` ActionMatcher, ActionPriorityEngine, ActionRegistry, BuiltinAction, ActionRulesSnapshot (FTP / read-only / palette
rules per action vs `src/test/resources/action-rules-snapshot.txt`) · `commands/` ExternalToolRunner · `config/` ActionScope, AppConfigLoader, AppRegistryShortcutMatching, ToolsConfig (every shipped file belongs to one apps.json tool) · `dialog/` DialogTheme, MetadataFormDialog · `helpers/` AppTempDir, AppVersion, ArchiveManager,
BugReportUrl, ExecutableCompressionSupport, ExternalProgressController, FileAttributesHelper, FileHelper, FileIcons, FilesPanesHelper, IncrementalFilter, ImageConversionSupport, ImageMetadataSupport, MediaFiles, MediaTagSupport, PaneSorter, SettingsStore · `model/` ArchiveMode,
FileItem · `services/` ArchiveOperations, ClipboardTransfer, FileOperations, FolderComparer, ImageConversionService, MediaConversionService, PaneDragDrop, PdfOperations, ToolUpdateService, TransferConflicts · `tools/` BundledTool, BundledToolCommands, BundledToolContract (runs every command-line tool under apps/ as the app does), ToolCommandBuilder, ToolUpstreamCheck, ProcessRunner · `vfs/` ArchiveFileSystem, FtpFileSystem, LocalFileSystem · root: ArchitectureRules (process / background / temp-file / app-path / version / fire-and-forget / no file walking or hashing in `Commander` rules), `CodeMapTest` (fails when a main
class or an apps.json action is missing from this file).
