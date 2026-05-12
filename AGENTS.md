# AGENTS.md

## Build Commands

- **Build & test**: `.\gradlew.bat build`
- **Run app**: `.\gradlew.bat run`
- **Build fat JAR**: `.\gradlew.bat shadowJar`
- **Create distribution** (EXE + runtime + apps + config + zip): `.\gradlew.bat dist`
- **Single test class**: `.\gradlew.bat test --tests "org.chaiware.acommander.model.FileItemTest"`

**Output locations**: `build/libs/` (JAR), `build/launch4j/` (EXE), `dist/` (distribution)

**JVM args**: `--enable-native-access=javafx.graphics` (required for JavaFX)

**Main class**: `org.chaiware.acommander.Launcher`

## Adding Features

### Builtin Action (Command Palette)

1. Add action to `config/apps.json` with `type: "builtin"` and `contexts` (e.g., `"global"`, `"commandPalette"`)
2. Add case in `ActionExecutor.java:executeBuiltin()` calling a method in `Commander.java`
3. Implement the method in `Commander.java`

### External Tool

1. Add config to `config/apps.json` with `type: "external"` and `path`
2. Place tool in `apps/` directory or reference installed path
3. Use placeholders: `${selectedFile}`, `${targetFolder}`, `${selectedFiles}`, `${selectedName}`

## Common Patterns

- **Admin elevation**: `Start-Process -Verb RunAs` via PowerShell
- **File existence check**: `new File(path).exists() && new File(path).isFile()`
- **Get action config**: `appRegistry.findAction("actionId")`

## IntelliJ Notes

- After clean build, debugger errors: **File → Invalidate Caches → Invalidate and Restart**
- LSP errors in `Commander.java` (e.g., "getPath() undefined for Folder") are false positives — build succeeds
- Gradle `run` task is incompatible with configuration cache due to IntelliJ debugger init scripts

## After Completing Work

After finishing a task, review what you did and consider:
- **Create a new skill**: If similar tasks recur and have reusable steps/warnings
- **Add to `AGENTS.md`**: If only a command, pattern, or fact — keep it simple
- **Do nothing**: If it was a one-off or already in docs/skills

Never create an agent or skill just "for future" without a specific use case.
