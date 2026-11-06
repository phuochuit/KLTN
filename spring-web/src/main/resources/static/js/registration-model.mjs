export const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

const QR_FIELDS = [
  ['citizenId', 'Số CCCD'],
  ['oldCitizenId', 'Số CMND cũ'],
  ['fullName', 'Họ và tên'],
  ['dateOfBirth', 'Ngày sinh'],
  ['gender', 'Giới tính'],
  ['permanentAddress', 'Địa chỉ thường trú'],
  ['cccdIssueDate', 'Ngày cấp CCCD'],
];

export function imageFileError(file) {
  if (!file) return null;
  if (file.size > MAX_IMAGE_BYTES) return 'Ảnh vượt quá 10 MB. Hãy chọn ảnh nhỏ hơn.';
  if (!file.type?.startsWith('image/')) return 'Tệp đã chọn phải là hình ảnh.';
  return null;
}

export function qrFieldValues(data = {}) {
  return QR_FIELDS.flatMap(([name, label]) => {
    const value = String(data[name] ?? '').trim();
    return value ? [{ name, label, value }] : [];
  });
}

export function parseVietnameseDate(value) {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(String(value ?? ''));
  if (!match) return null;
  const [, dayText, monthText, yearText] = match;
  const day = Number(dayText);
  const month = Number(monthText);
  const year = Number(yearText);
  if (year < 1 || month < 1 || month > 12) return null;
  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1];
  if (day < 1 || day > daysInMonth) return null;
  return `${yearText}-${monthText}-${dayText}`;
}

export function formatVietnameseDate(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value ?? ''));
  if (!match || !parseVietnameseDate(`${match[3]}/${match[2]}/${match[1]}`)) return '';
  return `${match[3]}/${match[2]}/${match[1]}`;
}

export function formatVietnameseDateInput(value) {
  const digits = String(value ?? '').replace(/\D/g, '').slice(0, 8);
  if (digits.length <= 2) return digits;
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`;
}

export function classifyRegistrationWrite({ status = 0, redirected = false, pathname = '' } = {}) {
  const path = pathname.replace(/\/+$/, '');
  if (redirected && /\/login$/.test(path)) return 'session';
  if (status === 400) return 'validation-error';
  if (status === 409) return 'conflict';
  if (status === 413) return 'upload-error';
  if (status === 401 || status === 403) return 'blocked';
  if (status === 0 || status >= 500) return 'unknown';
  if (redirected && /\/(cards|vehicles)$/.test(path) && status >= 200 && status < 300) return 'success';
  return 'unknown';
}
