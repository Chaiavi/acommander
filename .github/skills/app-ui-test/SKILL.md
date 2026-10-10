---
name: app-ui-test
description: "Drive the running app like a user (keys, clicks, screenshots) in an isolated copy that never touches the user's settings or files. Use when a change must be checked in the app itself (Command Palette actions, dialogs) and the user asked you to test it."
---
# Test the App by Hand, Isolated

The user's own app (F5 or `gradlew run`) keeps running untouched. Tell the user to keep hands off the keyboard and
mouse while you test: keys go to whatever window is in front.

## Setup

1. `.\gradlew.bat shadowJar`.
2. Make `$t = "$env:TEMP\ac-apptest"` with:
   - `config\apps.json` (copy) and `config\acommander.properties` with `left_folder`, `right_folder` (escape `\` as
     `\\` and `:` as `\:`) and `tool_updates_at_start=false`.
   - `apps` as a junction to the repo's `apps` (`New-Item -ItemType Junction`).
   - Test files in `$t\in`. Build PDFs with a correct xref table (see `BundledToolContractTest.pdf`).
3. Start it from `$t`: `Start-Process build\runtime\bin\javaw.exe '--enable-native-access=ALL-UNNAMED','-jar',"<repo>\build\libs\acommander.jar" -WorkingDirectory $t -PassThru`.
   Its log is `$t\logs\ALL.log`.

## Driving it

`pwsh -NoProfile -File .github\skills\app-ui-test\ui.ps1 <pid> '<steps>' <shot.png> [waitMs] [-noActivate]`, then view
the PNG. Steps: SendKeys codes, `TEXT:...`, `CLICK:x,y` (from the front window's corner, in screenshot pixels).

- One call per screen: send the keys that open a dialog, wait (`2500`), screenshot; act on it in the next call.
- A dialog is open: pass `-noActivate`. Activating the pid brings the main window forward and later keys miss the
  dialog (it looked like the dialog closed itself).
- Dialog radio buttons don't move with arrow keys (focus sits on the default button): `CLICK:` them.
- `TEXT:` sends ASCII only; Hebrew arrives as `?`. Put non-ASCII names in the test files instead.
- Typing in a pane starts its incremental filter; a stray key leaves a pane showing only `..`. `{ESC}` in that pane.

## Cleanup

Stop the pid, `cmd /c rmdir "$t\apps"` (removes the junction only; `Remove-Item -Recurse` could follow it into the
real `apps`), check `Test-Path apps\pdf`, then `Remove-Item $t -Recurse -Force`.
