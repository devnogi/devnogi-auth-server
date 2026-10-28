import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8').replace(/\r\n/g, '\n');
const workflow = read('../.github/workflows/deploy-prod.yml');
const compose = read('../docker-compose-auth-prod.yml');
const application = read('../src/main/resources/application-sample.yml');

test('only main push or main manual dispatch can reach production jobs', () => {
  assert.match(workflow, /^on:\n  push:\n    branches: \[main\]\n  workflow_dispatch:/m);
  assert.doesNotMatch(workflow, /refs\/heads\/dev|pull_request:/);
  const guard = "if: github.ref == 'refs/heads/main' && (github.event_name == 'push' || github.event_name == 'workflow_dispatch')";
  assert.equal(workflow.split(guard).length - 1, 3);
  assert.match(workflow, /group: auth-production-deploy\n  cancel-in-progress: false/);
});

test('the built digest is validated, deployed and recoverable', () => {
  assert.match(workflow, /tags: .*auth-server:\$\{\{ github.sha \}\}/);
  assert.match(workflow, /digest: \$\{\{ steps.build.outputs.digest \}\}/);
  assert.match(workflow, /docker buildx imagetools inspect "\$IMAGE_REF"/);
  assert.match(workflow, /auth-server@\$\{\{ needs.build-and-push.outputs.digest \}\}/);
  assert.match(compose, /image: \$\{IMAGE_REF:\?Set the immutable GHCR image digest\}/);
  assert.match(workflow, /Prior deployment files are missing; refusing an unrecoverable update/);
  assert.match(workflow, /! health_ok; then[\s\S]*IMAGE_REF="\$old_image_ref"[\s\S]*\/opt\/auth\/docker-compose-auth-prod.yml[\s\S]*exit 1/);
  assert.match(workflow, /preflight-prod-deploy\.sh/);
  assert.match(workflow, /docker manifest inspect "\$IMAGE_REF"/);
  assert.match(workflow, /appleboy\/ssh-action@0ff4204d59e8e51228ff73bce53f80d53301dee2/);
});

test('production retains env file and MinIO endpoint contract', () => {
  assert.match(compose, /env_file:\n\s+- \$\{DEPLOY_ENV_FILE:-\.env\.prod\}/);
  assert.match(compose, /SPRING_PROFILES_ACTIVE=prod/);
  assert.doesNotMatch(compose, /JAVA_OPTS|SPRING_CLOUD_AWS_S3_ENDPOINT/);
  assert.match(application, /endpoint: \$\{MINIO_ENDPOINT\}/);
  assert.doesNotMatch(application, /OCI_NAMESPACE|MAIL_PASSWORD|^\s+mail:/m);
});
