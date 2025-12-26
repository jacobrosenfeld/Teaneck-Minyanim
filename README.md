# Teaneck Minyanim

This is the code behind [Teaneck Minyanim](https://teaneckminyanim.com), a comprehensive web application that lists prayer services (minyanim) and Jewish religious times (zmanim) for Teaneck, NJ.

## Features

- **Automatic Time Calculations**: Service times automatically adjust based on the Jewish calendar using the Kosherjava library
- **Multiple Organizations**: Support for multiple synagogues and organizations
- **Rule-Based Scheduling**: Flexible scheduling system supporting fixed times and dynamic calculations based on sunrise/sunset
- **Calendar Import System**: Import events from external calendars with intelligent classification
- **Mincha/Maariv Combined Services**: Special handling for combined afternoon/evening prayers with automatic sunset (Shkiya) calculations
- **Admin Interface**: Comprehensive admin panel for managing organizations, locations, and prayer times
- **Responsive Design**: Mobile-friendly interface for users on any device

## Recent Enhancements (v1.2.2)

### Intelligent Calendar Import Classification
- **Automatic categorization** of imported events into Minyan, Non-Minyan, and special types
- **Conservative default**: Only explicitly recognized minyan patterns are shown to users
- **Title qualifier extraction**: Automatically extracts "Teen", "Early", "Women's", etc. from titles
- **Denylist filtering**: Excludes learning events (Daf Yomi, Shiur) and social events (Kiddush, Candle Lighting)

### Modern Admin UI
- **Sortable and filterable** calendar entries table
- **Inline location editing** with manual change tracking
- **Color-coded badges** showing prayer types and classifications
- **Statistics dashboard** for quick overview
- **Server-side filtering** for performance with large datasets

See [FEATURE_SUMMARY_v1.2.2.md](FEATURE_SUMMARY_v1.2.2.md) for complete details.

## Technology Stack

- **Backend**: Spring Boot, Java, Hibernate JPA
- **Database**: MariaDB
- **Frontend**: Thymeleaf templates, Bootstrap 4.6, jQuery
- **Calendar Library**: Kosherjava for Jewish calendar calculations
- **Security**: Spring Security with role-based access control

## Getting Started

### Prerequisites
- Java 11 or higher
- Maven 3.6+
- MariaDB 10.3+

### Database Setup
```sql
CREATE DATABASE minyanim CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

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

## Documentation

- [Architecture Overview](.github/copilot-instructions.md) - Detailed system architecture and patterns
- [Feature Summary v1.2.2](FEATURE_SUMMARY_v1.2.2.md) - Latest release features
- [Database Migration v1.2.2](MIGRATION_v1.2.2.sql) - Schema changes

## Project Status

This is an active project under continuous development. Contributions are welcome!

## Contributing

Please reach out if you are interested in helping develop the project. Areas of focus include:
- Additional calendar import providers
- Enhanced mobile experience
- Multi-language support
- API development for third-party integrations

## Support

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/M4M314FOFQ)

## License

[Add license information here]
