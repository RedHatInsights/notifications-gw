@AGENTS.md

## Quick Commands

```bash
# Dev mode (requires Kafka via docker-compose up -d first)
./mvnw quarkus:dev

# Compile without tests (fast feedback on syntax/type errors)
./mvnw compile -q
```

## Notes for Claude Code

- Checkstyle runs during the `validate` phase and will fail the build on violations. Fix all checkstyle errors before pushing.
- There are no git hooks (pre-commit, commit-msg, etc.). Commits run without hooks.
- After modifying any Java file, run `./mvnw compile -q` to catch errors before running the full test suite.

### Pre-Commit Verification

Before finishing any code change, run checkstyle:

```sh
./mvnw validate --no-transfer-progress
```

After checkstyle passes, run the full build to catch compile and test failures:

```sh
./mvnw clean package --no-transfer-progress
```
