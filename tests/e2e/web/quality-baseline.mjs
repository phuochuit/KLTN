import { createRequire } from 'node:module';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium } = require('@playwright/test');
const runArg = process.argv[2];
const stage = process.argv[3] ?? 'web-quality-baseline';
if (!runArg || !/^[a-z0-9-]+$/.test(stage)) {
  throw new Error('Usage: node tests/e2e/web/quality-baseline.mjs <fresh-active-run-directory> [fresh-stage]');
}

const runRoot = await realpathBelowTestRuns(runArg);
const portText = (await readFile(path.join(runRoot, 'server.port'), 'utf8')).trim();
try {
  await readFile(path.join(runRoot, 'stop'));
  throw new Error('Harness stop marker exists; run directory is not active.');
} catch (error) {
  if (error.code !== 'ENOENT') throw error;
}
if (!/^\d{1,5}$/.test(portText)) throw new Error('Invalid server.port marker.');
const port = Number(portText);
if (port < 1 || port > 65535) throw new Error('Invalid server.port marker.');
const origin = `http://127.0.0.1:${port}`;
const output = path.join(runRoot, stage);
await mkdir(output, { recursive: false });
const screenshots = path.join(output, 'screenshots');
await mkdir(screenshots, { recursive: false });

const routes = [
  { path: '/login', key: 'login', anonymous: true },
  { path: '/', key: 'dashboard' },
  { path: '/registrations', key: 'registrations' },
  { path: '/cards', key: 'cards' },
  { path: '/households', key: 'households' },
  { path: '/vehicles', key: 'vehicles' },
  { path: '/security-audit?page=0', key: 'security-audit' },
  { path: '/settings', key: 'settings' },
  { path: '/account/password', key: 'account-password' },
  { path: '/slots', key: 'slots' },
  { path: '/sessions', key: 'sessions' },
  { path: '/account-management', key: 'account-management' },
  { path: '/roles', key: 'roles' },
  { path: '/pricing', key: 'pricing' },
  { path: '/payments', key: 'payments' },
];
const viewports = [
  { name: 'desktop', width: 1440, height: 1000 },
  { name: 'mobile', width: 390, height: 844 },
];
const samplesPerViewport = 3;
const targetPaths = routes.map(route => route.path.split('?')[0]);
const secretSelectors = [
  '.temporary-secret', '.temporary-password', '[data-temporary-secret]',
  '[data-temporary-password]', '[data-sensitive="temporary-password"]',
].join(',');
const records = [];
const guardEvents = [];
let loginCredentialUsed = false;

const harnessFixture = await readFile(
  path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
const browserHarness = await readFile(
  path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java'), 'utf8');
const password = harnessFixture.match(/PASSWORD = "([^"]+)"/)?.[1];
const username = browserHarness.match(/MANAGER_USERNAME = "([^"]+)"/)?.[1];
if (!password || !username) throw new Error('Synthetic browser fixture credentials are unavailable.');
const sourceBefore = await sourceIdentity();

const browser = await chromium.launch({
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  headless: true,
});
const browserVersion = browser.version();

function classifyError(text) {
  if (/ERR_ABORTED|aborted/i.test(text)) return 'request-aborted';
  if (/ERR_BLOCKED_BY_CLIENT|blocked/i.test(text)) return 'request-blocked';
  if (/timeout/i.test(text)) return 'timeout';
  if (/403|forbidden/i.test(text)) return 'authorization';
  if (/404|not found/i.test(text)) return 'not-found';
  if (/5\d\d|server error/i.test(text)) return 'server-error';
  return 'other';
}

function hash(value) {
  return createHash('sha256').update(value).digest('hex');
}

function sanitizedAssetName(url) {
  const pathname = new URL(url).pathname;
  return pathname.startsWith('/static/') || pathname.startsWith('/css/')
    || pathname.startsWith('/js/') || pathname.startsWith('/images/')
    ? pathname : `/${path.posix.basename(pathname)}`;
}

function safePublicPath(value) {
  try {
    const url = new URL(value);
    if (url.origin !== origin) return '/external-origin-resource';
    const pathname = url.pathname;
    if (targetPaths.includes(pathname) || pathname === '/error') return pathname;
    if (/^\/api\/parking\/revenue\/(?:monthly|summary|transactions)$/.test(pathname)) return pathname;
    if (/^\/(?:css|js|images|static)\//.test(pathname)) return pathname;
    if (pathname.startsWith('/api/')) return '/api/[redacted]';
    return '/[other-same-origin-resource]';
  } catch { return '/[invalid-url]'; }
}

async function screenshotIfSafe(page, label) {
  const secretPanel = page.locator(secretSelectors);
  if (await secretPanel.count() > 0) {
    for (let index = 0; index < await secretPanel.count(); index += 1) {
      if (await secretPanel.nth(index).isVisible().catch(() => false)) {
        guardEvents.push({ surface: label, outcome: 'capture-blocked-sensitive-panel-visible' });
        return false;
      }
    }
  }
  await page.screenshot({ path: path.join(screenshots, `${label}.png`), fullPage: false, animations: 'disabled' });
  return true;
}

async function attachReadOnlyCdp(page) {
  const observations = {
    network: [], timeline: [], documentStartEpochMs: null, documentStartMonotonic: null,
    loadEventMonotonic: null, loadEventEpochMs: null, mainFrameId: null,
    supported: { network: false, performanceTimeline: false, page: false },
  };
  const session = await page.context().newCDPSession(page);
  session.on('Network.requestWillBeSent', event => {
    if (event.type === 'Document' && observations.documentStartEpochMs === null && Number.isFinite(event.wallTime)) {
      observations.documentStartEpochMs = event.wallTime * 1000;
      observations.documentStartMonotonic = event.timestamp;
    }
    observations.network.push({ kind: 'request', requestId: event.requestId, type: event.type ?? 'Other', path: safePublicPath(event.request.url) });
  });
  session.on('Network.responseReceived', event => {
    const item = observations.network.findLast(entry => entry.requestId === event.requestId);
    if (item) {
      item.status = event.response.status;
      item.mimeType = event.response.mimeType;
      item.fromDiskCache = event.response.fromDiskCache;
      item.fromServiceWorker = event.response.fromServiceWorker;
      const timing = event.response.timing;
      if (timing && Number.isFinite(timing.receiveHeadersEnd) && timing.receiveHeadersEnd >= 0) item.ttfbMs = timing.receiveHeadersEnd;
    }
  });
  session.on('Network.loadingFinished', event => {
    const item = observations.network.findLast(entry => entry.requestId === event.requestId);
    if (item) item.encodedBytes = event.encodedDataLength;
  });
  session.on('Network.loadingFailed', event => {
    const item = observations.network.findLast(entry => entry.requestId === event.requestId);
    if (item) item.failure = classifyError(event.errorText ?? '');
  });
  session.on('PerformanceTimeline.timelineEventAdded', event => {
    if (observations.mainFrameId && event.event.frameId !== observations.mainFrameId) return;
    if (event.event.type === 'largest-contentful-paint') {
      const details = event.event.lcpDetails;
      const presentationTime = details?.renderTime > 0 ? details.renderTime
        : details?.loadTime > 0 ? details.loadTime : event.event.time;
      observations.timeline.push({ type: 'LCP', time: presentationTime, size: details?.size });
    } else if (event.event.type === 'layout-shift') {
      const details = event.event.layoutShiftDetails;
      if (details) observations.timeline.push({ type: 'CLS', time: event.event.time, value: details.value, hadRecentInput: details.hadRecentInput });
    }
  });
  session.on('Page.loadEventFired', event => {
    observations.loadEventMonotonic = event.timestamp;
    if (observations.documentStartMonotonic !== null && observations.documentStartEpochMs !== null) {
      observations.loadEventEpochMs = observations.documentStartEpochMs
        + (event.timestamp - observations.documentStartMonotonic) * 1000;
    }
  });

  try {
    await session.send('Network.enable');
    observations.supported.network = true;
  } catch { /* report unavailable instead of inventing metrics */ }
  try {
    await session.send('Page.enable');
    observations.supported.page = true;
    const tree = await session.send('Page.getFrameTree');
    observations.mainFrameId = tree.frameTree?.frame?.id ?? null;
  } catch { /* load timing remains NOT VERIFIED */ }
  try {
    await session.send('PerformanceTimeline.enable', { eventTypes: ['largest-contentful-paint', 'layout-shift'] });
    observations.supported.performanceTimeline = true;
  } catch { /* report unavailable instead of inventing metrics */ }
  return { session, observations };
}

async function observeKeyboardAndSharedUi(page) {
  await page.keyboard.press('Tab').catch(() => {});
  const focused = page.locator(':focus');
  const focusCount = await focused.count().catch(() => 0);
  const focusRole = focusCount === 1 ? await focused.getAttribute('role').catch(() => null) : null;
  const dialogs = await page.getByRole('dialog').count().catch(() => 0);
  const navCount = await page.locator('nav').count().catch(() => 0);
  return { tabProducedFocus: focusCount === 1, focusedRole: focusRole, visibleDialogCount: dialogs, navLandmarkCount: navCount };
}

async function visit(page, route, viewport, sample, authentication = 'authenticated') {
  const acquisitionStarted = performance.now();
  await page.setViewportSize({ width: viewport.width, height: viewport.height });
  const errors = [];
  const failedRequests = [];
  const httpErrors = [];
  const assetHashes = [];
  const assetBodyTasks = [];
  page.on('pageerror', error => errors.push({ category: 'page-error', kind: error.name || 'Error', path: route.path.split('?')[0], resourceType: 'document' }));
  page.on('console', message => {
    if (message.type() === 'error') errors.push({ category: 'console-error', kind: classifyError(message.text()), path: route.path.split('?')[0], resourceType: 'console' });
  });
  page.on('requestfailed', request => failedRequests.push({ category: classifyError(request.failure()?.errorText ?? ''), path: safePublicPath(request.url()), resourceType: request.resourceType() }));
  page.on('response', response => {
    const resourceType = response.request().resourceType();
    const url = response.url();
    if (response.status() >= 400) httpErrors.push({ status: response.status(), path: safePublicPath(url), resourceType });
    if (!['script', 'stylesheet', 'image', 'font'].includes(resourceType) || response.status() >= 400) return;
    const pathname = new URL(url).pathname;
    if (!/^\/(?:static\/|css\/|js\/|images\/)/.test(pathname)) return;
    assetBodyTasks.push((async () => {
      try {
        const body = await response.body();
        assetHashes.push({ resourceType, name: sanitizedAssetName(url), sha256: hash(body), bytes: body.byteLength });
      } catch { /* a body unavailable to Playwright remains un-fingerprinted */ }
    })());
  });
  const { session, observations } = await attachReadOnlyCdp(page);
  let response;
  let navigationError = null;
  let navigationToLoadMs = null;
  let settleDurationMs = null;
  const navigationStarted = performance.now();
  try {
    response = await page.goto(`${origin}${route.path}`, { waitUntil: 'load', timeout: 30000 });
    navigationToLoadMs = Number((performance.now() - navigationStarted).toFixed(1));
    const settleStarted = performance.now();
    await page.waitForTimeout(700);
    settleDurationMs = Number((performance.now() - settleStarted).toFixed(1));
  } catch (error) {
    navigationError = classifyError(error.name);
  }
  await Promise.all(assetBodyTasks);

  const currentUrl = new URL(page.url());
  const finalPath = currentUrl.origin === origin ? currentUrl.pathname : 'external-blocked';
  const acquisitionDurationMs = Number((performance.now() - acquisitionStarted).toFixed(1));
  const expectedPath = route.path.split('?')[0];
  const httpStatus = response?.status() ?? null;
  let documentSha256 = null;
  if (response && response.request().resourceType() === 'document') {
    try { documentSha256 = hash(await response.body()); } catch { /* retain status without a body fingerprint */ }
  }
  const navigationOutcome = navigationError ? 'BLOCKED' : finalPath !== expectedPath ? 'REDIRECTED' : httpStatus === null ? 'NO_RESPONSE' : httpStatus >= 400 ? 'HTTP_ERROR' : 'LOADED';
  const safeToCapture = navigationOutcome === 'LOADED';
  let capture = 'not-requested';
  let sharedUi = null;
  if (safeToCapture && sample === 1) {
    const label = route.key === 'login' && authentication === 'anonymous' ? `login-anonymous-${viewport.name}` : `${route.key}-${viewport.name}`;
    capture = await screenshotIfSafe(page, label) ? 'captured' : 'blocked-sensitive-content';
    sharedUi = await observeKeyboardAndSharedUi(page);
  }

  const requestSummary = observations.network.reduce((result, item) => {
    result.count += 1;
    result.bytesKnown += item.encodedBytes ?? 0;
    result.statuses[item.status ?? 'pending'] = (result.statuses[item.status ?? 'pending'] ?? 0) + 1;
    result.types[item.type] = (result.types[item.type] ?? 0) + 1;
    if (item.type === 'Document' && Number.isFinite(item.ttfbMs)) result.documentTtfbMs = item.ttfbMs;
    return result;
  }, { count: 0, bytesKnown: 0, statuses: {}, types: {}, documentTtfbMs: null });
  const perfEntries = observations.timeline;
  const lcp = perfEntries.filter(item => item.type === 'LCP').at(-1)?.time ?? null;
  const observationEndEpochMs = Date.now();
  const clsWindowEndEpochMs = observations.loadEventEpochMs === null
    ? null : observations.loadEventEpochMs + 700;
  const clsItems = perfEntries.filter(item => item.type === 'CLS' && !item.hadRecentInput
    && clsWindowEndEpochMs !== null && item.time * 1000 <= clsWindowEndEpochMs
    && item.time * 1000 <= observationEndEpochMs).sort((left, right) => left.time - right.time);
  const validClsWindow = observations.supported.performanceTimeline && observations.mainFrameId
    && clsWindowEndEpochMs !== null && navigationOutcome === 'LOADED';
  let cls = validClsWindow ? 0 : null;
  if (clsItems.length) {
    let windowStart = clsItems[0].time;
    let lastShift = windowStart;
    let windowScore = 0;
    let maxScore = 0;
    for (const shift of clsItems) {
      if (shift.time - lastShift > 1 || shift.time - windowStart > 5) {
        maxScore = Math.max(maxScore, windowScore);
        windowStart = shift.time;
        windowScore = 0;
      }
      windowScore += shift.value;
      lastShift = shift.time;
    }
    cls = Number(Math.max(maxScore, windowScore).toFixed(4));
  }
  const loadMs = observations.loadEventMonotonic !== null && observations.documentStartMonotonic !== null
    ? Number(((observations.loadEventMonotonic - observations.documentStartMonotonic) * 1000).toFixed(1)) : null;
  const validLcp = navigationOutcome === 'LOADED' && observations.mainFrameId !== null
    && observations.documentStartEpochMs !== null && lcp !== null;

  records.push({
    route: route.path.split('?')[0], expectedPath, finalPath, key: route.key, viewport: viewport.name, sample,
    authentication, status: httpStatus, documentSha256, navigation: navigationOutcome,
    navigationToLoadMs, cdpLoadEventMs: loadMs, settleDurationMs, acquisitionDurationMs,
    ttfbMs: requestSummary.documentTtfbMs,
    requestCount: requestSummary.count, encodedTransferBytes: requestSummary.bytesKnown,
    responseStatusCounts: requestSummary.statuses, resourceTypeCounts: requestSummary.types,
    assetFingerprints: assetHashes, lcpMs: validLcp
      ? Number((lcp * 1000 - observations.documentStartEpochMs).toFixed(1)) : null,
    clsLoadWindow: cls, clsWindowEnd: clsWindowEndEpochMs === null ? 'NOT VERIFIED' : 'load event + 700ms settle window',
    metricsAvailability: {
      network: observations.supported.network ? 'available' : 'NOT VERIFIED - CDP Network unavailable',
      load: observations.supported.page && loadMs !== null ? 'available' : 'NOT VERIFIED - Page load timing unavailable',
      lcp: observations.supported.performanceTimeline && validLcp ? 'top-frame LCP available' : 'NOT VERIFIED - unsupported capability, invalid navigation, or top-frame LCP event/origin unavailable',
      cls: validClsWindow ? 'top-frame load-window CLS only; observed window may be 0; not full-lifecycle CLS' : 'NOT VERIFIED - unsupported capability or invalid navigation/window',
    },
    cache: { browserHttpCache: 'disabled by Playwright route interception', server: sample === 1 ? 'initial request; state not independently cold' : 'likely warmed by prior sample; not guaranteed' },
    errors: [...errors, ...failedRequests, ...httpErrors], sharedUi, capture,
  });
  await session.detach().catch(() => {});
}

try {
  const context = await browser.newContext({ viewport: { width: viewports[0].width, height: viewports[0].height } });
  await context.route('**/*', async route => {
    const request = route.request();
    let target;
    try { target = new URL(request.url()); } catch {
      guardEvents.push({ category: 'invalid-url-blocked', resourceType: request.resourceType() });
      await route.abort();
      return;
    }
    if (target.origin !== origin || !['127.0.0.1', 'localhost'].includes(target.hostname)) {
      guardEvents.push({ category: 'external-origin-blocked', path: safePublicPath(request.url()), resourceType: request.resourceType() });
      await route.abort();
      return;
    }
    const method = request.method().toUpperCase();
    if (method === 'GET' || method === 'HEAD') return route.continue();
    if (method === 'POST' && target.pathname === '/login') return route.continue();
    guardEvents.push({ category: 'business-mutation-blocked', method, path: safePublicPath(request.url()), resourceType: request.resourceType() });
    await route.abort();
  });

  // The fixture values are already read in memory; no credential is entered until these anonymous samples finish.
  for (const viewport of viewports) {
    for (let sample = 1; sample <= samplesPerViewport; sample += 1) {
      const page = await context.newPage();
      await visit(page, routes[0], viewport, sample, 'anonymous-before-credential-entry');
      await page.close();
    }
  }

  const loginPage = await context.newPage();
  let authenticated = false;
  try {
    await loginPage.goto(`${origin}/login`, { waitUntil: 'load', timeout: 30000 });
    await loginPage.locator('input[name="username"]').fill(username);
    await loginPage.locator('input[name="password"]').fill(password);
    loginCredentialUsed = true;
    await loginPage.locator('form button[type="submit"]').first().click();
    await loginPage.waitForURL(url => url.origin === origin && url.pathname !== '/login', { timeout: 15000 });
    authenticated = true;
  } catch {
    records.push({ route: '/login', expectedPath: 'authenticated landing route', finalPath: 'not recorded', key: 'synthetic-login',
      navigation: 'BLOCKED', reason: 'synthetic-login-did-not-reach-authenticated-route' });
  }
  await loginPage.close();

  for (const route of routes.filter(item => !item.anonymous)) {
    for (const viewport of viewports) {
      for (let sample = 1; sample <= samplesPerViewport; sample += 1) {
        const page = await context.newPage();
        await visit(page, route, viewport, sample, authenticated ? 'authenticated' : 'authentication-unavailable');
        await page.close();
      }
    }
  }

  for (const viewport of viewports) {
    const page = await context.newPage();
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    let errorResponse = null;
    let navigation = 'BLOCKED';
    try { errorResponse = await page.goto(`${origin}/error`, { waitUntil: 'load', timeout: 30000 }); } catch { /* sanitized below */ }
    const errorUrl = new URL(page.url());
    const finalPath = errorUrl.origin === origin ? errorUrl.pathname : 'external-blocked';
    if (errorResponse && finalPath === '/error') navigation = errorResponse.status() < 500 ? 'LOADED' : 'HTTP_ERROR';
    const capture = navigation === 'LOADED'
      ? (await screenshotIfSafe(page, `shared-error-${viewport.name}`) ? 'captured' : 'blocked-sensitive-content')
      : 'NOT VERIFIED - error route unavailable or redirected';
    records.push({ route: '/error', expectedPath: '/error', finalPath, key: 'shared-error', viewport: viewport.name,
      sample: 1, status: errorResponse?.status() ?? null, navigation, capture });
    await page.close();
  }
  records.push({ route: 'upload-error template', key: 'shared-upload-error', viewport: 'not-applicable', sample: 0,
    status: null, capture: 'NOT VERIFIED - reachable through upload failure; baseline does not submit/upload' });
  await context.close();
} finally {
  await browser.close();
  // Credentials were read in-memory before capture and are never persisted.
}

const sourceAfter = await sourceIdentity();
const sourceStable = JSON.stringify(sourceBefore) === JSON.stringify(sourceAfter);
const summary = {
  status: sourceStable ? 'BASELINE ACQUIRED - NOT ACCEPTANCE' : 'BASELINE INVALID - SOURCE CHANGED DURING ACQUISITION',
  runDirectory: path.relative(root, runRoot).replaceAll(path.sep, '/'),
  origin: 'loopback-only (port read from supplied server.port)',
  harness: 'externally managed LoginBrowserHarnessTest; expected H2/create-drop, synthetic accounts, mocked ANPR',
  source: { stable: sourceStable, before: sourceBefore, after: sourceAfter },
  environment: { platform: process.platform, osRelease: os.release(), arch: process.arch, node: process.version, browserVersion, viewports, samplesPerRouteViewport: samplesPerViewport, cpuNetworkThrottling: 'none applied' },
  cacheState: 'Playwright context route interception disables browser HTTP cache; first client navigation and later likely-server-warmed navigations are distinguished, not browser warm-cache runs.',
  waits: { navigation: 'load', settleAfterLoadMs: 700 },
  datasetLimit: 'small synthetic H2 fixture; not production-scale evidence',
  measurements: 'Read-only CDP Network request/response/encoded bytes and top-frame PerformanceTimeline LCP plus 700ms load-window CLS. Missing/unsupported metrics or invalid navigation are NOT VERIFIED; valid empty load-window CLS is 0. This is not full-lifecycle CLS. Navigation-to-load wall time is not INP; lab observations are not field/RUM.',
  credentialHandling: { source: 'LoginTestIsolation fixture, read in-memory before screenshots', entered: loginCredentialUsed, enteredAfterAnonymousCaptures: true, persisted: false },
  screenshotAndSmokeScope: 'Playwright animations:disabled screenshots are static captures, not motion verification. Tab/focus, landmark and dialog counts are smoke observations only; drawer/dialog lifecycle is not verified.',
  guards: { externalOrigins: 'blocked', businessMutations: 'blocked except synthetic POST /login', secrets: 'known one-time/temporary secret selectors block screenshot', requestBodiesCookiesCsrfAndHar: 'not recorded' },
  guardEvents,
  recordCount: records.length,
  routeCoverage: { targetScreenCount: targetPaths.length, uniqueTargetScreensAttempted: new Set(records.filter(row => targetPaths.includes(row.route)).map(row => row.route)).size,
    nonTargetSharedErrorRecords: records.filter(row => row.route === '/error' || row.route === 'upload-error template').length },
};
await writeFile(path.join(output, 'baseline.json'), JSON.stringify({ summary, records }, null, 2), { flag: 'wx' });
await writeFile(path.join(output, 'summary.md'), renderSummary(summary, records), { flag: 'wx' });
console.log(`Baseline artifacts written: ${path.relative(root, output).replaceAll(path.sep, '/')}`);

async function realpathBelowTestRuns(candidate) {
  const { realpath, stat } = await import('node:fs/promises');
  const runsRoot = await realpath(path.join(root, '.artifacts/test-runs'));
  const resolved = await realpath(path.resolve(candidate));
  const info = await stat(resolved);
  if (!info.isDirectory() || resolved === runsRoot || !resolved.startsWith(`${runsRoot}${path.sep}`)) {
    throw new Error('Run directory must be an existing child of .artifacts/test-runs.');
  }
  return resolved;
}

async function sourceIdentity() {
  const folders = [
    'spring-web/src/main/java', 'spring-web/src/main/resources', 'spring-web/src/main/frontend',
    'spring-web/target/classes', 'spring-web/target/test-classes',
  ];
  const files = [
    'spring-web/pom.xml', 'spring-web/package.json', 'spring-web/package-lock.json',
    'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java',
    'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java',
    'tests/e2e/web/quality-baseline.mjs',
  ].map(file => path.join(root, file));
  for (const folder of folders) {
    try { await walk(path.join(root, folder), files); } catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  const manifest = [];
  for (const file of files.sort()) {
    try {
      const content = await readFile(file);
      manifest.push({ path: path.relative(root, file).replaceAll(path.sep, '/'), sha256: hash(content) });
    } catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  return { gitRevision: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(), manifest };
}

async function walk(folder, files) {
  for (const entry of await readdir(folder, { withFileTypes: true })) {
    const full = path.join(folder, entry.name);
    if (entry.isDirectory()) await walk(full, files);
    else if (entry.isFile()) files.push(full);
  }
}

function renderSummary(info, rows) {
  const metric = rows.filter(row => targetPaths.includes(row.route) && row.sample > 0);
  const routeCount = new Set(metric.map(row => row.route)).size;
  const failed = metric.filter(row => row.navigation !== 'LOADED').length;
  return [
    '# Fresh Web baseline', '',
    `Status: **${info.status}**`, '',
    `- Run: \`${info.runDirectory}\``, `- Source HEAD: \`${info.source.before.gitRevision}\`; source identity stable: **${info.source.stable}** (${info.source.before.manifest.length} source/build/config hashes).`,
    `- Coverage: ${routeCount}/15 target routes attempted, ${metric.length} samples; ${failed} target navigations not loaded at expected path/status. Shared /error is reported separately.`,
    `- Samples: ${info.environment.samplesPerRouteViewport} per route and viewport; desktop 1440×1000, mobile 390×844.`,
    `- Cache: ${info.cacheState}`, `- Dataset: ${info.datasetLimit}`,
    '- Screenshots disable animations; motion and drawer/dialog lifecycle are not verified. Keyboard/focus/landmark checks are smoke observations only.',
    '- Report metrics as NOT VERIFIED where missing; no RUM, production-scale, or human-acceptance claim.',
    '', 'See `baseline.json` for sanitized per-sample observations. Screenshots are in `screenshots/`.', '',
  ].join('\n');
}
