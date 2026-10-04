# Refactor Plan

Phased refactor agreed on 2026-10-04. Fix bugs first, then move action rules into `apps.json`, then shrink `Commander`
into testable services and dialogs, then dedupe, then `File` → `Path`. Security is the last phase.

Tick a step when its commit lands. Line numbers are approximate; search the symbol.

## Decisions

1. FTP password at rest: encrypt with Windows DPAPI (`jna-platform` `Crypt32Util`).
2. `File` → `Path` migration is in scope (Phase 9).
3. One commit per step; push at the end of each phase.
4. Security is last. Earlier phases keep security-relevant behaviour identical (e.g. curl args).
5. Feature logic moved out of `Commander` goes to a new `services/` package.
6. Out of scope: new features, UI redesign, dependency upgrades, Lombok cleanup of config POJOs.
7. Security findings wait for Phase 10, even live ones (FTP password in logs, PowerShell quote injection). Their
   GitHub issues are filed in the same step as the fix, not earlier: the repo is public.
8. Data loss is not security and is not deferred: the archive repack bug is fixed in Phase 3 (3.3).
9. The app version has one source, `appVersion` in `build.gradle`. Code reads it at runtime; no version literal
   anywhere in `src/main/java`, and a test enforces that (3.5).
10. Bug fixes get a GitHub issue and `(fixes #n)` in the commit (AGENTS.md rule 7). Pure refactor steps do not.

## Phase Gate (start of every phase)

1. `git pull --rebase`; `.\gradlew.bat build` green.
2. For each step of the phase: grep the named symbols; confirm they exist and the problem still exists. Mark the step
   done / changed / dropped here.
3. Skim later phases; rename anything this phase moved.
4. Commit the plan update.

## Phase End

1. `.\gradlew.bat build` green. Record the `Commander` line count below.
2. `docs/CODEMAP.md` updated (`CodeMapTest` enforces the class list). `AGENTS.md` if a rule changed. README + F1 help
   only if user-visible.
3. Restart the app (`gradlew run`). A running app locks `build/resources`; stop its `Launcher` java process before
   building. Hand the user the smoke checklist plus the phase's own items.
4. Push.

Smoke checklist (user, inside a temp folder only): navigate + Backspace; F3 on a file and on a folder (size); F4;
F5/F6 to the other pane; Alt+F6; F7/Alt+F7; F8; F11 pack + F12 unpack; Enter an archive, edit, leave (repack);
Command Palette; theme toggle; one metadata edit on a copied jpg/mp3/mp4; checksum; compare files; FTP connect if a
server is available.

`Commander` line count: 7459 (start), 7199 (after Phase 1), 7038 (after Phase 2), 6929 (after Phase 3), 5380 (after
Phase 4), 3632 (after Phase 5), 3533 (after Phase 6), 3553 (after Phase 7; Search moved in from the commands).

## Phase 0 — Prep and Safety Net (no behaviour change)

- [x] 0.1 This file.
- [x] 0.2 `ActionRulesSnapshotTest` + `src/test/resources/action-rules-snapshot.txt`: for every `apps.json` action,
  today's FTP gate, read-only gate (source / target / both / none) and palette enablement for sample selections.
  Locks behaviour for Phase 2.
- [x] 0.3 Dropped: an empty `ArchitectureRulesTest` checks nothing. Created in 1.7 with its first rules.

## Phase 1 — Processes, Threads, Temp Files (real bugs)

- [x] 1.1 `tools/ProcessRunner`: list-form command, working dir, charset, merged or separate stderr drained on a
  virtual thread, `trackIn` for the Stop button, `launch()` for GUI tools. Stdin and timeout left out until a caller
  needs them (Phase 10 adds stdin for curl).
- [x] 1.2 `helpers/BackgroundTasks`: one virtual-thread executor. Replaced every `CompletableFuture.runAsync` /
  `supplyAsync` on the common pool (Commander, `ACommands.runExecutable`) and the per-dialog `newCachedThreadPool`.
  Fixed: an open viewer/editor held a common-pool thread until closed, so a few open viewers stalled all background
  work. No shutdown hook: virtual threads are daemon. The 5 archive-navigation blocks became
  `Commander.runWithProgress`.
- [x] 1.3 Every `ProcessBuilder` site now uses `ProcessRunner` (Commander, `ACommands.runExecutable`, 3 dialogs,
  `ArchiveManager`, `ComboBoxSetup`, `FileAttributesHelper`, `FtpFileSystem` x2). Args identical. Fixed: metadata
  delete runners and `attrib` waited without draining output; the dialogs read stdout then stderr (hangs on a full
  stderr pipe); image extract/insert left buttons disabled on early exits. The relative `"apps/..."` exe paths work
  (child resolves against the app's cwd = `user.dir`); `ToolLocator` (3.1) still unifies them.
- [x] 1.4 Off the FX thread via `Commander.runWithProgress` (now also a `Callable` form that shows failures):
  `calculateDirSpace` (new `FileHelper.folderSize`, which skips unreadable subfolders instead of failing),
  `compareFolders`, and the three metadata removes (merged into `Commander.removeMetadata`; the image one now gets
  the theme too).
- [x] 1.5 Every `Files.walk` / `Files.list` is closed. The three unclosed ones were all "delete this temp tree"
  (Commander audio staging, PDF merge work dir, `ArchiveSession.cleanup`); they are now one
  `FileHelper.deleteQuietly`. Deleted the unused `CommandsSimpleImpl.copyDirectory`.
- [x] 1.6 `helpers/AppTempDir`: every temp file/folder lives under `%TEMP%/acommander-<pid>`, deleted on exit; roots
  of dead runs are deleted at startup. Replaced all 20 `deleteOnExit` (they never deleted non-empty folders, so
  extracted archives and unpack dirs leaked into `%TEMP%` forever). `CommandsAdvancedImpl.deleteRecursive` became
  `FileHelper.deleteQuietly`. Per-operation `TempWorkspace` dropped: the root already bounds every leak to one run.
- [x] 1.7 `ArchitectureRulesTest` (source-text scan like `CodeMapTest`): `new ProcessBuilder` only in
  `ProcessRunner`; `Executors.new` / `CompletableFuture.runAsync|supplyAsync` only in `BackgroundTasks` (the
  metadata dialogs now call it directly); no `deleteOnExit`, `File.createTempFile`, or temp files in the default
  temp dir.

## Phase 2 — Action Rules in apps.json (depends on 0)

- [x] 2.1 `ActionDefinition` fields: `ftp` (bool), `writes` (`none|source|target|both`), `fileTypes` (list),
  `requires` (list: `clipboardHasFiles`, `focusedPaneIsFtp`, `textFileInEachPane`). Nested enums, so Jackson
  rejects a misspelled value at load.
- [x] 2.2 Filled `apps.json` with today's rules; snapshot test stayed green on the switch.
- [x] 2.3 Deleted `isActionSupportedOnFtp`, `isConditionalWriteBlocked`, `ActionMutator`, the id checks in
  `ActionRegistry` (now `isSelectionAllowed`). Kept the snapshot test: it is the cheap review of any rule change.
- [x] 2.4 `BuiltinAction` enum + exhaustive switch in `ActionExecutor.handler` (kept there, not in `Commander`).
  `BuiltinActionTest` checks apps.json ↔ enum both ways. Dropped the unreachable `setDarkMode` / `setLightMode` /
  `setRegularMode` builtins; `syncOtherPane` renamed to its action id.
- [x] 2.5 F-key buttons run the apps.json action for their key through `ActionExecutor` (#146): mouse clicks now
  get the FTP / read-only gates, the first click no longer runs twice, Alt+click F1/F2 opens the path lists the
  label promises. Dropped `handleF5/F6/F10Button` and the FXML `onAction`s; added the missing tooltips. Labels stay
  hard-coded in `updateBottomButtons` (deriving them from apps.json labels would change the visible text).
- [x] 2.6 Docs: CODEMAP §1-2, README "Fields" table, AGENTS "Adding an action".

## Phase 3 — Shared Infrastructure and Errors the User Never Sees (depends on 1, 2)

Rechecked after Phase 2. Dropped the `UiFeedback` and `CommanderContext` interfaces: services can throw and let
`Commander` show the error, and a narrow context can't cover `ActionExecutor.handler` (~60 `Commander` methods) or
`FilePaneKeyHandlerImpl` (12).

- [x] 3.1 Tool paths. Changed from the plan: no `tools` map in `apps.json` / `ToolLocator`. A `tools/BundledTool`
  enum (compile-checked names, one list) + `helpers/AppPaths` (app root, `config(name)`, `resolve`). Replaced all
  hard-coded paths (Commander, the 3 metadata dialogs, `ArchiveManager`, `FtpFileSystem`, `ToolCommandBuilder`),
  deleted the unused `ACommands.APP_PATH` and the dialogs' no-op `.directory(user.dir)`. The callers' own
  "not found" checks stay. `BundledToolTest`: every enum entry and every apps.json action `path` is on disk.
  `ArchitectureRulesTest`: `user.dir` only in `AppPaths`, `"apps/` only in `BundledTool`.
- [x] 3.2 `helpers/SettingsStore` over `acommander.properties`: typed get/set + atomic save (temp file, then move)
  for left/right folder, theme, `last_selection_pattern`, bookmarks and FTP connections (absorbs the old 4.2).
  Password stays plaintext until 10.6. `SettingsStoreTest`: round trip (Hebrew path), missing file, a failed save
  keeps the old file, an unreadable file is moved aside. Fixed on the way (#151): a malformed settings file
  crashed every start (`loadConfigFile` threw), and edits made with the Settings action were overwritten by the
  next save; the file is now reloaded when the editor closes.
- [x] 3.3 Errors and data loss the user never sees (bug fixes; one issue each):
  1. Done (#147). **Data loss on repack failure.** `ArchiveManager.closeArchive` deleted the extracted folder in
     `finally`, even when the repack failed, and every caller only logged. Now the edits are copied to
     `<archive>.recovered-<yyyyMMdd-HHmmss>` next to the archive (home folder as fallback) and the error names it;
     `VfsManager.closeFileSystem` and `FilesPanesHelper.cleanup` stop swallowing, and `Main` shows exit-time
     failures. Deleted the never-called `VFileSystem.repack`.
  2. Done (#148). **Archive open/close/navigate failures were only logged.** `enterArchive`, `exitArchive`,
     `goUpInArchive` now throw into `runWithProgress`. `exitArchive` shows the parent folder itself (before the
     repack), so the two "leave archive, show parent" copies in `Commander` and `goUpInArchive` are gone. Backspace
     inside an archive now runs through `Commander.goUpInArchive` (it repacked on the UI thread before).
  3. Done (#150). **Failed tool runs showed nothing.** Reviewing the `RuntimeException` wraps and ignored catches
     found them harmless (path-parse fallbacks, unwrapped delete lambdas; `loadConfigFile` goes with 3.2). The real
     hole was elsewhere: ~15 fire-and-forget `runExecutable` calls (copy, move, pack, unpack, extract all, view,
     edit, multi rename, wipe/unlock delete, split, every apps.json external action) and `do*` methods that caught
     and logged everything. Now `ACommands.reportFailure` shows them (not after Stop), the `do*` methods throw,
     copy batches report per-item failures, and F4 on FTP/archive names the temp copy when the save-back fails.
     Also fixed: pack/unpack to an FTP or archive pane uploaded with `targetFs.copy(localPath, …)`, which reads
     the local path as remote. `doUnpack` / `doExtractAll` merged into `unpackWith` (was 7.1).
- [x] 3.4 Metadata dialogs take the owner `Window` + theme style class instead of `Commander`. Their 6 copies of
  the theme code and `Commander.applyThemeToDialog`'s became `dialog/DialogTheme.apply`. `ActionContext` and the
  public fields stay: changing them is churn with no payoff, and `ActionRulesSnapshotTest` already covers
  `ActionExecutor` with a mocked `Commander`.
- [x] 3.5 App version from the build (bug fix: Report Bug shows and sends 4.0 while the build is 4.5). Done as
  planned: `processResources` fills `app-version.properties` from `appVersion`, `helpers/AppVersion` reads it,
  `APP_VERSION` deleted, `AppVersionTest` + `ArchitectureRulesTest.versionIsNeverHardCoded` guard it.
  1. `appVersion` in `build.gradle` stays the only place the version is written (it already feeds launch4j and the
     zip name).
  2. `processResources` expands it into `src/main/resources/app-version.properties` (`version=${appVersion}`), so
     `gradlew run`, the jar and the exe all carry it. Not the jar manifest: `gradlew run` has no jar.
  3. `helpers/AppVersion.current()` reads that resource; `"dev"` when it is missing or unexpanded.
  4. Report Bug dialog and `BugReportUrl` use it; delete `APP_VERSION`.
  5. Guards: `AppVersionTest` asserts `AppVersion.current()` equals `appVersion`, which Gradle passes to the test JVM as
     a system property; `ArchitectureRulesTest` bans a version literal assigned to a `*VERSION*` name in
     `src/main/java`.

Smoke items: open an archive, edit a file, make the repack fail (archive read-only on disk) and leave: the warning
names the recovered folder; Report Bug shows the build's version; every tool still starts (F3, F4, F5, F11, F12,
checksum, compare, convert, metadata).

## Phase 4 — Move Logic out of Commander (depends on 3; one commit + tests per item)

Rechecked after Phase 3: every symbol below still exists in `Commander`. Bug fixes first (4.0, 4.7), then moves.

- [x] 4.0 Pack from an archive pane (bug fix, #152). `CommandsAdvancedImpl.doPack` copies each item of a non-local
  source to `AppTempDir.createTempFile("acommander_pack_", "_" + name)`, so the archive entries get the temp name
  (`acommander_pack_123_name`), and it skips folders with only a warn log. Pack is `ftp=no`, but an archive pane is
  not `LocalFileSystem`, so this runs whenever you pack from inside an archive. Fix: copy each item into one
  `AppTempDir.createTempDirectory` under its own name (`VFileSystem.copy` already handles folders) and pack those.
  Test with a mocked source `VFileSystem`: the paths handed to 7-Zip end in the original names, folders included.
- [x] 4.1 `FolderComparer`: `compareFolderTrees`, `collectFolderEntries`, `checksumSha256`, `FolderEntry` /
  `FolderCompareResult` / `FolderCompareMark`. Test: only-left / only-right / different by size, date, checksum.
  Done as `services/FolderComparer` (first class in `services/`, decision 5); the never-hit checksum cache dropped.
- [x] 4.2 Merged into 3.2 (bookmark storage); the bookmark picker moves with the dialogs in Phase 5.
- [x] 4.3 `buildChecksumCommand`, `buildAnalyzeFileCommand`, `buildCompareCommand`, `parseSplitSize` → `tools/`,
  each with a test of the argument list. Done as one `tools/BundledToolCommands` with the option types; a split size
  that overflows `long` is now rejected (it could wrap to a positive number).
- [x] 4.4 `AudioConversionService` (`runAudioConversion*`, staging, AAC bridge), `ImageConversionService`
  (`buildImageConvertCommand` and the run). The option prompts (`promptImageConversionOptions`, …) stay for Phase 5.
  The audio service runs tools through an injected function (`Commander.runExternal`), so its tests use a fake.
- [x] 4.5 Metadata remove runners (`runVideoMetadataDeleteCommand`, `runAudioMetadataDeleteCommand`, the exiv2
  lambda in `removeImageMetadata`) → `*MetadataSupport`. `listAtomicParsleyArtifacts` + cleanup is in both
  `Commander` and `VideoMetadataDialog`; keep one copy in `VideoMetadataSupport`.
- [x] 4.6 `FilePropertiesLauncher` (VBS, moved as-is; hardened in 10.7). In `tools/`; the script is a text block.
- [x] 4.7 Report Bug (bug fix, #153): `submitBugReport` first runs curl (`BundledTool.CURL`) against the new-issue URL,
  ignores the HTTP code it asks for, and opens the browser only if curl exits 0. Offline or behind a proxy curl
  can't pass, Report Bug never opens. Fix: drop the curl call and open the browser directly (the copy-the-URL
  fallback stays). What a check could have caught is a URL GitHub rejects as too long, so `BugReportUrl` caps the
  body to keep the URL under ~8,000 characters and notes the cut. Test: a huge "steps" text still yields a URL
  under the cap.
- [x] 4.8 `ClipboardTransfer` (`ClipboardEntry`, `ClipboardTransferState`, copy/cut/paste). In `services/`, with the
  paste-target helpers F5 shares (`isSameFolder`, `targetInternalPath`, `duplicateName`).
- [x] 4.9 `FileIcons` (`resolveIconSpec`, `IconSpec`, `is*Extension`). In `helpers/`; its extension sets join
  `FileTypes` in 6.1.
- [x] 4.10 `IncrementalFilter` (`filterByChar`, `applyIncrementalFilter`), pure prefix logic tested. In `helpers/`,
  one per pane; the list and selection updates stay in `Commander`.
- [x] 4.11 Theme: `applyTheme`, `ThemeMode` and `applyThemeToDialog` join `dialog/DialogTheme` (3.4) in one theme
  class, not a second one. `ThemeMode.from` gets a test (unknown / blank value → default). `applyTheme` (persists
  the choice) and `applyThemeToDialog` (needs the window) stay in `Commander` as one-liners.
- [x] 4.12 `ExternalProgressController`, the UI side of external runs: `buildExternalCommandListener` (progress,
  Settings-editor reload, the `onFailure` error dialog), `show/hideOrUpdateExternalProgress`, `stopExternalTasks`,
  `runWithProgress`. The process side (`runExecutable`, `reportFailure`, Stop counter) is 7.2. Done in `helpers/`:
  the count, show/hide and the background `run` moved; the listener stays in `Commander` (it reloads settings and
  shows dialogs) and feeds the controller. FTP connect now counts like any other task instead of forcing the bar
  shut.
- [x] 4.13 `ArchitectureRulesTest`: `Commander` has no `Files.walk` / `Files.list` / `MessageDigest` (all leave with
  4.1) and no `java.util.Properties` (already true since 3.2). `new ProcessBuilder` is banned everywhere by 1.7,
  tool paths by 3.1.

Smoke items: F11 inside an archive with a file and a folder selected (entries keep their names), Compare Folders,
checksum, split, convert audio + image, remove metadata, Report Bug opens the browser, copy/cut/paste,
type-to-filter, theme toggle, Stop button on a long copy, a failing tool still shows its error.

## Phase 5 — Move Dialogs out of Commander (depends on 4)

- [x] 5.1 `dialog/OptionsDialog` helper: layout, OK/Cancel, validation, Enter/Escape, theme, owner.
- [x] 5.2 One class per dialog, ~1,640 lines today: FTP connect (inline in `ftpConnect`, 209), image conversion (190),
  audio conversion (175), PDF extract (131), bookmark picker (120), UPX (115), report bug (91), checksum options +
  checksum result, find-in-files options + file results, compare files, compare folders, split size, attributes,
  text prompt (`getUserFeedback` / `promptUser`), select by pattern. Moving a dialog counts as editing it: add the
  missing tooltips and Title Case labels (`ui-text.instructions.md`). Done: 19 classes in `dialog/`; the logic they
  held went to `BundledToolCommands` (checksum digest/path, ripgrep command/results) and
  `ExecutableCompressionSupport` (`UpxAction`, command, percent), with tests.
- [x] 5.3 Changed: no private records are left in `Commander`. The options types are public nested types of the
  class that consumes them (`BundledToolCommands.ChecksumOptions`, `ImageConversionService.ImageConversionRequest`,
  `ExecutableCompressionSupport.UpxAction`, …). Splitting each into its own file adds files and no behaviour.
- [x] 5.4 Target: `Commander` under 4,000 lines (5,380 after Phase 4, which moved ~1,550; Phase 5 moves ~1,700). The
  rest is feature handlers (read selection → validate → run), which Phase 7's services shrink further. Done: 3,632.

Smoke items: open every moved dialog once; Enter confirms, Escape cancels, dark theme applies, tooltips show.

## Phase 6 — Metadata Dialogs and File Types (depends on 1, 3)

Rechecked after Phase 5. Bug fix first (6.0). 6.1 shrank: the real duplication is the "extension of a name" code,
not the sets. 6.3 now runs before 6.2, so the base class is judged on what is left.

- [x] 6.0 Duplicate inside an archive hangs the app (bug fix, #157). `Commander.fileExists` treats
  `ArchiveFileSystem.listContents(path) != null` as "exists"; that call never returns null (it always adds `..`), so
  `ClipboardTransfer.duplicateName` loops forever on the FX thread. Fix: list the target folder once and test the
  name against it, for every file system; delete `fileExists`. Test: `duplicateName` with a taken-set ends. Done:
  `ClipboardTransfer.duplicateName(name, fs, folder)` checks the disk for local and archive panes (an archive pane's
  folder is its extracted temp folder) and one listing for FTP; `paste` lost its namer parameter.
- [x] 6.1 Changed. One `FileItem.extension()` (lower case, `""` when none) replaces ~16 copies of the
  `lastIndexOf('.')` code: `normalizedExtension` in the 5 `*Support` classes and `ActionPriorityEngine`,
  `getFileExtension` in `Commander` and `ArchiveManager`, `ACommands.isArchive/isPdf`, `ActionRegistry` (pdf),
  `FileIcons`, `LocalFileSystem`, `ArchiveFileSystem`. The archive extensions live in 3 places
  (`ArchiveService.SUPPORTED_EXTENSIONS`, `ArchiveMode` read-write / read-only sets, an inline zip/jar/tar/gz check in
  `FtpFileSystem`); keep one source and test that they agree. Each other set stays next to the feature that uses
  it; no `FileTypes` class. `ActionRegistry.areAllOfType` stays (it is one switch). `ActionRulesSnapshotTest` must
  not change. Done: `FileItem.allFilesWithExtension` replaced the six `isX` + `areAllX` copies (each `areAllX` is
  one line). `ArchiveService` became `ArchiveMode.isUnpackable` (browsable + unpack-only), which fixed #158: Unpack
  refused txz, tbz2, 001, vhdx and others that Enter opens. `VFileSystem.isVirtualFolder/enterVirtualFolder` were
  dead (only local panes enter archives; nobody called `enterVirtualFolder`) and held the FTP list; deleted, with
  `VfsManager.openArchive` in their place. Also deleted the unused `ImageMetadataSupport.getSupportedFormatsDescription`.
- [x] 6.3 Output parsers as pure functions in the `*MetadataSupport` classes, tested on captured sample output:
  exiv2 (now inside `ImageMetadataDialog.populateTreeTable`, mixed with the tree), id3 (inside
  `AudioMetadataDialog.queryAllTagValues`, mixed with the process run), AtomicParsley (`VideoMetadataDialog`
  `parseTextData` / `mapAtomToKey`, already pure, untested). Done: `ImageMetadataSupport.parsePrintAll` /
  `displayValue` / `groupName`, `AudioMetadataSupport.parseQuery` (+ `QUERY_FORMAT`, `QUERY_KEYS`),
  `VideoMetadataSupport.parseTextData` (atom → field map). exiv2 and id3 tests use output captured from the bundled
  exes; AtomicParsley's uses its documented format (no mp4 to capture from). The image dialog's second copy of the
  tree builder went too: `populateTreeTable` fills the entries and calls `rebuildTreeTable`. Dialogs now: image
  1,049, audio 475, video 341.
- [x] 6.2 Shared metadata dialog code, sized after 6.3. Today: image 1,148 lines (tree table), audio 509, video 398
  (forms). Shared: load in the background, disable controls, status line, Reload/Save, error dialog. Build audio
  and video on `OptionsDialog` if it fits; a `MetadataDialogBase` only if more than ~100 lines stay duplicated.
  Done: `OptionsDialog` doesn't fit (it closes on OK; these stay open to save and reload). ~150 lines per dialog
  were the same, so `dialog/MetadataFormDialog` holds the form (a field list, extra rows, Preserve File Time,
  Reload/Save, status, errors) and a `Tool` (read / writeCommand / write). Audio is now 71 lines, video 48, the
  form 195 (were 475 + 341). The tool runs moved to `Audio/VideoMetadataSupport.read/writeCommand/write` (tested);
  id3's code-page check now covers every argument but the exe path, so a path it can't open fails with that
  message. Every control got a tooltip. The image dialog keeps its own tree layout; it got a static `show`, and
  `Commander`'s three edit handlers became one `editMetadata`.

Smoke items: Duplicate inside a zip; edit + save + reload metadata on a copied jpg, mp3, mp4.

## Phase 7 — Commands Layer (depends on 1, 3)

Rechecked after Phase 6 (gate). Order: 7.4, 7.2, then 7.1 in three commits (PDF, archive, files); 7.3 falls out of
7.1. Changed: three services instead of six, and the runner keeps today's API (see 7.2). Found: PDF merge / extract
failures are only logged, and a merge into an FTP or archive pane writes to the wrong place (fixed in 7.1a).

- [x] 7.1 Replace `ACommands` (374) / `CommandsAdvancedImpl` (1,122) / `CommandsSimpleImpl` (369) with three
  `services/` classes: (a) `PdfOperations` (merge, extract pages, page count; `parsePageExpression` tested),
  (b) `ArchiveOperations` (pack, unpack / extract all = `unpackWith`), (c) `FileOperations` (rename, copy / move and
  their batches, delete / wipe / unlock, new folder / file, view / edit, terminal / explorer). Keep what 3.3 built:
  services throw, copy batches name every failed item, `verifyBatchCopy` stays, `unpackWith` stays one method.
  No JavaFX in services: `FilesPanesHelper.refreshFileListViews` posts itself to the FX thread, so the
  `Platform.runLater` wrappers go. Search by name (F10) runs a PowerShell command and shows its own copy of the
  found-files dialog in `CommandsSimpleImpl`; the command moves to `tools/BundledToolCommands` and `Commander` shows
  the existing `FoundFilesDialog`, like Find in Files. `PdfExtractOptions` moves to `services/`.
  Done in one commit, not three: removing the PDF methods from the abstract class meant editing the classes 7.1c
  deletes anyway. `FileOperations` keeps the old public API, so `Commander` changed little. Search by name now runs
  ripgrep (`BundledToolCommands.findByName`), not PowerShell, and shares `pickFoundFile` with Find in Files. Fixed on
  the way: #159 (PDF merge / extract failures silent, merge into an FTP pane saved locally) and #160 (Search showed
  nothing when a subfolder was unreadable; Find in Files showed an error when nothing matched). `AppRegistry` got
  `requireAction`. `ArchitectureRulesTest` now also keeps JavaFX out of `services/` and `commands/`.
- [x] 7.2 Changed: `commands/ExternalToolRunner` (next to its listener and exception) owns `runExecutable`,
  `reportFailure`, the Stop counter and `ProcessRunner.trackIn`, under the same names. Dropped "reporting by
  default": callers chain follow-up steps (upload, verify, cleanup) whose failure must reach the user with the
  tool's, and one `reportFailure` on the chain already does that. It takes a `Runnable` to run when a tool changed
  files instead of `FilesPanesHelper`, so it has no JavaFX. One runner is shared: today the advanced commands and
  their inner simple commands each have their own Stop counter and process set. `ArchitectureRulesTest` also catches
  a qualified `runner.runExecutable(...);`. `ReportFailureTest` becomes `ExternalToolRunnerTest`. Done; `Commander`
  owns the runner and passes it to the commands (the `ACommands.runExecutable` wrappers live until 7.1).
- [x] 7.3 Delete the 8 "Not implemented" stubs; move tests with the code (`CommanderCopyTest` builds a
  `CommandsSimpleImpl`). Falls out of 7.1: the stubs exist only to fill the abstract class. Done with 7.1; the tests
  are `FileOperationsTest`, `ArchiveOperationsTest`, `PdfOperationsTest`.
- [x] 7.4 `LocalFileSystem.copyDirectory` and `ArchiveFileSystem.copyDirectory` are the same method; keep one.
  Done: both call `FileHelper.copyTree(source, target, REPLACE_EXISTING)`; the archive recovery passes
  `COPY_ATTRIBUTES`, as before.

Smoke items: the full checklist, on local, archive and (if available) FTP panes.

## Phase 8 — FilesPanesHelper Split (depends on 7)

Rechecked after Phase 5: `FilesPanesHelper` is 848 lines, unchanged. Do 8.2 and the pure `PaneSorter` (already tested
by `FilesPanesHelperNaturalSortTest`); split archive navigation and selection only if Phase 7 or 9 needs it.

- [ ] 8.1 `PaneSorter` (`SortState`, `SortColumn`, `compareNaturalNames`), `ArchiveNavigator`, selection helpers.
- [ ] 8.2 Top-level `FilePane`, `ArchiveFolder`, `ArchiveParentItem`.

Smoke items: sort by each header, enter/leave nested archive folders, select by pattern, invert selection.

## Phase 9 — File → Path (depends on 8)

- [x] 9.1 Decided by the code: FTP items already have `file == null` (name only, path from the pane), so `FileItem`
  can hold a nullable `Path` for local and archive items.
- [ ] 9.2 `FileItem` holds `Path`; temporary `getFile()`; migrate the 29 `getFile()` call sites (rechecked after
  Phase 5: `Commander` 10, `CommandsAdvancedImpl` 8, `FtpFileSystem` 7, `FileHelper` 2, `CommandsSimpleImpl` 1,
  `FilesPanesHelper` 1; the commands ones move with Phase 7); delete `getFile()`.

Smoke items: the full checklist, plus Hebrew file and folder names.

## Phase 10 — Security (last)

Gate: re-grep every shell-out and every log call that prints a command; list anything earlier phases added. Order is
by severity. File each issue in the same step as its fix (decision 7).

Rechecked after Phase 5: 10.1–10.7 still apply as written; no new item. Open question for 10.6: decision 1 adds
`jna-platform` (not in `build.gradle` yet). Asking for the password on each connect needs no dependency.

- [ ] 10.1 Passwords in logs (live leak). `FtpFileSystem.runCurl` redacts its own debug log (`obfuscateCommand`) but
  hands the raw curl command, `-u user:pass` included, to the `ExternalCommandListener`, and the listener (4.12)
  logs `String.join(" ", command)` whenever a command fails. `runExecutable` (7.2) logs every command at debug, and
  since 3.3 `ExternalCommandException`'s message (the full command) is shown in the error dialog. Fix: move
  `obfuscateCommand` to `tools/CommandLog.redact(command)` and use it for every log line, exception message and
  listener call that carries a command. Test: no logged or shown form of an FTP command contains the password. Then
  delete the old `logs/*.log` files that may hold one. Also `@ToString.Exclude` the password in
  `FtpConnectionOptions` (Lombok `@Data` prints it).
- [ ] 10.2 FTP credentials on the command line (visible in Task Manager): pass `user:password` to curl via
  `--config -` on stdin (add stdin to `ProcessRunner`), not `-u`. Guard remote names starting with `-`.
- [ ] 10.3 PowerShell injection. `FileOperations.openTerminal` (F9; was `CommandsSimpleImpl`) puts the folder in
  `-Command "cd '<path>'"` without escaping `'`, so a folder named `a';calc;'` runs calc. Start the terminal in the
  folder (process working dir) instead of building a command string. Same review for `openHostsFile`
  (`Start-Process` string built from apps.json). `searchFiles` is gone (7.1: ripgrep with list arguments).
  `ComboBoxSetup`'s script has no user input. Test with such a folder in a temp dir.
- [ ] 10.4 `cmd.exe` with file names. `enterSelectedItem` runs `.bat` / `.cmd` as `cmd.exe /c <path>`, and
  `openFileWithSystemDefault` falls back to `cmd.exe /c start "" "<path>"`; `&`, `^`, `%VAR%` in a name are
  interpreted. Use `Desktop.open` / ShellExecute. Test with `a&b %PATH%.bat` in a temp folder.
- [ ] 10.5 FTP names from the server: temp files are safe (`Files.createTempFile` rejects separators in the suffix);
  the risk is FTP → local copy / move / paste, which joins the server's name onto the target folder. Reject names
  with `..`, `/`, `\`, `:`. Test the check.
- [ ] 10.6 FTP password at rest: DPAPI (`jna-platform` `Crypt32Util`); migrate plaintext on load and remove the old
  key; decrypt failure → ask for the password.
- [ ] 10.7 File Properties VBS: a static script resource, path passed as an argument only, deleted after the dialog
  closes. Also: the script sleeps forever to keep the dialog open, so every Alt+Enter leaves a `wscript.exe` running
  until logoff; end it when the Properties window closes.
- [x] 10.8 Bug report URL parameters are encoded (`BugReportUrl`, #144; labels are constants).
- [ ] 10.9 `ArchitectureRulesTest` locks the above: no `"cmd.exe", "/c"` with a path, no `"-u"` for curl, no command
  logged or put in an exception message without `CommandLog.redact`.

Smoke items: FTP connect, browse, copy both ways, a failing FTP command (wrong password) — then check `logs/` holds
no password; F9 in a folder named `a';calc;'`; Enter on `a&b.bat`; File Properties.

## Found Along the Way

Bugs noticed during the work, fixed in the phase named.

1. Fixed (#145): `duplicate` is not blocked on a read-only archive (`writes=none`); it writes into the focused folder,
   so it should be `source`.
2. Fixed (#145): `wipeDelete` (external SDelete) is not blocked on a read-only archive; should be `source` like
   `deleteWipe`.
3. Fixed (#145): `editImageMetadata` is allowed on FTP but `removeImageMetadata` and the audio/video editors are not.
   The dialog runs `exiv2` on a local path, so it should be `ftp=no`.
4. Kept: `syncToOtherPane` stays blocked on FTP. Its FTP branch is unfinished (a page of open questions in comments);
   enabling it is a feature, not a refactor.
5. Fixed (#146): mouse clicks on the F-key buttons call `Commander` directly and skip both gates.
6. Fixed (#149): bug reports say version 4.0 (`APP_VERSION`) while the build is 4.5.
7. Fixed (#153): Report Bug's unchecked curl request blocks the browser when curl can't connect.
8. Fixed (#148): a failure to open or close an archive is logged but never shown.
9. Phase 10.1 (security, so last by the user's rule): failing FTP commands log the password.
10. Fixed (#147): a failed repack deletes the user's edits inside the archive (data loss).
11. Fixed (#150): failed tool runs (copy, move, pack, unpack, view, edit, …) show nothing; F4 on FTP loses the
    edit silently when the upload fails.
12. Fixed (#150): pack/unpack to an FTP or archive pane uploaded with `targetFs.copy(localPath, …)`.
13. Fixed (#151): a malformed settings file crashes every start; Settings edits are overwritten by the next save.
14. Fixed (#152): packing from inside an archive names the entries after their temp copies and drops folders.
15. Fixed (#154): FTP connect saved the connection when Save was off (checkbox read before the dialog showed),
    replaced a saved custom port with the protocol default, and accepted any port.
16. Fixed (#155): Select by Pattern threw on an invalid regex or a blank pattern with regex on.
17. Fixed (#156): file sizes failed to parse in comma-decimal locales (`Double.parseDouble` of a formatted size).
18. Fixed (#157): Duplicate inside an archive loops forever on the FX thread (`Commander.fileExists` is always true
    for an archive).
19. Fixed (#158): Unpack refused archives that Enter opens (two extension lists).
20. Fixed (#159): PDF merge / extract failures were only logged; a merge into an FTP pane was saved locally.
21. Fixed (#160): Search showed nothing when a subfolder was unreadable; Find in Files showed an error when nothing
    matched (ripgrep exits 1 / 2).
