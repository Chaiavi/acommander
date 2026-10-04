# AGENTS.md

JavaFX dual-pane file manager (Java 21, Gradle, Windows). The entry point is `Launcher.java`, not `Main.java`.

## Commands

- Build + test: `.\gradlew.bat build`
- One test class: `.\gradlew.bat test --tests "org.chaiware.acommander.model.FileItemTest"`
- Run: `.\gradlew.bat run`
- Windows distribution (EXE + runtime + apps/config + zip, output in `dist/`): `.\gradlew.bat dist`
- After every fix or feature, run `build` and fix failures. Add a unit test for the new behaviour (JUnit 5, AssertJ,
  Mockito). Test business logic, not UI.

## Data-driven actions

All actions and external tools live in `config/apps.json` (schema in README.md). Look up a definition with
`appRegistry.findAction("id")`.

- External tool, no Java needed: `type: "external"`, `path` to the executable (under `apps/`), and placeholders such
  as `${selectedFile}` and `${targetFolder}` in the arguments.
- Builtin action:
  1. Add it to `config/apps.json` with `type: "builtin"` and `contexts` (e.g. `"global"`, `"commandPalette"`).
  2. Add a case in `ActionExecutor.executeBuiltin()` that calls a new method in `Commander.java`.
  3. If it must work on FTP panes, also add its id to `ActionExecutor.isActionSupportedOnFtp()`. Unlisted ids are
     rejected there.

## Conventions

- New storage types implement `VFileSystem` and are registered in `VfsManager`.
- Metadata editing: `*MetadataSupport` runs the external tool, `*MetadataDialog` collects input. Refresh the pane
  afterwards.
- Admin elevation: PowerShell `Start-Process -Verb RunAs`.
- `config/acommander.properties` is runtime state (gitignored). Do not commit it.

## IntelliJ Notes

- After clean build, if debugger errors occur: **File → Invalidate Caches → Invalidate and Restart**
- LSP errors in `Commander.java` (e.g., "getPath() undefined for Folder") are false positives - build succeeds