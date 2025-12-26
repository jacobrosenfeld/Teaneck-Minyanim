# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- CHANGELOG.md file to track version history
- Version management guidelines in copilot instructions

## [1.1.0-SNAPSHOT] - In Development

### Added
- Initial Spring Boot application for displaying minyanim (Jewish prayer services) in Teaneck, NJ
- Automatic time calculation based on Jewish calendar using Kosherjava library
- Organization and location management system
- Admin interface for managing minyanim schedules
- Support for multiple time calculation modes (fixed, dynamic, rounded)
- User authentication and role-based access control (SUPER_ADMIN, ADMIN, USER)
- Integration with MariaDB database
- Thymeleaf templates with Bootstrap 4.6 styling
- Support for multiple nusach types (Ashkenaz, Sefard, Edot Hamizrach, Arizal)
- Support for special days (Rosh Chodesh, Yom Tov, Chanuka)

### Technical Details
- Spring Boot 2.6.2 framework
- Java 17 runtime
- Hardcoded Teaneck coordinates (40.906871, -74.020924)
- America/New_York timezone
- 11 time columns per minyan (Sunday-Shabbat + special days)

---

## Version History Notes

### Version Numbering Convention
- **Patch versions (x.x.1)**: Bug fixes, UI tweaks, documentation updates
- **Minor versions (x.1.0)**: New features, non-breaking functionality additions
- **Major versions (1.x.0)**: Breaking changes, major architecture updates

### Changelog Entry Format
- **Patch**: Short 2-line summary of changes
- **Minor**: Detailed feature descriptions with subsections (Added/Changed/Fixed)
- **Major**: Comprehensive documentation including breaking changes and migration paths

[Unreleased]: https://github.com/jacobrosenfeld/Teaneck-Minyanim/compare/v1.1.0...HEAD
[1.1.0-SNAPSHOT]: https://github.com/jacobrosenfeld/Teaneck-Minyanim/releases/tag/v1.1.0
