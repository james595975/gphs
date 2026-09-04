package kr.hs.gunpo.school.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kr.hs.gunpo.school.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class NeisState(
    val meals: List<Meal> = emptyList(),
    val events: List<AcademicEvent> = emptyList(),
    val timetableByDate: Map<LocalDate, List<Lesson>> = emptyMap(),
    val isLoading: Boolean = false,
    val isFromCache: Boolean = false,
    val errorMessage: String? = null,
    val grade: Int? = null,
    val classNumber: Int? = null,
    val timetableSource: String? = null,
    val temporaryTimetableDates: Set<LocalDate> = emptySet(),
    val loadedMonths: Set<YearMonth> = emptySet(),
)

class NeisRepository(private val context: Context) {
    companion object {
        private const val BASE_URL = "https://open.neis.go.kr/hub"
        private const val OFFICE_CODE = "J10"
        private const val SCHOOL_CODE = "7530148"
        private const val CACHE_NAME = "neis_cache"
        private val DATE = DateTimeFormatter.BASIC_ISO_DATE
    }

    suspend fun load(
        settings: UserSettings,
        today: LocalDate,
        forceServerSync: Boolean = true,
    ): NeisState = withContext(Dispatchers.IO) {
        val monthFrom = today.withDayOfMonth(1)
        val monthTo = monthFrom.plusMonths(1).withDayOfMonth(monthFrom.plusMonths(1).lengthOfMonth())
        val yearFrom = LocalDate.of(today.year, 1, 1)
        val yearTo = LocalDate.of(today.year, 12, 31)
        val cacheSuffix = "${settings.grade}_${settings.classNumber}_${today.year}_${today.monthValue}"
        val prefs = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)

        fun cachedOrFetch(key: String, endpoint: String, parameters: Map<String, String>): Pair<String, Boolean> {
            return try {
                val body = request(endpoint, parameters)
                prefs.edit().putString(key, body).apply()
                body to false
            } catch (error: Exception) {
                val cached = prefs.getString(key, null) ?: throw error
                cached to true
            }
        }

        try {
            if (BuildConfig.CLOUDFLARE_API_BASE_URL.isNotBlank()) {
                loadFromServer(
                    settings = settings,
                    today = today,
                    forceSync = forceServerSync,
                    prefs = prefs,
                    cacheKey = "server_$cacheSuffix",
                )?.let { return@withContext it }
            }
            val (mealJson, mealCached) = cachedOrFetch(
                "meal_${today.year}_${today.monthValue}",
                "mealServiceDietInfo",
                mapOf("MLSV_FROM_YMD" to monthFrom.format(DATE), "MLSV_TO_YMD" to monthTo.format(DATE)),
            )
            val (scheduleJson, scheduleCached) = cachedOrFetch(
                "schedule_${today.year}",
                "SchoolSchedule",
                mapOf("AA_FROM_YMD" to yearFrom.format(DATE), "AA_TO_YMD" to yearTo.format(DATE)),
            )
            val (timetableJson, timetableCached) = cachedOrFetch(
                "timetable_$cacheSuffix",
                "hisTimetable",
                mapOf(
                    "AY" to today.year.toString(),
                    "GRADE" to settings.grade.toString(),
                    "CLASS_NM" to settings.classNumber.toString(),
                    "TI_FROM_YMD" to monthFrom.format(DATE),
                    "TI_TO_YMD" to monthTo.format(DATE),
                ),
            )
            NeisState(
                meals = parseMeals(mealJson),
                events = parseEvents(scheduleJson),
                timetableByDate = parseTimetable(timetableJson, settings),
                isFromCache = mealCached || scheduleCached || timetableCached,
                grade = settings.grade,
                classNumber = settings.classNumber,
                timetableSource = "neis",
                loadedMonths = setOf(YearMonth.from(today)),
            )
        } catch (error: Exception) {
            Log.e("NeisRepository", "NEIS synchronization failed", error)
            NeisState(
                errorMessage = error.message ?: "NEIS 데이터를 불러오지 못했습니다.",
                grade = settings.grade,
                classNumber = settings.classNumber,
            )
        }
    }

    /**
     * Cloudflare에서 한 번이라도 받은 병합 응답을 즉시 반환한다.
     * 알림은 네트워크를 기다리지 않고 이 시간표로 현재 교시를 계산할 수 있다.
     */
    fun loadCachedServerState(settings: UserSettings, date: LocalDate): NeisState? {
        val cacheSuffix = "${settings.grade}_${settings.classNumber}_${date.year}_${date.monthValue}"
        val body = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
            .getString("server_$cacheSuffix", null)
            ?: return null
        return parseServerState(body, settings, date)?.copy(isFromCache = true)
    }

    private fun loadFromServer(
        settings: UserSettings,
        today: LocalDate,
        forceSync: Boolean,
        prefs: SharedPreferences,
        cacheKey: String,
    ): NeisState? {
        val freshBody = runCatching {
            val query = "grade=${settings.grade}&classNumber=${settings.classNumber}&year=${today.year}&month=${today.monthValue}&sync=$forceSync"
            val connection = URL("${BuildConfig.CLOUDFLARE_API_BASE_URL}/v1/neis?$query").openConnection() as HttpURLConnection
            connection.run {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 25_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "GunpoSchoolAndroid/0.2")
                try {
                    if (responseCode !in 200..299) error("학교 데이터 서버 HTTP $responseCode")
                    inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally {
                    disconnect()
                }
            }
        }.getOrNull()

        if (freshBody != null) {
            parseServerState(freshBody, settings, today)?.let { state ->
                // D1 임시 시간표가 병합된 응답까지 저장해야 오프라인 알림에서도 같은 시간표를 쓸 수 있다.
                prefs.edit().putString(cacheKey, freshBody).apply()
                return state
            }
        }

        val cachedBody = prefs.getString(cacheKey, null) ?: return null
        return parseServerState(cachedBody, settings, today)?.copy(isFromCache = true)
    }

    private fun parseServerState(
        body: String,
        settings: UserSettings,
        today: LocalDate,
    ): NeisState? = runCatching {
        val result = JSONObject(body)
        val mealJson = result.optString("mealJson").takeIf { it.isNotBlank() } ?: return@runCatching null
        val scheduleJson = result.optString("scheduleJson").takeIf { it.isNotBlank() } ?: return@runCatching null
        val timetableJson = result.optString("timetableJson").takeIf { it.isNotBlank() } ?: return@runCatching null
        val temporaryDates = buildSet {
            val dates = result.optJSONObject("temporaryFallback")?.optJSONArray("dates") ?: JSONArray()
            for (index in 0 until dates.length()) {
                runCatching { LocalDate.parse(dates.getString(index)) }.getOrNull()?.let(::add)
            }
        }
        NeisState(
            meals = parseMeals(mealJson),
            events = parseEvents(scheduleJson),
            timetableByDate = parseTimetable(timetableJson, settings),
            isFromCache = result.optBoolean("cached"),
            grade = settings.grade,
            classNumber = settings.classNumber,
            timetableSource = result.optString("timetableSource").takeIf { it.isNotBlank() },
            temporaryTimetableDates = temporaryDates,
            loadedMonths = setOf(YearMonth.from(today)),
        )
    }.getOrNull()

    private fun request(endpoint: String, specific: Map<String, String>): String {
        val params = linkedMapOf(
            "Type" to "json",
            "pIndex" to "1",
            "pSize" to "1000",
            "ATPT_OFCDC_SC_CODE" to OFFICE_CODE,
            "SD_SCHUL_CODE" to SCHOOL_CODE,
        ).apply {
            putAll(specific)
        }
        val query = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }
        val connection = URL("$BASE_URL/$endpoint?$query").openConnection() as HttpURLConnection
        return connection.run {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "GunpoSchoolAndroid/0.1")
            try {
                if (responseCode !in 200..299) error("NEIS HTTP $responseCode")
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.also { validateResponse(it, endpoint) }
            } finally { disconnect() }
        }
    }

    private fun validateResponse(json: String, endpoint: String) {
        val root = JSONObject(json)
        root.optJSONObject("RESULT")?.let { result ->
            val code = result.optString("CODE")
            if (code != "INFO-000" && code != "INFO-200") error("$endpoint: ${result.optString("MESSAGE")}")
        }
    }

    private fun rows(json: String, key: String): JSONArray {
        val root = JSONObject(json)
        val blocks = root.optJSONArray(key) ?: return JSONArray()
        for (index in 0 until blocks.length()) {
            blocks.optJSONObject(index)?.optJSONArray("row")?.let { return it }
        }
        return JSONArray()
    }

    private fun parseMeals(json: String): List<Meal> {
        val result = mutableListOf<Meal>()
        val rows = rows(json, "mealServiceDietInfo")
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val date = runCatching { LocalDate.parse(row.getString("MLSV_YMD"), DATE) }.getOrNull() ?: continue
            val menu = row.optString("DDISH_NM").split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))
                .map { it.replace(Regex("\\s*\\([^)]*\\)"), "").trim().trimStart('㉠', '☆', '★') }
                .filter { it.isNotBlank() }
            result += Meal(date, row.optString("MMEAL_SC_NM", "중식"), menu, row.optString("CAL_INFO"))
        }
        return result.sortedWith(compareBy<Meal> { it.date }.thenBy { it.type })
    }

    private fun parseEvents(json: String): List<AcademicEvent> {
        data class EventDay(val date: LocalDate, val title: String, val scope: String, val grades: Set<Int>)
        val days = mutableListOf<EventDay>()
        val rows = rows(json, "SchoolSchedule")
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val date = runCatching { LocalDate.parse(row.getString("AA_YMD"), DATE) }.getOrNull() ?: continue
            val grades = buildSet {
                if (row.optString("ONE_GRADE_EVENT_YN") == "Y") add(1)
                if (row.optString("TW_GRADE_EVENT_YN") == "Y") add(2)
                if (row.optString("THREE_GRADE_EVENT_YN") == "Y") add(3)
            }
            val title = row.optString("EVENT_NM").trim()
            if (title.isNotBlank()) days += EventDay(date, title, if (grades.isEmpty() || grades.size == 3) "전학년" else grades.sorted().joinToString("·") { "${it}학년" }, grades)
        }
        val combined = mutableListOf<AcademicEvent>()
        days.sortedWith(compareBy<EventDay> { it.date }.thenBy { it.title }).forEach { day ->
            val previous = combined.lastOrNull()
            if (previous != null && previous.title == day.title && previous.grades == day.grades && previous.end.plusDays(1) == day.date) {
                combined[combined.lastIndex] = previous.copy(end = day.date)
            } else {
                combined += AcademicEvent(day.date, title = day.title, scope = day.scope, grades = day.grades)
            }
        }
        return combined
    }

    private fun parseTimetable(json: String, settings: UserSettings): Map<LocalDate, List<Lesson>> {
        val starts = listOf(500, 560, 620, 680, 790, 850, 910, 980)
        val byDate = linkedMapOf<LocalDate, MutableList<Lesson>>()
        val rows = rows(json, "hisTimetable")
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val rowGrade = row.optString("GRADE").toIntOrNull()
            val rowClassNumber = row.optString("CLASS_NM").toIntOrNull()
            if (rowGrade != null && rowGrade != settings.grade) continue
            if (rowClassNumber != null && rowClassNumber != settings.classNumber) continue
            val date = runCatching { LocalDate.parse(row.getString("ALL_TI_YMD"), DATE) }.getOrNull() ?: continue
            val period = row.optInt("PERIO")
            val subject = TimetableSubjectFormatter.display(
                subject = row.optString("ITRT_CNTNT").trim(),
                grade = rowGrade ?: settings.grade,
                date = date,
                period = period,
            )
            if (period !in 1..starts.size || subject.isBlank()) continue
            val start = starts[period - 1]
            byDate.getOrPut(date) { mutableListOf() } += Lesson(period, subject, start, start + 50, "${settings.grade}학년 ${settings.classNumber}반")
        }
        return byDate.mapValues { (_, lessons) -> lessons.distinctBy { it.period }.sortedBy { it.period } }
    }
}
