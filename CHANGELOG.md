# Changelog — 白い熊 書籍閲覧

Everything built on top of stock Episteme, per release.

## 1.0.52+6

Base: Episteme Android v1.0.52 (oss).

### Export / Import of every setting

- The 白い熊 UI page opens with a new first section, **Export / Import**. Its entry row shows the date of the latest export, queried from the persisted export directory whenever the page opens.
- The panel (a full-height sheet holding a 2 dp yellow-bordered box) carries: a **settable export directory** — a tappable bordered box opening the system folder picker, persisted with a durable permission so it survives reboots, red "Not set" until chosen; the last-export line; and a **category checklist** with Select all.
- Six categories cover everything settable in the app: 白い熊 UI (colors, fonts, shapes), 白い熊 gestures & page turning, 白い熊 library view, 白い熊 writing 縦書き, app settings (theme, font, behavior), reader settings (EPUB, PDF, TTS, annotation defaults).
- The export is a ZIP of plain per-category JSON files plus a manifest (format, version, categories). Whole preference stores serialize generically and type-tagged, so any keyset round-trips; device-local keys (installation id, sync timestamps, open tabs, per-device caches) never travel.
- One-tap export writes `shiroikuma-shosekietsuran-<version>-export_<timestamp>.zip` straight into the set directory; without a directory it falls back to a save-as dialog.
- Import picks a ZIP, merges key by key (never clears), skips categories absent from the file, reports a per-category count, and offers **Restart now / Later** so every imported setting takes effect.
- The result dialog is yellow-bordered; acknowledging a successful export or import closes the dialog, the panel, and the UI page in one go. Failures leave the panel open for a retry.

### UI page — kxkb look, all settings in one place

- Section headings restyled to the kxkb convention: 20 sp bold yellow, underlined **only as wide as the text** (2 dp); sub-headings 16 sp medium with a 1.5 dp text-wide underline; sections separated by thin 1 dp hairlines (none above the first).
- The page now gathers the previously scattered fork settables: a **Library view** section (grid/list toggle, thumbnail height, title and author sizes), **Split reading** (companion pane font scale), and **Writing 縦書き** (ruby line spacing toggle).

### Library grid fast scroller

- A black-and-yellow fast scroller floats over the grid's right edge — over the rightmost covers, no layout shift: a full-height 40 dp black track column with a thick yellow, black-outlined 88 dp thumb (grip bars included). It fades in while the grid scrolls and out 2 s after it stops.
- The whole track is interactive, not just the thumb: tap anywhere on the column to jump straight to that point of the library, or grab any spot and drag — the thumb snaps under the finger and the track maps linearly onto all items, so a 9000-book library is one screen-height swipe end to end.
- The bar ends 96 dp above the bottom edge so it never slides under the add-file button; its bottom still means end-of-library.

## 1.0.52+1

Base: Episteme Android v1.0.52 (oss) — **new upstream base**; the entire fork layer rebased onto it.

### Upstream 1.0.52 brings

- An optional, redesigned library view; sorting by newest/oldest; shelf-sync and sorting fixes.
- Markdown files split into chapters by headings; more reliable EPUB table-of-contents links; improved image sizing, wide-content handling, tables and complex layouts; a touchpad-scrolling fix.
- Fixes: PDF reading-position restore, Arabic text selection in paginated mode, files opened from outside the app, PDF-reader keyboard spacing, TTS playback/stop/navigation reliability, and general stability.
- Build system moved to AGP 9.0, with reproducible-build work for F-Droid.

### Fork-side changes in this release

- Ported the fork's APK-naming off the `applicationVariants` API that AGP 9.0 removed — the `buildApk` task now solely owns the `shiroikuma-shosekietsuran_<version>_arm64-v8a.apk` naming.
- Kept the fork's debounced library search on top of upstream's new local-field-state fix (upstream still pushes the query every keystroke; ours fires 220 ms after typing pauses).
- Kept the fork's reader image sizing (slider-driven width with the natural-size ornament exemption), which supersedes upstream's milder width-cap change.
- All customizations verified intact after the rebase: branding, theming, tategaki, parallel reading, gestures, annotation library, metadata round-trip, library remake, versioning and signing.

## 1.0.51+64

Base: Episteme Android v1.0.51 (oss).

### Fixes

- Library search on large libraries no longer mangles typed input. Every keystroke used to round-trip through the view model — whose cache key includes the query, forcing a full re-projection of the library per letter — and the stale state echo was written back into the text field, resetting text and cursor. That destroyed the IME composition, so autocorrect committed after every character; with thousands of books this fired on every letter. The search field is now the single source of truth while search is open (nothing is ever written back into it), and the query reaches the view model debounced — 220 ms after typing pauses, distinct values only — so the heavy filtering runs once per pause instead of once per letter. The clear button clears both the field and the query immediately.
- The per-book cover menu's three yellow dots carry a black outline (the glyph drawn underneath in black, offset 1.2 dp in eight directions), so they no longer blend into yellow, white, or busy cover art.

## 1.0.51+62

Base: Episteme Android v1.0.51 (oss).

### Fixes

- Whole-line pages: the bottom-line mask is now re-evaluated whenever the chapter's layout settles, instead of once shortly after page load. Previously the one-shot computation raced the style injection, custom fonts, chunked content and images that reflow a fresh chapter, so the **first view of a new chapter** could keep an unmasked partial bottom line. A ResizeObserver on the document re-runs the mask when the layout stops changing, every style reflow (font size, line height, margins) schedules a recompute — which also fixes stale masks after mid-page format changes, which fire no scroll event — and `document.fonts.ready` triggers one more pass for font swaps that change line metrics without resizing the document.

## 1.0.51+61

Base: Episteme Android v1.0.51 (oss).

### Annotation library

- New "Annotations" screen — bookmark-collection icon in the Home and Library top bars: every text highlight (with its note) from every book in one place, aggregated live from the existing stores (EPUB highlights from the database, PDF highlights from their sidecar files). New annotations appear automatically; there is no separate index to drift.
- Collapsible groups with three switchable modes: by book (with cover thumbnail), by tag, by color — each header folds and shows its count.
- Live search across highlighted text, notes, tags, book titles and locations; tapping a tag chip filters by it.
- Free-form tags per annotation: chip editor with removable chips, a new-tag field and suggestions from all existing tags; stored in a fork-owned JSON file (rebase-clean, and the natural payload for a future annotation import/export).
- Entries render study-style: color bar, the text in its highlight color and style (background, underline, strikethrough), the note in italics, book · location caption.
- Tap an annotation → the book opens at that spot (EPUB by CFI, PDF at the page).
- `CollectionsBookmark` glyph added to the local icon pack.

### Parallel reading layouts

- Layout chooser in the parallel tab bar for **any** 2–3-book set (previously only two-book sets had a split control): one book at a time; two side by side / stacked; three side by side / stacked; one left + two stacked right; two stacked left + one right; one top + two side-by-side bottom; two side-by-side top + one bottom.
- The split container hosts up to three panes (primary + two companion panes).
- **Draggable dividers** with centered grab handles between all panes; both split ratios persist across sessions.
- The chosen layout persists and degrades gracefully when the set shrinks (three-pane layouts fall back to the matching two-pane one, then to single); the legacy two-book split preference is migrated.
- Library cover menu: new "Start new parallel reading" (drops the old set and starts fresh with that book) alongside "Add to parallel reading".

### Page-turn animations

- Five styles, set in 白い熊 UI → Page turning: **None** (instant), **Slide** (smooth scroll), **Fade**, **Flip** (the page folds over the spine), **Curl** — a deluxe corner peel: a crease sweeps diagonally from the corner, the part past it folds over showing the paper's mirrored, washed-out backside, with a drop shadow on the revealed page and shading along the crease. Curl is the default.
- The backside color derives from the page itself — near-white paper on light themes, lifted dark grey on dark themes.
- Animation speed slider: 150–1500 ms in 50 ms steps.
- Animated in-settings preview: a black page with yellow border and yellow text lines turning on a loop with the selected style and speed.
- Works in yokogaki and tategaki; split-layout companion panes follow with smooth-scroll turns whenever an animation style is active.

### Whole-line pages

- A text line (yokogaki) or column (tategaki) that would be cut at the page's end edge is hidden — painted over in the page's own background color — and becomes the first line of the next page; the page turn advances exactly to it, so turns always flip whole lines.
- The mask drops during free scrolling and re-evaluates when the view settles; oversized blocks (images, tables) are never masked; the final view of a chapter stays intact.
- Partial page turns at chapter boundaries always smooth-scroll the remainder, giving the eye a cue where the text continues (no more hunting after a short flip).
- Tight line spacing fixed: with line height < 1.0, the previous line's descenders no longer peek above the first line (the top-line snap compensates the ink seepage), and the bottom mask anchors on the line box so the last visible line keeps its descenders.

### Images

- Small ornament images (both dimensions ≤ 96 px — separator stars and similar) are exempt from the reader's forced full-width sizing: the book's own CSS or the natural size applies. Fixes tiny separators being blown up into page-wide blurs that read as walls of whitespace.
- All-black ornaments are repainted accent yellow on dark backgrounds — pixel-exact with the alpha channel preserved, so anti-aliased edges stay smooth — and swap back to the original on light themes. Colored ornaments and photographs are never touched. Pixel access goes through a restricted bridge returning data: URLs (chapter documents load from file://, which taints a canvas), locked to the app's own directories and small files.
- New "Original image sizes (no resizing)" switch in the reader's format sheet, next to the Image size slider; persists through the existing per-book/global format machinery.

### UI & settings

- 白い熊 UI settings restructured: per-section previews (Colors, Typography, Shape & borders) replace the single preview card at the top, and the Page turning section gained the animation chooser, speed slider and live page preview.
- Long-press the reader's top-right ⋮ opens the 白い熊 UI settings directly, mirroring the home screen's cog long-press.

## 1.0.51+51

First published release. Base: Episteme Android v1.0.51 (oss).

### Major features

**Tategaki 縦書き — Japanese vertical text (WebView reader)**
- Vertical-writing EPUBs (`writing-mode: vertical-rl`, rtl spine) rendered blank pages in stock; they now render as true tategaki: columns top→down, flowing right→left, in one horizontally-scrolling stream that starts at the right edge.
- Left tap pages forward / right tap back (Japanese convention); chapter boundaries flip on logical end/start; the whole chapter loads up front; chunk virtualization works on both axes with scroll compensation.
- Print typography on an exact character grid: physical block margins zeroed (they staggered column tops), CSS text-indent zeroed (books indent with a literal full-width space — CSS on top doubled the drop), strict kinsoku line breaking, no CJK justification (it stretches between characters and wrecks the shared row grid).
- Column pitch equals the Line Height slider value verbatim — 1.0 means columns touch.
- 振り仮名の余白 toggle: ON keeps native ruby layout so only furigana-bearing lines get their single-sided annotation gap; OFF rebuilds rubies into base spans with absolutely-positioned reading satellites — strictly uniform pitch, readings overpaint when too tight. Toggling converts the live page both ways.
- Viewport scale locked while tategaki is active (the wide stream otherwise triggers the WebView's overview zoom-out and clips the columns); the pixel-exact viewport height self-heals on resize, rotation and font changes (Android WebView resolves root %/vh heights to 0).
- Unified reading-mode menu with its own toolbar icon: Scroll (horizontal text), Vertical text 縦書き (columns right → left), Scroll — native renderer (beta), Pages (left → right), Pages (right → left). One choice sets page layout and writing direction; persisted per book; vertical books default to tategaki automatically.

**Parallel reading — 2–3 books as a set**
- Ordered parallel set (e.g. the same book in two languages); assemble via library multi-select, the per-cover menu, or the reader tab bar's "Add book for parallel reading" (drops to the library; the next tapped book joins and opens).
- Flip between the set's books with a one-finger horizontal swipe (two-finger also works), wrap-around, each book at its own saved position, with an on-page overlay naming the target.
- Reader tab bar under the top chrome: a tab per book across the full row, tap to switch, long-press (short vibration) and drag to reorder, per-tab three-dot menu (file info, remove from set).
- Same-screen split reading: with a two-book set the tab bar's split tab offers Vertical (top/bottom), Horizontal (side by side) or None, persisted, with a theme-colored divider. The companion pane renders the other book (EPUB/MOBI/FB2) with its own saved position and full gesture parity — center drag scrolls, side taps turn pages with sound, center tap toggles the shared toolbars, right-third swipe adjusts the pane's own font scale, left-third adjusts brightness, one-finger horizontal swipe flips the two books. Format values (margins, line height, gaps, font, alignment) follow the primary reader live.
- Only the book content splits — top bar, tab bar and bottom toolbar span the full display; a book outside the set opens free of it, and starting parallel from a free book begins a new set.

**白い熊 UI — fully themeable black×yellow interface**
- Fork settings page: RGBA color pickers with recent-color memory and live preview for background, surface, text, accent and border; typography scale and weight sliders; corner-roundness slider; border thickness down to 0; master toggle; glyph-rendered font list reusing the app font preference and custom-font import.
- Pure-black surfaces (tonal-elevation tint disabled so nothing casts olive over black); the theme border frames dialogs, sheets, menus and banners app-wide.
- Entry points: a card at the top of Settings and a long-press on the Settings cog.

**Reading gestures**
- A render-mode-agnostic gesture layer: side-third quick taps turn pages (long-press still selects text); right-third vertical swipe steps font size; left-third steps screen brightness; live translucent overlay readout ("Font N%" / "Brightness N%"); per-gesture toggles and a master switch; gestures pause while the toolbars are shown so bar icons stay tappable.
- Real page turns in the WebView renderer: instant full-viewport jumps, "Page turn amount" slider (100% = no overlap), chapter-boundary crossing, and top-line snapping so no line starts mid-glyph.
- Page-turn sound: five bundled effects (Off/1–5) with tap-to-preview, played only on an actual turn, on the media channel.

### Library & metadata

- Grid layout with adaptive columns and live sliders for thumbnail height, title size and author size; list/grid toggle in a top-bar menu.
- Author and Tag pull-down filters above the library, with search-as-you-type in the author list and First/Last-name sort chips; crossed-filter icon shows all.
- Per-cover three-yellow-dot menu (list and grid): Add to parallel reading, File info, Tags, Share file, Save a copy, Delete.
- Long-press opens multi-select (stock behavior), with a parallel-reading action for 2–3 selected books.
- Deletion that deletes: yellow-framed confirm dialog (Cancel preselected), SAF deletion of folder-synced originals and sync sidecars (they used to resurrect on the next scan), instant library update (DB row removed first), and a banner flashing the deleted file's full path.
- Embedded metadata: EPUB `dc:subject`, MOBI EXTH subjects and FB2 genres become library tags on import and via a one-shot startup backfill; PDFs contribute their Keywords/Subject the same way.
- Metadata editing writes back into the files: EPUB OPF rewriting (title, author, summary, publication date, tags as `dc:subject`) and PDF info-dictionary editing (Title, Author, Subject, Keywords) via PDFBox — with a one-time original backup; encrypted PDFs refused.
- Extra metadata read live from the file when the details dialog opens: Published, Publisher, Language, Rating, ISBN (EPUB/MOBI/FB2).
- Author autocomplete and an in-dialog tag editor in edit mode; PDF pill on covers in theme colors.

### Reader & format settings

- Format settings are book-unique by default (font, size, line height, margins, alignment); a book starts from the global defaults and keeps its own values from the first change; the format sheet's Global/Local dropdown still opts out.
- External fonts: import any ttf/otf; families grouped with weight/italic variants, variable-weight detection, every picker entry previewed in its own glyphs; the chosen font applies in the WebView, paginated and native renderers.
- Format sheet outlined in the theme border; Line Height slider extended down to 0.3 with 0.05 steps and numeric labels; the WebView engine honors sub-1.0 line heights (stock silently reset them).
- Toolbars overlay the text — a center tap no longer reflows the page in any render mode.
- Quieter TTS overlay: the in-reader controller appears only while TTS is actually playing or loading.

### Fixes & packaging

- Startup crash fixed: R8's optimizer miscompiled a Long comparison in the reader's giant composable (ART `VerifyError` on launch); the fork disables the R8 optimizer (`whitebear-rules.pro`, shrinking and obfuscation stay on).
- The reader WebView is inspectable over adb (`chrome://inspect`) — the tategaki work was verified on-device through it.
- Branding: 白い熊 書籍閲覧 launcher label and traced yellow-on-black icon; no user-visible "Episteme" strings; sync folder `ShiroikumaSyncData`; About/feedback links point at this fork.
- Packaging: app id `shiroikuma.shosekietsuran` (side-by-side with upstream), single-ABI arm64-v8a APK named `shiroikuma-shosekietsuran_<version>_arm64-v8a.apk`, fork versioning `<upstream>+<build>` / `versionCode = upstream × 10000 + build`, `buildApk` convenience task.
