# Changelog

All notable changes to Chimahon are documented here.

The format follows a Keep a Changelog style and uses Semantic Versioning.

## [v1.9.0]

### Added
- OCR selection panel triggered with long-press to select and copy text
- OCR progress HUD
- Configurable OCR box opacity for inactive text blocks
- Support for more image formats in OCR: AVIF, HEIF, HEIC, JXL

### Changed
- Dictionary popup WebView warming refactored for lighter reader startup
- Improved dictionary popup rendering performance
- MangaBaka API updated with improved date-time formatting

### Fixed
- Clipped webtoon page rendering
- Novel category assignments lost during sync merge

## [v1.8.5]

### Added
- E-Ink swipe: instant page transitions

### Changed
- Replaced async OCR prefetch with structured scan and page-ready guard
- Updated hoshidicts (normalization processors, freq sorting)

### Fixed
- Reader crash and startup workaround
- Deduplicate manga page stats to prevent recount on back-navigation
- Preserve novel category assignments during sync merge
- Revert problematic NPE reader fixes
- General reader bug fixes (page viewer, Webtoon viewer)

## [v1.8.3]

### Added
- Per-manga stats sheet with session/today/all-time tracking
- Lower sensitivity swipe setting for E-ink

### Changed
- Separate paginated scrolling from e-ink mode in dictionary popup

### Fixed
- Prevent NPE on reader startup (Android 16 dispatchAttachedToWindow)
- Correct manga sentence export offset in Anki export
- Fix furigana spacing in Anki export
- Remove inline style overrides on table elements in Anki export

## [v1.8.2]

### Fixed
- Dictionary Reorder

## [v1.8.1]

### Added
- Mangabaka tracker integration
- Dictionary display name update
- Per-dictionary update notifications

### Changed
- Dictionary now uses directory names as stable keys

### Fixed
- Dictionary import UI
- Yomitan glossary HTML structure: always `<ol><li>`
## [v1.8.0]

### Added
- Dictionary auto-update 
- Dictionary type categorization.
- Paginated scrolling and improved E‑Ink border for the popup.
- Local OCR engine integration.
- Multi-file EPUB import support for novels.
- Arrow-key navigation and HUD/system-bar parity in the novel reader.
- Refactor and improve novel stats.

### Changed
- Novel categories aligned with manga categories.
- Faster word-audio database import.
- Pass OCR language locale to the manga text box for correct CJK rendering.

### Fixed
- Defer viewer visibility to avoid NPE crashes.
- Prevent the WebView from closing the keyboard on the Dictionary tab.
- Fix {selected-glossary} CSS for E‑Ink.

## [v1.7.7]

### Added
- popup theme setting
- novel and manga library parity — pull-to-refresh, debounce, multi-category

### Changed

- Novel library UI aligned with manga library.
- Lookup performance optimizations.

### Fixed
- popup not clearing old lookups.
- fix manga stats

## [v1.7.6]

### Fixed
- Word audio continuing to play after dictionary popup is dismissed.
- Word audio playing twice on lookup with autoplay enabled.


## [v1.7.5]

### Added
- AnkiDroid sync trigger on card creation.

### Changed
- Novel library UI aligned with manga library.
- Improved popup E-Ink mode.
- Lookup performance optimizations.

### Fixed
- Dictionary popup flicker and scroll delay.
- Edge-to-edge text at 0% padding in novel reader.
- Black background crop detection.
- Pitch accent ordering in popup and Anki exports.
- Glossary fallback when no dictionary selected.
- EPUB line-height stripping in CSS cleaning.
- Paged horizontal margins respecting line-height setting.


## [v1.7.3]

### Fixed
- Korean text handling for TTSU and dictionary deinflection.


## [v1.7.1]

### Added
- High-contrast E-ink mode for the dictionary popup.
- Active profile selection in the Dictionary tab.
- Advanced collapsible dictionary behavior in lookups.
- Ability to rename dictionaries.

### Changed
- Reorderable dictionary list via drag-and-drop.
- Refined dictionary popup positioning logic.

### Fixed
- Lookups for non-Japanese languages.
- Dictionary profile overrides in the Novel Reader.


## [v1.7.2]

### Added
- Statistics screen for novel/manga reading stats.
- Pitch accent deduplication option.

### Changed
- Make dictionary list draggable by a handle.
- System bar handling in Novel UI.
- Updated hoshidicts native library.

### Fixed
- Fix: horizontal paged reader behaviour.
- Revert OCR merge value regressions.
- Novel sync and language detection during import.
- Cascade fallback dictionaries in custom collapse mode.
- Korean deinflector text assembly bug.



## [v1.7.0]

### Added
- Cascading profiles that cascade from global down to per-reader context.
- Dictionary language support for Korean, English, and more.
- Auto kana input in dictionary search.
- Revamped novel library tab.
- Add button in the browse tab for importing local files.
- Popup position modes: Floating, Full-width, and Full-height.

### Changed
- Revamped popup positioning with priority-based floating (Below→Above→Right→Left).
- More sidecar formats (.mokuro) now supported — RAR, 7z, EPUB.
- OCR vowels displayed vertically for vertical text.
- Reader tap zones disabled by default.
- Renamed library tab to Manga.

### Fixed
- OCR text reconstruction for downloaded chapters — reading order is now correct and unrelated text bubbles are no longer merged together.
- Double-tap zoom now takes priority over single-tap actions.
- Improved popup margins for pitch accent and structured content.
- Anki `{SelectionText}` marker now preserves line breaks properly.
- Popup selection text not being cleared between lookups.

## [v1.6.2]

### Added
- Custom font support for the dictionary popup.
- Embedded scoped styles in Anki exports.
- Paragraph spacing option to the Novel Reader.
- System-themed sepia background setting for the Novel Reader.

### Changed
- Refined dictionary popup appearance better positioning and stable icons.
- Revamped Anki marker dropdown UX.
- Anki sentence exports now follow the natural OCR box reading order.
- Expanded the available range for line height adjustments in the Novel Reader.
- The "Shift Double Page" reader setting is now saved individually per manga.

### Fixed
- Fixed deinflection order.
- Fix pitch accent export in Anki.
- Resolved an issue causing incorrect page screenshots to be exported to Anki on double-page spreads.


## [v1.6.1]

### Added
- Added option to toggle visibility of dictionary navigation buttons in settings.

### Changed
- Enforced strict dictionary headword row layout (Audio -> Anki -> Open) across all render states.
- Optimized Anki status updates to prevent scroll resets and full WebView re-renders.
- Adjusted novel reader line height settings limit to a maximum of 1.8.
- Enhanced dictionary header selection visibility with a high-contrast accent bar (E-ink/B&W optimized).

### Fixed
- Fixed dictionary popup scroll position leaking between different word lookups.
- Resolved scroll state loss during recursive dictionary navigation.
- Fixed Anki marker "Single Glossary" sub-dropdown being unable to close.
- Improved Anki marker selection to automatically replace the empty `{}` placeholder.
- Fixed Anki duplicate check icon indicator failing to update to green after background check.
- Corrected Anki icon alignment and layout when the duplicate prevention policy is active.
- Fixed novel reader line height setting not applying unless advanced layout was enabled.


## [v1.6.0]

### Added
- Swipe-to-dismiss gesture for the dictionary popup.
- CSS highlighting for selected words in dictionary lookups.
- Support for additional audio formats in word audio databases.
- Modernized lookup UI and navigation stack.

### Changed
- Improved popup startup and lookup performance.
- Synchronized HUD animations with system navigation bars.
- Enhanced Anki markers flexibility and export sorting.
- Improved popup positioning and tap reliability.

### Fixed
- Fixed Novel Reader TOC navigation and layout shifting.
- Resolved inline image sizing issues in the Novel Reader.
- Fixed theme synchronization for various UI components (status/nav bars and bottom sheets).
- Improved OCR popup dismissal behavior on repeat taps.
- General stability improvements for the reader.


## [v1.5.0]

### Added
- Local Audio support.
- Novel progress and stats sync.
- Dict selection at at anki export marker.

### Fixed
- fixes to auto update.
- Update discord link to a working one.
- migration to the new wrapper for novels.


## [v1.4.2]

### Added
- Volume key navigation in Novel Reader.
- Customizable font size setting for the dictionary popup.
- Progress and reading statistics tracking for Novel Reader.
- Themed loading screen in Novel Reader matching selected background/text colors.
- More Anki markers.

### Changed
- Improve Anki markers.
- Improved auto-update reliability and download integrity checking.
- Optimized continuous mode scrolling and position restoration in Novel Reader.

### Fixed
- Inline image layout drops and viewport overflows in the Novel Reader.
- Navigation and boundary detection on image-only pages in Novel Reader.
- Dictionary popup dismissal logic when interacting with the Webtoon reader.

## [v1.4.1]

### Added
- Coordinate remapping for OCR blocks in Reader crop and double-page modes.

### Changed
- Improved flexibility and robustness of markers in Anki card templates.

### Fixed
- Horizontal pagination and margin calculation in Novel Reader.
- Novel Reader crash on release builds for specific books.
- Anki duplicate check reliability and model mismatch handling.
- Furigana stripping during recursive dictionary lookups.

## [v1.4.0]

### Added
- Novel Reader with lookup

### Changed
- Disable popup links

## [v1.3.6]

### Fixed
- revert broken bitmap capture

## [v1.3.5]

### Added
- Recursive dictionary lookups with enhanced search UX
- Pitch accent support in dictionary popup
- Anki and dictionary settings profiles
- Multiple dictionary import support
- Smart furigana distribution

### Changed
- OCR lookup and screenshot capture optimizations for Webtoon mode
- Image exports with WebP compression and resizing
- Anki dictionary mining UI with card navigation and sorting

### Fixed
- Paged reader panning while zoomed
- Popup dictionary order handling
- Webtoon reader gestures

## [v1.3.0]

### Added
- .mokuro OCR integration for reading .mokuro formatted manga files.
- Screenshot crop mode for Anki card creation with manual crop selection.
- Single glossary export with SINGLE_GLOSSARY marker support.
- Manga, chapter, and media Anki markers for card templates.
- Group terms toggle for dictionary popup definition grouping.

### Changed
- Single-tap OCR navigation: tap once on text to activate and show dictionary popup immediately.

### Improved
- Simplified OCR popup UI with better tap handling.

## [v1.2.2]

### Changed
- Enable updater and sync support for stable releases.
- Derive release app version name and version code from git tags in CI.

### Improved
- Refine chapter OCR status indicator badge behavior and layout.
- Improve OCR tap reliability across reader modes.

## [v1.2.1]

### Fixed
- Popup and Anki settings bugs.

## [v1.2.0]

### Added
- Webtoon OCR support.
- Arabic language support.

### Changed
- Improve OCR tap behavior.

### Fixed
- Popup and Anki UI issues.

## [v1.1.0]

### Added
- Per-line OCR rendering flow.
- OCR preprocessing and binary-search based text hit handling.
- URL interception for Anki bridge in OCR popup.

### Improved
- OWOCR merge quality.
- OCR rendering and preprocessing pipeline.

## [v1.0.0]

### Added
- Anki integration with auto-detect field mapping.
- OWOCR OCR engine processing.

### Changed
- Migration from Mihon base to Komikku base.
- App rebrand to Chimahon.

### Fixed
- Initial build and integration fixes around OCR and Anki.

## [v0.1.0]

### Added
- Initial Chimahon release baseline with integrated dictionary direction.
