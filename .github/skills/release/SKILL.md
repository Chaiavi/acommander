---
name: release
description: "Release a new ACommander version: update tools, bump the version, write notes, test, tag, and publish the GitHub release that a workflow builds. Use when the user asks to release, deploy or publish a version."
---
# Release a Version

The tag push makes [release.yml](../../workflows/release.yml) build the setup and the ZIP on a clean Windows
machine and create a **draft** release. Testing stays here: the UI checklist needs a real desktop.
Two steps go out to every user and need the user's "yes" in chat first: pushing the tag, and publishing.

`$v` below is the new version (`5.0`). Run `gh` with `$env:Path += ";$env:LOCALAPPDATA\Programs\gh\bin"` if missing.

## Steps

1. Preconditions. Stop and report any that fail:
   1. `git status --short` lists nothing of yours, `main` is pushed.
   2. The milestone has no open issues:
      `gh api "repos/Chaiavi/acommander/milestones?state=open" --jq '.[] | "\(.title): \(.open_issues) open"'`.
2. Run [update-tools](../update-tools/SKILL.md).
3. Set `version=$v` in `src/main/resources/app-version.properties`.
4. Notes:
   1. `pwsh -NoProfile -File .github\skills\release\notes.ps1 -Version $v -OutFile $env:TEMP\notes-v$v.md`.
      It lists the milestone's closed issues by label. Always use `-OutFile`: a console pipe turns the emoji into `??`.
   2. Polish that file like the v4.5 release: an emoji and a short user-facing title per line, then ` — ` and one
      sentence on what changed for the user. Move a line to the right group if its label was wrong. Many issues:
      merge related ones into one line that lists all their links. Write a one-line summary under the date.
   3. Add a `<details>` entry for `v$v` at the top of README "Release History", plain (no emoji), as the v4.5 one.
5. `.\gradlew.bat build`, then the release checklist in [app-ui-test](../app-ui-test/SKILL.md) (rows 1-25).
   Fix failures first; a fix goes in its own commit with its issue.
6. `git commit -m "Release v$v" -- src/main/resources/app-version.properties README.md`, push. Wait for CI:
   `gh run list --branch main --limit 1`, then `gh run watch <id> --exit-status`.
7. **Ask the user**, then `git tag -a v$v -m "v$v"` and `git push origin v$v`.
   Follow the release run: `gh run list --workflow release.yml --limit 1`, `gh run watch <id> --exit-status`.
8. Test what users get: `gh release download v$v --pattern "*setup*" --dir $env:TEMP\ac-rel --clobber`, then
   `pwsh -NoProfile -File .github\skills\app-ui-test\installer.ps1 -SetupPath $env:TEMP\ac-rel\acommander-setup-v$v.exe`.
   All four PASS.
9. `gh release edit v$v --notes-file $env:TEMP\notes-v$v.md`.
10. **Ask the user**, then `gh release edit v$v --draft=false --latest`. Check
    `gh release view --json tagName` shows `v$v`.
11. Milestones: close this one
    (`gh api -X PATCH repos/Chaiavi/acommander/milestones/<number> -f state=closed`) and create the next one the user
    names (`gh api repos/Chaiavi/acommander/milestones -f title="v<next> <name>"`). New issues go to the oldest open
    milestone (AGENTS.md), so nothing else changes.
12. Remove `$env:TEMP\ac-rel` and the notes file. Report the release link.

## When something fails

- The release run fails on "does not match version": the tag and `app-version.properties` differ. Delete the tag
  (`git push origin :refs/tags/v$v`, `git tag -d v$v`), fix, tag again.
- The release run fails in the build: read `gh run view <id> --log-failed`, fix on `main`, delete and recreate the tag
  as above. A draft the run made is deleted with `gh release delete v$v --yes` (only while it is a draft).
- Actions is down: `.\gradlew.bat dist`, then the `gh release create` line from `release.yml` with the local files.
