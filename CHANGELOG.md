# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.10.0] - 2026-01-30
### Added
- Added support for custom fonts
- Location suggestions in event editor using recently used locations ([#393])

### Changed
- Unified the local and synchronized calendar pickers in event editor ([#629])
- Updated holiday data ([#1003])
- Updated translations

## [1.9.0] - 2025-12-16
### Changed
- Replaced "event types" concept with "calendars" ([#629])
- Renamed built-in "Regular event" calendar to "Local calendar"
- Weekday labels now use three-letter abbreviations instead of single letters ([#103])
- Converting all-day events to timed events now respects the default start time and duration ([#917])
- Updated translations

### Fixed
- Fixed crashes and freezing on some devices ([#889])

## [1.8.1] - 2025-11-09
### Changed
- Updated holiday data
- Updated translations

### Fixed
- Fixed startup crash in weekly view ([#550])
- Fixed incorrect weekly view start date in some cases ([#45])
- Fixed issue with Up/Arrow button closing the app ([#870])
- Fixed time drift when switching between views ([#590])

## [1.8.0] - 2025-10-29
### Changed
- Compatibility updates for Android 15 & 16
- Removed permission to access network state (it was added accidentally) ([#826])
- Updated holiday data
- Updated translations

## [1.7.0] - 2025-10-16
### Changed
- Events shown in adjacent months are no longer dimmed ([#808])
- Updated translations

### Fixed
- Fixed missing email notifications for attendees in some cases ([#135])
- Fixed missing attendees list when using some specific providers ([#818])

## [1.6.2] - 2025-10-09
### Changed
- Synchronized events with unspecified status are now treated as confirmed ([#761])
- Updated translations

### Fixed
- Fixed event duplication when editing instances of recurring events ([#138])
- Fixed old reminders not being removed when moving events ([#486])
- Fixed drag and drop copying events instead of moving them ([#706])
- Fixed crash when editing events with attendees ([#34])
- Fixed event edits being silently discarded on back press ([#49])
- Fixed synchronization issues when editing events in a recurring series ([#641])

## [1.6.1] - 2025-09-01
### Changed
- Declined events will no longer trigger notifications ([#732])
- Updated translations

### Fixed
- Fixed incorrect widget font size on foldable devices ([#337])
- Fixed missing or delayed reminders in some cases ([#217])

## [1.6.0] - 2025-08-21
### Added
- Holidays for Philippines ([#729])

### Changed
- Updated translations

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

[#34]: https://github.com/FossifyOrg/Calendar/issues/34
[#45]: https://github.com/FossifyOrg/Calendar/issues/45
[#49]: https://github.com/FossifyOrg/Calendar/issues/49
[#103]: https://github.com/FossifyOrg/Calendar/issues/103
[#135]: https://github.com/FossifyOrg/Calendar/issues/135
[#138]: https://github.com/FossifyOrg/Calendar/issues/138
[#148]: https://github.com/FossifyOrg/Calendar/issues/148
[#196]: https://github.com/FossifyOrg/Calendar/issues/196
[#217]: https://github.com/FossifyOrg/Calendar/issues/217
[#262]: https://github.com/FossifyOrg/Calendar/issues/262
[#337]: https://github.com/FossifyOrg/Calendar/issues/337
[#393]: https://github.com/FossifyOrg/Calendar/issues/393
[#394]: https://github.com/FossifyOrg/Calendar/issues/394
[#484]: https://github.com/FossifyOrg/Calendar/issues/484
[#486]: https://github.com/FossifyOrg/Calendar/issues/486
[#550]: https://github.com/FossifyOrg/Calendar/issues/550
[#551]: https://github.com/FossifyOrg/Calendar/issues/551
[#567]: https://github.com/FossifyOrg/Calendar/issues/567
[#574]: https://github.com/FossifyOrg/Calendar/issues/574
[#590]: https://github.com/FossifyOrg/Calendar/issues/590
[#603]: https://github.com/FossifyOrg/Calendar/issues/603
[#613]: https://github.com/FossifyOrg/Calendar/issues/613
[#629]: https://github.com/FossifyOrg/Calendar/issues/629
[#641]: https://github.com/FossifyOrg/Calendar/issues/641
[#682]: https://github.com/FossifyOrg/Calendar/issues/682
[#706]: https://github.com/FossifyOrg/Calendar/issues/706
[#729]: https://github.com/FossifyOrg/Calendar/issues/729
[#732]: https://github.com/FossifyOrg/Calendar/issues/732
[#761]: https://github.com/FossifyOrg/Calendar/issues/761
[#808]: https://github.com/FossifyOrg/Calendar/issues/808
[#818]: https://github.com/FossifyOrg/Calendar/issues/818
[#826]: https://github.com/FossifyOrg/Calendar/issues/826
[#870]: https://github.com/FossifyOrg/Calendar/issues/870
[#889]: https://github.com/FossifyOrg/Calendar/issues/889
[#917]: https://github.com/FossifyOrg/Calendar/issues/917
[#1003]: https://github.com/FossifyOrg/Calendar/issues/1003

[Unreleased]: https://github.com/FossifyOrg/Calendar/compare/1.10.0...HEAD
[1.10.0]: https://github.com/FossifyOrg/Calendar/compare/1.9.0...1.10.0
[1.9.0]: https://github.com/FossifyOrg/Calendar/compare/1.8.1...1.9.0
[1.8.1]: https://github.com/FossifyOrg/Calendar/compare/1.8.0...1.8.1
[1.8.0]: https://github.com/FossifyOrg/Calendar/compare/1.7.0...1.8.0
[1.7.0]: https://github.com/FossifyOrg/Calendar/compare/1.6.2...1.7.0
[1.6.2]: https://github.com/FossifyOrg/Calendar/compare/1.6.1...1.6.2
[1.6.1]: https://github.com/FossifyOrg/Calendar/compare/1.6.0...1.6.1
[1.6.0]: https://github.com/FossifyOrg/Calendar/compare/1.5.0...1.6.0
[1.5.0]: https://github.com/FossifyOrg/Calendar/compare/1.4.0...1.5.0
[1.4.0]: https://github.com/FossifyOrg/Calendar/compare/1.3.0...1.4.0
[1.3.0]: https://github.com/FossifyOrg/Calendar/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/FossifyOrg/Calendar/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/FossifyOrg/Calendar/compare/1.0.3...1.1.0
[1.0.3]: https://github.com/FossifyOrg/Calendar/compare/1.0.2...1.0.3
[1.0.2]: https://github.com/FossifyOrg/Calendar/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/FossifyOrg/Calendar/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/FossifyOrg/Calendar/releases/tag/1.0.0
