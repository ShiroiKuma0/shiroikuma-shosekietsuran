<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="120" alt="白い熊 書籍閲覧 icon" />

# 白い熊 書籍閲覧

**A black-and-yellow e-book reader that reads Japanese the way Japan prints it.**

A fork of [Episteme](https://github.com/Aryan-Raj3112/episteme) with **major additions**: tategaki 縦書き vertical-text rendering, parallel reading of up to three books in nine screen layouts, a cross-book annotation library, page-turn animations with a real paper curl, whole-line page views, a fully themeable black×yellow UI, tap/swipe reading gestures with page-turn sound, a remade library, and metadata that writes back into the book files.

Installs **side-by-side** with Episteme (app id `shiroikuma.shosekietsuran`).

**📥 Latest release: [`1.0.51+64`](https://github.com/ShiroiKuma0/shiroikuma-shosekietsuran/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-shosekietsuran/releases)

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
Pure-black surfaces, pure-yellow text, accents and borders — and every one of those colors adjustable from a dedicated settings page with RGBA pickers, recent-color memory and live preview. Typography scale and weight, corner roundness, and border thickness are sliders; the yellow frame follows you into every dialog, sheet, menu and banner in the app.

## 👆 Reading gestures — tap zones, swipe control, page-turn sound
Side-third taps turn real pages in every render mode (instant full-viewport jumps that snap the top line whole and cross chapter boundaries); a right-third vertical swipe steps font size and a left-third swipe steps screen brightness, with a live on-page readout; page turns click with a choice of five bundled sounds. Every gesture has its own toggle.

## 🗄️ Library, remade
A grid layout with live-adjustable thumbnail, title and author sizes; author and tag pull-down filters (with search-as-you-type and first/last-name sorting); a three-dot menu on every cover for parallel reading, file info, tags, sharing and deletion; and deletion that actually deletes — folder-synced files included — confirmed by a yellow-framed dialog and an instant library update.

## 🏷️ Metadata that round-trips into your files
Embedded subjects (EPUB `dc:subject`, MOBI EXTH, FB2 genres, PDF Keywords) become library tags on import, and tag edits write back into the files — EPUB OPF rewriting and PDF info-dictionary editing included. Publication date, publisher, language, rating and ISBN are read live from the file; author, title, summary and date are editable for EPUBs and PDFs alike.

## 🔠 Per-book format settings & external fonts
Font, size, line height, margins and alignment are book-unique by default — set a mincho for the Japanese novels without touching anything else. Import any ttf/otf; the font picker groups families, detects variable weights, and previews every entry in its own glyphs.

---

## Built on Episteme
A fork of [Episteme](https://github.com/Aryan-Raj3112/episteme) (app id `shiroikuma.shosekietsuran`, so it coexists with the official build). Episteme is a beautiful multi-format document and e-book reader for Android; this fork stands on that foundation and keeps its code namespace intact for clean rebases. The code remains under AGPL-3.0.

## Building
```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-shosekietsuran.git
cd shiroikuma-shosekietsuran
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=~/android-sdk
./gradlew :app:assembleOssRelease        # or the buildApk convenience task
```
Signing expects a `keystore.properties` mapping onto Episteme's `MYAPP_RELEASE_*` mechanism; without one, build `:app:assembleOssDebug`.
