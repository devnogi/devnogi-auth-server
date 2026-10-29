import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8').replace(/\r\n/g, '\n');
const ci = read('../.github/workflows/verify.yml');
const prod = read('../.github/workflows/deploy-prod.yml');
const compose = read('../docker-compose-auth-prod.yml');
const revision = '9506ea4c4dd3147e3ab15a8b06e780ce416908a6';

test('PR and diagnostic verification use the pinned Auth profile', () => {
  assert.match(ci, /pull_request:\n    branches: \[dev, main\]\n  workflow_dispatch:/);
  assert.ok(ci.includes(`spring-ci.yml@${revision}`));
  for (const input of ['service-profile: auth-mysql', 'image-name: devnogi-auth-server', 'dockerfile: Dockerfile', 'compose-file: docker-compose-auth-prod.yml']) assert.ok(ci.includes(input));
});

test('production caller is limited to main push or dispatch', () => {
  assert.match(prod, /push:\n    branches: \[main\]\n  workflow_dispatch:/);
  assert.doesNotMatch(prod, /pull_request:/);
  assert.ok(prod.includes("github.ref == 'refs/heads/main' && (github.event_name == 'push' || github.event_name == 'workflow_dispatch')"));
  assert.ok(prod.includes(`spring-prod.yml@${revision}`));
  for (const input of ['service-profile: auth-mysql', 'image-name: devnogi-auth-server', 'dockerfile: Dockerfile', 'compose-file: docker-compose-auth-prod.yml', "health-timeout: '300'", 'platform: linux/amd64', 'secrets: inherit']) assert.ok(prod.includes(input));
  assert.ok(compose.includes('image: ${IMAGE_REF:?IMAGE_REF is required}'));
});
