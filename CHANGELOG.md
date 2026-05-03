# Changelog

All notable changes to Chimahon are documented here.

The format follows a Keep a Changelog style and uses Semantic Versioning.


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
