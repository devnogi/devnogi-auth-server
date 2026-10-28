import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';

const yaml = readFileSync(new URL('../.github/workflows/deploy-prod.yml', import.meta.url), 'utf8').replace(/\r\n/g, '\n');
const marker = '          script: |\n            set -eu\n            cd /opt/auth/.candidate';
const at = yaml.indexOf(marker);
assert.notEqual(at, -1);
const script = yaml.slice(at + '          script: |\n'.length).split('\n').map(line => line.slice(12)).join('\n')
  .replaceAll('/opt/auth', '${FIX_ROOT}/auth')
  .replaceAll('${{ github.repository }}', 'fixture/auth')
  .replaceAll('${{ github.actor }}', 'fixture')
  .replaceAll('${{ needs.build-and-push.outputs.digest }}', 'sha256:abc');
const bash = process.env.DEPLOY_TEST_BASH || 'bash';
const mock = `
docker() {
  printf '%s\\n' "$*" >> "$FIX_ROOT/events"
  case "$*" in
    "inspect auth-server"*) [ "$CASE" != first ] || return 1; echo old-id ;;
    "image inspect"*) echo old@sha256:abc ;;
    *" pull auth-server"*) [ "$CASE" != pull-fail ] ;;
    *" up "*)
      case "$*" in *"/auth/docker-compose-auth-prod.yml"*) [ "$CASE" != rollback-fail ] ;; *) [ "$CASE" != start-fail ] ;; esac ;;
  esac
}
curl() {
  case "$*" in *:8092/*) [ "$CASE" != rollback-health-fail ] ;; *) case "$CASE" in smoke-fail|rollback-fail|rollback-health-fail) return 1 ;; esac ;; esac
}
sleep() { :; }
sh() { [ "$CASE" != preflight-fail ] || return 1; echo 8091; }
`;
function run(scenario) {
  const root = mkdtempSync(join(tmpdir(), 'auth-deploy-fixture-'));
  const posix = root.replaceAll('\\', '/').replace(/^([A-Za-z]):/, (_, drive) => '/' + drive.toLowerCase());
  try {
    const auth = join(root, 'auth');
    const candidate = join(auth, '.candidate');
    mkdirSync(join(candidate, '.deploy-secrets'), { recursive: true });
    writeFileSync(join(candidate, '.deploy-secrets', '.env.prod'), 'SERVER_PORT=8091\n');
    writeFileSync(join(candidate, '.deploy-secrets', '.ghcr-token'), 'fixture');
    writeFileSync(join(candidate, 'docker-compose-auth-prod.yml'), 'fixture');
    if (scenario !== 'missing-old-config') {
      writeFileSync(join(auth, '.env.prod'), 'SERVER_PORT=8092\n');
      writeFileSync(join(auth, 'docker-compose-auth-prod.yml'), 'fixture');
    }
    const result = spawnSync(bash, ['-c', mock + script], { encoding: 'utf8', env: { ...process.env, FIX_ROOT: posix, CASE: scenario } });
    assert.equal(result.error, undefined, result.error?.message);
    const events = (() => { try { return readFileSync(join(root, 'events'), 'utf8'); } catch { return ''; } })();
    return { ...result, events };
  } finally { rmSync(root, { recursive: true, force: true }); }
}

test('first deployment and existing deployment succeed', () => {
  for (const scenario of ['first', 'success']) assert.equal(run(scenario).status, 0, scenario);
});
test('preflight, pull and missing previous config fail before replacement', () => {
  for (const scenario of ['preflight-fail', 'pull-fail', 'missing-old-config']) {
    const result = run(scenario); assert.notEqual(result.status, 0, scenario); assert.doesNotMatch(result.events, / up /);
  }
});
test('failed candidate start or smoke restores prior digest and checks prior port', () => {
  for (const scenario of ['start-fail', 'smoke-fail']) {
    const result = run(scenario); assert.notEqual(result.status, 0, scenario);
    assert.match(result.events, /\/auth\/docker-compose-auth-prod.yml/);
    assert.match(result.stderr, /Rollback restored prior image and passed HTTP\/DB smoke check/);
  }
});
test('rollback failure and failed restored smoke are reported distinctly', () => {
  assert.match(run('rollback-fail').stderr, /Rollback failed/);
  assert.match(run('rollback-health-fail').stderr, /Rollback health check failed/);
});
