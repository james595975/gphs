import * as cheerio from "cheerio";
import {decodeProtectedHeader, importPKCS8, importX509, jwtVerify, SignJWT, type JWTPayload} from "jose";

interface Env {
  DB: D1Database;
  PROFILE_DB: D1Database;
  NEIS_API_KEY: string;
  FIREBASE_PROJECT_ID: string;
  PROFILE_ENCRYPTION_KEY: string;
  PROFILE_HASH_KEY: string;
  PROFILE_CONSENT_VERSION: string;
  ACCOUNT_CONSENT_VERSION: string;
  FIREBASE_WEB_API_KEY: string;
  FIREBASE_SERVICE_ACCOUNT_EMAIL: string;
  FIREBASE_SERVICE_ACCOUNT_PRIVATE_KEY: string;
  KAKAO_REST_API_KEY: string;
  KAKAO_CLIENT_SECRET: string;
  NAVER_CLIENT_ID: string;
  NAVER_CLIENT_SECRET: string;
}

type Notice = {
  category: string;
  section: string;
  title: string;
  dateLabel: string;
  url: string;
  isNew: boolean;
};

type CacheEntry<T> = {
  value: T;
  fingerprint: string;
  updatedAt: number;
};

type ContractDinner = {
  meal_date: string;
  menu_json: string;
  grades: string;
  meal_type: string;
};

type TemporaryTimetableRow = {
  academic_year: number;
  semester: number;
  day_of_week: number;
  period: number;
  subject: string;
  effective_from: string;
  effective_to: string;
  source_name: string;
};

type TimetableDateOverrideRow = {
  lesson_date: string;
  period: number;
  subject: string;
  source_name: string;
};

const OFFICE_CODE = "J10";
const SCHOOL_CODE = "7530148";
const NEIS_BASE_URL = "https://open.neis.go.kr/hub";
const CACHE_MS = 30 * 60 * 1000;
const NOTICE_SYNC_COOLDOWN_MS = 60 * 1000;
const HOURLY_SYNC_CRON = "0 * * * *";
const DAILY_TIMETABLE_SYNC_CRON = "15 15 * * *"; // 매일 00:15 Asia/Seoul
const FIREBASE_CERTS_URL =
  "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
const PROFILE_RETENTION_MS = 365 * 24 * 60 * 60 * 1000;

class HttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

type FirebaseIdentity = {
  subject: string;
  phoneNumber?: string;
  email?: string;
  emailVerified: boolean;
  authenticationTime: number;
  signInProvider?: string;
};

type StudentProfilePayload = {
  phoneNumber: string;
  name: string;
  studentNumber: string;
  grade: number;
  classNumber: number;
  seatNumber: number;
};

type StudentAccountPayload = {
  loginId: string;
  email: string;
};

let firebaseCertCache: {certificates: Record<string, string>; expiresAt: number} | null = null;

const noticeSources = [
  {
    section: "가정통신문",
    category: "가정",
    url: "https://www.gunpo.hs.kr/main.php?menugrp=060300&master=bbs&act=list&master_sid=65",
  },
  {
    section: "공지사항",
    category: "공지",
    url: "https://www.gunpo.hs.kr/main.php?menugrp=060100&master=bbs&act=list&master_sid=63",
  },
];

function json(data: unknown, status = 200): Response {
  const headers = {
    "cache-control": "no-store",
    "access-control-allow-origin": "*",
    "access-control-allow-headers": "authorization,content-type",
    "access-control-allow-methods": "GET,POST,PUT,DELETE,OPTIONS",
  };
  if (status === 204) return new Response(null, {status, headers});
  return Response.json(data, {
    status,
    headers,
  });
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((value) => { binary += String.fromCharCode(value); });
  return btoa(binary);
}

function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

async function hmac(env: Env, value: string): Promise<string> {
  if (!env.PROFILE_HASH_KEY) throw new Error("PROFILE_HASH_KEY secret is missing");
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(env.PROFILE_HASH_KEY),
    {name: "HMAC", hash: "SHA-256"},
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value));
  return bytesToBase64(new Uint8Array(signature));
}

async function profileEncryptionKey(env: Env): Promise<CryptoKey> {
  if (!env.PROFILE_ENCRYPTION_KEY) throw new Error("PROFILE_ENCRYPTION_KEY secret is missing");
  const bytes = base64ToBytes(env.PROFILE_ENCRYPTION_KEY);
  if (bytes.byteLength !== 32) throw new Error("PROFILE_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
  return crypto.subtle.importKey("raw", bytes, {name: "AES-GCM"}, false, ["encrypt", "decrypt"]);
}

async function encryptProfile(env: Env, profile: StudentProfilePayload): Promise<{ciphertext: string; iv: string}> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    {name: "AES-GCM", iv},
    await profileEncryptionKey(env),
    new TextEncoder().encode(JSON.stringify(profile)),
  );
  return {ciphertext: bytesToBase64(new Uint8Array(ciphertext)), iv: bytesToBase64(iv)};
}

async function decryptProfile(env: Env, ciphertext: string, iv: string): Promise<StudentProfilePayload> {
  const plaintext = await crypto.subtle.decrypt(
    {name: "AES-GCM", iv: base64ToBytes(iv)},
    await profileEncryptionKey(env),
    base64ToBytes(ciphertext),
  );
  return JSON.parse(new TextDecoder().decode(plaintext)) as StudentProfilePayload;
}

async function encryptAccount(env: Env, account: StudentAccountPayload): Promise<{ciphertext: string; iv: string}> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    {name: "AES-GCM", iv},
    await profileEncryptionKey(env),
    new TextEncoder().encode(JSON.stringify(account)),
  );
  return {ciphertext: bytesToBase64(new Uint8Array(ciphertext)), iv: bytesToBase64(iv)};
}

async function decryptAccount(env: Env, ciphertext: string, iv: string): Promise<StudentAccountPayload> {
  const plaintext = await crypto.subtle.decrypt(
    {name: "AES-GCM", iv: base64ToBytes(iv)},
    await profileEncryptionKey(env),
    base64ToBytes(ciphertext),
  );
  return JSON.parse(new TextDecoder().decode(plaintext)) as StudentAccountPayload;
}

async function encryptSecretText(env: Env, value: string): Promise<{ciphertext: string; iv: string}> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    {name: "AES-GCM", iv},
    await profileEncryptionKey(env),
    new TextEncoder().encode(value),
  );
  return {ciphertext: bytesToBase64(new Uint8Array(ciphertext)), iv: bytesToBase64(iv)};
}

async function decryptSecretText(env: Env, ciphertext: string, iv: string): Promise<string> {
  const plaintext = await crypto.subtle.decrypt(
    {name: "AES-GCM", iv: base64ToBytes(iv)},
    await profileEncryptionKey(env),
    base64ToBytes(ciphertext),
  );
  return new TextDecoder().decode(plaintext);
}

function randomUrlToken(byteLength = 32): string {
  const bytes = crypto.getRandomValues(new Uint8Array(byteLength));
  return bytesToBase64(bytes).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function bytesToHex(bytes: Uint8Array): string {
  return [...bytes].map((value) => value.toString(16).padStart(2, "0")).join("");
}

async function firebaseCertificates(): Promise<Record<string, string>> {
  if (firebaseCertCache && firebaseCertCache.expiresAt > Date.now()) return firebaseCertCache.certificates;
  const response = await fetch(FIREBASE_CERTS_URL, {signal: AbortSignal.timeout(10_000)});
  if (!response.ok) throw new Error(`Firebase certificate HTTP ${response.status}`);
  const certificates = await response.json<Record<string, string>>();
  const maxAge = Number(response.headers.get("cache-control")?.match(/max-age=(\d+)/)?.[1] || 300);
  firebaseCertCache = {certificates, expiresAt: Date.now() + Math.max(maxAge - 30, 30) * 1000};
  return certificates;
}

function firebaseClaim(payload: JWTPayload): {sign_in_provider?: unknown} {
  return typeof payload.firebase === "object" && payload.firebase !== null
    ? payload.firebase as {sign_in_provider?: unknown}
    : {};
}

async function requireFirebaseIdentity(
  request: Request,
  env: Env,
  maximumAuthenticationAgeSeconds?: number,
): Promise<FirebaseIdentity> {
  const authorization = request.headers.get("authorization") || "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) throw new HttpError(401, "로그인이 필요합니다.");
  if (!env.FIREBASE_PROJECT_ID) throw new Error("FIREBASE_PROJECT_ID is missing");

  const token = match[1];
  let header: ReturnType<typeof decodeProtectedHeader>;
  try {
    header = decodeProtectedHeader(token);
  } catch {
    throw new HttpError(401, "유효하지 않은 인증 토큰입니다.");
  }
  if (header.alg !== "RS256" || !header.kid) throw new HttpError(401, "유효하지 않은 인증 토큰입니다.");
  const certificate = (await firebaseCertificates())[header.kid];
  if (!certificate) throw new HttpError(401, "만료되었거나 알 수 없는 인증 토큰입니다.");

  let payload: JWTPayload;
  try {
    payload = (await jwtVerify(token, await importX509(certificate, "RS256"), {
      algorithms: ["RS256"],
      audience: env.FIREBASE_PROJECT_ID,
      issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
      clockTolerance: 30,
    })).payload;
  } catch {
    throw new HttpError(401, "로그인이 만료되었습니다. 다시 로그인해 주세요.");
  }

  const now = Math.floor(Date.now() / 1000);
  const phoneNumber = typeof payload.phone_number === "string" ? payload.phone_number : "";
  if (!payload.sub || payload.sub.length > 128 || !payload.iat || payload.iat > now + 30 ||
      typeof payload.auth_time !== "number" || payload.auth_time > now + 30) {
    throw new HttpError(401, "유효하지 않은 로그인 정보입니다.");
  }
  if (maximumAuthenticationAgeSeconds !== undefined && now - payload.auth_time > maximumAuthenticationAgeSeconds) {
    throw new HttpError(401, "보안을 위해 다시 로그인해 주세요.");
  }
  return {
    subject: payload.sub,
    phoneNumber: /^\+[1-9]\d{7,14}$/.test(phoneNumber) ? phoneNumber : undefined,
    email: typeof payload.email === "string" ? payload.email.trim().toLowerCase() : undefined,
    emailVerified: payload.email_verified === true,
    authenticationTime: payload.auth_time,
    signInProvider: typeof firebaseClaim(payload).sign_in_provider === "string"
      ? firebaseClaim(payload).sign_in_provider as string
      : undefined,
  };
}

function parseStudentProfile(body: unknown): Omit<StudentProfilePayload, "phoneNumber"> & {
  consent: boolean;
  consentVersion: string;
} {
  if (typeof body !== "object" || body === null) throw new HttpError(400, "요청 형식이 올바르지 않습니다.");
  const value = body as Record<string, unknown>;
  const name = typeof value.name === "string" ? value.name.trim() : "";
  const studentNumber = typeof value.studentNumber === "string" ? value.studentNumber.trim() : "";
  if (!/^[\p{L}\p{M} .'-]{1,20}$/u.test(name)) {
    throw new HttpError(400, "이름은 문자 기준 1~20자로 입력해 주세요.");
  }
  if (!/^\d{5}$/.test(studentNumber)) throw new HttpError(400, "학번 5자리를 확인해 주세요.");
  const grade = Number(studentNumber.slice(0, 1));
  const classNumber = Number(studentNumber.slice(1, 3));
  const seatNumber = Number(studentNumber.slice(3, 5));
  if (grade < 1 || grade > 3 || classNumber < 1 || classNumber > 20 || seatNumber < 1 || seatNumber > 99) {
    throw new HttpError(400, "학번 5자리를 확인해 주세요.");
  }
  return {
    name,
    studentNumber,
    grade,
    classNumber,
    seatNumber,
    consent: value.consent === true,
    consentVersion: typeof value.consentVersion === "string" ? value.consentVersion : "",
  };
}

async function getCache<T>(env: Env, key: string): Promise<CacheEntry<T> | null> {
  const row = await env.DB.prepare(
    "SELECT value_json, fingerprint, updated_at FROM data_cache WHERE cache_key = ?1",
  ).bind(key).first<{value_json: string; fingerprint: string | null; updated_at: number}>();
  if (!row) return null;
  return {
    value: JSON.parse(row.value_json) as T,
    fingerprint: row.fingerprint || "",
    updatedAt: row.updated_at,
  };
}

async function putCache(env: Env, key: string, value: unknown, fingerprint = ""): Promise<number> {
  const updatedAt = Date.now();
  await env.DB.prepare(
    `INSERT INTO data_cache(cache_key, value_json, fingerprint, updated_at)
     VALUES (?1, ?2, ?3, ?4)
     ON CONFLICT(cache_key) DO UPDATE SET
       value_json = excluded.value_json,
       fingerprint = excluded.fingerprint,
       updated_at = excluded.updated_at`,
  ).bind(key, JSON.stringify(value), fingerprint, updatedAt).run();
  return updatedAt;
}

function seoulDateParts(date = new Date()): {year: number; month: number; day: number} {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "numeric",
    day: "numeric",
  }).formatToParts(date);
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    Number(parts.find((part) => part.type === type)?.value);
  return {year: value("year"), month: value("month"), day: value("day")};
}

function isNewDate(dateLabel: string): boolean {
  const today = seoulDateParts();
  const current = Date.UTC(today.year, today.month - 1, today.day);
  const [year, month, day] = dateLabel.split(".").map(Number);
  const target = Date.UTC(year, month - 1, day);
  return current - target <= 7 * 24 * 60 * 60 * 1000;
}

async function scrapeNotices(): Promise<Notice[]> {
  const groups = await Promise.all(noticeSources.map(async (source) => {
    const response = await fetch(source.url, {
      headers: {"user-agent": "GunpoSchoolDataWorker/1.0"},
      signal: AbortSignal.timeout(15_000),
    });
    if (!response.ok) throw new Error(`School website HTTP ${response.status}`);
    const $ = cheerio.load(await response.text());
    const notices: Notice[] = [];
    $("tr").each((_, row) => {
      const anchor = $(row).find("a[href*='act=view']").first();
      const match = $(row).text().match(/\b(20\d{2})[-.](\d{2})[-.](\d{2})\b/);
      const href = anchor.attr("href");
      const title = (anchor.attr("title") || anchor.text()).trim();
      if (!match || !href || !title) return;
      const dateLabel = `${match[1]}.${match[2]}.${match[3]}`;
      notices.push({
        category: source.category,
        section: source.section,
        title,
        dateLabel,
        url: new URL(href, source.url).toString(),
        isNew: isNewDate(dateLabel),
      });
    });
    return notices;
  }));
  const unique = new Map<string, Notice>();
  groups.flat().forEach((notice) => unique.set(notice.url, notice));
  return [...unique.values()].sort((a, b) => b.dateLabel.localeCompare(a.dateLabel));
}

async function noticeFingerprint(items: Notice[]): Promise<string> {
  const canonical = items.map((item) =>
    `${item.section}|${item.title}|${item.dateLabel}|${item.url}`
  ).join("\n");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(canonical));
  return [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, "0")).join("");
}

async function refreshNotices(env: Env): Promise<CacheEntry<Notice[]>> {
  const value = await scrapeNotices();
  if (!value.length) throw new Error("No notices parsed from school website");
  const fingerprint = await noticeFingerprint(value);
  const updatedAt = await putCache(env, "notices", value, fingerprint);
  return {value, fingerprint, updatedAt};
}

function ymd(year: number, month: number, day: number): string {
  return `${year}${String(month).padStart(2, "0")}${String(day).padStart(2, "0")}`;
}

function lastDay(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

async function requestNeis(env: Env, endpoint: string, parameters: Record<string, string>): Promise<string> {
  if (!env.NEIS_API_KEY) throw new Error("NEIS_API_KEY secret is missing");
  const query = new URLSearchParams({
    KEY: env.NEIS_API_KEY,
    Type: "json",
    pIndex: "1",
    pSize: "1000",
    ATPT_OFCDC_SC_CODE: OFFICE_CODE,
    SD_SCHUL_CODE: SCHOOL_CODE,
    ...parameters,
  });
  const response = await fetch(`${NEIS_BASE_URL}/${endpoint}?${query}`, {
    headers: {"user-agent": "GunpoSchoolDataWorker/1.0"},
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`NEIS ${endpoint} HTTP ${response.status}`);
  const body = await response.text();
  const result = (JSON.parse(body) as {RESULT?: {CODE?: string; MESSAGE?: string}}).RESULT;
  if (result?.CODE && !["INFO-000", "INFO-200"].includes(result.CODE)) {
    throw new Error(`NEIS ${endpoint}: ${result.MESSAGE || result.CODE}`);
  }
  return body;
}

async function loadCommonNeis(env: Env, year: number, month: number, force = false) {
  const key = `neis_common_${year}_${month}`;
  const cached = await getCache<{mealJson: string; scheduleJson: string}>(env, key);
  if (!force && cached && Date.now() - cached.updatedAt < CACHE_MS) {
    return {...cached.value, cached: true, stale: false, updatedAt: cached.updatedAt};
  }
  try {
    const [mealJson, scheduleJson] = await Promise.all([
      requestNeis(env, "mealServiceDietInfo", {
        MLSV_FROM_YMD: ymd(year, month, 1),
        MLSV_TO_YMD: ymd(year, month, lastDay(year, month)),
      }),
      requestNeis(env, "SchoolSchedule", {
        AA_FROM_YMD: ymd(year, 1, 1),
        AA_TO_YMD: ymd(year, 12, 31),
      }),
    ]);
    const updatedAt = await putCache(env, key, {mealJson, scheduleJson});
    return {mealJson, scheduleJson, cached: false, stale: false, updatedAt};
  } catch (error) {
    if (!cached) throw error;
    return {...cached.value, cached: true, stale: true, updatedAt: cached.updatedAt};
  }
}

async function mergeContractDinners(
  env: Env,
  mealJson: string,
  year: number,
  month: number,
  grade: number,
): Promise<{mealJson: string; count: number}> {
  const monthPrefix = `${year}-${String(month).padStart(2, "0")}`;
  const nextYear = month === 12 ? year + 1 : year;
  const nextMonth = month === 12 ? 1 : month + 1;
  const nextMonthPrefix = `${nextYear}-${String(nextMonth).padStart(2, "0")}`;
  const result = await env.DB.prepare(
    `SELECT meal_date, menu_json, grades, meal_type
     FROM contract_dinners
     WHERE meal_date >= ?1 AND meal_date < ?2
     ORDER BY meal_date`,
  ).bind(`${monthPrefix}-01`, `${nextMonthPrefix}-01`).all<ContractDinner>();
  const dinners = result.results.filter((dinner) =>
    dinner.grades.split(",").map(Number).includes(grade),
  );
  if (dinners.length === 0) return {mealJson, count: 0};

  const root = JSON.parse(mealJson) as Record<string, unknown>;
  const blocks = Array.isArray(root.mealServiceDietInfo)
    ? root.mealServiceDietInfo as Array<Record<string, unknown>>
    : [];
  const existingRows = blocks.flatMap((block) =>
    Array.isArray(block.row) ? block.row as Array<Record<string, unknown>> : [],
  );
  const contractKeys = new Set(dinners.map((dinner) =>
    `${dinner.meal_date.replaceAll("-", "")}:${dinner.meal_type}`,
  ));
  const rows = existingRows.filter((row) =>
    !contractKeys.has(`${String(row.MLSV_YMD)}:${String(row.MMEAL_SC_NM)}`),
  );
  rows.push(...dinners.map((dinner) => ({
    ATPT_OFCDC_SC_CODE: OFFICE_CODE,
    SD_SCHUL_CODE: SCHOOL_CODE,
    SCHUL_NM: "군포고등학교",
    MMEAL_SC_CODE: dinner.meal_type === "중식" ? "2" : "3",
    MMEAL_SC_NM: dinner.meal_type,
    MLSV_YMD: dinner.meal_date.replaceAll("-", ""),
    DDISH_NM: (JSON.parse(dinner.menu_json) as string[]).join("<br/>"),
    ORPLC_INFO: "위탁급식업체 제공 식단표",
    CAL_INFO: "위탁급식",
    NTR_INFO: "",
    MLSV_FGR: "0.00",
  })));
  rows.sort((a, b) => String(a.MLSV_YMD).localeCompare(String(b.MLSV_YMD)) ||
    String(a.MMEAL_SC_CODE).localeCompare(String(b.MMEAL_SC_CODE)));
  const head = blocks.find((block) => Array.isArray(block.head))?.head || [];
  root.mealServiceDietInfo = [
    {head},
    {row: rows},
  ];
  delete root.RESULT;
  return {mealJson: JSON.stringify(root), count: dinners.length};
}

async function loadTimetable(
  env: Env,
  year: number,
  month: number,
  grade: number,
  classNumber: number,
  force = false,
) {
  const key = `neis_timetable_${year}_${month}_${grade}_${classNumber}`;
  const cached = await getCache<{timetableJson: string}>(env, key);
  if (!force && cached && Date.now() - cached.updatedAt < CACHE_MS) {
    return {...cached.value, cached: true, stale: false, updatedAt: cached.updatedAt};
  }
  try {
    const timetableJson = await requestNeis(env, "hisTimetable", {
      AY: String(year),
      GRADE: String(grade),
      CLASS_NM: String(classNumber),
      TI_FROM_YMD: ymd(year, month, 1),
      TI_TO_YMD: ymd(year, month, lastDay(year, month)),
    });
    const updatedAt = await putCache(env, key, {timetableJson});
    return {timetableJson, cached: false, stale: false, updatedAt};
  } catch (error) {
    if (!cached) throw error;
    return {...cached.value, cached: true, stale: true, updatedAt: cached.updatedAt};
  }
}

function timetableRows(jsonText: string): Array<Record<string, unknown>> {
  const root = JSON.parse(jsonText) as Record<string, unknown>;
  const blocks = Array.isArray(root.hisTimetable)
    ? root.hisTimetable as Array<Record<string, unknown>>
    : [];
  return blocks.flatMap((block) =>
    Array.isArray(block.row) ? block.row as Array<Record<string, unknown>> : [],
  );
}

const SECOND_GRADE_ELECTIVE_BLOCKS: Record<string, string> = {
  "1-3": "2A", "1-4": "2C", "1-6": "논리와 사고",
  "2-1": "논리와 사고", "2-2": "2B", "2-3": "인간과 경제활동",
  "2-5": "2C", "2-6": "2D",
  "3-2": "2A", "3-3": "2B",
  "4-1": "2D", "4-4": "논리와 사고", "4-5": "2A", "4-6": "2B",
  "5-3": "2C", "5-4": "2D", "5-5": "인간과 경제활동",
};

function preserveElectiveBlockLabels(timetableJson: string): string {
  const root = JSON.parse(timetableJson) as Record<string, unknown>;
  const blocks = Array.isArray(root.hisTimetable)
    ? root.hisTimetable as Array<Record<string, unknown>>
    : [];
  const rows = blocks.flatMap((block) =>
    Array.isArray(block.row) ? block.row as Array<Record<string, unknown>> : [],
  );
  rows.forEach((row) => {
    const dateText = String(row.ALL_TI_YMD);
    if (String(row.GRADE) !== "2" || dateText < "20260813" || dateText > "20270228") return;
    const date = new Date(Date.UTC(
      Number(dateText.slice(0, 4)),
      Number(dateText.slice(4, 6)) - 1,
      Number(dateText.slice(6, 8)),
    ));
    const label = SECOND_GRADE_ELECTIVE_BLOCKS[`${date.getUTCDay()}-${Number(row.PERIO)}`];
    if (label) row.ITRT_CNTNT = label;
  });
  return JSON.stringify(root);
}

function scheduleRows(scheduleJson: string): Array<Record<string, unknown>> {
  const root = JSON.parse(scheduleJson) as Record<string, unknown>;
  const blocks = Array.isArray(root.SchoolSchedule)
    ? root.SchoolSchedule as Array<Record<string, unknown>>
    : [];
  return blocks.flatMap((block) =>
    Array.isArray(block.row) ? block.row as Array<Record<string, unknown>> : [],
  );
}

async function syncWednesdayActivities(env: Env, scheduleJson: string, year: number): Promise<number> {
  if (year !== 2026) return 0;
  const rows = scheduleRows(scheduleJson);
  if (rows.length === 0) return 0;

  const eventsByDate = new Map<string, string[]>();
  rows.filter((row) => String(row.TW_GRADE_EVENT_YN) === "Y").forEach((row) => {
    const date = String(row.AA_YMD);
    const events = eventsByDate.get(date) || [];
    events.push(String(row.EVENT_NM));
    eventsByDate.set(date, events);
  });
  const replacesRegularClasses = [
    "평가", "지필", "체험학습", "방학", "휴일", "추석", "설날", "신정", "성탄", "수능",
  ];
  const activities: Array<{lessonDate: string; subject: string}> = [];
  for (let date = new Date(Date.UTC(2026, 7, 19)); date <= new Date(Date.UTC(2026, 11, 30)); date.setUTCDate(date.getUTCDate() + 7)) {
    const lessonDate = `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}-${String(date.getUTCDate()).padStart(2, "0")}`;
    const ymdDate = lessonDate.replaceAll("-", "");
    const events = eventsByDate.get(ymdDate) || [];
    if (events.some((event) => replacesRegularClasses.some((keyword) => event.includes(keyword)))) continue;
    activities.push({
      lessonDate,
      subject: events.some((event) => event.includes("동아리")) ? "동아리" : "교육",
    });
  }

  const cacheKey = "timetable_wednesday_activities_2026";
  const cached = await getCache<Array<{lessonDate: string; subject: string}>>(env, cacheKey);
  if (cached && JSON.stringify(cached.value) === JSON.stringify(activities)) return activities.length * 2;

  const statements = [
    env.DB.prepare(
      `DELETE FROM timetable_date_overrides
       WHERE academic_year = 2026 AND grade = 2 AND class_number = 0
         AND lesson_date BETWEEN '2026-08-13' AND '2026-12-31'`,
    ),
    ...activities.flatMap((activity) => [6, 7].map((period) => env.DB.prepare(
      `INSERT INTO timetable_date_overrides (
         academic_year, grade, class_number, lesson_date, period, subject, source_name, updated_at
       ) VALUES (2026, 2, 0, ?1, ?2, ?3, 'NEIS SchoolSchedule', ?4)`,
    ).bind(activity.lessonDate, period, activity.subject, Date.now()))),
  ];
  await env.DB.batch(statements);
  await putCache(env, cacheKey, activities);
  return activities.length * 2;
}

async function mergeTemporaryTimetable(
  env: Env,
  timetableJson: string,
  year: number,
  month: number,
  grade: number,
  classNumber: number,
): Promise<{
  timetableJson: string;
  source: "neis" | "temporary" | "mixed" | "none";
  fallbackRowCount: number;
  fallbackDates: string[];
  sourceName: string | null;
}> {
  const monthStart = `${year}-${String(month).padStart(2, "0")}-01`;
  const monthEnd = `${year}-${String(month).padStart(2, "0")}-${String(lastDay(year, month)).padStart(2, "0")}`;
  const [result, dateOverrides] = await Promise.all([
    env.DB.prepare(
      `SELECT academic_year, semester, day_of_week, period, subject,
              effective_from, effective_to, source_name
       FROM temporary_timetable
       WHERE grade = ?1 AND class_number = ?2
         AND effective_from <= ?3 AND effective_to >= ?4
       ORDER BY day_of_week, period`,
    ).bind(grade, classNumber, monthEnd, monthStart).all<TemporaryTimetableRow>(),
    env.DB.prepare(
      `SELECT lesson_date, period, subject, source_name
       FROM timetable_date_overrides
       WHERE grade = ?1 AND (class_number = 0 OR class_number = ?2)
         AND lesson_date BETWEEN ?3 AND ?4
       ORDER BY lesson_date, period, class_number`,
    ).bind(grade, classNumber, monthStart, monthEnd).all<TimetableDateOverrideRow>(),
  ]);

  const officialRows = timetableRows(timetableJson);
  if (result.results.length === 0 && dateOverrides.results.length === 0) {
    return {
      timetableJson,
      source: officialRows.length > 0 ? "neis" : "none",
      fallbackRowCount: 0,
      fallbackDates: [],
      sourceName: null,
    };
  }

  const officialDates = new Set(officialRows.map((row) => String(row.ALL_TI_YMD)));
  const rulesByDay = new Map<number, TemporaryTimetableRow[]>();
  result.results.forEach((row) => {
    const rules = rulesByDay.get(row.day_of_week) || [];
    rules.push(row);
    rulesByDay.set(row.day_of_week, rules);
  });
  const fallbackRows: Array<Record<string, unknown>> = [];
  const fallbackDates = new Set<string>();
  const sourceName = result.results[0]?.source_name || null;

  for (let day = 1; day <= lastDay(year, month); day++) {
    const date = new Date(Date.UTC(year, month - 1, day));
    const dayOfWeek = date.getUTCDay();
    const rules = rulesByDay.get(dayOfWeek);
    if (!rules) continue;
    const isoDate = `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    const ymdDate = isoDate.replaceAll("-", "");
    if (officialDates.has(ymdDate)) continue;
    rules.filter((rule) => isoDate >= rule.effective_from && isoDate <= rule.effective_to)
      .forEach((rule) => {
        fallbackDates.add(isoDate);
        fallbackRows.push({
          ATPT_OFCDC_SC_CODE: OFFICE_CODE,
          SD_SCHUL_CODE: SCHOOL_CODE,
          AY: String(rule.academic_year),
          SEM: String(rule.semester),
          GRADE: String(grade),
          CLASS_NM: String(classNumber),
          ALL_TI_YMD: ymdDate,
          PERIO: String(rule.period),
          ITRT_CNTNT: rule.subject,
          TMPR_TI_YN: "Y",
        });
      });
  }

  dateOverrides.results.forEach((override) => {
    const ymdDate = override.lesson_date.replaceAll("-", "");
    if (officialDates.has(ymdDate)) return;
    const existingIndex = fallbackRows.findIndex((row) =>
      String(row.ALL_TI_YMD) === ymdDate && Number(row.PERIO) === override.period
    );
    if (existingIndex >= 0) fallbackRows.splice(existingIndex, 1);
    fallbackDates.add(override.lesson_date);
    fallbackRows.push({
      ATPT_OFCDC_SC_CODE: OFFICE_CODE,
      SD_SCHUL_CODE: SCHOOL_CODE,
      AY: "2026",
      SEM: "2",
      GRADE: String(grade),
      CLASS_NM: String(classNumber),
      ALL_TI_YMD: ymdDate,
      PERIO: String(override.period),
      ITRT_CNTNT: override.subject,
      TMPR_TI_YN: "Y",
      TMPR_OVERRIDE_YN: "Y",
    });
  });

  if (fallbackRows.length === 0) {
    return {
      timetableJson,
      source: officialRows.length > 0 ? "neis" : "none",
      fallbackRowCount: 0,
      fallbackDates: [],
      sourceName,
    };
  }

  const rows = [...officialRows, ...fallbackRows].sort((left, right) =>
    String(left.ALL_TI_YMD).localeCompare(String(right.ALL_TI_YMD)) ||
    Number(left.PERIO) - Number(right.PERIO)
  );
  const root = JSON.parse(timetableJson) as Record<string, unknown>;
  root.hisTimetable = [
    {head: [{list_total_count: rows.length}, {RESULT: {CODE: "INFO-000", MESSAGE: "정상 처리되었습니다."}}]},
    {row: rows},
  ];
  delete root.RESULT;
  return {
    timetableJson: JSON.stringify(root),
    source: officialRows.length > 0 ? "mixed" : "temporary",
    fallbackRowCount: fallbackRows.length,
    fallbackDates: [...fallbackDates].sort(),
    sourceName,
  };
}

async function refreshActiveTimetables(env: Env, year: number, month: number): Promise<number> {
  const result = await env.DB.prepare(
    "SELECT cache_key FROM data_cache WHERE cache_key LIKE 'neis_timetable_%'",
  ).all<{cache_key: string}>();
  const activeClasses = result.results.map((row) => {
    const match = row.cache_key.match(/^neis_timetable_(\d{4})_(\d{1,2})_(\d+)_(\d+)$/);
    if (!match || Number(match[1]) !== year || Number(match[2]) !== month) return null;
    return {grade: Number(match[3]), classNumber: Number(match[4])};
  }).filter((value): value is {grade: number; classNumber: number} => value !== null);

  for (let index = 0; index < activeClasses.length; index += 5) {
    await Promise.all(activeClasses.slice(index, index + 5).map(({grade, classNumber}) =>
      loadTimetable(env, year, month, grade, classNumber, true)
    ));
  }
  return activeClasses.length;
}

function intParam(url: URL, name: string): number {
  return Number(url.searchParams.get(name));
}

function maskedPhoneNumber(phoneNumber: string): string {
  if (phoneNumber.startsWith("+82") && phoneNumber.length >= 12) {
    const local = `0${phoneNumber.slice(3)}`;
    return `${local.slice(0, 3)}-****-${local.slice(-4)}`;
  }
  return `${phoneNumber.slice(0, 3)}****${phoneNumber.slice(-4)}`;
}

async function profileSubjectHash(env: Env, subject: string): Promise<string> {
  return hmac(env, `firebase:${subject}`);
}

function normalizeLoginId(value: unknown): string {
  const loginId = typeof value === "string" ? value.trim().toLowerCase() : "";
  if (!/^[a-z0-9][a-z0-9_]{3,19}$/.test(loginId)) {
    throw new HttpError(400, "아이디는 영문 소문자·숫자·밑줄을 사용해 4~20자로 입력해 주세요.");
  }
  return loginId;
}

function normalizeEmail(value: unknown): string {
  const email = typeof value === "string" ? value.trim().toLowerCase() : "";
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new HttpError(400, "이메일 주소를 확인해 주세요.");
  }
  return email;
}

async function enforceAuthRateLimit(
  request: Request,
  env: Env,
  action: string,
  identifier: string,
  maximumRequests: number,
): Promise<void> {
  const now = Date.now();
  const windowMs = 15 * 60 * 1000;
  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  const rateKey = await hmac(env, `auth-rate:${action}:${ip}:${identifier}`);
  const row = await env.PROFILE_DB.prepare(
    "SELECT window_started_at, request_count, blocked_until FROM auth_rate_limits WHERE rate_key = ?1",
  ).bind(rateKey).first<{window_started_at: number; request_count: number; blocked_until: number}>();
  if (row?.blocked_until && row.blocked_until > now) {
    throw new HttpError(429, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
  }
  const inWindow = row && now - row.window_started_at < windowMs;
  const count = inWindow ? row.request_count + 1 : 1;
  const startedAt = inWindow ? row.window_started_at : now;
  const blockedUntil = count > maximumRequests ? now + windowMs : 0;
  await env.PROFILE_DB.prepare(
    `INSERT INTO auth_rate_limits(rate_key, window_started_at, request_count, blocked_until, updated_at)
     VALUES (?1, ?2, ?3, ?4, ?5)
     ON CONFLICT(rate_key) DO UPDATE SET
       window_started_at = excluded.window_started_at,
       request_count = excluded.request_count,
       blocked_until = excluded.blocked_until,
       updated_at = excluded.updated_at`,
  ).bind(rateKey, startedAt, count, blockedUntil, now).run();
  if (blockedUntil) throw new HttpError(429, "요청이 너무 많습니다. 15분 후 다시 시도해 주세요.");
}

async function firebaseIdentityToolkit(
  env: Env,
  endpoint: string,
  body: Record<string, unknown>,
): Promise<{ok: boolean; value: Record<string, unknown>}> {
  if (!env.FIREBASE_WEB_API_KEY) throw new Error("FIREBASE_WEB_API_KEY secret is missing");
  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/${endpoint}?key=${encodeURIComponent(env.FIREBASE_WEB_API_KEY)}`,
    {
      method: "POST",
      headers: {"content-type": "application/json"},
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(10_000),
    },
  );
  const value = await response.json<Record<string, unknown>>().catch(() => ({}));
  return {ok: response.ok, value};
}

async function accountByLoginId(env: Env, loginId: string): Promise<StudentAccountPayload | null> {
  const loginHash = await hmac(env, `login:${loginId}`);
  const row = await env.PROFILE_DB.prepare(
    "SELECT account_ciphertext, account_iv FROM student_accounts WHERE login_id_hash = ?1",
  ).bind(loginHash).first<{account_ciphertext: string; account_iv: string}>();
  return row ? decryptAccount(env, row.account_ciphertext, row.account_iv) : null;
}

async function putStudentAccount(request: Request, env: Env): Promise<Response> {
  const identity = await requireFirebaseIdentity(request, env, 10 * 60);
  if (!identity.phoneNumber) throw new HttpError(403, "최초 1회 휴대전화 인증이 필요합니다.");
  if (!identity.email || !identity.emailVerified) {
    throw new HttpError(403, "이메일 인증을 완료한 뒤 다시 시도해 주세요.");
  }
  const body = await request.json<Record<string, unknown>>().catch((): Record<string, unknown> => ({}));
  const loginId = normalizeLoginId(body.loginId);
  if (!env.ACCOUNT_CONSENT_VERSION || body.consent !== true ||
      body.consentVersion !== env.ACCOUNT_CONSENT_VERSION) {
    throw new HttpError(400, "계정 개인정보 수집·이용 동의가 필요합니다.");
  }
  const subjectHash = await profileSubjectHash(env, identity.subject);
  const profile = await env.PROFILE_DB.prepare(
    "SELECT provider_subject_hash FROM verified_student_profiles WHERE provider_subject_hash = ?1",
  ).bind(subjectHash).first();
  if (!profile) throw new HttpError(409, "학생 정보를 먼저 등록해 주세요.");

  const [loginIdHash, emailHash, encrypted] = await Promise.all([
    hmac(env, `login:${loginId}`),
    hmac(env, `email:${identity.email}`),
    encryptAccount(env, {loginId, email: identity.email}),
  ]);
  const now = Date.now();
  try {
    await env.PROFILE_DB.prepare(
      `INSERT INTO student_accounts(
         provider_subject_hash, login_id_hash, email_hash, account_ciphertext,
         account_iv, encryption_key_version, consent_version, consented_at, created_at, updated_at
       ) VALUES (?1, ?2, ?3, ?4, ?5, 1, ?6, ?7, ?7, ?7)
       ON CONFLICT(provider_subject_hash) DO UPDATE SET
         login_id_hash = excluded.login_id_hash,
         email_hash = excluded.email_hash,
         account_ciphertext = excluded.account_ciphertext,
         account_iv = excluded.account_iv,
         encryption_key_version = excluded.encryption_key_version,
         consent_version = excluded.consent_version,
         consented_at = excluded.consented_at,
         updated_at = excluded.updated_at`,
    ).bind(
      subjectHash, loginIdHash, emailHash, encrypted.ciphertext, encrypted.iv,
      env.ACCOUNT_CONSENT_VERSION, now,
    ).run();
  } catch {
    throw new HttpError(409, "이미 사용 중인 아이디 또는 이메일입니다.");
  }
  return json({saved: true, account: {loginId, email: identity.email}});
}

async function getStudentAccount(request: Request, env: Env): Promise<Response> {
  const identity = await requireFirebaseIdentity(request, env);
  const subjectHash = await profileSubjectHash(env, identity.subject);
  const row = await env.PROFILE_DB.prepare(
    "SELECT account_ciphertext, account_iv FROM student_accounts WHERE provider_subject_hash = ?1",
  ).bind(subjectHash).first<{account_ciphertext: string; account_iv: string}>();
  if (!row) throw new HttpError(404, "등록된 로그인 계정이 없습니다.");
  const account = await decryptAccount(env, row.account_ciphertext, row.account_iv);
  return json({account});
}

async function resolveLogin(request: Request, env: Env): Promise<Response> {
  const body = await request.json<Record<string, unknown>>().catch((): Record<string, unknown> => ({}));
  const loginId = normalizeLoginId(body.loginId);
  const password = typeof body.password === "string" ? body.password : "";
  if (password.length < 8 || password.length > 128) throw new HttpError(401, "아이디 또는 비밀번호가 올바르지 않습니다.");
  await enforceAuthRateLimit(request, env, "login", loginId, 8);
  const account = await accountByLoginId(env, loginId);
  if (!account) throw new HttpError(401, "아이디 또는 비밀번호가 올바르지 않습니다.");
  const result = await firebaseIdentityToolkit(env, "accounts:signInWithPassword", {
    email: account.email,
    password,
    returnSecureToken: true,
  });
  if (!result.ok) throw new HttpError(401, "아이디 또는 비밀번호가 올바르지 않습니다.");
  return json({email: account.email});
}

async function requestPasswordReset(request: Request, env: Env): Promise<Response> {
  const body = await request.json<Record<string, unknown>>().catch((): Record<string, unknown> => ({}));
  const loginId = normalizeLoginId(body.loginId);
  await enforceAuthRateLimit(request, env, "password-reset", loginId, 4);
  const account = await accountByLoginId(env, loginId);
  if (account) {
    await firebaseIdentityToolkit(env, "accounts:sendOobCode", {
      requestType: "PASSWORD_RESET",
      email: account.email,
    });
  }
  return json({accepted: true, message: "등록된 계정이면 비밀번호 재설정 메일을 보냈습니다."});
}

async function requestLoginIdEmail(request: Request, env: Env): Promise<Response> {
  const body = await request.json<Record<string, unknown>>().catch((): Record<string, unknown> => ({}));
  const email = normalizeEmail(body.email);
  await enforceAuthRateLimit(request, env, "find-login-id", email, 4);
  const emailHash = await hmac(env, `email:${email}`);
  const exists = await env.PROFILE_DB.prepare(
    "SELECT provider_subject_hash FROM student_accounts WHERE email_hash = ?1",
  ).bind(emailHash).first();
  if (exists) {
    await firebaseIdentityToolkit(env, "accounts:sendOobCode", {
      requestType: "EMAIL_SIGNIN",
      email,
      continueUrl: `https://${env.FIREBASE_PROJECT_ID}.firebaseapp.com/auth/email-link`,
      canHandleCodeInApp: true,
      androidPackageName: "kr.hs.gunpo.school",
      androidInstallApp: true,
    });
  }
  return json({accepted: true, message: "등록된 이메일이면 아이디 확인 링크를 보냈습니다."});
}

type SocialProvider = "naver" | "kakao";

function requireSocialConfiguration(env: Env, provider: SocialProvider): void {
  if (!(provider === "kakao" ? env.KAKAO_REST_API_KEY && env.KAKAO_CLIENT_SECRET : env.NAVER_CLIENT_ID && env.NAVER_CLIENT_SECRET) ||
      !env.FIREBASE_SERVICE_ACCOUNT_EMAIL || !env.FIREBASE_SERVICE_ACCOUNT_PRIVATE_KEY) {
    throw new HttpError(503, "소셜 로그인이 아직 서버에 설정되지 않았습니다.");
  }
}

async function startSocialAuthentication(request: Request, env: Env, provider: SocialProvider): Promise<Response> {
  requireSocialConfiguration(env, provider);
  await enforceAuthRateLimit(request, env, `${provider}-start`, "oauth", 8);
  const authorization = request.headers.get("authorization");
  const identity = authorization ? await requireFirebaseIdentity(request, env, 10 * 60) : null;
  const body = await request.json<Record<string, unknown>>().catch((): Record<string, unknown> => ({}));
  const codeChallenge = typeof body.codeChallenge === "string" ? body.codeChallenge : "";
  if (!/^[A-Za-z0-9_-]{43}$/.test(codeChallenge)) throw new HttpError(400, "소셜 보안 요청이 올바르지 않습니다.");
  const state = randomUrlToken();
  const stateHash = await hmac(env, `${provider}-state:${state}`);
  const encryptedUid = identity ? await encryptSecretText(env, identity.subject) : null;
  const now = Date.now();
  await env.PROFILE_DB.prepare(
    `INSERT INTO naver_auth_sessions(
      state_hash, code_challenge, firebase_uid_ciphertext, firebase_uid_iv, expires_at, created_at
     ) VALUES (?1, ?2, ?3, ?4, ?5, ?6)`,
  ).bind(stateHash, codeChallenge, encryptedUid?.ciphertext ?? null, encryptedUid?.iv ?? null, now + 10 * 60 * 1000, now).run();
  const callbackUrl = `${new URL(request.url).origin}/v1/auth/${provider}/callback`;
  const authorizeUrl = new URL(provider === "kakao" ? "https://kauth.kakao.com/oauth/authorize" : "https://nid.naver.com/oauth2.0/authorize");
  authorizeUrl.searchParams.set("response_type", "code");
  authorizeUrl.searchParams.set("client_id", provider === "kakao" ? env.KAKAO_REST_API_KEY : env.NAVER_CLIENT_ID);
  authorizeUrl.searchParams.set("redirect_uri", callbackUrl);
  authorizeUrl.searchParams.set("state", state);
  return json({authorizeUrl: authorizeUrl.toString()});
}

async function firebaseCustomToken(env: Env, uid: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const privateKey = env.FIREBASE_SERVICE_ACCOUNT_PRIVATE_KEY.replace(/\\n/g, "\n");
  return new SignJWT({uid})
    .setProtectedHeader({alg: "RS256", typ: "JWT"})
    .setIssuer(env.FIREBASE_SERVICE_ACCOUNT_EMAIL)
    .setSubject(env.FIREBASE_SERVICE_ACCOUNT_EMAIL)
    .setAudience("https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit")
    .setIssuedAt(now)
    .setExpirationTime(now + 60 * 60)
    .sign(await importPKCS8(privateKey, "RS256"));
}

async function completeSocialAuthentication(request: Request, env: Env, provider: SocialProvider): Promise<Response> {
  requireSocialConfiguration(env, provider);
  const url = new URL(request.url);
  const state = url.searchParams.get("state") || "";
  const authorizationCode = url.searchParams.get("code") || "";
  if (!state || !authorizationCode || url.searchParams.get("error")) {
    throw new HttpError(400, "소셜 인증이 취소되었거나 올바르지 않습니다.");
  }
  const stateHash = await hmac(env, `${provider}-state:${state}`);
  const session = await env.PROFILE_DB.prepare(
    `DELETE FROM naver_auth_sessions WHERE state_hash = ?1
     RETURNING code_challenge, firebase_uid_ciphertext, firebase_uid_iv, expires_at`,
  ).bind(stateHash).first<{
    code_challenge: string;
    firebase_uid_ciphertext: string | null;
    firebase_uid_iv: string | null;
    expires_at: number;
  }>();
  if (!session || session.expires_at <= Date.now()) throw new HttpError(400, "소셜 인증 요청이 만료되었습니다.");

  const callbackUrl = `${url.origin}/v1/auth/${provider}/callback`;
  const tokenUrl = provider === "kakao" ? "https://kauth.kakao.com/oauth/token" : "https://nid.naver.com/oauth2.0/token";
  const tokenBody = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: provider === "kakao" ? env.KAKAO_REST_API_KEY : env.NAVER_CLIENT_ID,
    client_secret: provider === "kakao" ? env.KAKAO_CLIENT_SECRET : env.NAVER_CLIENT_SECRET,
    code: authorizationCode,
    state,
    redirect_uri: callbackUrl,
  });
  const tokenResponse = await fetch(tokenUrl, {method: "POST", body: tokenBody, signal: AbortSignal.timeout(10_000)});
  const token = await tokenResponse.json<{access_token?: string}>().catch((): {access_token?: string} => ({}));
  if (!tokenResponse.ok || !token.access_token) throw new HttpError(401, "소셜 인증 토큰을 확인하지 못했습니다.");
  const profileResponse = await fetch(provider === "kakao" ? "https://kapi.kakao.com/v2/user/me" : "https://openapi.naver.com/v1/nid/me", {
    headers: {authorization: `Bearer ${token.access_token}`}, signal: AbortSignal.timeout(10_000),
  });
  const profile = await profileResponse.json<{id?: number; resultcode?: string; response?: {id?: string}}>()
    .catch((): {id?: number; resultcode?: string; response?: {id?: string}} => ({}));
  const naverId = provider === "kakao"
    ? (Number.isSafeInteger(profile.id) && Number(profile.id) > 0 ? String(profile.id) : undefined)
    : (profile.resultcode === "00" ? profile.response?.id : undefined);
  if (!profileResponse.ok || !naverId) throw new HttpError(401, "소셜 사용자 정보를 확인하지 못했습니다.");

  const providerUserHash = await hmac(env, `${provider}-user:${naverId}`);
  let firebaseUid: string | null = null;
  if (session.firebase_uid_ciphertext && session.firebase_uid_iv) {
    firebaseUid = await decryptSecretText(env, session.firebase_uid_ciphertext, session.firebase_uid_iv);
  } else {
    const linked = await env.PROFILE_DB.prepare(
      `SELECT firebase_uid_ciphertext, firebase_uid_iv FROM social_identity_links
       WHERE provider = ?2 AND provider_user_hash = ?1`,
    ).bind(providerUserHash, provider).first<{firebase_uid_ciphertext: string; firebase_uid_iv: string}>();
    if (linked) firebaseUid = await decryptSecretText(env, linked.firebase_uid_ciphertext, linked.firebase_uid_iv);
  }
  if (!firebaseUid) {
    const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(naverId)));
    firebaseUid = `${provider}_${bytesToHex(digest).slice(0, 48)}`;
  }

  const encryptedUid = await encryptSecretText(env, firebaseUid);
  const firebaseSubjectHash = await profileSubjectHash(env, firebaseUid);
  const now = Date.now();
  // A social identity must never be reassigned to another Firebase account.
  await env.PROFILE_DB.prepare(
    `INSERT OR IGNORE INTO social_identity_links(
       provider, provider_user_hash, firebase_uid_ciphertext, firebase_uid_iv, created_at, updated_at
     ) VALUES (?1, ?2, ?3, ?4, ?5, ?5)`,
  ).bind(provider, providerUserHash, encryptedUid.ciphertext, encryptedUid.iv, now).run();
  const owner = await env.PROFILE_DB.prepare(
    "SELECT firebase_uid_ciphertext, firebase_uid_iv FROM social_identity_links WHERE provider = ?1 AND provider_user_hash = ?2",
  ).bind(provider, providerUserHash).first<{firebase_uid_ciphertext: string; firebase_uid_iv: string}>();
  if (!owner || await decryptSecretText(env, owner.firebase_uid_ciphertext, owner.firebase_uid_iv) !== firebaseUid) {
    throw new HttpError(409, "이미 다른 계정에 연결된 소셜 계정입니다.");
  }
  await env.PROFILE_DB.prepare(
    "INSERT OR IGNORE INTO firebase_social_links(provider_subject_hash, provider, created_at) VALUES (?1, ?2, ?3)",
  ).bind(firebaseSubjectHash, provider, now).run();

  const customToken = await firebaseCustomToken(env, firebaseUid);
  const exchangeCode = randomUrlToken();
  const exchangeHash = await hmac(env, `${provider}-code:${exchangeCode}`);
  const encryptedToken = await encryptSecretText(env, customToken);
  await env.PROFILE_DB.prepare(
    `INSERT INTO naver_login_codes(
       code_hash, code_challenge, custom_token_ciphertext, custom_token_iv, expires_at, created_at
     ) VALUES (?1, ?2, ?3, ?4, ?5, ?6)`,
  ).bind(exchangeHash, session.code_challenge, encryptedToken.ciphertext, encryptedToken.iv, now + 2 * 60 * 1000, now).run();
  return Response.redirect(`gunposchool://auth/${provider}?code=${encodeURIComponent(exchangeCode)}`, 302);
}

async function exchangeSocialLoginCode(request: Request, env: Env, provider: SocialProvider): Promise<Response> {
  requireSocialConfiguration(env, provider);
  const body = await request.json<Record<string, unknown>>().catch((): Record<string, unknown> => ({}));
  const code = typeof body.code === "string" ? body.code : "";
  const verifier = typeof body.codeVerifier === "string" ? body.codeVerifier : "";
  if (!/^[A-Za-z0-9_-]{20,100}$/.test(code)) throw new HttpError(400, "소셜 로그인 코드가 올바르지 않습니다.");
  if (!/^[A-Za-z0-9_-]{43,128}$/.test(verifier)) throw new HttpError(400, "소셜 보안 검증값이 올바르지 않습니다.");
  const codeHash = await hmac(env, `${provider}-code:${code}`);
  const row = await env.PROFILE_DB.prepare(
    `DELETE FROM naver_login_codes WHERE code_hash = ?1
     RETURNING code_challenge, custom_token_ciphertext, custom_token_iv, expires_at`,
  ).bind(codeHash).first<{code_challenge: string; custom_token_ciphertext: string; custom_token_iv: string; expires_at: number}>();
  if (!row || row.expires_at <= Date.now()) throw new HttpError(400, "소셜 로그인 코드가 만료되었습니다.");
  const verifierDigest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  const verifierChallenge = bytesToBase64(verifierDigest).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  if (verifierChallenge !== row.code_challenge) throw new HttpError(401, "소셜 로그인 보안 검증에 실패했습니다.");
  return json({customToken: await decryptSecretText(env, row.custom_token_ciphertext, row.custom_token_iv)});
}

async function socialLinkStatus(request: Request, env: Env, provider: SocialProvider): Promise<Response> {
  const identity = await requireFirebaseIdentity(request, env);
  const subjectHash = await profileSubjectHash(env, identity.subject);
  const linked = await env.PROFILE_DB.prepare(
    "SELECT provider FROM firebase_social_links WHERE provider_subject_hash = ?1 AND provider = ?2",
  ).bind(subjectHash, provider).first();
  return json({linked: Boolean(linked), configured: Boolean(
    (provider === "kakao" ? env.KAKAO_REST_API_KEY && env.KAKAO_CLIENT_SECRET : env.NAVER_CLIENT_ID && env.NAVER_CLIENT_SECRET) &&
    env.FIREBASE_SERVICE_ACCOUNT_EMAIL && env.FIREBASE_SERVICE_ACCOUNT_PRIVATE_KEY
  )});
}

async function putStudentProfile(request: Request, env: Env): Promise<Response> {
  const identity = await requireFirebaseIdentity(request, env);
  if (!identity.phoneNumber) throw new HttpError(403, "최초 1회 휴대전화 인증이 필요합니다.");
  const subjectHash = await profileSubjectHash(env, identity.subject);
  const existing = await env.PROFILE_DB.prepare(
    "SELECT provider_subject_hash FROM verified_student_profiles WHERE provider_subject_hash = ?1",
  ).bind(subjectHash).first();
  const nowSeconds = Math.floor(Date.now() / 1000);
  if (!existing && nowSeconds - identity.authenticationTime > 10 * 60) {
    throw new HttpError(401, "휴대전화 인증 시간이 지났습니다. 다시 인증해 주세요.");
  }
  const input = parseStudentProfile(await request.json<unknown>().catch(() => null));
  if (!env.PROFILE_CONSENT_VERSION || !input.consent || input.consentVersion !== env.PROFILE_CONSENT_VERSION) {
    throw new HttpError(400, "개인정보 수집·이용 및 국외 이전 동의가 필요합니다.");
  }

  const now = Date.now();
  const profile: StudentProfilePayload = {
    phoneNumber: identity.phoneNumber,
    name: input.name,
    studentNumber: input.studentNumber,
    grade: input.grade,
    classNumber: input.classNumber,
    seatNumber: input.seatNumber,
  };
  const [phoneHash, encrypted] = await Promise.all([
    hmac(env, `phone:${identity.phoneNumber}`),
    encryptProfile(env, profile),
  ]);
  await env.PROFILE_DB.prepare(
    `INSERT INTO verified_student_profiles(
       provider_subject_hash, verification_provider, verification_reference_hash,
       profile_ciphertext, profile_iv, encryption_key_version, consent_version,
       consented_at, verified_at, expires_at, created_at, updated_at
     ) VALUES (?1, 'firebase_sms', ?2, ?3, ?4, 1, ?5, ?6, ?6, ?7, ?6, ?6)
     ON CONFLICT(provider_subject_hash) DO UPDATE SET
       verification_provider = excluded.verification_provider,
       verification_reference_hash = excluded.verification_reference_hash,
       profile_ciphertext = excluded.profile_ciphertext,
       profile_iv = excluded.profile_iv,
       encryption_key_version = excluded.encryption_key_version,
       consent_version = excluded.consent_version,
       consented_at = excluded.consented_at,
       verified_at = excluded.verified_at,
       expires_at = excluded.expires_at,
       updated_at = excluded.updated_at`,
  ).bind(
    subjectHash,
    phoneHash,
    encrypted.ciphertext,
    encrypted.iv,
    input.consentVersion,
    now,
    now + PROFILE_RETENTION_MS,
  ).run();

  return json({
    saved: true,
    profile: {
      name: profile.name,
      studentNumber: profile.studentNumber,
      phoneNumberMasked: maskedPhoneNumber(profile.phoneNumber),
    },
    expiresAt: now + PROFILE_RETENTION_MS,
  });
}

async function getStudentProfile(request: Request, env: Env): Promise<Response> {
  const identity = await requireFirebaseIdentity(request, env);
  const subjectHash = await profileSubjectHash(env, identity.subject);
  const row = await env.PROFILE_DB.prepare(
    `SELECT profile_ciphertext, profile_iv, consent_version, consented_at, verified_at, expires_at
     FROM verified_student_profiles WHERE provider_subject_hash = ?1`,
  ).bind(subjectHash).first<{
    profile_ciphertext: string;
    profile_iv: string;
    consent_version: string;
    consented_at: number;
    verified_at: number;
    expires_at: number;
  }>();
  if (!row) throw new HttpError(404, "저장된 학생 정보가 없습니다.");
  if (row.expires_at <= Date.now()) {
    await env.PROFILE_DB.prepare(
      "DELETE FROM verified_student_profiles WHERE provider_subject_hash = ?1",
    ).bind(subjectHash).run();
    throw new HttpError(404, "보관 기간이 끝나 학생 정보를 삭제했습니다.");
  }
  const profile = await decryptProfile(env, row.profile_ciphertext, row.profile_iv);
  return json({
    profile: {
      name: profile.name,
      studentNumber: profile.studentNumber,
      phoneNumberMasked: maskedPhoneNumber(profile.phoneNumber),
    },
    consentVersion: row.consent_version,
    consentedAt: row.consented_at,
    verifiedAt: row.verified_at,
    expiresAt: row.expires_at,
  });
}

async function deleteStudentProfile(request: Request, env: Env): Promise<Response> {
  const identity = await requireFirebaseIdentity(request, env);
  const subjectHash = await profileSubjectHash(env, identity.subject);
  await env.PROFILE_DB.prepare(
    "DELETE FROM verified_student_profiles WHERE provider_subject_hash = ?1",
  ).bind(subjectHash).run();
  return json({deleted: true});
}

async function handleRequest(request: Request, env: Env): Promise<Response> {
  if (request.method === "OPTIONS") return json({}, 204);
  const url = new URL(request.url);

  if (request.method === "GET" && url.pathname === "/health") {
    return json({ok: true, service: "gunpo-school"});
  }

  if (request.method === "GET" && url.pathname === "/v1/time") {
    const now = new Date();
    const seoul = seoulDateParts(now);
    return json({
      epochMillis: now.getTime(),
      timeZone: "Asia/Seoul",
      date: `${seoul.year}-${String(seoul.month).padStart(2, "0")}-${String(seoul.day).padStart(2, "0")}`,
    });
  }

  if (url.pathname === "/v1/profile") {
    if (request.method === "PUT") return putStudentProfile(request, env);
    if (request.method === "GET") return getStudentProfile(request, env);
    if (request.method === "DELETE") return deleteStudentProfile(request, env);
  }

  if (url.pathname === "/v1/account") {
    if (request.method === "PUT") return putStudentAccount(request, env);
    if (request.method === "GET") return getStudentAccount(request, env);
  }

  if (request.method === "POST" && url.pathname === "/v1/auth/resolve-login") {
    return resolveLogin(request, env);
  }

  if (request.method === "POST" && url.pathname === "/v1/auth/password-reset") {
    return requestPasswordReset(request, env);
  }

  if (request.method === "POST" && url.pathname === "/v1/auth/find-login-id") {
    return requestLoginIdEmail(request, env);
  }

  const socialRoute = url.pathname.match(/^\/v1\/auth\/(naver|kakao)\/(start|callback|exchange|status)$/);
  if (socialRoute) {
    const provider = socialRoute[1] as SocialProvider;
    const action = socialRoute[2];
    if (request.method === "POST" && action === "start") return startSocialAuthentication(request, env, provider);
    if (request.method === "GET" && action === "callback") return completeSocialAuthentication(request, env, provider);
    if (request.method === "POST" && action === "exchange") return exchangeSocialLoginCode(request, env, provider);
    if (request.method === "GET" && action === "status") return socialLinkStatus(request, env, provider);
  }

  if (request.method === "GET" && url.pathname === "/v1/notices") {
    const cached = await getCache<Notice[]>(env, "notices") || await refreshNotices(env);
    return json({items: cached.value, fingerprint: cached.fingerprint, updatedAt: cached.updatedAt});
  }

  if (request.method === "POST" && url.pathname === "/v1/notices/sync") {
    const body = await request.json<{clientFingerprint?: string}>().catch(
      (): {clientFingerprint?: string} => ({}),
    );
    const cached = await getCache<Notice[]>(env, "notices");
    const shouldRefresh = !cached || (
      cached.fingerprint !== body.clientFingerprint &&
      Date.now() - cached.updatedAt >= NOTICE_SYNC_COOLDOWN_MS
    );
    const result = shouldRefresh ? await refreshNotices(env) : cached!;
    return json({items: result.value, fingerprint: result.fingerprint, updatedAt: result.updatedAt});
  }

  if (request.method === "GET" && url.pathname === "/v1/neis") {
    const grade = intParam(url, "grade");
    const classNumber = intParam(url, "classNumber");
    const year = intParam(url, "year");
    const month = intParam(url, "month");
    const sync = url.searchParams.get("sync") === "true";
    if (!Number.isInteger(grade) || grade < 1 || grade > 3 ||
        !Number.isInteger(classNumber) || classNumber < 1 || classNumber > 20 ||
        !Number.isInteger(year) || year < 2025 || year > 2100 ||
        !Number.isInteger(month) || month < 1 || month > 12) {
      return json({error: "학년, 반, 조회 연월이 올바르지 않습니다."}, 400);
    }
    const [common, timetable] = await Promise.all([
      loadCommonNeis(env, year, month, sync),
      loadTimetable(env, year, month, grade, classNumber, sync),
    ]);
    const activityOverrideCount = await syncWednesdayActivities(env, common.scheduleJson, year);
    const mergedMeals = await mergeContractDinners(env, common.mealJson, year, month, grade);
    const mergedTimetable = await mergeTemporaryTimetable(
      env,
      timetable.timetableJson,
      year,
      month,
      grade,
      classNumber,
    );
    return json({
      mealJson: mergedMeals.mealJson,
      contractDinnerCount: mergedMeals.count,
      scheduleJson: common.scheduleJson,
      timetableJson: preserveElectiveBlockLabels(mergedTimetable.timetableJson),
      timetableSource: mergedTimetable.source,
      temporaryFallback: {
        rowCount: mergedTimetable.fallbackRowCount,
        dates: mergedTimetable.fallbackDates,
        sourceName: mergedTimetable.sourceName,
        activityOverrideCount,
      },
      cached: common.cached || timetable.cached,
      syncMode: sync ? "realtime" : "cache-first",
      common: {cached: common.cached, stale: common.stale, updatedAt: common.updatedAt},
      timetable: {cached: timetable.cached, stale: timetable.stale, updatedAt: timetable.updatedAt},
    });
  }

  return json({error: "Not found"}, 404);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      return await handleRequest(request, env);
    } catch (error) {
      console.error(error);
      if (error instanceof HttpError) return json({error: error.message}, error.status);
      return json({error: "학교 데이터를 처리하지 못했습니다."}, 500);
    }
  },

  async scheduled(controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil((async () => {
      const {year, month} = seoulDateParts();
      if (controller.cron === DAILY_TIMETABLE_SYNC_CRON) {
        await refreshActiveTimetables(env, year, month);
      } else if (controller.cron === HOURLY_SYNC_CRON) {
        await Promise.all([refreshNotices(env), loadCommonNeis(env, year, month, true)]);
      }
    })());
  },
} satisfies ExportedHandler<Env>;
