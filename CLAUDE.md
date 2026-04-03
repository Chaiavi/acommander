# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building and Running

```bash
# Run the application
.\gradlew.bat run

# Run tests
.\gradlew.bat test

# Build fat JAR
.\gradlew.bat shadowJar

# Build Windows distribution (EXE + runtime + apps/config + zip)
.\gradlew.bat dist
```

### Test Execution

- Run all tests: `.\gradlew.bat test`
- Run a single test class: `.\gradlew.bat test --tests "org.chaiware.acommander.model.FileItemTest"`
- Run tests with specific pattern: `.\gradlew.bat test --tests "*Metadata*"`

### Project Artifacts

- JAR + resources: `build/libs/`
- EXE: `build/launch4j/`
- Distribution: `dist/` (created by `gradlew dist`)

## Code Architecture

### High-Level Structure

- **Entry Point**: `src/main/java/org/chaiware/acommander/Launcher.java` (configured in build.gradle)
- **JavaFX Application**: `src/main/java/org/chaiware/acommander/Main.java` sets up the UI
- **Main Controller**: `src/main/java/org/chaiware/acommander/Commander.java` manages the dual-pane interface
- **Configuration**: `config/apps.json` defines all external tools and actions (data-driven system)
- **Virtual File System**: `src/main/java/org/chaiware/acommander/vfs/` handles local, FTP, and archive browsing
- **Metadata Editing**: Modules in `src/main/java/org/chaiware/acommander/helpers/*MetadataSupport.java` and
  corresponding dialogs

### Key Architectural Patterns

1. **Data-Driven Action System**: All external tools are configured in `config/apps.json` - no code changes needed to
   add new tools
2. **Command Pattern**: Actions are encapsulated in `ACommands.java` and `Commands*.java` classes
3. **Plugin Architecture**: Virtual file systems implement `VFileSystem` interface for different storage types
4. **MVC-like Structure**: JavaFX FXML files (`Commander.fxml`, `CommandPalette.fxml`) with controller classes

### Important Directories

- `src/main/java/` - Application source code
    - `org.chaiware.acommander.actions/` - Action execution and matching logic
    - `org.chaiware.acommander.config/` - Configuration loading and AppRegistry
    - `org.chaiware.acommander.dialog/` - Metadata editing dialogs
    - `org.chaiware.acommander.helpers/` - Support classes for metadata, conversion, file operations
    - `org.chaiware.acommander.keybinding/` - Keyboard handling
    - `org.chaiware.acommander.model/` - Data models (FileItem, Folder, VirtualFolder, etc.)
    - `org.chaiware.acommander.palette/` - Command palette implementation
    - `org.chaiware.acommander.tools/` - Command-line tool building utilities
    - `org.chaiware.acommander.vfs/` - Virtual file system implementations
- `src/main/resources/` - FXML layouts, CSS styles, icons, logging configuration
- `config/` - Runtime configuration (apps.json, acommander.properties, F1 help)
- `apps/` - Bundled external tools (7-Zip, Notepad4, ripgrep, etc.)

## Development Guidelines

### Adding New External Tools

1. Place tool executables in the `apps/` directory (or reference existing installations)
2. Add configuration to `config/apps.json` following the schema documented in README.md
3. Use placeholders like `${selectedFile}`, `${targetFolder}` for dynamic arguments
4. No Java code changes required - the system is fully data-driven

### Working with Virtual File Systems

- Implement `VFileSystem` interface for new storage types
- Register implementations in `VfsManager`
- Local file system: `LocalFileSystem.java`
- FTP/SFTP: `FtpFileSystem.java`
- Archives: `ArchiveFileSystem.java` (handles zip, 7z, etc.)

### Metadata Editing Flow

1. Helper classes (`*MetadataSupport.java`) handle tool execution and parsing
2. Dialog classes (`*MetadataDialog.java`) collect user input
3. Changes are applied via external tools configured in apps.json
4. Refresh file pane after metadata changes

### Testing Practices

- Unit tests are in `src/test/java/` matching the package structure
- Tests use JUnit 5, AssertJ, and Mockito
- Focus on testing business logic, not UI components
- Test helpers like `FileAttributesHelperTest` validate utility functions
- **After every bug fix or new feature implementation:**
    1. Build the project using `.\gradlew.bat build` to ensure there are no compilation errors
    2. Run all tests using `.\gradlew.bat test` to verify nothing is broken
    3. Consider adding unit tests that specifically test the fix or feature
    4. When adding new tests, follow unit test conventions and test intent rather than implementation details

## Configuration Files

- `config/apps.json` - Main configuration for external tools and actions
- `config/acommander.properties` - Persistent state (window positions, themes, bookmarks)
- `config/F1Help.md` - Help content displayed when F1 is pressed
- `src/main/resources/logback.xml` - Logging configuration
- `src/main/resources/styles/app-theme.css` - Application styling

## Build System

- Uses Gradle with Java 21 toolchain
- Dependencies managed in build.gradle
- JavaFX modules for Windows platform
- Lombok for reduced boilerplate
- Launch4j and Shadow plugins for EXE creation
- Distribution task bundles EXE, JAR, runtime, config, and apps
