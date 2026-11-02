// Bounded synthetic-fixture browser evidence for management editor recovery.
// Usage: node tests/e2e/web/directory-role-recovery.mjs <fresh-run-dir> [fresh-stage]
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { appendFileSync, writeFileSync } from 'node:fs';
import { mkdir, readFile, readdir, realpath, stat, writeFile } from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium } = require('@playwright/test');
const runArg = process.argv[2];
const stage = process.argv[3] ?? 'b7a-directory-role-recovery';
const rolesOnly = process.argv.includes('--roles-only');
if (!runArg || !/^[a-z0-9-]+$/.test(stage)) {
  throw new Error('Usage: node tests/e2e/web/directory-role-recovery.mjs <fresh-run-dir> [fresh-stage]');
}

const runs = await realpath(path.join(root, '.artifacts/test-runs'));
const run = await realpath(path.resolve(runArg));
if (!run.startsWith(`${runs}${path.sep}`) || !(await stat(run)).isDirectory()) throw new Error('Run must be below .artifacts/test-runs.');
const port = Number((await readFile(path.join(run, 'server.port'), 'utf8')).trim());
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid service port marker.');
try { await readFile(path.join(run, 'stop')); throw new Error('Fixture stop marker exists.'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
const controllerText = (await readFile(path.join(run, 'controller.pid'), 'utf8')).trim();
if (!/^\d+$/.test(controllerText)) throw new Error('Invalid controller PID marker.');
const controllerPid = Number(controllerText);
execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
  `$controller = Get-Process -Id ${controllerPid} -ErrorAction SilentlyContinue; if (!$controller) { exit 2 }`],
{ stdio: 'ignore', timeout: 5000 });
await new Promise((resolve, reject) => {
  const socket = net.connect(port, '127.0.0.1', resolve);
  socket.setTimeout(1500, () => { socket.destroy(); reject(new Error('Loopback connection timed out.')); });
  socket.on('error', reject);
  socket.on('connect', () => socket.end());
});

const output = path.join(run, stage);
await mkdir(output, { recursive: false });
const partialPath = path.join(output, 'observations.jsonl');
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
const skippedCases = [];
const pageErrors = [];
const consoleErrors = [];
const casePages = new Set();
const guards = {
  syntheticLoginForwarded: 0, businessPostsStubbed: 0, otherMutationsBlocked: 0,
  externalRequestsBlocked: 0, forwardedBusinessPosts: 0,
};
let browser = null, context = null, sourceBefore = null, sourceAfter = null;
let activeWrite = null, overallTimer = null, currentCase = 'setup';
let password = null, username = null;
let memberPosts = 0, vehiclePosts = 0, rolePosts = 0;
const preflight = { controllerPid, processPresent: true, servicePort: port, loopbackConnected: true, stopMarker: 'absent' };

function record(value) {
  observations.push(value);
  appendFileSync(partialPath, `${JSON.stringify(value)}\n`, 'utf8');
}
function check(id, ok, detail = {}) {
  record({ case: id, outcome: ok ? 'PASS' : 'FAIL', ...detail });
  if (!ok) failures.push(id);
}
function notVerified(id, missingHook) {
  partialCases.push(id);
  record({ case: id, outcome: 'NOT VERIFIED', missingHook });
}
function deadline(promise, ms, label = 'case-timeout') {
  let timer;
  return Promise.race([promise, new Promise((_, reject) => {
    timer = setTimeout(() => reject(Object.assign(new Error(label), { boundedDeadline: true })), ms);
  })])
    .finally(() => clearTimeout(timer));
}
async function boundedCase(id, work, ms = 18000) {
  currentCase = id;
  record({ case: id, outcome: 'STARTED', deadlineMs: ms });
  try { await deadline(work(), ms); }
  catch (error) {
    const timedOut = error?.boundedDeadline === true || error?.name === 'TimeoutError';
    if (timedOut) {
      partialCases.push(id);
      record({ case: id, outcome: 'NOT VERIFIED — bounded case ended', errorKind: error.name || 'Error', deadlineMs: ms,
        note: 'Timeout is inconclusive and is not an application finding.' });
    } else check(id, false, { errorKind: error?.name || 'Error', step: currentCase });
  } finally {
    if (activeWrite) activeWrite.release();
    activeWrite = null;
    await Promise.all([...casePages].map(page => page.close().catch(() => {})));
    casePages.clear();
  }
}
async function newPage() {
  const page = await context.newPage();
  casePages.add(page);
  page.once('close', () => casePages.delete(page));
  page.setDefaultTimeout(5000);
  page.setDefaultNavigationTimeout(7000);
  return page;
}
async function walk(folder, files) {
  for (const entry of await readdir(folder, { withFileTypes: true })) {
    const full = path.join(folder, entry.name);
    if (entry.isDirectory()) await walk(full, files);
    else if (entry.isFile()) files.push(full);
  }
}
async function sourceIdentity() {
  const explicit = [
    'tests/e2e/web/directory-role-recovery.mjs',
    'spring-web/src/main/resources/templates/households.html',
    'spring-web/src/main/resources/templates/vehicles.html',
    'spring-web/src/main/resources/templates/security-roles.html',
    'spring-web/src/main/resources/static/js/management-core.js',
    'spring-web/src/main/resources/static/js/security-roles.js',
    'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java',
    'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java',
  ].map(file => path.join(root, file));
  for (const folder of ['spring-web/src/main/java', 'spring-web/src/main/resources/static',
    'spring-web/src/main/resources/templates', 'spring-web/src/main/frontend',
    'spring-web/target/classes', 'spring-web/target/test-classes']) {
    try { await walk(path.join(root, folder), explicit); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  const manifest = [];
  for (const file of [...new Set(explicit)].sort()) {
    const bytes = await readFile(file);
    manifest.push({ path: path.relative(root, file).replaceAll(path.sep, '/'),
      sha256: createHash('sha256').update(bytes).digest('hex') });
  }
  return manifest;
}
function requestFields(request) {
  const body = request.postDataBuffer();
  const contentType = request.headers()['content-type'] || '';
  const fields = new Map();
  if (!body) return fields;
  const text = body.toString('utf8');
  if (contentType.includes('application/x-www-form-urlencoded')) {
    for (const [key, value] of new URLSearchParams(text)) fields.set(key, value);
  } else {
    const boundary = contentType.match(/boundary=(?:"([^"]+)"|([^;]+))/i);
    if (boundary) {
      for (const part of text.split(`--${boundary[1] || boundary[2]}`)) {
        const split = part.indexOf('\r\n\r\n');
        if (split < 0) continue;
        const name = part.slice(0, split).match(/name="([^"]+)"/i)?.[1];
        if (!name) continue;
        fields.set(name, part.slice(split + 4).replace(/\r\n--?\s*$/, '').replace(/\r\n$/, ''));
      }
    }
  }
  return fields;
}
function summarizeRequest(request, expected = {}) {
  const fields = requestFields(request);
  const fieldPresence = Object.fromEntries(Object.keys(expected).map(key => [key, fields.has(key)]));
  const fieldValueMatch = Object.fromEntries(Object.entries(expected).map(([key, value]) => [key, fields.get(key) === value]));
  return {
    fieldPresence, fieldValueMatch,
    csrfPresent: fields.has('_csrf') && Boolean(fields.get('_csrf')),
    emptyPermissionSet: !fields.has('permissions'),
  };
}
function armWrite(pathname, outcome, expected = {}) {
  let arrivedResolve, releaseResolve, completeResolve;
  const arrived = new Promise(resolve => { arrivedResolve = resolve; });
  const released = new Promise(resolve => { releaseResolve = resolve; });
  const completed = new Promise(resolve => { completeResolve = resolve; });
  const write = { pathname, outcome, expected, count: 0, arrived, arrivedResolve, released, completed,
    release: releaseResolve, completeResolve, requestSummary: null, stubStatus: null };
  activeWrite = write;
  return write;
}
function writeOutcome(write) {
  if (write.outcome === 'network') return { kind: 'abort' };
  if (write.outcome === 'known') return { kind: 'response', status: 400, body: '' };
  if (write.outcome === 'unknown') return { kind: 'response', status: 503, body: '' };
  if (write.outcome === 'session') return { kind: 'redirect', status: 302, location: '/login' };
  if (write.outcome === 'success') return { kind: 'redirect', status: 303,
    location: write.pathname === '/members' ? '/households?mode=residents' : '/vehicles' };
  if (write.outcome === 'native-no-content') return { kind: 'response', status: 204, body: '' };
  throw new Error('Unknown local response scenario.');
}
async function routeBusinessPost(route, target) {
  guards.businessPostsStubbed++;
  const write = activeWrite;
  if (!write || target.pathname !== write.pathname) {
    guards.otherMutationsBlocked++;
    return route.abort();
  }
  write.count++;
  write.requestSummary ??= summarizeRequest(route.request(), write.expected);
  if (write.count === 1) write.arrivedResolve();
  if (write.outcome === 'network') {
    write.stubStatus = 'NETWORK_ABORT';
    write.completeResolve();
    return route.abort('failed');
  }
  if (write.outcome === 'native-no-content') await write.released;
  const outcome = writeOutcome(write);
  write.stubStatus = outcome.status;
  try {
    if (outcome.kind === 'redirect') await route.fulfill({ status: outcome.status, headers: { location: outcome.location }, body: '' });
    else await route.fulfill({ status: outcome.status, contentType: 'text/plain', body: outcome.body });
  } catch { }
  finally { write.completeResolve(); }
}
async function responseFor(pathname, outcome, expected, action) {
  const write = armWrite(pathname, outcome, expected);
  await action();
  await deadline(write.completed, 7000, 'local-response-not-completed');
  if (activeWrite === write) activeWrite = null;
  return { write };
}
async function waitForFeedback(page, message) {
  await page.getByText(message, { exact: false }).waitFor({ state: 'visible', timeout: 5000 });
}
async function login(page) {
  await page.goto(`${origin}/login`, { waitUntil: 'load', timeout: 7000 });
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#loginForm button[type="submit"]').click();
  await page.waitForURL(url => url.origin === origin && url.pathname !== '/login', { timeout: 7000 });
}
async function pageForAuthenticatedRoute(routePath) {
  const page = await newPage();
  await page.goto(`${origin}${routePath}`, { waitUntil: 'load', timeout: 7000 });
  return page;
}
async function waitUntilFormLoaded(page, selector) {
  const form = page.locator(selector);
  const until = Date.now() + 5000;
  while (Date.now() < until) {
    if (await form.getAttribute('inert') === null) return true;
    await page.waitForTimeout(50);
  }
  return false;
}
async function openSavedMember(page) {
  await page.goto(`${origin}/households?mode=residents`, { waitUntil: 'load', timeout: 7000 });
  const links = page.locator('.resident-results-table a.households-open-profile');
  const count = await links.count();
  for (let index = 0; index < Math.min(count, 12); index++) {
    await links.nth(index).click();
    await page.waitForURL(url => url.pathname === '/households' && url.searchParams.has('memberId'), { timeout: 7000 });
    await page.locator('.edit-member').click();
    await page.locator('#memberForm').waitFor({ state: 'visible', timeout: 5000 });
    if (!await waitUntilFormLoaded(page, '#memberForm')) throw new Error('Member editor load did not complete.');
    const available = (await page.locator('#faceSourceStatus').innerText()).startsWith('Đã có ảnh đăng ký');
    if (available) return true;
    await page.goto(`${origin}/households?mode=residents`, { waitUntil: 'load', timeout: 7000 });
  }
  return false;
}
async function openSavedVehicle(page) {
  await page.goto(`${origin}/vehicles`, { waitUntil: 'load', timeout: 7000 });
  const links = page.locator('.vehicle-plate-link');
  const count = await links.count();
  for (let index = 0; index < Math.min(count, 12); index++) {
    await links.nth(index).click();
    await page.waitForURL(url => url.pathname === '/vehicles' && url.searchParams.has('vehicleId'), { timeout: 7000 });
    await page.locator('.edit-vehicle').click();
    await page.locator('#vehicleForm').waitFor({ state: 'visible', timeout: 5000 });
    if (!await waitUntilFormLoaded(page, '#vehicleForm')) throw new Error('Vehicle editor load did not complete.');
    const available = (await page.locator('#registrationImageStatus').innerText()).startsWith('Đã có ảnh lưu');
    if (available) return true;
    await page.goto(`${origin}/vehicles`, { waitUntil: 'load', timeout: 7000 });
  }
  return false;
}
async function editorOutcomeCase(kind, outcome) {
  const id = `${kind}-${outcome}`;
  const page = await newPage();
  try {
    const ready = kind === 'member' ? await openSavedMember(page) : await openSavedVehicle(page);
    if (!ready) return notVerified(id, `Fixture lacks a saved ${kind} image; the app correctly prevents a valid edit submit, and file selection is out of scope.`);
    const fieldName = kind === 'member' ? 'fullName' : 'plateNumber';
    const expectedValue = kind === 'member' ? 'B7A Synthetic Resident Draft' : 'B7A-TEST-001';
    const field = page.locator(`#${kind === 'member' ? 'memberForm' : 'vehicleForm'} [name="${fieldName}"]`);
    await field.fill(expectedValue);
    const pathName = kind === 'member' ? '/members' : '/vehicles';
    const statusSelector = kind === 'member' ? '#memberSaveStatus' : '#vehicleSaveStatus';
    const buttonSelector = kind === 'member' ? '#saveMemberButton' : '#saveVehicleButton';
    const { write } = await responseFor(pathName, outcome,
      { [fieldName]: expectedValue }, () => page.locator(buttonSelector).click({ timeout: 5000 }));
    if (outcome === 'network') {
      await waitForFeedback(page, 'mất kết nối');
      const visible = await page.locator(statusSelector).isVisible();
      const text = await page.locator(statusSelector).innerText();
      const generic = text.includes('mất kết nối');
      const locked = kind === 'member' ? await page.locator(buttonSelector).isDisabled() : await page.locator(buttonSelector).isDisabled();
      check(id, visible && generic && locked && write.count === 1, { syntheticNetworkAbort: true, alertVisible: visible,
        genericNetworkFeedback: generic, retryControlLocked: locked, postCount: write.count,
        requestChecks: write.requestSummary, forwardedBusinessPosts: 0 });
      return;
    }
    if (outcome === 'success') {
      const expectedPath = kind === 'member' ? '/households' : '/vehicles';
      await page.waitForURL(url => url.pathname === expectedPath
        && !(kind === 'member' ? url.searchParams.has('memberId') : url.searchParams.has('vehicleId')), { timeout: 7000 });
      const expectedNotice = kind === 'member' ? 'Đã lưu cư dân.' : 'Đã lưu phương tiện.';
      const notice = page.locator('#coreMain > [role="status"].notice');
      await notice.waitFor({ state: 'visible', timeout: 5000 });
      const syntheticNoticeVisible = await notice.isVisible().catch(() => false);
      const syntheticNoticeMatches = syntheticNoticeVisible && (await notice.innerText()) === expectedNotice;
      check(id, write.stubStatus === 303 && syntheticNoticeMatches && write.count === 1,
        { interceptedRedirect: true, finalPath: expectedPath, clientGeneratedNoticeVisible: syntheticNoticeVisible,
          clientGeneratedNoticeMatches: syntheticNoticeMatches, serverFlash: 'NOT asserted; POST response/redirect was synthetic',
          requestChecks: write.requestSummary, forwardedBusinessPosts: 0 });
      return;
    }
    if (outcome === 'session') {
      await waitForFeedback(page, 'Phiên đăng nhập đã hết hạn');
      const visible = await page.locator(statusSelector).isVisible();
      const saveDisabled = await page.locator(buttonSelector).isDisabled();
      const recoveryLink = page.locator(kind === 'member' ? '#memberLoginLink' : '#vehicleReloadLink');
      const recoveryLinkVisible = await recoveryLink.isVisible();
      const editorPath = kind === 'member' ? '/households' : '/vehicles';
      const sessionStateMatches = kind === 'member' ? !saveDisabled : saveDisabled;
      check(id, write.stubStatus === 302 && new URL(page.url()).pathname === editorPath && visible
        && sessionStateMatches && recoveryLinkVisible,
        { syntheticSessionRedirect: true, responsePath: '/login', alertVisible: visible, saveDisabled,
          recoveryLinkVisible, recoveryLinkKind: kind === 'member' ? 'new login tab' : 'reload to inspect state',
          requestChecks: write.requestSummary, forwardedBusinessPosts: 0 });
      return;
    }
    if (outcome === 'known') {
      await waitForFeedback(page, 'Yêu cầu lưu bị từ chối');
      const visible = await page.locator(statusSelector).isVisible();
      const text = await page.locator(statusSelector).innerText();
      const locked = await page.locator(buttonSelector).isDisabled();
      const retrySafe = !locked && text.includes('Yêu cầu lưu bị từ chối');
      check(id, write.stubStatus === 400 && visible && retrySafe,
        { syntheticHttpStatus: 400, bodyKind: 'empty text/plain; no server-rendered error markup', alertVisible: visible,
          retryControlAvailable: !locked, actualSpringErrorRendering: 'NOT exercised', requestChecks: write.requestSummary,
          forwardedBusinessPosts: 0 });
    } else {
      await waitForFeedback(page, 'Không xác nhận được kết quả lưu');
      const visible = await page.locator(statusSelector).isVisible();
      const text = await page.locator(statusSelector).innerText();
      const locked = await page.locator(buttonSelector).isDisabled();
      const unknown = text.includes('Không xác nhận được kết quả lưu');
      const reloadVisible = await page.locator(kind === 'member' ? '#memberReloadLink' : '#vehicleReloadLink').isVisible();
      check(id, write.stubStatus === 503 && visible && unknown && locked && reloadVisible,
        { syntheticHttpStatus: 503, bodyKind: 'empty text/plain; no server-rendered error markup', alertVisible: visible,
          unknownOutcomeFeedback: unknown, retryControlLocked: locked, reloadVisible,
          actualSpringErrorRendering: 'NOT exercised', requestChecks: write.requestSummary, forwardedBusinessPosts: 0 });
    }
  } finally { await page.close().catch(() => {}); }
}
async function householdDraftCase() {
  const page = await pageForAuthenticatedRoute('/households?mode=households');
  const links = page.locator('.household-results-table a.households-open-profile');
  if (await links.count() === 0) {
    await page.close();
    return notVerified('household-member-draft-keep-discard', 'No household profile is available in this fixture.');
  }
  await links.first().click();
  await page.waitForURL(url => url.pathname === '/households' && url.searchParams.has('householdId'), { timeout: 7000 });
  const selectedHouseholdId = new URL(page.url()).searchParams.get('householdId');
  await page.locator('#addMember').click();
  const householdSelect = page.locator('#memberForm select[name="householdId"]');
  const presetContext = await householdSelect.inputValue() === selectedHouseholdId;
  const options = page.locator('#memberForm select[name="householdId"] option:not([value=""])');
  if (await options.count() < 2) {
    await page.close();
    return notVerified('household-member-draft-keep-discard', 'Need a second fixture household to test draft preservation while changing resident household context.');
  }
  let alternateHousehold = null;
  for (let index = 0; index < await options.count(); index++) {
    const value = await options.nth(index).getAttribute('value');
    if (value !== selectedHouseholdId) { alternateHousehold = value; break; }
  }
  if (!alternateHousehold) {
    await page.close();
    return notVerified('household-member-draft-keep-discard', 'No alternate household option is available in the fixture.');
  }
  const memberName = page.locator('#memberForm [name="fullName"]');
  await memberName.fill('B7A Draft Keep Discard');
  await householdSelect.selectOption(alternateHousehold);
  const dependentContextChanged = await householdSelect.inputValue() === alternateHousehold;
  let confirmMessage = false;
  page.once('dialog', async dialog => { confirmMessage = dialog.type() === 'confirm'; await dialog.dismiss(); });
  await page.locator('#closeMember').click();
  const kept = confirmMessage && await page.locator('#memberEditor').isVisible()
    && await memberName.inputValue() === 'B7A Draft Keep Discard'
    && await householdSelect.inputValue() === alternateHousehold;
  let discardMessage = false;
  page.once('dialog', async dialog => { discardMessage = dialog.type() === 'confirm'; await dialog.accept(); });
  await page.locator('#closeMember').click();
  const discarded = discardMessage && await page.locator('#memberEditor').isHidden();
  check('household-member-draft-keep-discard', presetContext && dependentContextChanged && kept && discarded,
    { residentEditorPresetToSelectedHousehold: presetContext, residentHouseholdChanged: dependentContextChanged,
      nativeConfirmObserved: confirmMessage && discardMessage, keepPreservedSyntheticDraftAndContext: kept,
      discardClosedEditor: discarded, businessPostsForwarded: 0 });
  await page.close();
}
async function vehicleContextDraftCase() {
  const page = await pageForAuthenticatedRoute('/vehicles');
  await page.locator('#newVehicle').click();
  const households = page.locator('#vehicleHousehold option:not([value=""])');
  const householdCount = await households.count();
  if (householdCount < 2) {
    await page.close();
    return notVerified('vehicle-household-owner-member-draft', 'Need at least two fixture households to observe dependent owner/member filtering.');
  }
  const firstHousehold = await households.nth(0).getAttribute('value');
  const secondHousehold = await households.nth(1).getAttribute('value');
  await page.locator('#vehicleHousehold').selectOption(firstHousehold);
  const owners = page.locator('#vehicleOwner option:not([value=""])');
  let selectedOwner = null;
  for (let index = 0; index < await owners.count(); index++) {
    const option = owners.nth(index);
    if (await option.getAttribute('data-household') === firstHousehold) { selectedOwner = await option.getAttribute('value'); break; }
  }
  if (!selectedOwner) {
    await page.close();
    return notVerified('vehicle-household-owner-member-draft', 'Selected household has no owner option in fixture.');
  }
  await page.locator('#vehicleOwner').selectOption(selectedOwner);
  const ownerIncluded = await page.locator(`#authorizedMemberOptions input[value="${selectedOwner}"]`).isChecked().catch(() => false);
  const ownerLocked = await page.locator(`#authorizedMemberOptions input[value="${selectedOwner}"]`).isDisabled().catch(() => false);
  await page.locator('#vehicleHousehold').selectOption(secondHousehold);
  const ownerCleared = await page.locator('#vehicleOwner').inputValue() === '';
  let nonmatchingVisible = false;
  const refreshedOwners = page.locator('#vehicleOwner option:not([value=""])');
  for (let index = 0; index < await refreshedOwners.count(); index++) {
    const option = refreshedOwners.nth(index);
    if (await option.getAttribute('data-household') !== secondHousehold && await option.isVisible()) nonmatchingVisible = true;
  }
  const name = page.locator('#vehicleForm [name="plateNumber"]');
  await name.fill('B7A-DRAFT-ONLY');
  let kept = false;
  page.once('dialog', async dialog => { kept = dialog.type() === 'confirm'; await dialog.dismiss(); });
  await page.locator('#closeVehicle').click();
  const draftKept = kept && await name.inputValue() === 'B7A-DRAFT-ONLY' && await page.locator('#vehicleEditor').isVisible();
  let discarded = false;
  page.once('dialog', async dialog => { discarded = dialog.type() === 'confirm'; await dialog.accept(); });
  await page.locator('#closeVehicle').click();
  const editorClosed = discarded && await page.locator('#vehicleEditor').isHidden();
  check('vehicle-household-owner-member-draft', ownerIncluded && ownerLocked && ownerCleared && !nonmatchingVisible && draftKept && editorClosed,
    { ownerIncludedAutomatically: ownerIncluded, ownerCheckboxLocked: ownerLocked, ownerClearedOnHouseholdChange: ownerCleared,
      nonmatchingMemberOptionsVisible: nonmatchingVisible, keepPreservedSyntheticDraft: draftKept,
      discardClosedEditor: editorClosed, businessPostsForwarded: 0 });
  await page.close();
}
async function systemRoleCase() {
  const page = await pageForAuthenticatedRoute('/roles');
  const system = page.locator('.role-choice[data-system="true"]').first();
  if (await system.count() === 0) {
    await page.close();
    return notVerified('roles-system-identity-restrictions', 'Fixture contains no system-role choice.');
  }
  await system.click();
  const codeReadonly = await page.locator('#roleCode').isEditable().then(value => !value);
  const prefixReadonly = await page.locator('#rolePrefix').isEditable().then(value => !value);
  const enabledLocked = await page.locator('#roleEnabled').isDisabled();
  const hiddenValueEnabled = await page.locator('#roleEnabledLockedValue').isEnabled();
  const enabledValueMatches = await page.locator('#roleEnabledLockedValue').inputValue()
    === String(await page.locator('#roleEnabled').isChecked());
  const identityBadgeVisible = await page.locator('#roleSystemBadge').isVisible();
  check('roles-system-identity-restrictions', codeReadonly && prefixReadonly && enabledLocked && hiddenValueEnabled
    && enabledValueMatches && identityBadgeVisible,
    { codeReadonly, employeePrefixReadonly: prefixReadonly, enabledControlDisabled: enabledLocked,
      hiddenEnabledValueSuccessful: hiddenValueEnabled, hiddenEnabledValueMatchesCurrent: enabledValueMatches,
      systemBadgeVisible: identityBadgeVisible, noSubmitAttempted: true });
  await page.close();
}
async function roleNativePendingCase(viewport) {
  const page = await pageForAuthenticatedRoute('/roles');
  await page.setViewportSize(viewport);
  await page.locator('#newRoleButton').click();
  const form = page.locator('#roleEditorForm');
  await page.locator('#roleDisplayName').fill('B7A Synthetic Role');
  await page.locator('#roleCode').fill('B7A_EMPTY');
  await page.locator('#rolePrefix').fill('B7A');
  const permissionCount = await page.locator('#roleEditorForm input[name="permissions"]:checked').count();
  const invalidCount = await page.locator('#roleEditorForm :invalid').count();
  const button = page.locator('#saveRoleButton');
  await button.scrollIntoViewIfNeeded();
  const box = await button.boundingBox();
  if (!box) throw new Error('Role submit control has no visible bounds.');
  const cdp = await context.newCDPSession(page);
  await cdp.send('DOM.enable');
  const rootNode = (await cdp.send('DOM.getDocument', { depth: 0 })).root.nodeId;
  const nodeIds = {};
  for (const [key, selector] of Object.entries({ form: '#roleEditorForm', button: '#saveRoleButton', saving: '#roleSavingState' })) {
    nodeIds[key] = (await cdp.send('DOM.querySelector', { nodeId: rootNode, selector })).nodeId;
  }
  if (Object.values(nodeIds).some(id => !id)) throw new Error('Role passive observer node missing.');
  const events = [];
  cdp.on('DOM.attributeModified', event => {
    const target = Object.entries(nodeIds).find(([, id]) => id === event.nodeId)?.[0];
    if (target && ['aria-busy', 'disabled', 'hidden'].includes(event.name)) events.push({ target, name: event.name, value: event.value });
  });
  cdp.on('DOM.attributeRemoved', event => {
    const target = Object.entries(nodeIds).find(([, id]) => id === event.nodeId)?.[0];
    if (target && ['aria-busy', 'disabled', 'hidden'].includes(event.name)) events.push({ target, name: event.name, removed: true });
  });
  const write = armWrite('/roles/create', 'native-no-content', {
    displayName: 'B7A Synthetic Role', code: 'B7A_EMPTY', employeePrefix: 'B7A',
  });
  const fieldsBefore = { formValid: invalidCount === 0, permissionCount, permissionSetEmpty: permissionCount === 0 };
  let heldScreenshot = false;
  try {
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await deadline(write.arrived, 7000, 'native-post-not-observed');
    await page.waitForTimeout(150);
    const pendingEvents = events.slice();
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await page.keyboard.press('Enter');
    await page.waitForTimeout(250);
    try {
      const screenshot = await deadline(cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }), 2500, 'held-capture-timeout');
      await writeFile(path.join(output, `roles-pending-${viewport.width}.png`), Buffer.from(screenshot.data, 'base64'), { flag: 'wx' });
      heldScreenshot = true;
    } catch { heldScreenshot = false; }
    const pendingSummary = {
      requestCountWhileHeld: write.count,
      formBusyEvent: pendingEvents.some(event => event.target === 'form' && event.name === 'aria-busy' && event.value === 'true'),
      submitDisabledEvent: pendingEvents.some(event => event.target === 'button' && event.name === 'disabled' && !event.removed),
      savingStateExposed: pendingEvents.some(event => event.target === 'saving' && event.name === 'hidden' && event.removed),
      passiveEvents: pendingEvents,
    };
    write.release();
    await deadline(write.completed, 7000, 'role-stub-response-not-completed');
    if (activeWrite === write) activeWrite = null;
    if (write.stubStatus !== 204 || new URL(page.url()).pathname !== '/roles') throw new Error('Native POST did not settle on its held 204 document.');
    await page.goto(`${origin}/`, { waitUntil: 'load', timeout: 7000 });
    await page.goBack({ waitUntil: 'domcontentloaded', timeout: 7000 });
    const backPath = new URL(page.url()).pathname;
    const backForm = page.locator('#roleEditorForm');
    const backUnlocked = backPath === '/roles' && (await backForm.getAttribute('aria-busy')) !== 'true'
      && await page.locator('#roleSavingState').isHidden() && await page.locator('#saveRoleButton').isEnabled();
    const draftRestored = await page.locator('#roleEditorTitle').innerText() === 'Tạo vai trò'
      && await page.locator('#roleDisplayName').inputValue() === 'B7A Synthetic Role'
      && await page.locator('#roleCode').inputValue() === 'B7A_EMPTY'
      && await page.locator('#rolePrefix').inputValue() === 'B7A'
      && await page.locator('#roleEditorForm input[name="permissions"]:checked').count() === 0;
    const requestChecks = write.requestSummary;
    const oneRequest = write.count === 1;
    check(`roles-empty-permissions-native-pending-back-${viewport.width}`,
      fieldsBefore.formValid && fieldsBefore.permissionSetEmpty && oneRequest && write.stubStatus === 204
      && pendingSummary.formBusyEvent && pendingSummary.submitDisabledEvent && pendingSummary.savingStateExposed
      && requestChecks?.csrfPresent && requestChecks?.fieldValueMatch?.displayName
      && requestChecks?.fieldValueMatch?.code && requestChecks?.fieldValueMatch?.employeePrefix
      && requestChecks?.emptyPermissionSet && backUnlocked && draftRestored,
      { formValidWithZeroPermissions: fieldsBefore.formValid, permissionCount, nativeRequestCount: write.count,
        localNoContentResponse: write.stubStatus, redirectSuccessNotProven: true, pending: pendingSummary,
        serializedFieldChecks: requestChecks, heldScreenshotCaptured: heldScreenshot,
        visualEvidence: heldScreenshot ? 'Response-held screenshot saved for independent inspection.' : 'NOT VERIFIED — screenshot capture unavailable.',
        backPath, nativeBackUnlocked: backUnlocked, changedCreateDraftRestored: draftRestored, exactBackForwardCacheUse: 'NOT asserted',
        serverRoleCreated: false, forwardedBusinessPosts: 0 });
  } finally {
    write.release();
    if (activeWrite === write) activeWrite = null;
    await cdp.detach().catch(() => {});
    await page.close().catch(() => {});
  }
}

try {
  overallTimer = setTimeout(() => {
    record({ case: 'overall-watchdog', outcome: 'NOT VERIFIED — PARTIAL', activeCase: currentCase, deadlineMs: 370000 });
    failures.push('overall-watchdog');
    process.exitCode = 1;
    if (activeWrite) activeWrite.release();
    void context?.close().catch(() => {});
  }, 370000);
  const fixture = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
  const harness = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java'), 'utf8');
  password = fixture.match(/PASSWORD\s*=\s*"([^"]+)"/)?.[1] ?? null;
  username = harness.match(/MANAGER_USERNAME\s*=\s*"([^"]+)"/)?.[1] ?? null;
  if (!password || !username) throw new Error('Synthetic browser fixture credentials were not found.');
  sourceBefore = await sourceIdentity();
  browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  context = await browser.newContext();
  context.setDefaultTimeout(5000);
  context.on('page', page => {
    page.on('pageerror', error => pageErrors.push({ type: 'pageerror', kind: error.name || 'Error' }));
    page.on('console', message => {
      if (message.type() === 'error') consoleErrors.push({ type: 'console-error', expectedStimulusPossible: true });
    });
  });
  await context.route('**/*', async route => {
    const request = route.request();
    let target;
    try { target = new URL(request.url()); } catch { guards.externalRequestsBlocked++; return route.abort(); }
    if (target.origin !== origin || !['127.0.0.1', 'localhost'].includes(target.hostname)) {
      guards.externalRequestsBlocked++;
      return route.abort();
    }
    const method = request.method().toUpperCase();
    if (method === 'GET' || method === 'HEAD') return route.continue();
    if (method === 'POST' && target.pathname === '/login') { guards.syntheticLoginForwarded++; return route.continue(); }
    if (method === 'POST' && ['/members', '/vehicles', '/roles/create', '/roles/update'].includes(target.pathname)) {
      if (target.pathname === '/members') memberPosts++;
      if (target.pathname === '/vehicles') vehiclePosts++;
      if (target.pathname.startsWith('/roles/')) rolePosts++;
      return routeBusinessPost(route, target);
    }
    guards.otherMutationsBlocked++;
    return route.abort();
  });
  const loginPage = await newPage();
  await login(loginPage);
  record({ case: 'synthetic-login', outcome: 'PASS', credentialValuesRecorded: false });
  await loginPage.close();

  if (rolesOnly) {
    for (const id of ['household-member-draft-keep-discard', 'vehicle-household-owner-member-draft',
      ...['member', 'vehicle'].flatMap(kind => ['known', 'unknown', 'network', 'session', 'success'].map(outcome => `${kind}-${outcome}`))]) {
      skippedCases.push(id);
      record({ case: id, outcome: 'SKIPPED', reason: '--roles-only mode' });
    }
  } else {
    await boundedCase('household-member-draft-keep-discard', householdDraftCase);
    await boundedCase('vehicle-household-owner-member-draft', vehicleContextDraftCase);
    for (const kind of ['member', 'vehicle']) {
      for (const outcome of ['known', 'unknown', 'network', 'session', 'success']) {
        await boundedCase(`${kind}-${outcome}`, () => editorOutcomeCase(kind, outcome), 24000);
      }
    }
  }
  await boundedCase('roles-system-identity-restrictions', systemRoleCase);
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    await boundedCase(`roles-empty-permissions-native-pending-back-${viewport.width}`, () => roleNativePendingCase(viewport), 30000);
  }
} catch (error) {
  record({ case: 'run', outcome: 'PARTIAL — NOT VERIFIED', errorKind: error?.name || 'Error', activeCase: currentCase });
  failures.push('run');
} finally {
  clearTimeout(overallTimer);
  if (activeWrite) activeWrite.release();
  await context?.close().catch(() => {});
  await browser?.close().catch(() => {});
  sourceAfter = await sourceIdentity().catch(() => null);
  const sourceStable = sourceBefore !== null && sourceAfter !== null && JSON.stringify(sourceBefore) === JSON.stringify(sourceAfter);
  if (!sourceStable) failures.push('source-identity-stable');
  if (pageErrors.length > 0) failures.push('browser-page-errors');
  if (savedTemp === undefined) delete process.env.TEMP; else process.env.TEMP = savedTemp;
  if (savedTmp === undefined) delete process.env.TMP; else process.env.TMP = savedTmp;
  const summary = {
    status: failures.length === 0 && partialCases.length === 0 ? 'BOUNDED DIRECTORY/ROLE RECOVERY EVIDENCE COMPLETE — NOT ACCEPTANCE' : 'PARTIAL/FAILURES — NOT VERIFIED',
    fixture: 'Root-owned isolated synthetic H2/mock-ANPR only', preflight,
    sourceIdentity: { stable: sourceStable, before: sourceBefore, after: sourceAfter },
    tempLocation: path.relative(root, tempDirectory).replaceAll(path.sep, '/'),
    observations, failures, partialCases, skippedCases, pageErrors, consoleErrors,
    counts: { memberPosts, vehiclePosts, rolePosts }, guards,
    boundaries: 'Only same-origin GET/HEAD and synthetic login POST were forwarded. All /members, /vehicles and /roles writes were locally intercepted; every other mutation and external request was blocked. Request summaries retain booleans only (field presence/value match/CSRF presence/empty permission set). No request body, CSRF value, cookie, credential, PII, response HTML, trace, HAR or media was persisted. Synthetic HTTP responses and client feedback are not server-rendered behavior or server flash. No server-side business write occurred.',
  };
  await writeFile(path.join(output, 'summary.json'), JSON.stringify(summary, null, 2), { flag: 'wx' });
  process.exitCode = failures.length === 0 && partialCases.length === 0 ? 0 : 1;
}
