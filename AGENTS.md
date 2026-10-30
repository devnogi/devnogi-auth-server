# devnogi-auth-server

- This directory is an independent Git repository. Inspect root, branch, status and the approved specification before editing. Preserve existing changes.
- Read README.md and docs/development.md. Build/runtime configuration belongs here; cross-service work needs named provider and consumer contracts.
- Java 21; use the committed Gradle wrapper. Verify: ./gradlew verify --no-daemon (Windows: .\gradlew.bat verify --no-daemon). This runs check plus bootJar, and never auto-formats sources.
- Run ./gradlew spotlessApply explicitly only when intentionally formatting a change and this task exists. Never skip a failing test/check to claim success.
- PR CI is verification only. A main push invokes the pinned shared production workflow, which verifies, publishes and deploys the immutable image digest. Manual recovery dispatch is restricted to main. A merge therefore triggers production deployment.
- The shared workflow owns production preflight, secret staging, pinned actions and rollback/health checks. Production Compose requires `IMAGE_REF`; the old GHCR-specific `scripts/preflight-prod-deploy.sh` is retained for historical reference and is not used by the shared workflow.
- Never commit actual .env files, keys, production data or credentials. Tests use synthetic fixtures.
