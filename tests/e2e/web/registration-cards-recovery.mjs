// B8a: bounded browser recovery evidence against an owner-prepared isolated H2 fixture.
// Usage: node tests/e2e/web/registration-cards-recovery.mjs <fresh-active-run-dir> [fresh-stage]
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { appendFileSync, writeFileSync } from 'node:fs';
import { access, mkdir, readFile, realpath, stat, writeFile } from 'node:fs/promises';
import { createServer, request as httpRequest } from 'node:http';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium, expect } = require('@playwright/test');
const runArg = process.argv[2];
const stage = process.argv[3] ?? 'b8a-registration-cards-recovery';
if (!runArg || !/^[a-z0-9-]+$/.test(stage)) throw new Error('Usage: node tests/e2e/web/registration-cards-recovery.mjs <fresh-active-run-dir> [fresh-stage]');

const runs = await realpath(path.join(root, '.artifacts/test-runs'));
const run = await realpath(path.resolve(runArg));
if (!run.startsWith(`${runs}${path.sep}`) || !(await stat(run)).isDirectory()) throw new Error('Run must be below .artifacts/test-runs.');
const portText = (await readFile(path.join(run, 'server.port'), 'utf8')).trim();
if (!/^\d{1,5}$/.test(portText)) throw new Error('Invalid service port marker.');
const harnessPort = Number(portText);
if (harnessPort < 1 || harnessPort > 65535) throw new Error('Invalid service port marker.');
try { await readFile(path.join(run, 'stop')); throw new Error('Fixture stop marker exists.'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
const pidText = (await readFile(path.join(run, 'controller.pid'), 'utf8')).trim();
if (!/^\d+$/.test(pidText)) throw new Error('Invalid controller PID marker.');
const controllerPid = Number(pidText);
execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
  `$controller = Get-Process -Id ${controllerPid} -ErrorAction SilentlyContinue; if (!$controller) { exit 2 }`],
{ stdio: 'ignore', timeout: 5000 });
await new Promise((resolve, reject) => {
  const socket = net.connect(harnessPort, '127.0.0.1', resolve);
  socket.setTimeout(1500, () => { socket.destroy(); reject(new Error('Loopback listener timed out.')); });
  socket.on('error', reject); socket.on('connect', () => socket.end());
});

const output = path.join(run, stage);
await mkdir(output, { recursive: false });
const partialPath = path.join(output, 'observations.jsonl');
writeFileSync(partialPath, '', { flag: 'wx' });
const tempDirectory = path.join(root, '.cache/test-temp', `${path.basename(run)}-${stage}`);
await mkdir(tempDirectory, { recursive: false });
const savedTemp = process.env.TEMP, savedTmp = process.env.TMP;
process.env.TEMP = tempDirectory; process.env.TMP = tempDirectory;

const sourceFiles = [
  'tests/e2e/web/registration-cards-recovery.mjs',
  'spring-web/src/main/resources/templates/registrations.html',
  'spring-web/src/main/resources/templates/cards.html',
  'spring-web/target/classes/templates/registrations.html',
  'spring-web/target/classes/templates/cards.html',
  'spring-web/src/main/resources/static/js/registration.mjs',
  'spring-web/src/main/resources/static/js/registration-model.mjs',
  'spring-web/src/main/resources/static/js/cards.mjs',
  'spring-web/src/main/resources/static/js/cards-model.mjs',
  'spring-web/target/classes/static/js/registration.mjs',
  'spring-web/target/classes/static/js/registration-model.mjs',
  'spring-web/target/classes/static/js/cards.mjs',
  'spring-web/target/classes/static/js/cards-model.mjs',
  'spring-web/src/main/frontend/styles/registration.css',
  'spring-web/src/main/frontend/styles/cards.css',
  'spring-web/target/classes/static/css/registration.css',
  'spring-web/target/classes/static/css/cards.css',
  'spring-web/src/main/java/vn/edu/parking/web/AdminController.java',
  'spring-web/src/main/java/vn/edu/parking/service/ManagementCoreWriteService.java',
  'spring-web/src/main/java/vn/edu/parking/service/CardManagementWriteService.java',
  'spring-web/target/classes/vn/edu/parking/web/AdminController.class',
  'spring-web/target/classes/vn/edu/parking/service/ManagementCoreWriteService.class',
  'spring-web/target/classes/vn/edu/parking/service/CardManagementWriteService.class',
  'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java',
  'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java',
  'spring-web/target/test-classes/vn/edu/parking/LoginBrowserHarnessTest.class',
  'spring-web/target/test-classes/vn/edu/parking/LoginTestIsolation.class',
].map(file => path.join(root, file));
const approvedFixtureDirectory = path.join(root, '.artifacts/agent-work/web-quality-performance-engineering/fixtures/registration-upload-proposal-20261102T132856Z');
const approvedRegistrationFiles = [
  { field: 'registrationFaceImage', name: 'synthetic-face.jpg', sha256: 'DEF34653E729E735C4AC3439F7A15858A635B7FADC22C1CBFB5AAF0DD8ED4EB3' },
  { field: 'registrationImage', name: 'synthetic-registration.jpg', sha256: 'DEF34653E729E735C4AC3439F7A15858A635B7FADC22C1CBFB5AAF0DD8ED4EB3' },
];
async function identity() {
  const values = [];
  for (const file of sourceFiles) values.push({ path: path.relative(root, file).replaceAll(path.sep, '/'), sha256: createHash('sha256').update(await readFile(file)).digest('hex') });
  return values;
}
const sourceBefore = await identity();
const fixtureText = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
const password = fixtureText.match(/PASSWORD\s*=\s*"([^"]+)"/)?.[1];
assert.ok(password, 'Synthetic fixture credential missing in test source.');
const edgeCandidates = [
  process.env.B8A_EDGE_PATH,
  process.env['ProgramFiles(x86)'] && path.join(process.env['ProgramFiles(x86)'], 'Microsoft/Edge/Application/msedge.exe'),
  process.env.ProgramFiles && path.join(process.env.ProgramFiles, 'Microsoft/Edge/Application/msedge.exe'),
  process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Microsoft/Edge/Application/msedge.exe'),
].filter(Boolean);
let edgeExecutable = null;
for (const candidate of edgeCandidates) {
  try { await access(candidate); edgeExecutable = candidate; break; } catch { }
}
assert.ok(edgeExecutable, 'Installed Edge executable not found; set B8A_EDGE_PATH to its explicit path.');
assert.equal(path.basename(edgeExecutable).toLowerCase(), 'msedge.exe', 'B8A_EDGE_PATH must identify the installed Microsoft Edge executable.');

const observations = [], failures = [], partialCases = [], casePages = new Set(), payloadObservations = [];
const registrationPayloadObservations = [];
const guards = { syntheticLoginForwarded: 0, registrationPostsStubbed: 0, cardPostsStubbed: 0, otherMutationsBlocked: 0, externalRequestsBlocked: 0, businessPostsForwarded: 0 };
const blockedMutations = [];
let browser = null, context = null, proxy = null, proxyOrigin = null, pendingRelease = null, holdNextBusinessWrite = false;
let currentCase = 'setup', currentStep = 'setup', overallTimer = null, syntheticMode = 'success', pendingArrived = null, globalTimedOut = false;
let expectedCardPayload = { passType: 'MONTHLY', startDate: '2026-11-01', paymentMethod: 'CASH', durationDays: '30' };
const expectedRegistrationFields = { householdCode: 'B8B-SYNTH-HH', apartmentNumber: 'B8B-SYNTH-UNIT', fullName: 'B8B Synthetic Resident', citizenId: '999999999991', plateNumber: 'B8B-12345', vehicleType: 'MOTORBIKE', fuelType: 'GASOLINE', registrationNumber: 'B8B-SYNTH-REG', brand: 'Synthetic', modelName: 'Fixture', color: 'Blue', chassisNumber: 'B8B-SYNTH-CHASSIS', engineNumber: 'B8B-SYNTH-ENGINE' };
const heldResponses = new Set(), proxySockets = new Set();
const preflight = { controllerPid, processPresent: true, harnessPort, loopbackConnected: true, stopMarker: 'absent' };
function record(value) { observations.push(value); appendFileSync(partialPath, `${JSON.stringify(value)}\n`, 'utf8'); }
function check(id, ok, detail = {}) { record({ case: id, outcome: ok ? 'PASS' : 'FAIL', ...detail }); if (!ok) failures.push(id); }
function notVerified(id, missingHook) { partialCases.push(id); record({ case: id, outcome: 'NOT VERIFIED', missingHook, step: currentStep }); }
function step(id) { currentStep = id; }
function safeFailure(error) {
  const stack = String(error?.stack ?? '');
  const line = stack.match(/registration-cards-recovery\.mjs:(\d+):\d+/)?.[1];
  const assertionCategory = error?.matcherResult || error?.name === 'AssertionError' ? 'assertion'
    : error?.name === 'TypeError' ? 'type-or-matcher' : error?.name === 'TimeoutError' ? 'timeout' : 'runtime-or-assertion';
  return { errorKind: ['AssertionError', 'TypeError', 'TimeoutError', 'Error'].includes(error?.name) ? error.name : 'Error',
    step: currentStep, assertionCategory, helperLine: line ? Number(line) : null };
}
function deadline(promise, ms) {
  let timer;
  return Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(Object.assign(new Error('bounded deadline'), { boundedDeadline: true })), ms); })]).finally(() => clearTimeout(timer));
}
async function boundedCase(id, work, ms = 22000) {
  if (globalTimedOut) return;
  currentCase = id; currentStep = `${id}:start`; record({ case: id, outcome: 'STARTED', deadlineMs: ms });
  try { await deadline(work(), ms); }
  catch (error) {
    if (globalTimedOut) notVerified(id, 'overall-watchdog');
    else if (error?.boundedDeadline || error?.name === 'TimeoutError') notVerified(id, 'bounded-deadline');
    else check(id, false, safeFailure(error));
  } finally {
    if (pendingRelease) pendingRelease(); pendingRelease = null; holdNextBusinessWrite = false;
    await Promise.all([...casePages].map(page => page.close().catch(() => {}))); casePages.clear();
  }
}

function send(res, status, body = '', headers = {}) {
  if (res.destroyed) return;
  const bytes = Buffer.from(body);
  res.writeHead(status, { 'cache-control': 'no-store', ...headers, 'content-length': String(bytes.length), connection: 'close' }); res.end(bytes);
}
function redirect(res, location) { send(res, 303, '', { location }); }
const hopByHop = new Set(['connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization', 'te', 'trailer', 'transfer-encoding', 'upgrade']);
function upstreamHeaders(source, { recalculateBody = false } = {}) {
  const connectionTokens = String(source.connection ?? '').split(',').map(value => value.trim().toLowerCase()).filter(Boolean);
  const excluded = new Set([...hopByHop, ...connectionTokens]);
  if (recalculateBody) for (const name of ['content-length', 'content-encoding']) excluded.add(name);
  return Object.fromEntries(Object.entries(source).filter(([name]) => !excluded.has(name)));
}
function rewriteOwnedLocation(headers) {
  if (!headers.location) return headers;
  try {
    const target = new URL(headers.location, `http://127.0.0.1:${harnessPort}`);
    if (target.origin === `http://127.0.0.1:${harnessPort}` && /^[a-z]+:\/\//i.test(headers.location))
      headers.location = `${proxyOrigin}${target.pathname}${target.search}${target.hash}`;
  } catch { }
  return headers;
}
function forwardRead(req, requestPath, res) {
  const requestUrl = new URL(requestPath, `http://127.0.0.1:${harnessPort}`);
  const upstream = httpRequest({ hostname: '127.0.0.1', port: harnessPort, method: req.method, path: requestPath,
    headers: { cookie: req.headers.cookie ?? '', accept: req.headers.accept ?? '*/*', 'user-agent': 'B8a-isolated-browser-helper', host: `127.0.0.1:${harnessPort}`, connection: 'close' } }, response => {
    const chunks = []; response.on('data', chunk => chunks.push(chunk));
    response.on('end', () => {
      const headers = rewriteOwnedLocation(upstreamHeaders(response.headers, { recalculateBody: true }));
      let body = Buffer.concat(chunks);
      if (requestUrl.searchParams.get('__b8a') === 'synthetic-flash' && requestUrl.pathname === '/cards') {
        body = Buffer.from(body.toString('utf8').replace('</main>', '<div class="cards-feedback cards-feedback-success" role="status"><p>B8a synthetic redirect/flash fixture. Not Spring-rendered.</p></div></main>'));
        headers['cache-control'] = 'no-store';
      }
      send(res, response.statusCode ?? 502, body, headers);
    });
  });
  upstream.on('error', () => send(res, 502, 'Synthetic loopback read failed.'));
  upstream.end();
}
async function summarizeCardPayload(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const encoded = Buffer.concat(chunks).toString('utf8');
  const fields = new URLSearchParams(encoded);
  const fieldPresence = Object.fromEntries(['_csrf', 'vehicleId', 'passType', 'status', 'startDate', 'durationDays', 'paymentMethod', 'paymentReference'].map(name => [name, fields.has(name)]));
  const fieldValueMatch = Object.fromEntries(Object.entries(expectedCardPayload).map(([name, expected]) => [name, fields.get(name) === expected]));
  fieldValueMatch.vehicleId = /^\d+$/.test(fields.get('vehicleId') || '');
  fieldValueMatch.csrfPresent = Boolean(fields.get('_csrf'));
  payloadObservations.push({ fieldPresence, fieldValueMatch, bodyLogged: false });
}
async function summarizeRegistrationPayload(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const body = Buffer.concat(chunks);
  const contentType = String(req.headers['content-type'] ?? '');
  const boundary = contentType.match(/^multipart\/form-data;\s*boundary=(?:"([^"]+)"|([^;]+))/i);
  const result = { contentTypeMultipart: Boolean(boundary), fieldPresence: {}, fieldValueMatch: {}, filePartPresence: {}, filePartMimeMatch: {}, filePartHashMatch: {}, bodyLogged: false };
  if (boundary) {
    const marker = Buffer.from(`--${(boundary[1] || boundary[2]).trim()}`);
    for (const part of body.toString('latin1').split(marker.toString('latin1'))) {
      const split = part.indexOf('\r\n\r\n');
      if (split < 0) continue;
      const headers = part.slice(0, split);
      const disposition = headers.match(/content-disposition:\s*form-data;\s*name="([^"]+)"(?:;\s*filename="([^"]*)")?/i);
      if (!disposition) continue;
      const [, name, fileName] = disposition;
      const contentStart = split + 4;
      let contentEnd = part.length;
      if (part.slice(contentEnd - 2) === '\r\n') contentEnd -= 2;
      const value = part.slice(contentStart, contentEnd);
      if (fileName !== undefined) {
        const approved = approvedRegistrationFiles.find(file => file.field === name);
        const bytes = Buffer.from(value, 'latin1');
        result.filePartPresence[name] = true;
        result.filePartMimeMatch[name] = /content-type:\s*image\/jpeg/i.test(headers);
        result.filePartHashMatch[name] = Boolean(approved && createHash('sha256').update(bytes).digest('hex').toUpperCase() === approved.sha256);
      } else if (Object.hasOwn(expectedRegistrationFields, name)) {
        result.fieldPresence[name] = true;
        result.fieldValueMatch[name] = value === expectedRegistrationFields[name];
      } else if (name === '_csrf') result.csrfPresent = value.length > 0;
    }
  }
  registrationPayloadObservations.push(result);
}
async function selectApprovedRegistrationFiles(page) {
  const current = new URL(page.url());
  assert.equal(current.origin, proxyOrigin, 'Approved upload fixture is only allowed on the owned loopback proxy origin.');
  assert.equal(current.pathname, '/registrations', 'Approved upload fixture is only allowed on the registration form path.');
  await page.locator('#faceUpload').setInputFiles(path.join(approvedFixtureDirectory, 'synthetic-face.jpg'));
  await page.locator('#registrationImage').setInputFiles(path.join(approvedFixtureDirectory, 'synthetic-registration.jpg'));
}
async function fillValidRegistration(page) {
  for (const [field, value] of Object.entries(expectedRegistrationFields)) {
    const control = page.locator(`#${field}`);
    if (field === 'vehicleType' || field === 'fuelType') await control.selectOption(value);
    else await control.fill(value);
  }
  await selectApprovedRegistrationFiles(page);
}
function assertLastRegistrationPayload() {
  const result = registrationPayloadObservations.at(-1);
  assert.ok(result?.contentTypeMultipart && result.csrfPresent, 'Registration request must be native multipart with CSRF present.');
  assert.ok(Object.values(result.fieldValueMatch).length === Object.keys(expectedRegistrationFields).length
    && Object.values(result.fieldValueMatch).every(Boolean), 'Synthetic registration fields must match expected values.');
  for (const file of approvedRegistrationFiles) {
    assert.equal(result.filePartPresence[file.field], true, 'Approved file part must be present.');
    assert.equal(result.filePartMimeMatch[file.field], true, 'Approved file part must use image/jpeg.');
    assert.equal(result.filePartHashMatch[file.field], true, 'Multipart file bytes must match approved fixture hash.');
  }
}
function assertLastCardPayload() {
  const result = payloadObservations.at(-1);
  assert.ok(result && Object.values(result.fieldValueMatch).every(Boolean), 'Card payload field assertions must pass; values are never emitted.');
}
function forwardLogin(req, res) {
  guards.syntheticLoginForwarded++;
  const upstream = httpRequest({ hostname: '127.0.0.1', port: harnessPort, method: 'POST', path: '/login',
    headers: { cookie: req.headers.cookie ?? '', 'content-type': req.headers['content-type'] ?? 'application/x-www-form-urlencoded', 'content-length': req.headers['content-length'] ?? '0', host: `127.0.0.1:${harnessPort}`, connection: 'close' } }, response => {
    const headers = rewriteOwnedLocation(upstreamHeaders(response.headers));
    res.writeHead(response.statusCode ?? 502, headers); response.pipe(res);
  });
  upstream.on('error', () => send(res, 502, 'Synthetic login forwarding failed.')); req.pipe(upstream);
}
proxy = createServer(async (req, res) => {
  let target;
  try { target = new URL(req.url, proxyOrigin); } catch { return send(res, 400, 'Invalid local request.'); }
  if (target.origin !== proxyOrigin) return send(res, 400, 'Loopback origin required.');
  const method = req.method || '';
  if (method === 'POST' && target.pathname === '/login') return forwardLogin(req, res);
  if (method === 'POST' && target.pathname === '/registrations') {
    guards.registrationPostsStubbed++;
    await summarizeRegistrationPayload(req);
    if (holdNextBusinessWrite) return holdBusinessWrite(res, 'registration');
    return syntheticBusinessResponse(res, 'registration');
  }
  if (method === 'POST' && target.pathname === '/cards') {
    guards.cardPostsStubbed++;
    await summarizeCardPayload(req);
    if (holdNextBusinessWrite) return holdBusinessWrite(res, 'cards');
    return syntheticBusinessResponse(res, 'cards');
  }
  if (method !== 'GET' && method !== 'HEAD') { guards.otherMutationsBlocked++; blockedMutations.push({ method, path: target.pathname }); return send(res, 405, 'Mutation blocked by B8a proxy.'); }
  const fixture = target.searchParams.get('__b8a');
  if ((target.pathname === '/cards' || target.pathname === '/registrations') && fixture === 'synthetic-flash') {
    return forwardRead(req, `${target.pathname}${target.search}`, res);
  }
  forwardRead(req, `${target.pathname}${target.search}`, res);
});
proxy.on('connection', socket => {
  proxySockets.add(socket);
  socket.once('close', () => proxySockets.delete(socket));
});

function abortHeldResponses() {
  for (const response of heldResponses) response.destroy();
  heldResponses.clear(); pendingRelease = null; holdNextBusinessWrite = false;
}
function triggerOverallWatchdog() {
  if (globalTimedOut) return;
  globalTimedOut = true;
  partialCases.push(currentCase);
  record({ case: currentCase, outcome: 'NOT VERIFIED', missingHook: 'overall-watchdog', step: currentStep });
  abortHeldResponses();
  for (const page of casePages) void page.close().catch(() => {});
  void context?.close().catch(() => {});
  void browser?.close().catch(() => {});
  for (const socket of proxySockets) socket.destroy();
}

function holdBusinessWrite(res, screen) {
  holdNextBusinessWrite = false;
  heldResponses.add(res);
  res.once('close', () => heldResponses.delete(res));
  pendingArrived?.();
  const release = () => {
    pendingRelease = null;
    heldResponses.delete(res);
    if (globalTimedOut) { res.destroy(); return; }
    if (syntheticMode === 'network') { res.destroy(); return; }
    if (syntheticMode === 'known-rejection') return send(res, 400, '<main><div class="settings-write-message">Synthetic rejection fixture. Not Spring-rendered.</div></main>', { 'content-type': 'text/html; charset=utf-8' });
    if (syntheticMode === 'conflict') return send(res, 409, '<main><div class="settings-write-message">Synthetic conflict fixture. Not Spring-rendered.</div></main>', { 'content-type': 'text/html; charset=utf-8' });
    if (syntheticMode === 'upload-error') return send(res, 413, '<main><div class="settings-write-message">Synthetic upload rejection fixture. Not Spring-rendered.</div></main>', { 'content-type': 'text/html; charset=utf-8' });
    if (syntheticMode === 'blocked') return send(res, 403, '<main><div class="settings-write-message">Synthetic blocked fixture. Not Spring-rendered.</div></main>', { 'content-type': 'text/html; charset=utf-8' });
    if (syntheticMode === 'session') return redirect(res, '/login?error');
    if (syntheticMode === 'unknown') return send(res, 503, '<main><div class="settings-write-message">Synthetic unavailable fixture. Not Spring-rendered.</div></main>', { 'content-type': 'text/html; charset=utf-8' });
    return redirect(res, `${screen === 'cards' ? '/cards' : '/vehicles'}?__b8a=synthetic-flash`);
  };
  pendingRelease = release;
}
function syntheticBusinessResponse(res, screen) {
  if (syntheticMode === 'known-rejection') return send(res, 400, '<main><div class="settings-write-message">Synthetic rejection fixture. Not Spring-rendered.</div></main>', { 'content-type': 'text/html; charset=utf-8' });
  if (syntheticMode === 'conflict') return send(res, 409, '<main><div class="settings-write-message">Synthetic conflict fixture. Not Spring-rendered.</div></main>', { 'content-type': 'text/html; charset=utf-8' });
  if (syntheticMode === 'upload-error') return send(res, 413, '<main><div class="settings-write-message">Synthetic upload rejection fixture. Not Spring-rendered.</div></main>', { 'content-type': 'text/html; charset=utf-8' });
  if (syntheticMode === 'blocked') return send(res, 403, '<main><div class="settings-write-message">Synthetic blocked fixture. Not Spring-rendered.</div></main>', { 'content-type': 'text/html; charset=utf-8' });
  if (syntheticMode === 'session') return redirect(res, '/login?error');
  if (syntheticMode === 'unknown') return send(res, 503, '<main><div class="settings-write-message">Synthetic unavailable fixture. Not Spring-rendered.</div></main>', { 'content-type': 'text/html; charset=utf-8' });
  return redirect(res, `${screen === 'cards' ? '/cards' : '/vehicles'}?__b8a=synthetic-flash`);
}

async function newPage() {
  const page = await context.newPage(); casePages.add(page); page.once('close', () => casePages.delete(page));
  page.setDefaultTimeout(4500); page.setDefaultNavigationTimeout(7000);
  const cdp = await context.newCDPSession(page);
  await cdp.send('DOM.enable'); await cdp.send('Page.enable');
  await cdp.send('DOM.getDocument', { depth: -1, pierce: false });
  let attributeEvents = 0;
  cdp.on('DOM.attributeModified', event => { if (['aria-busy', 'disabled', 'data-state'].includes(event.name)) attributeEvents++; });
  page.__b8aCdp = cdp; page.__b8aAttributeEvents = () => attributeEvents;
  return page;
}
async function capture(page, filename) {
  const result = await page.__b8aCdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  await writeFile(path.join(output, filename), Buffer.from(result.data, 'base64'));
}
async function login(page) {
  await page.goto('/login');
  await page.locator('#username').fill('lumipark-browser-manager');
  await page.locator('#password').fill(password);
  await page.locator('#loginSubmit').click();
  await expect(page).toHaveURL(url => url.origin === proxyOrigin && url.pathname === '/');
}
async function navigate(page, url) { await page.goto(url); }
function withPending() { let arrived; const promise = new Promise(resolve => { arrived = resolve; }); pendingArrived = arrived; return promise; }

try {
  for (const file of approvedRegistrationFiles) {
    const fixturePath = path.join(approvedFixtureDirectory, file.name);
    assert.equal(await realpath(fixturePath), fixturePath, 'Approved fixture must resolve to its exact canonical path.');
    const bytes = await readFile(fixturePath);
    assert.equal(bytes.length, 691, 'Approved image fixture size must remain exactly 691 bytes.');
    assert.equal(createHash('sha256').update(bytes).digest('hex').toUpperCase(), file.sha256, 'Approved image fixture hash mismatch.');
  }
  const sourceBuildPairs = [
    ['spring-web/src/main/resources/templates/registrations.html', 'spring-web/target/classes/templates/registrations.html'],
    ['spring-web/src/main/resources/templates/cards.html', 'spring-web/target/classes/templates/cards.html'],
    ['spring-web/src/main/resources/static/js/registration.mjs', 'spring-web/target/classes/static/js/registration.mjs'],
    ['spring-web/src/main/resources/static/js/registration-model.mjs', 'spring-web/target/classes/static/js/registration-model.mjs'],
    ['spring-web/src/main/resources/static/js/cards.mjs', 'spring-web/target/classes/static/js/cards.mjs'],
    ['spring-web/src/main/resources/static/js/cards-model.mjs', 'spring-web/target/classes/static/js/cards-model.mjs'],
  ];
  for (const [source, built] of sourceBuildPairs) {
    assert.deepEqual(await readFile(path.join(root, built)), await readFile(path.join(root, source)), `Build output differs from source: ${path.basename(source)}.`);
  }
  check('source-build-template-and-js-identity', true, { pairs: sourceBuildPairs.length, valuesLogged: false });
  const fixtureProxyReady = await new Promise((resolve, reject) => { proxy.once('error', reject); proxy.listen(0, '127.0.0.1', () => resolve(proxy.address().port)); });
  proxyOrigin = `http://127.0.0.1:${fixtureProxyReady}`;
  browser = await chromium.launch({ executablePath: edgeExecutable, headless: true });
  context = await browser.newContext({ baseURL: proxyOrigin, serviceWorkers: 'block', viewport: { width: 1440, height: 1000 } });
  // Install before any page exists: every browser request is same-origin and allowlisted.
  await context.route('**/*', async route => {
    const request = route.request(); let url;
    try { url = new URL(request.url()); } catch { guards.externalRequestsBlocked++; return route.abort(); }
    if (url.origin !== proxyOrigin) { guards.externalRequestsBlocked++; return route.abort(); }
    const method = request.method();
    if (method === 'GET' || method === 'HEAD') return route.continue();
    if (method === 'POST' && ['/login', '/registrations', '/cards'].includes(url.pathname)) return route.continue();
    guards.otherMutationsBlocked++; blockedMutations.push({ method, path: url.pathname }); return route.abort();
  });
  overallTimer = setTimeout(triggerOverallWatchdog, 240000);

  await boundedCase('synthetic login and authenticated baseline', async () => {
    const page = await newPage(); await login(page); check('synthetic-login-forwarded-only', guards.syntheticLoginForwarded === 1, { forwarded: guards.syntheticLoginForwarded });
  });
  await boundedCase('registration native validation summary focus and responsive feedback', async () => {
    const page = await newPage(); await navigate(page, '/registrations');
    step('registration-validation: native submit');
    await page.locator('#saveRegistrationButton').click();
    step('registration-validation: summary visible and focused');
    await expect(page.locator('#registrationErrors')).toBeVisible();
    await expect(page.locator('#registrationErrors')).toBeFocused();
    step('registration-validation: actual section label and field association');
    await expect(page.locator('#registrationErrorGroups')).toContainText('Hộ/căn hộ');
    await expect(page.locator('#householdCode')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.locator('#faceUpload')).toHaveAttribute('required', '');
    await expect(page.locator('#registrationImage')).toHaveAttribute('required', '');
    await capture(page, 'registration-validation-desktop-1440x1000.png');
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.locator('#registrationErrors')).toBeVisible();
    await capture(page, 'registration-validation-mobile-390x844.png');
    check('registration-native-validation-summary-focus', true, { requiredNativeMediaInputs: true, screenshots: 2 });
  });
  await boundedCase('registration selected-form recovery and no replay boundary', async () => {
    const page = await newPage(); await navigate(page, '/registrations');
    step('registration-recovery: synthetic draft values');
    const household = page.locator('#householdCode'); await household.fill('B8A-SYNTH-HH');
    await page.locator('#apartmentNumber').fill('B8A-SYNTH-UNIT');
    step('registration-recovery: validation blocked by real required fields');
    await page.locator('#registrationForm button[type="submit"]').click();
    await expect(page.locator('#registrationErrors')).toBeVisible();
    await expect(household).toHaveValue('B8A-SYNTH-HH');
    await expect(page.locator('#faceUpload')).toHaveAttribute('required', '');
    assert.equal(guards.registrationPostsStubbed, 0, 'Missing real native image files must block registration POST.');
    await page.locator('.registration-steps a[href="#vehicleSection"]').click();
    await expect(household).toHaveValue('B8A-SYNTH-HH');
    await page.setViewportSize({ width: 390, height: 844 });
    await capture(page, 'registration-validation-recovery-mobile-390x844.png');
    check('registration-draft-is-document-local-only', true, { retainedSyntheticValuesInOpenDocument: true, postCount: guards.registrationPostsStubbed, persistentDraftClaim: false });
  });
  await boundedCase('cards monthly quote inclusive date validation and review cancel', async () => {
    const page = await newPage(); await navigate(page, '/cards');
    await page.locator('#cardPassType').selectOption('PER_VISIT');
    await expect(page.locator('#monthlyFields')).toBeHidden();
    await page.locator('#cardPassType').selectOption('MONTHLY');
    step('cards-monthly: native required attributes');
    await expect(page.locator('#cardStartDate')).toHaveAttribute('required', '');
    await expect(page.locator('#cardPaymentMethod')).toHaveAttribute('required', '');
    step('cards-monthly: server-rendered quote metadata');
    const price = Number(await page.locator('#cardVehicleId option:checked').getAttribute('data-monthly-price'));
    assert.ok(Number.isFinite(price) && price > 0, 'Monthly quote must originate from server-rendered vehicle metadata.');
    await expect(page.locator('#previewAmount')).toHaveText(`${new Intl.NumberFormat('vi-VN').format(price)} đ`);
    step('cards-monthly: invalid calendar date blocks review');
    await page.locator('#cardStartDate').fill('31/02/2026');
    expectedCardPayload = { passType: 'MONTHLY', startDate: '2026-11-01', paymentMethod: 'CASH', durationDays: '30' };
    await page.locator('#cardSubmitButton').click();
    await expect(page.locator('#cardReviewDialog')).toBeHidden();
    await expect(page.locator('#cardStartDate')).toBeFocused();
    await page.locator('#cardStartDate').fill('28/02/2026');
    step('cards-monthly: out-of-range period blocks review');
    await page.locator('#cardDurationDays').fill('0');
    await page.locator('#cardSubmitButton').click();
    await expect(page.locator('#cardReviewDialog')).toBeHidden();
    await expect(page.locator('#cardDurationDays')).toBeFocused();
    await page.locator('#cardDurationDays').fill('2');
    step('cards-monthly: inclusive end-date preview');
    await expect(page.locator('#previewPeriod')).toContainText('28/02/2026 – 01/03/2026');
    await expect(page.locator('#previewPeriod')).toContainText('tính cả ngày cuối');
    await page.locator('#cardPaymentMethod').selectOption('BANK_TRANSFER');
    step('cards-monthly: transfer reference required attribute');
    await expect(page.locator('#cardPaymentReference')).toHaveAttribute('required', '');
    await page.locator('#cardPaymentReference').fill('B8A-SYNTH-REF');
    await page.locator('#cardSubmitButton').click();
    step('cards-monthly: review values and focus');
    await expect(page.locator('#cardReviewDialog')).toBeVisible();
    await expect(page.locator('#reviewAmount')).toHaveText(`${new Intl.NumberFormat('vi-VN').format(price)} đ`);
    await expect(page.locator('#reviewPeriod')).toContainText('28/02/2026 – 01/03/2026');
    await expect(page.locator('#reviewReference')).toHaveText('B8A-SYNTH-REF');
    await expect(page.locator('#reviewDisclosure')).toContainText('không xác nhận đối soát');
    await expect(page.locator('#cardReviewHeading')).toBeFocused();
    await capture(page, 'cards-monthly-review-desktop-1440x1000.png');
    await page.keyboard.press('Escape'); await expect(page.locator('#cardSubmitButton')).toBeFocused();
    step('cards-monthly: cancel has no write');
    await page.locator('#cardSubmitButton').click(); await page.locator('#cancelCardReview').click();
    await expect(page.locator('#cardReviewDialog')).toBeHidden();
    assert.equal(guards.cardPostsStubbed, 0, 'Review and cancel must not POST.');
    await page.setViewportSize({ width: 390, height: 844 }); await capture(page, 'cards-monthly-review-mobile-390x844.png');
    check('cards-monthly-server-quote-period-inclusive-review-cancel', true, { amountSource: 'server-rendered monthlyPrices metadata', businessPosts: 0, screenshots: 2 });
  });
  await boundedCase('cards per-visit context and no monthly payment fields', async () => {
    const page = await newPage(); await navigate(page, '/cards');
    await page.locator('#cardPassType').selectOption('PER_VISIT');
    await expect(page.locator('#monthlyFields')).toBeHidden();
    await expect(page.locator('#previewAmount')).toHaveText('Không thu gói tháng');
    await expect(page.locator('#previewDisclosure')).toContainText('Vé lượt không tạo bản ghi thu gói tháng');
    await page.locator('#cardSubmitButton').click(); await expect(page.locator('#cardReviewDialog')).toBeVisible();
    await expect(page.locator('#reviewPeriodRow')).toBeHidden();
    await expect(page.locator('#reviewPaymentRow')).toBeHidden();
    await capture(page, 'cards-per-visit-review-desktop-1440x1000.png');
    await page.setViewportSize({ width: 390, height: 844 }); await capture(page, 'cards-per-visit-review-mobile-390x844.png');
    await page.keyboard.press('Escape');
    expectedCardPayload = { passType: 'PER_VISIT' };
    await page.locator('#cardSubmitButton').click(); await page.locator('#confirmCardWrite').click();
    await expect(page.locator('#cardFeedback')).toHaveAttribute('data-state', 'success');
    assertLastCardPayload();
    assert.equal(payloadObservations.at(-1).fieldPresence.startDate, false);
    assert.equal(payloadObservations.at(-1).fieldPresence.durationDays, false);
    assert.equal(payloadObservations.at(-1).fieldPresence.paymentMethod, false);
    assert.equal(payloadObservations.at(-1).fieldPresence.paymentReference, false);
    await capture(page, 'cards-per-visit-synthetic-success-mobile-390x844.png');
    check('cards-per-visit-separate-context', true, { monthlyFieldsDisabled: true, monthlyPayloadFieldsAbsent: true, localStubbedPosts: 1, forwardedBusinessPosts: 0, screenshots: 3 });
  });
  await boundedCase('cards synthetic pending one-submit and redirect flash classification', async () => {
    const postsBeforeCase = guards.cardPostsStubbed;
    const page = await newPage(); await navigate(page, '/cards');
    await page.locator('#cardPassType').selectOption('MONTHLY');
    await page.locator('#cardStartDate').fill('01/11/2026');
    await page.locator('#cardPaymentMethod').selectOption('BANK_TRANSFER');
    await page.locator('#cardPaymentReference').fill('B8A-SYNTH-REF');
    await page.locator('#cardSubmitButton').click();
    await page.__b8aCdp.send('DOM.getDocument', { depth: -1, pierce: false });
    syntheticMode = 'success'; expectedCardPayload = { passType: 'MONTHLY', startDate: '2026-11-01', paymentMethod: 'BANK_TRANSFER', paymentReference: 'B8A-SYNTH-REF', durationDays: '30' }; holdNextBusinessWrite = true; const arrived = withPending();
    step('cards-pending: arm native confirmation gate');
    await page.locator('#confirmCardWrite').click(); await arrived;
    const seenWhileHeld = page.__b8aAttributeEvents();
    assertLastCardPayload();
    assert.equal(payloadObservations.at(-1).fieldPresence.paymentReference, true);
    await capture(page, 'cards-pending-desktop-1440x1000.png');
    await page.setViewportSize({ width: 390, height: 844 }); await capture(page, 'cards-pending-mobile-390x844.png');
    assert.ok(seenWhileHeld > 0, 'Passive CDP DOM attribute observer must see pending/disabled state before release.');
    assert.equal(guards.cardPostsStubbed - postsBeforeCase, 1, 'Exactly one business POST is locally held in this case.');
    step('cards-pending: release exactly-one local response gate');
    pendingRelease?.(); pendingArrived = null;
    step('cards-pending: classify fixture redirect and flash after gate release');
    await expect(page.locator('#cardFeedback')).toHaveAttribute('data-state', 'success');
    await expect(page.locator('#cardFeedbackMessage')).toContainText('B8a synthetic redirect/flash fixture');
    await expect(page.locator('#cardSubmitButton')).toBeDisabled();
    await capture(page, 'cards-synthetic-success-flash-mobile-390x844.png');
    assert.equal(guards.businessPostsForwarded, 0);
    check('cards-pending-one-submit-synthetic-success', true, { localStubbedPosts: guards.cardPostsStubbed, forwardedBusinessPosts: 0, passiveAttributeEvents: seenWhileHeld, response: 'synthetic redirect and fixture flash; not Spring rendering' });
  });
  for (const [mode, label] of [['known-rejection', 'retry-safe-400'], ['conflict', 'retry-safe-409'], ['unknown', 'unknown-503'], ['session', 'session-redirect']]) {
    await boundedCase(`cards recovery ${label}`, async () => {
      const page = await newPage(); await navigate(page, '/cards'); await page.locator('#cardPassType').selectOption('MONTHLY');
      await page.locator('#cardPaymentMethod').selectOption('CASH'); await page.locator('#cardSubmitButton').click();
      await page.__b8aCdp.send('DOM.getDocument', { depth: -1, pierce: false });
      await page.locator('#cardStartDate').fill('02/11/2026');
      expectedCardPayload = { passType: 'MONTHLY', startDate: '2026-11-02', paymentMethod: 'CASH', durationDays: '30' };
      syntheticMode = mode; holdNextBusinessWrite = true; const arrived = withPending(); await page.locator('#confirmCardWrite').click(); await arrived;
      assertLastCardPayload();
      const before = guards.cardPostsStubbed; assert.ok(before > 1);
      pendingRelease?.(); pendingArrived = null;
      if (mode === 'known-rejection' || mode === 'conflict') {
        await expect(page.locator('#cardFeedback')).toHaveAttribute('data-state', 'error');
        await expect(page.locator('#cardSubmitButton')).toBeEnabled();
      } else {
        await expect(page.locator('#cardFeedback')).toHaveAttribute('data-state', 'error');
        await expect(page.locator('#cardSubmitButton')).toBeDisabled();
      }
      const after = guards.cardPostsStubbed; await new Promise(resolve => setTimeout(resolve, 300));
      assert.equal(guards.cardPostsStubbed, after, 'No automatic POST replay after recovery outcome.');
      await page.setViewportSize({ width: 390, height: 844 });
      await capture(page, `cards-${label}-mobile-390x844.png`);
      check(`cards-${label}-no-auto-replay`, true, { responseFixture: 'synthetic-only', postCount: after, clientAutomaticReplay: false });
    });
  }
  await boundedCase('cards network-abort unknown outcome locks and does not replay', async () => {
    const page = await newPage(); await navigate(page, '/cards'); await page.locator('#cardPassType').selectOption('MONTHLY');
    await page.locator('#cardPaymentMethod').selectOption('CASH'); await page.locator('#cardSubmitButton').click();
    await page.__b8aCdp.send('DOM.getDocument', { depth: -1, pierce: false });
    await page.locator('#cardStartDate').fill('02/11/2026');
    expectedCardPayload = { passType: 'MONTHLY', startDate: '2026-11-02', paymentMethod: 'CASH', durationDays: '30' };
    syntheticMode = 'network'; holdNextBusinessWrite = true; const arrived = withPending(); await page.locator('#confirmCardWrite').click(); await arrived;
    assertLastCardPayload();
    const before = guards.cardPostsStubbed; pendingRelease?.(); pendingArrived = null;
    await expect(page.locator('#cardFeedbackMessage')).toContainText('Mất kết nối');
    await expect(page.locator('#cardSubmitButton')).toBeDisabled();
    assert.equal(guards.cardPostsStubbed, before);
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.locator('#cardFeedback')).toBeVisible();
    await capture(page, 'cards-network-unknown-mobile-390x844.png');
    await page.reload(); assert.equal(guards.cardPostsStubbed, before, 'Reload GET cannot replay the prior POST.');
    check('cards-network-unknown-no-replay', true, { networkFailure: 'proxy socket destroyed locally', postCount: before, reloadMethod: 'GET' });
  });
  await boundedCase('registration valid multipart pending and selected-context success redirect', async () => {
    const before = guards.registrationPostsStubbed;
    const page = await newPage(); await navigate(page, '/registrations'); await fillValidRegistration(page);
    step('registration-pending: all real required fields and approved native files');
    await expect(page.locator('#registrationErrors')).toBeHidden();
    await expect(page.locator('#faceStatus')).toContainText('đã sẵn sàng gửi');
    await expect(page.locator('#registrationImageStatus')).toContainText('đã sẵn sàng gửi');
    syntheticMode = 'success'; holdNextBusinessWrite = true; const arrived = withPending();
    await page.locator('#saveRegistrationButton').click(); await arrived;
    step('registration-pending: native busy state and repeat activation blocked');
    assertLastRegistrationPayload();
    await expect(page.locator('#registrationForm')).toHaveAttribute('aria-busy', 'true');
    await expect(page.locator('#saveRegistrationButton')).toBeDisabled();
    await expect(page.locator('#registrationFeedback')).toContainText('Đang lưu hồ sơ');
    await capture(page, 'registration-pending-desktop-1440x1000.png');
    await page.setViewportSize({ width: 390, height: 844 }); await capture(page, 'registration-pending-mobile-390x844.png');
    await page.locator('#saveRegistrationButton').click({ timeout: 500 }).then(() => assert.fail('Disabled pending submit unexpectedly activated twice.'), () => {});
    assert.equal(guards.registrationPostsStubbed - before, 1, 'Exactly one valid multipart POST is held for this case.');
    pendingRelease?.(); pendingArrived = null;
    step('registration-success: fixture redirect navigates to registration continuation');
    await expect(page).toHaveURL(url => url.origin === proxyOrigin && url.pathname === '/vehicles');
    assert.equal(registrationPayloadObservations.at(-1).filePartHashMatch.registrationFaceImage, true);
    assert.equal(registrationPayloadObservations.at(-1).filePartHashMatch.registrationImage, true);
    assert.equal(guards.businessPostsForwarded, 0);
    check('registration-valid-multipart-pending-success', true, { localStubbedPosts: guards.registrationPostsStubbed - before, forwardedBusinessPosts: 0, approvedFilePartsMatched: true, successTarget: '/vehicles', screenshots: 2 });
  });
  for (const [mode, label, status] of [['known-rejection', 'validation-400', 400], ['conflict', 'conflict-409', 409], ['upload-error', 'upload-413', 413], ['blocked', 'blocked-403', 403], ['session', 'session-redirect', 302], ['unknown', 'unknown-503', 503], ['network', 'network-unknown', 0]]) {
    await boundedCase(`registration multipart recovery ${label}`, async () => {
      const before = guards.registrationPostsStubbed;
      const page = await newPage(); await navigate(page, '/registrations'); await fillValidRegistration(page);
      step(`registration-${label}: hold one native multipart submit`);
      syntheticMode = mode; holdNextBusinessWrite = true; const arrived = withPending();
      await page.locator('#saveRegistrationButton').click(); await arrived; assertLastRegistrationPayload();
      const firstPayload = registrationPayloadObservations.at(-1);
      pendingRelease?.(); pendingArrived = null;
      step(`registration-${label}: client recovery state and retained selected files`);
      if (['known-rejection', 'conflict', 'upload-error'].includes(mode)) {
        await expect(page.locator('#registrationFeedback')).toHaveAttribute('data-state', 'error');
        await expect(page.locator('#saveRegistrationButton')).toBeEnabled();
      } else {
        await expect(page.locator('#registrationFeedback')).toHaveAttribute('data-state', 'error');
        await expect(page.locator('#saveRegistrationButton')).toBeDisabled();
      }
      await expect(page.locator('#householdCode')).toHaveValue(expectedRegistrationFields.householdCode);
      await expect(page.locator('#faceStatus')).toContainText('đã sẵn sàng gửi');
      await expect(page.locator('#registrationImageStatus')).toContainText('đã sẵn sàng gửi');
      const after = guards.registrationPostsStubbed; await new Promise(resolve => setTimeout(resolve, 300));
      assert.equal(after - before, 1, 'One locally consumed multipart request per recovery case.');
      assert.equal(guards.registrationPostsStubbed, after, 'No automatic multipart replay.');
      assert.equal(firstPayload.filePartHashMatch.registrationFaceImage, true);
      assert.equal(firstPayload.filePartHashMatch.registrationImage, true);
      await page.setViewportSize({ width: 390, height: 844 }); await capture(page, `registration-${label}-mobile-390x844.png`);
      check(`registration-${label}-no-auto-replay`, true, { responseStatus: status, responseFixture: 'synthetic-only', localStubbedPosts: 1, retainedDraftAndFiles: true, clientAutomaticReplay: false });
    });
  }
  await boundedCase('registration synthetic outcome classifier model boundaries', async () => {
    const { classifyRegistrationWrite } = await import(pathToFileURL(path.join(root, 'spring-web/src/main/resources/static/js/registration-model.mjs')));
    assert.equal(classifyRegistrationWrite({ status: 400 }), 'validation-error');
    assert.equal(classifyRegistrationWrite({ status: 409 }), 'conflict');
    assert.equal(classifyRegistrationWrite({ status: 413 }), 'upload-error');
    assert.equal(classifyRegistrationWrite({ status: 503 }), 'unknown');
    assert.equal(classifyRegistrationWrite({ status: 0 }), 'unknown');
    assert.equal(classifyRegistrationWrite({ status: 200, redirected: true, pathname: '/login' }), 'session');
    assert.equal(classifyRegistrationWrite({ status: 200, redirected: true, pathname: '/cards' }), 'success');
    check('registration-classifier-unit-boundaries', true, { evidence: 'model-only; browser recovery cases separately use locally stubbed multipart responses' });
  });
  const approvedMultipartOnly = registrationPayloadObservations.length === guards.registrationPostsStubbed
    && registrationPayloadObservations.every(result => result.contentTypeMultipart && result.csrfPresent
      && approvedRegistrationFiles.every(file => result.filePartHashMatch[file.field] === true));
  check('global-mutation-and-origin-guard', guards.businessPostsForwarded === 0 && approvedMultipartOnly
    && guards.externalRequestsBlocked === 0 && guards.otherMutationsBlocked === 0, {
    businessPostsForwarded: guards.businessPostsForwarded, registrationPostsStubbed: guards.registrationPostsStubbed,
    approvedMultipartOnly,
    externalRequestsBlocked: guards.externalRequestsBlocked, otherMutationsBlocked: guards.otherMutationsBlocked,
  });
} catch (error) {
  check('helper-outer-failure', false, { ...safeFailure(error), case: currentCase });
} finally {
  clearTimeout(overallTimer);
  if (pendingRelease) pendingRelease();
  try { await context?.close(); } catch { }
  try { await browser?.close(); } catch { }
  if (proxy?.listening) await new Promise(resolve => proxy.close(resolve));
  process.env.TEMP = savedTemp; process.env.TMP = savedTmp;
  try {
    const sourceAfter = await identity();
    const stable = JSON.stringify(sourceBefore) === JSON.stringify(sourceAfter);
    record({ case: 'source-before-after-identity', outcome: stable ? 'PASS' : 'FAIL', stable, entries: sourceAfter.length });
    if (!stable) failures.push('source-before-after-identity');
    const summary = { status: failures.length ? 'FAIL' : (partialCases.length || globalTimedOut) ? 'PARTIAL — NOT VERIFIED' : 'PASS — SYNTHETIC H2 BROWSER EVIDENCE ONLY', preflight,
      stage, sourceBefore, sourceAfter, sourceStable: stable, observations, payloadObservations, registrationPayloadObservations, failures, partialCases, guards, blockedMutations,
      safetyBoundary: 'GET/HEAD and isolated synthetic login POST only reach Spring. Registration/cards POSTs terminate in the loopback fixture; registration multipart is consumed in memory and reduced to field presence/value-match and approved-file presence/type/hash-match booleans. No request body, file bytes, CSRF, cookies, credentials, form values, HTML, trace or HAR is recorded. Browser screenshots use direct read-only Page.captureScreenshot. Synthetic response bodies/redirect flashes are fixture-generated, not Spring rendering; no persistence, biometric, ANPR or Spring evidence claim.' };
    writeFileSync(path.join(output, 'summary.json'), JSON.stringify(summary, null, 2));
  } catch (error) {
    writeFileSync(path.join(output, 'summary.json'), JSON.stringify({ status: 'INTERRUPTED', errorKind: error?.name || 'Error', observations, failures, guards, blockedMutations, registrationPayloadObservations }, null, 2));
  }
    if (failures.length) process.exitCode = 1;
    else if (partialCases.length || globalTimedOut) process.exitCode = 2;
}
