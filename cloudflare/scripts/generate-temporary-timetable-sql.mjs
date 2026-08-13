import {execFileSync} from "node:child_process";
import {readFileSync, unlinkSync, writeFileSync} from "node:fs";
import {tmpdir} from "node:os";
import {basename, resolve} from "node:path";

const [pdfArgument, outputArgument] = process.argv.slice(2);
if (!pdfArgument || !outputArgument) {
  throw new Error("Usage: node generate-temporary-timetable-sql.mjs <source.pdf> <output.sql>");
}

const pdfPath = resolve(pdfArgument);
const outputPath = resolve(outputArgument);
const htmlPath = `${tmpdir()}/gphs-temporary-timetable-${process.pid}.html`;
execFileSync("pdftotext", ["-bbox-layout", pdfPath, htmlPath]);

const html = readFileSync(htmlPath, "utf8");
unlinkSync(htmlPath);
const pages = [...html.matchAll(/<page[^>]*>([\s\S]*?)<\/page>/g)].map((match) => match[1]);
if (pages.length !== 30) throw new Error(`Expected 30 timetable pages, found ${pages.length}`);

const dayCenters = [173, 240, 307, 375, 442];
const periodCenters = [147, 190, 232, 275, 317, 360, 402];
const nearestIndex = (value, centers, tolerance) => {
  const distances = centers.map((center) => Math.abs(center - value));
  const minimum = Math.min(...distances);
  return minimum <= tolerance ? distances.indexOf(minimum) : -1;
};
const escapeSql = (value) => `'${value.replaceAll("'", "''")}'`;
const rows = [];

pages.forEach((page, pageIndex) => {
  const words = [...page.matchAll(
    /<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">([^<]+)<\/word>/g,
  )].map((match) => ({
    x: (Number(match[1]) + Number(match[3])) / 2,
    y: (Number(match[2]) + Number(match[4])) / 2,
    text: match[5],
  }));
  const classLabel = words.find((word) => /^\d+-\d+$/.test(word.text))?.text;
  if (!classLabel) throw new Error(`Missing class label on page ${pageIndex + 1}`);
  const [grade, classNumber] = classLabel.split("-").map(Number);

  words.forEach((word) => {
    const dayIndex = nearestIndex(word.x, dayCenters, 28);
    const periodIndex = nearestIndex(word.y, periodCenters, 12);
    if (dayIndex < 0 || periodIndex < 0) return;
    rows.push({grade, classNumber, dayOfWeek: dayIndex + 1, period: periodIndex + 1, subject: word.text});
  });
});

const counts = new Map();
rows.forEach((row) => counts.set(row.grade, (counts.get(row.grade) || 0) + 1));
const expected = new Map([[1, 300], [2, 290], [3, 300]]);
expected.forEach((count, grade) => {
  if (counts.get(grade) !== count) {
    throw new Error(`Grade ${grade}: expected ${count} cells, found ${counts.get(grade) || 0}`);
  }
});

const sourceName = basename(pdfPath);
const values = rows.map((row) => `  (2026, 2, ${row.grade}, ${row.classNumber}, ${row.dayOfWeek}, ${row.period}, ${escapeSql(row.subject)}, '2026-08-13', '2027-02-28', ${escapeSql(sourceName)})`);
const inserts = [];
for (let index = 0; index < values.length; index += 100) {
  inserts.push(`INSERT INTO temporary_timetable (
  academic_year, semester, grade, class_number, day_of_week, period,
  subject, effective_from, effective_to, source_name
) VALUES
${values.slice(index, index + 100).join(",\n")};`);
}
const sql = `CREATE TABLE IF NOT EXISTS temporary_timetable (
  academic_year INTEGER NOT NULL,
  semester INTEGER NOT NULL,
  grade INTEGER NOT NULL,
  class_number INTEGER NOT NULL,
  day_of_week INTEGER NOT NULL CHECK(day_of_week BETWEEN 1 AND 5),
  period INTEGER NOT NULL CHECK(period BETWEEN 1 AND 8),
  subject TEXT NOT NULL,
  effective_from TEXT NOT NULL,
  effective_to TEXT NOT NULL,
  source_name TEXT NOT NULL,
  PRIMARY KEY (academic_year, semester, grade, class_number, day_of_week, period)
);

CREATE INDEX IF NOT EXISTS idx_temporary_timetable_lookup
ON temporary_timetable(academic_year, grade, class_number, effective_from, effective_to);

DELETE FROM temporary_timetable WHERE academic_year = 2026 AND semester = 2;

${inserts.join("\n\n")}
`;

writeFileSync(outputPath, sql);
console.log(`Generated ${rows.length} timetable cells in ${outputPath}`);
