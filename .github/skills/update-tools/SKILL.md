---
name: update-tools
description: "Update the bundled tools under apps/ to their newest upstream versions, test each one, and commit and push the ones that pass. Use when the user asks to update the tools, or when `build` reports bundled tools with a newer release."
---
# Update the Bundled Tools

One tool at a time, so a bad update is held back alone and can be reverted alone.

## Steps

1. Start from a clean tree: `git status --short` lists nothing of yours. Other sessions' files stay untouched.
2. List what is newer: `.\gradlew.bat checkToolUpdates`. The `*` rows are the candidates.
3. For each candidate `<id>` (apps.json `tools` id):
   1. `.\gradlew.bat upgradeTools -Ptools=<id>`. It prints `UPGRADED`, `UP TO DATE` or `FAILED <id>: <why>`.
      - `no automatic download` (RHash: SourceForge blocks scripts): tell the user where to get it; skip.
      - Any other failure: nothing changed; report it and go to the next tool.
   2. `.\gradlew.bat build guiToolTest`. `build` rewrites `apps/tools.sha256` and runs the command-line tool tests;
      `guiToolTest` runs the tools that open a window.
   3. Passed: `git status --short` must list only the tool's files, `config/apps.json`, `apps/tools.sha256` (and for
      ffmpeg `build.gradle`). A tracked file a tool rewrote on its run belongs in `toolSettingsTemplates` (AGENTS.md).
      Commit by path, `Update <Tool name> to <version>`, and push.
      ffmpeg: also `.\gradlew.bat publishFfmpeg` after the push, so Tool Updates can download it.
   4. Failed: restore exactly that tool's paths and the two lists: `git checkout -- <tool paths> config/apps.json
      apps/tools.sha256` (plus `build.gradle` for ffmpeg), and `git clean -f <tool paths>` for a file the upgrade
      added. Report the failing test and its message.
4. Summary for the user: updated (old → new), failed (why), skipped (why).

## Pitfalls

- A new upstream layout (renamed exe, a DLL moved) shows as `<name> is not in <download>`. Set `upstream.files` in
  apps.json to the names to take, then retry.
- An upstream that changed its download page shows as `no version matching` or a missing download: fix `pattern`,
  `url` or `downloadPattern` in apps.json; `ToolUpstreamCheckTest` shows the expected shapes.
