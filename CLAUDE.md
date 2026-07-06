# shiroikuma-etsuran

A fork of [Aryan-Raj3112/episteme](https://github.com/Aryan-Raj3112/episteme) (AGPL-3.0) — a
multi-platform document / e-book reader. Our Android build: package `shiroikuma.etsuran`, label
**「白い熊 閲覧」**, installable side-by-side with upstream Episteme.

## Branch & remote model (same as the sister forks)

- `origin` = `git@github.com:ShiroiKuma0/shiroikuma-etsuran.git` (ssh) — our fork.
- `upstream` = `https://github.com/Aryan-Raj3112/episteme.git` (https, fetch only).
- **`main`** mirrors upstream Android releases by **tag** (pattern `v<X.Y.Z>-oss`, e.g.
  `v1.0.51-oss`; ignore the `windows-*` tags). No fork work lives on `main`.
- **`custom`** carries all our work, rebased onto `main` on each new upstream release. **All
  development happens on `custom`.**
- **Do not rename the `com.aryan.reader` code namespace** — only the installed `applicationId`
  differs (`shiroikuma.etsuran`). Renaming would make every rebase a mass-conflict.

## Skills (`.claude/skills/`)

- **`build-apk`** — build the signed oss release APK via the `buildApk` Gradle task, then deliver
  it automatically via the global `/after-build` skill (adb push to `/sdcard/tmp/` if a phone is
  connected, else scp to skhw) — **no transfer prompt**.
- **`upstream-new-version`** — check upstream for a newer `v*-oss` tag, present a **proceed-gated
  table of the new upstream features** (mandatory, before any rebase), then advance `main`, rebase
  `custom`, reset `BUILD_NUMBER`, build the new `+1`.
- **`publish-version`** — publish the latest tested APK as a GitHub release of the fork; keep the
  GitHub default branch on `custom`. Pin `gh` with `-R ShiroiKuma0/shiroikuma-etsuran`.

## Build, versioning, signing

- **Build env (this machine):** default `java` is JDK 11. Always:
  `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk`.
- **Build:** `./gradlew buildApk` (assembles `ossRelease`, signed; copies the APK to `~/tmp` and
  bumps `BUILD_NUMBER`). Fast dev iteration: `./gradlew :app:assembleOssDebug`.
- **Versioning:** upstream's `versionCode` / `versionName` literals stay in
  `app/build.gradle.kts` → `defaultConfig` (rebase-friendly); the fork lines directly below them
  derive the real values: `versionName = "<upstream>+<BUILD_NUMBER>"`,
  `versionCode = <upstream> * 10000 + BUILD_NUMBER` (55 → `550001`, …). `BUILD_NUMBER` lives in
  `gradle.properties`, is bumped by every `buildApk`, and resets to `1` on each new upstream
  version. The upstream `.oss` appId suffix and `-oss` versionName suffix are removed in the `oss`
  flavor.
- **APK filename:** `shiroikuma-etsuran_<versionName>_arm64-v8a.apk` (single-ABI arm64 build via
  `ndk.abiFilters`).
- **Signing:** release signed from gitignored `keystore.properties` →
  `~/.android-keystores/shiroikuma-etsuran.jks` (alias `etsuran`). The properties are mapped onto
  upstream's `MYAPP_RELEASE_*` local.properties mechanism, so upstream's signing blocks stay
  untouched. Password recorded in 白い熊's keystore org file; jks backed up alongside it.
- **Label:** `app_name` **and** `app_name_oss` in `app/src/main/res/values/strings.xml` (the
  launcher uses `app_name`, the in-app home header uses `app_name_oss`).
- **Delivery:** APK lands in `~/tmp`, then `/after-build` pushes it to `/sdcard/tmp/`; **the user
  installs from the on-device file manager** (never `adb install`).

## Working rules (override harness defaults where noted)

- **No `Co-Authored-By: Claude` / "Generated with Claude" trailer** in commits or PR bodies — end
  the message at the last line of the body. (Overrides the harness default; global rule in
  `~/.claude/CLAUDE.md`.)
- **Never commit or push until the user says "Push".** Treat the working tree as scratch between
  "Push" commands. "Push" = `git commit` + `git push origin custom` (and `main` after an upstream
  sync). The user tests each build on-device first.
- **After every successful build, deliver the APK automatically via `/after-build`** — never ask
  how to transfer it.
- **Never `adb install` / `adb uninstall`** — 白い熊 installs manually from `/sdcard/tmp/`.
