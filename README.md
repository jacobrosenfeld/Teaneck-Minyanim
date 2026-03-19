# Teaneck Minyanim

This is the code behind [Teaneck Minyanim](https://teaneckminyanim.com), a comprehensive web application (and companion mobile app) that lists prayer services (minyanim) and Jewish religious times (zmanim) for Teaneck, NJ.

## Features

- **Automatic Time Calculations**: Service times automatically adjust based on the Jewish calendar using the Kosherjava library
- **Multiple Organizations**: Support for multiple synagogues and organizations
- **Materialized Calendar Architecture**: Pre-computed 11-week rolling schedule window (3 past + 8 future weeks) with day-level precedence — imported events override rule-based schedules for an org on a given date
- **Rule-Based Scheduling**: Flexible scheduling system supporting fixed times and dynamic Zman-based calculations (e.g. Netz+5min, Plag-10min)
- **Calendar Import System**: Import events from external calendars with automatic intelligent classification (Minyan / Non-Minyan / Mincha-Maariv)
- **Public REST API**: Versioned JSON API at `/api/v1/` for mobile app and third-party consumers, with interactive docs at `/api/docs`
- **Mobile App**: React Native / Expo companion app (iOS & Android) consuming the REST API
- **Announcement & Notification System**: Banner and popup notifications with markdown support and optional display-count limiting
- **Organization Geocoding**: Addresses are automatically geocoded via Mapbox for map display in the mobile app
- **Mincha/Maariv Combined Services**: Special handling for combined afternoon/evening prayers with automatic Plag/Shkiya time annotations
- **Admin Interface**: Comprehensive admin panel for managing organizations, locations, minyan rules, and calendar events — with role-based access for super admins and org admins
- **Responsive Design**: Mobile-friendly public interface for users on any device
- **Privacy Policy**: In-app privacy policy page at `/privacy` required for App Store listing

## Technology Stack

### Backend
- **Runtime**: Java 21 LTS
- **Framework**: Spring Boot 3.5.9
- **Persistence**: Hibernate JPA, Spring Data JPA
- **Database**: MariaDB 10.3+
- **Security**: Spring Security with role-based access control (SUPER_ADMIN / ADMIN / USER)
- **Hebrew Calendar**: Kosherjava library for all Jewish time calculations (hardcoded for Teaneck, NJ)
- **API Docs**: springdoc-openapi (OpenAPI 3), Scalar UI

### Frontend
- **Templating**: Thymeleaf with Thymeleaf Layout Dialect
- **CSS**: Bootstrap 4.6 + custom design system (`design-system.css`) with CSS custom properties
- **JS**: jQuery 3.6, Expo SDK-compatible via API

### Mobile App (`mobile/`)
- **Framework**: React Native 0.83 / Expo SDK 55 / Expo Router
- **Language**: TypeScript
- **Data fetching**: TanStack Query with async-storage persistence
- **Maps**: react-native-maps, Mapbox geocoding
- **Push notifications**: Expo Notifications

## Getting Started

### Prerequisites
- Java 21 or higher
- Maven 3.6+
- MariaDB 10.3+

### Database Setup
```sql
CREATE DATABASE minyanim CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Configuration

Copy `src/main/resources/application.properties` and adjust as needed:

```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/minyanim
spring.datasource.username=root
spring.datasource.password=yourpassword

# Super admin created automatically on first startup if none exists
superadmin.username=admin
superadmin.password=Admin123!

# API rate limiting (requests per minute per IP, default 60)
api.ratelimit.requests-per-minute=60
```

> **Security note**: Change the default database credentials and super admin password before deploying to production.

### Build and Run
```bash
# Build
mvn clean install

# Run
mvn spring-boot:run

# Access at http://localhost:8080
```

### Run Tests
```bash
mvn test
```

### Mobile App Development
```bash
cd mobile
npm install
npm start          # Expo dev server
npm run ios        # iOS simulator
npm run android    # Android emulator
```

> The mobile app reads `EXPO_PUBLIC_API_URL` from `mobile/.env.local`. Copy `mobile/.env.example` to `mobile/.env.local` and set the backend URL.

## Architecture Overview

### Data Flow (Materialized Calendar)
1. `CalendarMaterializationService` generates the `calendar_events` table from rule-based `Minyan` entities and imported `OrganizationCalendarEntry` records
2. Materialized events are the single source of truth, pre-computed for an 11-week rolling window
3. **Day-level precedence**: If any `IMPORTED` events exist for an org on a given date, all `RULES` events for that org+date are suppressed
4. Frontend and API query via `EffectiveScheduleService`, which applies precedence automatically
5. A `@Scheduled` job rebuilds `RULES` events every Sunday at 2 AM (preserving `IMPORTED` / `MANUAL` events)

### Scheduling Model
- Each `Minyan` entity stores separate time strings for each day type (Sunday–Shabbat, Rosh Chodesh, Yom Tov, Chanuka, etc.)
- Time strings support:
  - Fixed: `T07:30:00:0` (7:30 AM)
  - Dynamic (Zman-based): `RNETZ:10` (10 min after Netz)
  - Rounded dynamic: `QPLAG_HAMINCHA:-5` (5 min before Plag, rounded to nearest 5 min)
  - No service: `NM`

### Role Hierarchy
| Role | Access |
|---|---|
| `SUPER_ADMIN` | Unrestricted — all organizations, settings, maintenance |
| `ADMIN` | Own organization — minyanim, locations, calendar, users |
| `USER` | View only |

## Public REST API

The REST API (v1.0) is available at `/api/v1/`. All endpoints are public (no auth required).

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/organizations` | List all enabled organizations |
| GET | `/api/v1/organizations/{id}` | Single organization by ID or slug |
| GET | `/api/v1/organizations/{id}/schedule` | Per-org effective schedule (max 30-day range) |
| GET | `/api/v1/schedule` | Combined all-org schedule (max 14-day range) |
| GET | `/api/v1/zmanim` | 14 halachic times for a given date |
| GET | `/api/v1/notifications` | Active announcements/banners |

- **Interactive docs (Scalar)**: `/api/docs`
- **OpenAPI JSON**: `/api/docs.json`
- **Rate limit**: 60 requests / minute / IP (configurable)
- **Full reference**: [`docs/api/README.md`](docs/api/README.md)

## Documentation

- [`docs/api/README.md`](docs/api/README.md) — Public REST API reference
- [`docs/v1.4.0/ARCHITECTURE_OVERVIEW.md`](docs/v1.4.0/ARCHITECTURE_OVERVIEW.md) — Materialized calendar architecture
- [`CHANGELOG.md`](CHANGELOG.md) — Full version history

## Project Status

This is an active project under continuous development. The web app, REST API, and mobile app are all actively maintained.

## Contributing

Please reach out if you are interested in helping develop the project. Areas of focus include:
- Additional calendar import providers
- Enhanced mobile experience
- Multi-language support (Hebrew UI)
- Push notification scheduling

## Support

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/M4M314FOFQ)

## License

[Add license information here]
