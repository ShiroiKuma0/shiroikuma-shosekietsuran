# Changelog — 白い熊 書籍閲覧

Everything built on top of stock Episteme, per release.

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
