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

`Commander` line count: 7459 (start).

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
- [ ] 1.2 `BackgroundTasks`: one app executor (virtual threads), shut down in `Main` on close. Replaces
  `CompletableFuture.runAsync` without an executor and `newCachedThreadPool` in the three metadata dialogs.
- [ ] 1.3 Move every `ProcessBuilder` site to `ProcessRunner` (Commander, `ACommands.runExecutable`, 3 dialogs,
  `ArchiveManager`, `ComboBoxSetup`, `FileAttributesHelper`, `FtpFileSystem` x2). Args stay identical. Fixes the
  `waitFor`-without-drain deadlock and the relative `"apps/..."` exe paths in the metadata delete runners.
- [ ] 1.4 Off the FX thread: `calculateDirSpace`, `compareFolders` entry collection, metadata remove runners.
- [ ] 1.5 Close every `Files.walk` / `Files.list` (Commander `calculateDirSpace` + audio staging,
  `CommandsAdvancedImpl`, `CommandsSimpleImpl`, `ArchiveSession`).
- [ ] 1.6 `AppTempDir` (`%TEMP%/acommander-<pid>`, deleted on exit, stale roots removed at startup) + per-operation
  `TempWorkspace`. Replace ~20 `deleteOnExit`.
- [ ] 1.7 Create `ArchitectureRulesTest` (source-text scan like `CodeMapTest`): no `new ProcessBuilder` outside
  `ProcessRunner`; no `deleteOnExit`; no `newCachedThreadPool`.

## Phase 2 — Action Rules in apps.json (depends on 0)

- [ ] 2.1 `ActionDefinition` fields: `ftp` (bool), `writes` (`none|source|target|both`), `fileTypes` (list).
  `AppConfigLoader` rejects unknown values.
- [ ] 2.2 Fill `apps.json` from the Phase 0 snapshot; snapshot test green before deleting old code.
- [ ] 2.3 Delete `isActionSupportedOnFtp`, `isConditionalWriteBlocked`, `ActionMutator`, id checks in
  `ActionRegistry.isSelectionAllowedForBuiltin`. Snapshot test becomes normal rule tests.
- [ ] 2.4 `BuiltinAction` enum + exhaustive switch in `Commander`. Test: every `apps.json` builtin resolves.
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
- [ ] 4.5 Metadata remove runners → `*MetadataSupport`
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

1. Phase 2: `duplicate` is not blocked on a read-only archive (`writes=none`); it writes into the focused folder,
   so it should be `source`.
2. Phase 2: `wipeDelete` (external SDelete) is not blocked on a read-only archive; should be `source` like
   `deleteWipe`.
3. Phase 2: `editImageMetadata` is allowed on FTP but `removeImageMetadata` and the audio/video editors are not.
   The dialog runs `exiv2` on a local path, so it should be `ftp=no`.
4. Phase 2: `syncToOtherPane` is blocked on FTP only because the FTP list checks builtin `syncOtherPane`, which is
   missing from it. Decide on purpose.
5. Phase 2 (step 2.5): mouse clicks on the F-key buttons call `Commander` directly and skip both gates.
