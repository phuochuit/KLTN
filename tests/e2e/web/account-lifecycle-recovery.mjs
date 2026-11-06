// B10/B12: bounded, test-only account lifecycle browser preparation.
// Usage: node tests/e2e/web/account-lifecycle-recovery.mjs <fresh-active-run-dir> [fresh-stage]
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { appendFileSync, writeFileSync } from 'node:fs';
import { access, mkdir, readFile, realpath, stat } from 'node:fs/promises';
import { createServer, request as httpRequest } from 'node:http';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium, expect } = require('@playwright/test');
const runArg = process.argv[2];
const stage = process.argv[3] ?? 'b12-account-lifecycle-recovery';
if (!runArg || !/^[a-z0-9-]+$/.test(stage)) throw new Error('Usage: node tests/e2e/web/account-lifecycle-recovery.mjs <fresh-active-run-dir> [fresh-stage]');

const runs = await realpath(path.join(root, '.artifacts/test-runs'));
const run = await realpath(path.resolve(runArg));
if (!run.startsWith(`${runs}${path.sep}`) || !(await stat(run)).isDirectory()) throw new Error('Run must be below .artifacts/test-runs.');
const marker = async name => readFile(path.join(run, name), 'utf8');
const portText = (await marker('server.port')).trim();
if (!/^\d{1,5}$/.test(portText)) throw new Error('Invalid service port marker.');
const harnessPort = Number(portText);
if (harnessPort < 1 || harnessPort > 65535) throw new Error('Invalid service port marker.');
if ((await marker('b12.fixture.ready')).trim() !== 'ready') throw new Error('The isolated harness was not started with the B12 test-only fixture enabled.');
try { await marker('stop'); throw new Error('Fixture stop marker exists.'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
const pidText = (await marker('controller.pid')).trim();
if (!/^\d+$/.test(pidText)) throw new Error('Invalid controller PID marker.');
const controllerPid = Number(pidText);
execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', `$controller = Get-Process -Id ${controllerPid} -ErrorAction SilentlyContinue; if (!$controller) { exit 2 }`], { stdio: 'ignore', timeout: 5000 });
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
  'tests/e2e/web/account-lifecycle-recovery.mjs',
  'spring-web/src/main/resources/templates/account-management.html',
  'spring-web/target/classes/templates/account-management.html',
  'spring-web/src/main/resources/templates/account-management-live.html',
  'spring-web/target/classes/templates/account-management-live.html',
  'spring-web/src/main/resources/static/js/account-management.js',
  'spring-web/target/classes/static/js/account-management.js',
  'spring-web/src/main/resources/static/js/account-management-live.mjs',
  'spring-web/target/classes/static/js/account-management-live.mjs',
  'spring-web/src/main/java/vn/edu/parking/web/AccountManagementPageController.java',
  'spring-web/target/classes/vn/edu/parking/web/AccountManagementPageController.class',
  'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java',
  'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java',
  'spring-web/target/test-classes/vn/edu/parking/LoginBrowserHarnessTest.class',
  'spring-web/target/test-classes/vn/edu/parking/LoginBrowserHarnessTest$FixtureOnlyWebHooks.class',
  'spring-web/target/test-classes/vn/edu/parking/LoginBrowserHarnessTest$FixtureOnlyController.class',
];
const sourceBuildPairs = [
  ['spring-web/src/main/resources/templates/account-management.html', 'spring-web/target/classes/templates/account-management.html'],
  ['spring-web/src/main/resources/templates/account-management-live.html', 'spring-web/target/classes/templates/account-management-live.html'],
  ['spring-web/src/main/resources/static/js/account-management.js', 'spring-web/target/classes/static/js/account-management.js'],
  ['spring-web/src/main/resources/static/js/account-management-live.mjs', 'spring-web/target/classes/static/js/account-management-live.mjs'],
];
async function identity() {
  return Promise.all(sourceFiles.map(async file => ({ path: file, sha256: createHash('sha256').update(await readFile(path.join(root, file))).digest('hex') })));
}
const sourceBefore = await identity();
async function sourceBuildIdentity() {
  return Promise.all(sourceBuildPairs.map(async ([source, served]) => {
    const sourceBytes = await readFile(path.join(root, source));
    const servedBytes = await readFile(path.join(root, served));
    return { source, served, sourceHash: createHash('sha256').update(sourceBytes).digest('hex'),
      servedHash: createHash('sha256').update(servedBytes).digest('hex'), byteEqual: sourceBytes.equals(servedBytes) };
  }));
}
const sourceBuildBefore = await sourceBuildIdentity();
assert.ok(sourceBuildBefore.every(pair => pair.byteEqual), 'Served account templates/JS must byte-match source before browser preparation.');
const fixtureText = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
const harnessText = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java'), 'utf8');
const password = fixtureText.match(/PASSWORD\s*=\s*"([^"]+)"/)?.[1];
assert.ok(password, 'Synthetic fixture credential missing from test source.');
const edgeCandidates = [process.env.B10_EDGE_PATH,
  process.env['ProgramFiles(x86)'] && path.join(process.env['ProgramFiles(x86)'], 'Microsoft/Edge/Application/msedge.exe'),
  process.env.ProgramFiles && path.join(process.env.ProgramFiles, 'Microsoft/Edge/Application/msedge.exe'),
  process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Microsoft/Edge/Application/msedge.exe')].filter(Boolean);
let edgeExecutable = null;
for (const candidate of edgeCandidates) { try { await access(candidate); edgeExecutable = candidate; break; } catch { } }
assert.ok(edgeExecutable, 'Installed Edge executable not found; set B10_EDGE_PATH to its explicit path.');
assert.equal(path.basename(edgeExecutable).toLowerCase(), 'msedge.exe', 'B10_EDGE_PATH must identify the installed Microsoft Edge executable.');

const observations = [], failures = [], attemptedPartials = [], deferredCoverage = [], casePages = new Set();
const guards = { syntheticLoginForwarded: 0, accountPostsTerminatedLocally: 0, otherMutationsBlocked: 0, externalRequestsBlocked: 0, mutationPostsForwarded: 0 };
const payloadChecks = [];
const heldResponses = new Set(), mutationHeldResponses = new Set(), proxySockets = new Set();
let browser = null, context = null, proxy = null, proxyOrigin = null;
let currentCase = 'setup', currentStep = 'setup', globalTimedOut = false, overallTimer = null;
let liveHoldCount = 0, liveHeldRelease = null, liveArrivals = 0, liveArrivalSignal = null;
const liveResponseQueue = [];
let mutationHold = false, mutationArrived = null, mutationRelease = null, mutationResponseDone = null;
let activeExpectedFields = {}, activeExpectedPath = null;
const preflight = { controllerPid, processPresent: true, harnessPort, loopbackConnected: true, stopMarker: 'absent' };
function record(value) { observations.push(value); appendFileSync(partialPath, `${JSON.stringify(value)}\n`, 'utf8'); }
function check(id, ok, detail = {}) { record({ case: id, outcome: ok ? 'PASS' : 'FAIL', ...detail }); if (!ok) failures.push(id); }
function partialCase(id, missingHook) { attemptedPartials.push(id); record({ case: id, outcome: 'PARTIAL', missingHook, step: currentStep }); }
function deferCoverage(id, reason) { deferredCoverage.push({ case: id, reason }); record({ case: id, outcome: 'NOT VERIFIED — DEFERRED COVERAGE', reason }); }
function step(name) { currentStep = `${currentCase}:${name}`; record({ case: currentCase, outcome: 'PROGRESS', step: currentStep }); }
function safeFailure(error) {
  const line = String(error?.stack ?? '').match(/account-lifecycle-recovery\.mjs:(\d+):\d+/)?.[1];
  return { errorKind: ['AssertionError', 'TypeError', 'TimeoutError', 'Error'].includes(error?.name) ? error.name : 'Error', step: currentStep,
    assertionCategory: error?.matcherResult || error?.name === 'AssertionError' ? 'assertion' : error?.name === 'TimeoutError' ? 'timeout' : 'runtime-or-assertion', helperLine: line ? Number(line) : null };
}
function deadline(promise, ms) {
  let timer;
  return Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(Object.assign(new Error('bounded deadline'), { boundedDeadline: true })), ms); })]).finally(() => clearTimeout(timer));
}
function send(res, status, body = '', headers = {}) {
  if (res.destroyed) return;
  const bytes = Buffer.isBuffer(body) ? body : Buffer.from(body);
  res.writeHead(status, { 'cache-control': 'no-store', ...headers, 'content-length': String(bytes.length), connection: 'close' }); res.end(bytes);
}
const hopByHop = new Set(['connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization', 'te', 'trailer', 'transfer-encoding', 'upgrade']);
function safeHeaders(headers) {
  return Object.fromEntries(Object.entries(headers).filter(([name]) => !hopByHop.has(name.toLowerCase())));
}
function markSyntheticSnapshot(html, marker) {
  let result = html;
  for (const id of ['live-refresh-status', 'live-sessions', 'live-shifts']) {
    const opening = new RegExp(`(<(?:div|section)\\b[^>]*\\bid="${id}"[^>]*)(>)`);
    if (!opening.test(result)) return null;
    result = result.replace(opening, `$1 data-b10-snapshot="${marker}"$2`);
  }
  return result;
}
function queueLiveResponse(scenario) {
  let resolve;
  const consumed = new Promise(done => { resolve = done; });
  liveResponseQueue.push({ ...scenario, resolve });
  return consumed;
}
function forwardRead(req, requestPath, res) {
  const upstream = httpRequest({ hostname: '127.0.0.1', port: harnessPort, method: req.method, path: requestPath,
    headers: { cookie: req.headers.cookie ?? '', accept: req.headers.accept ?? '*/*', 'user-agent': 'B10-isolated-browser-helper', host: `127.0.0.1:${harnessPort}`, connection: 'close' } }, response => {
    const chunks = [];
    response.on('data', chunk => chunks.push(chunk));
    response.on('end', () => {
      let body = Buffer.concat(chunks);
      const headers = safeHeaders(response.headers);
      const location = headers.location;
      if (location) {
        try { const target = new URL(location, `http://127.0.0.1:${harnessPort}`); if (target.origin === `http://127.0.0.1:${harnessPort}`) headers.location = `${proxyOrigin}${target.pathname}${target.search}${target.hash}`; } catch { }
      }
      if (req.method === 'HEAD') return send(res, response.statusCode ?? 502, '', headers);
      const isLive = new URL(req.url, proxyOrigin).pathname === '/account-management/live';
      const isLiveHtml = isLive && /^text\/html\b/i.test(String(response.headers['content-type'] ?? ''));
      if (isLiveHtml) body = body.toString('utf8');
      let status = response.statusCode ?? 502;
      if (isLive) {
        liveArrivals++;
        liveArrivalSignal?.done(); liveArrivalSignal = null;
        const scenario = liveResponseQueue.shift();
        if (scenario) {
          if (scenario.marker) {
            const marked = isLiveHtml ? markSyntheticSnapshot(body, scenario.marker) : null;
            scenario.resolve({ responseReceived: true, markerApplied: Boolean(marked) });
            if (marked) body = marked;
          } else scenario.resolve({ responseReceived: true, markerApplied: true });
          if (scenario.kind === 'malformed' && isLiveHtml) body = body.replace('id="live-shifts"', 'id="b10-missing-live-shifts"');
          if (scenario.kind === 'status') status = scenario.status;
          if (scenario.kind === 'redirect') { status = 302; headers.location = `${proxyOrigin}/login?error`; body = ''; }
          if (scenario.kind === 'network') { res.destroy(); return; }
        }
        if (liveHoldCount > 0) {
          liveHoldCount--;
          heldResponses.add(res);
          const release = () => { heldResponses.delete(res); liveHeldRelease = null; send(res, status, body, headers); };
          liveHeldRelease = release;
          res.once('close', () => heldResponses.delete(res));
          return;
        }
      }
      send(res, status, body, headers);
    });
  });
  upstream.on('error', () => send(res, 502, 'Synthetic loopback read failed.'));
  upstream.end();
}
function forwardLogin(req, res) {
  guards.syntheticLoginForwarded++;
  const upstream = httpRequest({ hostname: '127.0.0.1', port: harnessPort, method: 'POST', path: '/login',
    headers: { cookie: req.headers.cookie ?? '', 'content-type': req.headers['content-type'] ?? 'application/x-www-form-urlencoded', 'content-length': req.headers['content-length'] ?? '0', host: `127.0.0.1:${harnessPort}`, connection: 'close' } }, response => {
    const headers = safeHeaders(response.headers);
    if (headers.location) { try { const target = new URL(headers.location, `http://127.0.0.1:${harnessPort}`); if (target.origin === `http://127.0.0.1:${harnessPort}`) headers.location = `${proxyOrigin}${target.pathname}${target.search}${target.hash}`; } catch { } }
    res.writeHead(response.statusCode ?? 502, headers); response.pipe(res);
  });
  upstream.on('error', () => send(res, 502, 'Synthetic login forwarding failed.'));
  req.pipe(upstream);
}
async function summarizeLocalMutation(req) {
  let length = 0; const chunks = [];
  for await (const chunk of req) { length += chunk.length; if (length <= 65536) chunks.push(chunk); }
  const fields = new URLSearchParams(Buffer.concat(chunks).toString('utf8'));
  const allowed = ['_csrf', 'username', 'enabled', 'fullName', 'roleId', 'phone', 'email', 'citizenId', 'dateOfBirth', 'address', 'shiftName', 'startsAt', 'endsAt', 'duty', 'notes', 'sessionId', 'id'];
  const names = [...fields.keys()];
  const expectedTargetField = ['username', 'sessionId', 'id'].find(name => activeExpectedFields[name] !== undefined);
  payloadChecks.push({ contentTypeForm: String(req.headers['content-type'] ?? '').startsWith('application/x-www-form-urlencoded'),
    fieldNamesAllowed: names.every(name => allowed.includes(name)), csrfPresent: fields.has('_csrf') && Boolean(fields.get('_csrf')),
    payloadBounded: length <= 65536,
    expectedPathMatches: activeExpectedPath === null || new URL(req.url, proxyOrigin).pathname === activeExpectedPath,
    expectedTargetMatches: expectedTargetField === undefined || fields.get(expectedTargetField) === activeExpectedFields[expectedTargetField],
    expectedValuesMatch: Object.entries(activeExpectedFields).every(([name, value]) => fields.get(name) === value),
    bodyLogged: false });
}
const accountMutation = /^\/account-management\/(?:create|profile|state|reset-password|shifts(?:\/delete)?|sessions\/(?:desktop|web)\/revoke)$/;
proxy = createServer(async (req, res) => {
  let target;
  try { target = new URL(req.url, proxyOrigin); } catch { return send(res, 400, 'Invalid local request.'); }
  if (target.origin !== proxyOrigin) { guards.externalRequestsBlocked++; return send(res, 400, 'Loopback origin required.'); }
  const method = req.method || '';
  if (method === 'POST' && target.pathname === '/login') return forwardLogin(req, res);
  if (method === 'POST' && accountMutation.test(target.pathname)) {
    guards.accountPostsTerminatedLocally++;
    await summarizeLocalMutation(req);
    if (mutationHold) {
      heldResponses.add(res);
      mutationHeldResponses.add(res);
      mutationArrived?.();
      if (!mutationRelease) {
        mutationRelease = () => {
          for (const held of [...mutationHeldResponses]) {
            mutationHeldResponses.delete(held);
            heldResponses.delete(held);
            send(held, 204);
          }
          const done = mutationResponseDone;
          mutationResponseDone = null;
          mutationRelease = null;
          done?.();
        };
      }
      res.once('close', () => { heldResponses.delete(res); mutationHeldResponses.delete(res); });
      return;
    }
    return send(res, 204);
  }
  if (method !== 'GET' && method !== 'HEAD') {
    guards.otherMutationsBlocked++;
    record({ case: currentCase, outcome: 'BLOCKED LOCALLY', methodAllowed: false, routePresent: true });
    req.resume(); return send(res, 405, 'Mutation blocked locally.');
  }
  forwardRead(req, `${target.pathname}${target.search}`, res);
});
proxy.on('connection', socket => { proxySockets.add(socket); socket.once('close', () => proxySockets.delete(socket)); });

function abortHeldResponses() {
  for (const response of heldResponses) response.destroy();
  heldResponses.clear(); mutationHeldResponses.clear(); liveHeldRelease = null; liveArrivalSignal = null; liveResponseQueue.length = 0;
  mutationRelease = null; mutationResponseDone = null; mutationHold = false; liveHoldCount = 0;
  activeExpectedFields = {}; activeExpectedPath = null;
}
function triggerOverallWatchdog() {
  if (globalTimedOut) return;
  globalTimedOut = true; attemptedPartials.push(currentCase);
  record({ case: currentCase, outcome: 'NOT VERIFIED', missingHook: 'overall-watchdog', step: currentStep });
  abortHeldResponses();
  for (const page of casePages) void page.close().catch(() => {});
  void context?.close().catch(() => {}); void browser?.close().catch(() => {});
  for (const socket of proxySockets) socket.destroy();
}
async function boundedCase(id, work, ms = 30000) {
  if (globalTimedOut) return;
  currentCase = id; currentStep = `${id}:start`; record({ case: id, outcome: 'STARTED', deadlineMs: ms });
  try { await deadline(work(), ms); }
  catch (error) {
    if (globalTimedOut) partialCase(id, 'overall-watchdog');
    else if (error?.boundedDeadline || error?.name === 'TimeoutError') partialCase(id, 'bounded-deadline');
    else check(id, false, safeFailure(error));
  } finally {
    liveHeldRelease?.(); mutationRelease?.(); abortHeldResponses();
    await Promise.all([...casePages].map(page => page.close().catch(() => {}))); casePages.clear();
  }
}
async function newPage() {
  const page = await context.newPage(); casePages.add(page); page.once('close', () => casePages.delete(page));
  page.setDefaultTimeout(5000); page.setDefaultNavigationTimeout(8000);
  const cdp = await context.newCDPSession(page);
  await cdp.send('DOM.enable'); await cdp.send('Page.enable');
  await cdp.send('DOM.getDocument', { depth: -1, pierce: false });
  const disabledEvents = new Map();
  const attributeEvents = new Map(), watchedTextNodes = new Set();
  const labelEvents = { count: 0 };
  cdp.on('DOM.attributeModified', event => {
    if (event.name === 'disabled' && disabledEvents.has(event.nodeId)) disabledEvents.set(event.nodeId, disabledEvents.get(event.nodeId) + 1);
    const key = `${event.nodeId}:${event.name}`;
    if (attributeEvents.has(key)) attributeEvents.set(key, attributeEvents.get(key) + 1);
  });
  cdp.on('DOM.attributeRemoved', event => {
    const key = `${event.nodeId}:${event.name}`;
    if (attributeEvents.has(key)) attributeEvents.set(key, attributeEvents.get(key) + 1);
  });
  cdp.on('DOM.characterDataModified', event => { if (watchedTextNodes.has(event.nodeId)) labelEvents.count++; });
  cdp.on('DOM.childNodeCountUpdated', event => { if (labelEvents.nodeIds?.has(event.nodeId)) labelEvents.count++; });
  cdp.on('DOM.childNodeInserted', event => { if (labelEvents.nodeIds?.has(event.parentNodeId)) labelEvents.count++; });
  cdp.on('DOM.childNodeRemoved', event => { if (labelEvents.nodeIds?.has(event.parentNodeId)) labelEvents.count++; });
  page.__b10Cdp = cdp;
  page.__b10PrepareNativeSubmit = async selector => {
    const locator = page.locator(selector);
    await locator.scrollIntoViewIfNeeded();
    const document = await cdp.send('DOM.getDocument', { depth: -1, pierce: false });
    const { nodeId } = await cdp.send('DOM.querySelector', { nodeId: document.root.nodeId, selector });
    assert.ok(nodeId > 0, 'Native submit button must resolve to a passive CDP DOM node.');
    const { model } = await cdp.send('DOM.getBoxModel', { nodeId });
    const quad = model.content;
    assert.ok(Array.isArray(quad) && quad.length === 8, 'Native submit button must have a measurable content quad.');
    disabledEvents.set(nodeId, 0);
    return { nodeId, eventCount: () => disabledEvents.get(nodeId) ?? 0,
      x: (quad[0] + quad[2] + quad[4] + quad[6]) / 4, y: (quad[1] + quad[3] + quad[5] + quad[7]) / 4 };
  };
  page.__b10PrepareConfirmation = async ({ formSelector, submitterSelector, expectedPath }) => {
    const document = await cdp.send('DOM.getDocument', { depth: -1, pierce: false });
    const queryNode = async selector => (await cdp.send('DOM.querySelector', { nodeId: document.root.nodeId, selector })).nodeId;
    const [formNodeId, submitterNodeId, confirmNodeId] = await Promise.all([
      queryNode(formSelector), queryNode(submitterSelector), queryNode('#app-dialog-confirm'),
    ]);
    assert.ok(formNodeId > 0 && submitterNodeId > 0 && confirmNodeId > 0,
      'Expected form, original submitter and dialog confirm must resolve to passive CDP nodes.');
    const findNode = (node, nodeId) => {
      if (node.nodeId === nodeId) return node;
      for (const child of node.children ?? []) {
        const match = findNode(child, nodeId);
        if (match) return match;
      }
      return null;
    };
    const formNode = findNode(document.root, formNodeId);
    const submitterBelongsToForm = Boolean(formNode && findNode(formNode, submitterNodeId));
    const attributes = new Map((formNode?.attributes ?? []).reduce((pairs, value, index, all) => {
      if (index % 2 === 0) pairs.push([value, all[index + 1]]);
      return pairs;
    }, []));
    const formPathMatches = new URL(attributes.get('action') || proxyOrigin, proxyOrigin).pathname === expectedPath;
    const formIsPost = (attributes.get('method') || 'get').toLowerCase() === 'post';
    assert.ok(submitterBelongsToForm && formPathMatches && formIsPost,
      'Passive form/submitter nodes must resolve to the expected POST target before confirmation.');

    const textNodeIds = node => {
      if (node.nodeType === 3 && node.nodeId > 0) watchedTextNodes.add(node.nodeId);
      for (const child of node.children ?? []) textNodeIds(child);
    };
    const submitterNode = findNode(document.root, submitterNodeId);
    textNodeIds(submitterNode);
    labelEvents.count = 0;
    labelEvents.nodeIds ??= new Set();
    labelEvents.nodeIds.add(submitterNodeId);
    for (const [nodeId, names] of [[formNodeId, ['aria-busy']], [submitterNodeId, ['disabled', 'aria-busy', 'aria-label', 'class']]]) {
      for (const name of names) attributeEvents.set(`${nodeId}:${name}`, 0);
    }
    const { model } = await cdp.send('DOM.getBoxModel', { nodeId: confirmNodeId });
    const quad = model.content;
    assert.ok(Array.isArray(quad) && quad.length === 8, 'Dialog confirm must have a measurable passive content quad.');
    const attrCount = (nodeId, name) => attributeEvents.get(`${nodeId}:${name}`) ?? 0;
    return {
      x: (quad[0] + quad[2] + quad[4] + quad[6]) / 4,
      y: (quad[1] + quad[3] + quad[5] + quad[7]) / 4,
      formAndSubmitterMatched: submitterBelongsToForm && formPathMatches && formIsPost,
      pendingEvidence: () => ({
        formBusyChanged: attrCount(formNodeId, 'aria-busy') > 0,
        submitterDisabledChanged: attrCount(submitterNodeId, 'disabled') > 0,
        submitterBusyChanged: attrCount(submitterNodeId, 'aria-busy') > 0,
        submitterLabelAttributeChanged: attrCount(submitterNodeId, 'aria-label') > 0,
        submitterClassChanged: attrCount(submitterNodeId, 'class') > 0,
        submitterTextChanged: labelEvents.count > 0,
      }),
    };
  };
  return page;
}
async function login(page, username = 'lumipark-browser-manager', assertManagerPage = true) {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#loginSubmit').click();
  await expect(page).toHaveURL(url => url.origin === proxyOrigin && url.pathname !== '/login');
  const pageResponse = await page.goto('/account-management');
  if (assertManagerPage) await expect(page.locator('#account-list')).toBeVisible();
  return { pageStatus: pageResponse?.status() ?? null };
}
function signal() { let done; const promise = new Promise(resolve => { done = resolve; }); return { promise, done }; }
function fixtureUsername(constant) {
  const match = harnessText.match(new RegExp(`static final String ${constant} = "([^"]+)"`));
  assert.ok(match, `Synthetic fixture username ${constant} is missing.`);
  return match[1];
}
async function requestLiveScenario(page, scenario) {
  const consumed = queueLiveResponse(scenario);
  await page.locator('#live-refresh-retry').click();
  await deadline(consumed, 6000);
  if (scenario.marker) await page.locator('#account-search').focus();
}
async function fillCreateForm(form, suffix) {
  await form.locator('[name="username"]').fill(`b10-synthetic-${suffix}`);
  await form.locator('[name="fullName"]').fill('B10 Synthetic Account');
  await form.locator('[name="roleId"]').selectOption({ index: 1 });
  await form.locator('[name="phone"]').fill('+84912345678');
  await form.locator('[name="email"]').fill(`b10-${suffix}@example.com`);
  await form.locator('[name="citizenId"]').fill('999999999991');
  await form.locator('[name="dateOfBirth"]').fill('1990-01-01');
  await form.locator('[name="address"]').fill('B10 synthetic address');
}
async function submitNativeAndObserve(page, formSelector, expectedFields = {}) {
  const selector = `${formSelector} button[type="submit"]`;
  const probe = await page.__b10PrepareNativeSubmit(selector);
  mutationHold = true;
  activeExpectedFields = expectedFields;
  const arrived = signal(), completed = signal();
  mutationArrived = arrived.done; mutationResponseDone = completed.done;
  const before = guards.accountPostsTerminatedLocally;
  await page.mouse.click(probe.x, probe.y);
  await deadline(arrived.promise, 6000);
  await page.waitForTimeout(150);
  const firstPointerPendingEvent = probe.eventCount() > 0;
  await page.mouse.click(probe.x, probe.y);
  await page.waitForTimeout(150);
  const secondPointerPendingEventObserved = probe.eventCount() > 0;
  const exactlyOneLocalPost = guards.accountPostsTerminatedLocally === before + 1;
  const payload = payloadChecks.at(-1);
  mutationRelease?.(); mutationHold = false;
  await deadline(completed.promise, 3000);
  activeExpectedFields = {};
  return { firstPointerPendingEvent, secondPointerPendingEventObserved, exactlyOneLocalPost,
    payloadStructureValid: Boolean(payload?.contentTypeForm && payload.fieldNamesAllowed && payload.csrfPresent && payload.payloadBounded),
    expectedTargetMatches: Boolean(payload?.expectedTargetMatches), expectedValuesMatch: Boolean(payload?.expectedValuesMatch) };
}
async function confirmLocalMutation(page, submitButton, formSelector, expectedPath, expectedFields,
  { repeatWhileHeld = false, fragmentMarker = null } = {}) {
  const before = guards.accountPostsTerminatedLocally;
  const beforePayloads = payloadChecks.length;
  const arrived = signal(), completed = signal();
  activeExpectedFields = expectedFields;
  activeExpectedPath = expectedPath;
  mutationHold = true; mutationArrived = arrived.done; mutationResponseDone = completed.done;
  await submitButton.click();
  await expect(page.locator('#app-dialog[open]')).toHaveCount(1);
  const viewportBeforeAcceptedSubmit = await page.__b10Cdp.send('Page.getLayoutMetrics');
  const probe = await page.__b10PrepareConfirmation({
    formSelector, submitterSelector: `${formSelector} button[type="submit"]`, expectedPath,
  });
  await page.mouse.click(probe.x, probe.y);
  await deadline(arrived.promise, 6000);
  const status = page.locator('#admin-submit-status');
  const statusVisibleInViewport = await status.isVisible();
  const statusBounds = await status.boundingBox();
  const viewportAfterAcceptedSubmit = await page.__b10Cdp.send('Page.getLayoutMetrics');
  const statusWithinViewport = Boolean(statusBounds && statusBounds.x >= 0 && statusBounds.y >= 0
    && statusBounds.x + statusBounds.width <= viewportAfterAcceptedSubmit.layoutViewport.clientWidth
    && statusBounds.y + statusBounds.height <= viewportAfterAcceptedSubmit.layoutViewport.clientHeight);
  const pageDidNotJump = viewportBeforeAcceptedSubmit.layoutViewport.pageY === viewportAfterAcceptedSubmit.layoutViewport.pageY;
  let statusSurvivesLiveReplacement = true;
  if (fragmentMarker) {
    await requestLiveScenario(page, { marker: fragmentMarker });
    statusSurvivesLiveReplacement = await page.locator('#live-shifts').getAttribute('data-b10-snapshot') === fragmentMarker
      && await status.isVisible();
  }
  if (repeatWhileHeld) {
    await new Promise(resolve => setTimeout(resolve, 150));
    await page.mouse.click(probe.x, probe.y);
  }
  await new Promise(resolve => setTimeout(resolve, 150));
  const localPostCountWhileHeld = guards.accountPostsTerminatedLocally - before;
  const payloadCountWhileHeld = payloadChecks.length - beforePayloads;
  const payload = payloadChecks[beforePayloads];
  const exactlyOneLocalPost = localPostCountWhileHeld === 1 && payloadCountWhileHeld === 1;
  const pendingEvidence = probe.pendingEvidence();
  const checks = { exactlyOneLocalPost, repeatPointerAttempted: repeatWhileHeld,
    repeatPointerAttemptTargetVerified: false,
    formAndSubmitterMatched: probe.formAndSubmitterMatched,
    localPostCountWhileHeld, payloadCountWhileHeld,
    pendingEvidence, pendingUiEvidenceObserved: Object.values(pendingEvidence).some(Boolean)
      || statusVisibleInViewport && statusWithinViewport,
    statusVisibleInViewport, statusWithinViewport, pageDidNotJump, statusSurvivesLiveReplacement,
    expectedPathMatches: Boolean(payload?.expectedPathMatches),
    expectedTargetMatches: Boolean(payload?.expectedTargetMatches), expectedValuesMatch: Boolean(payload?.expectedValuesMatch),
    csrfPresent: Boolean(payload?.csrfPresent), payloadStructureValid: Boolean(payload?.contentTypeForm && payload?.fieldNamesAllowed && payload?.payloadBounded) };
  mutationRelease?.(); mutationHold = false;
  await deadline(completed.promise, 3000);
  activeExpectedFields = {}; activeExpectedPath = null;
  return checks;
}
function localMutationChecksPassed(checks) {
  return checks.exactlyOneLocalPost && checks.formAndSubmitterMatched && checks.expectedPathMatches
    && checks.expectedTargetMatches && checks.expectedValuesMatch && checks.csrfPresent && checks.payloadStructureValid
    && checks.statusVisibleInViewport && checks.statusWithinViewport && checks.pageDidNotJump
    && checks.statusSurvivesLiveReplacement;
}

try {
  const proxyPort = await new Promise((resolve, reject) => { proxy.once('error', reject); proxy.listen(0, '127.0.0.1', () => resolve(proxy.address().port)); });
  proxyOrigin = `http://127.0.0.1:${proxyPort}`;
  browser = await chromium.launch({ executablePath: edgeExecutable, headless: true });
  context = await browser.newContext({ baseURL: proxyOrigin, serviceWorkers: 'block', viewport: { width: 1440, height: 1000 } });
  await context.route('**/*', route => {
    const url = new URL(route.request().url());
    if (url.origin !== proxyOrigin) { guards.externalRequestsBlocked++; return route.abort(); }
    return route.continue();
  });
  overallTimer = setTimeout(triggerOverallWatchdog, 5 * 60 * 1000); overallTimer.unref?.();

  await boundedCase('real-h2-limited-grant-page-live-matrix', async () => {
    const cases = [
      { name: 'account-only', constant: 'ACCOUNT_ONLY_USERNAME', page: true, directory: true, sessions: true, shifts: false, staffOption: false },
      { name: 'shift-only', constant: 'SHIFT_ONLY_USERNAME', page: true, directory: false, sessions: false, shifts: true, staffOption: true },
      { name: 'audit-only', constant: 'AUDIT_ONLY_USERNAME', page: true, directory: false, sessions: false, shifts: false, staffOption: false, auditLink: true },
      { name: 'combined', constant: 'ACCOUNT_SHIFT_USERNAME', page: true, directory: true, sessions: true, shifts: true, staffOption: true },
      { name: 'legacy-management', constant: 'MANAGER_USERNAME', page: true, directory: true, sessions: true, shifts: true, staffOption: true, auditLink: true },
      { name: 'gate-only', constant: 'GATE_ONLY_USERNAME', page: false },
    ];
    for (const entry of cases) {
      const page = await newPage();
      const username = fixtureUsername(entry.constant);
      step(`grant-matrix-${entry.name}-synthetic-login`);
      const result = await login(page, username, false);
      const pageAllowed = result.pageStatus === 200;
      if (entry.page) {
        const directory = await page.locator('#account-list').count() === 1;
        const liveStatus = await page.locator('#live-refresh-status').count() === 1;
        const sessions = await page.locator('#live-sessions form[data-live-action-form]').count() > 0;
        const shifts = await page.locator('#shift-assignment-form').count() === 1;
        const shiftRows = await page.locator('#live-shifts tr').filter({ hasText: 'B12 upcoming shift' }).count() === 1;
        const staffOption = await page.locator('#shift-assignment-form select[name="username"] option').filter({ hasText: 'lumipark-browser-manager' }).count() > 0;
        const managerDirectoryMarker = await page.locator('[data-account-row]').filter({ hasText: 'lumipark-browser-manager' }).count() > 0;
        const auditLink = await page.locator('a[href="/security-audit"]').count() > 0;
        const shapeMatches = directory === entry.directory && sessions === entry.sessions && liveStatus === (entry.directory || entry.shifts)
          && shifts === entry.shifts && shiftRows === entry.shifts
          && staffOption === entry.staffOption && auditLink === Boolean(entry.auditLink);
        check(`grant-matrix-${entry.name}`, pageAllowed && shapeMatches
          && (entry.directory || !managerDirectoryMarker),
          { loginContextSynthetic: true, expectedRouteAllowed: true, pageAllowed, directoryPresent: directory,
            sessionPayloadMarkerPresent: sessions, shiftsPresent: shifts, eligibleGateUsernameOptionPresent: staffOption,
            liveStatusPresent: liveStatus, shiftRowsPresent: shiftRows, managerDirectoryMarkerPresent: managerDirectoryMarker,
            auditLinkPresent: auditLink, noResponseBodyLogged: true });
      } else {
        check(`grant-matrix-${entry.name}`, !pageAllowed,
          { loginContextSynthetic: true, expectedRouteAllowed: false, pageAllowed, deniedHttpStatus: result.pageStatus, noResponseBodyLogged: true });
      }
      await page.close();
    }
  }, 75000);

  await boundedCase('shared-confirmation-cancel-focus-and-live-deferral', async () => {
    const page = await newPage(); step('login'); await login(page);
    const before = guards.accountPostsTerminatedLocally;
    const managerRow = page.locator('[data-account-row]').filter({ hasText: 'lumipark-browser-manager' }).first();
    const actionDisclosure = managerRow.locator('.account-row-menu-trigger');
    await actionDisclosure.click();
    const managerStateFormSelector = 'tr[data-account-row]:has(.edit-profile[data-username="lumipark-browser-manager"]) form[action="/account-management/state"]';
    const stateForm = managerRow.locator('form[action="/account-management/state"]');
    const stateSubmit = stateForm.locator('button[type="submit"]');
    await stateSubmit.click();
    await expect(page.locator('#app-dialog[open]')).toHaveCount(1);
    await page.locator('#app-dialog-cancel').click();
    const cancelClosed = await page.locator('#app-dialog[open]').count() === 0;
    // Use native focus matching only; no page evaluation is permitted.
    const stateTriggerFocused = await managerRow.locator('.account-row-menu-trigger:focus').count() === 1;
    const stateFocusReturned = await stateForm.locator('button[type="submit"]:focus').count() === 1
      || stateTriggerFocused && await managerRow.locator('.account-row-menu-trigger').isVisible();
    const cancelNoPost = guards.accountPostsTerminatedLocally === before;
    const cancelStatusHidden = await page.locator('#admin-submit-status').isHidden();
    const cancelFormNotBusy = await stateForm.getAttribute('aria-busy') === null;
    if (await managerRow.locator('.account-row-menu-actions:popover-open').count() === 0)
      await actionDisclosure.click();
    await stateSubmit.click();
    await page.keyboard.press('Escape');
    const escapeClosed = await page.locator('#app-dialog[open]').count() === 0;
    const escapeTriggerFocused = await managerRow.locator('.account-row-menu-trigger:focus').count() === 1;
    const escapeFocusReturned = await stateForm.locator('button[type="submit"]:focus').count() === 1
      || escapeTriggerFocused && await managerRow.locator('.account-row-menu-trigger').isVisible();
    const escapeNoPost = guards.accountPostsTerminatedLocally === before;
    const escapeStatusHidden = await page.locator('#admin-submit-status').isHidden();
    const escapeFormNotBusy = await stateForm.getAttribute('aria-busy') === null;
    check('account-state-confirm-cancel-escape', cancelClosed && stateFocusReturned && cancelNoPost
      && cancelStatusHidden && cancelFormNotBusy && escapeClosed && escapeFocusReturned && escapeNoPost
      && escapeStatusHidden && escapeFormNotBusy,
      { cancelClosed, stateFocusReturned, stateTriggerFocused, cancelNoPost, cancelStatusHidden, cancelFormNotBusy,
        escapeClosed, escapeFocusReturned, escapeTriggerFocused, escapeNoPost, escapeStatusHidden, escapeFormNotBusy,
        forwardedMutationPosts: guards.mutationPostsForwarded });
    if (await managerRow.locator('.account-row-menu-actions:popover-open').count() === 0)
      await actionDisclosure.click();
    const stateConfirmed = await confirmLocalMutation(page, stateSubmit, managerStateFormSelector,
      '/account-management/state', { username: 'lumipark-browser-manager', enabled: 'false' }, { repeatWhileHeld: true });
    check('account-state-confirm-exact-local-post', localMutationChecksPassed(stateConfirmed)
      && stateConfirmed.repeatPointerAttempted && !stateConfirmed.repeatPointerAttemptTargetVerified,
      { ...stateConfirmed, forwardedMutationPosts: guards.mutationPostsForwarded });
    await page.goto('/account-management');
    await page.setViewportSize({ width: 390, height: 844 });

    if (await managerRow.locator('.account-row-menu-actions:popover-open').count() === 0)
      await actionDisclosure.click();
    const managerResetFormSelector = 'tr[data-account-row]:has(.edit-profile[data-username="lumipark-browser-manager"]) form[action="/account-management/reset-password"]';
    const resetForm = managerRow.locator('form[action="/account-management/reset-password"]');
    const resetSubmit = resetForm.locator('button[type="submit"]');
    await resetSubmit.click(); await expect(page.locator('#app-dialog[open]')).toHaveCount(1);
    await page.keyboard.press('Escape');
    const resetEscapeClosed = await page.locator('#app-dialog[open]').count() === 0;
    const resetEscapeNoPost = guards.accountPostsTerminatedLocally === before + 1;
    if (await managerRow.locator('.account-row-menu-actions:popover-open').count() === 0)
      await actionDisclosure.click();
    const resetConfirmed = await confirmLocalMutation(page, resetSubmit, managerResetFormSelector,
      '/account-management/reset-password', { username: 'lumipark-browser-manager' });
      check('account-reset-confirm-escape-and-exact-local-post', resetEscapeClosed && resetEscapeNoPost
        && localMutationChecksPassed(resetConfirmed),
        { resetEscapeClosed, resetEscapeNoPost, ...resetConfirmed, forwardedMutationPosts: guards.mutationPostsForwarded });
    await page.goto('/account-management');
    await page.setViewportSize({ width: 1440, height: 1000 });

    const desktopSessionForm = page.locator('#live-sessions form[action="/account-management/sessions/desktop/revoke"]')
      .filter({ has: page.locator('input[name="sessionId"]') }).first();
    if (await desktopSessionForm.count() === 1) {
      const desktopSessionId = await desktopSessionForm.locator('input[name="sessionId"]').inputValue();
      assert.match(desktopSessionId, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
        'The synthetic Desktop session form must expose its actual hidden session GUID.');
      const desktopSessionFormSelector = `#live-sessions form[data-live-action-id="${desktopSessionId}"][action="/account-management/sessions/desktop/revoke"]`;
      const formDataActionMatches = await desktopSessionForm.getAttribute('data-live-action-id') === desktopSessionId;
      await desktopSessionForm.locator('button[type="submit"]').click();
      await expect(page.locator('#app-dialog[open]')).toHaveCount(1);
      await page.locator('#app-dialog-cancel').click();
      const beforeDesktopConfirm = guards.accountPostsTerminatedLocally;
      const canceled = await page.locator('#app-dialog[open]').count() === 0
        && guards.accountPostsTerminatedLocally === beforeDesktopConfirm;
      const desktopConfirmed = await confirmLocalMutation(page, desktopSessionForm.locator('button[type="submit"]'),
        desktopSessionFormSelector, '/account-management/sessions/desktop/revoke',
        { sessionId: desktopSessionId });
      check('desktop-revoke-confirm-cancel-and-exact-local-post', canceled && formDataActionMatches
        && localMutationChecksPassed(desktopConfirmed),
        { confirmationCanceled: canceled, formDataActionMatches, ...desktopConfirmed, forwardedMutationPosts: guards.mutationPostsForwarded });
      await page.goto('/account-management');
    } else deferCoverage('desktop-revoke-confirm-cancel-no-post', 'seeded-desktop-session-row-not-rendered');

    const shiftRow = page.locator('#live-shifts tr').filter({ hasText: 'B12 upcoming shift' }).first();
    if (await shiftRow.count() === 1) {
      const deleteForm = shiftRow.locator('form[data-live-action-form]');
      const deleteButton = deleteForm.locator('button[type="submit"]');
      await deleteButton.click();
      await expect(page.locator('#app-dialog[open]')).toHaveCount(1);
      const first = queueLiveResponse({ marker: 'b12-action-snapshot-one' });
      const second = queueLiveResponse({ marker: 'b12-action-snapshot-two' });
      const firstObserved = await deadline(first, 8500);
      const secondObserved = await deadline(second, 8500);
      const heldLatest = await page.locator('#live-shifts').getAttribute('data-b10-snapshot') === null
        && await page.locator('#live-refresh-status').getAttribute('data-state') === 'pending';
      await page.locator('#app-dialog-cancel').click();
      const newestApplied = await page.locator('#live-shifts').getAttribute('data-b10-snapshot') === 'b12-action-snapshot-two';
      const equivalentActionExists = await page.locator('#live-shifts form[data-live-action-form]').count() > 0;
      const actionFocusReturned = await page.locator('#live-shifts [data-live-action]:focus').count() === 1;
      const beforeShiftConfirmations = guards.accountPostsTerminatedLocally;
      const noPost = guards.accountPostsTerminatedLocally === beforeShiftConfirmations;
      let replacementActionDialogOpened = false, replacementEscapeClosed = false, replacementEscapeNoPost = false;
      let shiftDeleteConfirmed = {};
      if (firstObserved.responseReceived && secondObserved.responseReceived && heldLatest && newestApplied && equivalentActionExists && noPost) {
        const nextRow = page.locator('#live-shifts tr').filter({ hasText: 'B12 upcoming shift' }).first();
        const deleteForm = nextRow.locator('form[data-live-action-form]');
        const shiftId = await deleteForm.locator('input[name="id"]').inputValue();
        const deleteSubmit = deleteForm.locator('button[type="submit"]');
        await deleteSubmit.click();
        replacementActionDialogOpened = await page.locator('#app-dialog[open]').count() === 1;
        if (replacementActionDialogOpened) {
          await page.keyboard.press('Escape');
          replacementEscapeClosed = await page.locator('#app-dialog[open]').count() === 0;
          replacementEscapeNoPost = guards.accountPostsTerminatedLocally === beforeShiftConfirmations;
          if (replacementEscapeClosed) {
            const shiftFormSelector = `#live-shifts form[data-live-action-id="${shiftId}"]`;
            shiftDeleteConfirmed = await confirmLocalMutation(page, deleteSubmit, shiftFormSelector,
              '/account-management/shifts/delete', { id: shiftId }, { fragmentMarker: 'b14-pending-live-refresh' });
            check('shift-delete-confirm-escape-and-exact-local-post', replacementEscapeClosed && replacementEscapeNoPost
              && localMutationChecksPassed(shiftDeleteConfirmed),
              { deleteEscapeClosed: replacementEscapeClosed, deleteEscapeNoPost: replacementEscapeNoPost,
                ...shiftDeleteConfirmed, forwardedMutationPosts: guards.mutationPostsForwarded });
          }
        }
      }
      check('shift-delete-confirmation-two-poll-defer-newest-delegation', firstObserved.responseReceived && secondObserved.responseReceived
        && heldLatest && newestApplied && equivalentActionExists && actionFocusReturned && noPost
        && replacementActionDialogOpened && replacementEscapeClosed && replacementEscapeNoPost
        && localMutationChecksPassed(shiftDeleteConfirmed),
        { twoSyntheticGetsReceived: firstObserved.responseReceived && secondObserved.responseReceived, dialogAndFragmentsStableWhilePending: heldLatest,
          newestSnapshotAppliedAfterCancel: newestApplied, delegatedActionAvailableAfterReplacement: equivalentActionExists,
          actionFocusReturned, replacementActionDialogOpened, replacementEscapeClosed, replacementEscapeNoPost,
          shiftDeleteLocalPayloadChecksPassed: localMutationChecksPassed(shiftDeleteConfirmed), noPost,
          forwardedMutationPosts: guards.mutationPostsForwarded });
    } else {
      deferCoverage('shift-delete-confirmation-two-poll-defer-newest-delegation', 'seeded-upcoming-shift-row-not-rendered');
    }
    deferCoverage('web-session-revoke-actionable-second-session', 'fixture has no separately authenticated second Web session context; current-session revoke is intentionally not actionable');
  }, 50000);

  await boundedCase('profile-dirty-keep-discard-cancel-two-polls-responsive', async () => {
    for (const [width, height, label] of [[1440, 1000, 'desktop'], [390, 844, 'mobile']]) {
      const page = await newPage(); await page.setViewportSize({ width, height }); await login(page);
      const managerRow = page.locator('[data-account-row]').filter({ hasText: 'lumipark-browser-manager' }).first();
      await managerRow.locator('.edit-profile').click();
      const identityUsername = await page.locator('#profile-form [name="username"]').inputValue();
      const roleSelect = page.locator('#profile-form [name="roleId"]');
      const roleOptions = await roleSelect.locator('option').count();
      const identityControlsLocked = await page.locator('#profile-form [name="username"]').getAttribute('type') === 'hidden'
        && await page.locator('#profile-form [name="employeeCode"]').count() === 0;
      const currentRoleEligible = await roleSelect.locator('option:checked:not(:disabled)').count() === 1;
      await page.locator('#profile-form [name="fullName"]').fill(`B12 synthetic dirty ${label}`);
      const first = queueLiveResponse({ marker: `b12-profile-${label}-one` });
      const second = queueLiveResponse({ marker: `b12-profile-${label}-two` });
      const firstObserved = await deadline(first, 8500), secondObserved = await deadline(second, 8500);
      const dirtySurvivedPolls = await page.locator('#profile-form [name="fullName"]').inputValue() === `B12 synthetic dirty ${label}`
        && await page.locator('#profile-dialog[open]').count() === 1;
      await page.locator('#cancel-profile').click();
      const discardPromptVisible = await page.locator('#profile-discard-prompt:not([hidden])').count() === 1;
      await page.locator('#keep-profile-draft').click();
      const keepPreservedDraft = await page.locator('#profile-dialog[open]').count() === 1
        && await page.locator('#profile-form [name="fullName"]').inputValue() === `B12 synthetic dirty ${label}`;
      await page.keyboard.press('Escape');
      const escapeShowsDiscard = await page.locator('#profile-discard-prompt:not([hidden])').count() === 1;
      await page.locator('#discard-profile-draft').click();
      const discardedAndClosed = await page.locator('#profile-dialog[open]').count() === 0;
      const returnFocus = await managerRow.locator('.edit-profile:focus').count() === 1;
      const otherRow = page.locator('[data-account-row]').filter({ hasText: 'lumipark-browser-account-only' }).first();
      await otherRow.locator('.edit-profile').click();
      const noIdentityBleed = (await page.locator('#profile-form [name="username"]').inputValue()) !== identityUsername
        && await page.locator('#profile-form [name="fullName"]').inputValue() !== `B12 synthetic dirty ${label}`;
      check(`profile-draft-retention-${label}`, firstObserved.responseReceived && secondObserved.responseReceived
        && dirtySurvivedPolls && discardPromptVisible && keepPreservedDraft && escapeShowsDiscard && discardedAndClosed
        && returnFocus && roleOptions > 0 && identityControlsLocked && currentRoleEligible && noIdentityBleed,
        { viewport: label, twoSyntheticGetsReceived: firstObserved.responseReceived && secondObserved.responseReceived,
          dirtyDraftSurvivedInPagePolls: dirtySurvivedPolls, discardPromptVisible, keepPreservedDraft, escapeShowsDiscard,
          discardClosedEditor: discardedAndClosed, focusReturnedToInvokingAction: returnFocus, profileRoleOptionsPresent: roleOptions > 0,
          usernameAndEmployeeCodeReadOnlyIdentity: identityControlsLocked, currentRoleOptionEnabled: currentRoleEligible,
          noCrossAccountDraftOrIdentityBleed: noIdentityBleed, noPIIStorageUsed: true });
      await page.close();
    }
  }, 50000);

  await boundedCase('temporary-secret-synthetic-flash-authenticated-model-filtering', async () => {
    const managerPage = await newPage(); await login(managerPage);
    const hookResponse = await managerPage.goto('/account-management?fixture-temporary-secret');
    const firstRenderHasPanel = await managerPage.locator('.temporary-secret').count() === 1;
    const firstWarningPresent = await managerPage.locator('.temporary-secret strong').count() === 1
      && await managerPage.locator('.temporary-secret strong').isVisible();
    const livePollsBefore = liveArrivals;
    await managerPage.waitForTimeout(7500);
    const livePollsDuringFlash = liveArrivals - livePollsBefore;
    const panelSurvivedPolls = await managerPage.locator('.temporary-secret').count() === 1
      && await managerPage.locator('.temporary-secret strong').count() === 1;
    await managerPage.reload();
    const laterRenderOmitsPanel = await managerPage.locator('.temporary-secret').count() === 0;
    await managerPage.goto('/account-management/live');
    const liveOmitsPanel = await managerPage.locator('.temporary-secret').count() === 0;
    const limitedResults = [];
    for (const [name, constant] of [['shift-only', 'SHIFT_ONLY_USERNAME'], ['audit-only', 'AUDIT_ONLY_USERNAME']]) {
      const page = await newPage(); await login(page, fixtureUsername(constant), false);
      const response = await page.goto('/account-management?fixture-temporary-secret');
      const status = response?.status() ?? null;
      const pageAllowed = status === 200 && await page.locator('#accountTitle').count() === 1;
      const panelAbsent = await page.locator('.temporary-secret').count() === 0;
      limitedResults.push({ name, status, pageAllowed, panelAbsent });
      await page.close();
    }
    check('temporary-secret-synthetic-flash-authenticated-model-filtering', hookResponse?.status() === 200
      && firstRenderHasPanel && firstWarningPresent && livePollsDuringFlash >= 2 && panelSurvivedPolls
      && laterRenderOmitsPanel && liveOmitsPanel && limitedResults.every(result => result.pageAllowed && result.panelAbsent),
      { fixtureModelInputOnly: true, sentinelValueInspectedOrLogged: false, managerPageStatus: hookResponse?.status() ?? null,
        firstRenderHasPanel, firstWarningPresent, livePollsDuringFlash, panelSurvivedPolls,
        laterServerRenderOmitsPanel: laterRenderOmitsPanel, liveResponseOmitsPanel: liveOmitsPanel,
        limitedGrantResults: limitedResults, noScreenshotOrDOMTextCapture: true });
  }, 34000);

  await boundedCase('password-fixture-error-thymeleaf-guidance-keyboard-responsive-no-post', async () => {
    const page = await newPage(); await login(page);
    const viewports = [[1440, 1000, 'desktop'], [390, 844, 'mobile']];
    const errors = [
      ['current', 'Mật khẩu hiện tại không chính xác.'],
      ['ambiguous', 'Không thể xác nhận kết quả đổi mật khẩu. Hãy thử đăng nhập lại trước khi gửi lại.'],
    ];
    const outcomes = [];
    for (const [width, height, viewport] of viewports) {
      await page.setViewportSize({ width, height });
      for (const [kind, guidance] of errors) {
        const beforePosts = guards.accountPostsTerminatedLocally + guards.otherMutationsBlocked + guards.mutationPostsForwarded;
        const response = await page.goto(`/account/password?fixture-error=${kind}`);
        const error = page.locator('#passwordError');
        const pageStatus = response?.status() ?? null;
        const focusedError = await error.count() === 1 && await page.locator('#passwordError:focus').count() === 1;
        const correctGuidance = await error.locator('p').filter({ hasText: guidance }).count() === 1;
        await page.keyboard.press('Tab');
        const keyboardReachedFirstField = await page.locator('#currentPassword:focus').count() === 1;
        const fieldResults = [];
        for (const id of ['currentPassword', 'newPassword', 'confirmPassword']) {
          const field = page.locator(`#${id}`);
          fieldResults.push({ blank: await field.inputValue() === '', masked: await field.getAttribute('type') === 'password',
            visible: await field.isVisible(), enabled: await field.isEnabled() });
        }
        const togglesUsable = await page.locator('.password-toggle').count() === 3
          && await page.locator('.password-toggle:disabled').count() === 0;
        const submitUsable = await page.locator('#passwordSubmit').isEnabled();
        const card = await page.locator('.password-card').boundingBox();
        const cardFitsViewport = Boolean(card && card.x >= 0 && card.x + card.width <= width + 1);
        const afterPosts = guards.accountPostsTerminatedLocally + guards.otherMutationsBlocked + guards.mutationPostsForwarded;
        const noPost = afterPosts === beforePosts;
        const controlsUsable = fieldResults.every(field => field.blank && field.masked && field.visible && field.enabled)
          && togglesUsable && submitUsable;
        outcomes.push({ viewport, kind, pageStatus, focusedError, correctGuidance, keyboardReachedFirstField,
          controlsUsable, cardFitsViewport, noPost });
      }
    }
    check('password-fixture-error-thymeleaf-guidance-keyboard-responsive-no-post',
      outcomes.every(result => result.pageStatus === 200 && result.focusedError && result.correctGuidance
        && result.keyboardReachedFirstField && result.controlsUsable && result.cardFitsViewport && result.noPost),
      { fixtureErrorModelInputOnly: true, noPasswordPost: true, outcomes });
  }, 35000);

  await boundedCase('upload-error-fixture-exception-view-responsive-keyboard-return-no-post', async () => {
    const page = await newPage(); await login(page);
    const outcomes = [];
    for (const [width, height, viewport] of [[1440, 1000, 'desktop'], [390, 844, 'mobile']]) {
      await page.setViewportSize({ width, height });
      await page.goto('/account-management');
      const beforePosts = guards.accountPostsTerminatedLocally + guards.otherMutationsBlocked + guards.mutationPostsForwarded;
      const response = await page.goto('/account-management?fixture-upload-error');
      const status = response?.status() ?? null;
      const uploadGuidance = await page.locator('main .card .notice')
        .filter({ hasText: 'Ảnh tải lên quá lớn.' }).count() === 1;
      const actualView = await page.locator('main .card').count() === 1
        && await page.locator('main .card h1').count() === 1
        && await page.locator('main .card .notice').count() === 1 && uploadGuidance;
      const button = page.locator('main button[onclick="history.back()"]');
      const backButtonUsable = await button.count() === 1 && await button.isVisible() && await button.isEnabled();
      const card = await page.locator('main .card').boundingBox();
      const cardFitsViewport = Boolean(card && card.x >= 0 && card.x + card.width <= width + 1);
      let returnedToAccount = false;
      if (backButtonUsable) {
        await button.focus();
        await page.keyboard.press('Enter');
        await expect(page).toHaveURL(url => url.pathname === '/account-management'
          && !url.searchParams.has('fixture-upload-error'));
        returnedToAccount = await page.locator('#account-list').count() === 1;
      }
      const afterPosts = guards.accountPostsTerminatedLocally + guards.otherMutationsBlocked + guards.mutationPostsForwarded;
      outcomes.push({ viewport, status, actualView, backButtonUsable, cardFitsViewport,
        uploadGuidance, returnedToAccount, noPost: afterPosts === beforePosts });
    }
    check('upload-error-fixture-exception-view-responsive-keyboard-return-no-post',
      outcomes.every(result => result.status === 413 && result.actualView && result.backButtonUsable
        && result.cardFitsViewport && result.returnedToAccount && result.noPost),
      { syntheticServerExceptionOnly: true, noMultipartOrFileUpload: true, outcomes });
  }, 36000);

  await boundedCase('live-polling-held-get-no-overlap', async () => {
    const page = await newPage();
    const livePath = '/account-management/live';
    const requestSignal = signal();
    page.on('request', request => { if (request.method() === 'GET' && new URL(request.url()).pathname === livePath) requestSignal.done(); });
    step('login'); await login(page);
    const before = liveArrivals;
    liveHoldCount = 1;
    const arrivalSignal = signal(); liveArrivalSignal = arrivalSignal;
    step('wait-for-three-second-poll');
    await page.locator('#live-refresh-status').waitFor({ state: 'attached' });
    await deadline(requestSignal.promise, 5500);
    await deadline(arrivalSignal.promise, 1500);
    const firstArrivals = liveArrivals - before;
    step('hold-response-for-over-six-seconds');
    await page.waitForTimeout(6500);
    const arrivalsWhileHeld = liveArrivals - before;
    liveHeldRelease?.();
    await expect(page.locator('#live-refresh-status')).toBeVisible();
    check('live-polling-held-get-no-overlap', firstArrivals === 1 && arrivalsWhileHeld === 1,
      { firstGetObserved: firstArrivals === 1, noOverlapForOverSixSeconds: arrivalsWhileHeld === 1, bodyLogged: false });
  }, 24000);

  await boundedCase('live-focus-defers-two-distinct-snapshots', async () => {
    const page = await newPage(); step('login'); await login(page);
    const focusedRegion = page.locator('#live-shifts [role="region"]').first();
    await focusedRegion.focus();
    const firstSnapshot = queueLiveResponse({ marker: 'b10-snapshot-one' });
    const secondSnapshot = queueLiveResponse({ marker: 'b10-snapshot-two' });
    step('receive-two-synthetic-ssr-derived-snapshots');
    const firstFixture = await deadline(firstSnapshot, 8000);
    const secondFixture = await deadline(secondSnapshot, 8000);
    const firstMarkerApplied = await page.locator('#live-shifts').getAttribute('data-b10-snapshot');
    const status = page.locator('#live-refresh-status');
    const pendingState = await status.getAttribute('data-state');
    const activeRegionCount = await page.locator('#live-shifts [role="region"]:focus').count();
    const deferredTogether = firstFixture.markerApplied && secondFixture.markerApplied
      && firstMarkerApplied === null && pendingState === 'pending' && activeRegionCount === 1;
    step('move-focus-outside-and-check-newest-snapshot');
    await page.locator('#account-search').focus();
    await expect(page.locator('#live-shifts')).toHaveAttribute('data-b10-snapshot', 'b10-snapshot-two');
    const allRegionsNewest = (await page.locator('#live-refresh-status').getAttribute('data-b10-snapshot') === 'b10-snapshot-two')
      && (await page.locator('#live-sessions').getAttribute('data-b10-snapshot') === 'b10-snapshot-two')
      && (await page.locator('#live-shifts').getAttribute('data-b10-snapshot') === 'b10-snapshot-two');
    check('live-focus-defers-two-distinct-snapshots', deferredTogether && allRegionsNewest,
      { snapshotsWereDistinct: true, activeDomAndFocusUnchangedWhilePending: deferredTogether, newestSnapshotAppliedToAllThreeRegions: allRegionsNewest,
        snapshotBodiesLogged: false, syntheticResponsesDerivedFromRealSafeSsr: true });
  }, 26000);

  for (const kind of ['status-500', 'network', 'malformed']) {
    await boundedCase(`live-${kind}-stale-retention-valid-retry`, async () => {
      const page = await newPage(); step('login'); await login(page);
      const status = page.locator('#live-refresh-status');
      const timeBefore = await status.locator('#live-snapshot-time').getAttribute('datetime');
      const sessionsBefore = page.locator('#live-sessions'), shiftsBefore = page.locator('#live-shifts');
      const sessionTextBefore = await sessionsBefore.innerText(), shiftTextBefore = await shiftsBefore.innerText();
      const sessionRowsBefore = await sessionsBefore.locator('tr').count(), shiftRowsBefore = await shiftsBefore.locator('tr').count();
      step(`inject-${kind}-through-controlled-get`);
      if (kind === 'status-500') await requestLiveScenario(page, { kind: 'status', status: 500 });
      else if (kind === 'network') await requestLiveScenario(page, { kind: 'network' });
      else await requestLiveScenario(page, { kind: 'malformed' });
      await expect(status).toHaveAttribute('data-state', 'stale');
      const staleTimeRetained = (await status.locator('#live-snapshot-time').getAttribute('datetime')) === timeBefore;
      const sessionTextAfter = await sessionsBefore.innerText(), shiftTextAfter = await shiftsBefore.innerText();
      const sessionRowsAfter = await sessionsBefore.locator('tr').count(), shiftRowsAfter = await shiftsBefore.locator('tr').count();
      const sessionTextRetained = sessionTextAfter === sessionTextBefore;
      const shiftTextRetained = shiftTextAfter === shiftTextBefore;
      const sessionRowCountRetained = sessionRowsAfter === sessionRowsBefore;
      const shiftRowCountRetained = shiftRowsAfter === shiftRowsBefore;
      const staleRowsRetained = sessionTextRetained && shiftTextRetained
        && sessionRowCountRetained && shiftRowCountRetained;
      step('valid-get-retry');
      await requestLiveScenario(page, { marker: `b10-recovered-${kind}` });
      await expect(page.locator('#live-shifts')).toHaveAttribute('data-b10-snapshot', `b10-recovered-${kind}`);
      const recovered = ['fresh', 'updated'].includes(await status.getAttribute('data-state'));
      check(`live-${kind}-stale-retention-valid-retry`, staleTimeRetained && staleRowsRetained && recovered,
        { staleTimeRetained, staleRowsRetained, sessionTextRetained, shiftTextRetained,
          sessionRowCountRetained, shiftRowCountRetained, validRetryRecovered: recovered,
          noLiveRowTextOrHashPersisted: true, responseFixtureSynthetic: true });
    }, 24000);
  }

  await boundedCase('live-403-clears-disables-and-stops-polling', async () => {
    const page = await newPage(); step('login'); await login(page);
    const shiftForm = page.locator('#shift-assignment-form');
    if (await shiftForm.count() !== 1) return deferCoverage('live-403-clears-disables-and-stops-polling', 'fixture-shift-form-absent');
    await requestLiveScenario(page, { kind: 'status', status: 403 });
    await expect(page.locator('#live-refresh-status')).toHaveAttribute('data-state', 'forbidden');
    const sessionsCleared = await page.locator('#live-sessions > *').count() === 0;
    const shiftsCleared = await page.locator('#live-shifts > *').count() === 0;
    const shiftControlsDisabled = await shiftForm.locator(':disabled').count() > 0;
    const arrivalsAtTerminal = liveArrivals;
    step('verify-terminal-state-does-not-poll'); await page.waitForTimeout(6500);
    const timerStopped = liveArrivals === arrivalsAtTerminal;
    check('live-403-clears-disables-and-stops-polling', sessionsCleared && shiftsCleared && shiftControlsDisabled && timerStopped,
      { sessionsCleared, shiftsCleared, shiftControlsDisabled, automaticTimerStopped: timerStopped, fixtureResponseSynthetic: true });
  }, 23000);

  for (const kind of ['401', 'redirect']) {
    await boundedCase(`live-${kind}-navigates-to-login`, async () => {
      const page = await newPage(); step('login'); await login(page);
      await requestLiveScenario(page, kind === '401' ? { kind: 'status', status: 401 } : { kind: 'redirect' });
      await expect(page).toHaveURL(url => url.origin === proxyOrigin && url.pathname === '/login');
      check(`live-${kind}-navigates-to-login`, true, { loginNavigationObserved: true, controlledResponseSynthetic: true });
    }, 20000);
  }

  await boundedCase('create-valid-native-submit-two-viewports-local-only', async () => {
    for (const [width, height, suffix] of [[1440, 1000, 'desktop'], [390, 844, 'mobile']]) {
      const page = await newPage(); await page.setViewportSize({ width, height }); step(`create-${suffix}-login`); await login(page);
      await page.locator('#openCreateAccount').click();
      const form = page.locator('#create-account-form'); await fillCreateForm(form, suffix);
      const expectedUsername = `b10-synthetic-${suffix}`;
      const submit = await submitNativeAndObserve(page, '#create-account-form',
        { username: expectedUsername, fullName: 'B10 Synthetic Account' });
      const draftRetained = (await form.locator('[name="username"]').inputValue()) === expectedUsername
        && (await form.locator('[name="fullName"]').inputValue()) === 'B10 Synthetic Account';
      const fieldsRemainValid = await form.locator(':invalid').count() === 0;
      await page.goto('/account-management');
      await page.locator('#openCreateAccount').click();
      const readyAfterFreshGet = await page.locator('#create-account-form button[type="submit"]').isEnabled();
      const ok = submit.exactlyOneLocalPost && submit.payloadStructureValid && submit.firstPointerPendingEvent
        && submit.expectedTargetMatches && submit.expectedValuesMatch && readyAfterFreshGet && draftRetained && fieldsRemainValid;
      check(`create-valid-native-submit-${suffix}-local-only`, ok,
        { exactlyOneLocalPost: submit.exactlyOneLocalPost, payloadStructureValid: submit.payloadStructureValid,
          firstPointerPendingEvent: submit.firstPointerPendingEvent, secondPointerPendingEventObserved: submit.secondPointerPendingEventObserved,
          expectedTargetMatches: submit.expectedTargetMatches, expectedValuesMatch: submit.expectedValuesMatch,
          syntheticFieldsRetainedOnOriginalDocument: draftRetained, formValidOnOriginalDocument: fieldsRemainValid,
          submitReadyAfterFreshGet: readyAfterFreshGet, forwardedMutationPosts: guards.mutationPostsForwarded });
      await page.close();
    }
  }, 60000);

  await boundedCase('native-profile-submit-held-local-response', async () => {
    const page = await newPage(); step('login'); await login(page);
    const create = page.locator('#create-account-form');
    step('native-create-required-validation');
    await page.locator('#openCreateAccount').click();
    const countBefore = guards.accountPostsTerminatedLocally;
    await create.locator('button[type="submit"]').click();
    const invalidFieldCount = await create.locator(':invalid').count();
    const focusedInvalidCount = await create.locator(':focus:invalid').count();
    const noInvalidPost = guards.accountPostsTerminatedLocally === countBefore;
    await page.locator('#account-search').focus();
    check('create-native-required-validation-no-post', invalidFieldCount > 0 && focusedInvalidCount > 0 && noInvalidPost,
      { invalidControlPresent: invalidFieldCount > 0, nativeFocusOnInvalidControl: focusedInvalidCount > 0, postCountUnchanged: noInvalidPost });

    const row = page.locator('[data-account-row]').filter({ hasText: 'lumipark-browser-manager' }).first();
    const edit = row.locator('.edit-profile');
    step('open-inline-profile-editor-for-manager-fixture-row');
    await edit.click();
    const profile = page.locator('#profile-form');
    await profile.locator('[name="fullName"]').fill('B10 synthetic profile draft');
    await profile.locator('[name="phone"]').fill('+84912345678');
    await profile.locator('[name="email"]').fill('b10-profile@example.test');
    await profile.locator('[name="citizenId"]').fill('999999999991');
    await profile.locator('[name="dateOfBirth"]').fill('1990-01-01');
    await profile.locator('[name="address"]').fill('B10 synthetic address');
    const targetUsername = await profile.locator('[name="username"]').inputValue();
    const submit = await submitNativeAndObserve(page, '#profile-form',
      { username: targetUsername, fullName: 'B10 synthetic profile draft' });
    const draftRetained = (await profile.locator('[name="fullName"]').inputValue()) === 'B10 synthetic profile draft';
    const identityReady = await page.locator('#profile-identity').isVisible();
    await page.goto('/account-management');
    const freshManagerRow = page.locator('[data-account-row]').filter({ hasText: 'lumipark-browser-manager' }).first();
    await freshManagerRow.locator('.edit-profile').click();
    const profileUnlockedAfterGet = await page.locator('#save-profile').isDisabled()
      && await page.locator('#profile-form').getAttribute('aria-busy') === null;
    const identityRestored = (await page.locator('#profile-form [name="username"]').inputValue()) === targetUsername
      && await page.locator('#profile-identity').isVisible();
    check('profile-native-submit-held-local-response-original-document', submit.exactlyOneLocalPost && submit.payloadStructureValid
      && submit.firstPointerPendingEvent && submit.expectedTargetMatches && submit.expectedValuesMatch
      && draftRetained && identityReady && profileUnlockedAfterGet && identityRestored,
      { exactlyOneLocalPost: submit.exactlyOneLocalPost, payloadStructureValid: submit.payloadStructureValid,
        firstPointerPendingEvent: submit.firstPointerPendingEvent, secondPointerPendingEventObserved: submit.secondPointerPendingEventObserved,
        expectedTargetMatches: submit.expectedTargetMatches, expectedValuesMatch: submit.expectedValuesMatch,
        syntheticDraftRetainedOnOriginalDocument: draftRetained, identityPresentOnOriginalDocument: identityReady,
        profileUnlockedAfterFreshGet: profileUnlockedAfterGet, correctSyntheticIdentityRestored: identityRestored,
        forwardedMutationPosts: guards.mutationPostsForwarded });
  }, 40000);

  await boundedCase('shift-assignment-native-required-validation', async () => {
    const page = await newPage(); step('login'); await login(page);
    const form = page.locator('#shift-assignment-form');
    if (await form.count() !== 1) return deferCoverage('shift-assignment-native-required-validation', 'fixture-shift-form-absent');
    step('native-shift-required-validation');
    const before = guards.accountPostsTerminatedLocally;
    await form.locator('button[type="submit"]').click();
    const invalidCount = await form.locator(':invalid').count();
    check('shift-assignment-native-required-validation', invalidCount > 0 && guards.accountPostsTerminatedLocally === before,
      { invalidControlPresent: invalidCount > 0, postCountUnchanged: guards.accountPostsTerminatedLocally === before });
  }, 18000);

  deferCoverage('web-session-revoke-confirmation', 'a second independent Web session context is not created in this bounded helper; current-session revoke is intentionally unavailable');
  deferCoverage('password-change-post-error-behavior', 'B12 renders the existing password view from fixture-only GET model inputs; no AccountController password-change POST is sent');
  deferCoverage('multipart-size-limit-enforcement', 'B12 raises a synthetic upload exception from a safe GET; no multipart request or file upload is sent');
  deferCoverage('cross-navigation-create-and-profile-draft-retention', 'deliberately excluded from required native-pending behavior; browser BFCache/fresh-document outcomes are experimental and do not imply PII storage or an app defect');
  deferCoverage('temporary-secret-back-cache-variant', 'flash consume behavior is checked through subsequent SSR navigation/reload; browser back-cache restoration is qualified separately');
} catch (error) {
  failures.push('top-level');
  record({ case: 'top-level', outcome: 'FAIL', ...safeFailure(error) });
} finally {
  clearTimeout(overallTimer);
  abortHeldResponses();
  await context?.close().catch(() => {}); await browser?.close().catch(() => {});
  if (proxy?.listening) await new Promise(resolve => proxy.close(resolve));
  process.env.TEMP = savedTemp; process.env.TMP = savedTmp;
  try {
    const sourceAfter = await identity();
    const stable = JSON.stringify(sourceBefore) === JSON.stringify(sourceAfter);
    record({ case: 'source-before-after-identity', outcome: stable ? 'PASS' : 'FAIL', stable, entries: sourceAfter.length });
    if (!stable) failures.push('source-before-after-identity');
    const sourceBuildAfter = await sourceBuildIdentity();
    const servedIdentityStable = JSON.stringify(sourceBuildBefore) === JSON.stringify(sourceBuildAfter);
    record({ case: 'source-served-byte-identity-before-after', outcome: servedIdentityStable ? 'STABLE' : 'CHANGED', stable: servedIdentityStable,
      allPairsByteEqual: sourceBuildAfter.every(pair => pair.byteEqual), pairs: sourceBuildAfter.length });
    if (!servedIdentityStable || !sourceBuildAfter.every(pair => pair.byteEqual)) failures.push('source-served-byte-identity-before-after');
    const attemptedOutcome = failures.length ? 'FAIL' : attemptedPartials.length || globalTimedOut ? 'PARTIAL' : 'ATTEMPTED SUBSET COMPLETE — SYNTHETIC H2 ONLY';
    const summary = { status: attemptedOutcome, preflight, stage, sourceBefore, sourceAfter, sourceStable: stable,
      sourceBuildBefore, sourceBuildAfter, servedIdentityStable, observations, payloadChecks, failures, attemptedPartials, deferredCoverage, guards,
      safetyBoundary: 'Only same-origin GET/HEAD and synthetic login POST reach Spring. All account-management/password/other mutations terminate at this loopback proxy; all other mutations and external origins are blocked. Allowed form structure is reduced to booleans only. No raw body or values, CSRF, cookies, credentials, PII, HTML, temporary-secret, trace or HAR is logged, persisted or captured. H2 and proxy-fixture evidence is not live E2E or application acceptance.' };
    writeFileSync(path.join(output, 'summary.json'), JSON.stringify(summary, null, 2));
  } catch (error) {
    process.exitCode = 1;
    writeFileSync(path.join(output, 'summary.json'), JSON.stringify({ status: 'INTERRUPTED', errorKind: error?.name || 'Error', observations, failures, attemptedPartials, deferredCoverage, guards }, null, 2));
  }
}
if (failures.length || attemptedPartials.length || globalTimedOut) process.exitCode = 1;
