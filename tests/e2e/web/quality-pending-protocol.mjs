import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { appendFileSync, writeFileSync } from 'node:fs';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium, expect } = require('@playwright/test');
const runArg = process.argv[2];
const stage = process.argv[3] ?? 'settings-pending';
const diagnosticMode = process.argv[4] ?? 'critical-only';
if (!runArg || !/^[a-z0-9-]+$/.test(stage)) throw new Error('Usage: node tests/e2e/web/quality-pending-protocol.mjs <fresh-active-run-directory> [fresh-stage]');
if (!['critical-only', 'with-legacy-history-diagnostic'].includes(diagnosticMode)) throw new Error('Unknown diagnostic mode.');

const { realpath, stat } = await import('node:fs/promises');
const runs = await realpath(path.join(root, '.artifacts/test-runs'));
const run = await realpath(path.resolve(runArg));
if (!run.startsWith(`${runs}${path.sep}`) || !(await stat(run)).isDirectory()) throw new Error('Run path must be inside .artifacts/test-runs.');
const port = Number((await readFile(path.join(run, 'server.port'), 'utf8')).trim());
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid service port marker.');
try { await readFile(path.join(run, 'stop')); throw new Error('Harness stop marker exists.'); } catch (error) { if (error.code !== 'ENOENT') throw error; }
const controllerText = (await readFile(path.join(run, 'controller.pid'), 'utf8')).trim();
if (!/^\d+$/.test(controllerText)) throw new Error('Invalid controller PID marker.');
const controllerPid = Number(controllerText);
let listenerProbe = 'listener-table';
try {
  const probeOutput = execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
    `$controller = Get-Process -Id ${controllerPid} -ErrorAction SilentlyContinue; if (!$controller) { exit 2 }; $listener = Get-NetTCPConnection -State Listen -LocalPort ${port} -ErrorAction SilentlyContinue; if (!$listener) { $client = New-Object System.Net.Sockets.TcpClient; $connected = $false; try { $pending = $client.BeginConnect('127.0.0.1', ${port}, $null, $null); try { if ($pending.AsyncWaitHandle.WaitOne(1500)) { $client.EndConnect($pending); $connected = $client.Connected } } finally { $pending.AsyncWaitHandle.Close() } } finally { $client.Close() }; if (!$connected) { exit 3 }; Write-Output 'loopback-connect' } else { Write-Output 'listener-table' }`],
  { encoding: 'utf8', timeout: 5000 });
  listenerProbe = probeOutput.trim();
} catch { throw new Error('Harness PID or actual loopback service listener is absent; do not restart from this helper.'); }

const output = path.join(run, stage);
await mkdir(output, { recursive: false });
const partialPath = path.join(output, 'pending-observations.jsonl');
writeFileSync(partialPath, '', { flag: 'wx' });
const tempDirectory = path.join(root, '.cache/test-temp', `${path.basename(run)}-${stage}`);
await mkdir(tempDirectory, { recursive: false });
const savedTemp = process.env.TEMP, savedTmp = process.env.TMP;
process.env.TEMP = tempDirectory;
process.env.TMP = tempDirectory;
const origin = `http://127.0.0.1:${port}`;
const observations = [];
const failures = [];
const partialCases = [];
const runtimeErrors = [];
const guards = { loginPostsForwarded: 0, settingsPostsStubbed: 0, otherMutationsBlocked: 0, externalRequestsBlocked: 0, financialPostsForwarded: 0 };
const preflight = { controllerPid, controllerAlive: true, servicePort: port, listenerVerified: true, listenerProbe, stopMarker: 'absent' };
const fixture = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
const harness = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java'), 'utf8');
const password = fixture.match(/PASSWORD = "([^"]+)"/)?.[1];
const username = harness.match(/MANAGER_USERNAME = "([^"]+)"/)?.[1];
if (!password || !username) throw new Error('Synthetic fixture unavailable.');

let browser = null;
let context = null;
let activePending = null;
let sourceBefore = null;
let sourceAfter = null;
let settingsPostCount = 0;
let runWatchdog;
let activeDiagnosticStep = { name: 'setup', location: 'helper initialization', category: 'setup' };
async function diagnosticStep(name, location, action, category = 'playwright-await') {
  activeDiagnosticStep = { name, location, category };
  record({ probe: 'recovery-diagnostic-step', outcome: 'STEP STARTED', ...activeDiagnosticStep });
  try {
    const value = await action();
    record({ probe: 'recovery-diagnostic-step', outcome: 'STEP COMPLETED', ...activeDiagnosticStep });
    return value;
  }
  catch (error) {
    error.diagnosticStep = activeDiagnosticStep;
    record({ probe: 'recovery-diagnostic-step', outcome: 'STEP FAILED', ...activeDiagnosticStep, errorKind: error?.name ?? 'Error' });
    throw error;
  }
}
function record(value) {
  observations.push(value);
  appendFileSync(partialPath, `${JSON.stringify(value)}\n`, { encoding: 'utf8' });
}
function assertProbe(id, ok, detail = {}) {
  record({ probe: id, outcome: ok ? 'PASS' : 'FAIL', ...detail });
  if (!ok) failures.push(id);
}
function deadline(promise, ms) {
  let timer;
  return Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('probe-timeout')), ms); })])
    .finally(() => clearTimeout(timer));
}
async function boundedCase(id, work, ms = 20000) {
  activeDiagnosticStep = { name: `${id}: start`, location: 'bounded case', category: 'case-start' };
  try {
    await deadline(work(), ms);
  } catch (error) {
    if (error?.name === 'TimeoutError' || error?.message === 'probe-timeout') {
      partialCases.push(id);
      record({ probe: id, outcome: 'NOT VERIFIED — BOUNDED CASE ENDED', step: activeDiagnosticStep.name,
        category: activeDiagnosticStep.category, location: activeDiagnosticStep.location, errorKind: error?.name ?? 'Error', deadlineMs: ms,
        note: 'A bounded browser/helper timeout is inconclusive, not an application defect.' });
    } else {
      assertProbe(id, false, { step: error?.diagnosticStep?.name ?? activeDiagnosticStep.name,
        category: error?.diagnosticStep?.category ?? activeDiagnosticStep.category,
        location: error?.diagnosticStep?.location ?? activeDiagnosticStep.location, errorKind: error?.name ?? 'Error' });
    }
  }
}
async function walk(folder, files) {
  for (const entry of await readdir(folder, { withFileTypes: true })) {
    const full = path.join(folder, entry.name);
    if (entry.isDirectory()) await walk(full, files);
    else if (entry.isFile()) files.push(full);
  }
}
async function sourceIdentity() {
  const files = [
    'tests/e2e/web/quality-pending-protocol.mjs',
    'spring-web/src/main/resources/templates/settings.html',
    'spring-web/src/main/resources/static/js/management-core.js',
    'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java',
    'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java',
  ].map(file => path.join(root, file));
  for (const folder of ['spring-web/src/main/java', 'spring-web/src/main/resources/static', 'spring-web/src/main/frontend',
    'spring-web/target/classes', 'spring-web/target/test-classes']) await walk(path.join(root, folder), files);
  const manifest = [];
  for (const file of [...new Set(files)].sort()) {
    const bytes = await readFile(file);
    manifest.push({ path: path.relative(root, file).replaceAll(path.sep, '/'), sha256: createHash('sha256').update(bytes).digest('hex') });
  }
  return { manifest };
}
function requestChecks(request, expected) {
  const params = new URLSearchParams(request.postData() ?? '');
  return {
    fieldPresence: {
      twoWheeler: params.has('defaultMaxTwoWheelers'),
      cars: params.has('defaultMaxCars'),
    },
    fieldValuesMatch: {
      twoWheeler: params.get('defaultMaxTwoWheelers') === expected.twoWheeler,
      cars: params.get('defaultMaxCars') === expected.cars,
    },
    csrfPresent: params.has('_csrf') && Boolean(params.get('_csrf')),
  };
}
function armPending(pathname, expected) {
  let markArrived, markDuplicate, release;
  const arrived = new Promise(resolve => { markArrived = resolve; });
  const duplicate = new Promise(resolve => { markDuplicate = resolve; });
  const released = new Promise(resolve => { release = resolve; });
  const state = { pathname, expected, requests: 0, checks: null, arrived, duplicate, released, markArrived, markDuplicate, release };
  activePending = state;
  return state;
}
async function attributes(cdp, nodes) {
  const values = {};
  for (const [key, nodeId] of Object.entries(nodes)) {
    const { attributes: flat = [] } = await cdp.send('DOM.getAttributes', { nodeId });
    const map = Object.fromEntries(Array.from({ length: flat.length / 2 }, (_, i) => [flat[i * 2], flat[i * 2 + 1]]));
    values[key] = {
      disabled: Object.hasOwn(map, 'disabled'),
      readonly: Object.hasOwn(map, 'readonly'),
      ariaBusy: map['aria-busy'] ?? null,
      state: map['data-state'] ?? null,
    };
  }
  return values;
}
async function nativeHeldProbe(page, viewport) {
  await page.setViewportSize(viewport);
  await page.goto(`${origin}/settings`, { waitUntil: 'load', timeout: 5000 });
  const two = page.locator('#twoWheelerLimit'), cars = page.locator('#carLimit');
  const expected = { twoWheeler: String(Number(await two.inputValue()) + 1), cars: await cars.inputValue() };
  await two.fill(expected.twoWheeler);
  const button = page.locator('#saveLimitButton');
  if (await button.isDisabled() || await page.locator('#limitForm :invalid').count()) throw new Error('SETTINGS_FORM_NOT_SUBMITTABLE');
  await button.scrollIntoViewIfNeeded();
  const box = await button.boundingBox();
  if (!box) throw new Error('SUBMIT_BOUNDS_MISSING');

  const cdp = await context.newCDPSession(page);
  await cdp.send('DOM.enable');
  const rootNode = (await cdp.send('DOM.getDocument', { depth: 0, pierce: true })).root.nodeId;
  const selectors = { form: '#limitForm', button: '#saveLimitButton', reset: '#resetLimitButton', status: '#limitDraft', two: '#twoWheelerLimit', cars: '#carLimit' };
  const nodes = {};
  for (const [key, selector] of Object.entries(selectors)) nodes[key] = (await cdp.send('DOM.querySelector', { nodeId: rootNode, selector })).nodeId;
  if (Object.values(nodes).some(id => !id)) throw new Error('MISSING_OBSERVER_NODE');
  const events = [];
  cdp.on('DOM.attributeModified', event => {
    const target = Object.entries(nodes).find(([, id]) => id === event.nodeId)?.[0];
    if (target && ['disabled', 'readonly', 'aria-busy', 'data-state'].includes(event.name))
      events.push({ target, name: event.name, value: event.value });
  });
  cdp.on('DOM.attributeRemoved', event => {
    const target = Object.entries(nodes).find(([, id]) => id === event.nodeId)?.[0];
    if (target && ['disabled', 'readonly', 'aria-busy', 'data-state'].includes(event.name))
      events.push({ target, name: event.name, removed: true });
  });

  const requestWait = page.waitForRequest(request => request.method() === 'POST' && new URL(request.url()).pathname === '/settings', { timeout: 5000 });
  const responseWait = page.waitForResponse(response => response.status() === 204 && response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/settings', { timeout: 5000 });
  const pending = armPending('/settings', expected);
  let responseStatus = null, observed = null, duplicateObserved = 'NOT VERIFIED';
  try {
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await requestWait;
    await deadline(pending.arrived, 5000);
    await new Promise(resolve => setTimeout(resolve, 100));
    const firstEvents = events.slice();
    await deadline(page.mouse.click(box.x + box.width / 2, box.y + box.height / 2), 1500);
    await new Promise(resolve => setTimeout(resolve, 300));
    duplicateObserved = await Promise.race([
      pending.duplicate.then(() => 'duplicate POST observed'),
      new Promise(resolve => setTimeout(() => resolve('no duplicate POST observed in bounded window'), 50)),
    ]);
    observed = {
      pendingRequests: pending.requests,
      attributes: {
        submitDisabled: firstEvents.some(event => event.target === 'button' && event.name === 'disabled' && !event.removed),
        resetDisabled: firstEvents.some(event => event.target === 'reset' && event.name === 'disabled' && !event.removed),
        formBusy: firstEvents.some(event => event.target === 'form' && event.name === 'aria-busy' && event.value === 'true'),
        savingFeedback: firstEvents.some(event => event.target === 'status' && event.name === 'data-state' && event.value === 'saving'),
        fieldsReadonly: ['two', 'cars'].every(target => firstEvents.some(event => event.target === target && event.name === 'readonly' && !event.removed)),
      },
      duplicateObserved,
      requestChecks: pending.checks,
      forwardedBusinessPosts: 0,
    };
  } finally {
    pending.release();
    try { responseStatus = await deadline(responseWait.then(response => response.status()), 5000); } catch { }
    if (activePending === pending) activePending = null;
    await cdp.detach().catch(() => {});
  }
  const good = pending.requests === 1 && responseStatus === 204
    && observed?.attributes.submitDisabled && observed?.attributes.resetDisabled && observed?.attributes.formBusy && observed?.attributes.savingFeedback
    && observed?.attributes.fieldsReadonly && duplicateObserved.startsWith('no duplicate')
    && observed?.requestChecks?.fieldPresence.twoWheeler && observed?.requestChecks?.fieldPresence.cars
    && observed?.requestChecks?.fieldValuesMatch.twoWheeler && observed?.requestChecks?.fieldValuesMatch.cars
    && observed?.requestChecks?.csrfPresent;
  assertProbe(`native-pointer-held-${viewport.width}`, good, { ...observed, stubStatus: responseStatus });
}

async function draftResetAndValidation(page) {
  await page.goto(`${origin}/settings`, { waitUntil: 'load', timeout: 5000 });
  const two = page.locator('#twoWheelerLimit'), cars = page.locator('#carLimit');
  const original = { two: await two.inputValue(), cars: await cars.inputValue() };
  const save = page.locator('#saveLimitButton'), reset = page.locator('#resetLimitButton');
  const initialCount = settingsPostCount;
  assertProbe('settings-noop-save-disabled', await save.isDisabled() && settingsPostCount === initialCount);
  await two.fill(String(Number(original.two) + 1));
  await reset.click();
  assertProbe('settings-reset-restores-loaded-values', await two.inputValue() === original.two && await cars.inputValue() === original.cars && await save.isDisabled());

  for (const [label, value] of [['required', ''], ['min', '-1'], ['integer-step', '1.5']]) {
    await two.fill(value);
    const invalidBeforeActivation = await page.locator('#limitForm :invalid').count() > 0;
    await save.click({ timeout: 3000 });
    assertProbe(`settings-native-${label}-validation`, invalidBeforeActivation && settingsPostCount === initialCount);
    await two.fill(original.two);
  }
}

async function historyRecovery(page) {
  await page.goto(`${origin}/`, { waitUntil: 'load', timeout: 5000 });
  const settingsLink = page.locator('a[href="/settings"]').first();
  if (await settingsLink.count() === 0) throw new Error('SETTINGS_NAVIGATION_LINK_MISSING');
  await settingsLink.click();
  await page.waitForURL(url => url.pathname === '/settings', { timeout: 5000 });
  const two = page.locator('#twoWheelerLimit');
  const draft = String(Number(await two.inputValue()) + 1);
  await two.fill(draft);
  const button = page.locator('#saveLimitButton');
  await button.scrollIntoViewIfNeeded();
  const box = await button.boundingBox();
  const cdp = await context.newCDPSession(page);
  await cdp.send('DOM.enable');
  const rootNode = (await cdp.send('DOM.getDocument', { depth: 0 })).root.nodeId;
  const nodes = {};
  for (const [key, selector] of Object.entries({ form: '#limitForm', button: '#saveLimitButton', two: '#twoWheelerLimit' }))
    nodes[key] = (await cdp.send('DOM.querySelector', { nodeId: rootNode, selector })).nodeId;
  const requestWait = page.waitForRequest(request => request.method() === 'POST' && new URL(request.url()).pathname === '/settings', { timeout: 5000 });
  const responseWait = page.waitForResponse(response => response.status() === 204 && response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/settings', { timeout: 5000 });
  const postsBeforeProbe = settingsPostCount;
  const pending = armPending('/settings', { twoWheeler: draft, cars: await page.locator('#carLimit').inputValue() });
  let historyReturned = false, retainedDraft = false, pendingCleared = false, draftStateConsistent = false, forwardCount = null;
  try {
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await requestWait;
    await deadline(pending.arrived, 5000);
    await page.goBack({ waitUntil: 'domcontentloaded', timeout: 5000 });
    historyReturned = new URL(page.url()).pathname !== '/settings';
    pending.release();
    await deadline(responseWait, 5000).catch(() => {});
    await page.goForward({ waitUntil: 'domcontentloaded', timeout: 5000 });
    await page.waitForURL(url => url.pathname === '/settings', { timeout: 5000 });
    retainedDraft = await page.locator('#twoWheelerLimit').inputValue() === draft;
    const formBusy = await page.locator('#limitForm').getAttribute('aria-busy');
    const draftState = await page.locator('#limitDraft').getAttribute('data-state');
    const buttonDisabled = await page.locator('#saveLimitButton').isDisabled();
    const fieldsReadonly = !(await page.locator('#twoWheelerLimit').isEditable()) && !(await page.locator('#carLimit').isEditable());
    pendingCleared = formBusy !== 'true' && !buttonDisabled && !fieldsReadonly;
    draftStateConsistent = retainedDraft ? draftState === 'draft' : draftState !== 'saving';
    forwardCount = settingsPostCount - postsBeforeProbe;
  } finally {
    pending.release();
    if (activePending === pending) activePending = null;
    await cdp.detach().catch(() => {});
  }
  const recoveryVerified = historyReturned && pendingCleared && draftStateConsistent && forwardCount === 1;
  record({ probe: 'settings-native-history-pageshow-recovery', outcome: recoveryVerified
    ? retainedDraft ? 'PASS' : 'QUALIFIED — BFCache/draft retention NOT VERIFIED' : 'FAIL',
  historyReturned, retainedDraft, pendingCleared, draftStateConsistent, settingsPostsDuringProbe: forwardCount, exactBfcache: 'not directly asserted' });
  if (!recoveryVerified) failures.push('settings-native-history-pageshow-recovery');
}

async function restoredDraftAfterDashboardBack(page, viewport) {
  await diagnosticStep('set viewport', `viewport ${viewport.width}x${viewport.height}`, () => page.setViewportSize(viewport));
  await diagnosticStep('GET home before settings', 'GET /', () => page.goto(`${origin}/`, { waitUntil: 'load', timeout: 5000 }));
  const mobileNavigation = viewport.width < 768;
  const navigationDialog = page.locator('#navigationDialog');
  if (mobileNavigation) {
    await diagnosticStep('open mobile navigation dialog', '#openNavigation', () => page.locator('#openNavigation').click());
    await diagnosticStep('wait for visible mobile navigation', '#navigationDialog', () => expect(navigationDialog).toBeVisible());
  }
  const navigation = mobileNavigation
    ? navigationDialog.locator('.navigation-content')
    : page.locator('.sidebar .navigation-content');
  const settingsDetails = navigation.locator('.nav-extra');
  const settingsSummary = settingsDetails.locator('summary');
  const settingsLink = settingsDetails.locator('a[href="/settings"]');
  if (await diagnosticStep('locate native settings disclosure', '.nav-extra > summary', () => settingsSummary.count(), 'locator-count') === 0) throw new Error('SETTINGS_DISCLOSURE_MISSING');
  await diagnosticStep('verify settings link starts collapsed', 'a[href="/settings"] inside closed .nav-extra', () => expect(settingsLink).toBeHidden());
  await diagnosticStep('open settings disclosure with native summary', '.nav-extra > summary', () => settingsSummary.click());
  await diagnosticStep('verify visible scoped settings link', 'visible a[href="/settings"] inside .nav-extra', () => expect(settingsLink).toBeVisible());
  await diagnosticStep('navigate to settings', 'visible a[href="/settings"] inside .nav-extra', () => settingsLink.click());
  await diagnosticStep('wait for settings page', 'URL /settings', () => page.waitForURL(url => url.pathname === '/settings', { timeout: 5000 }));
  if (mobileNavigation) await diagnosticStep('verify native navigation reflow after route change', 'closed #navigationDialog', () => expect(navigationDialog).not.toBeVisible());

  const two = page.locator('#twoWheelerLimit'), cars = page.locator('#carLimit');
  const renderedDefault = await diagnosticStep('read rendered default', '#twoWheelerLimit[value]', () => two.getAttribute('value'), 'state-read');
  const draft = String(Number(await diagnosticStep('read loaded limit', '#twoWheelerLimit', () => two.inputValue(), 'state-read')) + 1);
  const expected = { twoWheeler: draft, cars: await diagnosticStep('read loaded car limit', '#carLimit', () => cars.inputValue(), 'state-read') };
  await diagnosticStep('enter changed draft', '#twoWheelerLimit', () => two.fill(draft));
  const button = page.locator('#saveLimitButton');
  await diagnosticStep('scroll submit control into view', '#saveLimitButton', () => button.scrollIntoViewIfNeeded());
  const box = await diagnosticStep('read submit bounds', '#saveLimitButton', () => button.boundingBox(), 'layout-read');
  if (!box || await diagnosticStep('assert form submit-ready', '#limitForm and #saveLimitButton', async () => await button.isDisabled() || await page.locator('#limitForm :invalid').count() > 0, 'form-readiness')) throw new Error('SETTINGS_FORM_NOT_SUBMITTABLE');
  const requestWait = page.waitForRequest(request => request.method() === 'POST' && new URL(request.url()).pathname === '/settings', { timeout: 5000 });
  const responseWait = page.waitForResponse(response => response.status() === 204 && response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/settings', { timeout: 5000 });
  const postsBeforeProbe = settingsPostCount;
  const pending = armPending('/settings', expected);
  let responseStatus = null, returnedToSettings = false, retainedDiffersFromRenderedDefault = false;
  let draftState = null, resetEnabled = false, saveEnabled = false, unlocked = false, notBusy = false;
  try {
    await diagnosticStep('activate native submit', '#saveLimitButton pointer click', () => page.mouse.click(box.x + box.width / 2, box.y + box.height / 2));
    await diagnosticStep('observe settings request', 'POST /settings request', () => requestWait, 'request-observation');
    await diagnosticStep('observe intercepted request held pending', 'local POST /settings route', () => deadline(pending.arrived, 5000), 'pending-arrival');
    activeDiagnosticStep = { name: 'release intercepted response', location: 'local POST /settings route', category: 'response-release' };
    pending.release();
    const response = await diagnosticStep('observe local response', 'POST /settings response', () => deadline(responseWait, 5000), 'response-observation');
    responseStatus = response.status();
    if (activePending === pending) activePending = null;

    await diagnosticStep('GET home after response', 'GET /', () => page.goto(`${origin}/`, { waitUntil: 'load', timeout: 5000 }));
    await diagnosticStep('native Back to settings', 'browser history Back', () => page.goBack({ waitUntil: 'domcontentloaded', timeout: 5000 }));
    returnedToSettings = await diagnosticStep('read restored route', 'current URL pathname', async () => new URL(page.url()).pathname === '/settings', 'history-state-read');
    if (returnedToSettings) {
      const restoredValue = await diagnosticStep('read restored draft value', '#twoWheelerLimit', () => page.locator('#twoWheelerLimit').inputValue(), 'state-read');
      const restoredDefault = await diagnosticStep('read restored rendered default', '#twoWheelerLimit[value]', () => page.locator('#twoWheelerLimit').getAttribute('value'), 'state-read');
      retainedDiffersFromRenderedDefault = restoredValue !== restoredDefault;
      draftState = await diagnosticStep('read draft state', '#limitDraft[data-state]', () => page.locator('#limitDraft').getAttribute('data-state'), 'state-read');
      resetEnabled = await diagnosticStep('read reset enabled state', '#resetLimitButton', () => page.locator('#resetLimitButton').isEnabled(), 'state-read');
      saveEnabled = await diagnosticStep('read save enabled state', '#saveLimitButton', () => page.locator('#saveLimitButton').isEnabled(), 'state-read');
      unlocked = await diagnosticStep('read fields editable state', '#twoWheelerLimit and #carLimit', async () => await page.locator('#twoWheelerLimit').isEditable() && await page.locator('#carLimit').isEditable(), 'state-read');
      notBusy = await diagnosticStep('read form busy state', '#limitForm[aria-busy]', async () => (await page.locator('#limitForm').getAttribute('aria-busy')) !== 'true', 'state-read');
    }
  } finally {
    pending.release();
    if (activePending === pending) activePending = null;
  }
  const postsDuringProbe = settingsPostCount - postsBeforeProbe;
  const recoveryVerified = responseStatus === 204 && returnedToSettings && retainedDiffersFromRenderedDefault
    && draftState === 'draft' && resetEnabled && saveEnabled && unlocked && notBusy && postsDuringProbe === 1;
  assertProbe(`settings-post-response-dashboard-back-${viewport.width}`, recoveryVerified, {
    step: 'assert recovered state', category: 'recovery-assertion', location: 'response + GET / + Back + restored settings controls',
    responseStatus, returnedToSettings, retainedDiffersFromRenderedDefault, draftState,
    resetEnabled, saveEnabled, unlocked, notBusy, settingsPostsDuringProbe: postsDuringProbe,
    exactBfcache: 'not asserted; native history state only',
  });
}

try {
  sourceBefore = await sourceIdentity();
  browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  context = await browser.newContext();
  runWatchdog = setTimeout(() => {
    record({ probe: 'run', outcome: 'PARTIAL — NOT VERIFIED', step: activeDiagnosticStep.name,
      category: 'overall-timeout', location: activeDiagnosticStep.location, errorKind: 'TimeoutError' });
    failures.push('run:overall-timeout');
    process.exitCode = 1;
    void context?.close().catch(() => {});
  }, 90000);
  await context.route('**/*', async route => {
    const request = route.request();
    let target;
    try { target = new URL(request.url()); } catch { guards.externalRequestsBlocked++; return route.abort(); }
    if (target.origin !== origin || !['127.0.0.1', 'localhost'].includes(target.hostname)) { guards.externalRequestsBlocked++; return route.abort(); }
    const method = request.method().toUpperCase();
    if (method === 'GET' || method === 'HEAD') return route.continue();
    if (method === 'POST' && target.pathname === '/login') { guards.loginPostsForwarded++; return route.continue(); }
    if (method === 'POST' && target.pathname === '/settings') {
      guards.settingsPostsStubbed++;
      settingsPostCount++;
      const checks = activePending ? requestChecks(request, activePending.expected) : null;
      if (activePending?.pathname === target.pathname) {
        activePending.requests++;
        activePending.checks ??= checks;
        if (activePending.requests === 1) activePending.markArrived(); else activePending.markDuplicate();
        await activePending.released;
      }
      return route.fulfill({ status: 204, body: '' }).catch(() => {});
    }
    if (method === 'POST' && /^\/(pricing|payments)(\/|$)/.test(target.pathname)) { guards.otherMutationsBlocked++; return route.abort(); }
    guards.otherMutationsBlocked++;
    return route.abort();
  });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  page.setDefaultNavigationTimeout(5000);
  page.on('pageerror', error => runtimeErrors.push({ category: 'page-error', kind: error.name || 'Error' }));
  page.on('console', message => { if (message.type() === 'error') runtimeErrors.push({ category: 'console-error' }); });
  await page.goto(`${origin}/login`, { waitUntil: 'load', timeout: 5000 });
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#loginForm button[type=submit]').click();
  await page.waitForURL(url => url.origin === origin && url.pathname !== '/login', { timeout: 5000 });
  assertProbe('synthetic-login', true);
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    const caseId = `settings-post-complete-dashboard-get-back-${viewport.width}`;
    const casePage = await context.newPage();
    await boundedCase(caseId, () => restoredDraftAfterDashboardBack(casePage, viewport));
    await casePage.close().catch(() => {});
  }
  await boundedCase('settings-reset-and-native-validation', () => draftResetAndValidation(page));
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    const casePage = await context.newPage();
    await boundedCase(`settings-held-native-submit-${viewport.width}`, () => nativeHeldProbe(casePage, viewport));
    await casePage.close().catch(() => {});
  }
  if (diagnosticMode === 'with-legacy-history-diagnostic') {
    const diagnosticPage = await context.newPage();
    await boundedCase('OPTIONAL legacy pending-submit Back/Forward recovery diagnostic', () => historyRecovery(diagnosticPage));
    await diagnosticPage.close().catch(() => {});
  } else {
    record({ probe: 'OPTIONAL legacy pending-submit Back/Forward recovery diagnostic', outcome: 'NOT RUN',
      reason: 'Default path prioritizes verified post-completion GET-away → native Back recovery.' });
  }
} catch (error) {
  record({ probe: 'run', outcome: 'PARTIAL — NOT VERIFIED', errorKind: error?.name ?? 'Error' });
  failures.push('run:probe-error');
} finally {
  clearTimeout(runWatchdog);
  if (activePending) activePending.release();
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  sourceAfter = await sourceIdentity().catch(() => null);
  const sourceStable = sourceBefore !== null && sourceAfter !== null && JSON.stringify(sourceBefore) === JSON.stringify(sourceAfter);
  if (!sourceStable) failures.push('source-identity-stable');
  if (savedTemp === undefined) delete process.env.TEMP; else process.env.TEMP = savedTemp;
  if (savedTmp === undefined) delete process.env.TMP; else process.env.TMP = savedTmp;
  const summary = {
    status: failures.length === 0 && partialCases.length === 0 ? 'PENDING PRESENTATION PROBES COMPLETED — NOT APPLICATION ACCEPTANCE' : 'PARTIAL/FAILURES — NOT VERIFIED',
    preflight, sourceIdentity: { stable: sourceStable, before: sourceBefore, after: sourceAfter },
    tempLocation: path.relative(root, tempDirectory).replaceAll(path.sep, '/'), observations, failures, guards,
    settingsPostCount, runtimeErrors, partialCases,
    boundaries: 'Only same-origin GET/HEAD and synthetic login POST were forwarded. Settings POSTs were intercepted/held/fulfilled locally; all business mutations remained local or blocked. Request evidence contains booleans only: field presence, expected-value match, CSRF presence. No raw request body/token/value, screenshots, trace, cookies, HAR or page text were recorded.',
  };
  await writeFile(path.join(output, 'pending-summary.json'), JSON.stringify(summary, null, 2), { flag: 'wx' });
  process.exitCode = failures.length === 0 && partialCases.length === 0 ? 0 : 1;
}
