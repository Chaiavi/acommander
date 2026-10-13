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
  later keys miss the dialog (it looked like the dialog closed itself). If another app took the front meanwhile,
  pass `-title '<dialog title>'` instead: it activates that window. Never `-title 'A Commander'`: the user's own
  app has that title too.
- `foreground pid` is another app (Chrome) and the log shows no action for your keys: the user is at the machine
  (`GetLastInputInfo` idle under a second). Your keys went into their window. Stop, `env.ps1 stop`, and say which
  rows are left.
- Dialog radio buttons don't move with arrow keys (focus sits on the default button): `CLICK:` them.
- `TEXT:` sends ASCII only; Hebrew arrives as `?`. The Hebrew names are in the test files for that reason.
- `ui.ps1` turns Caps Lock off (it made `TEXT:renamed.txt` arrive as `RENAMED.TXT`) and switches the app window to
  the US layout while it sends keys, then back: under a Hebrew layout SendKeys can't type Latin letters, so `^+p`
  never arrived and `TEXT:` letters turned into other keys, while F-keys still worked.
- `ui.ps1` ends with `app windows: ...`, the app's visible window titles: it tells whether a dialog is still open
  without viewing a screenshot.
- Actions keep focus in the source pane. `CLICK:` a row to pick the other pane: pane rows are 28 px apart, the first
  file row at y=139, the right pane's names at x=1850 (3456 px wide window).
- Marks survive a cancelled action: `{HOME}{DOWN N}` then moves only the cursor and the next F-key acts on the
  marks (an F8 deleted two marked PDFs instead of the cursor row). `{ESC}` first; a finished action clears them.
- Typing in a pane starts its incremental filter; a stray key leaves a pane showing only `..`. `{ESC}` in that pane.
- Cursor to the Nth row: `{HOME}{DOWN N}` (row 0 is `..`). Rows shift when a test adds or removes files in `in\`.
  Rows 3-6: `nested` 1, `box.zip` 3, `notes.txt` 4, `picture.png` 5. From row 8 on: `big.bin` 3, `box.zip` 4,
  `notes.txt` 6, `picture.png` 8, `second.pdf` 9, `tone.wav` 10; after row 23 `renamed.txt` is 8. Right pane from
  row 19: `notes.txt` at `CLICK:1850,279`, `second.pdf` at `CLICK:1850,419`.
- Judge a step by the disk and the log (`Select-String -Path $t\logs\ALL.log -Pattern ' ERROR | WARN ' -CaseSensitive`;
  without `-Path` the path is taken as the pattern and fails), not by the screenshot alone.
- Shorthands for the session (`$t` = `%TEMP%\ac-apptest`, `$ap` = its pid):
  `function U($k,$n,$w=2500,[switch]$na){ $a=@($ap,$k,"$t\$n.png",$w); if($na){$a+='-noActivate'}; pwsh -NoProfile -File .github\skills\app-ui-test\ui.ps1 @a }`
  and `function L { Select-String -Path $t\logs\ALL.log -Pattern ' ERROR | WARN ' -CaseSensitive }`. Several `U`
  calls fit in one terminal command; the printed window titles show each step's result.
- A chat holds at most 20 images; one more fails the whole request ("Too many images in request"). Every viewed
  screenshot stays in the chat. Take screenshots freely (they go to disk) but VIEW one only when the disk, the log
  and the front window's size (printed by `ui.ps1`) can't tell you what happened. Budget about 15 views per chat;
  past that, hand the remaining rows to a new chat.
- `ui.ps1` prints `foreground pid=0 size=0x0` and the Bitmap "Parameter is not valid": the Windows session is
  locked (`Get-Process LogonUI` exists). Keys go nowhere; stop and say which rows are left.

## Release checklist

Run in this order: the steps that delete, move or rename files in `in\` come last. Shortcuts open their dialog;
confirm with `{ENTER}` unless the row says otherwise. Palette actions: `^+p`, `TEXT:<label>`, `{ENTER}`.

| # | Flow | Steps | Pass when |
|---|---|---|---|
| 1 | Start | screenshot | Both panes listed as above; no ERROR in the log |
| 2 | Select All / Invert / Unselect | `^a`, palette `TEXT:merge` (Esc), `^i`, `^+a` | `^a` marks all rows but `..`; status bar counts them; Merge PDF Files is NOT offered (mixed types) |
| 3 | Copy a file (F5) | cursor on `notes.txt`, `{F5}` (no dialog) | `out\notes.txt` |
| 4 | Copy a folder (F5) | cursor on `nested`, `{F5}` | `out\nested\deep.txt` |
| 5 | Clipboard copy | `picture.png`, `^c`, `{TAB}`, `^v`, `{TAB}` | `out\picture.png` |
| 6 | Duplicate (Alt+F6) | `notes.txt`, `%{F6}` | `in\notes_copy.txt` |
| 7 | Create Directory / File | `{F7}` `TEXT:made` `{ENTER}`; `%{F7}` `TEXT:made.txt` `{ENTER}` (both `-noActivate`) | `in\made\`, `in\made.txt` |
| 8 | Pack to Zip (F11) | `notes.txt`, `{F11}`, name dialog: `{ENTER}` (`-noActivate`) | `out\notes.zip` |
| 9 | Unpack (F12) | `box.zip`, `{F12}`; 7-Zip asks to replace `out\notes.txt`: Auto Rename `CLICK:446,311` | `out\notes_1.txt` |
| 10 | Browse a zip | `box.zip`, `{ENTER}`, `{DOWN}`, `{F5}`; conflict dialog: Overwrite All `CLICK:30,344`, `{ENTER}`; `{BACKSPACE}` | Pane shows `notes.txt` inside; `out\notes.txt` overwritten (its time becomes the zip entry's) |
| 11 | Split (Alt+F11) | `big.bin`, `%{F11}`, size field `CLICK:270,146` `^a` `TEXT:1m`, `{ENTER}` | `out\big.7z.001` to `.004`; three are 1 MB |
| 12 | Search for Files (F10) | `{F10}`, `TEXT:*.txt`, `{ENTER}`; close with Cancel `CLICK:963,532` | `notes.txt`, `notes_copy.txt`, `made.txt`, `שלום.txt`, `deep.txt` listed |
| 13 | Find in Files (Alt+F10) | `%{F10}`, `TEXT:needle`, `{ENTER}`; `{ENTER}` goes to the file | `notes.txt`, `notes_copy.txt` |
| 14 | View / Edit | `notes.txt`, `{F3}` (close `%{F4}` `-noActivate`), `{F4}` (close the same way) | Universal Viewer, then Notepad4 in front with the text |
| 15 | Analyze File | `picture.png`, palette `Analyze File` | Log: "Analyze File response ... PNG image data, 64 x 48" |
| 16 | Checksum File | `notes.txt`, palette `Checksum File`, `{ENTER}` (SHA256 default) | rhash exit 0 in the log; value equals `Get-FileHash` |
| 17 | Convert Graphics | `picture.png`, palette `Convert Graphics`, `{ENTER}` (JPEG is the default) | `out\picture_converted.jpg` selected in `out\`; focus stays in `in\` |
| 18 | Audio | `tone.wav`: palette `Media Info` (`{ENTER}` closes); `Convert Audio Files` (MP3 default), `{ENTER}`; `Trim Media`, `CLICK:130,152` (End), `TEXT:0:01`, `{ENTER}` | Info shows 00:00:03; `out\tone_converted.mp3`; `out\tone_trimmed.wav` of 1 s (`ffmpeg -i`) |
| 19 | PDF | `second.pdf`, `{INSERT}`, `{DOWN}`, `{INSERT}` (marks it and `ראשון.pdf`, cursor outline on `שלום.txt`), Merge PDF Files, `{ENTER}`. `CLICK:` `out\second.pdf`, Extract PDF Pages: All; again with `CLICK:24,181` `CLICK:175,211` `TEXT:1, 3`: Items Already Exist lists `_0001`, `_0003`, `{ENTER}` (Skip Existing); again with `CLICK:24,241` `CLICK:175,271` `^a` `TEXT:2` | `out\second.pdf` has 5 pages; `in\second_0001`..`_0005`, then unchanged; `_0001-0002`, `_0003-0004`, `_0005-0005` selected in `in\` |
| 20 | Compare Files | `notes.txt` left, `out\notes.txt` right, palette `Compare Files`, `{ENTER}` | ExamDiff's command line has both files; for identical files it shows only a small message box |
| 21 | Compare Folders | palette `Compare Folders`, `{ENTER}` | "Only left / Only right / Different" counts match `in\` vs `out\` (recursive; the empty `made\` counts as one left-only item) |
| 22 | App | `{F1}` (Esc closes); palette `Toggle Dark Mode` twice; palette Bookmark this path, `{ENTER}`; enter `nested`; Goto Bookmark, `{ENTER}`; Remove Bookmark, `{ENTER}` | Window "A Commander <version> Help"; `theme_mode` in `config\acommander.properties` flips and returns; log lists `in\` again; no `bookmark.` line left |
| 23 | Rename (F2) | `notes.txt`, `{F2}`, `^a`, `TEXT:renamed.txt`, `{ENTER}` | `in\renamed.txt`, no `notes.txt` |
| 24 | Move (F6) | `{END}` (`שלום.txt` sorts last), `{F6}` | `out\שלום.txt`, gone from `in\` |
| 25 | Delete (F8) / Wipe (Shift+F8) | `renamed.txt` `{F8}`: "Move "renamed.txt" to the Recycle Bin?" `{ENTER}`; `big.bin` `+{F8}`: "Wipe "big.bin"? ..." `{ENTER}`. Esc on either question keeps the file | Both gone from `in\` (`renamed.txt` in the Recycle Bin); sdelete64 in the log |

### Installer (after `dist`)

`pwsh -NoProfile -File .github\skills\app-ui-test\installer.ps1` runs the four steps below and prints PASS/FAIL for
each. It skips if `HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{1D54CF8C-10E1-4E2A-BD4D-5F4CFE658C1C}_is1`
exists: that is the user's real install, and a test install would take it over. It passes `/TASKS=""`: the desktop
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
