import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { appendFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium } = require('@playwright/test');
const runArg = process.argv[2];
const stage = process.argv[3] ?? 'b4-interaction-survey';
if (!runArg || !/^[a-z0-9-]+$/.test(stage)) throw new Error('Usage: node tests/e2e/web/quality-interactions.mjs <active-run-directory> [fresh-stage]');

const { realpath, stat } = await import('node:fs/promises');
const runs = await realpath(path.join(root, '.artifacts/test-runs'));
const run = await realpath(path.resolve(runArg));
if (!run.startsWith(`${runs}${path.sep}`) || !(await stat(run)).isDirectory()) throw new Error('Run path must be inside .artifacts/test-runs.');
const port = Number((await readFile(path.join(run, 'server.port'), 'utf8')).trim());
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid harness port.');
try { await readFile(path.join(run, 'stop')); throw new Error('Harness is stopped.'); } catch (error) { if (error.code !== 'ENOENT') throw error; }
const controllerText = (await readFile(path.join(run, 'controller.pid'), 'utf8')).trim();
if (!/^\d+$/.test(controllerText)) throw new Error('Invalid controller PID marker.');
const controllerPid = Number(controllerText);
try {
  execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
    `$controller = Get-Process -Id ${controllerPid} -ErrorAction SilentlyContinue; $listener = Get-NetTCPConnection -State Listen -LocalPort ${port} -ErrorAction SilentlyContinue; if (!$controller -or !$listener) { exit 2 }`],
  { stdio: 'ignore', timeout: 5000 });
} catch { throw new Error('Harness process or actual service listener is absent; do not restart from survey helper.'); }
const harnessPreflight = { controllerPid, controllerLiveness: 'Get-Process confirmed alive', servicePort: port, serviceListener: 'loopback listener present', stopMarker: 'absent' };
const output = path.join(run, stage);
await mkdir(output, { recursive: false });
const tempDirectory = path.join(root, '.cache/test-temp', `${path.basename(run)}-${stage}`);
await mkdir(tempDirectory, { recursive: false });
const savedTemp = process.env.TEMP;
const savedTmp = process.env.TMP;
process.env.TEMP = tempDirectory;
process.env.TMP = tempDirectory;
const origin = `http://127.0.0.1:${port}`;
const routes = ['/login', '/', '/registrations', '/cards', '/households', '/vehicles', '/security-audit?page=0', '/settings', '/account/password', '/slots', '/sessions', '/account-management', '/roles', '/pricing', '/payments'];
const sizes = [{ width: 1440, height: 1000 }, { width: 1280, height: 720 }, { width: 390, height: 844 }, { width: 320, height: 740 }];
const observations = [];
const guards = { blocked: 0, forwardedLogin: 0 };
const assertionFailures = [];
const runtimeErrors = [];
const partialPath = path.join(output, 'observations.jsonl');
writeFileSync(partialPath, '', { flag: 'wx' });
const fixture = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
const harness = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java'), 'utf8');
const password = fixture.match(/PASSWORD = "([^"]+)"/)?.[1];
const username = harness.match(/MANAGER_USERNAME = "([^"]+)"/)?.[1];
if (!password || !username) throw new Error('Synthetic fixture unavailable.');
let browser = null;
let context = null;
let sourceBefore = null;
let sourceAfter = null;

function record(data) {
  observations.push(data);
  appendFileSync(partialPath, `${JSON.stringify(data)}\n`, { encoding: 'utf8' });
}
function check(id, ok, detail = {}) {
  record({ assertion: id, outcome: ok ? 'PASS' : 'FAIL', ...detail });
  if (!ok) assertionFailures.push(id);
}
async function walk(folder, files) {
  for (const entry of await readdir(folder, { withFileTypes: true })) {
    const full = path.join(folder, entry.name);
    if (entry.isDirectory()) await walk(full, files);
    else if (entry.isFile()) files.push(full);
  }
}
async function sourceIdentity() {
  const paths = [
    'tests/e2e/web/quality-interactions.mjs',
    'spring-web/pom.xml', 'spring-web/package.json', 'spring-web/package-lock.json',
    'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java',
    'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java',
  ].map(file => path.join(root, file));
  for (const folder of [
    'spring-web/src/main/java', 'spring-web/src/main/resources/templates',
    'spring-web/src/main/resources/static', 'spring-web/src/main/frontend',
    'spring-web/target/classes', 'spring-web/target/test-classes',
  ]) await walk(path.join(root, folder), paths);
  const manifest = [];
  for (const file of [...new Set(paths)].sort()) {
    const bytes = await readFile(file);
    manifest.push({ path: path.relative(root, file).replaceAll(path.sep, '/'), sha256: createHash('sha256').update(bytes).digest('hex') });
  }
  return { manifest };
}
async function captureRoute(page, route, size) {
  await page.setViewportSize(size);
  const cdp = await context.newCDPSession(page);
  let response = null;
  let error = null;
  let readiness = 'NOT VERIFIED';
  try {
    response = await page.goto(`${origin}${route}`, { waitUntil: 'load', timeout: 5000 });
    await page.waitForTimeout(100);
    readiness = 'load + 100ms settle';
  } catch (e) { error = e.name === 'TimeoutError' ? 'timeout' : 'navigation-error'; }
  let metrics = null;
  try {
    let previous = null;
    for (let i = 0; i < 3; i++) {
      const m = await cdp.send('Page.getLayoutMetrics');
      metrics = { contentWidth: m.cssContentSize.width, viewportWidth: m.cssLayoutViewport.clientWidth };
      if (previous && previous.contentWidth === metrics.contentWidth && previous.viewportWidth === metrics.viewportWidth) { readiness += ' + stable CDP metrics'; break; }
      previous = metrics;
      await page.waitForTimeout(100);
    }
  } catch { metrics = null; }
  const pathName = new URL(page.url()).pathname;
  let headingPresent = false;
  let controlsNamed = null;
  try {
    headingPresent = await page.locator('main').first().isVisible() && await page.locator('main h1').first().isVisible();
    const controls = page.locator('main input:not([type=hidden]), main select, main textarea, main button');
    controlsNamed = true;
    for (let i = 0; controlsNamed && i < await controls.count(); i++) {
      const control = controls.nth(i);
      if (!await control.isVisible()) continue;
      const id = await control.getAttribute('id');
      const labelledby = (await control.getAttribute('aria-labelledby'))?.split(/\s+/).filter(Boolean) ?? [];
      const referencedName = labelledby.length > 0 && (await Promise.all(labelledby.map(name => page.locator(`[id="${name}"]`).count()))).every(count => count > 0);
      const named = await control.getAttribute('aria-label') || referencedName
        || (id && await page.locator(`label[for="${id}"]`).count())
        || await control.locator('xpath=ancestor::label[1]').count()
        || (await control.innerText().catch(() => '')).trim();
      if (!named) controlsNamed = false;
    }
  } catch { controlsNamed = false; }
  const viewportMeta = await page.locator('meta[name="viewport"]').count().then(n => n > 0).catch(() => false);
  const overflow = metrics ? metrics.contentWidth > metrics.viewportWidth + 1 : null;
  check(`route:${route.split('?')[0]}:${size.width}x${size.height}`, response?.status() === 200 && pathName === route.split('?')[0] && !error && viewportMeta && headingPresent && controlsNamed && readiness.includes('stable CDP metrics') && overflow === false,
    { route: route.split('?')[0], viewport: `${size.width}x${size.height}`, status: response?.status() ?? null, finalPath: pathName, expectedPath: route.split('?')[0], navigationError: error, viewportMeta, mainAndH1Visible: headingPresent, visibleControlsHaveBasicNameSignal: controlsNamed, readiness, cdpWidths: metrics, horizontalOverflow: overflow });
  await cdp.detach().catch(() => {});
}

try {
  sourceBefore = await sourceIdentity();
  browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  context = await browser.newContext();
  await context.route('**/*', async route => {
    const req = route.request();
    const url = new URL(req.url());
    if (url.origin !== origin || !['127.0.0.1', 'localhost'].includes(url.hostname)) { guards.blocked++; return route.abort(); }
    const method = req.method().toUpperCase();
    if (method === 'GET' || method === 'HEAD') return route.continue();
    if (method === 'POST' && url.pathname === '/login') { guards.forwardedLogin++; return route.continue(); }
    guards.blocked++;
    return route.abort();
  });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  page.setDefaultNavigationTimeout(5000);
  page.on('pageerror', error => runtimeErrors.push({ category: 'page-error', kind: error.name || 'Error' }));
  page.on('console', message => { if (message.type() === 'error') runtimeErrors.push({ category: 'console-error' }); });
  for (const size of sizes) {
    try { await captureRoute(page, '/login', size); }
    catch (error) { check(`route:/login:${size.width}x${size.height}`, false, { errorKind: error?.name ?? 'Error', outcome: 'partial-probe-error' }); }
  }
  await page.goto(`${origin}/login`, { waitUntil: 'load', timeout: 5000 });
  await page.locator('#loginForm').locator('button[type=submit]').click();
  check('login-empty-native-validation', await page.locator('#username:invalid').count() > 0,
    { feedbackVisible: await page.locator('#login-error').isVisible().catch(() => false) });
  const loginToggle = page.locator('#togglePassword');
  if (await loginToggle.isVisible().catch(() => false)) {
    await loginToggle.click();
    check('login-empty-password-visibility', await page.locator('#password').getAttribute('type') === 'text' && await loginToggle.getAttribute('aria-pressed') === 'true');
    await loginToggle.click();
  } else check('login-empty-password-visibility-available', false);
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#loginForm button[type=submit]').click();
  await page.waitForURL(url => url.origin === origin && url.pathname !== '/login', { timeout: 5000 }).catch(() => {});
  check('synthetic-login-reached-authenticated-route', new URL(page.url()).origin === origin && new URL(page.url()).pathname !== '/login');
  for (const route of routes.slice(1)) for (const size of sizes) {
    try { await captureRoute(page, route, size); }
    catch (error) { check(`route:${route.split('?')[0]}:${size.width}x${size.height}`, false, { errorKind: error?.name ?? 'Error', outcome: 'partial-probe-error' }); }
  }
  for (const size of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(size);
    try {
    await page.goto(`${origin}/settings`, { waitUntil: 'load', timeout: 5000 });
    const twoWheelers = page.locator('#twoWheelerLimit');
    const originalLimit = Number(await twoWheelers.inputValue());
    await twoWheelers.fill('');
    await page.locator('#saveLimitButton').click();
    check(`settings-empty-native-validation:${size.width}`, await page.locator('#twoWheelerLimit:invalid').count() > 0);
    await twoWheelers.fill(String(originalLimit));
    await twoWheelers.fill(String(originalLimit + 1));
    check(`settings-draft:${size.width}`, await page.locator('#limitDraftState').isVisible() && await page.locator('#saveLimitButton').isEnabled() && await page.locator('#resetLimitButton').isVisible());
    await page.locator('#resetLimitButton').click();
    check(`settings-reset:${size.width}`, Number(await twoWheelers.inputValue()) === originalLimit && await page.locator('#saveLimitButton').isDisabled());
    record({ probe: `settings-submit:${size.width}`, outcome: 'NOT RUN — reserved for separate bounded pending protocol' });
    } catch (error) {
      record({ probe: `settings:${size.width}`, errorKind: error?.name ?? 'Error', outcome: 'PARTIAL' });
    }

    try {
    await page.goto(`${origin}/account/password`, { waitUntil: 'load', timeout: 5000 });
    await page.locator('#passwordForm button[type=submit]').click({ noWaitAfter: true, timeout: 5000 });
    check(`password-empty-native-validation:${size.width}`, await page.locator('#currentPassword:invalid').count() > 0);
    const toggles = page.locator('.password-toggle');
    check(`password-toggle-count:${size.width}`, await toggles.count() === 3);
    for (let i = 0; i < await toggles.count(); i++) {
      const toggle = toggles.nth(i);
      const targetId = await toggle.getAttribute('aria-controls');
      const target = page.locator(`#${targetId}`);
      await toggle.click();
      check(`password-empty-visibility:${targetId}:${size.width}`, await target.getAttribute('type') === 'text' && await toggle.getAttribute('aria-pressed') === 'true' && await target.inputValue() === '');
      await toggle.click();
    }
    record({ probe: `password-submit:${size.width}`, outcome: 'NOT RUN — reserved for separate bounded pending protocol' });
    } catch (error) {
      record({ probe: `account-password:${size.width}`, errorKind: error?.name ?? 'Error', outcome: 'PARTIAL' });
    }

    try {
    await page.goto(`${origin}/roles`, { waitUntil: 'load', timeout: 5000 });
    const choices = page.locator('.role-choice');
    const roleName = page.locator('#roleDisplayName');
    if (await choices.count() > 0 && await roleName.isEditable()) {
      await roleName.fill(`${(await roleName.inputValue()).slice(0, 90)} B4`);
      check(`roles-dirty-state:${size.width}`, await page.locator('#roleDraftState.is-dirty').count() === 1);
      if (await choices.count() > 1) {
        const keepPrompt = page.locator('#roleDiscardPrompt');
        await choices.nth(1).click();
        check(`roles-switch-prompts:${size.width}`, await keepPrompt.isVisible());
        await page.locator('#keepRoleDraft').click();
        check(`roles-keep-draft:${size.width}`, await keepPrompt.isHidden() && await page.locator('#roleDraftState.is-dirty').count() === 1);
        await choices.nth(1).click();
        await page.locator('#discardRoleDraft').click();
        check(`roles-discard-draft:${size.width}`, await keepPrompt.isHidden() && await page.locator('#roleDraftState.is-dirty').count() === 0);
      }
      await roleName.fill(`${(await roleName.inputValue()).slice(0, 90)} B4`);
      await page.locator('#cancelRoleChanges').click();
      check(`roles-cancel-prompts:${size.width}`, await page.locator('#roleDiscardPrompt').isVisible());
      await page.locator('#keepRoleDraft').click();
      check(`roles-cancel-keep:${size.width}`, await page.locator('#roleDraftState.is-dirty').count() === 1);
    await page.locator('#cancelRoleChanges').click();
    await page.locator('#discardRoleDraft').click();
    check(`roles-cancel-discard:${size.width}`, await page.locator('#roleDraftState.is-dirty').count() === 0);
    } else check(`roles-draft-fixture:${size.width}`, false, { reason: 'required role choice/editor unavailable' });
    await page.locator('#newRoleButton').click();
    await page.locator('#saveRoleButton').click({ noWaitAfter: true, timeout: 5000 });
    check(`roles-empty-native-validation:${size.width}`, await page.locator('#roleDisplayName:invalid').count() > 0);
    record({ probe: `roles-submit:${size.width}`, outcome: 'NOT RUN — reserved for separate bounded pending protocol' });
    } catch (error) {
      record({ probe: `roles:${size.width}`, errorKind: error?.name ?? 'Error', outcome: 'PARTIAL' });
    }
  }
  await page.close();
} catch (error) {
  record({ probe: 'run', errorKind: error?.name ?? 'Error', messageClass: 'probe-incomplete' });
} finally {
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  sourceAfter = await sourceIdentity().catch(() => null);
  const sourceStable = sourceBefore !== null && sourceAfter !== null && JSON.stringify(sourceBefore) === JSON.stringify(sourceAfter);
  if (!sourceStable) assertionFailures.push('source-identity-stable');
  if (savedTemp === undefined) delete process.env.TEMP; else process.env.TEMP = savedTemp;
  if (savedTmp === undefined) delete process.env.TMP; else process.env.TMP = savedTmp;
  const routeAssertions = observations.filter(item => typeof item.assertion === 'string' && item.assertion.startsWith('route:'));
  const partial = observations.some(item => item.outcome === 'PARTIAL' || item.probe === 'run');
  const summary = { status: assertionFailures.length === 0 && routeAssertions.length === routes.length * sizes.length && !partial ? 'LAYOUT/FORM-DRAFT SUBSURVEY COMPLETE — SUBMIT PENDING NOT RUN' : 'PARTIAL/FAILURES - NOT ACCEPTANCE', harness: 'isolated H2/mock ANPR', pendingProtocol: 'NOT RUN; requires separate bounded protocol', harnessPreflight, sourceIdentity: { stable: sourceStable, before: sourceBefore, after: sourceAfter }, tempLocation: path.relative(root, tempDirectory).replaceAll(path.sep, '/'), routeCount: routes.length, routeAssertions: routeAssertions.length, expectedRouteAssertions: routes.length * sizes.length, sizes, assertionFailures, guards, runtimeErrors, observations };
  await writeFile(path.join(output, 'quality-interactions.json'), JSON.stringify(summary, null, 2), { flag: 'wx' });
}
