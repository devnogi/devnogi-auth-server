import { execFileSync } from 'node:child_process';
import { readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Basic current-tree scanner, not a replacement for history scans or GitHub secret scanning.
export function secretKinds(text) {
  const patterns = [
    ['private-key', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/],
    ['github-token', /\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{60,})\b/],
    ['aws-access-key', /\bAKIA[0-9A-Z]{16}\b/],
    ['openai-key', /\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{40,}\b/],
  ];
  return patterns.filter(([, regex]) => regex.test(text)).map(([kind]) => kind);
}
export function scan(directory) {
  const files = [...new Set(execFileSync('git', ['-C', directory, 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], { encoding: 'utf8' }).split('\0').filter(Boolean))];
  const findings = [];
  for (const file of files) {
    const absolute = path.join(directory, file);
    let stats; try { stats = statSync(absolute); } catch { continue; }
    if (!stats.isFile() || stats.size > 2_000_000) continue;
    if (/(^|\/)\.env(?:\.|$)/.test(file) && !/[.-](?:example|sample|template)$/.test(file)) findings.push({ file, kind: 'tracked-environment-file' });
    const contents = readFileSync(absolute, 'utf8');
    if (contents.includes('\0')) continue;
    for (const kind of secretKinds(contents)) findings.push({ file, kind });
  }
  return findings;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const findings = scan(path.resolve(process.argv[2] || '.'));
  findings.forEach(f => console.error(`${f.file}: ${f.kind}`));
  console.log(`Basic secret scan: ${findings.length} finding(s). Values are never printed.`);
  process.exitCode = findings.length ? 1 : 0;
}
