# devnogi-auth-server

- This directory is an independent Git repository. Inspect root, branch, status and the approved specification before editing. Preserve existing changes.
- Read README.md and docs/development.md. Build/runtime configuration belongs here; cross-service work needs named provider and consumer contracts.
- Java 21; use the committed Gradle wrapper. Verify: ./gradlew verify --no-daemon (Windows: .\gradlew.bat verify --no-daemon). This runs check plus bootJar, and never auto-formats sources.
- Run ./gradlew spotlessApply explicitly only when intentionally formatting a change and this task exists. Never skip a failing test/check to claim success.
- PR CI is verification only. A push to `main` deploys the verified commit to production; a manual main dispatch supports recovery. A merge therefore triggers production deployment.
- The production workflow publishes the commit SHA tag and deploys the verified digest through the Compose `IMAGE_REF` requirement. Preserve digest preflight, secret staging, pinned actions, and rollback/smoke checks.
- Never commit actual .env files, keys, production data or credentials. Tests use synthetic fixtures.
