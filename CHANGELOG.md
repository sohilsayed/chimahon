# Changelog

All notable changes to Chimahon are documented here.

The format follows a Keep a Changelog style and uses Semantic Versioning.

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
