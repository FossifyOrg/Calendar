# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.5.0] - 2025-07-22
### Added
- Holidays for Guatemala ([#682])

### Changed
- Updated translations

### Fixed
- Audio stream preference now works correctly ([#394])
- Fixed "today" highlight alignment in month view ([#603])

## [1.4.0] - 2025-07-05
### Added
- Holidays for Vietnam ([#613])
- Holidays for Hong Kong ([#574])

### Changed
- Updated translations

## [1.3.0] - 2025-05-13
### Added
- Support for setting event visibility ([#148])
- Option to hide date header in event list widget ([#484])
- Holidays for Bangladesh

### Changed
- Updated some in-app icons for consistency ([#567])
- Updated translations
- Updated holiday data

### Fixed
- Addressed a glitch when long pressing in quick filter
- Fixed age calculation for birthdays from private contacts ([#196])
- Fixed incorrect time in some events imported via ICS files ([#262])
- Fixed "Go to today" button in weekly view ([#551])

## [1.2.0] - 2025-01-26
### Added
- Added ability to export event colors in ICS files (#188)
- Added ability to quickly filter calendars on long press (#309)
- Added state-specific and optional holidays (#379, #413)

### Changed
- Other minor bug fixes and improvements
- Added more translations

### Fixed
- Fixed issue with "Mark completed" notification button (#156)
- Fixed cut-off text in month view on some devices (#265)
- Fixed broken weekly repetition in some timezones (#408)
- Fixed "Mark completed" button color in black & white theme (#357)
- Fixed invisible attendee suggestions (#41)

## [1.1.0] - 2024-11-15
### Added
- Added support for event status

### Changed
- Replaced checkboxes with switches
- Other minor bug fixes and improvements
- Added more translations

### Removed
- Removed support for Android 7 and older versions

### Fixed
- Resolved issue with multi-day all-day events not displaying on the top bar
- Fixed task opening functionality from widgets
- Fixed resizing issue in date widget
- Fixed opacity for incomplete tasks in widgets
- Fixed spanish translation for saturday.

## [1.0.3] - 2024-03-12
### Changed
- Highlight weekends in print mode.
- Updated holidays for some countries.
- Added some translations.

### Fixed
- Fixed month view issue on Google Pixel 8 Pro.
- Fixed event color dots on monthly and daily view.
- Fixed incorrect timezone when import ICS files.

## [1.0.2] - 2024-01-02
### Fixed
- Fixed import compatibility with Simple Calendar.
- Fixed foss flavor configuration.

## [1.0.1] - 2024-01-02
### Fixed
- Fixed import compatibility with Simple Calendar.

## [1.0.0] - 2024-01-01
### Added
- Initial release

[#148]: https://github.com/FossifyOrg/Calendar/issues/148
[#196]: https://github.com/FossifyOrg/Calendar/issues/196
[#262]: https://github.com/FossifyOrg/Calendar/issues/262
[#394]: https://github.com/FossifyOrg/Calendar/issues/394
[#484]: https://github.com/FossifyOrg/Calendar/issues/484
[#551]: https://github.com/FossifyOrg/Calendar/issues/551
[#567]: https://github.com/FossifyOrg/Calendar/issues/567
[#574]: https://github.com/FossifyOrg/Calendar/issues/574
[#603]: https://github.com/FossifyOrg/Calendar/issues/603
[#613]: https://github.com/FossifyOrg/Calendar/issues/613
[#682]: https://github.com/FossifyOrg/Calendar/issues/682

[Unreleased]: https://github.com/FossifyOrg/Calendar/compare/1.5.0...HEAD
[1.5.0]: https://github.com/FossifyOrg/Calendar/compare/1.4.0...1.5.0
[1.4.0]: https://github.com/FossifyOrg/Calendar/compare/1.3.0...1.4.0
[1.3.0]: https://github.com/FossifyOrg/Calendar/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/FossifyOrg/Calendar/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/FossifyOrg/Calendar/compare/1.0.3...1.1.0
[1.0.3]: https://github.com/FossifyOrg/Calendar/compare/1.0.2...1.0.3
[1.0.2]: https://github.com/FossifyOrg/Calendar/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/FossifyOrg/Calendar/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/FossifyOrg/Calendar/releases/tag/1.0.0
