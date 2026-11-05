# Changelog

All notable changes to Spark REST are documented in this file.

## [1.0.5] - 2026-11-05

### Fixed
- `ClassNotFoundException: me.lucko.spark.common.api.AbstractStatistic` when requesting metrics (mspt). HTTP handlers now run on a dedicated executor whose threads use the mod context classloader.
- Requests no longer hang when an error occurs: the handler now always returns a 500 response.
- Missing spark is checked before using it, avoiding a `NullPointerException` on startup.

## [1.0.4] - 2026-06-06

### Fixed
- The HTTP server is started on its own daemon thread.

## [1.0.3] - 2026-06-05

### Added
- Startup log with the spark implementation and TPS/CPU self-checks.

## [1.0.2] - 2026-06-04

### Changed
- Updated Forge to 47.4.10.

### Fixed
- `mspt_1m` is only included when the mspt statistic is available.

## [1.0.1] - 2025-12-08

### Changed
- `mspt_1m` reports the mean value.

## [1.0.0] - 2025-12-07

### Added
- Initial release: REST endpoint exposing spark TPS, MSPT and CPU metrics.
