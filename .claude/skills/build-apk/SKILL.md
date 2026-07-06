---
name: build-apk
description: Build the signed release APK of shiroikuma-shosekietsuran (the 「白い熊 書籍閲覧」 document / e-book reader — a fork of Episteme) with the `buildApk` Gradle task, then deliver it automatically via the global /after-build skill (adb push if a phone is connected, else scp to skhw — no prompt). Always build first without asking permission to build. Use whenever the user asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the shosekietsuran release APK and deliver it

> **Never ask whether to build — just build.** When this skill applies (the user asked to build,
> or you've made changes ready to test), run the build immediately. Do **not** ask "shall I
> build?". There is **no** transfer question either: after a successful build, deliver the APK
> automatically via the global **`/after-build`** skill — no prompts at all.

> **The push destination is ALWAYS `/sdcard/tmp/`.** Never `/sdcard/Download/`.

> **Never run `adb install` (or `pm install`).** The user installs the APK themselves from the
> phone's file manager.

> **Never `git commit` or `git push` on your own.** Building does not include committing. Only
> when the user explicitly says **"Push"** do you `git commit` + `git push origin custom`.

## Build environment (this machine)

- The default `java` is **JDK 11**, which cannot run recent Gradle. Always export JDK 21.
- The Android SDK is **not** on a default env var; export `ANDROID_HOME` explicitly.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/shiroikuma/android-sdk
```

## Steps

1. **Note the output filename / version.**
   - `grep BUILD_NUMBER gradle.properties` — our per-build counter (the value **before** the
     build; `buildApk` bumps it afterward).
   - Upstream's base version lives in `app/build.gradle.kts` → `defaultConfig`
     (`versionCode` / `versionName` literals, e.g. `55` / `"1.0.51"`).
   - The APK will be `shiroikuma-shosekietsuran_<upstreamVersion>+<BUILD_NUMBER>_arm64-v8a.apk`; its
     versionCode = `<upstream versionCode> * 10000 + BUILD_NUMBER` (e.g. `550001`).

2. **Build** (oss flavor, release, signed) — from the repo root:
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk
   ./gradlew buildApk --console=plain < /dev/null
   ```
   - `buildApk` runs `assembleOssRelease`, copies the signed APK to `~/tmp/<apk name>`, and
     auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - It prints `>>> <path>` and `>>> versionCode <n>` (cyan) — use those to confirm the exact
     filename/code; confirm `BUILD SUCCESSFUL`.
   - A cold build (fresh checkout / after `git clean`) downloads deps and compiles the native
     (CMake) parts — it can take a while; run it with `run_in_background` if it may exceed the
     foreground timeout. Subsequent builds are much faster.
   - **Fast dev iteration:** `./gradlew :app:assembleOssDebug` produces a debug-signed APK at
     `app/build/outputs/apk/oss/debug/` (no R8, faster) — useful while iterating, but the
     shippable build is `buildApk` (release-signed).

3. **At the end of every build, deliver the APK via `/after-build`** — no exceptions, no asking.
   As soon as `BUILD SUCCESSFUL` appears and the signed APK is in `~/tmp/`, invoke the global
   **`/after-build`** skill; it picks adb-push (phone connected) or scp-to-skhw on its own and
   announces what landed.

## Signing

Release signing is non-interactive: the fork block at the top of `app/build.gradle.kts` maps the
gitignored `keystore.properties` (repo root) onto upstream's `MYAPP_RELEASE_*` local.properties
mechanism. The keystore is `~/.android-keystores/shiroikuma-shosekietsuran.jks` (alias `shosekietsuran`); the
password is recorded in 白い熊's keystore org file (`~/〇/[666] 私資料/[666][27] 暗号/`), and the
jks is backed up there too. If `keystore.properties` is absent the release build is **unsigned**
and won't install — restore it (`storeFile` / `storePassword` / `keyAlias` / `keyPassword`).

## Versioning (how the numbers are formed)

- Upstream's `versionCode` / `versionName` literals in `app/build.gradle.kts` →
  `defaultConfig` track upstream Episteme; the fork lines directly below transform them in place.
- `BUILD_NUMBER` (`gradle.properties`) is **our** increment: bumped on every `buildApk`, reset to
  `1` on each new upstream version (see the `upstream-new-version` skill).
- Fork `versionName = "<upstream>+<BUILD_NUMBER>"`;
  `versionCode = <upstream versionCode> * 10000 + BUILD_NUMBER` (Episteme 55 → `550001`, …).
- The upstream `.oss` appId suffix and `-oss` versionName suffix are removed — the installed id
  is exactly `shiroikuma.shosekietsuran`.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line
of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
