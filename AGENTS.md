# Repository Guidelines

## Project Structure & Module Organization
`src/main/java/com/tbdev/teaneckminyanim/` contains the Spring Boot backend. Key packages are `controllers/`, `api/`, `service/`, `repo/`, `model/`, and `security/`. Server-rendered views live in `src/main/resources/templates/`, with admin templates under `templates/admin/`; static CSS/JS/assets live in `src/main/resources/static/`. Tests are in `src/test/java/`, with fixtures in `src/test/resources/fixtures/`. The Expo mobile app is isolated in `mobile/` (`app/`, `components/`, `api/`, `utils/`). Longer-form architecture, migrations, and release notes are under `docs/`.

## Build, Test, and Development Commands
From the repo root:

- `./mvnw clean package` builds the backend JAR.
- `./mvnw spring-boot:run` starts the web app locally on port 8080.
- `./mvnw test` runs JUnit 5 tests.
- `./mvnw clean package -DskipTests` matches the dev deploy workflow build.

From `mobile/`:

- `npm install` installs Expo dependencies.
- `npm start` launches the Expo dev server.
- `npm run ios`, `npm run android`, `npm run web` run the mobile client on each target.

## Coding Style & Naming Conventions
Follow existing Java style: 4-space indentation, `PascalCase` classes, `camelCase` methods/fields, singular entity names (`Organization`, `Minyan`), and package-by-responsibility organization. Keep controllers thin and put business rules in `service/`. For Thymeleaf and static assets, use descriptive kebab-case filenames such as `printable-schedule.html` and `calendar-events-tabulator.js`. In the mobile app, keep React components in `PascalCase` and utility modules in lowercase or camelCase.

## Testing Guidelines
Backend tests use JUnit 5 and Mockito. Add tests beside the code they cover, using `*Test.java` naming, for example `CalendarUrlBuilderTest`. Prefer focused unit tests for parser, classifier, and service logic; use fixtures from `src/test/resources/fixtures/` when parsing imported data. Run `./mvnw test` before opening a PR. There is no dedicated mobile test suite configured yet, so at minimum verify critical mobile flows manually in Expo.

## Commit & Pull Request Guidelines
Recent history favors short, imperative subjects with optional prefixes: `fix:`, `docs:`, `ci:`. Keep the first line under about 72 characters and describe the user-visible change, for example `fix: guard null nusach on org page`. PRs should target `dev`, summarize behavior changes, note config or schema impacts, and include screenshots for UI/admin/mobile work. Update `CHANGELOG.md` for user-facing or operational changes; skip changelog-only edits for agent-instruction files and tooling-only tweaks.
