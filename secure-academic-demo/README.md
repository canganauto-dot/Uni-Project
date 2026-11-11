# Campus Secure — academic assignment demo

A compact local application demonstrating authentication, role-based authorization, ownership checks, audit events, and HMAC-SHA256 grade integrity. The existing Professor OS project is separate and is not modified by this project.

## Run

Requires Java 17 or newer and Maven 3.9+. From this folder run `mvn spring-boot:run`. The SQLite database is created as `secure-academic.db` in the working directory; set `DB_PATH` to use another path. Set `GRADE_HMAC_SECRET` to a private random value of at least 32 characters before starting. Without it, grade publishing is disabled.

Demo accounts (change these before any shared use):

| Role | Username | Password |
|---|---|---|
| Professor | `professor` | `Professor123!` |
| Student | `student` | `Student123!` |
| Student | `student2` | `Student123!` |

Open <http://localhost:8080>. The UI uses HTTP Basic authentication over a local session in the browser. Do not expose this demo over an untrusted network.

## Security demonstration

1. Sign in as `student`, submit work to an assignment, and note the student identity is taken from the authenticated server principal.
2. Sign in as `student2`: the submissions view returns only that account's submissions; unpublished grades are omitted from `/api/grades`.
3. Sign in as `professor`, create an assignment, review submissions, save a grade, then publish it.
4. Sign back in as the student and see the published grade. The server checks its HMAC signature before returning it. Change a stored grade in SQLite and reload `/api/grades` to see integrity verification reject the altered value.
5. The professor's Audit trail records assignment creation, submission, grading, and publication.

## Implementation notes

Passwords are BCrypt hashes. Professor routes are checked on the server; student submission listing is filtered by the authenticated username, and grades are student scoped. The HMAC key comes only from `GRADE_HMAC_SECRET`, not the database. This intentionally small assignment demo uses Hibernate schema update and seeded demo accounts; it is not a production identity, migration, or deployment setup.
