# AGENTS.md

## Build Commands

- **Build & test**: `.\gradlew.bat build`
- **Single test class**: `.\gradlew.bat test --tests "org.chaiware.acommander.model.FileItemTest"`
- **Run app**: `.\gradlew.bat run`

## Adding New Features

### Adding a Builtin Action (Command Palette)

1. Add action to `config/apps.json` with `type: "builtin"` and appropriate `contexts` (e.g., `"global"`,
   `"commandPalette"`)
2. Add case in `ActionExecutor.java:executeBuiltin()` to call a new method in `Commander.java`
3. Implement the method in `Commander.java`

### Adding an External Tool

1. Add configuration to `config/apps.json` with `type: "external"` and `path` to the executable
2. Place tool in `apps/` directory or reference existing installation
3. Use placeholders like `${selectedFile}`, `${targetFolder}` for arguments

## Common Patterns

- **Admin elevation**: Use `Start-Process -Verb RunAs` via PowerShell for elevated operations
- **File existence check**: Use `new File(path).exists() && new File(path).isFile()`
- **Get app config**: Use `appRegistry.findAction("actionId")` to retrieve action definitions

## IntelliJ Notes

- After clean build, if debugger errors occur: **File → Invalidate Caches → Invalidate and Restart**
- LSP errors in `Commander.java` (e.g., "getPath() undefined for Folder") are false positives - build succeeds