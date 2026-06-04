@AGENTS.md

## Quick Commands

```bash
# Dev mode (requires Kafka via docker-compose up -d first)
./mvnw quarkus:dev

# Compile without tests (fast feedback on syntax/type errors)
./mvnw compile -q
```

## Notes for Claude Code

- There is no linter, formatter, or checkstyle configured. Do not attempt to run any.
- There are no git hooks (pre-commit, commit-msg, etc.). Commits run without hooks.
- After modifying any Java file, run `./mvnw compile -q` to catch errors before running the full test suite.
