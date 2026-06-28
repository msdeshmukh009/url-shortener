# Changelog

All notable changes to this project will be documented in this file.

## [2.3.0] - Jun 10, 2026

### Added
- Logging and auth middlewares 
- Blacklisting support 

## [2.2.0] - Jun 7, 2026

### Added
- Password protection for urls
- Added Health check endpoints
- List urls for user

## [2.1.0] - Jun 6, 2026

### Added
- Support for Custom shortcode
- New Tierlist for user 
- Bulk shorten support for Enterprise tier user

## [2.0.0] - Jun 3, 2026

### Added
- API key authentication via X-API-Key header
- User registration endpoint (POST /api/users)
- Per-user URL ownership and isolation

### Changed
- **BREAKING**: All /api/* endpoints now require X-API-Key header
- Response now includes user context (userId in URL objects)

### Removed
- **BREAKING**: Anonymous URL shortening no longer supported

### Migration
- See [MIGRATION.md](./MIGRATION.md) for upgrade instructions

## [1.2.0] - Jun 2, 2026

### Added
- Allowed duplicate original url

## [1.1.0] -  Jun 1, 2026

### Added
- Visit count tracking
- Input Validation

## [1.0.0] - May 24, 2026

### Added
- Initial release
- POST /api/shorten — create short URL
- GET /api/redirect — JSON-based redirect