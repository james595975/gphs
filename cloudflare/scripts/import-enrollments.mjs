import {execFileSync} from 'node:child_process';
import {createHash, randomBytes} from 'node:crypto';
import {existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {buildImport} from './enrollment-data.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const wrangler = fileURLToPath(new URL('../node_modules/wrangler/bin/wrangler.js', import.meta.url));
const directory = root + '.enrollments/';
const args = process.argv.slice(2);
const csvIndex = args.indexOf('--csv');
const modes = ['--prepare', '--local', '--remote'].filter(mode => args.includes(mode));
if (csvIndex < 0 || !args[csvIndex + 1] || modes.length !== 1) {
  console.error('Usage: pnpm run enrollments:import --csv /absolute/enrollments_rows.csv --prepare|--local|--remote');
  process.exit(1);
}
mkdirSync(directory, {recursive: true, mode: 0o700});
const keyFile = directory + 'key';
const existingKey = existsSync(keyFile);
const secret = existingKey ? readFileSync(keyFile, 'utf8').trim() : randomBytes(32).toString('base64');
const keyFingerprint = createHash('sha256').update(secret).digest('hex');
const data = buildImport(readFileSync(args[csvIndex + 1], 'utf8'), secret);
writeFileSync(keyFile, secret + '\n', {mode: 0o600});
const sqlFile = directory + 'import.sql';
writeFileSync(sqlFile, data.sql, {mode: 0o600});
console.log(`Validated ${data.studentCount} students, ${data.rowCount} enrollments; blocks: ${data.blocks.join(', ')}`);

function cli(args, input) {
  return execFileSync(process.execPath, [wrangler, ...args], {
    cwd: root, env: {...process.env, WRANGLER_SEND_METRICS: 'false', CI: 'true'},
    input, encoding: 'utf8', timeout: 180_000, maxBuffer: 8 * 1024 * 1024,
  });
}
if (modes[0] !== '--prepare') {
  const location = modes[0];
  if (location === '--remote') {
    // Never silently create a different key for an already running dataset.
    const remote = JSON.parse(cli(['d1', 'execute', 'gunpo-school-profiles', '--remote', '--command',
      'SELECT COUNT(*) AS count FROM enrollment_students', '--json']));
    const marker = directory + 'remote-key-registered';
    if (remote[0].results[0].count > 0 && (!existingKey || !existsSync(marker) || readFileSync(marker, 'utf8').trim() !== keyFingerprint)) {
      throw new Error('A remote dataset already exists. Restore its original .enrollments/key before importing.');
    }
    cli(['secret', 'put', 'ENROLLMENT_KEY'], secret + '\n');
    writeFileSync(marker, keyFingerprint + '\n', {mode: 0o600});
  } else {
    const variables = root + '.dev.vars';
    let content = existsSync(variables) ? readFileSync(variables, 'utf8') : '';
    content = content.replace(/^ENROLLMENT_KEY=.*\r?\n?/gm, '');
    writeFileSync(variables, content.trimEnd() + `\nENROLLMENT_KEY=${secret}\n`, {mode: 0o600});
  }
  cli(['d1', 'execute', 'gunpo-school-profiles', location, '--file', sqlFile, '--json']);
  const result = JSON.parse(cli(['d1', 'execute', 'gunpo-school-profiles', location,
    '--command', `SELECT d.source_sha256,
      (SELECT COUNT(*) FROM enrollment_students WHERE dataset_id=d.source_sha256) AS student_count,
      (SELECT COUNT(*) FROM enrollments WHERE dataset_id=d.source_sha256) AS enrollment_count
      FROM enrollment_dataset d WHERE id=1`, '--json']));
  if (result[0].results[0].source_sha256 !== data.dataset || result[0].results[0].student_count !== data.studentCount || result[0].results[0].enrollment_count !== data.rowCount) {
    throw new Error('Imported dataset counts did not match');
  }
  console.log(`Encrypted dataset imported ${location.slice(2)}. No plaintext student identifiers were uploaded.`);
} else console.log('Encrypted SQL and key prepared in ignored .enrollments/. Nothing uploaded.');
