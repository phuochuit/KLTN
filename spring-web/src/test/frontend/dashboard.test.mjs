import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { periodQuery, revenueParts, localDate, readDashboardState, dashboardSearch } from '../../main/resources/static/js/dashboard-model.mjs';

test('revenue parts preserve zero and reject missing, negative or inconsistent data', () => {
  assert.deepEqual(revenueParts({ visitRevenue: 30, subscriptionRevenue: 70, totalRevenue: 100 }), { visit: 30, subscription: 70, total: 100, visitPercent: 30 });
  assert.equal(revenueParts({ visitRevenue: 0, subscriptionRevenue: 0, totalRevenue: 0 }).visitPercent, 0);
  for (const value of [{}, { visitRevenue: -1, subscriptionRevenue: 0, totalRevenue: -1 }, { visitRevenue: 2, subscriptionRevenue: 1, totalRevenue: 4 }]) assert.throws(() => revenueParts(value));
});
test('period query rejects invalid dates and builds explicit period parameters', () => {
  assert.equal(periodQuery({ type: 'month', month: '2', year: '2026' }).toString(), 'type=month&year=2026&month=2');
  assert.equal(periodQuery({ type: 'range', from: '2026-01-01', to: '2026-01-31' }).get('to'), '2026-01-31');
  for (const value of [{ type: 'range', from: '2026-02-30', to: '2026-03-02' }, { type: 'range', from: '2026-02-02', to: '2026-01-01' }, { type: 'month', year: '2026', month: '13' }]) assert.throws(() => periodQuery(value));
  assert.equal(localDate(new Date(2026, 0, 1)), '2026-01-01');
});
test('Dashboard keeps its own CSS entry and snapshot KPI outside filter rendering', async () => {
  const html = await readFile(new URL('../../main/resources/templates/dashboard.html', import.meta.url), 'utf8');
  assert.match(html, /dashboard-ui.css/);
  assert.doesNotMatch(html, /css\/app.css/);
});
test('Dashboard URL round trip keeps period, query, source, sort and page', () => {
  const state = readDashboardState('?type=range&from=2026-01-01&to=2026-01-31&q=TEST&source=VISIT&sort=oldest&page=2&size=50&months=6', new Date(2026,9,26));
  assert.equal(state.page, 2); assert.equal(state.q, 'TEST'); assert.equal(state.months, 6); assert.equal(state.size, 10);
  assert.equal(readDashboardState('?size=500').size, 10);
  assert.equal(readDashboardState('').size, 10);
  assert.equal(new URLSearchParams(dashboardSearch(state)).has('size'), false);
  assert.deepEqual(readDashboardState('?' + dashboardSearch(state), new Date(2026,9,26)), state);
  for (const query of ['?page=-1','?source=UNKNOWN','?sort=random','?type=range&from=2026-02-30&to=2026-03-01']) assert.throws(() => readDashboardState(query));
});
