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

`Commander` line count: 7459 (start), 7199 (after Phase 1), 7038 (after Phase 2).

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
- [ ] 3.2 `SettingsStore` over `acommander.properties`: typed get/set + atomic save (temp file, then move) for
  left/right folder, theme, `last_selection_pattern`, bookmarks and FTP connections (absorbs the old 4.2). Password
  stays plaintext until 10.6. Test on a temp dir: round trip, and an interrupted save keeps the old file.
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
- [ ] 3.4 Metadata dialogs take the owner `Window` + theme instead of `Commander` (they use only `rootPane` and
  `getCurrentThemeMode`). `ActionContext` and the public fields stay: changing them is churn with no payoff, and
  `ActionRulesSnapshotTest` already covers `ActionExecutor` with a mocked `Commander`.
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

- [ ] 4.1 `FolderComparer`: `compareFolderTrees`, `collectFolderEntries`, `checksumSha256`, `FolderEntry` /
  `FolderCompareResult` / `FolderCompareMark`. Test: only-left / only-right / different by size, date, checksum.
- [x] 4.2 Merged into 3.2 (bookmark storage); the bookmark picker moves with the dialogs in Phase 5.
- [ ] 4.3 `buildChecksumCommand`, `buildAnalyzeFileCommand`, `buildCompareCommand`, `parseSplitSize` → `tools/`,
  each with a test of the argument list.
- [ ] 4.4 `AudioConversionService` (`runAudioConversion*`, staging, AAC bridge), `ImageConversionService`.
- [ ] 4.5 Metadata remove runners (`runVideoMetadataDeleteCommand`, `runAudioMetadataDeleteCommand`, the exiv2
  lambda in `removeImageMetadata`) → `*MetadataSupport`. Merge the AtomicParsley artifact cleanup that is duplicated
  in `Commander` and `VideoMetadataDialog`.
- [ ] 4.6 `FilePropertiesLauncher` (VBS, moved as-is; hardened in 10.7).
- [ ] 4.7 Report Bug (bug fix): `submitBugReport` first runs curl against the new-issue URL, ignores the HTTP code it
  asks for, and opens the browser only if curl exits 0. Offline or behind a proxy curl can't pass, Report Bug never
  opens. Fix: drop the curl call and open the browser directly (the copy-the-URL fallback stays). What a check could
  have caught is a URL GitHub rejects as too long, so `BugReportUrl` caps the body to keep the URL under ~8,000
  characters and notes the cut. Test: a huge "steps" text still yields a URL under the cap.
- [ ] 4.8 `ClipboardTransfer` (`ClipboardEntry`, `ClipboardTransferState`, copy/cut/paste).
- [ ] 4.9 `FileIcons` (`resolveIconSpec`, `IconSpec`, `is*Extension`).
- [ ] 4.10 `IncrementalFilter` (`filterByChar`, `applyIncrementalFilter`), pure prefix logic tested.
- [ ] 4.11 `ThemeManager` (`applyTheme`, `ThemeMode`, `applyThemeToDialog`).
- [ ] 4.12 `ExternalProgressController` (`buildExternalCommandListener`, `show/hideOrUpdateExternalProgress`,
  `stopExternalTasks`, `runWithProgress`).
- [ ] 4.13 `ArchitectureRulesTest`: `Commander` has no `Files.walk` / `Files.list`, `Properties` access or
  `MessageDigest`. (`new ProcessBuilder` is banned everywhere by 1.7, tool paths by 3.1.)

Smoke items: Compare Folders, checksum, split, convert audio + image, remove metadata, Report Bug opens the browser,
copy/cut/paste, type-to-filter, theme toggle, Stop button on a long copy.

## Phase 5 — Move Dialogs out of Commander (depends on 4)

- [ ] 5.1 `dialog/OptionsDialog` helper: layout, OK/Cancel, validation, Enter/Escape, theme, owner.
- [ ] 5.2 One class per dialog, ~1,640 lines today: FTP connect (inline in `ftpConnect`, 209), image conversion (190),
  audio conversion (175), PDF extract (131), bookmark picker (120), UPX (115), report bug (91), checksum options +
  checksum result, find-in-files options + file results, compare files, compare folders, split size, attributes,
  text prompt (`getUserFeedback` / `promptUser`), select by pattern. Moving a dialog counts as editing it: add the
  missing tooltips and Title Case labels (`ui-text.instructions.md`).
- [ ] 5.3 Options records (`SplitSize`, `ChecksumOptions`, `CompareFilesOptions`, `ImageConversionRequest`, …)
  top-level next to their service.
- [ ] 5.4 Target: `Commander` under 4,000 lines (7038 now; Phases 4-5 move ~3,000). The rest is feature handlers
  (read selection → validate → run), which Phase 7's services shrink further. Was 2,500, which the sizing doesn't
  support.

Smoke items: open every moved dialog once; Enter confirms, Escape cancels, dark theme applies, tooltips show.

## Phase 6 — Metadata Dialogs and File Types (depends on 1, 3)

- [ ] 6.1 `FileTypes` owns the extension sets now spread over `ImageConversionSupport`, `AudioConversionSupport`,
  `Image/Video/AudioMetadataSupport`, `ExecutableCompressionSupport`, `ArchiveService`, `ACommands.isArchive/isPdf`
  and `Commander.getFileExtension/normalizedExtension`. `ActionRegistry.areAllOfType` moves there. Target-format
  lists stay with the conversion services (4.4). `ActionRulesSnapshotTest` must not change.
- [ ] 6.2 `MetadataDialogBase`; image (1,189 lines) / audio (544) / video (477) become adapters.
- [ ] 6.3 Output parsers (exiv2, AtomicParsley, id3) as pure functions, tested on captured sample output.

Smoke items: edit + save + reload metadata on a copied jpg, mp3, mp4.

## Phase 7 — Commands Layer (depends on 1, 3)

- [ ] 7.1 Replace `ACommands` / `CommandsAdvancedImpl` (1,248) / `CommandsSimpleImpl` with `CopyMoveService`,
  `DeleteService`, `ArchiveOps`, `PdfService`, `ShellService`, `ViewEditService`. (`doUnpack` / `doExtractAll`
  already merged in 3.3.)
- [ ] 7.2 `ExternalToolRunner` (run, listener, stop).
- [ ] 7.3 Delete the "Not implemented yet" stubs; move tests with the code (`CommanderCopyTest` builds a
  `CommandsSimpleImpl`).
- [ ] 7.4 `LocalFileSystem.copyDirectory` and `ArchiveFileSystem.copyDirectory` are the same method; keep one.

Smoke items: the full checklist, on local, archive and (if available) FTP panes.

## Phase 8 — FilesPanesHelper Split (depends on 7)

- [ ] 8.1 `PaneSorter` (`SortState`, `SortColumn`, `compareNaturalNames`), `ArchiveNavigator`, selection helpers.
- [ ] 8.2 Top-level `FilePane`, `ArchiveFolder`, `ArchiveParentItem`.

Smoke items: sort by each header, enter/leave nested archive folders, select by pattern, invert selection.

## Phase 9 — File → Path (depends on 8)

- [x] 9.1 Decided by the code: FTP items already have `file == null` (name only, path from the pane), so `FileItem`
  can hold a nullable `Path` for local and archive items.
- [ ] 9.2 `FileItem` holds `Path`; temporary `getFile()`; migrate the ~30 `getFile()` call sites (`vfs/`, services,
  `Commander`); delete `getFile()`.

Smoke items: the full checklist, plus Hebrew file and folder names.

## Phase 10 — Security (last)

Gate: re-grep every shell-out and every log call that prints a command; list anything earlier phases added. Order is
by severity. File each issue in the same step as its fix (decision 7).

- [ ] 10.1 Passwords in logs (live leak). `FtpFileSystem.runCurl` hands the curl command, `-u user:pass` included, to
  the `ExternalCommandListener`, and `Commander`'s listener logs `String.join(" ", command)` whenever a command fails;
  `ACommands.runExecutable` also logs every command at debug. Fix: one `CommandLog.redact(command)` used by every log
  of a command; the FTP listener gets the redacted form. Test: no logged form of an FTP command contains the
  password. Then delete the old `logs/*.log` files that may hold one.
- [ ] 10.2 FTP credentials on the command line (visible in Task Manager): pass `user:password` to curl via
  `--config -` on stdin (add stdin to `ProcessRunner`), not `-u`. Guard remote names starting with `-`.
- [ ] 10.3 PowerShell injection. `CommandsSimpleImpl.openTerminal` (F9) puts the folder in
  `-Command "cd '<path>'"` without escaping `'`, so a folder named `a';calc;'` runs calc. Start the terminal in the
  folder (process working dir) instead of building a command string. Same review for `searchFiles` (escapes `'`,
  still string-built: pass the values as script arguments) and `openHostsFile` (`Start-Process` string built from
  apps.json). `ComboBoxSetup`'s script has no user input. Test with such a folder in a temp dir.
- [ ] 10.4 `cmd.exe` with file names. `enterSelectedItem` runs `.bat` / `.cmd` as `cmd.exe /c <path>`, and
  `openFileWithSystemDefault` falls back to `cmd.exe /c start "" "<path>"`; `&`, `^`, `%VAR%` in a name are
  interpreted. Use `Desktop.open` / ShellExecute. Test with `a&b %PATH%.bat` in a temp folder.
- [ ] 10.5 FTP names from the server: temp files are safe (`Files.createTempFile` rejects separators in the suffix);
  the risk is FTP → local copy / move / paste, which joins the server's name onto the target folder. Reject names
  with `..`, `/`, `\`, `:`. Test the check.
- [ ] 10.6 FTP password at rest: DPAPI (`jna-platform` `Crypt32Util`); migrate plaintext on load and remove the old
  key; decrypt failure → ask for the password.
- [ ] 10.7 File Properties VBS: a static script resource, path passed as an argument only, deleted after the dialog
  closes.
- [x] 10.8 Bug report URL parameters are encoded (`BugReportUrl`, #144; labels are constants).
- [ ] 10.9 `ArchitectureRulesTest` locks the above: no `"cmd.exe", "/c"` with a path, no `"-u"` for curl, no command
  logged without `CommandLog.redact`.

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
6. Phase 3.5: bug reports say version 4.0 (`APP_VERSION`) while the build is 4.5.
7. Phase 4.7: Report Bug's unchecked curl request blocks the browser when curl can't connect.
8. Phase 3.3: a failure to open or close an archive is logged but never shown.
9. Phase 10.1 (security, so last by the user's rule): failing FTP commands log the password.
10. Phase 3.3: a failed repack deletes the user's edits inside the archive (data loss).
