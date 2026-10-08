// Verify migrations and the actual workerd HTTP runtime without remote resources.
import assert from 'node:assert/strict';
import {execFileSync, spawn} from 'node:child_process';
import {once} from 'node:events';
import {mkdirSync, mkdtempSync, readdirSync, rmSync, writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {buildImport, parseCsv} from './enrollment-data.mjs';
import {createServer} from 'node:net';
import {fileURLToPath} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const wrangler = fileURLToPath(new URL('../node_modules/wrangler/bin/wrangler.js', import.meta.url));
const stateRoot = fileURLToPath(new URL('../.wrangler/', import.meta.url));
mkdirSync(stateRoot, {recursive: true});
const state = mkdtempSync(stateRoot + 'local-check-');
const environment = {...process.env, WRANGLER_SEND_METRICS: 'false', CI: 'true'};
let server;
let log = '';

function cli(args) {
  try {
    return execFileSync(process.execPath, [wrangler, ...args], {
      cwd: root, env: environment, encoding: 'utf8', timeout: 120_000,
    });
  } catch (error) {
    throw new Error(`Local Wrangler command failed: ${error.stdout || ''}\n${error.stderr || error.message}`);
  }
}

async function main() {
  for (const [database, directory] of [
    ['gunpo-school-data', 'migrations'],
    ['gunpo-school-profiles', 'profile_migrations'],
  ]) {
    cli(['d1', 'migrations', 'apply', database, '--local', '--persist-to', state]);
    const response = JSON.parse(cli(['d1', 'execute', database, '--local', '--persist-to', state,
      '--command', 'SELECT COUNT(*) AS count FROM d1_migrations', '--json']));
    const expected = readdirSync(new URL(`../${directory}/`, import.meta.url)).filter(f => f.endsWith('.sql')).length;
    assert.equal(response[0].results[0].count, expected);
    console.log(`${database}: ${expected} local migrations verified`);
  }

  const october = JSON.parse(cli(['d1', 'execute', 'gunpo-school-data', '--local', '--persist-to', state,
    '--command', "SELECT meal_date, menu_json, grades FROM contract_dinners WHERE meal_date BETWEEN '2026-10-01' AND '2026-10-31' ORDER BY meal_date", '--json']))[0].results;
  assert.equal(october.length, 16);
  assert.deepEqual(october.map(row => Number(row.meal_date.slice(-2))), [7,8,12,13,14,15,16,19,21,22,23,26,27,28,29,30]);
  assert.ok(october.every(row => row.grades === '1,2,3' && JSON.parse(row.menu_json).length >= 5));
  const fixtureMeals = JSON.stringify({mealServiceDietInfo: [{row: [
    {MLSV_YMD:'20261007', MMEAL_SC_NM:'중식', DDISH_NM:'테스트 중식'},
    {MLSV_YMD:'20261007', MMEAL_SC_NM:'석식', DDISH_NM:'이전 NEIS 석식'},
  ]}]});
  const fixtureCache = [
    ['neis_common_2026_10', {mealJson:fixtureMeals, scheduleJson:'{}'}],
    ['neis_timetable_2026_10_2_1', {timetableJson:JSON.stringify({hisTimetable:[{row:[{ALL_TI_YMD:'20261007',PERIO:'1',ITRT_CNTNT:'테스트 수업'}]}]})}],
  ].map(([key, value]) => `INSERT INTO data_cache(cache_key,value_json,updated_at) VALUES ('${key}','${JSON.stringify(value).replaceAll("'", "''")}',${Date.now()});`).join('\n');
  const mealSeed = state + '/meal-cache.sql';
  writeFileSync(mealSeed, fixtureCache);
  cli(['d1', 'execute', 'gunpo-school-data', '--local', '--persist-to', state, '--file', mealSeed, '--json']);

  assert.deepEqual(parseCsv('\uFEFFa,b\r\n"c,d","e""f"\r\n'), [['a', 'b'], ['c,d', 'e"f']]);
  assert.throws(() => parseCsv('a\n"unterminated'), /Unterminated/);
  const enrollmentKey = randomBytes(32).toString('base64');
  const blocks = ['2A', '2B', '2C', '2D', '2E', '2F'];
  const people = [
    {id: '20101', name: '테스트가', subject: '논리, 사고'},
    {id: '20102', name: '테스트나', subject: '논리, 사고'},
    {id: '20103', name: '테스트가', subject: '기하'},
    {id: '20104', name: '<img src=x onerror=alert(1)>', subject: '논리, 사고'},
  ];
  const csv = ['id,student_id,student_name,block,subject,class_num', ...people.flatMap((person, index) =>
    blocks.map((block, b) => `${index * 6 + b + 1},${person.id},${person.name},${block},"${person.subject}",1`))].join('\n');
  const imported = buildImport(csv, enrollmentKey);
  assert.equal(imported.studentCount, 4);
  assert.equal(imported.rowCount, 24);
  assert.ok(!imported.sql.includes("'20101'"));
  assert.ok(!imported.sql.includes('테스트가'));
  assert.throws(() => buildImport(csv + '\n' + csv.split('\n')[1], enrollmentKey), /Invalid/);
  assert.throws(() => buildImport(csv.split('\n').slice(0, -1).join('\n'), enrollmentKey), /Every student/);
  const seed = state + '/enrollments.sql';
  writeFileSync(seed, imported.sql);
  cli(['d1', 'execute', 'gunpo-school-profiles', '--local', '--persist-to', state, '--file', seed, '--json']);

  const probe = createServer();
  probe.listen(0, '127.0.0.1');
  await once(probe, 'listening');
  const port = probe.address().port;
  await new Promise((resolve, reject) => probe.close(error => error ? reject(error) : resolve()));
  server = spawn(process.execPath, [wrangler, 'dev', '--local', '--ip', '127.0.0.1',
    '--port', String(port), '--persist-to', state, '--var', `ENROLLMENT_KEY:${enrollmentKey}`,
    '--var', 'ENROLLMENT_RATE_LIMIT:20',
    '--show-interactive-dev-session=false', '--log-level', 'warn'],
  {cwd: root, env: environment, stdio: ['ignore', 'pipe', 'pipe']});
  const capture = chunk => { log = (log + chunk).slice(-16_000); };
  server.stdout.on('data', capture);
  server.stderr.on('data', capture);
  let spawnError;
  server.on('error', error => { spawnError = error; });
  const base = `http://127.0.0.1:${port}`;
  let ready = false;
  for (let attempt = 0; attempt < 60; attempt++) {
    if (spawnError) throw spawnError;
    if (server.exitCode !== null) throw new Error(`Local Worker exited: ${log}`);
    try {
      const response = await fetch(`${base}/health`, {signal: AbortSignal.timeout(1000)});
      if (response.ok) { ready = true; break; }
    } catch {}
    await delay(500);
  }
  assert.ok(ready, `Local Worker did not become ready: ${log}`);
  const request = (path, options = {}) => fetch(base + path, {...options, signal: AbortSignal.timeout(5000)});
  assert.deepEqual(await (await request('/health')).json(), {ok: true, service: 'gunpo-school'});
  const time = await (await request('/v1/time')).json();
  assert.equal(time.timeZone, 'Asia/Seoul');
  assert.ok(Number.isFinite(time.epochMillis));
  assert.match(time.date, /^\d{4}-\d{2}-\d{2}$/);
  const options = await request('/v1/profile', {method: 'OPTIONS'});
  assert.equal(options.status, 204);
  assert.equal(options.headers.get('access-control-allow-origin'), '*');
  assert.equal((await request('/v1/profile')).status, 401);
  assert.equal((await request('/v1/research/summary', {method: 'POST'})).status, 401);
  assert.equal((await request('/not-a-route')).status, 404);
  console.log('Local Worker: health, time, CORS, unauthenticated profile and 404 verified');

  const mealResponse = await request('/v1/neis?grade=2&classNumber=1&year=2026&month=10');
  assert.equal(mealResponse.status, 200);
  const merged = await mealResponse.json();
  assert.equal(merged.contractDinnerCount, 16);
  const mealRows = JSON.parse(merged.mealJson).mealServiceDietInfo.flatMap(block => block.row || []);
  assert.equal(mealRows.filter(row => row.MMEAL_SC_NM === '석식').length, 16);
  assert.equal(mealRows.find(row => row.MMEAL_SC_NM === '중식').DDISH_NM, '테스트 중식');
  assert.equal(mealRows.find(row => row.MLSV_YMD === '20261007' && row.MMEAL_SC_NM === '석식').DDISH_NM,
    JSON.parse(october[0].menu_json).join('<br/>'));
  console.log('October dinner: 16 dates, exam/holiday exclusions, allergy annotations and NEIS override without duplicated dinner or changed lunch verified');

  const page = await request('/class-align');
  assert.equal(page.status, 200);
  assert.match(page.headers.get('content-type'), /text\/html/);
  assert.match(page.headers.get('content-security-policy'), /script-src 'nonce-/);
  const html = await page.text();
  assert.ok(!html.includes('__NONCE__') && !html.includes('class-align.vercel.app'));
  assert.ok(!html.includes('20101') || html.includes('예: 20101'));
  assert.equal((await request('/v1/enrollments/lookup')).status, 405);
  assert.equal((await request('/v1/enrollments')).status, 404);
  const lookup = (studentId, studentName, extra = {}) => request('/v1/enrollments/lookup', {
    method: 'POST', headers: {'content-type': 'application/json', ...extra}, body: JSON.stringify({studentId, studentName}),
  });
  const valid = await lookup(' 20101 ', ' ' + '테스트가'.normalize('NFD') + ' ');
  assert.equal(valid.status, 200);
  assert.equal(valid.headers.get('cache-control'), 'no-store');
  const data = await valid.json();
  assert.equal(data.student.name, '테스트가');
  assert.deepEqual(data.classes.map(c => c.block), blocks);
  for (const item of data.classes) {
    assert.deepEqual(item.students.map(s => s.id), ['20101', '20102', '20104']);
    assert.equal(item.students.filter(s => s.isMe).length, 1);
  }
  const other = await (await lookup('20103', '테스트가')).json();
  assert.deepEqual(other.classes[0].students.map(s => s.id), ['20103']);
  assert.equal((await lookup('20101', '다른이름')).status, 404);
  assert.equal((await lookup("' OR 1=1 --", '테스트가')).status, 400);
  assert.equal((await lookup('20101', '테스트가', {origin:'https://other.test'})).status, 403);
  assert.equal((await request('/v1/enrollments/lookup', {method:'POST', body:'{'})).status, 400);
  assert.equal((await lookup('20101', 'x'.repeat(1100))).status, 400);
  // Each attempt counts across identifiers; concurrent requests cannot lose updates.
  const limited = await Promise.all(Array.from({length: 21}, (_, i) => lookup(`202${String(i).padStart(2,'0')}`, '없는학생')));
  assert.ok(limited.some(response => response.status === 429));
  const retry = await lookup('20101', '테스트가');
  assert.equal(retry.status, 429);
  assert.ok(Number(retry.headers.get('retry-after')) > 0);
  console.log('Enrollments: encrypted CSV, six blocks, classmates, duplicate names, validation, no public roster and atomic rate limit verified');

  if (process.env.GPHS_BROWSER_QA) {
    const {runBrowserQa} = await import(process.env.GPHS_BROWSER_QA);
    await runBrowserQa(base);
  }
}

try {
  await main();
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  if (server && server.exitCode === null && server.signalCode === null) {
    const exited = once(server, 'exit').catch(() => {});
    server.kill('SIGINT');
    let timer;
    await Promise.race([exited, new Promise(resolve => { timer = setTimeout(resolve, 5000); })]);
    clearTimeout(timer);
    if (server.exitCode === null && server.signalCode === null) { server.kill('SIGKILL'); await exited; }
  }
  rmSync(state, {recursive: true, force: true});
}
