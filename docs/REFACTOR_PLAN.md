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
3. Restart the app (`gradlew run`). Hand the user the smoke checklist.
4. Push.

Smoke checklist (user, inside a temp folder only): navigate + Backspace; F3 on a file and on a folder (size); F4;
F5/F6 to the other pane; Alt+F6; F7/Alt+F7; F8; F11 pack + F12 unpack; Enter an archive, edit, leave (repack);
Command Palette; theme toggle; one metadata edit on a copied jpg/mp3/mp4; checksum; compare files; FTP connect if a
server is available.

`Commander` line count: 7459 (start), 7199 (after Phase 1).

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
- [ ] 2.5 F-key mouse buttons go through `ActionExecutor` (fixes: mouse clicks skip FTP and read-only gates). Labels
  and actions from `apps.json` shortcuts.
- [ ] 2.6 Docs: CODEMAP §2, README "Fields" table, AGENTS "Adding an action".

## Phase 3 — Shared Infrastructure (depends on 1, 2)

- [ ] 3.1 `AppPaths` + `ToolLocator` + `tools` map in `apps.json`; replace ~17 `Paths.get(user.dir, "apps", ...)`.
  Test: every tool/action path exists on disk.
- [ ] 3.2 `SettingsStore` over `acommander.properties`: typed access, atomic save.
- [ ] 3.3 `UiFeedback` interface; replace swallowed / rethrown exceptions in UI handlers.
- [ ] 3.4 `CommanderContext` narrow interface for actions, palette, key handlers. Delete `ActionContext`. Make
  `Commander` fields private. `ActionExecutor` Mockito tests.
- [ ] 3.5 App version from the jar manifest; delete `APP_VERSION = "4.0"` (build says 4.5).

## Phase 4 — Move Logic out of Commander (depends on 3; one commit + tests per item)

- [ ] 4.1 `FolderComparer`
- [ ] 4.2 `BookmarkService`
- [ ] 4.3 Checksum / analyze / compare / split command builders → `tools/`
- [ ] 4.4 `AudioConversionService`, `ImageConversionService`
- [ ] 4.5 Metadata remove runners (`runVideoMetadataDeleteCommand`, `runAudioMetadataDeleteCommand`, the exiv2
  lambda in `removeImageMetadata`) → `*MetadataSupport`. Merge the AtomicParsley artifact cleanup that is duplicated
  in `Commander` and `VideoMetadataDialog`.
- [ ] 4.6 `FilePropertiesLauncher` (VBS, moved as-is)
- [ ] 4.7 `BugReportService` (moved as-is)
- [ ] 4.8 `ClipboardTransfer`
- [ ] 4.9 `FileIcons`
- [ ] 4.10 `IncrementalFilter`
- [ ] 4.11 `ThemeManager`
- [ ] 4.12 `ExternalProgressController`
- [ ] 4.13 `ArchitectureRulesTest`: `Commander` has no `ProcessBuilder`, `Files.walk`, `Properties`, `MessageDigest`.

## Phase 5 — Move Dialogs out of Commander (depends on 4)

- [ ] 5.1 `dialog/OptionsDialog` helper: layout, OK/Cancel, validation, Enter/Escape, theme, owner.
- [ ] 5.2 One class per prompt returning `Optional<Options>` (conversion, checksum, compare, split, PDF, attributes,
  UPX, FTP connect, bookmarks, text prompt, find results, select by pattern).
- [ ] 5.3 Options records top-level next to their service.
- [ ] 5.4 Target: `Commander` < 2,500 lines.

## Phase 6 — Metadata Dialogs and File Types (depends on 1, 3)

- [ ] 6.1 `FileTypes` replaces the 7 extension-check classes.
- [ ] 6.2 `MetadataDialogBase`; image / audio / video become adapters.
- [ ] 6.3 Output parsers as pure functions with tests.

## Phase 7 — Commands Layer (depends on 1, 3)

- [ ] 7.1 Replace `ACommands` / `CommandsAdvancedImpl` / `CommandsSimpleImpl` with `CopyMoveService`,
  `DeleteService`, `ArchiveOps`, `PdfService`, `ShellService`, `ViewEditService`.
- [ ] 7.2 `ExternalToolRunner` (run, listener, stop).
- [ ] 7.3 Delete the "Not implemented yet" stubs; move tests with the code.
- [ ] 7.4 `LocalFileSystem.copyDirectory` and `ArchiveFileSystem.copyDirectory` are the same method; keep one.

## Phase 8 — FilesPanesHelper Split (depends on 7)

- [ ] 8.1 `PaneSorter`, `ArchiveNavigator`, selection helpers.
- [ ] 8.2 Top-level `FilePane`, `ArchiveFolder`, `ArchiveParentItem`.

## Phase 9 — File → Path (depends on 8)

- [ ] 9.1 Decide how FTP remote paths are represented (`Path` would mangle `/`).
- [ ] 9.2 `FileItem` holds the new type; temporary `getFile()`; migrate `vfs/`, services, `Commander`; delete
  `getFile()`.

## Phase 10 — Security (last)

Gate: re-grep every shell-out; list any new ones added by earlier phases.

- [ ] 10.1 Open files and `.bat` with `Desktop.open` / ShellExecute, not `cmd /c`. Test with `a&b %PATH%.bat`.
- [ ] 10.2 PowerShell sites: user values as arguments, never inside `-Command` text.
- [ ] 10.3 FTP credentials to curl via `--config -` on stdin, not `-u`. Guard remote names starting with `-`.
- [ ] 10.4 FTP downloads reject server names with `..`, `/`, `\`, `:`.
- [ ] 10.5 FTP password at rest: DPAPI; migrate plaintext on load; decrypt failure → ask.
- [ ] 10.6 No passwords or credential-bearing command lines in logs.
- [ ] 10.7 File Properties VBS: static resource, path as argument, deleted after run.
- [ ] 10.8 Bug report: every URL parameter encoded.
- [ ] 10.9 `ArchitectureRulesTest` locks the above.

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
5. Phase 2 (step 2.5): mouse clicks on the F-key buttons call `Commander` directly and skip both gates.
