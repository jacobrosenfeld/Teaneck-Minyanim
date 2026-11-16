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
Recent history favors short, imperative subjects with optional prefixes: `fix:`, `docs:`, `ci:`. Keep the first line under about 72 characters and describe the user-visible change, for example `fix: guard null nusach on org page`. PRs should target `main` (not `dev`; see the testing flow below), summarize behavior changes, note config or schema impacts, and include screenshots for UI/admin/mobile work. Update `CHANGELOG.md` for user-facing or operational changes; skip changelog-only edits for agent-instruction files and tooling-only tweaks.

When the user says "staging branch", interpret that as likely referring to the `dev` branch unless they explicitly specify a separate branch named `staging`.

### Testing a PR on the dev site
Don't merge a PR into `dev` to test it; that closes the PR. Keep the PR open against `main` and deploy its branch to dev instead: run the **Deploy to dev** workflow manually (`workflow_dispatch`), picking the branch in "Use workflow from" and leaving the `ref` input blank. Via the API, dispatch on `main` and set `ref` to the branch name, which also works for branches that predate the manual trigger. To test several open PRs together, build a throwaway `test/<name>` branch from `main`, merge the PR branches into it, and deploy that ref. Fix issues on the original feature branch, then rebuild the `test/*` branch. Only one ref is live on dev at a time; the latest deploy wins. See `CLAUDE.md` → Branch & Deployment Strategy for caveats about the shared dev database.

## Version & Changelog Rules
- Bump the project version in `pom.xml` once per release-intent change set (for example, a new PR/release candidate).
- Use semantic versioning:
  - Patch (`x.y.z`) for fixes/UI updates.
  - Minor (`x.y.0`) for new features.
  - Major (`x.0.0`) for breaking changes.
- Add corresponding changelog notes in `CHANGELOG.md` under `## [Unreleased]` using Keep a Changelog sections (`Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`) as applicable.
- If a commit is a direct follow-up fix for a bug introduced in the same unreleased change set, do **not** bump the version again; keep updating the existing `[Unreleased]` entry.
- Exception: do **not** bump version or changelog for agent-instruction-only updates (for example `AGENTS.md`, `CLAUDE.md`, or similar AI context files) and non-runtime tooling-only adjustments.
