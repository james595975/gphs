import * as cheerio from "cheerio";

interface Env {
  DB: D1Database;
  NEIS_API_KEY: string;
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

const OFFICE_CODE = "J10";
const SCHOOL_CODE = "7530148";
const NEIS_BASE_URL = "https://open.neis.go.kr/hub";
const CACHE_MS = 30 * 60 * 1000;
const NOTICE_SYNC_COOLDOWN_MS = 60 * 1000;

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
  return Response.json(data, {
    status,
    headers: {
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "content-type",
      "access-control-allow-methods": "GET,POST,OPTIONS",
    },
  });
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
    return {...cached.value, cached: true};
  }
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
  await putCache(env, key, {mealJson, scheduleJson});
  return {mealJson, scheduleJson, cached: false};
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

async function loadTimetable(env: Env, year: number, month: number, grade: number, classNumber: number) {
  const key = `neis_timetable_${year}_${month}_${grade}_${classNumber}`;
  const cached = await getCache<{timetableJson: string}>(env, key);
  if (cached && Date.now() - cached.updatedAt < CACHE_MS) {
    return {...cached.value, cached: true};
  }
  const timetableJson = await requestNeis(env, "hisTimetable", {
    AY: String(year),
    SEM: month >= 3 && month <= 7 ? "1" : "2",
    GRADE: String(grade),
    CLASS_NM: String(classNumber),
    TI_FROM_YMD: ymd(year, month, 1),
    TI_TO_YMD: ymd(year, month, lastDay(year, month)),
  });
  await putCache(env, key, {timetableJson});
  return {timetableJson, cached: false};
}

function intParam(url: URL, name: string): number {
  return Number(url.searchParams.get(name));
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
    if (!Number.isInteger(grade) || grade < 1 || grade > 3 ||
        !Number.isInteger(classNumber) || classNumber < 1 || classNumber > 20 ||
        !Number.isInteger(year) || year < 2025 || year > 2100 ||
        !Number.isInteger(month) || month < 1 || month > 12) {
      return json({error: "학년, 반, 조회 연월이 올바르지 않습니다."}, 400);
    }
    const [common, timetable] = await Promise.all([
      loadCommonNeis(env, year, month),
      loadTimetable(env, year, month, grade, classNumber),
    ]);
    const mergedMeals = await mergeContractDinners(env, common.mealJson, year, month, grade);
    return json({
      mealJson: mergedMeals.mealJson,
      contractDinnerCount: mergedMeals.count,
      scheduleJson: common.scheduleJson,
      timetableJson: timetable.timetableJson,
      cached: common.cached || timetable.cached,
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
      return json({error: "학교 데이터를 처리하지 못했습니다."}, 500);
    }
  },

  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil((async () => {
      const {year, month} = seoulDateParts();
      await Promise.all([refreshNotices(env), loadCommonNeis(env, year, month, true)]);
    })());
  },
} satisfies ExportedHandler<Env>;
