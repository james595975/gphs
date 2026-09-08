// Run with Node 22+: node scripts/test-social-auth.cjs. No live accounts or APIs.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const {DatabaseSync} = require('node:sqlite');
const {generateKeyPairSync, createHash, randomBytes} = require('node:crypto');
const ts = require('typescript');
const jose = require('jose');

async function main() {
  const db = new DatabaseSync(':memory:');
  db.exec(fs.readFileSync(new URL('../profile_migrations/0003_student_accounts.sql', `file://${__filename}`), 'utf8'));
  const d1 = {prepare(sql) {
    const stmt = db.prepare(sql);
    return {bind(...args) {
      const params = Object.fromEntries(args.map((value, i) => [String(i + 1), value]));
      return {first: async () => stmt.get(params) ?? null, run: async () => stmt.run(params), all: async () => ({results: stmt.all(params)})};
    }};
  }, batch: async statements => {
    db.exec('BEGIN');
    try { const result = []; for (const stmt of statements) result.push(await stmt.run()); db.exec('COMMIT'); return result; }
    catch (error) { db.exec('ROLLBACK'); throw error; }
  }};
  const keys = generateKeyPairSync('rsa', {modulusLength: 2048});
  const env = {
    PROFILE_DB: d1, PROFILE_HASH_KEY: 'test-only-hash',
    PROFILE_ENCRYPTION_KEY: randomBytes(32).toString('base64'),
    NAVER_CLIENT_ID: 'naver-client', NAVER_CLIENT_SECRET: 'naver-secret',
    KAKAO_REST_API_KEY: 'kakao-client', KAKAO_CLIENT_SECRET: 'kakao-secret',
    FIREBASE_SERVICE_ACCOUNT_EMAIL: 'test@example.test',
    FIREBASE_SERVICE_ACCOUNT_PRIVATE_KEY: keys.privateKey.export({type: 'pkcs8', format: 'pem'}),
  };
  let profileId = 12345;
  const source = fs.readFileSync(new URL('../src/index.ts', `file://${__filename}`), 'utf8') +
    '\nexports.test = {startSocialAuthentication, completeSocialAuthentication, exchangeSocialLoginCode, removeSocialLinks, hmac, encryptSecretText};';
  const sandbox = {
    exports: {}, require, console, crypto: globalThis.crypto,
    Request, Response, URL, URLSearchParams, AbortSignal, TextEncoder, TextDecoder, atob, btoa,
    fetch: async (url, init) => {
      if (String(url).endsWith('/token')) {
        assert.equal(init.method, 'POST');
        const provider = String(url).includes('kakao') ? 'kakao' : 'naver';
        assert.equal(init.body.get('client_id'), `${provider}-client`);
        assert.equal(init.body.get('client_secret'), `${provider}-secret`);
        assert.equal(init.body.get('redirect_uri'), `https://school.test/v1/auth/${provider}/callback`);
        return Response.json({access_token: 'test-access'});
      }
      assert.equal(init.headers.authorization, 'Bearer test-access');
      return Response.json(String(url).includes('kakao') ? {id: profileId} : {resultcode: '00', response: {id: String(profileId)}});
    },
  };
  vm.runInNewContext(ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}}).outputText, sandbox);
  const api = sandbox.exports.test;
  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const post = (path, body) => new Request(`https://school.test/v1/auth/${path}`, {method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify(body)});
  async function start(provider, uid) {
    const res = await api.startSocialAuthentication(post(`${provider}/start`, {codeChallenge: challenge}), env, provider);
    const url = new URL((await res.json()).authorizeUrl);
    assert.equal(url.hostname, provider === 'kakao' ? 'kauth.kakao.com' : 'nid.naver.com');
    const state = url.searchParams.get('state');
    if (uid) {
      const encrypted = await api.encryptSecretText(env, uid);
      db.prepare('UPDATE naver_auth_sessions SET firebase_uid_ciphertext = ?, firebase_uid_iv = ? WHERE state_hash = ?')
        .run(encrypted.ciphertext, encrypted.iv, await api.hmac(env, `${provider}-state:${state}`));
    }
    return new Request(`https://school.test/v1/auth/${provider}/callback?code=test&state=${state}`);
  }
  async function complete(provider, request, expectedMode = 'login') {
    const res = await api.completeSocialAuthentication(request, env, provider);
    assert.equal(res.status, 302);
    const url = new URL(res.headers.get('location'));
    assert.equal(url.pathname, `/${provider}`);
    assert.equal(url.searchParams.get('mode'), expectedMode);
    return url.searchParams.get('code');
  }
  async function exchange(provider, code, value = verifier) {
    return api.exchangeSocialLoginCode(post(`${provider}/exchange`, {code, codeVerifier: value}), env, provider);
  }
  for (const provider of ['kakao', 'naver']) {
    const callback = await start(provider, 'student-one');
    const code = await complete(provider, callback, 'link');
    await assert.rejects(() => complete(provider, callback), /만료/);
    const res = await exchange(provider, code);
    const {payload} = await jose.jwtVerify((await res.json()).customToken, keys.publicKey, {issuer: env.FIREBASE_SERVICE_ACCOUNT_EMAIL});
    assert.equal(payload.uid, 'student-one');
    await assert.rejects(() => exchange(provider, code), /만료/);
    const again = await exchange(provider, await complete(provider, await start(provider)));
    assert.equal(jose.decodeJwt((await again.json()).customToken).uid, 'student-one');
    const collision = await start(provider, 'student-two');
    await assert.rejects(() => complete(provider, collision, 'link'), /다른 계정/);
    const wrongVerifier = await complete(provider, await start(provider));
    await assert.rejects(() => exchange(provider, wrongVerifier, randomBytes(32).toString('base64url')), /보안 검증/);
    const crossProvider = await start(provider);
    await assert.rejects(() => complete(provider === 'kakao' ? 'naver' : 'kakao', crossProvider), /만료/);
    db.exec('UPDATE naver_auth_sessions SET expires_at = 0');
    await assert.rejects(() => complete(provider, crossProvider), /만료/);
    console.log(`${provider}: login, linking, repeat login, collision, replay, PKCE, provider isolation and expiry passed`);
  }
  profileId = undefined;
  const invalidProfile = await start('kakao');
  await assert.rejects(() => complete('kakao', invalidProfile), /사용자 정보/);
  const studentHash = await api.hmac(env, 'firebase:student-one');
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM firebase_social_links WHERE provider_subject_hash = ?').get(studentHash).n, 2);
  await api.removeSocialLinks(env, 'student-two', 'kakao');
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM social_identity_links WHERE provider = 'kakao'").get().n, 1);
  await api.removeSocialLinks(env, 'student-one', 'kakao');
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM social_identity_links WHERE provider = 'kakao'").get().n, 0);
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM firebase_social_links WHERE provider = 'kakao'").get().n, 0);
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM social_identity_links WHERE provider = 'naver'").get().n, 1);
  await api.removeSocialLinks(env, 'student-one', 'kakao');
  console.log('unlink: ownership, provider isolation, mapping removal and idempotency passed');
  profileId = 67890;
  // Reset test-only rate limits before checking a new user's unlink lifecycle.
  db.exec('DELETE FROM auth_rate_limits');
  const firstLogin = await exchange('kakao', await complete('kakao', await start('kakao')));
  const firstUid = jose.decodeJwt((await firstLogin.json()).customToken).uid;
  await api.removeSocialLinks(env, firstUid, 'kakao');
  const nextLogin = await exchange('kakao', await complete('kakao', await start('kakao')));
  assert.notEqual(jose.decodeJwt((await nextLogin.json()).customToken).uid, firstUid);
  console.log('unlink: subsequent social login cannot restore the disconnected Firebase account');
  db.close();
}
main().catch(error => { console.error(error); process.exitCode = 1; });
