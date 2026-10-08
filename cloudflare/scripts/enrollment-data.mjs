import {createHash, createHmac, createCipheriv, randomBytes} from 'node:crypto';

// RFC 4180 fields, including BOM, CRLF, embedded commas/newlines and escaped quotes.
export function parseCsv(text) {
  text = text.replace(/^\uFEFF/, '');
  const rows = [];
  let row = [], field = '', quoted = false, closed = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') { quoted = false; closed = true; }
      else field += c;
    } else if (c === '"' && !field && !closed) quoted = true;
    else if (c === ',' || c === '\n' || c === '\r') {
      row.push(field); field = ''; closed = false;
      if (c !== ',') {
        if (row.some(value => value !== '')) rows.push(row);
        row = [];
        if (c === '\r' && text[i + 1] === '\n') i++;
      }
    } else {
      if (closed || c === '"') throw new Error('Malformed CSV quoting');
      field += c;
    }
  }
  if (quoted) throw new Error('Unterminated CSV field');
  if (field || row.length || closed) { row.push(field); rows.push(row); }
  return rows;
}

export function lookupHash(secret, id, name) {
  const key = createHash('sha256').update(`enrollments:lookup:${secret}`).digest();
  return createHmac('sha256', key).update(`${id.trim()}\0${name.trim().normalize('NFC')}`).digest('hex');
}

export function buildImport(csv, secret) {
  if (Buffer.from(secret, 'base64').length !== 32) throw new Error('Enrollment key must be 32 bytes in base64');
  const [header, ...rows] = parseCsv(csv);
  const required = ['id', 'student_id', 'student_name', 'block', 'subject', 'class_num'];
  if (!header || new Set(header).size !== header.length || required.some(c => !header.includes(c))) {
    throw new Error('CSV must have id, student_id, student_name, block, subject, class_num columns');
  }
  if (!rows.length) throw new Error('CSV has no enrollment rows');
  const students = new Map();
  const rowIds = new Set();
  const blocks = new Set();
  for (const [index, values] of rows.entries()) {
    const invalid = () => { throw new Error(`Invalid or conflicting enrollment at CSV row ${index + 2}`); };
    if (values.length !== header.length) invalid();
    const record = Object.fromEntries(header.map((name, i) => [name, values[i].trim().normalize('NFC')]));
    const {id, student_id: number, student_name: name, block, subject, class_num: classNumber} = record;
    if (!id || rowIds.has(id) || !/^[1-3]\d{4}$/.test(number) || !name || name.length > 40 ||
        /[\u0000-\u001f\u007f]/.test(name) || !/^[1-3][A-Z]$/.test(block) ||
        block[0] !== number[0] || !subject || subject.length > 100 || !/^\d+$/.test(classNumber) ||
        Number(classNumber) < 1 || Number(classNumber) > 99) invalid();
    rowIds.add(id); blocks.add(block);
    const student = students.get(number) || {id: number, name, assignments: new Map()};
    if (student.name !== name || student.assignments.has(block)) invalid();
    student.assignments.set(block, {block, subject, classNumber: Number(classNumber)});
    students.set(number, student);
  }
  for (const student of students.values()) {
    if (student.assignments.size !== blocks.size) throw new Error('Every student must have one enrollment in each block');
  }
  const dataset = createHash('sha256').update(csv).digest('hex');
  const identityKey = createHash('sha256').update(`enrollments:identity:${secret}`).digest();
  const quote = value => "'" + String(value).replaceAll("'", "''") + "'";
  const sql = ['-- Generated encrypted enrollment import. Do not commit this file.'];
  for (const student of students.values()) {
    const hash = lookupHash(secret, student.id, student.name);
    const iv = randomBytes(12);
    const cipher = createCipheriv('aes-256-gcm', identityKey, iv);
    const ciphertext = Buffer.concat([
      cipher.update(JSON.stringify({id: student.id, name: student.name}), 'utf8'), cipher.final(), cipher.getAuthTag(),
    ]);
    sql.push(`INSERT INTO enrollment_students(dataset_id, lookup_hash, identity_ciphertext, identity_iv) VALUES (${quote(dataset)}, ${quote(hash)}, ${quote(ciphertext.toString('base64'))}, ${quote(iv.toString('base64'))}) ON CONFLICT(dataset_id, lookup_hash) DO UPDATE SET identity_ciphertext=excluded.identity_ciphertext, identity_iv=excluded.identity_iv;`);
    for (const item of student.assignments.values()) {
      sql.push(`INSERT INTO enrollments(dataset_id, lookup_hash, block, subject, class_num) VALUES (${quote(dataset)}, ${quote(hash)}, ${quote(item.block)}, ${quote(item.subject)}, ${item.classNumber}) ON CONFLICT(dataset_id, lookup_hash, block) DO UPDATE SET subject=excluded.subject, class_num=excluded.class_num;`);
    }
  }
  // Activate only after all rows exist. Partial imports leave the previous data active.
  sql.push(`INSERT INTO enrollment_dataset(id, source_sha256, imported_at, student_count, enrollment_count) VALUES (1, ${quote(dataset)}, ${quote(new Date().toISOString())}, ${students.size}, ${rows.length}) ON CONFLICT(id) DO UPDATE SET source_sha256=excluded.source_sha256, imported_at=excluded.imported_at, student_count=excluded.student_count, enrollment_count=excluded.enrollment_count;`);
  sql.push(`DELETE FROM enrollments WHERE dataset_id <> ${quote(dataset)};`);
  sql.push(`DELETE FROM enrollment_students WHERE dataset_id <> ${quote(dataset)};`);
  return {sql: sql.join('\n') + '\n', studentCount: students.size, rowCount: rows.length, blocks: [...blocks].sort(), dataset};
}
