// Bounded, synthetic-H2 operations survey. No business POST is forwarded.
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium, expect } = require('@playwright/test');
const runArg = process.argv[2], stage = process.argv[3] ?? 'operations-quality';
assert.ok(runArg && /^[a-z0-9-]+$/.test(stage), 'Usage: node tests/e2e/web/operations-quality.mjs <active-run-directory> [fresh-stage]');
const runs = await (await import('node:fs/promises')).realpath(path.join(root, '.artifacts/test-runs'));
const run = await (await import('node:fs/promises')).realpath(path.resolve(runArg));
assert.ok(run.startsWith(`${runs}${path.sep}`), 'Run directory must resolve below .artifacts/test-runs');
const portText = (await readFile(path.join(run, 'server.port'), 'utf8')).trim();
assert.match(portText, /^\d{1,5}$/);
const port = Number(portText);
assert.ok(port > 0 && port <= 65535);
try { await readFile(path.join(run, 'stop')); throw new Error('Harness stop marker exists'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
const pidText = (await readFile(path.join(run, 'controller.pid'), 'utf8')).trim();
assert.match(pidText, /^\d+$/);
const controllerPid = Number(pidText);
let listenerProbe;
try {
  listenerProbe = execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
    `$controller = Get-Process -Id ${controllerPid} -ErrorAction SilentlyContinue; if (!$controller) { exit 2 }; $listener = Get-NetTCPConnection -State Listen -LocalPort ${port} -ErrorAction SilentlyContinue; if (!$listener) { $client = New-Object System.Net.Sockets.TcpClient; $connected = $false; try { $pending = $client.BeginConnect('127.0.0.1', ${port}, $null, $null); try { if ($pending.AsyncWaitHandle.WaitOne(1500)) { $client.EndConnect($pending); $connected = $client.Connected } } finally { $pending.AsyncWaitHandle.Close() } } finally { $client.Close() }; if (!$connected) { exit 3 }; Write-Output 'loopback-connect' } else { Write-Output 'listener-table' }`],
  { encoding: 'utf8', timeout: 5000 }).trim();
} catch { throw new Error('Harness PID or actual loopback listener is absent; helper will not restart it.'); }
const output = path.join(run, stage);
await mkdir(output, { recursive: false });
const tempDirectory = path.join(root, '.cache/test-temp', `${path.basename(run)}-${stage}`);
await mkdir(tempDirectory, { recursive: false });
const savedTemp = process.env.TEMP, savedTmp = process.env.TMP;
process.env.TEMP = tempDirectory;
process.env.TMP = tempDirectory;

const origin = `http://127.0.0.1:${port}`;
const identityFiles = [
  'tests/e2e/web/operations-quality.mjs',
  'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java',
  'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java',
];
const identityFolders = ['spring-web/src/main/java', 'spring-web/src/main/resources/static', 'spring-web/src/main/resources/templates',
  'spring-web/src/main/frontend', 'spring-web/target/classes'];
async function walk(folder, files) { for (const entry of await readdir(folder, { withFileTypes: true })) { const full=path.join(folder,entry.name); if(entry.isDirectory()) await walk(full,files); else if(entry.isFile()) files.push(full); } }
const hashes = async () => {
  const files=identityFiles.map(relative=>path.join(root,relative));
  for(const folder of identityFolders){try{if((await stat(path.join(root,folder))).isDirectory())await walk(path.join(root,folder),files);}catch(error){if(error.code!=='ENOENT')throw error;}}
  const manifest=[];
  for(const file of [...new Set(files)].sort())manifest.push({path:path.relative(root,file).replaceAll(path.sep,'/'),sha256:createHash('sha256').update(await readFile(file)).digest('hex')});
  return manifest;
};
const sourceBefore = await hashes();
const fixture = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
const harness = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java'), 'utf8');
const password = fixture.match(/PASSWORD = "([^"]+)"/)?.[1];
const username = harness.match(/MANAGER_USERNAME = "([^"]+)"/)?.[1];
assert.ok(password && username, 'Synthetic login fixture unavailable');

const outcomes = [], guards = { loginForwarded: 0, businessPostsStubbed: 0, otherMutationsBlocked: 0, externalBlocked: 0 };
let browser;
try {
  browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  const context = await browser.newContext();
  await context.route('**/*', async route => {
    const request = route.request();
    let target;
    try { target = new URL(request.url()); } catch { guards.externalBlocked++; return route.abort(); }
    if (target.origin !== origin) { guards.externalBlocked++; return route.abort(); }
    const method = request.method().toUpperCase();
    if (method === 'GET' || method === 'HEAD') return route.continue();
    if (method === 'POST' && target.pathname === '/login') { guards.loginForwarded++; return route.continue(); }
    if (method === 'POST' && target.pathname.startsWith('/slots')) {
      guards.businessPostsStubbed++;
      return route.fulfill({ status: 204, body: '' }).catch(() => {});
    }
    guards.otherMutationsBlocked++;
    return route.abort();
  });
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  page.setDefaultNavigationTimeout(5000);
  const errors = [];
  page.on('pageerror', error => errors.push(error.name || 'Error'));
  await page.goto(`${origin}/login`, { waitUntil: 'load' });
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#loginForm button[type=submit]').click();
  await page.waitForURL(url => url.origin === origin && url.pathname !== '/login');

  const record = async (name, test) => {
    try { await test(); outcomes.push({ case: name, outcome: 'PASS' }); }
    catch (error) { outcomes.push({ case: name, outcome: 'FAIL', errorKind: error.name || 'Error' }); }
  };

  await record('sessions GET query/status/date/sort/page URL and native history', async () => {
    await page.goto(`${origin}/sessions`);
    await page.locator('input[name=q]').fill('QA-HISTORY-05');
    await page.locator('select[name=status]').selectOption('COMPLETED');
    await page.locator('input[name=from]').fill('2026-01-01');
    await page.locator('select[name=sort]').selectOption('oldest');
    await page.locator('.sessions-filter-form button[type=submit]').click();
    await expect(page).toHaveURL(/q=QA-HISTORY-05/);
    await expect(page).toHaveURL(/status=COMPLETED/);
    await expect(page).toHaveURL(/from=2026-01-01/);
    await expect(page).toHaveURL(/sort=oldest/);
    await expect(page.locator('.sessions-table-scroll tbody tr')).toHaveCount(1);
    await page.goBack({ waitUntil: 'domcontentloaded', timeout: 5000 });
    await expect(page).toHaveURL(/\/sessions(?:\?|$)/);
    await expect(page.locator('input[name=q]')).toHaveValue('');
  });

  await record('sessions invalid/reversed dates show no unfiltered data; clear returns genuine fixture', async () => {
    await page.goto(`${origin}/sessions?from=2026-12-31&to=2026-01-01`);
    await expect(page.locator('.sessions-date-error')).toBeVisible();
    await expect(page.locator('.sessions-table-scroll tbody tr:not(:has(.sessions-empty-row))')).toHaveCount(0);
    await page.goto(`${origin}/sessions?q=UXQA-NO-MATCH`);
    await expect(page.locator('.sessions-result-summary')).toContainText('Không có lượt xe phù hợp');
    await page.locator('.sessions-filter-form a[href="/sessions"]').click();
    await expect(page.locator('.sessions-table-scroll tbody tr')).not.toHaveCount(0);
  });

  await record('sessions named horizontal region reaches last column by keyboard', async () => {
    const table = page.locator('.sessions-table-scroll');
    await table.focus();
    await page.keyboard.press('End');
    await expect(page.locator('.sessions-table-scroll th').last()).toContainText('Phí lượt');
    assert.ok(await table.getAttribute('aria-label'));
  });

  await record('audit filters, URL pagination, and keyboard disclosure', async () => {
    await page.goto(`${origin}/security-audit?outcome=FAILURE`);
    await expect(page.locator('select[name=outcome]')).toHaveValue('FAILURE');
    await expect(page.locator('.audit-table tbody tr')).not.toHaveCount(0);
    await page.goto(`${origin}/security-audit?page=1`);
    await expect(page).toHaveURL(/page=1/);
    const disclosure = page.locator('.audit-value-disclosure').first();
    if (await disclosure.count()) {
      await disclosure.locator('summary').focus();
      await page.keyboard.press('Enter');
      await expect(disclosure.locator('p')).toBeVisible();
      await page.keyboard.press('Enter');
      await expect(disclosure.locator('p')).toBeHidden();
    }
  });

  await record('audit genuine empty and invalid filter are distinct from repository outage', async () => {
    await page.goto(`${origin}/security-audit?actor=UXQA-no-matching-actor`);
    const genuineEmpty = await page.locator('.audit-empty-state h3').textContent();
    await page.goto(`${origin}/security-audit?from=not-a-date`);
    await expect(page.locator('.audit-alert.is-invalid')).toBeVisible();
    assert.notEqual(genuineEmpty, await page.locator('.audit-alert.is-invalid strong').textContent());
    outcomes.push({ case: 'audit repository outage HTML', outcome: 'NOT RUN', reason: 'Covered by a fresh isolated H2 integration test; not faked as live browser evidence' });
  });

  await record('slot detail tabs, Escape/focus return, and assignment reset on reopen', async () => {
    await page.goto(`${origin}/slots`);
    const slot = page.locator('.slot-tile').first();
    await slot.focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('#slotDialog')).toBeVisible();
    await expect(page.locator('#modalSlotTitle')).not.toBeEmpty();
    await page.locator('#residentTab').focus();
    await page.keyboard.press('ArrowRight');
    await expect(page.locator('#borrowTab')).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('Escape');
    await expect(page.locator('#slotDialog')).toBeHidden();
    await expect(slot).toBeFocused();
    await slot.click();
    await expect(page.locator('#assignVehicleSelect')).toHaveValue('');
    await expect(page.locator('#assignVehicleButton')).toBeDisabled();
    await page.keyboard.press('Escape');
  });

  await record('slot release consequence and cancel remain non-mutating', async () => {
    await page.goto(`${origin}/slots`);
    await page.locator('.slot-tile').first().click();
    await page.locator('#manageTab').click();
    await expect(page.locator('.slot-release-area')).toContainText('không đóng lượt hoặc hoàn tiền');
    await page.locator('#showReleaseConfirm').click();
    await expect(page.locator('#releaseConfirmation')).toBeVisible();
    await page.locator('#cancelRelease').click();
    await expect(page.locator('#releaseConfirmation')).toBeHidden();
  });

  outcomes.push({ case: 'slot representative POST serialization/pending', outcome: 'NOT RUN', reason: 'No POST activated in this read-only survey; optional local-stub case remains for a separately bounded refinement' });
  outcomes.push({ case: 'slot layout metrics when CDP supported', outcome: 'NOT RUN', reason: 'This helper uses DOM/keyboard assertions only; geometry capture remains pending' });
  assert.deepEqual(errors, []);
  await context.close();
} catch (error) {
  outcomes.push({ case: 'preflight-or-run', outcome: 'PARTIAL', errorKind: error.name || 'Error' });
  process.exitCode = 1;
} finally {
  await browser?.close().catch(() => {});
  const sourceAfter = await hashes().catch(() => null);
  const sourceStable = sourceAfter !== null && JSON.stringify(sourceBefore) === JSON.stringify(sourceAfter);
  if (savedTemp === undefined) delete process.env.TEMP; else process.env.TEMP = savedTemp;
  if (savedTmp === undefined) delete process.env.TMP; else process.env.TMP = savedTmp;
  const summary = { status: 'BOUNDED OPERATIONS SURVEY — NOT APPLICATION ACCEPTANCE', sourceStable,
    preflight: { controllerPidAlive: true, servicePort: port, listenerProbe, stopMarker: 'absent' },
    tempLocation: path.relative(root, tempDirectory).replaceAll(path.sep, '/'), cases: outcomes, guards,
    limits: ['Synthetic isolated H2/mock-ANPR only; no production or live-E2E claim', 'No actual slot/business POST forwarded', 'No screenshots, trace, HAR, cookies, CSRF/body/value output', 'Audit repository-outage rendering requires fresh-H2 integration evidence'] };
  await writeFile(path.join(output, 'operations-quality.json'), JSON.stringify(summary, null, 2), { flag: 'wx' });
  if (!sourceStable) process.exitCode = 1;
}
