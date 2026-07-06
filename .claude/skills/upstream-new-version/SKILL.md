---
name: upstream-new-version
description: Rebase the shiroikuma-etsuran fork onto a new upstream release of Aryan-Raj3112/episteme (the document / e-book reader this is forked from). Use when 白い熊 says a new upstream Episteme version is out, asks to update/sync to upstream, bump to the new release, check for a new version, or rebase custom onto the latest upstream tag. ALWAYS presents a proceed-gated table of the new upstream features BEFORE rebasing.
---

# Rebase the fork onto a new upstream Episteme release

Goal: move `main` to the new upstream Android release tag, replay our `custom` customizations on
top, and produce a fresh `+1` build.

> **Never `git push` or `git commit` unprompted, and never `adb install`.** Same hard rules as
> everyday development (see `CLAUDE.md`). After the rebase + build you stop and let 白い熊 test;
> you only push when they explicitly say **"Push"**.

## Background — branch model & versioning

- `upstream` = `https://github.com/Aryan-Raj3112/episteme` (https). `origin` =
  `git@github.com:ShiroiKuma0/shiroikuma-etsuran.git` (ssh).
- **`main` tracks upstream Android releases by TAG** — pattern `v<X.Y.Z>-oss` (e.g.
  `v1.0.51-oss`). **Ignore the `windows-*` tags** (desktop releases). We base on the latest
  Android release tag, not bleeding `upstream/main`.
- **`custom`** carries all our work, rebased onto `main` on each new release.
- Upstream's `versionCode` / `versionName` literals live in `app/build.gradle.kts` →
  `defaultConfig` and flow in from the rebase; our fork lines directly below transform them:
  `versionName = "<upstream>+<BUILD_NUMBER>"`, `versionCode = <upstream> * 10000 + BUILD_NUMBER`.
- `BUILD_NUMBER` (`gradle.properties`) is **our** fork increment; it **resets to `1`** on each
  new upstream version. So when upstream's code climbs (55 → 56), the new line's codes
  (`560001`, …) all exceed the previous line's (`550001`, …), keeping upgrades monotonic.

## Steps

1. **Check for a newer upstream release:**
   - `git fetch upstream --tags`
   - `git tag --sort=-version:refname | grep -- '-oss$' | head` — newest Android release tag.
     Compare against our current base (the commit `main` points at).
   - Read the new tag's declared version:
     `git show <newtag>:app/build.gradle.kts | grep -E 'versionCode|versionName'`.
   - If nothing newer than our base, stop and report "already current".

2. **★ Proceed gate — new-features table (MANDATORY, before ANY rebasing).**
   Compile a **descriptive table of what the new upstream version introduces** and present it to
   白い熊, then **stop and wait for an explicit go-ahead** before touching `main` or `custom`.
   - Sources: the GitHub release notes of every tag between our base and the new one
     (`gh api repos/Aryan-Raj3112/episteme/releases --paginate` or `gh release view <tag> -R
     Aryan-Raj3112/episteme`), plus the commit log `git log --oneline main..<newtag>` and, where a
     commit subject is opaque, the PR / diff behind it.
   - Table shape (one row per notable feature / change, grouped: features first, then fixes):

     | Area | Change | What it does / why it matters | Impact on our patches |
     | --- | --- | --- | --- |

   - Cover **every** release between the old and new base, not just the newest.
   - End with the explicit question: **“Proceed with the rebase to `<newtag>`?”** — and do **not**
     continue until 白い熊 answers affirmatively. If they decline, stop; nothing has been touched.

3. **Advance `main` to the new release tag** (no fork work lives on `main`):
   - `git checkout -B main <newtag>`

4. **Rebase `custom` onto the new `main`:**
   - `git checkout custom`
   - `git rebase main`
   - Resolve conflicts so **all** our customizations survive (see the table below). Reconcile,
     don't drop. If upstream restructured a file we patch, port our change to the new structure
     rather than forcing the old diff. **If conflicts are significant, stop and plan with 白い熊**
     before continuing.
   - Upstream's new `versionCode` / `versionName` literals flow in automatically — keep
     **upstream's** values for those two lines; our fork lines below them do the transformation,
     so we never edit the literals by hand.

5. **Reset the build tail:**
   - In `gradle.properties`, set **`BUILD_NUMBER=1`** (new upstream line starts its `+N` at 1).

6. **Verify our customizations are intact** after resolving the rebase:

   | What | Expected value | Where |
   | --- | --- | --- |
   | Installed app id | `shiroikuma.etsuran` | `app/build.gradle.kts` → `defaultConfig.applicationId` |
   | Code namespace | `com.aryan.reader` (unchanged from upstream — never rename) | `app/build.gradle.kts` → `namespace` |
   | oss flavor suffixes removed | no `applicationIdSuffix` / `versionNameSuffix` | `app/build.gradle.kts` → `productFlavors.oss` |
   | App label | `白い熊 閲覧` | `app_name` **and** `app_name_oss` in `app/src/main/res/values/strings.xml` |
   | Launcher icon | yellow-on-black traced book + play badge | `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml` |
   | Fork version lines | `versionName = "$versionName+$forkBuildNumber"`, `versionCode = versionCode!! * 10000 + forkBuildNumber` | `app/build.gradle.kts` → `defaultConfig` |
   | Single-ABI | `ndk { abiFilters += "arm64-v8a" }` | `app/build.gradle.kts` → `defaultConfig` |
   | Signing bridge | keystore.properties → `MYAPP_RELEASE_*` mapping block | top of `app/build.gradle.kts` |
   | APK filename | `shiroikuma-etsuran_<version>_arm64-v8a.apk` | `app/build.gradle.kts` → `applicationVariants.all` |
   | `buildApk` task | copies to `~/tmp`, bumps `BUILD_NUMBER` | end of `app/build.gradle.kts` |
   | Build tail | `BUILD_NUMBER=1` | `gradle.properties` |
   | Committed agent files | `CLAUDE.md`, `.claude/` in; `keystore.properties`, `.claude/settings.local.json` ignored | `.gitignore` |
   | Feature patches | every shipped customization since | their source files |

   Conflict-prone files: `app/build.gradle.kts`, `gradle.properties`, `.gitignore`,
   `app/src/main/res/values/strings.xml`, and any reader source we patch as development proceeds.

   Sanity check the build script still evaluates:
   `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew :app:tasks --console=plain | head`.

7. **Build the new `+1`** via the **build-apk** skill
   (`./gradlew buildApk < /dev/null` with the JDK-21 / ANDROID_HOME env); build-apk then delivers
   the APK automatically via `/after-build` (adb push if a phone is connected, else scp to skhw —
   no prompt). This is the first build of the new upstream line (`<newVersion>+1`).

8. **Stop.** Let 白い熊 test. Commit/push only on their explicit **"Push"**. Because the rebase
   rewrites `custom`'s history: `git push --force-with-lease origin custom`; `main` is
   `git push origin main` (new tag base).

## Notes

- Keep our changes a **small, legible layer** on top of upstream — prefer rebasing (linear
  history) over merging, so the customization set stays easy to audit and replay.
- Do **not** rename the `com.aryan.reader` code namespace (only the installed `applicationId`
  differs) — renaming would make every rebase a mass-conflict.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line
of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
