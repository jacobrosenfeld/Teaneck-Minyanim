# Teaneck Minyanim Web Application

Teaneck Minyanim is a Java Spring Boot 2.6.2 web application that manages Jewish prayer group (minyan) schedules for Teaneck, New Jersey. The application provides public schedule viewing and an admin interface for managing organizations, locations, and minyanim.

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively

### Bootstrap and Build the Application
CRITICAL: All builds and commands may take longer than typical - NEVER CANCEL them:

1. **Verify Prerequisites:**
   ```bash
   java --version  # Should show Java 17+
   mvn --version   # Should show Maven 3.6+
   ```

2. **Clean and Build (NEVER CANCEL - timing varies by system):**
   ```bash
   ./mvnw clean                    # Takes ~1 second
   ./mvnw compile                  # Takes ~4 seconds (first time: ~45 seconds with downloads)
   ./mvnw package -DskipTests      # Takes ~3 seconds (first time: ~30 seconds with downloads)
   ```
   - NEVER CANCEL: Initial builds take 45+ seconds due to dependency downloads
   - Set timeout to 60+ minutes for first-time builds
   - Subsequent builds are much faster (1-4 seconds)
   - Expected warnings about deprecated APIs and unchecked operations (normal)

3. **Test Framework:**
   ```bash
   ./mvnw test  # Tests exist but have dependency issues - this is expected
   ```
   - Test framework has JUnit version conflicts (known issue)
   - Tests run: 0 (tests are commented out in TeaneckMinyanimApplicationTests.java)
   - Build still succeeds - this is normal

### Run the Application
**IMPORTANT**: Application requires MariaDB database connection to start successfully.

1. **Attempt to Start (will fail without database):**
   ```bash
   java -jar target/Teaneck-Minyanim-1.1.0-SNAPSHOT.jar
   ```
   - EXPECTED: Application fails with MariaDB connection error
   - Database config: `localhost:3306/minyanim` (username: root, password: passw0rd)
   - Failure is normal and expected without database setup

2. **Database Requirements:**
   - MariaDB 10.3+ required on localhost:3306
   - Database name: `minyanim`
   - Username: `root`, Password: `passw0rd`
   - Schema auto-created via Hibernate DDL (spring.jpa.hibernate.ddl-auto=update)

## Validation

### Manual Testing Scenarios
Always manually validate any new code via these scenarios after making changes:

1. **Build Validation:**
   ```bash
   ./mvnw clean package -DskipTests  # Must complete successfully in ~8 seconds
   ls -lh target/*.jar               # Verify main JAR is ~58MB, original ~1.3MB
   ```

2. **Application Startup Test:**
   ```bash
   timeout 15s java -jar target/Teaneck-Minyanim-1.1.0-SNAPSHOT.jar --server.port=0
   ```
   - EXPECTED OUTPUT: Should show Spring Boot banner, Tomcat initialization
   - EXPECTED FAILURE: MariaDB connection refused error (this confirms proper structure)
   - If no Spring Boot banner appears, there's a fundamental build issue

3. **Static Content Check:**
   - Verify changes to templates in `src/main/resources/templates/`
   - Check JavaScript files in `src/main/resources/static/`
   - Confirm CSS changes in `src/main/resources/static/assets/css/`

4. **Dependency Resolution Test:**
   ```bash
   ./mvnw dependency:resolve  # Should complete in ~23 seconds without errors
   ```

### Code Quality
- No automated linting tools configured
- Follow existing Java Spring Boot coding patterns
- Use Lombok annotations consistently (@RequiredArgsConstructor, @Builder, etc.)

## Common Tasks

### Repository Structure
```
Teaneck-Minyanim/
├── .github/
│   └── workflows/        # GitHub Actions (deploy.yml, tunnel.yml)
├── src/main/java/com/tbdev/teaneckminyanim/
│   ├── controllers/      # REST endpoints (@GetMapping, @PostMapping)
│   ├── model/           # JPA entities
│   ├── repo/            # JPA repositories
│   ├── service/         # Business logic
│   ├── security/        # Spring Security configuration
│   ├── enums/           # Application enums
│   ├── minyan/          # Minyan-specific logic
│   ├── front/           # Frontend utilities
│   └── tools/           # Utility classes
├── src/main/resources/
│   ├── templates/       # Thymeleaf templates (.html)
│   ├── static/          # CSS, JS, images
│   └── application.properties  # Database and app config
└── src/test/java/       # Test files (mostly empty/commented)
```

### Key Application Components
- **Main class:** `TeaneckMinyanimApplication.java`
- **Database:** MariaDB with JPA/Hibernate
- **Templates:** Thymeleaf with Bootstrap 4.6.1
- **Security:** Spring Security with custom authentication
- **Zmanim:** Jewish calendar integration via `com.kosherjava:zmanim:2.5.0`

### Important Endpoints
**Public:**
- `/` - Homepage with minyan schedules
- `/zmanim` - Jewish calendar times
- `/subscription` - Subscription management

**Admin (requires authentication):**
- `/admin/login` - Admin login
- `/admin` - Admin dashboard  
- `/admin/organizations` - Manage organizations
- `/admin/new-minyan` - Create new minyan

### Key Files to Check After Changes
- Always verify `src/main/java/com/tbdev/teaneckminyanim/controllers/AdminController.java` after modifying admin functionality
- Check `src/main/resources/static/admin/new-minyan.js` after updating minyan creation UI
- Verify `src/main/resources/application.properties` for database configuration changes
- Review `src/main/resources/templates/homepage.html` for public interface changes

### Dependencies and Versions
- Java 17
- Spring Boot 2.6.2
- MariaDB Java Client 2.5.2
- Zmanim Library 2.5.0
- Bootstrap 4.6.1 (WebJars)
- Thymeleaf with Spring Security 5 extras

### Build Artifacts
- Main JAR: `target/Teaneck-Minyanim-1.1.0-SNAPSHOT.jar` (58MB)
- Original JAR: `target/Teaneck-Minyanim-1.1.0-SNAPSHOT.jar.original` (~1.3MB)
- Uses Spring Boot's fat JAR packaging with embedded Tomcat
- Verify build success: `ls -lh target/*.jar` should show both files

### Common Patterns
- Controllers use `@RequiredArgsConstructor` with injected services
- Models use Lombok for builders and accessors
- JPA repositories extend `JpaRepository<Entity, String>`
- Thymeleaf templates use Bootstrap classes and custom CSS
- JavaScript uses jQuery 3.6.3 for DOM manipulation

### Troubleshooting
- **Build failures:** Usually dependency download issues - retry with longer timeout
- **Application won't start:** Check MariaDB connection settings in application.properties
- **Missing dependencies:** Run `./mvnw dependency:resolve` to download all dependencies (takes ~23 seconds)
- **Template not found:** Verify Thymeleaf template path matches controller return value
- **JAR size verification:** Main JAR should be ~58MB, original JAR ~1.3MB