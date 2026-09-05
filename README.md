<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="120" alt="白い熊 書籍閲覧 icon" />

# 白い熊 書籍閲覧

**A black-and-yellow e-book reader that reads Japanese the way Japan prints it.**

A fork of [Episteme](https://github.com/Aryan-Raj3112/episteme) with **major additions**: tategaki 縦書き vertical-text rendering, parallel reading of up to three books in nine screen layouts, a cross-book annotation library, page-turn animations with a real paper curl, whole-line page views, a fully themeable black×yellow UI, one-ZIP backup of the whole library and every setting (headless automation, and app-data backup that survives a wipe), tap/swipe reading gestures with page-turn sound, a remade library with a grab-anywhere fast scroller, and metadata that writes back into the book files.

Installs **side-by-side** with Episteme (app id `shiroikuma.shosekietsuran`).

**📥 Latest release: [`1.0.54+003`](https://github.com/ShiroiKuma0/shiroikuma-shosekietsuran/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-shosekietsuran/releases)

</div>

---

## 📜 Tategaki 縦書き — real Japanese vertical text
Vertical-writing EPUBs render as true tategaki: columns flow top-to-bottom, right-to-left, on an exact character grid with print typography — strict kinsoku line breaking, one-character paragraph indents, and a 振り仮名の余白 toggle that either gives furigana-bearing lines their annotation gap (print style) or keeps a strictly uniform column pitch. Left tap pages forward, the way a Japanese book turns. A unified reading-mode menu (with its own toolbar icon) offers Scroll, 縦書き, native scroll, and both page-flip directions — one choice sets layout and writing direction, remembered per book.

## 📖📖 Parallel reading — up to three books at once, nine layouts
Pick two or three books (the same novel in two languages, say), and flip between them with a one-finger swipe, each keeping its own position. Or share the screen: a layout chooser in the reader's tab bar offers one book at a time, two or three side by side or stacked, and four mixed one-plus-two arrangements — with **draggable dividers** to give any book more room, remembered across sessions. Companion panes have full gesture parity (scroll, tap page turns, font and brightness swipes) and follow the same format settings live.

## 🗂️ Annotation library — study every highlight in one place
One screen gathers every text highlight and note from every book, live — nothing to sync or rebuild. Fold and unfold collapsible groups by book, by tag, or by color; search across highlighted text, notes, tags and titles as you type; organize with free-form tags. Tap any annotation and the book opens at that exact spot.

## 📃 Page turns that behave like paper
Five page-turn styles — instant, slide, fade, flip over the spine, and a deluxe **page curl** that folds the paper over a sweeping crease, showing the washed-out backside with true fold shadows — with a speed slider (150–1500 ms) and a live animated preview in the settings. Pages end on whole lines: a line that would be cut at the bottom (or a tategaki column at the left edge) is hidden and leads the next page instead, and short flips at chapter ends smooth-scroll the remainder so your eyes never lose the text. Tiny ornament images keep their intended size (no more page-wide blurs), and all-black separator glyphs repaint themselves yellow on dark pages.

## 🐻 白い熊 UI — black and yellow, all the way down
Pure-black surfaces, pure-yellow text, accents and borders — and every one of those colors adjustable from a dedicated settings page with RGBA pickers, recent-color memory and live preview. Typography scale and weight, corner roundness, and border thickness are sliders; the yellow frame follows you into every dialog, sheet, menu and banner in the app. The page gathers **every fork setting in one place** — UI, gestures, library view, split reading, 縦書き — under text-wide underlined headings.

## 💾 Backup — the whole app in one ZIP, by hand or unattended
The UI page opens with an Export/Import panel: pick a directory once, and one tap writes a timestamped `shiroikuma-shosekietsuran_<date>.zip` holding **everything** — the six settings categories (白い熊 UI, gestures & page turning, library view, writing 縦書き, app settings, reader settings) and the library itself: every book with its reading position, bookmarks and highlights, plus shelves & tags, annotation sidecars and custom fonts, each selectable by checkbox with sub-options indented under their parent. **Book covers are their own item and start unticked** — thousands of files and the bulk of an archive's bytes, and the one part derived from your book files rather than authored. Settings serialize generically and type-tagged, tables as JSON lines; import merges key by key and row by row (never clears), drops columns a newer schema no longer has, and offers an in-place restart. **Import is chosen from the archive, not from the catalogue**: picking a file reads what it really holds and offers exactly that, all ticked — a category that is not in there is not a row, so the list can never promise a restore the file cannot deliver.

That same export runs **headlessly on request**, and out of the box: the automation switch ships **on**, so a sister-app task — 白い熊's 自由作業盤 backs up every app in one batch — writes one ZIP wherever the batch says, reporting live counts (`書籍 1234/8942`) as it goes and replying with the path and byte size. That run is carried by a foreground service with a wakelock, so a multi-gigabyte library exports to the end with the screen off instead of being killed halfway. **An authorization token is optional** — 「Use authorization token?」 is off by default, and the token row only appears once you ask for one. A token sent by a caller that no longer needs to send one is quietly ignored rather than refused, so turning the switch off never turns into half a batch mysteriously failing.

**And the app can be restored onto a wiped phone, data and all.** A second door — a provider that identifies who is knocking — lets 白い熊 応用管理 back this app up *with its data* and put that data back after a factory reset, alongside the APK it already keeps. It is not the token that guards it: a broadcast cannot say who sent it, so the caller is checked by exact package name, by the user id the kernel reports, and against a **pinned signing certificate** — the one check that still holds on a clean phone, where the name of an app that is not installed yet is a name anyone could take. The backup itself travels through a file handle 応用管理 opens, never a path, so it lands inside the encrypted, checksummed archive instead of beside it. **Your books are not in it** — reading positions, bookmarks, shelves and annotations are what this app made and what cannot be made again; the book files are yours, they are gigabytes, and no category can reach them.

**And it cannot hang.** Every step of the export is bounded — a cover stream that never ends, a cursor that never stops, a write that stalls — and whatever will not finish is skipped, counted, and **named in the result**, because a backup that quietly lost half your covers is worse than one that failed. Watching from outside, a watchdog answers for a run that stops moving (naming the very entry it stopped on), lets go of it without waiting, and hands the slot to your next attempt — so "already running" can never be said by a run that died half an hour ago. The heartbeat is sent by that same watchdog, so it can never outlive the work it reports on.

**Nothing ever wears a backup's name until it is one.** Every export — by hand or unattended — is written to a `.part` file and moved onto its real name only once the archive is closed and whole, so a crash, a kill, a full disk or a cancelled run leaves your backup directory with **no file at all** rather than a truncated ZIP sorting to the top as "the latest backup" and waiting for the day you need it. Whatever a killed run does manage to leave is swept away by the next one. And a long export can be **stopped**: 中止 unwinds it between entries — never mid-write, never by killing anything — deletes the partial, and reports it as cancelled, leaving the directory exactly as it found it.

## 👆 Reading gestures — tap zones, swipe control, page-turn sound
Side-third taps turn real pages in every render mode (instant full-viewport jumps that snap the top line whole and cross chapter boundaries); a right-third vertical swipe steps font size and a left-third swipe steps screen brightness, with a live on-page readout; page turns click with a choice of five bundled sounds. Every gesture has its own toggle.

## 🗄️ Library, remade
A grid layout with live-adjustable thumbnail, title and author sizes; author and tag pull-down filters (with search-as-you-type and first/last-name sorting) — **or just tap the author printed under any book** to filter the shelf to that author, and tap it again to clear; a three-dot menu on every cover for parallel reading, file info, tags, sharing and deletion; and deletion that actually deletes — folder-synced files included — confirmed by a yellow-framed dialog and an instant library update. A thick black-and-yellow **fast scroller** floats over the grid's right edge: tap anywhere on its track to jump, or grab the fat yellow thumb and sweep a 9000-book library end to end in one swipe.

## 🔎 Rescan on demand — new books in seconds, not minutes
Add books to a synced folder and make the library notice **now**: a rescan button in the library's own top bar, pull-to-refresh on the grid, and **Scan this folder** on any folder card. Finding what is new reads no per-book sidecars at all — the part that turned a rescan into a minutes-long wait — so a 9000-book shelf answers in seconds, and the new books appear *while* the scan is still running instead of after it. Returning to the app rescans by itself, and the result says what it covered: 「9214 件を 1.3 秒でスキャン」, with time spent waiting reported separately from time spent scanning, so a stall can never hide inside a scan.

## 🏷️ Metadata that round-trips into your files
Embedded subjects (EPUB `dc:subject`, MOBI EXTH, FB2 genres, PDF Keywords) become library tags on import, and tag edits write back into the files — EPUB OPF rewriting and PDF info-dictionary editing included. Publication date, publisher, language, rating and ISBN are read live from the file; author, title, summary and date are editable for EPUBs and PDFs alike. Everything the info screen shows — title, author, series, publisher, path, summary, tag names — is long-press-selectable and copyable, HTML summaries included.

## 🔠 Per-book format settings & external fonts
Font, size, line height, margins and alignment are book-unique by default — set a mincho for the Japanese novels without touching anything else. Import any ttf/otf; the font picker groups families, detects variable weights, and previews every entry in its own glyphs.

---

## Built on Episteme
A fork of [Episteme](https://github.com/Aryan-Raj3112/episteme) (app id `shiroikuma.shosekietsuran`, so it coexists with the official build), currently tracking upstream **v1.0.54**. Episteme is a beautiful multi-format document and e-book reader for Android; this fork stands on that foundation and keeps its code namespace intact for clean rebases. The code remains under AGPL-3.0.

## Building
```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-shosekietsuran.git
cd shiroikuma-shosekietsuran
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=~/android-sdk
./gradlew :app:assembleOssRelease        # or the buildApk convenience task
```
Signing expects a `keystore.properties` mapping onto Episteme's `MYAPP_RELEASE_*` mechanism; without one, build `:app:assembleOssDebug`.
