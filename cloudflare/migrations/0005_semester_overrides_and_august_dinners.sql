CREATE TABLE IF NOT EXISTS timetable_date_overrides (
  academic_year INTEGER NOT NULL,
  grade INTEGER NOT NULL,
  class_number INTEGER NOT NULL DEFAULT 0,
  lesson_date TEXT NOT NULL,
  period INTEGER NOT NULL CHECK(period BETWEEN 1 AND 8),
  subject TEXT NOT NULL,
  source_name TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (academic_year, grade, class_number, lesson_date, period)
);

CREATE INDEX IF NOT EXISTS idx_timetable_date_overrides_lookup
ON timetable_date_overrides(grade, class_number, lesson_date);

-- 2학년 공통 자율 시간: 월·금 7교시, 수 4·5교시.
WITH classes(class_number) AS (
  VALUES (1), (2), (3), (4), (5), (6), (7), (8), (9), (10)
), slots(day_of_week, period, subject) AS (
  VALUES (1, 7, '자율'), (3, 4, '자율'), (3, 5, '자율'), (5, 7, '자율')
)
INSERT OR REPLACE INTO temporary_timetable (
  academic_year, semester, grade, class_number, day_of_week, period,
  subject, effective_from, effective_to, source_name
)
SELECT 2026, 2, 2, classes.class_number, slots.day_of_week, slots.period,
       slots.subject, '2026-08-13', '2027-01-06',
       '2026학년도 2학기 2학년 공통 시간표 보완'
FROM classes CROSS JOIN slots;

-- NEIS 학사일정에서 동아리활동 날짜를 확인하고, 정상수업 수요일은 교육으로 구분했다.
INSERT OR REPLACE INTO timetable_date_overrides (
  academic_year, grade, class_number, lesson_date, period, subject, source_name, updated_at
) VALUES
  (2026, 2, 0, '2026-08-19', 6, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-08-19', 7, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-08-26', 6, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-08-26', 7, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-09-09', 6, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-09-09', 7, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-09-16', 6, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-09-16', 7, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-09-23', 6, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-09-23', 7, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-10-07', 6, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-10-07', 7, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-10-14', 6, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-10-14', 7, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-10-28', 6, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-10-28', 7, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-11-04', 6, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-11-04', 7, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-11-11', 6, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-11-11', 7, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-11-18', 6, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-11-18', 7, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-11-25', 6, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-11-25', 7, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-12-02', 6, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-12-02', 7, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-12-09', 6, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-12-09', 7, '교육', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-12-23', 6, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-12-23', 7, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-12-30', 6, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000),
  (2026, 2, 0, '2026-12-30', 7, '동아리', 'NEIS SchoolSchedule', unixepoch() * 1000);

-- 2026년 8월 위탁 석식 식단표. 대체휴일(8/17)은 식사가 없어 제외한다.
INSERT OR REPLACE INTO contract_dinners (
  meal_date, menu_json, grades, source, updated_at, meal_type
) VALUES
  ('2026-08-13', '["차조밥","설렁탕","통새우살까스","메추리알장조림","실곤약야채무침","배추겉절이","초코우유","샐러드바"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-14', '["한방장각탕(장각+녹두밥)","메밀전병","돈채버섯볶음","사과오이초무침","배추김치","수박주스","샐러드바"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-18', '["차조밥","닭육개장","코다리강정","까르보크림떡볶이","오이생채","배추김치","한입약과","샐러드바"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-19', '["백미밥","도토리묵냉국","돈육불고기","상추쌈/쌈장","어묵채콩나물무침","배추김치","초코츄러스","샐러드바"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-20', '["차조밥","대파육개장","떡갈비육전","연근견과조림","시금치나물","깍두기","망고스틱","샐러드바"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-21', '["백미밥","팽이미소국","치즈불닭볶음","고기짜조/소스","표고버섯쑥갓무침","배추김치","푸딩","샐러드바"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-24', '["차조밥","냉소바우동","고구마닭볶음탕","새우튀김","짜사이무침","깍두기","탱크보이","샐러드바"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-25', '["기장밥","호박두부맑은국","로제순살찜닭","야끼우동","양배추사과초무침","배추김치","떠먹는피자","샐러드바"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-26', '["산나물비빔밥","무채된장국","햄벅스테이크","마카로니건포도샐러드","배추김치","딸기우유"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-27', '["흑미밥","등뼈감자탕","갈비만두/비빔채소","달걀찜","타코야끼","깍두기","마시는요거트"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-28', '["혼합잡곡밥","차돌순두부찌개","연어까스/갈릭디핑","떡햄치즈그라탕","치커리상추매실무침","배추김치","팝콘"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식'),
  ('2026-08-31', '["차조밥","냉소바우동","고구마닭볶음탕","새우튀김","짜사이무침","깍두기","탱크보이","샐러드바"]', '1,2,3', '2026년8월석식식단표(위탁)001.jpg', unixepoch() * 1000, '석식');
