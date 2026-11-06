import test from 'node:test';
import assert from 'node:assert/strict';
import {
  installAccountManagementLive,
  parseVietnamShiftDateTime,
  shiftIntervalError,
} from '../../main/resources/static/js/account-management-live.mjs';

function deferred() {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
}

function liveScheduler(fetch) {
  const listeners = new Map();
  const intervals = [];
  const message = { textContent: '' };
  const retry = {
    textContent: '', disabled: false,
    setAttribute() {}, removeAttribute() {},
  };
  const status = {
    dataset: {},
    querySelector(selector) {
      return selector === '#live-refresh-message' ? message
        : selector === '#live-refresh-retry' ? retry : null;
    },
  };
  const documentRef = {
    hidden: true,
    activeElement: null,
    getElementById(id) { return id === 'live-refresh-status' ? status : null; },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    addEventListener(type, listener) { listeners.set(type, listener); },
  };
  const windowRef = {
    location: { href: 'http://localhost/account-management', assign() {}, reload() {} },
    fetch,
    setInterval(callback, delay) { intervals.push({ callback, delay }); return intervals.length; },
    setTimeout() { return 1; },
  };
  installAccountManagementLive({ documentRef, windowRef });
  return {
    documentRef, intervals, listeners, message, retry, status,
    tick() { return intervals[0].callback(); },
    visibilityChanged() { listeners.get('visibilitychange')(); },
    manualRefresh() {
      listeners.get('click')({ target: { closest: selector => selector === '#live-refresh-retry' ? retry : null } });
    },
  };
}

const staleResponse = { status: 500, ok: false, url: 'http://localhost/account-management/live', redirected: false };

test('hidden scheduler skips ticks, visibility and manual refresh then resumes one request without overlap', async () => {
  const held = deferred();
  const requests = [];
  const scheduler = liveScheduler((...args) => {
    requests.push(args);
    return requests.length === 1 ? held.promise : Promise.resolve(staleResponse);
  });

  await scheduler.tick(); await scheduler.tick(); await scheduler.tick();
  scheduler.visibilityChanged();
  scheduler.manualRefresh();
  assert.equal(requests.length, 0);

  scheduler.documentRef.hidden = false;
  scheduler.visibilityChanged();
  assert.equal(requests.length, 1);
  assert.equal(requests[0][0], '/account-management/live');
  assert.equal(requests[0][1].credentials, 'same-origin');
  assert.equal(requests[0][1].headers['X-Requested-With'], 'XMLHttpRequest');
  await scheduler.tick(); await scheduler.tick(); await scheduler.tick();
  assert.equal(requests.length, 1);

  held.resolve(staleResponse);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(scheduler.status.dataset.state, 'stale');
  await scheduler.tick();
  assert.equal(requests.length, 2);
});

test('an in-flight refresh completes while hidden and visibility immediately resumes after completion', async () => {
  const held = deferred();
  const requests = [];
  const scheduler = liveScheduler((...args) => {
    requests.push(args);
    return requests.length === 1 ? held.promise : Promise.resolve(staleResponse);
  });
  scheduler.documentRef.hidden = false;
  const firstTick = scheduler.tick();
  assert.equal(requests.length, 1);

  scheduler.documentRef.hidden = true;
  await scheduler.tick();
  assert.equal(requests.length, 1);
  held.resolve(staleResponse);
  await firstTick;
  assert.equal(scheduler.status.dataset.state, 'stale');
  await scheduler.tick();
  assert.equal(requests.length, 1);

  scheduler.documentRef.hidden = false;
  scheduler.visibilityChanged();
  assert.equal(requests.length, 2);
  await new Promise(resolve => setImmediate(resolve));
});

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
