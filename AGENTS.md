# AGENTS.md

JavaFX 21 dual-pane file manager for Windows (Gradle, Lombok, SLF4J/Logback, Jackson). Features, `config/apps.json`
schema, placeholders and shortcuts are in [README.md](README.md) — link there, don't duplicate.

## Working with the user

- Write in simple, short, direct sentences. Use numbered lists for several points. No intros or wrap-ups.
- Never hand the user a command to run. Run one-time commands yourself and report the result. A need that recurs
  gets a feature in the app (an `apps.json` action / Command Palette entry), not documented CLI steps.
- If the app is running (`gradlew run`) when you finish a change, restart it so the user never tests stale code.
- Clean up dead or broken code you come across in the same turn — don't just report it. First check what it still
  provides that live code lacks, and keep that.
- Try file operations only on files you created in a temp folder. The user works in the app while you work; never
  move, delete or rewrite their files to test something.

## Writing code (ponytail)

Lazy senior dev mode, from [ponytail](https://github.com/DietrichGebert/ponytail). The best code is the code never
written. Read the task and the code it touches and trace the real flow first, then stop at the first rung that holds:

1. Does it need to exist at all? (YAGNI)
2. Does this codebase already have it? Reuse the helper or pattern.
3. Does the standard library do it?
4. Does a native platform (JDK/JavaFX) feature do it?
5. Does an already-used dependency do it?
6. Can it be one line?
7. Only then: the minimum code that works.

- Bug fix = root cause. Grep every caller and fix the shared function once, not each path the report names.
- No unrequested abstractions, new dependencies or boilerplate. Prefer deletion, boring code, fewest files.
- Question complex requests: "Do you need X, or does Y cover it?"
- Mark a deliberate corner-cut with a known ceiling (O(n²) scan, global lock, naive heuristic) with a `ponytail:`
  comment naming the ceiling and the upgrade path.
- Never lazy about: understanding the problem, input validation at trust boundaries, error handling that prevents
  data loss, security, accessibility, anything explicitly requested.
- Upstream asks for one small check per non-trivial logic. Here that is a JUnit test (see Commands); UI is not tested.

## Commit and push at the end of every task

When a task changed files, finish by committing and pushing to `origin/main` without asking.

1. User-visible change (action, shortcut, feature, bundled tool, behaviour; added, changed or removed) → update
   [config/f1-help.html](config/f1-help.html) and [README.md](README.md) in the same commit. `DocsShortcutsTest`
   fails the build if an `apps.json` shortcut is missing from either; features and descriptions are on you.
2. Code changes: `.\gradlew.bat build` must pass first. If it fails, fix it; don't commit a broken build.
3. Commit only the files you changed, by path: `git add <new files>` then `git commit -m "..." -- <file> <file>`
   (a commit pathspec rejects untracked files). Other agent sessions may have uncommitted work in the same tree;
   `git add -A` / `git commit -a` would sweep it in.
4. Message: a short imperative subject (≤ 72 chars) saying what changed, e.g.
   `Add tooltip to Stop button; fix PDF page range parsing`. Add a body with `-m` bullets only if there are
   several unrelated changes.
5. Never commit gitignored or runtime files (`config/acommander.properties`, `UniExtract.ini`, Eclipse files).
6. If the push is rejected, `git pull --rebase` then push again. Never `--force`.
7. Bug fix or user-visible feature → link a GitHub issue (`gh`, repo `Chaiavi/acommander`). Search first:
   `gh issue list --search "<words>"`. No match → `gh issue create --label bug|enhancement --milestone
   "v5.0 Perfect NC Clone"`, title = the symptom or feature, body = root cause and fix. Put `(fixes #n)` in the
   commit subject so the push to `main` closes it. Refactors, docs-only and agent-file changes get no issue.
8. Report the commit hash (and issue number) in the final reply.

## Where project knowledge lives

| File | Loaded |
|---|---|
| `AGENTS.md` (this file) | Always |
| [docs/CODEMAP.md](docs/CODEMAP.md) | Before searching code: key→action flow, action id → `Commander` method → tool, every class in one line, `Commander` layout |
| [ui-logic.instructions.md](.github/instructions/ui-logic.instructions.md) | Editing `Commander.java`, `dialog/**`, `palette/**` |
| [ui-text.instructions.md](.github/instructions/ui-text.instructions.md) | Editing UI classes, `*.fxml`, `config/apps.json` |

At the end of every task ask: "is anything I just did reusable?" Answer in one line. If yes, capture it in the same
turn, in the cheapest place that fits:

1. Checkable pass/fail → a test or a Gradle task.
2. Always true in this repo (command, convention, pitfall) → this file.
3. Matters only while editing certain files → a `.github/instructions/*.instructions.md` with `applyTo`.
4. A multi-step procedure invoked by name → a skill in `.github/skills/<name>/SKILL.md`.

Write the falsifiable part (symptom, wrong conclusion, fix), not "be careful with X". Edit or delete notes that
turn out wrong. Commit these files — they are shared project knowledge, not private memory.

Personal agent memory (`/memories/`) comes from other, mostly Python, projects. Don't follow its project rules here;
the files above win on any conflict. Use it only for agent tool and shell quirks (`02-tooling-quirks.md`), and save
lessons about this repo in the files above, not in memory.

## Commands

- Build + test (same as CI, [ci.yml](.github/workflows/ci.yml)): `.\gradlew.bat build`
- One test class: `.\gradlew.bat test --tests "org.chaiware.acommander.model.FileItemTest"`
- Run: `.\gradlew.bat run` (working dir must be repo root, see below)
- Windows distribution (EXE + bundled runtime + apps/config + zip → `dist/`): `.\gradlew.bat dist`. The ZIP is built
  from `releaseResources` in `build.gradle` (public config files only, no tool logs or settings), never from `dist/`
  or `build/libs/`; `verifyDistribution` fails it if per-user files get in. `dist/` is a mirror: each `dist` run
  deletes anything in it the build doesn't produce, so don't keep files or run the app there.
- After every fix or feature run `build` and fix failures. Add a JUnit 5 + AssertJ + Mockito test for new business
  logic under the matching package in `src/test/java`. Tests don't start the JavaFX toolkit — don't test UI.

## Architecture

- Entry point is `Launcher` (calls `Application.launch(Main.class)`), not `Main` — needed for the non-modular JavaFX jar.
- `Commander` (~3.6k lines) is the FXML controller for `Commander.fxml` and holds most UI behaviour. Prefer putting new
  logic in `services/` (feature logic), `helpers/` or `tools/` and calling it from `Commander`, so it can
  be unit-tested.
- Read [docs/CODEMAP.md](docs/CODEMAP.md) to find code instead of reading `Commander`. When you add, move, rename or
  delete a class, action, bundled tool or `Commander` feature method, update the map in the same change.
- `config/apps.json` and `config/f1-help.html` are read from `user.dir`, not the classpath. `shadowJar` copies
  `config/` and `apps/` into `build/libs/`.
- Packages: `actions/` dispatch + matching, `config/` apps.json loading (`AppRegistry`, `AppConfigLoader`),
  `keybinding/` key handlers, `palette/` Command Palette, `tools/ToolCommandBuilder` placeholder expansion,
  `vfs/` local/FTP/archive file systems, `dialog/` every dialog (`OptionsDialog` shell) + `DialogTheme`, `services/`
  feature logic moved out of `Commander` (file, archive and PDF operations, …; no JavaFX), `commands/`
  `ExternalToolRunner` (tool runs, Stop button, failure reports).

## Adding an action

All actions and external tools live in `config/apps.json`; shortcuts are declared there too. Look one up with
`appRegistry.findAction("id")`.

- External tool, no Java: `type: "external"`, `path` to an exe under `apps/`, placeholders in `args`.
- Builtin:
  1. Add to `config/apps.json` with `type: "builtin"` and `contexts`, plus its rules: `ftp: true` if it works on FTP
     panes, `writes` if it writes files, `fileTypes` / `requires` if the palette should offer it only sometimes
     (README "Fields" table).
  2. Add a `BuiltinAction` constant and its case in `ActionExecutor.handler()` calling a new method in `Commander`
     (the switch won't compile without it; `BuiltinActionTest` checks apps.json ↔ enum).
  3. Run the build: `ActionRulesSnapshotTest` fails and writes `build/action-rules-snapshot.actual.txt`. Check the new
     line, then copy the file over `src/test/resources/action-rules-snapshot.txt`.
  4. New shortcut → also update `config/f1-help.html` and the README shortcuts table (`DocsShortcutsTest` checks).
     A shortcut on F1–F12 (with or without Alt/Shift) also drives that bottom button.
  5. Add the row to the feature table in [docs/CODEMAP.md](docs/CODEMAP.md).

## Conventions

- UI updates from background threads go through `Platform.runLater`.
- Start processes with `tools/ProcessRunner`, run background work with `helpers/BackgroundTasks`, create temp files
  with `helpers/AppTempDir`. `ArchitectureRulesTest` fails the build on `new ProcessBuilder`,
  `CompletableFuture.runAsync`, `deleteOnExit` or a temp file in the default temp dir anywhere else.
- A tool under `apps/` that Java runs directly is a `tools/BundledTool` entry; other app files come from
  `helpers/AppPaths`. `ArchitectureRulesTest` fails on `"user.dir"` or an `"apps/` literal anywhere else.
- A tool run nobody waits on goes through `ExternalToolRunner.reportFailure` (or `Commander.runExternalReported`), or its
  failure is only logged. `ArchitectureRulesTest` fails on a bare `runExecutable(...);` / `runExternal(...);`.
- A service that runs tools takes the runner as a `Function<List<String>, CompletableFuture<List<String>>>`
  (`Commander` passes `command -> runExternal(command, false)`, which drives the progress bar and Stop), so its test
  passes a fake that records the commands (`AudioConversionServiceTest`).
- `Commander` doesn't walk folders, hash files or read settings itself; `ArchitectureRulesTest` fails on
  `Files.walk/list`, `MessageDigest` or a `Properties` variable there.
- A secret goes to a process on stdin (`ProcessRunner.stdin`), never in its arguments (Task Manager shows them). A
  file or folder name never goes inside a shell string: open it through ShellExecute
  (`rundll32.exe shell32.dll,ShellExec_RunDLL <path>`, as Explorer does) or pass a folder as the working directory
  (`ProcessRunner.directory`). `ArchitectureRulesTest` fails on `"/c"`, on `"-Command"` outside `FileOperations`,
  `ComboBoxSetup` and `Dpapi`, and on `-u` / `-k` in `FtpFileSystem`.
- New storage types implement `VFileSystem` and are wired through `VfsManager`.
- To select an item after an operation, call `FilesPanesHelper.selectFileItem(focused, folder, name)`. Never build
  the probe with `Path.of`: FTP names and bad-media names hold `:*?` and throw `InvalidPathException`.
- Metadata editing: `*MetadataSupport` runs the external tool, `*MetadataDialog` collects input; refresh the pane after.
- Tool-output parsers are tested on real output. Capture it in a temp folder: a jpg from PowerShell
  `System.Drawing.Bitmap` tagged with `apps/image_metadata/exiv2.exe -M"set Exif.Image.Make X"`; id3.exe tags an
  empty `.mp3`. AtomicParsley needs a real mp4.
- Admin elevation: PowerShell `Start-Process -Verb RunAs`.
- Logging: `logback.xml` writes to `logs/` (gitignored).

## Pitfalls

- `apps/extract_all/UniExtract/UniExtract.ini` is gitignored because UniExtract rewrites it on every run. Edit defaults
  in `UniExtract.default.ini`; the `seedUniExtractIni` Gradle task copies it into place when missing.
- Eclipse files (`.classpath`, `.project`, `.factorypath`, `.settings/`) are gitignored and regenerated from
  `build.gradle`; never commit them.
- `config/acommander.properties` is per-user runtime state (gitignored). Never commit it.
- Root `*.bat`, `run_build.py`, `verify_changes.py`, `bin/` are gitignored local leftovers; ignore them.
- Build fails with `:processResources` "Failed to clean up stale outputs": a running app (`gradlew run`) locks
  `build/resources`. Stop the `org.chaiware.acommander.Launcher` java process, build, then start the app again.
  Killing the terminal that ran `gradlew run` does not stop the app's java process.
- A file you must commit already has another session's uncommitted edits (`git diff <file>` shows hunks you didn't
  write): `git commit -- <file>` would take theirs too. Stage only yours: `cmd /c "git show HEAD:<file> > %TEMP%\x"`
  (cmd keeps the bytes; PowerShell `>` re-encodes), make your edit in both `%TEMP%\x` and the working file, then
  `git update-index --cacheinfo 100644,$(git hash-object -w --path <file> $env:TEMP\x),<file>` and `git commit -m`
  without a pathspec. A test failing on a class you never wrote is the same signal.
- After `git mv` / `git rm`, `git commit -- <old path>` fails ("pathspec did not match any file(s) known to git").
  `git add` the other paths (the move/delete is already staged), check `git status` lists only your files, then
  `git commit -m` without a pathspec.
- IntelliJ: after a clean build, debugger errors → **File → Invalidate Caches → Invalidate and Restart**. LSP errors in
  `Commander.java` (e.g. "getPath() undefined for Folder") are false positives if Gradle builds.
- `Desktop.open` throws "Unsupported URI content" for executables (`.bat`, `.exe`); use ShellExec_RunDLL (above).
- A console program started from the app gets no window (the app has no console). To give it one, have a hidden
  `powershell -Command "Start-Process ..."` start it (`FileOperations.openTerminal`). Check such process tricks
  from `build\runtime\bin\javaw.exe Probe.java`, not `java.exe`: a console parent behaves differently.
