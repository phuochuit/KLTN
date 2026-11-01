import test from 'node:test';
import assert from 'node:assert/strict';
import {
  parseVietnamShiftDateTime,
  shiftIntervalError,
} from '../../main/resources/static/js/account-management-live.mjs';

test('Vietnamese shift date-time format maps to the existing backend local-ISO contract', () => {
  const startsAt = parseVietnamShiftDateTime('31/10/2026 13:45');
  assert.equal(startsAt, '2026-10-31T13:45');
  assert.equal(parseVietnamShiftDateTime('29/02/2024 00:05'), '2024-02-29T00:05');
  assert.equal(parseVietnamShiftDateTime('31/10/2026 22:00'), '2026-10-31T22:00');
  assert.equal(shiftIntervalError(startsAt, parseVietnamShiftDateTime('01/11/2026 13:45')), null);
  assert.equal(
    shiftIntervalError(startsAt, parseVietnamShiftDateTime('01/11/2026 13:46')),
    'Một ca làm không được dài quá 24 giờ.',
  );
});

test('Vietnamese shift date-time parser rejects impossible dates, invalid 24-hour times, and ambiguous formats', () => {
  for (const value of [
    '31/02/2026 13:45',
    '29/02/2025 13:45',
    '31/10/2026 24:00',
    '31/10/2026 13:60',
    '1/10/2026 13:45',
    '10/31/2026 01:45 PM',
  ]) assert.equal(parseVietnamShiftDateTime(value), null, value);
});

test('shift interval accepts exactly 24 hours using the existing Vietnam-local form contract', () => {
  assert.equal(shiftIntervalError('2026-10-29T08:00', '2026-10-30T08:00'), null);
  assert.equal(shiftIntervalError('2026-10-29T23:30', '2026-10-30T00:15'), null);
});

test('shift interval rejects an end at or before the start', () => {
  assert.equal(
    shiftIntervalError('2026-10-29T08:00', '2026-10-29T08:00'),
    'Thời gian kết thúc phải sau thời gian bắt đầu.',
  );
  assert.equal(
    shiftIntervalError('2026-10-29T09:00', '2026-10-29T08:00'),
    'Thời gian kết thúc phải sau thời gian bắt đầu.',
  );
});

test('shift interval rejects durations longer than the existing 24-hour server maximum', () => {
  assert.equal(
    shiftIntervalError('2026-10-29T08:00', '2026-10-30T08:01'),
    'Một ca làm không được dài quá 24 giờ.',
  );
});
