---
name: app-ui-test
description: "Drive the app like a user (keys, clicks, screenshots) in an isolated copy that never touches the user's settings or files, and run the release checklist. Use before every deploy (`dist`), after a change to how keys reach actions (Commander, ActionExecutor, FilesPanesHelper, palette, keybinding), or when the user asks to test something in the app."
---
# Test the App by Hand, Isolated

The user's own app (F5 or `gradlew run`) keeps running untouched. Tell the user to keep hands off the keyboard and
mouse while you test: keys go to whatever window is in front.

## Start and stop

1. `.\gradlew.bat shadowJar`.
2. `pwsh -NoProfile -File .github\skills\app-ui-test\env.ps1 start`. It builds `%TEMP%\ac-apptest`:
   - Its own settings, so your test never changes the user's.
   - `apps\` as a junction to the repo's tools.
   - The same test files every run, and the app running on them. Its pid is in `pid.txt`, its log is `logs\ALL.log`.
3. `env.ps1 stop` stops the app and deletes the folder. It removes the junction first, so the real `apps\` is safe.
   `env.ps1 start` again resets to fresh test files.

Left pane `in\` after start (Name order): `..`, `nested\` (holds `deep.txt`), `big.bin` (3 MB), `box.zip` (holds
`notes.txt`), `notes.txt` ("the needle is here"), `picture.png` (64x48), `second.pdf` (2 pages), `tone.wav` (3 s),
`ראשון.pdf` (3 pages), `שלום.txt`. Right pane `out\` is empty.

## Driving it

`pwsh -NoProfile -File .github\skills\app-ui-test\ui.ps1 <pid> '<steps>' <shot.png> [waitMs] [-noActivate]`, then view
the PNG. Steps are joined by `||`: SendKeys codes (`{ENTER}`, `^a`, `^+p`, `{DOWN 4}`), `TEXT:...`, `CLICK:x,y`
(pixels from the front window's corner, as in the screenshot).

- One call per screen: send the keys that open a dialog, wait (`2500`), screenshot; act on it in the next call.
- A dialog or tool window is in front: pass `-noActivate`. Activating the pid brings the main window forward and
  later keys miss the dialog (it looked like the dialog closed itself).
- Dialog radio buttons don't move with arrow keys (focus sits on the default button): `CLICK:` them.
- `TEXT:` sends ASCII only; Hebrew arrives as `?`. The Hebrew names are in the test files for that reason.
- Typing in a pane starts its incremental filter; a stray key leaves a pane showing only `..`. `{ESC}` in that pane.
- Cursor to the Nth row: `{HOME}{DOWN N}` (row 0 is `..`). Rows shift when a test adds or removes files in `in\`.
- Judge a step by the disk and the log (`Select-String $t\logs\ALL.log ' ERROR | WARN ' -CaseSensitive`), not by
  the screenshot alone. A screenshot shows where you are.
- `ui.ps1` prints `foreground pid=0 size=0x0` and the Bitmap "Parameter is not valid": the Windows session is
  locked (`Get-Process LogonUI` exists). Keys go nowhere; stop and say which rows are left.

## Release checklist

Run in this order: the steps that delete, move or rename files in `in\` come last. Shortcuts open their dialog;
confirm with `{ENTER}` unless the row says otherwise. Palette actions: `^+p`, `TEXT:<label>`, `{ENTER}`.

| # | Flow | Steps | Pass when |
|---|---|---|---|
| 1 | Start | screenshot | Both panes listed as above; no ERROR in the log |
| 2 | Select All / Invert / Unselect | `^a`, palette `TEXT:merge` (Esc), `^i`, `^+a` | `^a` marks all rows but `..`; status bar counts them; Merge PDF Files is NOT offered (mixed types) |
| 3 | Copy a file (F5) | cursor on `notes.txt`, `{F5}` | `out\notes.txt` |
| 4 | Copy a folder (F5) | cursor on `nested`, `{F5}` | `out\nested\deep.txt` |
| 5 | Clipboard copy | `picture.png`, `^c`, `{TAB}`, `^v`, `{TAB}` | `out\picture.png` |
| 6 | Duplicate (Alt+F6) | `notes.txt`, `%{F6}` | A second copy of `notes.txt` in `in\` |
| 7 | Create Directory / File | `{F7}` `TEXT:made`; `%{F7}` `TEXT:made.txt` | `in\made\`, `in\made.txt` |
| 8 | Pack to Zip (F11) | `notes.txt`, `{F11}`, 7-Zip window: `{ENTER}` (`-noActivate`) | A zip holding `notes.txt` |
| 9 | Unpack (F12) | `box.zip`, `{F12}`, 7-Zip window: `{ENTER}` | `notes.txt` from the zip in the target |
| 10 | Browse a zip | `box.zip`, `{ENTER}`, `{DOWN}`, `{F5}`, `{BACKSPACE}` | Pane shows `notes.txt` inside; it is copied to `out\` |
| 11 | Split (Alt+F11) | `big.bin`, `%{F11}`, 1 MB parts | `big.bin.001` ... parts in the target, sizes add up to 3 MB |
| 12 | Search for Files (F10) | `{F10}`, `TEXT:*.txt` | `notes.txt`, `שלום.txt`, `deep.txt` found |
| 13 | Find in Files (Alt+F10) | `%{F10}`, `TEXT:needle` | Only `notes.txt` found |
| 14 | View / Edit | `notes.txt`, `{F3}` (close `%{F4}`), `{F4}` (close `%{F4}`) | Universal Viewer, then Notepad4 opens with the text |
| 15 | Analyze File | `picture.png`, palette `Analyze File` | "PNG image data, 64 x 48", no `\012` |
| 16 | Checksum File | `notes.txt`, palette `Checksum File` | SHA-256 equals `Get-FileHash` |
| 17 | Convert Graphics | `picture.png`, palette `Convert Graphics`, JPG | `picture.jpg` in the target |
| 18 | Audio | `tone.wav`: Media Info (3 s), Convert Audio to MP3, Trim Media 0 to 1 s | Info shows 0:03; `tone.mp3`; a ~1 s file |
| 19 | PDF | `{INSERT}` on both PDFs, Merge PDF Files; on the result Extract PDF Pages: All, `1, 3`, per 2 | 5 pages; 5 files; 2 files; `_0001-0002`, `_0003-0004`, `_0005-0005` |
| 20 | Compare Files | `notes.txt` left, `out\notes.txt` right, palette `Compare Files` | ExamDiff opens on both |
| 21 | Compare Folders | palette `Compare Folders` | Dialog lists the differences between `in\` and `out\` |
| 22 | App | `{F1}`; palette `Toggle Dark Mode` twice; palette Bookmark this path, Goto Bookmark, Remove Bookmark | Help opens; colors flip and return; bookmark goes to the folder, then is gone |
| 23 | Rename (F2) | `notes.txt`, `{F2}`, `TEXT:renamed.txt` | `in\renamed.txt`, no `notes.txt` |
| 24 | Move (F6) | `שלום.txt`, `{F6}` | `out\שלום.txt`, gone from `in\` |
| 25 | Delete (F8) / Wipe (Shift+F8) | `renamed.txt` `{F8}`; `big.bin` `+{F8}` | Both gone from `in\` |

### Installer (after `dist`)

Skip and say so if `HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{1D54CF8C-10E1-4E2A-BD4D-5F4CFE658C1C}_is1`
exists: that is the user's real install, and a test install would take it over. Always pass `/TASKS=""`: the desktop
task overwrites a desktop `ACommander.lnk` the user may have made for a portable copy, and uninstall then deletes it.

1. `dist\acommander-setup-v<version>.exe /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /TASKS="" "/DIR=$env:TEMP\ac-install"`
   (via `Start-Process -Wait`). Pass: exit 0, the Start Menu `ACommander.lnk` points into the folder.
2. Start the `.lnk`; screenshot with `ui.ps1`. Pass: panes show, `logs\` and `config\acommander.properties` are in the
   install folder, no ERROR in its log.
3. Add a line to its `UniExtract.ini` and `acommander.properties`, run step 1 again with the app open. Pass: Setup
   closes the app (its `/LOG` says "Shutting down applications"), both lines survive.
4. `unins000.exe /VERYSILENT /SUPPRESSMSGBOXES`. Pass: the folder, the `.lnk` and the uninstall key are gone.

Not covered (say why if asked): FTP (needs a server), hosts file (admin), Check Tool Updates (network), Report Bug
(sends a message), drag and drop (no key path).

## Report and follow-up

1. A table: row, pass/fail, what was seen.
2. Each failure: find the root cause, fix it, add a JUnit test where the logic allows (as for #193), open a GitHub
   issue, then rerun that row.
3. A wrong step in this table (a dialog changed, a label moved) is not an app bug: fix the row.
4. `env.ps1 stop` at the end.
