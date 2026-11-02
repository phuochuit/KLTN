import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { appendFileSync, writeFileSync } from 'node:fs';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium } = require('@playwright/test');
const runArg = process.argv[2];
const stage = process.argv[3] ?? 'b4-pending';
if (!runArg || !/^[a-z0-9-]+$/.test(stage)) throw new Error('Usage: node tests/e2e/web/quality-interactions-pending.mjs <fresh-active-run-directory> [fresh-stage]');

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
try {
  execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
    `$controller = Get-Process -Id ${controllerPid} -ErrorAction SilentlyContinue; $listener = Get-NetTCPConnection -State Listen -LocalPort ${port} -ErrorAction SilentlyContinue; if (!$controller -or !$listener) { exit 2 }`],
  { stdio: 'ignore', timeout: 5000 });
} catch { throw new Error('Harness process or actual service listener is absent; do not restart from survey helper.'); }

const output = path.join(run, stage);
await mkdir(output, { recursive: false });
const partialPath = path.join(output, 'pending-observations.jsonl');
writeFileSync(partialPath, '', { flag: 'wx' });
const tempDirectory = path.join(root, '.cache/test-temp', `${path.basename(run)}-${stage}`);
await mkdir(tempDirectory, { recursive: false });
const savedTemp = process.env.TEMP;
const savedTmp = process.env.TMP;
process.env.TEMP = tempDirectory;
process.env.TMP = tempDirectory;
const origin = `http://127.0.0.1:${port}`;
const observations = [];
const failures = [];
const runtimeErrors = [];
const requestCounts = { '/settings': 0, '/account/password': 0, '/roles/create': 0 };
const guards = { loginPostsForwarded: 0, businessPostsStubbed: 0, otherMutationsBlocked: 0, externalRequestsBlocked: 0, financialPostsForwarded: 0 };
const preflight = { controllerPid, controllerAlive: true, servicePort: port, listenerVerified: true, stopMarker: 'absent' };
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
function armPending(pathname) {
  let markArrived;
  let markDuplicate;
  let release;
  const arrived = new Promise(resolve => { markArrived = resolve; });
  const duplicate = new Promise(resolve => { markDuplicate = resolve; });
  const released = new Promise(resolve => { release = resolve; });
  const state = { pathname, requests: 0, arrived, duplicate, released, markArrived, markDuplicate, release };
  activePending = state;
  return state;
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
    'tests/e2e/web/quality-interactions.mjs', 'tests/e2e/web/quality-interactions-pending.mjs',
    'spring-web/pom.xml', 'spring-web/package.json', 'spring-web/package-lock.json',
    'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java',
    'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java',
  ].map(file => path.join(root, file));
  for (const folder of ['spring-web/src/main/java', 'spring-web/src/main/resources/templates',
    'spring-web/src/main/resources/static', 'spring-web/src/main/frontend',
    'spring-web/target/classes', 'spring-web/target/test-classes']) await walk(path.join(root, folder), files);
  const manifest = [];
  for (const file of [...new Set(files)].sort()) {
    const bytes = await readFile(file);
    manifest.push({ path: path.relative(root, file).replaceAll(path.sep, '/'), sha256: createHash('sha256').update(bytes).digest('hex') });
  }
  return { manifest };
}
async function readPendingAttributes(cdp, selectors) {
  const rootNode = await cdp.send('DOM.getDocument', { depth: 0, pierce: true });
  const values = {};
  for (const [key, selector] of Object.entries(selectors)) {
    const node = await cdp.send('DOM.querySelector', { nodeId: rootNode.root.nodeId, selector });
    if (!node.nodeId) { values[key] = null; continue; }
    const { attributes = [] } = await cdp.send('DOM.getAttributes', { nodeId: node.nodeId });
    const map = Object.fromEntries(Array.from({ length: attributes.length / 2 }, (_, i) => [attributes[i * 2], attributes[i * 2 + 1]]));
    values[key] = {
      disabled: Object.hasOwn(map, 'disabled'),
      ariaBusy: map['aria-busy'] ?? null,
      hidden: Object.hasOwn(map, 'hidden'),
    };
  }
  return values;
}
async function runPendingProbe(page, spec) {
  await page.goto(`${origin}${spec.route}`, { waitUntil: 'load', timeout: 5000 });
  await spec.prepare(page);
  const cdp = await context.newCDPSession(page);
  await cdp.send('DOM.enable');
  const requestWait = page.waitForRequest(request => request.method() === 'POST' && new URL(request.url()).pathname === spec.path, { timeout: 5000 });
  const responseWait = page.waitForResponse(response => response.status() === 204 && response.request().method() === 'POST'
    && new URL(response.url()).pathname === spec.path, { timeout: 5000 });
  const pending = armPending(spec.path);
  let outcome = 'NOT VERIFIED';
  let attributes = null;
  let duplicateActivation = 'NOT VERIFIED';
  let duplicateObserved = 'NOT VERIFIED';
  let responseStatus = null;
  try {
    await spec.submit(page);
    await requestWait;
    await deadline(pending.arrived, 5000);
    try {
      attributes = await deadline(readPendingAttributes(cdp, spec.selectors), 2500);
      outcome = spec.assertPending(attributes) ? 'PENDING STATE OBSERVED' : 'PENDING STATE NOT OBSERVED';
    } catch (error) {
      outcome = 'NOT VERIFIED';
      record({ probe: `${spec.name}:cdp-pending-read`, outcome, errorKind: error?.message === 'probe-timeout' ? 'timeout' : error?.name ?? 'Error' });
    }
    try {
      await deadline(spec.duplicate(page), 1500);
      duplicateActivation = 'keyboard activation completed';
      const duplicateResult = await Promise.race([
        pending.duplicate.then(() => 'duplicate request observed'),
        new Promise(resolve => setTimeout(() => resolve('no duplicate request observed in 300ms window'), 300)),
      ]);
      duplicateObserved = duplicateResult;
    } catch (error) {
      duplicateActivation = error?.message === 'probe-timeout' ? 'NOT VERIFIED — activation timeout' : `NOT VERIFIED — ${error?.name ?? 'Error'}`;
    }
    record({ probe: spec.name, pendingOutcome: outcome, attributes, duplicateActivation, duplicateObserved, requestCountWhileHeld: pending.requests, note: 'request was observed and held before CDP state capture; 204 is local presentation-only' });
  } finally {
    pending.release();
    try { responseStatus = await deadline(responseWait.then(response => response.status()), 5000); }
    catch { responseStatus = null; }
    if (activePending === pending) activePending = null;
    await cdp.detach().catch(() => {});
  }
  assertProbe(`${spec.name}:held-request-and-single-stub`, pending.requests === 1 && responseStatus === 204,
    { pendingOutcome: outcome, duplicateActivation, duplicateObserved, requestCountWhileHeld: pending.requests, stubStatus: responseStatus });
  if (outcome !== 'PENDING STATE OBSERVED' || duplicateActivation.startsWith('NOT VERIFIED')) failures.push(`${spec.name}:pending-state-or-duplicate-NOT-VERIFIED`);
}

try {
  sourceBefore = await sourceIdentity();
  browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  context = await browser.newContext();
  await context.route('**/*', async route => {
    const request = route.request();
    let target;
    try { target = new URL(request.url()); } catch { guards.externalRequestsBlocked++; return route.abort(); }
    if (target.origin !== origin || !['127.0.0.1', 'localhost'].includes(target.hostname)) { guards.externalRequestsBlocked++; return route.abort(); }
    const method = request.method().toUpperCase();
    if (method === 'GET' || method === 'HEAD') return route.continue();
    if (method === 'POST' && target.pathname === '/login') { guards.loginPostsForwarded++; return route.continue(); }
    if (method === 'POST' && /^\/(pricing|payments)(\/|$)/.test(target.pathname)) { guards.otherMutationsBlocked++; return route.abort(); }
    if (method === 'POST' && (['/settings', '/account/password'].includes(target.pathname) || /^\/roles\/(create|update)$/.test(target.pathname))) {
      guards.businessPostsStubbed++;
      requestCounts[target.pathname]++;
      if (activePending?.pathname === target.pathname) {
        activePending.requests++;
        if (activePending.requests === 1) activePending.markArrived();
        else activePending.markDuplicate();
        await activePending.released;
      }
      return route.fulfill({ status: 204, body: '' });
    }
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
  await page.waitForURL(url => url.origin === origin && url.pathname !== '/login', { timeout: 5000 }).catch(() => {});
  assertProbe('synthetic-login', new URL(page.url()).origin === origin && new URL(page.url()).pathname !== '/login');

  const specs = [
    {
      name: 'settings', route: '/settings', path: '/settings', selectors: { form: '#limitForm', button: '#saveLimitButton' },
      prepare: async p => { const field = p.locator('#twoWheelerLimit'); await field.fill(String(Number(await field.inputValue()) + 1)); },
      submit: p => p.locator('#saveLimitButton').click({ noWaitAfter: true, timeout: 5000 }),
      duplicate: p => p.locator('#twoWheelerLimit').press('Enter', { noWaitAfter: true, timeout: 1500 }),
      assertPending: a => a.form?.ariaBusy === 'true' && a.button?.disabled === true,
    },
    {
      name: 'account-password', route: '/account/password', path: '/account/password', selectors: { form: '#passwordForm', button: '#passwordSubmit' },
      prepare: async p => { await p.locator('#currentPassword').fill('survey-only'); await p.locator('#newPassword').fill('survey-only-new'); await p.locator('#confirmPassword').fill('survey-only-new'); },
      submit: p => p.locator('#passwordSubmit').click({ noWaitAfter: true, timeout: 5000 }),
      duplicate: p => p.locator('#newPassword').press('Enter', { noWaitAfter: true, timeout: 1500 }),
      assertPending: a => a.form?.ariaBusy === 'true' && a.button?.disabled === true,
    },
    {
      name: 'roles-create', route: '/roles', path: '/roles/create', selectors: { form: '#roleEditorForm', button: '#saveRoleButton', status: '#roleSavingState' },
      prepare: async p => { await p.locator('#newRoleButton').click(); await p.locator('#roleDisplayName').fill('B4 Survey Role'); await p.locator('#roleCode').fill('B4_SURVEY'); await p.locator('#rolePrefix').fill('BS'); },
      submit: p => p.locator('#saveRoleButton').click({ noWaitAfter: true, timeout: 5000 }),
      duplicate: p => p.locator('#rolePrefix').press('Enter', { noWaitAfter: true, timeout: 1500 }),
      assertPending: a => a.form?.ariaBusy === 'true' && a.button?.disabled === true && a.status?.hidden === false,
    },
  ];
  for (const spec of specs) {
    try { await runPendingProbe(page, spec); }
    catch (error) { record({ probe: spec.name, outcome: 'PARTIAL — NOT VERIFIED', errorKind: error?.name ?? 'Error' }); failures.push(`${spec.name}:probe-error`); }
  }
  await page.close();
} catch (error) {
  record({ probe: 'run', outcome: 'PARTIAL — NOT VERIFIED', errorKind: error?.name ?? 'Error' });
  failures.push('run:probe-error');
} finally {
  if (activePending) activePending.release();
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  sourceAfter = await sourceIdentity().catch(() => null);
  const sourceStable = sourceBefore !== null && sourceAfter !== null && JSON.stringify(sourceBefore) === JSON.stringify(sourceAfter);
  if (!sourceStable) failures.push('source-identity-stable');
  if (savedTemp === undefined) delete process.env.TEMP; else process.env.TEMP = savedTemp;
  if (savedTmp === undefined) delete process.env.TMP; else process.env.TMP = savedTmp;
  const summary = { status: failures.length === 0 ? 'PENDING PRESENTATION PROBES COMPLETED — NOT APPLICATION ACCEPTANCE' : 'PARTIAL/FAILURES — NOT VERIFIED', preflight, sourceIdentity: { stable: sourceStable, before: sourceBefore, after: sourceAfter }, tempLocation: path.relative(root, tempDirectory).replaceAll(path.sep, '/'), observations, failures, guards, requestCounts, runtimeErrors, boundaries: 'All business POSTs were held and fulfilled locally with 204. No body/cookie/CSRF/password values recorded; no screenshot/trace; no financial POST forwarded.' };
  await writeFile(path.join(output, 'pending-summary.json'), JSON.stringify(summary, null, 2), { flag: 'wx' });
}
