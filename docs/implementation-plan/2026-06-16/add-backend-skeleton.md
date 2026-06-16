# Add Backend Skeleton

## Objective

Create the initial Kotlin and Spring Boot backend skeleton for Time Archive using the architecture and operations documents as the implementation guide.

## Scope

- Add Gradle project configuration for a Kotlin Spring Boot backend.
- Add a minimal Spring Boot application entry point.
- Add initial Hexagonal Architecture package boundaries.
- Add the first domain value object and tests for time range rules.
- Add local Docker Compose infrastructure for PostgreSQL and optional Redis.
- Add Docker packaging files for the backend.
- Add the first GitHub Actions CI workflow for backend tests and build.
- Add basic application configuration examples.
- Update repository documentation with build and test instructions.

## Relevant Files or Modules

- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `src/main/kotlin/com/timearchive/TimeArchiveApplication.kt`
- `src/main/kotlin/com/timearchive/domain/model/TimeRange.kt`
- `src/main/kotlin/com/timearchive/domain/port/ClockPort.kt`
- `src/main/resources/application.yml`
- `src/test/kotlin/com/timearchive/domain/model/TimeRangeTest.kt`
- `docker-compose.yml`
- `Dockerfile`
- `.dockerignore`
- `.github/workflows/ci.yml`
- `README.md`
- `docs/implementation-plan/2026-06-16/add-backend-skeleton.md`

## Key Design Decisions

- Use a modular monolith with explicit Hexagonal Architecture package boundaries.
- Keep initial production logic small and domain-focused.
- Use PostgreSQL as the local database service.
- Include Redis in Docker Compose as optional infrastructure, but do not make the application depend on it yet.
- Use Spring Boot, Kotlin, Spring Web, Spring Validation, Spring Security, Spring Data JDBC, Flyway, PostgreSQL, Actuator, JUnit 5, AssertJ, MockK, and Testcontainers.
- Use Eclipse Temurin JDK and JRE base images for Docker packaging.
- Avoid Kafka and Jenkins in the initial skeleton.

## Dependency Justification

- Spring Boot: primary application framework selected in architecture documentation.
- Kotlin JVM and Kotlin Spring plugins: Kotlin backend support and Spring proxy compatibility.
- Spring Web: required for REST APIs.
- Spring Security: required for future authentication and authorization boundaries.
- Spring Validation: required for API input validation.
- Spring Data JDBC: initial persistence abstraction with explicit aggregate behavior.
- Flyway: database migration management.
- PostgreSQL driver: production-aligned database connectivity.
- Actuator: health and operational endpoints.
- JUnit 5, AssertJ, MockK: unit testing stack.
- Testcontainers PostgreSQL: integration testing against real PostgreSQL behavior.
- Testcontainers module version 1.21.4: required because Spring Boot 4.1.0 did not provide a resolved version for `org.testcontainers:junit-jupiter` and `org.testcontainers:postgresql` during local verification. Maven Repository showed 1.21.4 as the latest central version for both modules.

Alternatives considered:

- JPA instead of Spring Data JDBC: deferred to keep persistence behavior explicit.
- H2 instead of Testcontainers: rejected for important PostgreSQL-specific behavior.
- Jenkins instead of GitHub Actions: deferred by operations strategy.
- Kafka instead of transactional outbox: deferred until event volume requires it.

License compatibility:

- The selected dependencies are commonly used in commercial and open-source JVM projects.
- Dependency licenses must be verified again before production release and automated license reporting should be added later.

## Step-by-Step Execution Plan

1. Add Gradle project files.
2. Add the Spring Boot application entry point.
3. Add Hexagonal Architecture package placeholders through real minimal types.
4. Add `TimeRange` domain value object and tests.
5. Add application configuration and Docker Compose.
6. Add Docker packaging files.
7. Add GitHub Actions CI workflow.
8. Update README build and test instructions.
9. Generate or verify Gradle wrapper if possible.
10. Run tests and build.
11. Record completion details in this implementation plan.

## Risks and Rollback Strategy

- Risk: Current latest dependency versions may require adjustment after actual build verification.
  - Mitigation: Keep the skeleton small and update versions if build failures show incompatibility.
- Risk: Network-restricted dependency resolution may prevent local verification.
  - Mitigation: Record the limitation and keep configuration standard.
- Risk: Gradle wrapper generation may fail if the local Gradle installation is not usable.
  - Mitigation: Record the limitation and defer wrapper generation.
- Rollback: Revert the skeleton commit if the project structure is rejected.

## Verification Plan

- Run `gradle test` or `./gradlew test` if wrapper generation succeeds.
- Run `gradle build` or `./gradlew build` if wrapper generation succeeds.
- Confirm Git status only contains intended skeleton and documentation changes.

## Open Questions

- Should the first production deployment target be ECS Fargate or EC2 with Docker Compose?
- Should object storage start with AWS S3 or Cloudflare R2?
- Which payment provider will be selected for the first payment adapter?

## Progress

- [x] Implementation plan created.
- [x] Gradle project files added.
- [x] Spring Boot application entry point added.
- [x] Initial Hexagonal Architecture package boundaries added.
- [x] Domain value object and tests added.
- [x] Local infrastructure configuration added.
- [x] Docker packaging files added.
- [x] GitHub Actions CI workflow added.
- [x] README updated.
- [x] Build and tests run.
- [x] Completion details recorded.

## Completion Summary

Added the initial Kotlin and Spring Boot backend skeleton for Time Archive.

The skeleton follows the documented modular monolith and Hexagonal Architecture direction with a Spring Boot entry point, domain package, domain port package, adapter package placeholders, application package, and configuration package. The first meaningful domain logic is the `TimeRange` value object, which enforces inclusive start and exclusive end semantics and provides duration, containment, overlap, and archive-boundary validation behavior.

## Files Changed

- `.gitignore`
- `README.md`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `docker-compose.yml`
- `Dockerfile`
- `.dockerignore`
- `.github/workflows/ci.yml`
- `src/main/kotlin/com/timearchive/TimeArchiveApplication.kt`
- `src/main/kotlin/com/timearchive/domain/model/TimeRange.kt`
- `src/main/kotlin/com/timearchive/domain/port/ClockPort.kt`
- `src/main/resources/application.yml`
- `src/test/kotlin/com/timearchive/domain/model/TimeRangeTest.kt`
- `docs/implementation-plan/2026-06-16/add-backend-skeleton.md`

## Tests Run and Results

- `.\gradlew.bat test` - passed.
- `.\gradlew.bat build` - passed.
- `docker build -t time-archive-api:local .` - passed after rerunning with elevated permissions because the sandbox could not access Docker buildx lock files.

## Manual Verification Results

- Confirmed Gradle wrapper was generated with Gradle 9.3.0.
- Confirmed domain tests compile and pass.
- Confirmed Spring Boot bootJar is produced by `build`.
- Confirmed Docker image builds successfully from the added `Dockerfile`.
- Confirmed `.kotlin/`, `.gradle/`, and `build/` generated directories are ignored.

## Known Limitations

- No REST APIs are implemented yet.
- No database migrations are implemented yet.
- No integration tests use Testcontainers yet; dependencies are present for the next database-focused step.
- Docker Compose services were not started during this task.
- Security is present only as a dependency and not yet configured for application endpoints.

## Follow-Up Recommendations

- Add the first Flyway migration for canonical archive ownership records.
- Add application use cases and ports for querying archive metadata and timeline manifest.
- Add a basic health or info endpoint smoke test once web adapters are introduced.
- Add GitHub Actions CI workflow after confirming the desired first CI gate.
