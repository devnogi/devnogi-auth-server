# devnogi-auth-server

- This directory is an independent Git repository. Inspect root, branch, status and the approved specification before editing. Preserve existing changes.
- Read README.md and docs/development.md. Build/runtime configuration belongs here; cross-service work needs named provider and consumer contracts.
- Java 21; use the committed Gradle wrapper. Verify: ./gradlew verify --no-daemon (Windows: .\gradlew.bat verify --no-daemon). This runs check plus bootJar, and never auto-formats sources.
- Run ./gradlew spotlessApply explicitly only when intentionally formatting a change and this task exists. Never skip a failing test/check to claim success.
- PR CI is verification only. Image publication and deployment are manual workflows, with a separate environment and branch guard. Do not dispatch, merge or deploy without explicit authorization.
- Until 2026-12-31 23:59:59 KST, an approved operator may manually deploy `dev` to `production` after the environment's reviewer gate; the workflow blocks `dev` from 2027-01-01 00:00 KST. This temporary allowance does not authorize an agent to dispatch deployment.
- Never commit actual .env files, keys, production data or credentials. Tests use synthetic fixtures.
