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
7. Report the commit hash in the final reply.

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
- Windows distribution (EXE + bundled runtime + apps/config + zip → `dist/`): `.\gradlew.bat dist`
- After every fix or feature run `build` and fix failures. Add a JUnit 5 + AssertJ + Mockito test for new business
  logic under the matching package in `src/test/java`. Tests don't start the JavaFX toolkit — don't test UI.

## Architecture

- Entry point is `Launcher` (calls `Application.launch(Main.class)`), not `Main` — needed for the non-modular JavaFX jar.
- `Commander` (~7.4k lines) is the FXML controller for `Commander.fxml` and holds most UI behaviour. Prefer putting new
  logic in `helpers/`, `commands/` or `tools/` and calling it from `Commander`, so it can be unit-tested.
- Read [docs/CODEMAP.md](docs/CODEMAP.md) to find code instead of reading `Commander`. When you add, move, rename or
  delete a class, action, bundled tool or `Commander` feature method, update the map in the same change.
- `config/apps.json` and `config/f1-help.html` are read from `user.dir`, not the classpath. `shadowJar` copies
  `config/` and `apps/` into `build/libs/`.
- Packages: `actions/` dispatch + matching, `config/` apps.json loading (`AppRegistry`, `AppConfigLoader`),
  `keybinding/` key handlers, `palette/` Command Palette, `tools/ToolCommandBuilder` placeholder expansion,
  `vfs/` local/FTP/archive file systems, `dialog/` metadata dialogs.

## Adding an action

All actions and external tools live in `config/apps.json`; shortcuts are declared there too. Look one up with
`appRegistry.findAction("id")`.

- External tool, no Java: `type: "external"`, `path` to an exe under `apps/`, placeholders in `args`.
- Builtin:
  1. Add to `config/apps.json` with `type: "builtin"` and `contexts`.
  2. Add a case in `ActionExecutor.executeBuiltin()` calling a new method in `Commander`.
  3. To work on FTP panes, add the id to `ActionExecutor.isActionSupportedOnFtp()` — unlisted ids are rejected (the
     palette uses the same list).
  4. If it writes files, add the id to `ActionMutator` and, if needed, `ActionExecutor.isConditionalWriteBlocked()`.
  5. If it only fits certain file types, add the palette check in `ActionRegistry.isSelectionAllowedForBuiltin()`.
  6. New shortcut → also update `config/f1-help.html` and the README shortcuts table (`DocsShortcutsTest` checks).
  7. Add the row to the feature table in [docs/CODEMAP.md](docs/CODEMAP.md).

## Conventions

- UI updates from background threads go through `Platform.runLater`.
- New storage types implement `VFileSystem` and are wired through `VfsManager`.
- Metadata editing: `*MetadataSupport` runs the external tool, `*MetadataDialog` collects input; refresh the pane after.
- Admin elevation: PowerShell `Start-Process -Verb RunAs`.
- Logging: `logback.xml` writes to `logs/` (gitignored).

## Pitfalls

- `apps/extract_all/UniExtract/UniExtract.ini` is gitignored because UniExtract rewrites it on every run. Edit defaults
  in `UniExtract.default.ini`; the `seedUniExtractIni` Gradle task copies it into place when missing.
- Eclipse files (`.classpath`, `.project`, `.factorypath`, `.settings/`) are gitignored and regenerated from
  `build.gradle`; never commit them.
- `config/acommander.properties` is per-user runtime state (gitignored). Never commit it.
- Root `*.bat`, `run_build.py`, `verify_changes.py`, `bin/` are gitignored local leftovers; ignore them.
- IntelliJ: after a clean build, debugger errors → **File → Invalidate Caches → Invalidate and Restart**. LSP errors in
  `Commander.java` (e.g. "getPath() undefined for Folder") are false positives if Gradle builds.
