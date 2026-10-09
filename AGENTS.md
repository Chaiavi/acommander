# AGENTS.md

JavaFX 21 dual-pane file manager for Windows (Gradle, Lombok, SLF4J/Logback, Jackson). Features, `config/apps.json`
schema, placeholders and shortcuts are in [README.md](README.md) — link there, don't duplicate.

## Working with the user

- Write in simple, short, direct sentences. Use numbered lists for several points. No intros or wrap-ups.
- Never hand the user a command to run. Run one-time commands yourself and report the result. A need that recurs
  gets a feature in the app (an `apps.json` action / Command Palette entry), not documented CLI steps.
- If the app is running (`gradlew run`) when you finish a change, restart it so the user never tests stale code.
  Read the end of its log first: an action in the last minute (an open Rename dialog, say) means the user is mid-task,
  and killing the app loses it. Say so and let them restart instead. A `build` writes its test logs to the same
  `logs/ALL.log`, so its tail can look busy: check the last `[ActionExecutor]` line instead.
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
   [README.md](README.md) in the same commit (the F1 help is built from `apps.json`). `DocsShortcutsTest` fails the
   build if an `apps.json` shortcut is missing from it; features and descriptions are on you.
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
- Static analysis (javac lint + PMD, on demand, not in `build`):
  `.\gradlew.bat --init-script gradle\analysis\analysis.init.gradle compileJava pmdMain --no-configuration-cache`,
  then `build/reports/pmd/main.xml`. The 21 known false positives: `==` on controls/`ButtonType`/file systems and
  the null check in `PaneSorter` (identity is meant; two rules flag it), `PreserveStackTrace` where a wrapper's cause is unwrapped, the
  commented empty catch in `LocalFileSystem.addEntries`, `plainFtpPasswordsFound` in `SettingsStore` (a field read
  elsewhere). Anything else is new.
- Run: `.\gradlew.bat run` (working dir must be repo root, see below). Faster in VS Code: F5 ("ACommander" in
  `.vscode/launch.json`) skips Gradle's configuration step, which `run` can't cache.
- Windows distribution (EXE + bundled runtime + apps/config + zip → `dist/`): `.\gradlew.bat dist`. The ZIP is built
  from `releaseResources` in `build.gradle` (public config files only, no tool logs or settings), never from `dist/`
  or `build/libs/`; `verifyDistribution` fails it if per-user files get in. `dist/` is a mirror: each `dist` run
  deletes anything in it the build doesn't produce, so don't keep files or run the app there.
- Asked to deploy (`dist`): first run [update-tools](.github/skills/update-tools/SKILL.md), so every tool that has a
  tested newer version ships. `dist` also prints the tool report as a last check.
- After every fix or feature run `build` and fix failures. Add a JUnit 6 (Jupiter) + AssertJ + Mockito test for new business
  logic under the matching package in `src/test/java`. Tests don't start the JavaFX toolkit — don't test UI.

## Architecture

- Entry point is `Launcher` (calls `Application.launch(Main.class)`), not `Main` — needed for the non-modular JavaFX jar.
- `Commander` (~3.6k lines) is the FXML controller for `Commander.fxml` and holds most UI behaviour. Prefer putting new
  logic in `services/` (feature logic), `helpers/` or `tools/` and calling it from `Commander`, so it can
  be unit-tested.
- Read [docs/CODEMAP.md](docs/CODEMAP.md) to find code instead of reading `Commander`. When you add, move, rename or
  delete a class, action, bundled tool or `Commander` feature method, update the map in the same change.
- `config/apps.json` is read from `user.dir`, not the classpath. `shadowJar` copies
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
  1. Add to `config/apps.json` with `type: "builtin"`, `contexts`, a one-sentence `description` and a `category`
     from `HelpTopics.CATEGORIES` (the F1 help shows both; `HelpTopicsTest` fails without them), plus its rules: `ftp: true` if it works on FTP
     panes, `writes` if it writes files, `fileTypes` / `requires` if the palette should offer it only sometimes
     (README "Fields" table).
  2. Add a `BuiltinAction` constant and its case in `ActionExecutor.handler()` calling a new method in `Commander`
     (the switch won't compile without it; `BuiltinActionTest` checks apps.json ↔ enum).
  3. Run the build: `ActionRulesSnapshotTest` fails and writes `build/action-rules-snapshot.actual.txt`. Check the new
     line, then copy the file over `src/test/resources/action-rules-snapshot.txt`.
  4. New shortcut → also update the README shortcuts table (`DocsShortcutsTest` checks).
     A shortcut on F1–F12 (with or without Alt/Shift) also drives that bottom button.
  5. Add the row to the feature table in [docs/CODEMAP.md](docs/CODEMAP.md).

## Conventions

- UI updates from background threads go through `Platform.runLater`.
- Bump the version only in `src/main/resources/app-version.properties`; `build.gradle` and `AppVersion` read it.
  Don't template it (`${...}`): IDE launches (F5) copy resources without Gradle and showed "dev".
- Start processes with `tools/ProcessRunner`, run background work with `helpers/BackgroundTasks`, create temp files
  with `helpers/AppTempDir`. `ArchitectureRulesTest` fails the build on `new ProcessBuilder`,
  `CompletableFuture.runAsync`, `deleteOnExit` or a temp file in the default temp dir anywhere else.
- A tool under `apps/` that Java runs directly is a `tools/BundledTool` entry; other app files come from
  `helpers/AppPaths`. `ArchitectureRulesTest` fails on `"user.dir"` or an `"apps/` literal anywhere else.
- `apps/media/ffmpeg.exe` is not in git: it is over GitHub's 100 MB file limit. The `fetchFfmpeg` Gradle task
  downloads it, pinned by SHA-256, and `test`, `run` and `dist` depend on it. To update ffmpeg, change its `version`
  and `release` (`ffmpeg-<version>`) in the `tools` section of `config/apps.json` and `ffmpegZipSha256` in
  `build.gradle`; `dist` then creates that release with `gh` so Tool Updates can download it. All audio/video work (convert, tags, trim, join, info) runs
  through it; `MediaConversionServiceTest` and `MediaTagSupportTest` run the real exe on generated clips.
- Every file under `apps/` belongs to one tool in the `tools` section of `config/apps.json` (`ToolsConfigTest`). To
  update tools, follow [update-tools](.github/skills/update-tools/SKILL.md): `upgradeTools -Ptools=<id>` downloads and
  replaces the files, then `build` (rewrites `apps/tools.sha256`; `BundledToolContractTest`
  runs each command-line tool the way the app does), commit the files and `apps/tools.sha256` together. Tools that
  open a window run in `guiToolTest` (`BundledGuiToolContractTest`, tag `gui`, kept out of `build`): run it too.
  Users' Tool Updates compare against `main`, so a binary pushed without its new hash list fails their download check.
- A tool built with MSVC imports `VCRUNTIME140.dll`, which a clean Windows lacks (it runs here only because a VC++
  redist is installed). List a new exe's DLLs with a strings scan (`[regex]::Matches(<ASCII bytes>, '[\w\-]+\.dll')`);
  if it needs it, copy `build/runtime/bin/vcruntime140.dll` beside the exe and leave it out of `upstream.files`
  (`file` does this).
- caesiumclt exits 0 when a file fails ("Cannot convert to the same format" for PNG to PNG): check its output with
  `ImageConversionService.failures`, not the exit code. Same-format files run with `--format original`.
- `apps/**` is `-text` in `.gitattributes`: GitHub must serve the bytes `tools.sha256` was computed from. With
  `core.autocrlf` git stored LF and checked out CRLF, so every text file's hash differed from the raw download.
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
- To show a local folder in a pane, call `Commander.showLocalFolder`. `setFileListPath` keeps the pane's file system,
  so on a pane in an archive or on FTP it lists the local path through that VFS (#141).
- Metadata editing: `*MetadataSupport` runs the external tool, `*MetadataDialog` collects input; refresh the pane after.
- Tool-output parsers are tested on real output. Capture it in a temp folder: a jpg from PowerShell
  `System.Drawing.Bitmap` tagged with `apps/image_metadata/exiv2.exe -M"set Exif.Image.Make X"`; id3.exe tags an
  empty `.mp3`. AtomicParsley needs a real mp4.
- Admin elevation: PowerShell `Start-Process -Verb RunAs`.
- A bundled tool's command-line syntax is often only in its `.chm` help: `hh.exe -decompile <tempdir> <file.chm>`
  gives plain HTML (Ant Renamer: `params_en.html`, `-af` takes each path as its own argument).
- Logging: `logback.xml` writes to `logs/` (gitignored).

## Pitfalls

- `UniExtract.ini`, `Renamer.xml` (Ant Renamer) and `ThisIsMyFile.ini` are gitignored because the tools rewrite them
  on every run. Edit defaults in their `.default` templates; the `seedToolSettings` Gradle task copies a missing
  one into place and the release ships the templates. A new tool that does this: add it to `toolSettingsTemplates`
  in `build.gradle` and to `.gitignore`. `guiToolTest` runs every window tool, so `git status` after it shows any
  other tracked file a tool rewrites.
- `ActionRulesSnapshotTest` diff flips many actions to `ftp=yes | writes=none`: a new gate in
  `ActionExecutor.execute` runs before the FTP / read-only gates and returns first (its mocks have no selection).
  Put new gates after those two.
- `build` hangs in `:test` with no output: a `Commander` field initializer started a process (`new ComboBoxSetup()`
  runs PowerShell, which never returns under Gradle). `ActionRulesSnapshotTest` runs `new Commander()`, so its field
  initializers run in tests. Create such objects in `initialize()`. To find a hang, run `jstack <Gradle Test Executor pid>`.
- Gradle warns "Deprecated Gradle features were used" (`Configuration.setVisible`, `build.gradle` at
  `apply plugin: 'edu.sc.seis.launch4j'`). It comes from the Launch4j plugin 4.0.0, not our script; it breaks in
  Gradle 11 unless the plugin ships a fix. Check `--warning-mode all` for any other source before ignoring it.
- Eclipse files (`.classpath`, `.project`, `.factorypath`, `.settings/`) are gitignored and regenerated from
  `build.gradle`; never commit them.
- `config/acommander.properties` is per-user runtime state (gitignored). Never commit it.
- Root `*.bat`, `run_build.py`, `verify_changes.py`, `bin/` are gitignored local leftovers; ignore them.
- Build fails with `:processResources` "Failed to clean up stale outputs": a running app (`gradlew run`, or the F5
  launch too) locks `build/resources`. Stop the `org.chaiware.acommander.Launcher` java process, build, then start the app again.
  Killing the terminal that ran `gradlew run` does not stop the app's java process.
- A file you must commit already has another session's uncommitted edits (`git diff <file>` shows hunks you didn't
  write): `git commit -- <file>` would take theirs too. Stage only yours: `cmd /c "git show HEAD:<file> > %TEMP%\x"`
  (cmd keeps the bytes; PowerShell `>` re-encodes), make your edit in both `%TEMP%\x` and the working file, then
  `git update-index --cacheinfo 100644,$(git hash-object -w --path <file> $env:TEMP\x),<file>` and `git commit -m`
  without a pathspec. A test failing on a class you never wrote is the same signal.
- After `git mv`, `git commit -- <old path>` fails ("pathspec did not match any file(s) known to git"). (A plain
  `git rm <file>` then `git commit -- <file> ...` works.)
  `git add` the other paths (the move/delete is already staged), check `git status` lists only your files, then
  `git commit -m` without a pathspec.
- IntelliJ: after a clean build, debugger errors → **File → Invalidate Caches → Invalidate and Restart**. LSP errors in
  `Commander.java` (e.g. "getPath() undefined for Folder") are false positives if Gradle builds.
- Universal Viewer (`BundledTool.VIEWER`) renders HTML with legacy IE: CSS variables, flexbox and web fonts are
  ignored, so a styled page shows as plain black text on white. Open HTML with the default
  browser (`Desktop.open`). To screenshot it, use
  `SetProcessDPIAware` + `Graphics.CopyFromScreen`; `PrintWindow` returns a black page for the embedded browser.
- A `ContextMenu`/popup shown on a node takes CSS from that node: on a `.file-pane` it gets the dark pane background
  but Modena's dark text (unreadable). Style it under `.file-pane .context-menu` in `app-theme.css` with `-ac-*` colors.
- `Desktop.open` throws "Unsupported URI content" for executables (`.bat`, `.exe`); use ShellExec_RunDLL (above).
- Drag and drop on Windows: JavaFX reports a plain drag and a Shift+drag the same way (both MOVE; only Ctrl = COPY,
  Alt or Ctrl+Shift = LINK; see openjfx `GlassDnD.cpp`), and a `DragEvent` carries no key state. Don't build on Shift.
  Accept only COPY from another app: a source told MOVE may delete its files while our background copy still runs.
- A console program started from the app gets no window (the app has no console). To give it one, have a hidden
  `powershell -Command "Start-Process ..."` start it (`FileOperations.openTerminal`). Check such process tricks
  from `build\runtime\bin\javaw.exe Probe.java`, not `java.exe`: a console parent behaves differently.
