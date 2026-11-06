const isoDate = /^(\d{4})-(\d{2})-(\d{2})$/;
const vietnameseDate = /^(\d{2})\/(\d{2})\/(\d{4})$/;

function validDate(year, month, day) {
  if (year < 1 || year > 9999 || month < 1 || month > 12 || day < 1 || day > 31) return null;
  const date = new Date(0);
  date.setUTCHours(0, 0, 0, 0);
  date.setUTCFullYear(year, month - 1, day);
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
    ? date
    : null;
}

function dateText(date) {
  return `${String(date.getUTCFullYear()).padStart(4, '0')}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`;
}

export function formatVietnameseDate(value) {
  const match = isoDate.exec(String(value ?? ''));
  if (!match) return '';
  const [, year, month, day] = match.map(Number);
  return validDate(year, month, day) ? `${match[3]}/${match[2]}/${match[1]}` : '';
}

export function parseVietnameseDate(value) {
  const match = vietnameseDate.exec(String(value ?? ''));
  if (!match) return null;
  const [, day, month, year] = match.map(Number);
  return validDate(year, month, day) ? `${match[3]}-${match[2]}-${match[1]}` : null;
}

export function inclusiveEndDate(startDate, durationDays) {
  const match = isoDate.exec(String(startDate ?? ''));
  if (!match || !Number.isInteger(durationDays) || durationDays < 1 || durationDays > 366) return null;
  const [, year, month, day] = match.map(Number);
  const date = validDate(year, month, day);
  if (!date) return null;
  date.setUTCDate(date.getUTCDate() + durationDays - 1);
  return date.getUTCFullYear() <= 9999 ? dateText(date) : null;
}

export function classifyCardWrite({ status = 0, redirected = false, pathname = '' } = {}) {
  const path = String(pathname).replace(/\/+$/, '');
  if (redirected && /\/login$/.test(path)) return 'session';
  if ([400, 409, 413].includes(status)) return 'retry-safe';
  if ([401, 403].includes(status)) return 'blocked';
  if (status === 0 || status >= 500) return 'unknown';
  if (redirected && status >= 200 && status < 300 && /\/cards$/.test(path)) return 'success';
  return 'unknown';
}

export function cardSuccessMessage(document) {
  return document.querySelector('.cards-feedback-success p')?.textContent.trim().slice(0, 400) || null;
}
