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

- Renovate (via MintMaker) handles Maven, GitHub Actions, and base container image updates, configured in `renovate.jsonc`. Patch and minor bumps of vetted packages/actions automerge once required checks pass; majors, `io.quarkus*`, and the Maven wrapper stay on manual review. See `renovate.jsonc` for the full rules.
- Dependabot is not used in this repository; `.github/dependabot.yml` was removed once Renovate covered the same ecosystems, to avoid two bots racing to open PRs for the same bump. Dependabot alerts (Settings > Advanced Security) stay on regardless — Renovate's vulnerability fix PRs are built from those alerts.
- The base container image (`ubi9/openjdk-21-runtime:latest`) is pinned to `tag@sha256` in `src/main/docker/Dockerfile-build.jvm` and updated via Renovate digest PRs. The old `.github/workflows/base-image-auto-update.yml` script and its `.baseimage` tracking file were removed as redundant.
- Prefer merging automated dependency PRs from Renovate rather than manually editing versions, so the commit history retains bot attribution and PR references.

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
