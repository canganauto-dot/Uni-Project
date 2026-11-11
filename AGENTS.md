# Professor OS Development Rules

## Project
Professor OS is an offline-first Java desktop workspace for university professors.
Use Java 25 backend services with Kotlin/JVM Compose Desktop, Maven, Gradle, SQLite and JDBC. Apache POI and PDFBox provide the implemented Excel and PDF workflows. Compose is the only UI; do not reintroduce JavaFX.

## Architecture
- Keep UI, business logic, services, repositories, database access and domain models separate.
- Dependency direction: Compose UI → Java services → repositories → database; domain models remain independent of UI toolkits and JDBC.
- Never put SQL or substantial business logic in Compose screens or controllers.
- Views build UI; controllers coordinate interactions. Demo data belongs in `demo`, never in persistence.
- Prefer small cohesive classes, descriptive names and extending existing conventions. Avoid giant controllers, duplicated logic, unused code and unnecessary abstractions.

## Dependencies and quality
- Add dependencies only for a clear reason; first check the JDK and existing dependencies. Prefer established libraries and pin versions.
- Validate external/user input. Do not create fake functionality or present demo data as persisted data.
- Never silently swallow exceptions. Log diagnostic details or propagate errors; show concise, actionable messages to users.
- Use standard Java logging until there is a concrete reason to change it.
- Keep the application runnable after meaningful changes.

## Database
- Use SQLite and enable foreign keys on every connection.
- Use parameterized SQL for variable values; never concatenate user input into queries.
- Use try-with-resources for JDBC connections, statements and result sets.
- Preserve user data. Never casually delete or recreate a production database.
- Introduce migrations/schema versioning with the first persistent domain tables.
- Keep database paths configurable; tests use temporary directories.
- **Never modify, delete, rename or overwrite an already-applied database migration.** This rule applies to all future development phases.
- Introduce all database schema changes through new, versioned migrations. Preserve migration history, checksums and existing user data.
- Never bypass migration checksum verification or alter stored checksums to hide inconsistencies.
- Before any database repair, create a verified backup and investigate the root cause. Prove the schema and data state before any explicit, documented corrective repair.

## Testing
- After meaningful changes, run `./mvnw clean verify` (Windows: `.\mvnw.cmd clean verify`) and relevant tests; fix failures before declaring completion.
- Add meaningful tests for important business behavior when practical.
- Run the Compose suite with `./prototype-compose/gradlew -p prototype-compose test` (Windows: `.\prototype-compose\gradlew.bat -p prototype-compose test`). For UI changes, inspect its snapshots and check native startup when a graphical desktop is available.
- Run Maven and Gradle sequentially, and close the development app before rebuilding: they share generated backend outputs. The Maven JAR is a backend library; launch the app with the Compose Gradle `run` task.
- Report environment limitations honestly; never claim checks that were not run.
- On the initial Windows machine, see README for the verified `cmd /d /c` plus `-DforkCount=0` workaround for an existing shell AutoRun setting. Keep default test isolation for unaffected environments.

## Documentation
- Update `PROJECT_NOTES.md` for significant architecture, schema, algorithm, dependency, feature or technical decisions, not trivial edits.
- Keep README setup instructions and implemented/planned status accurate.

## AI behavior
Before major architectural changes: inspect the code, understand conventions, preserve working behavior, favor extensions over rewrites and explain major tradeoffs.
Never rewrite large portions simply because another design is possible. Keep changes within the requested scope and understandable for an academic defense.
Do not introduce Docker, Spring Boot, React, Node.js, cloud databases or microservices without an explicit project decision.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.
