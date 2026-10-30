import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const pin = 'c5b33bfa84762986d47037f5ce3eca14a1c516ca';
const ci = read('.github/workflows/verify.yml');
const prod = read('.github/workflows/deploy-prod.yml');
const build = read('build.gradle.kts');
const security = read('src/main/java/until/the/eternity/das/common/config/SecurityConfig.java');

test('PR caller targets the approved reusable Auth CI', () => {
  assert.match(ci, /pull_request:\s*\n\s*branches: \[dev, main\]/);
  assert.match(ci, /workflow_dispatch:/);
  assert.match(ci, new RegExp(`spring-ci\\.yml@${pin}`));
  assert.doesNotMatch(ci, /spring-prod\.yml|\bpush:/);
  assert.match(ci, /node --test scripts\/workflow-contract\.test\.mjs/);
  assert.match(ci, /ci:\s*\n\s*needs: workflow-contract/);
});

test('production caller only permits main push or manual dispatch', () => {
  assert.match(prod, /push:\s*\n\s*branches: \[main\]/);
  assert.match(prod, /workflow_dispatch:/);
  assert.match(prod, /if: github\.ref == 'refs\/heads\/main' && \(github\.event_name == 'push' \|\| github\.event_name == 'workflow_dispatch'\)/);
  assert.doesNotMatch(prod, /pull_request:/);
  assert.match(prod, new RegExp(`spring-prod\\.yml@${pin}`));
  assert.match(prod, /permissions:\s*\n\s*contents: write/);
  assert.match(prod, /concurrency:\s*\n\s*group: auth-production\s*\n\s*cancel-in-progress: false/);
  assert.match(prod, /production:\s*\n\s*needs: workflow-contract/);
  assert.match(prod, /deployment-target: prod2/);
  assert.match(prod, /health-timeout: '300'/);
  assert.match(prod, /platform: linux\/amd64/);
  assert.match(prod, /secrets: inherit/);
  assert.match(build, /^version = "1\.0\.1-SNAPSHOT"$/m);
});

test('callers agree on Auth inputs and health deployment contracts', () => {
  for (const workflow of [ci, prod]) {
    for (const line of ['service-profile: auth-mysql', 'service-name: auth',
      'dockerfile: Dockerfile', 'compose-file: docker-compose-auth-prod.yml']) {
      assert.ok(workflow.includes(line), line);
    }
  }
  assert.match(read('docker-compose-auth-prod.yml'), /auth-server:\s*\n\s*image: \$\{IMAGE_REF:\?IMAGE_REF is required\}/);
  assert.match(security, /requestMatchers\("\/actuator\/health"\)\s*\.permitAll\(\)/);
});
