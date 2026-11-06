import assert from 'node:assert/strict';
import { readFile, realpath } from 'node:fs/promises';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium, expect } = require('@playwright/test');
const testRunsRoot = await realpath(path.join(root, '.artifacts/test-runs'));
const runDirectory = await realpath(path.resolve(process.argv[2] ?? ''));
assert.ok(runDirectory.startsWith(`${testRunsRoot}${path.sep}`), 'run directory must be below .artifacts/test-runs');
assert.ok(!(await import('node:fs')).existsSync(path.join(runDirectory, 'stop')), 'harness stop marker must be absent');
const port = (await readFile(path.join(runDirectory, 'server.port'), 'utf8')).trim();
assert.match(port, /^\d+$/, 'expected a loopback harness server.port');
assert.ok(Number(port) >= 1 && Number(port) <= 65535, 'server.port must be a valid TCP port');
const origin = `http://127.0.0.1:${port}`;
const authSource = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java'), 'utf8');
const isolationSource = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
const username = authSource.match(/MANAGER_USERNAME\s*=\s*"([^"]+)"/)?.[1];
const password = isolationSource.match(/PASSWORD\s*=\s*"([^"]+)"/)?.[1];
assert.ok(username && password, 'synthetic harness credentials must be available in test source');

const routes = [
  '/', '/registrations', '/cards', '/households', '/vehicles', '/security-audit', '/settings',
  '/slots', '/sessions', '/account-management', '/roles', '/pricing', '/payments',
];
const desktopSizes = [{ width: 1440, height: 1000 }, { width: 1280, height: 720 }];
const drawerSizes = [{ width: 390, height: 844 }, { width: 320, height: 740 }];
const browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
const page = await browser.newPage();
const checks = [];

try {
  await page.route('**/*', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.origin !== origin || !(['GET', 'HEAD'].includes(request.method())
      || (request.method() === 'POST' && url.pathname === '/login'))) {
      await route.abort();
      return;
    }
    await route.continue();
  });
  await page.goto(`${origin}/login`);
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[name="password"]').fill(password);
  await page.locator('button[type="submit"]').click();
  await page.locator('.sidebar .dashboard-navigation').waitFor();
  assert.notEqual(new URL(page.url()).pathname, '/login', 'synthetic browser login must succeed');
  assert.ok((await page.locator('script').allTextContents()).some(script => script.includes('parkingNavigationScrollInitialized')),
    'shared navigation behavior must be present in rendered shell fragments');

  for (const viewport of desktopSizes) {
    await page.setViewportSize(viewport);
    for (const route of routes) {
      await page.goto(`${origin}${route}`);
      const active = page.locator('.sidebar .nav-item[aria-current="page"]');
      const nav = page.locator('.sidebar .dashboard-navigation');
      await expect.poll(async () => {
        const activeBox = await active.boundingBox();
        const navBox = await nav.boundingBox();
        return Boolean(activeBox && navBox && activeBox.y >= navBox.y && activeBox.y + activeBox.height <= navBox.y + navBox.height);
      }).toBe(true);
      const header = await page.locator('.dashboard-topbar').boundingBox();
      const activeBox = await active.boundingBox();
      const navBox = await nav.boundingBox();
      assert.ok(header && header.y >= 0 && header.y < 40, `${route} ${viewport.width}: topbar must remain at page top`);
      assert.ok(activeBox && navBox && activeBox.y >= navBox.y && activeBox.y + activeBox.height <= navBox.y + navBox.height,
        `${route} ${viewport.width}: active route must be fully visible in sidebar navigation; active=${JSON.stringify(activeBox)} nav=${JSON.stringify(navBox)}`);
      checks.push({ route, viewport: `${viewport.width}x${viewport.height}`, headerY: header.y, activeVisible: true });
    }
  }

  for (const viewport of drawerSizes) {
    await page.setViewportSize(viewport);
    for (const route of routes) {
      await page.goto(`${origin}${route}`);
      const headerBefore = await page.locator('.dashboard-topbar').boundingBox();
      await page.locator('#openNavigation').click();
      const dialog = page.locator('#navigationDialog');
      await dialog.waitFor({ state: 'visible' });
      const active = dialog.locator('.nav-item[aria-current="page"]');
      const closeButton = page.locator('#closeNavigation');
      await expect.poll(async () => {
        const dialogBox = await dialog.boundingBox();
        const activeBox = await active.boundingBox();
        return Boolean(dialogBox && activeBox && activeBox.y >= dialogBox.y
          && activeBox.y + activeBox.height <= dialogBox.y + dialogBox.height);
      }).toBe(true);
      const dialogBox = await dialog.boundingBox();
      const activeBox = await active.boundingBox();
      const navBox = await dialog.locator('.dashboard-navigation').boundingBox();
      const brandBox = await dialog.locator('.dashboard-brand').boundingBox();
      const footerBox = await dialog.locator('.navigation-bottom').boundingBox();
      const headerAfter = await page.locator('.dashboard-topbar').boundingBox();
      const closeBox = await closeButton.boundingBox();
      assert.ok(closeBox && closeBox.y >= 0 && closeBox.y + closeBox.height <= viewport.height,
        `${route} ${viewport.width}: close control must remain visible while active route is revealed`);
      assert.ok(dialogBox && brandBox && brandBox.y >= dialogBox.y && footerBox
        && footerBox.y + footerBox.height <= dialogBox.y + dialogBox.height,
      `${route} ${viewport.width}: brand and drawer footer must not be clipped`);
      assert.ok(activeBox && navBox && activeBox.y >= navBox.y && activeBox.y + activeBox.height <= navBox.y + navBox.height,
        `${route} ${viewport.width}: active route must remain inside the navigation scrollport, clear of the close control`);
      assert.ok(headerBefore && headerAfter && Math.abs(headerAfter.y - headerBefore.y) < 1,
        `${route} ${viewport.width}: opening drawer must preserve document position`);
      checks.push({ route, viewport: `${viewport.width}x${viewport.height}`, drawerActiveVisible: true, drawerCloseVisible: true, mainPositionPreserved: true });
      await page.keyboard.press('Escape');
      await assert.rejects(dialog.waitFor({ state: 'visible', timeout: 100 }), 'Escape must close the native drawer');
      await assert.doesNotReject(page.locator('#openNavigation').waitFor({ state: 'visible' }), 'drawer trigger must remain available');
    }
  }

  await page.setViewportSize(drawerSizes[0]);
  await page.goto(`${origin}/`);
  const trigger = page.locator('#openNavigation');
  await trigger.focus();
  await trigger.press('Enter');
  const dialog = page.locator('#navigationDialog');
  await dialog.waitFor({ state: 'visible' });
  const closeButton = page.locator('#closeNavigation');
  await closeButton.focus();
  await page.keyboard.press('Shift+Tab');
  const focusedDomElement = page.locator(':focus');
  if (await focusedDomElement.count()) {
    assert.equal(await page.locator('#navigationDialog :focus').count(), 1,
      'backward navigation must not focus an application control behind the modal');
  }
  await page.keyboard.press('Tab');
  assert.equal(await page.locator('.dashboard-shell :focus').count(), 0,
    'forward traversal must not focus a background shell control');
  await closeButton.click();
  await expect(trigger).toBeFocused();
  await trigger.click();
  await dialog.waitFor({ state: 'visible' });
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();
  for (const route of routes) {
    await page.setViewportSize(drawerSizes[0]);
    await page.goto(`${origin}${route}`);
    const routeTrigger = page.locator('#openNavigation');
    await routeTrigger.click();
    await dialog.waitFor({ state: 'visible' });
    await page.setViewportSize({ width: 1280, height: 720 });
    await expect(dialog).not.toBeVisible();
    checks.push({ route, resizeClose: true });
  }

  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto(`${origin}/roles`);
  const reducedHeader = await page.locator('.dashboard-topbar').boundingBox();
  assert.ok(reducedHeader && reducedHeader.y >= 0 && reducedHeader.y < 40, 'reduced-motion route reveal must not scroll the document');
  await page.setViewportSize(drawerSizes[1]);
  await page.locator('#openNavigation').click();
  const reducedDialogBox = await dialog.boundingBox();
  const reducedActiveBox = await dialog.locator('.nav-item[aria-current="page"]').boundingBox();
  assert.ok(reducedDialogBox && reducedActiveBox && reducedActiveBox.y >= reducedDialogBox.y
    && reducedActiveBox.y + reducedActiveBox.height <= reducedDialogBox.y + reducedDialogBox.height,
  'reduced-motion active navigation must remain fully visible');
  checks.push({ route: '/roles', viewport: '320x740', reducedMotion: true, drawerActiveVisible: true });

  console.log(JSON.stringify({ result: 'PASS', checks: checks.length, checks }, null, 2));
} finally {
  await browser.close();
}
