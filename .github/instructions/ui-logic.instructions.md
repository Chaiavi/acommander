---
description: "Use when editing JavaFX UI classes: Commander.java (main FXML controller), dialog/** or palette/**. Keeps business logic out of UI classes so it can be unit-tested."
applyTo: "src/main/java/org/chaiware/acommander/Commander.java, src/main/java/org/chaiware/acommander/dialog/**, src/main/java/org/chaiware/acommander/palette/**"
---
# Keep Logic Out of JavaFX UI Classes

Tests can't start the JavaFX toolkit, so logic left in a UI class is untestable. `Commander` alone is ~3.6k lines.
Find the method you need through [CODEMAP.md](../../docs/CODEMAP.md) instead of reading the file.

- Keep in UI classes only: reading UI state (selection, focused pane, field values), showing dialogs,
  `Platform.runLater`, refreshing panes.
- Put decisions, parsing, validation, filtering and argument building in a class under `services/`, `helpers/` or
  `tools/`, taking plain inputs (`List<FileItem>`, `Path`, `String`) — no JavaFX types.
- Pattern to copy: [ImageConversionSupport](../../src/main/java/org/chaiware/acommander/helpers/ImageConversionSupport.java)
  is a `final` class with static methods. `Commander` calls `ImageConversionSupport.areAllConvertibleImages(selected)`.
- Add a test for the new class next to the existing ones, e.g.
  [ImageConversionSupportTest](../../src/test/java/org/chaiware/acommander/helpers/ImageConversionSupportTest.java).
- When you change an existing method, move the logic you touch out of the UI class. Don't refactor code you aren't
  changing.
- A new dialog is never built inline in `Commander`. Add `dialog/XxxDialog` with a static
  `show(Window owner, String themeClass, …)` returning `Optional<Result>`, built on `OptionsDialog` (heading,
  OK = Enter, Cancel = Escape, theme). Set each control's tooltip inline with `OptionsDialog.tip(control, text)`.
  `Commander` calls it as `XxxDialog.show(dialogOwner(), currentThemeMode.styleClass, …)`. Example:
  [CompareFoldersDialog](../../src/main/java/org/chaiware/acommander/dialog/CompareFoldersDialog.java).
