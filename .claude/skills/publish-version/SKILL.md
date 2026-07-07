---
name: publish-version
description: Publish the latest built shiroikuma-shosekietsuran APK as a GitHub release of the fork — create the version tag (no leading "v"), attach the APK, write/refresh a futokxkb-style README (fork header + "major additions" feature sections) and an exhaustive CHANGELOG, and switch the GitHub default branch to `custom` so the repo page lands on our work. Use when 白い熊 says publish / release / cut a version / ship this build / make a GitHub release / publish the latest build.
---

# Publish a 白い熊 書籍閲覧 version to GitHub

Turn the latest tested build into a public GitHub **release** of the fork
(`ShiroiKuma0/shiroikuma-shosekietsuran`): a bare version **tag** (no `v`), the APK as a
downloadable asset, a **futokxkb-style README** whose landing page sells our fork, an
**exhaustive CHANGELOG**, and a **default branch of `custom`** so the repo page shows our work
and not upstream's `main`.

> **This is outward-facing — it publishes to GitHub.** 白い熊 invoking this skill *is* the
> authorization. Still, summarise the exact version + assets first, then proceed. Never publish a
> build 白い熊 hasn't tested on-device.

> **No `Co-Authored-By: Claude` / "Generated with Claude" trailer** in commits or release notes —
> end at the last line of the body. (Global rule in `~/.claude/CLAUDE.md`.)

## What gets published

The **latest APK in `~/tmp/`** (`shiroikuma-shosekietsuran_<version>+<N>_arm64-v8a.apk`) — the build
just tested on-device. Derive the version from the **APK filename**, NOT `gradle.properties`
(whose `BUILD_NUMBER` is already the *next* number, because `buildApk` bumps it after building).

```bash
APK=$(ls -t ~/tmp/shiroikuma-shosekietsuran_*.apk 2>/dev/null | head -1)
VERSION=$(basename "$APK" | sed -E 's/^shiroikuma-shosekietsuran_(.+)_arm64-v8a\.apk$/\1/')   # e.g. 1.0.51+19
TAG="$VERSION"   # the tag is the BARE version — never a leading "v"
```

If `$APK` is empty, stop and tell 白い熊 there's no built APK to publish (run `/build-apk` first).

## Preconditions to check

1. **The APK matches `HEAD`.** If the working tree has uncommitted source changes, or `HEAD` was
   advanced past the build, warn — the safest path is to rebuild (`/build-apk`) so the published
   APK and the tag agree.
2. **On `custom`** (`git rev-parse --abbrev-ref HEAD` = `custom`) and pushed.
3. **The tag doesn't already exist** (`git tag -l "$TAG"` empty, and
   `gh release view "$TAG" -R ShiroiKuma0/shiroikuma-shosekietsuran` 404s). If it exists, confirm
   with 白い熊 before re-cutting.

## Steps

### 1. Land the GitHub repo page on our fork

Set the default branch to `custom` and give the repo our description (idempotent — run every time):

```bash
gh repo edit ShiroiKuma0/shiroikuma-shosekietsuran --default-branch custom
gh repo edit ShiroiKuma0/shiroikuma-shosekietsuran \
  --description "白い熊 書籍閲覧 — a fork of Episteme, the multi-platform document / e-book reader. Black-yellow UI, reading gestures, reworked library. Side-by-side installable, AGPL-3."
```

### 2. Write / refresh the README (futokxkb-style)

The README is the shop window. Model it **exactly** on `~/git/shiroikuma-futokxkb/README.md`:
a centered header, a one-line "**a fork of X with major additions:** …" pitch, a side-by-side
note, the latest-release badge, then **one section per major feature** — an emoji heading and a
short, vivid description of what it does for the reader. Close with a "**Built on Episteme**"
lineage/licence/credit section and a "**Building**" section.

**Pick the most important major updates against stock Episteme** — the things a user would care
about — and describe each in two or three friendly sentences (no changelog minutiae here; that's
the CHANGELOG's job). Keep the feature list **current**: on each publish, add a section for any
new major feature and drop nothing that still ships. As of this writing the majors are:

- **The 白い熊 書籍閲覧 UI** — a whole settings page that themes the app: pure black-yellow by
  default, RGBA colour pickers with one-tap recent swatches and live preview, external-font
  loading (each font shown in its own glyphs), and sliders for text size/weight, corner
  roundness and border thickness — everything with a live preview.
- **Read with taps and swipes** — tap the left/right third to turn pages, swipe the right third
  for font size and the left third for brightness (with a live on-page readout), configurable
  page-turn sounds on the Media channel, whole-line page snapping, and page turns that cross
  chapter boundaries. All toggleable.
- **A library you can shape** — switch between list and a cover-grid layout with live thumbnail /
  title / author sizing, filter by author or tag from prefilled dropdowns, and long-press any
  book for a full-screen details sheet with edit, share-file and save-a-copy.
- **Your books' own metadata & tags** — the embedded `dc:subject` / genre entries become library
  tags automatically, and publication date, rating, publisher, language and ISBN are read
  straight from the file; publication date and tags are editable and the author field
  auto-completes from your library.

Update only the badge line when nothing major changed:

```
**📥 Latest release: [`<VERSION>`](https://github.com/ShiroiKuma0/shiroikuma-shosekietsuran/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-shosekietsuran/releases)
```

**README skeleton** (adapt copy; keep the structure). Note the four-backtick outer fence so the
inner shell fences survive:

````markdown
<div align="center">

# 白い熊 書籍閲覧

**A black-and-yellow, gesture-driven document & e-book reader — tuned for focused reading.**

A fork of [Episteme](https://github.com/Aryan-Raj3112/episteme) with **major additions**: a full
black-yellow UI customizer, tap/swipe reading gestures with page-turn sounds, a reworked library
with a cover grid and author/tag filters, rich in-app book-details editing, and automatic import
of the tags already inside your books.

Installs **side-by-side** with the official Episteme build (Android package
`shiroikuma.shosekietsuran`).

**📥 Latest release: [`<VERSION>`](https://github.com/ShiroiKuma0/shiroikuma-shosekietsuran/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-shosekietsuran/releases)

</div>

---

## 🎨 The 白い熊 書籍閲覧 UI — theme every pixel

<short description>

## 👆 Read with taps and swipes

<short description>

## 📚 A library you can shape

<short description>

## 🏷 Your books' own metadata & tags

<short description>

## 📖 Real page turns

<short description>

---

## Built on Episteme

This project is a fork of [Episteme](https://github.com/Aryan-Raj3112/episteme) (Android package
`shiroikuma.shosekietsuran`, so it coexists with the official build) — a multi-platform document /
e-book reader. All upstream work and the project's mission belong to the Episteme author; see the
[upstream repository](https://github.com/Aryan-Raj3112/episteme) for issues, contributing and the
canonical source. The code remains under the **GNU Affero General Public License v3 (AGPL-3.0)**.

## Building

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=<your-android-sdk>
./gradlew :app:assembleOssDebug     # fast debug build
./gradlew buildApk                  # signed oss release APK
```
````

### 3. Write / refresh the CHANGELOG — exhaustive and specific

`CHANGELOG.md` is the opposite of the README: **list everything we have built on top of stock,
in specifics.** Rename the existing `## <old> — current` heading to the released `<VERSION>` and
add a fresh `## <new> — current` section above it, grouped by area (UI page / gestures / library /
metadata / theming / build), **one concrete bullet per real change** since the previous tag:

```bash
git log --oneline <previous-tag>..HEAD    # source the specifics from real commits
```

On the **very first publish** there is no previous tag — enumerate the **entire fork layer**
exhaustively: every setting added, every gesture, every dialog, every default changed, the
versioning/signing/branding scheme, the build & delivery pipeline. Err on the side of listing too
much; the CHANGELOG is where completeness lives.

### 4. Commit the docs on `custom` and push

```bash
git add README.md CHANGELOG.md
git commit -m "Release <VERSION>: README + changelog"
git push origin custom
```

### 5. Tag and release (tag has NO leading "v")

Annotated tag at `HEAD`, then a GitHub release targeting `custom` with the APK attached and the
new CHANGELOG section as the notes. **Always pin the repo with
`-R ShiroiKuma0/shiroikuma-shosekietsuran`** — the working copy has an `upstream` remote and a bare
`gh release` may resolve against it.

```bash
REPO=ShiroiKuma0/shiroikuma-shosekietsuran
git tag -a "$TAG" -m "白い熊 書籍閲覧 $VERSION"
git push origin "$TAG"
NOTES="$HOME/tmp/shosekietsuran_release_notes.md"
sed -n "/^## ${VERSION} —/,/^## [0-9]/p" CHANGELOG.md | sed '/^## [0-9]/d' | tail -n +2 > "$NOTES"
gh release create "$TAG" "$APK" -R "$REPO" \
  --target custom \
  --title "白い熊 書籍閲覧 $VERSION" \
  --notes-file "$NOTES"
rm -f "$NOTES"
```

Keep the APK asset name as built (`shiroikuma-shosekietsuran_<VERSION>_arm64-v8a.apk`).

### 6. Report

```bash
gh release view "$TAG" -R ShiroiKuma0/shiroikuma-shosekietsuran --json url -q .url
gh repo view ShiroiKuma0/shiroikuma-shosekietsuran --json defaultBranchRef -q .defaultBranchRef.name
```

Give 白い熊 the release URL and confirm the default branch is `custom`.

## Notes

- **Bare tags only — never a leading `v`.** `TAG="$VERSION"`, e.g. `1.0.51+19`.
- `git push`, `gh` and `scp` need `~/.ssh` / `~/.config/gh`, which the command sandbox blocks —
  run those steps with the sandbox disabled, same as the other fork skills.
- This skill **does not build** — it ships whatever is newest in `~/tmp/`. For a fresh build
  first, that's `/build-apk`.
- `main` stays tracking upstream; releases are always cut from `custom`. After an
  `/upstream-new-version` rebase, the first release on the new base resets the build number to `+1`.
