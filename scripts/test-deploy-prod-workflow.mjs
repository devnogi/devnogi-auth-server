import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const workflow = readFileSync(new URL('../.github/workflows/deploy-prod.yml', import.meta.url), 'utf8').replace(/\r\n/g, '\n');
const compose = readFileSync(new URL('../docker-compose-auth-prod.yml', import.meta.url), 'utf8').replace(/\r\n/g, '\n');
const cutoff = Date.UTC(2026, 11, 31, 15) / 1000;
const bash = process.env.DEPLOY_TEST_BASH || 'bash';

function job(name, nextName) {
  const start = workflow.indexOf('  ' + name + ':\n');
  assert.notEqual(start, -1, 'missing job: ' + name);
  const end = nextName ? workflow.indexOf('  ' + nextName + ':\n', start + 1) : workflow.length;
  assert.notEqual(end, -1, 'missing next job: ' + nextName);
  return workflow.slice(start, end);
}

function step(jobText, name) {
  const start = jobText.indexOf('      - name: ' + name + '\n');
  assert.notEqual(start, -1, 'missing step: ' + name);
  const next = jobText.indexOf('      - name: ', start + 1);
  return jobText.slice(start, next < 0 ? undefined : next);
}

function block(stepText, marker, indent) {
  const start = stepText.indexOf(marker);
  assert.notEqual(start, -1, 'missing ' + marker.trim());
  return stepText.slice(start + marker.length)
    .split('\n')
    .filter((line) => line.startsWith(' '.repeat(indent)))
    .map((line) => line.slice(indent))
    .join('\n');
}

function execute(script, ref, epoch) {
  const expanded = script
    .replaceAll('$(date -u +%s)', String(epoch))
    .replaceAll('${{ github.ref }}', ref);
  const result = spawnSync(bash, ['-c', expanded], {
    encoding: 'utf8',
    env: { ...process.env, GITHUB_REF: ref },
  });
  assert.equal(result.error, undefined, result.error?.message);
  return result.status;
}

const preflight = job('preflight', 'build-and-push');
const build = job('build-and-push', 'deploy');
const deploy = job('deploy');
const preflightCheck = step(preflight, 'Check branch and temporary dev deployment window');
const buildApproval = step(build, 'Recheck temporary dev deployment window after build approval');
const prePublish = step(build, 'Recheck dev window before publishing image');
const deployApproval = step(deploy, 'Recheck temporary dev deployment window after deploy approval');
const preSsh = step(deploy, 'Recheck dev window before first SSH connection');
const remote = block(step(deploy, 'Setup and Execute Docker on Server 2'), '          script: |\n', 12);

test('only manual workflow dispatch reaches production jobs', () => {
  assert.match(workflow, /^on:\n  workflow_dispatch:\n/m);
  assert.doesNotMatch(workflow, /^  (push|pull_request):/m);
  assert.doesNotMatch(preflight, /environment: production/);
  for (const jobText of [build, deploy]) {
    assert.match(jobText, /github\.event_name == 'workflow_dispatch'/);
    assert.match(jobText, /github\.ref == 'refs\/heads\/main' \|\| github\.ref == 'refs\/heads\/dev'/);
    assert.match(jobText, /environment: production/);
    assert.match(jobText, /needs: (?:preflight|\[preflight, build-and-push\])/);
    assert.match(jobText, /steps:\n      - name: Recheck temporary dev deployment window after (?:build|deploy) approval/);
  }
});

test('branch and cutoff gate uses actual workflow shell for synthetic times', () => {
  assert.equal(cutoff, 1798729200);
  const script = block(preflightCheck, '        run: |\n', 10);
  for (const time of [cutoff - 1, cutoff]) {
    assert.equal(execute(script, 'refs/heads/main', time), 0);
    assert.notEqual(execute(script, 'refs/heads/feature', time), 0);
  }
  assert.equal(execute(script, 'refs/heads/dev', cutoff - 1), 0);
  assert.notEqual(execute(script, 'refs/heads/dev', cutoff), 0);
});

test('approval waits and publication or SSH delays expire safely', () => {
  const checks = [buildApproval, prePublish, deployApproval, preSsh];
  for (const check of checks) {
    assert.match(check, /if: github\.ref == 'refs\/heads\/dev'/);
    const script = block(check, '        run: |\n', 10);
    assert.equal(execute(script, 'refs/heads/dev', cutoff - 1), 0);
    assert.notEqual(execute(script, 'refs/heads/dev', cutoff), 0);
  }
  assert.ok(build.indexOf('Recheck dev window before publishing image') < build.indexOf('Build and push Docker image'));
  assert.ok(deploy.indexOf('Recheck dev window before first SSH connection') < deploy.indexOf('Prepare staging directory on Server 2'));
});

test('server cutoff check is immediately before container update', () => {
  const up = remote.indexOf('docker compose -p auth -f docker-compose-auth-prod.yml --env-file "$DEPLOY_ENV_FILE" up -d');
  assert.notEqual(up, -1);
  const start = remote.lastIndexOf("if [ '${{ github.ref }}' = 'refs/heads/dev' ]", up);
  assert.notEqual(start, -1);
  const guard = remote.slice(start, up);
  assert.ok(guard.trimEnd().endsWith('fi'), 'remote UTC guard must immediately precede docker compose up');
  assert.ok(guard.includes('$(date -u +%s)') && guard.includes('1798729200'));
  assert.equal(execute(guard, 'refs/heads/main', cutoff), 0);
  assert.equal(execute(guard, 'refs/heads/dev', cutoff - 1), 0);
  assert.notEqual(execute(guard, 'refs/heads/dev', cutoff), 0);
});

test('main and dev tags both deploy the verified build digest', () => {
  assert.match(build, /tags: .*github\.ref == 'refs\/heads\/dev' && format\('dev-\{0\}', github\.sha\) \|\| github\.sha/);
  assert.ok(build.includes('digest: ${{ steps.build.outputs.digest }}'));
  assert.ok(build.includes('docker buildx imagetools inspect "$IMAGE_REF"'));
  assert.ok(deploy.includes("IMAGE_REF='ghcr.io/${{ github.repository }}/auth-server@${{ needs.build-and-push.outputs.digest }}'"));
  assert.ok(deploy.includes('docker manifest inspect "$IMAGE_REF"'));
  assert.ok(compose.includes('image: ${IMAGE_REF:?Set the immutable GHCR image digest}'));
  assert.doesNotMatch(compose, /AUTH_IMAGE_TAG|:latest/);
});
