# Dependency Management Guidelines

## Version Properties in pom.xml

- Define every non-BOM-managed dependency version as a `<properties>` entry in `pom.xml` (e.g., `<testcontainers.version>1.21.4</testcontainers.version>`), then reference it with `${...}` in the dependency declaration.
- Avoid hardcoding version numbers directly in `<dependency>` or `<plugin>` blocks. Two exceptions currently exist (`json-schema-validator` at `2.2.14` and `git-commit-id-maven-plugin` at `9.0.2`); prefer extracting these to properties when touching those dependencies.
- Omit `<version>` for dependencies managed by the Quarkus BOM (`quarkus-bom` imported in `<dependencyManagement>`). Quarkus extensions like `quarkus-cache`, `quarkus-rest-jackson`, `quarkus-rest-client-jackson`, `quarkus-messaging-kafka`, and test dependencies like `quarkus-junit5-mockito`, `rest-assured`, `smallrye-reactive-messaging-in-memory`, and `awaitility` inherit their versions from the BOM.

## Dependency Organization in pom.xml

- Group dependencies under the existing section comments in `pom.xml`: `<!-- Insights -->`, `<!-- Quarkus -->`, `<!-- Quarkiverse -->`, `<!-- Clowder -->`, then test-scoped dependencies at the end.
- Place new Quarkus extensions (groupId `io.quarkus`) under the `<!-- Quarkus -->` comment without a `<version>` tag.
- Place new Quarkiverse extensions (groupId `io.quarkiverse.*`) under `<!-- Quarkiverse -->` with an explicit version property.
- Mark test-only dependencies with `<scope>test</scope>` and place them after the Clowder section.

## Quarkus BOM Alignment

- Keep the `quarkus.version` property as the single source of truth for the Quarkus platform version. Both the BOM import and the `quarkus-maven-plugin` reference `${quarkus.version}`.
- When upgrading Quarkus, update only the `quarkus.version` property; do not add version overrides to individual Quarkus extensions unless resolving a specific compatibility issue.

## Automated Dependency Updates

- Dependabot handles Maven dependency bumps on a daily schedule and GitHub Actions version bumps weekly, configured in `.github/dependabot.yml`.
- Renovate is also configured via `renovate.json` (extending `github>konflux-ci/mintmaker//config/renovate/renovate.json`) and runs against the `main` branch.
- The base container image (`ubi9/openjdk-21-runtime:latest`) is tracked by digest in `.baseimage` and auto-updated nightly by `.github/workflows/base-image-auto-update.yml` using `skopeo inspect`. Do not edit `.baseimage` manually.
- Prefer merging automated dependency PRs from Dependabot/Renovate rather than manually editing versions, so the commit history retains bot attribution and PR references.

## Adding a New Dependency

- Before adding a new runtime dependency, check whether the Quarkus BOM already manages it by searching `quarkus-bom` for the artifact.
- For Quarkiverse or third-party dependencies not in the BOM, create a property like `<new-lib.version>X.Y.Z</new-lib.version>` in the `<properties>` block and reference it in the dependency declaration.
- Avoid adding dependencies that duplicate functionality already provided by a Quarkus extension in use (e.g., do not add standalone Jackson when `quarkus-rest-jackson` is present).

## Maven Wrapper

- Use `./mvnw` (not a system-installed `mvn`) for all builds. The wrapper version is pinned in `.mvn/wrapper/maven-wrapper.properties` (currently Maven 3.9.11).
- When upgrading the Maven wrapper, update the `distributionUrl` in `.mvn/wrapper/maven-wrapper.properties`.

## Verification

```bash
# Confirm pom.xml parses and dependency tree resolves
./mvnw dependency:tree -q

# Build and run tests to validate dependency compatibility
./mvnw clean package --no-transfer-progress

# Check for dependency convergence issues
./mvnw dependency:analyze -q

# List properties to audit version definitions
grep -E '<[a-z].*\.version>' pom.xml
```
