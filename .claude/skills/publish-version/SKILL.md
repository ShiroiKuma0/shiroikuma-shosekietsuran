---
name: publish-version
description: Publish the latest built shiroikuma-etsuran APK as a GitHub release of the fork — create the version tag, attach the APK, update the README and CHANGELOG, ensure the GitHub default branch is `custom` so the repo page lands on our work, and write specific release notes. Use when the user says publish / release / cut a version / ship this build / make a GitHub release / publish the latest build.
---

# Publish an etsuran version to GitHub

Turn the latest tested build into a public GitHub **release** of the fork
(`ShiroiKuma0/shiroikuma-etsuran`): a version tag, the APK as a downloadable asset, an updated
README + CHANGELOG, and a default branch (`custom`) so the repo landing page shows our work.

> **This is outward-facing — it publishes to GitHub.** The user invoking this skill *is* the
> authorization. Still, summarise the exact version + assets first, then proceed. Never publish a
> build the user hasn't tested.

> **No `Co-Authored-By: Claude` / "Generated with Claude" trailer** in commits or release notes —
> end at the last line of the body. (Global rule.)

## What gets published

The **latest APK in `~/tmp/`** (`shiroikuma-etsuran_<version>+<N>_arm64-v8a.apk`) — the build the
user just tested on-device. Derive the version from the **APK filename**, NOT `gradle.properties`
(whose `BUILD_NUMBER` is already the *next* number, because `buildApk` bumps it after building).

```bash
APK=$(ls -t ~/tmp/shiroikuma-etsuran_*.apk 2>/dev/null | head -1)
VERSION=$(basename "$APK" | sed -E 's/^shiroikuma-etsuran_(.+)_arm64-v8a\.apk$/\1/')   # e.g. 1.0.51+3
TAG="$VERSION"   # the tag is the bare version, no "v" prefix
```

If `$APK` is empty, stop and tell the user there's no built APK to publish (run `build-apk` first).

## Preconditions to check

1. **The APK matches `HEAD`.** If the working tree has uncommitted source changes, or `HEAD` was
   advanced past the build, warn — the safest path is to rebuild (`build-apk`) so the published
   APK and the tag agree.
2. **On `custom`** (`git rev-parse --abbrev-ref HEAD` = `custom`) and pushed.
3. **The tag doesn't already exist** (`git tag -l "$TAG"` empty, and `gh release view "$TAG"`
   404s). If it exists, confirm with the user before re-cutting.

## Steps

1. **Ensure the GitHub default branch is `custom`** so the repo page lands on our README, not
   upstream's `main`:
   ```bash
   gh repo edit ShiroiKuma0/shiroikuma-etsuran --default-branch custom
   gh repo edit ShiroiKuma0/shiroikuma-etsuran \
     --description "白い熊 閲覧 — a fork of Episteme, the multi-platform document / e-book reader. Side-by-side installable, AGPL-3."
   ```
   (Idempotent — safe to run every time.)

2. **Update the README badge.** Point the "Latest release" line at the new version
   (`📥 Latest release: [\`<VERSION>\`](…/releases/latest)`). On the first publish, write a
   fork-style README (fork header + what-differs section, in the style of the sister forks).

3. **Update `CHANGELOG.md`.** Keep it **specific — list everything** built on top of stock.
   Rename the `## <old> — current` heading to the released version and add a fresh
   `## <new> — current` section above it summarising what changed **since the last tag**
   (`git log --oneline <previous-tag>..HEAD`), grouped by area, one specific bullet each. On the
   very first publish there is no previous tag — enumerate the whole fork layer.

4. **Commit the docs** on `custom` and push:
   ```bash
   git add README.md CHANGELOG.md
   git commit -m "Release <VERSION>: README + changelog"
   git push origin custom
   ```

5. **Tag and release.** Annotated tag at `HEAD`, then a GitHub release targeting `custom` with
   the APK attached and the new CHANGELOG section as the notes. **Always pin the repo with
   `-R ShiroiKuma0/shiroikuma-etsuran`** — the working copy has an `upstream` remote, and bare
   `gh release` may otherwise resolve against upstream. Write the notes to a real file under
   `~/tmp`:
   ```bash
   REPO=ShiroiKuma0/shiroikuma-etsuran
   git tag -a "$TAG" -m "白い熊 閲覧 $VERSION"
   git push origin "$TAG"
   NOTES="$HOME/tmp/etsuran_release_notes.md"
   sed -n "/^## ${VERSION} —/,/^## [0-9]/p" CHANGELOG.md | sed '/^## [0-9]/d' | tail -n +2 > "$NOTES"
   gh release create "$TAG" "$APK" -R "$REPO" \
     --target custom \
     --title "白い熊 閲覧 $VERSION" \
     --notes-file "$NOTES"
   rm -f "$NOTES"
   ```
   Keep the APK asset name as built (`shiroikuma-etsuran_<VERSION>_arm64-v8a.apk`).

6. **Report** the release URL and confirm the default branch:
   ```bash
   gh release view "$TAG" -R ShiroiKuma0/shiroikuma-etsuran --json url -q .url
   gh repo view ShiroiKuma0/shiroikuma-etsuran --json defaultBranchRef -q .defaultBranchRef.name
   ```

## Notes

- `git push`, `gh` and `scp` need `~/.ssh` / `~/.config/gh`, which the command sandbox blocks —
  run those steps with the sandbox disabled, same as the other fork skills.
- This skill **does not build** — it ships whatever is newest in `~/tmp/`. If the user wants a
  fresh build first, that's the `build-apk` skill's job.
- `main` stays tracking upstream; releases are always cut from `custom`. After an
  `upstream-new-version` rebase, the first release on the new base resets the build number to `+1`.
