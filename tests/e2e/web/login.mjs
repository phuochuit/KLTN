import { assertIsolatedHarness } from './isolated-harness.mjs';
// Opt-in, loopback H2 harness only. No traces, cookies, form values or request bodies in evidence.
import { createRequire } from 'node:module';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium, expect } = require('@playwright/test');
const { run, port } = await assertIsolatedHarness(root, process.argv[2]);
const baseURL = `http://127.0.0.1:${port}`;
const fixture = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
const password = fixture.match(/PASSWORD = "([^"]+)"/)[1];
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const results = [];
let current = '';
const check = async (name, fn) => {
  current = name;
  await fn();
  results.push({ check: name, status: 'PASS' });
};
try {
  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();
  const errors = [];
  const pathname = url => { try { return new URL(url).pathname; } catch { return 'unknown'; } };
  page.on('pageerror', () => errors.push({ type: 'pageerror', path: pathname(page.url()) }));
  page.on('console', msg => { if (msg.type() === 'error') errors.push({ type: 'console-error', path: pathname(msg.location().url) }); });
  page.on('requestfailed', request => errors.push({ type: 'requestfailed', path: pathname(request.url()), reason: request.failure()?.errorText }));
  await check('desktop default and feedback fit viewport without hidden overflow', async () => {
    const measurements = [];
    for (const [width, height] of [[1920,1080],[1536,864],[1440,900],[1366,768],[1280,720],[1024,768]]) {
      await page.setViewportSize({ width, height });
      for (const state of ['', '?error', '?throttled', '?passwordChanged']) {
        await page.goto('/login' + state);
        await page.locator('.login-panel').waitFor();
        await page.evaluate(() => document.fonts.ready);
        const bounds = await page.evaluate(() => ({
          width: innerWidth, height: innerHeight,
          scrollWidth: document.documentElement.scrollWidth,
          scrollHeight: document.documentElement.scrollHeight,
          overflowY: getComputedStyle(document.body).overflowY,
        }));
        measurements.push({ viewport: [width, height], state: state || 'default', ...bounds });
        await writeFile(path.join(run, 'viewport-measurements.json'), JSON.stringify(measurements, null, 2));
        assert.ok(bounds.scrollWidth <= width + 1, 'No horizontal overflow');
        assert.ok(bounds.scrollHeight <= height + 1, `No unnecessary vertical scroll at ${width}x${height} ${state}`);
        assert.notEqual(bounds.overflowY, 'hidden', 'Do not mask overflow by hiding the page');
        const submit = await page.locator('#loginSubmit').boundingBox();
        assert.ok(submit && submit.y >= 0 && submit.y + submit.height <= height, 'CTA fully inside viewport');
      }
      await page.goto('/login');
      await page.screenshot({ path: path.join(run, `viewport-${width}.png`), fullPage: false });
    }
  });
  await check('empty-page responsive bounds and screenshots', async () => {
    for (const [width, height] of [[1440,900],[1280,800],[1024,768],[375,812],[390,844],[430,932],[320,640],[812,375]]) {
      await page.setViewportSize({ width, height });
      await page.goto('/login');
      for (const selector of ['.brand-lockup', '.login-panel', '#username', '#password', '#loginSubmit']) {
        const box = await page.locator(selector).boundingBox();
        assert.ok(box && box.x >= 0 && box.x + box.width <= width + 1);
      }
      await page.screenshot({ path: path.join(run, `verified-${width}.png`), fullPage: true });
    }
  });
  await check('mobile form follows header without vertically centered empty space', async () => {
    const positions = [];
    for (const [width, height] of [[320,640],[375,812],[390,844],[430,932]]) {
      await page.setViewportSize({ width, height });
      await page.goto('/login');
      await page.evaluate(() => document.fonts.ready);
      const header = await page.locator('.login-header').boundingBox();
      const panel = await page.locator('.login-panel').boundingBox();
      const gap = panel.y - header.y - header.height;
      assert.ok(gap >= 24 && gap <= 40, 'Mobile header/form gap 24–40px');
      for (const selector of ['#username', '#password', '#loginSubmit']) {
        const box = await page.locator(selector).boundingBox();
        assert.ok(box.height >= 48, 'Do not shrink interaction targets');
      }
      positions.push({ width, height, formTop: panel.y, headerGap: gap });
    }
    await writeFile(path.join(run, 'mobile-positions.json'), JSON.stringify(positions, null, 2));
  });
  await check('desktop form resizes fluidly across intermediate widths', async () => {
    const sizes = [];
    await page.setViewportSize({ width: 960, height: 900 });
    await page.goto('/login');
    await page.evaluate(() => document.fonts.ready);
    for (let width = 960; width <= 1600; width += 40) {
      await page.setViewportSize({ width, height: 900 });
      const box = await page.locator('.login-panel').boundingBox();
      const metrics = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        scrollHeight: document.documentElement.scrollHeight,
        padding: parseFloat(getComputedStyle(document.querySelector('.login-panel')).paddingLeft),
      }));
      assert.ok(metrics.scrollWidth <= width + 1 && metrics.scrollHeight <= 901);
      if (sizes.length) {
        const delta = box.width - sizes.at(-1).formWidth;
        assert.ok(delta >= -1 && delta <= 24, 'Form changes gradually inside desktop mode');
      }
      sizes.push({ width, formWidth: box.width, padding: metrics.padding });
    }
    assert.ok(sizes.at(-1).formWidth - sizes[0].formWidth >= 100, 'Form must not stay at one fixed desktop width');
    await writeFile(path.join(run, 'fluid-resize.json'), JSON.stringify(sizes, null, 2));
  });
  await check('short viewport and reflow retain scrolling and reachable controls', async () => {
    for (const [width, height] of [[812,375],[320,640]]) {
      await page.setViewportSize({ width, height });
      await page.goto('/login?throttled');
      await page.locator('#loginSubmit').focus();
      await expect(page.locator('#loginSubmit')).toBeInViewport();
      const bounds = await page.evaluate(() => ({
        horizontal: document.documentElement.scrollWidth > innerWidth + 1,
        scrollable: document.documentElement.scrollHeight > innerHeight,
        overflowY: getComputedStyle(document.body).overflowY,
      }));
      assert.equal(bounds.horizontal, false);
      assert.equal(bounds.scrollable, true, 'Small/short viewports may scroll instead of cutting content');
      assert.notEqual(bounds.overflowY, 'hidden');
    }
  });
  await check('keyboard order, visibility and native required validation', async () => {
    await page.goto('/login');
    await page.locator('#username').focus();
    await page.keyboard.press('Tab');
    assert.equal(await page.locator('#password').getAttribute('type'), 'password');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Space');
    assert.equal(await page.locator('#togglePassword').getAttribute('aria-pressed'), 'true');
    assert.equal(await page.locator('#password').getAttribute('type'), 'text');
    // Finish the dynamically requested icon before toggling back/navigation;
    // otherwise the test itself can abort its request and report a false asset failure.
    await expect(page.locator('#passwordIcon')).toHaveJSProperty('complete', true);
    await expect(page.locator('#passwordIcon')).toHaveJSProperty('naturalWidth', 24);
    await page.keyboard.press('Space');
    await expect(page.locator('#passwordIcon')).toHaveJSProperty('complete', true);
    await expect(page.locator('#passwordIcon')).toHaveJSProperty('naturalWidth', 24);
    let posts = 0;
    const count = request => { if (request.method() === 'POST') posts++; };
    page.on('request', count);
    await page.locator('#loginSubmit').click();
    assert.equal(posts, 0);
    assert.ok(page.url().endsWith('/login'));
    page.off('request', count);
  });
  await check('wrong credentials, Enter submit and error focus', async () => {
    await page.locator('#username').fill(`lumipark-invalid-${Date.now()}`);
    await page.locator('#password').fill('not-a-valid-test-password');
    await Promise.all([page.waitForURL('**/login?error'), page.locator('#password').press('Enter')]);
    assert.equal(await page.locator('[role=alert]').textContent(), 'Sai tài khoản hoặc mật khẩu.');
    assert.equal(await page.locator('#login-error').getAttribute('tabindex'), '-1');
    await expect(page.locator('#login-error')).toBeFocused();
    await page.locator('#login-error').screenshot({ path: path.join(run, 'error-feedback.png') });
  });
  await check('submitting guard (204 response stub) and browser history recovery', async () => {
    await page.goto('/login');
    await page.locator('#username').fill('lumipark-browser-manager');
    await page.locator('#password').fill('not-a-valid-test-password');
    await page.route('**/login', async route => {
      if (route.request().method() === 'POST') await route.fulfill({ status: 204 });
      else await route.continue();
    });
    let posts = 0;
    const count = request => { if (request.method() === 'POST') posts++; };
    page.on('request', count);
    const response = page.waitForResponse(response => response.url() === baseURL + '/login' && response.request().method() === 'POST');
    current = 'guard: native submit to 204 stub';
    await page.locator('#loginSubmit').click({ noWaitAfter: true });
    current = 'guard: inspect pending state';
    assert.equal(await page.locator('#loginSubmit').isDisabled(), true, 'CTA disabled while POST pending');
    assert.equal(await page.locator('#username').isEnabled(), true);
    assert.equal(await page.locator('#password').isEnabled(), true);
    assert.equal(await page.locator('#loginForm').getAttribute('aria-busy'), 'true');
    await page.keyboard.press('Enter');
    current = 'guard: response and single POST';
    await response;
    assert.equal(posts, 1);
    page.off('request', count);
    await page.unroute('**/login');
    current = 'guard: browser history recovery';
    await page.goto('/login?logout');
    await page.goBack();
    assert.equal(await page.locator('#loginSubmit').isEnabled(), true);
    assert.equal(await page.locator('#loginForm').getAttribute('aria-busy'), null);
    assert.equal(await page.locator('#password').getAttribute('type'), 'password');
    await page.goto('/login');
  });
  await check('successful native login and legacy assets remain isolated', async () => {
    await page.locator('#username').fill('lumipark-browser-manager');
    await page.locator('#password').fill(password);
    await Promise.all([page.waitForURL(url => url.origin === baseURL && url.pathname === '/'), page.locator('#loginSubmit').click()]);
    assert.equal(await page.locator('link[href$="web-ui.css"]').count(), 0);
    assert.equal(await page.locator('script[src$="login.js"]').count(), 0);
    assert.ok(await page.locator('link[href$="dashboard-ui.css"]').count() > 0);
    await page.goto('/vehicles');
    assert.ok(await page.locator('link[href$="app.css"]').count() > 0);
    assert.equal(await page.locator('link[href$="dashboard-ui.css"]').count(), 0);
  });
  await check('logout feedback through real POST logout', async () => {
    await page.locator('form[action="/logout"] button').click();
    await Promise.all([page.waitForURL('**/login?logout'), page.locator('#app-dialog-confirm').click()]);
    assert.equal(await page.locator('.feedback-success').textContent(), 'Bạn đã đăng xuất an toàn.');
  });
  await check('forced password-change destination unchanged', async () => {
    await page.locator('#username').fill('lumipark-browser-change');
    await page.locator('#password').fill(password);
    await Promise.all([page.waitForURL('**/account/password'), page.locator('#loginSubmit').click()]);
    assert.equal(await page.locator('link[href$="web-ui.css"]').count(), 0);
    // Do not change the password: destination regression only.
  });
  await check('password-changed feedback rendering', async () => {
    await page.goto('/login?passwordChanged');
    assert.ok((await page.locator('.feedback-success').textContent()).includes('Đổi mật khẩu thành công'));
  });
  const throttleContext = await browser.newContext({ baseURL });
  const throttle = await throttleContext.newPage();
  await check('real throttle after ten wrong attempts', async () => {
    const throttleUser = `lumipark-browser-throttle-${Date.now()}`;
    for (let i = 0; i < 11; i++) {
      await throttle.goto('/login');
      await throttle.locator('#username').fill(throttleUser);
      await throttle.locator('#password').fill('not-a-valid-test-password');
      await Promise.all([
        throttle.waitForURL(i < 10 ? '**/login?error' : '**/login?throttled'),
        throttle.locator('#loginSubmit').click(),
      ]);
    }
    assert.ok((await throttle.locator('[role=alert]').textContent()).includes('15 phút'));
  });
  await throttleContext.close();
  await check('no JavaScript native form login', async () => {
    const noJs = await browser.newContext({ baseURL, javaScriptEnabled: false });
    const plain = await noJs.newPage();
    await plain.goto('/login');
    assert.equal(await plain.locator('#togglePassword').isVisible(), false);
    await plain.locator('#username').fill('lumipark-browser-manager');
    await plain.locator('#password').fill(password);
    await Promise.all([plain.waitForURL(baseURL + '/'), plain.locator('#loginSubmit').click()]);
    await noJs.close();
  });
  await check('reduced motion and opaque fallback rendered', async () => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/login');
    await page.screenshot({ path: path.join(run, 'reduced-motion.png'), fullPage: true });
    // Controlled QA override simulates the already-defined @supports fallback, not application code.
    await page.addStyleTag({ content: '.login-panel { backdrop-filter:none !important; background:#fff !important; }' });
    await page.screenshot({ path: path.join(run, 'opaque-fallback.png'), fullPage: true });
  });
  await check('single glass surface and forced-colors fallback', async () => {
    await page.emulateMedia({ reducedMotion: 'no-preference', forcedColors: 'none' });
    await page.goto('/login');
    const materials = await page.evaluate(() => ({
      panelBlur: getComputedStyle(document.querySelector('.login-panel')).backdropFilter,
      pageBorder: getComputedStyle(document.querySelector('.login-layout')).borderTopWidth,
      stageBorder: getComputedStyle(document.querySelector('.form-stage')).borderTopWidth,
    }));
    assert.equal(materials.panelBlur, 'blur(20px)');
    assert.equal(materials.pageBorder, '0px', 'No enclosing page frame');
    assert.equal(materials.stageBorder, '0px', 'No nested frame hugging the form');
    await page.emulateMedia({ forcedColors: 'active' });
    await page.goto('/login');
    assert.equal(await page.locator('.login-panel').evaluate(element => getComputedStyle(element).backdropFilter), 'none');
    await page.locator('#username').focus();
    await expect(page.locator('#username')).toBeFocused();
    await page.screenshot({ path: path.join(run, 'forced-colors.png'), fullPage: true });
  });
  await writeFile(path.join(run, 'browser-errors-sanitized.json'), JSON.stringify(errors, null, 2));
  await check('no Login console/page/asset errors (legacy favicon request reported separately)', async () => {
    // Unchanged destination pages have no favicon link and request /favicon.ico automatically.
    // Login explicitly uses /images/brand/favicon.svg; do not rebrand unrelated pages to hide this gap.
    assert.deepEqual(errors.filter(error => error.path !== '/favicon.ico'), []);
  });
  await context.close();
} catch (error) {
  // Deliberately avoid Playwright error text: it may include filled values or HTML tokens.
  results.push({ check: current, status: 'FAIL', errorType: error.name,
    assertion: error.code === 'ERR_ASSERTION' ? error.message : undefined });
  process.exitCode = 1;
} finally {
  await browser.close();
  await writeFile(path.join(run, 'browser-checks.json'), JSON.stringify(results, null, 2));
  console.log(JSON.stringify(results, null, 2));
}
