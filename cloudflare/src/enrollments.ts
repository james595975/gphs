import page from "./class-align.html";

export interface EnrollmentEnv {
  PROFILE_DB: D1Database;
  ENROLLMENT_KEY: string;
  ENROLLMENT_RATE_LIMIT?: string;
}

type Identity = {id: string; name: string};
type Assignment = {block: string; subject: string; class_num: number};
type Peer = Assignment & {lookup_hash: string; identity_ciphertext: string; identity_iv: string};

function reply(data: unknown, status = 200): Response {
  return Response.json(data, {status, headers: {
    "cache-control": "no-store", "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer",
  }});
}

function decode(value: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(atob(value), c => c.charCodeAt(0));
}

async function derivedKey(secret: string, purpose: string): Promise<ArrayBuffer> {
  return crypto.subtle.digest("SHA-256", new TextEncoder().encode(`enrollments:${purpose}:${secret}`));
}

async function hash(secret: string, value: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", await derivedKey(secret, "lookup"),
    {name: "HMAC", hash: "SHA-256"}, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value));
  return Array.from(new Uint8Array(signature), b => b.toString(16).padStart(2, "0")).join("");
}

export function classAlignPage(): Response {
  const nonce = crypto.randomUUID();
  return new Response(page.replaceAll("__NONCE__", nonce), {headers: {
    "content-type": "text/html; charset=utf-8",
    "cache-control": "no-store",
    "content-security-policy": `default-src 'none'; script-src 'nonce-${nonce}'; style-src 'nonce-${nonce}'; connect-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`,
    "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer",
    "permissions-policy": "camera=(), microphone=(), geolocation=()",
  }});
}

async function readPayload(request: Request): Promise<unknown> {
  const reader = request.body?.getReader();
  if (!reader) return null;
  const chunks: Uint8Array[] = [];
  let length = 0;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      length += value.byteLength;
      if (length > 1024) { await reader.cancel(); return null; }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  try { return JSON.parse(new TextDecoder().decode(bytes)); } catch { return null; }
}

export async function lookupEnrollments(request: Request, env: EnrollmentEnv): Promise<Response> {
  if (request.method !== "POST") {
    const response = reply({error: "학번과 이름을 입력해 조회해 주세요."}, 405);
    response.headers.set("allow", "POST");
    return response;
  }
  const origin = request.headers.get("origin");
  if (origin && origin !== new URL(request.url).origin) return reply({error: "이 사이트에서 조회해 주세요."}, 403);
  if (!env.ENROLLMENT_KEY) return reply({error: "분반 조회를 준비하고 있습니다. 잠시 후 다시 이용해 주세요."}, 503);

  // Atomic per-IP counter: changing names/numbers does not bypass the limit.
  const rateKey = await hash(env.ENROLLMENT_KEY, `rate:${request.headers.get("cf-connecting-ip") || "local"}`);
  const windowStart = Math.floor(Date.now() / 900_000) * 900_000;
  const limit = await env.PROFILE_DB.prepare(
    `INSERT INTO enrollment_lookup_limits(rate_key, window_started_at, request_count) VALUES (?1, ?2, 1)
     ON CONFLICT(rate_key) DO UPDATE SET
       request_count = CASE WHEN window_started_at = excluded.window_started_at THEN request_count + 1 ELSE 1 END,
       window_started_at = excluded.window_started_at RETURNING request_count`,
  ).bind(rateKey, windowStart).first<{request_count: number}>();
  // Leave room for a school Wi-Fi network sharing one IP address.
  const maximum = Number(env.ENROLLMENT_RATE_LIMIT || "300");
  if (!limit || limit.request_count > (Number.isInteger(maximum) && maximum > 0 ? maximum : 300)) {
    const response = reply({error: "조회 횟수를 초과했습니다. 15분 후 다시 시도해 주세요."}, 429);
    response.headers.set("retry-after", String(Math.ceil((windowStart + 900_000 - Date.now()) / 1000)));
    return response;
  }

  const payload = await readPayload(request);
  const body = payload && typeof payload === "object" && !Array.isArray(payload)
    ? payload as Record<string, unknown> : {};
  const id = typeof body.studentId === "string" ? body.studentId.trim() : "";
  const name = typeof body.studentName === "string" ? body.studentName.trim().normalize("NFC") : "";
  if (!/^\d{5}$/.test(id) || !name || name.length > 40 || /[\u0000-\u001f\u007f]/.test(name)) {
    return reply({error: "5자리 학번과 이름을 정확히 입력해 주세요."}, 400);
  }
  const lookupHash = await hash(env.ENROLLMENT_KEY, `${id}\0${name}`);
  // Join only the requesting student's classes; there is no public roster/list endpoint.
  const peers = await env.PROFILE_DB.prepare(
    `SELECT e.block, e.subject, e.class_num, s.lookup_hash, s.identity_ciphertext, s.identity_iv
     FROM enrollments mine JOIN enrollments e
       ON e.dataset_id = mine.dataset_id AND e.block = mine.block
         AND e.subject = mine.subject AND e.class_num = mine.class_num
     JOIN enrollment_students s ON s.dataset_id = e.dataset_id AND s.lookup_hash = e.lookup_hash
     WHERE mine.dataset_id = (SELECT source_sha256 FROM enrollment_dataset WHERE id = 1)
       AND mine.lookup_hash = ?1 ORDER BY e.block, e.lookup_hash`,
  ).bind(lookupHash).all<Peer>();
  if (!peers.results.length) return reply({error: "일치하는 분반 정보가 없습니다. 학번과 이름을 확인해 주세요."}, 404);
  const own = peers.results.filter(row => row.lookup_hash === lookupHash);
  const key = await crypto.subtle.importKey("raw", await derivedKey(env.ENROLLMENT_KEY, "identity"),
    "AES-GCM", false, ["decrypt"]);
  const identities = new Map<string, Identity>();
  for (const row of peers.results) {
    if (!identities.has(row.lookup_hash)) {
      const plaintext = await crypto.subtle.decrypt({name: "AES-GCM", iv: decode(row.identity_iv)},
        key, decode(row.identity_ciphertext));
      identities.set(row.lookup_hash, JSON.parse(new TextDecoder().decode(plaintext)) as Identity);
    }
  }
  const classes = own.map(assignment => ({
    block: assignment.block, subject: assignment.subject, classNumber: assignment.class_num,
    students: peers.results.filter(row => row.block === assignment.block)
      .map(row => ({...identities.get(row.lookup_hash)!, isMe: row.lookup_hash === lookupHash}))
      .sort((a, b) => a.id.localeCompare(b.id)),
  }));
  return reply({student: {id, name}, classes});
}
