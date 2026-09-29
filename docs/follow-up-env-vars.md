# Follow-up: move plaintext secrets to environment variables

Status: **open / not started.** Recorded during the Step 2 baseline work. No code
change has been made for this item yet.

## Problem

`store-service/src/main/resources/application-dev.yml` and `docker-compose.yml`
both contain credentials in plaintext:

| File | Key | Value |
| --- | --- | --- |
| `application-dev.yml` | `spring.datasource.password` | `nicsi_dev` |
| `docker-compose.yml` | `POSTGRES_PASSWORD` | `nicsi_dev` |
| `docker-compose.yml` | `MINIO_ROOT_PASSWORD` | `minioadmin` |

These are local development credentials for `localhost:5432`, not production
secrets. They are nonetheless already present in this repository's published
history, so removing them from the working tree later would not remove them from
GitHub — a history rewrite would be required, which is out of scope here.

`application.yml` additionally carries a default JWT signing key
(`${JWT_SECRET:...}`). It is already env-overridable, so the fallback is the only
part worth removing.

## Constraint that must not be broken

`application-dev.yml` is required to build. 36 test classes are annotated
`@ActiveProfiles("dev")` and will fail to start without it, so this file must
remain tracked in git. Gitignoring it is **not** an acceptable fix.

## Planned change

1. `application-dev.yml` — read the password from an env var with a dev default:
   ```yaml
   spring:
     datasource:
       url: ${DB_URL:jdbc:postgresql://localhost:5432/nicsi_store}
       username: ${DB_USERNAME:nicsi}
       password: ${DB_PASSWORD:nicsi_dev}
   ```
2. `application.yml` — drop the hardcoded JWT fallback, so an unset `JWT_SECRET`
   fails fast at startup instead of silently signing tokens with a known key.
3. `docker-compose.yml` — move the two passwords to an untracked `.env` file
   (already gitignored via the `.env` / `.env.*` patterns) and reference them as
   `${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env}`.
4. Add `docs/local-setup.md` documenting the required variables and a sample
   `.env.example` that carries no real values.
5. Document in `README.md` that contributors must copy `.env.example` to `.env`
   before running the stack.

## Why this is deferred

`JWT_SECRET` is read during context startup, so removing the fallback changes
startup behaviour for anyone who currently runs with no `JWT_SECRET` set. That
belongs in its own change with a full test run, not folded into a security commit
whose diff should stay limited to the Issue authorization fix and the
`permitAll` regression.
