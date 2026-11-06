import { createRequire } from 'node:module';
import { createServer, request as httpRequest } from 'node:http';
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium, expect } = require('@playwright/test');
const run = path.resolve(process.argv[2] ?? '');
assert.ok(run.startsWith(path.join(root, '.artifacts/test-runs') + path.sep), 'Run must be below .artifacts/test-runs.');
const stage = process.argv[3] ?? 'pricing-payments';
assert.match(stage, /^[a-z0-9-]+$/);
const output = path.join(run, stage);
await mkdir(output, { recursive: false });
const harnessPort = Number((await readFile(path.join(run, 'server.port'), 'utf8')).trim());
assert.ok(Number.isInteger(harnessPort) && harnessPort > 0 && harnessPort < 65536, 'Invalid isolated harness marker.');
const harnessSource = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
const password = harnessSource.match(/PASSWORD = "([^"]+)"/)?.[1];
assert.ok(password, 'Synthetic harness credential must exist in test source.');

const paymentRows = `
<tr class="payment-row" data-status="PAID"><td><strong class="payment-receipt">UXQA-RCPT-PAID-01</strong><small>Không có mã tham chiếu</small></td><td>Phí lượt gửi xe</td><td>Tiền mặt</td><td class="payment-amount">135.000 đ</td><td><span class="payment-status is-paid">Đã thu</span></td><td>01/11/2026 09:15</td><td>Nhân viên synthetic</td><td><form class="payment-reconcile-form" method="post" action="/payments/901/reconcile" data-receipt="UXQA-RCPT-PAID-01" data-amount="135.000"><input type="hidden" name="_csrf" value="synthetic-browser-token"><label>Ghi chú đối soát<input name="note" required maxlength="500" placeholder="Nhập ghi chú" aria-describedby="paymentNoteHint-901 paymentNoteError-901"><small class="payment-note-hint" id="paymentNoteHint-901">Bắt buộc · tối đa 500 ký tự.</small><small class="payment-note-error" id="paymentNoteError-901" role="alert" hidden>Vui lòng nhập ghi chú đối soát.</small></label><button type="submit">Xem lại đối soát</button></form></td></tr>
<tr class="payment-row" data-status="PAID"><td><strong class="payment-receipt">UXQA-RCPT-PAID-02</strong><small>QA-SUB-REF-02</small></td><td>Phí gói gửi xe</td><td>Chuyển khoản</td><td class="payment-amount">1.250.000 đ</td><td><span class="payment-status is-paid">Đã thu</span></td><td>31/10/2026 16:40</td><td>Nhân viên synthetic</td><td><form class="payment-reconcile-form" method="post" action="/payments/902/reconcile" data-receipt="UXQA-RCPT-PAID-02" data-amount="1.250.000"><input type="hidden" name="_csrf" value="synthetic-browser-token"><label>Ghi chú đối soát<input name="note" required maxlength="500" placeholder="Nhập ghi chú" aria-describedby="paymentNoteHint-902 paymentNoteError-902"><small class="payment-note-hint" id="paymentNoteHint-902">Bắt buộc · tối đa 500 ký tự.</small><small class="payment-note-error" id="paymentNoteError-902" role="alert" hidden>Vui lòng nhập ghi chú đối soát.</small></label><button type="submit">Xem lại đối soát</button></form></td></tr>
<tr class="payment-row" data-status="RECONCILED"><td><strong class="payment-receipt">UXQA-RCPT-DONE-03</strong><small>QA-POS-REF-03</small></td><td>Phí lượt gửi xe</td><td>Máy POS</td><td class="payment-amount">45.000 đ</td><td><span class="payment-status is-reconciled">Đã đối soát</span></td><td>30/10/2026 13:05</td><td>Nhân viên synthetic</td><td><span class="payment-note">Đã khớp ca tổng hợp</span></td></tr>`;

const intercepted = [];
const routeAbortedMutations = [];
const upstreamReads = [];
let responseMode = 'success';
let holdNextPost = false;
let pendingResolver;
let pendingReady;
let proxyOrigin;
const proxyServer = createServer(async (incoming, outgoing) => {
  try {
    const requestUrl = new URL(incoming.url, proxyOrigin);
    if (requestUrl.origin !== proxyOrigin) return send(outgoing, 400, 'Invalid proxy origin.');
    const isPricingWrite = requestUrl.pathname === '/pricing';
    const isReconcileWrite = /^\/payments\/\d+\/reconcile$/.test(requestUrl.pathname);
    if (incoming.method === 'POST' && requestUrl.pathname === '/login') return forwardLogin(incoming, outgoing);
    if (incoming.method === 'POST' && (isPricingWrite || isReconcileWrite)) {
      const screen = isPricingWrite ? 'pricing' : 'payments';
      const mode = responseMode;
      intercepted.push({ screen, path: requestUrl.pathname, mode, forwarded: false });
      if (holdNextPost) {
        pendingReady?.();
        await new Promise(resolve => { pendingResolver = resolve; });
        holdNextPost = false;
      }
      if (mode === 'network') return send(outgoing, 400, 'Network scenario escaped the scoped browser route abort.');
      if (mode === 'forbidden') return send(outgoing, 403, 'Synthetic permission denial.');
      if (mode === 'session') return redirect(outgoing, '/login?error');
      if (mode === 'unknown') return send(outgoing, 503, 'Synthetic unknown outcome.');
      const suffix = mode === 'missing-flash' ? 'missing' : 'success';
      return redirect(outgoing, `/${screen}?fixture=${suffix}`);
    }
    if (incoming.method !== 'GET' && incoming.method !== 'HEAD') {
      return send(outgoing, 405, 'Proxy allows only GET/HEAD reads and isolated login POST.');
    }
    if (requestUrl.pathname.startsWith('/api/') && incoming.method !== 'GET') {
      return send(outgoing, 405, 'Read-only proxy policy.');
    }
    const fixture = requestUrl.searchParams.get('fixture');
    const pagePath = requestUrl.pathname;
    const upstreamPath = (pagePath === '/pricing' || pagePath === '/payments') && fixture ? pagePath : `${pagePath}${requestUrl.search}`;
    const body = await forwardRead(incoming, upstreamPath);
    upstreamReads.push({ path: pagePath, fixture: fixture ?? null });
    if (fixture === 'success' || fixture === 'missing') {
      let html = body.body.toString('utf8');
      if (pagePath === '/payments') html = addPaymentRows(html);
      if (fixture === 'success') html = addServerFlash(html, pagePath);
      return send(outgoing, body.status, html, body.headers);
    }
    if (pagePath === '/payments' && fixture !== 'empty') {
      return send(outgoing, body.status, addPaymentRows(body.body.toString('utf8')), body.headers);
    }
    return send(outgoing, body.status, body.body, body.headers);
  } catch {
    send(outgoing, 502, 'Synthetic loopback proxy failed closed.');
  }
});

function send(response, status, body, sourceHeaders = {}) {
  if (response.destroyed) return;
  const headers = proxyHeaders(sourceHeaders);
  for (const header of ['content-length', 'transfer-encoding', 'content-encoding', 'connection']) delete headers[header];
  if (typeof body === 'string') headers['content-type'] = headers['content-type'] || 'text/html; charset=utf-8';
  const bytes = Buffer.isBuffer(body) ? body : Buffer.from(body);
  headers['content-length'] = String(bytes.length);
  response.writeHead(status, headers);
  response.end(bytes);
}

function proxyHeaders(sourceHeaders) {
  const headers = { ...sourceHeaders };
  if (headers.location) {
    try {
      const target = new URL(headers.location, `http://127.0.0.1:${harnessPort}`);
      if (target.origin === `http://127.0.0.1:${harnessPort}`) headers.location = `${proxyOrigin}${target.pathname}${target.search}${target.hash}`;
    } catch { delete headers.location; }
  }
  return headers;
}

function redirect(response, location) {
  response.writeHead(303, { location, 'content-length': '0', connection: 'close' });
  response.end();
}

function addServerFlash(html, pagePath) {
  const successClass = pagePath === '/pricing' ? 'pricing-feedback pricing-success' : 'payments-feedback payments-success';
  return html.replace(/(<main[^>]*>)/, `$1<div class="${successClass}" role="status"><p>Đã ghi nhận phản hồi synthetic của fixture.</p></div>`);
}

function addPaymentRows(html) {
  html = html.replace(/<tbody>\s*<\/tbody>/, `<tbody>${paymentRows}</tbody>`);
  html = html.replace(/<strong>0<\/strong>/, '<strong>2</strong>');
  html = html.replace(/<strong>0 đ<\/strong>/, '<strong>1.385.000 đ</strong>');
  return html.replace(/<p class="payments-empty"[^>]*>[^<]*<\/p>/, '');
}

function forwardRead(incoming, requestPath) {
  return new Promise((resolve, reject) => {
    const request = httpRequest({
      hostname: '127.0.0.1', port: harnessPort, method: incoming.method, path: requestPath,
      headers: { cookie: incoming.headers.cookie ?? '', accept: incoming.headers.accept ?? '*/*', 'user-agent': 'LumiPark-focused-browser-proxy', host: `127.0.0.1:${harnessPort}`, connection: 'close' },
    }, response => {
      const chunks = [];
      response.on('data', chunk => chunks.push(chunk));
      response.on('end', () => resolve({ status: response.statusCode ?? 502, headers: response.headers, body: Buffer.concat(chunks) }));
    });
    request.on('error', reject);
    request.end();
  });
}

function forwardLogin(incoming, outgoing) {
  const request = httpRequest({
    hostname: '127.0.0.1', port: harnessPort, method: 'POST', path: '/login',
    headers: { cookie: incoming.headers.cookie ?? '', 'content-type': incoming.headers['content-type'] ?? 'application/x-www-form-urlencoded', 'content-length': incoming.headers['content-length'] ?? '0', host: `127.0.0.1:${harnessPort}`, connection: 'close' },
  }, response => {
    outgoing.writeHead(response.statusCode ?? 502, proxyHeaders(response.headers));
    response.pipe(outgoing);
  });
  request.on('error', () => send(outgoing, 502, 'Synthetic login forwarding failed.'));
  incoming.pipe(request);
}

await new Promise((resolve, reject) => {
  proxyServer.once('error', reject);
  proxyServer.listen(0, '127.0.0.1', resolve);
});
const proxyPort = proxyServer.address().port;
proxyOrigin = `http://127.0.0.1:${proxyPort}`;
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const checks = [];
const pageErrors = [];
const expectedConsoleStatuses = [];
const servedAssetHashes = {};
const paymentLayoutBounds = [];
const check = async (name, action) => { await action(); checks.push({ check: name, status: 'PASS' }); };
const formatVnd = value => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 6 }).format(Number(value));

async function routeAbortNextMutation(page, screen) {
  const routePattern = screen === 'pricing' ? '**/pricing' : '**/payments/*/reconcile';
  const handler = async route => {
    const request = route.request();
    if (request.method() !== 'POST') return route.continue();
    routeAbortedMutations.push({ screen, path: new URL(request.url()).pathname, forwarded: false });
    await route.abort('failed');
  };
  await page.route(routePattern, handler);
  return () => page.unroute(routePattern, handler);
}

async function getThroughProxy(requestPath) {
  return new Promise((resolve, reject) => {
    const request = httpRequest({ hostname: '127.0.0.1', port: proxyPort, path: requestPath, method: 'GET', connection: 'close' }, response => {
      const chunks = []; response.on('data', chunk => chunks.push(chunk));
      response.on('end', () => resolve({ status: response.statusCode, body: Buffer.concat(chunks) }));
    });
    request.on('error', reject); request.end();
  });
}

for (const [routePath, sourcePath, outputPath, compareSource] of [
  ['/js/pricing.mjs', 'spring-web/src/main/resources/static/js/pricing.mjs', 'spring-web/target/classes/static/js/pricing.mjs', true],
  ['/js/pricing-model.mjs', 'spring-web/src/main/resources/static/js/pricing-model.mjs', 'spring-web/target/classes/static/js/pricing-model.mjs', true],
  ['/js/payments.mjs', 'spring-web/src/main/resources/static/js/payments.mjs', 'spring-web/target/classes/static/js/payments.mjs', true],
  ['/js/payments-model.mjs', 'spring-web/src/main/resources/static/js/payments-model.mjs', 'spring-web/target/classes/static/js/payments-model.mjs', true],
  ['/css/pricing.css', null, 'spring-web/target/classes/static/css/pricing.css', false],
  ['/css/payments.css', null, 'spring-web/target/classes/static/css/payments.css', false],
]) {
  const served = await getThroughProxy(routePath);
  const built = await readFile(path.join(root, outputPath));
  assert.equal(served.status, 200, `Asset unavailable through proxy: ${routePath}`);
  assert.deepEqual(served.body, built, `Served bytes differ from isolated harness output: ${routePath}`);
  if (compareSource) assert.deepEqual(built, await readFile(path.join(root, sourcePath)), `Built JS differs from source: ${routePath}`);
  servedAssetHashes[routePath] = createHash('sha256').update(served.body).digest('hex');
}

try {
  const context = await browser.newContext({ baseURL: proxyOrigin, viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  page.on('pageerror', error => pageErrors.push(error.name));
  page.on('console', message => { if (message.type() === 'error') expectedConsoleStatuses.push('console-error'); });
  page.on('response', response => { if ([403, 503].includes(response.status())) expectedConsoleStatuses.push(`http-${response.status()}`); });
  await page.goto('/login');
  await page.locator('#username').fill('lumipark-browser-manager');
  await page.locator('#password').fill(password);
  await page.locator('#loginSubmit').click();
  await expect(page).toHaveURL(url => url.origin === proxyOrigin && url.pathname === '/');

  if (process.argv[4] === '--probe') {
    await page.goto('/pricing');
    const form = page.locator('.pricing-form').first();
    const input = form.locator('[name="basePrice"]');
    const oldValue = await input.inputValue();
    const nextValue = String(Number(oldValue) + 1234);
    await input.fill(nextValue);
    await form.locator('button[type="submit"]').click();
    await expect(page.locator('#pricingReviewValues')).toContainText(`${formatVnd(oldValue)} đ → ${formatVnd(nextValue)} đ`);
    responseMode = 'success';
    await page.locator('#pricingConfirm').click();
    await expect(page.locator('#pricingFeedback')).toHaveAttribute('data-state', 'success');
    await expect(page.locator('#pricingFeedbackMessage')).toContainText('synthetic');
    assert.equal(intercepted.length, 1);
    assert.equal(intercepted[0].screen, 'pricing');
    assert.equal(intercepted[0].forwarded, false);
    assert.ok(upstreamReads.some(item => item.path === '/pricing' && item.fixture === 'success'));
    await page.screenshot({ path: path.join(output, 'redirect-flash-probe.png') });
  } else {
    await runPricing(page, check, formatVnd, output);
    await runPayments(page, context, check, output);
  }

  assert.equal(intercepted.filter(item => item.forwarded).length, 0);
  assert.deepEqual(pageErrors, []);
  await writeFile(path.join(output, 'checks.json'), JSON.stringify({
    checks, pageErrors, expectedConsoleStatuses, servedAssetHashes, paymentLayoutBounds, routeAbortedMutations,
    proxy: { origin: proxyOrigin, boundAddress: '127.0.0.1', harnessPort, interceptedMutationPosts: intercepted, forwardedMutationPosts: 0, upstreamReads },
    fixtureBoundary: 'Actual server-rendered pricing forms and H2 values; browser/proxy-only synthetic payments ledger and flash. Login POST only is forwarded to the isolated H2 harness; pricing/reconciliation mutations are never forwarded.',
  }, null, 2));
} finally {
  await browser.close();
  await new Promise(resolve => proxyServer.close(resolve));
}

async function runPricing(page, check, formatVnd, output) {
  await page.goto('/pricing');
  await expect(page.locator('.pricing-form')).toHaveCount(6);
  await check('pricing uses actual server forms and native minimum validation', async () => {
    const form = page.locator('.pricing-form').first();
    const input = form.locator('[name="basePrice"]');
    const oldValue = await input.inputValue();
    await input.fill('-1');
    await form.locator('button[type="submit"]').click();
    await expect(page.locator('#pricingReview')).toBeHidden();
    assert.equal(intercepted.length, 0);
    await input.fill(oldValue);
  });
  await check('pricing review shows localized actual old/new values; Escape/cancel restore focus without POST', async () => {
    const form = page.locator('.pricing-form').first();
    const input = form.locator('[name="basePrice"]');
    const oldValue = await input.inputValue(); const newValue = String(Number(oldValue) + 2345);
    await input.fill(newValue); const trigger = form.locator('button[type="submit"]');
    await trigger.click();
    await expect(page.locator('#pricingReviewValues')).toContainText(`${formatVnd(oldValue)} đ → ${formatVnd(newValue)} đ`);
    await expect(page.locator('#pricingReviewValues')).toContainText('Không đổi');
    await page.screenshot({ path: path.join(output, 'pricing-review-desktop-1440x1000.png') });
    await expect(page.locator('#pricingReviewTitle')).toBeFocused();
    await page.keyboard.press('Shift+Tab'); await expect(page.locator('#pricingConfirm')).toBeFocused();
    await page.keyboard.press('Tab'); await expect(page.locator('#pricingCancel')).toBeFocused();
    await page.keyboard.press('Shift+Tab'); await expect(page.locator('#pricingConfirm')).toBeFocused();
    await page.keyboard.press('Tab'); await expect(page.locator('#pricingCancel')).toBeFocused();
    await page.keyboard.press('Shift+Tab'); await expect(page.locator('#pricingConfirm')).toBeFocused();
    await page.keyboard.press('Escape'); await expect(trigger).toBeFocused();
    await trigger.click(); await page.locator('#pricingCancel').click(); await expect(trigger).toBeFocused();
    assert.equal(intercepted.length, 0);
    assert.equal(Number(oldValue), 2000, 'The isolated first pricing form should retain its known H2 base price.');
    await input.fill(`${oldValue}.0`); await trigger.click();
    await expect(page.locator('#pricingReviewValues')).toContainText('Không đổi · 2.000,0 đ');
    assert.equal(await input.inputValue(), `${oldValue}.0`, 'Review comparison must not rewrite the submitted field value.');
    await page.screenshot({ path: path.join(output, 'pricing-equivalent-numeric-review-1440x1000.png') });
    await page.keyboard.press('Escape'); await expect(trigger).toBeFocused();
    await input.fill('2000.0000001'); await trigger.click();
    await expect(page.locator('#pricingReviewValues')).toContainText('2.000,0000001 đ');
    assert.equal(await input.inputValue(), '2000.0000001');
    await page.screenshot({ path: path.join(output, 'pricing-submitted-decimal-precision-1440x1000.png') });
    await page.keyboard.press('Escape'); await expect(trigger).toBeFocused();
    await input.fill('2000.0000000000001'); await trigger.click();
    const changedPriceRow = page.locator('#pricingReviewValues > div').filter({ hasText: 'Giá lượt ban ngày' });
    await expect(changedPriceRow).toContainText('2.000,0000000000001 đ');
    await expect(changedPriceRow).not.toContainText('Không đổi');
    await page.keyboard.press('Escape'); await expect(trigger).toBeFocused();
    await input.fill(oldValue);
    await page.setViewportSize({ width: 320, height: 844 }); await trigger.click();
    await page.screenshot({ path: path.join(output, 'pricing-review-mobile-320x844.png') });
    await page.keyboard.press('Escape');
    await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/pricing');
    const car = page.locator('.pricing-form').filter({ has: page.locator('input[name="nightPrice"][readonly]') }).first();
    await expect(car).toHaveCount(1);
    await car.locator('button[type="submit"]').click();
    await expect(car.locator('[name="nightPrice"]')).toHaveAttribute('readonly', 'readonly');
    await expect(page.locator('#pricingReviewValues')).toContainText('Không đổi');
    await page.screenshot({ path: path.join(output, 'pricing-car-review-390x844.png') });
    await page.keyboard.press('Escape');
    await page.setViewportSize({ width: 812, height: 375 });
    await page.screenshot({ path: path.join(output, 'pricing-landscape-812x375.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/pricing');
  });
  await check('pricing delayed response exposes disabled/busy then real loopback redirect flash success', async () => {
    const form = page.locator('.pricing-form').first(); await form.locator('button[type="submit"]').click();
    holdNextPost = true; responseMode = 'success';
    const pending = new Promise(resolve => { pendingReady = resolve; });
    await page.locator('#pricingConfirm').click(); await pending;
    await expect(page.locator('#pricingList')).toHaveAttribute('aria-busy', 'true');
    await expect(page.locator('#pricingFeedback')).toHaveAttribute('data-state', 'pending');
    await expect(form.locator('button[type="submit"]')).toBeDisabled();
    await page.screenshot({ path: path.join(output, 'pricing-pending-390x844.png') });
    pendingResolver(); pendingResolver = null; pendingReady = null;
    await expect(page.locator('#pricingFeedback')).toHaveAttribute('data-state', 'success');
    await expect(page.locator('#pricingFeedbackMessage')).toContainText('synthetic');
  });
  for (const [mode, message] of [['forbidden', 'Phiên đăng nhập hoặc quyền'], ['session', 'Phiên đăng nhập hoặc quyền'], ['unknown', 'Không xác nhận được kết quả'], ['missing-flash', 'Không xác nhận được kết quả']]) {
    const before = intercepted.length;
    await page.goto('/pricing'); responseMode = mode;
    const form = page.locator('.pricing-form').first(); await form.locator('button[type="submit"]').click(); await page.locator('#pricingConfirm').click();
    await expect(page.locator('#pricingFeedbackMessage')).toContainText(message);
    await expect(form.locator('button[type="submit"]')).toBeDisabled();
    assert.equal(intercepted.length, before + 1, `Expected exactly one ${mode} interception.`);
    await page.screenshot({ path: path.join(output, `pricing-${mode}-1440x1000.png`) });
  }
  await check('pricing route-aborted network failure locks submission without a proxy request or replay', async () => {
    const beforeProxy = intercepted.length; const beforeAborts = routeAbortedMutations.length;
    const unroute = await routeAbortNextMutation(page, 'pricing');
    await page.goto('/pricing');
    const form = page.locator('.pricing-form').first();
    await form.locator('button[type="submit"]').click();
    await page.locator('#pricingConfirm').click();
    await expect(page.locator('#pricingFeedbackMessage')).toContainText('Mất kết nối');
    await expect(form.locator('button[type="submit"]')).toBeDisabled();
    assert.equal(routeAbortedMutations.length, beforeAborts + 1);
    assert.equal(intercepted.length, beforeProxy, 'The route-aborted request must not reach the loopback proxy.');
    await page.screenshot({ path: path.join(output, 'pricing-network-aborted-1440x1000.png') });
    await unroute();
  });
}

async function runPayments(page, context, check, output) {
  await page.setViewportSize({ width: 1440, height: 1000 }); await page.goto('/payments');
  await check('populated ledger search/status/no-match and full desktop table/action fit', async () => {
    await expect(page.locator('.payment-row')).toHaveCount(3);
    await page.locator('#paymentSearch').fill('QA-SUB-REF-02'); await expect(page.locator('.payment-row:visible')).toHaveCount(1);
    await page.locator('#paymentStatusFilter').selectOption('PAID'); await expect(page.locator('.payment-row:visible')).toHaveCount(1);
    await page.locator('#paymentSearch').fill('NO-MATCH'); await expect(page.locator('#paymentsNoResults')).toBeVisible();
    await page.locator('#paymentSearch').fill(''); await page.locator('#paymentStatusFilter').selectOption('');
    await expect(page.locator('.payment-row:visible')).toHaveCount(3);
    await page.screenshot({ path: path.join(output, 'payments-desktop-populated-1440x1000.png'), fullPage: true });
    const region1440 = page.locator('.payments-table-scroll'); const button1440 = page.locator('.payment-reconcile-form button').first();
    const regionBox1440 = await region1440.boundingBox(); const buttonBox1440 = await button1440.boundingBox();
    paymentLayoutBounds.push({ viewport: '1440x1000', region: regionBox1440, action: buttonBox1440 });
    assert.ok(buttonBox1440.x + buttonBox1440.width <= regionBox1440.x + regionBox1440.width + 1, '1440px reconcile action must fit inside the ledger viewport.');
    await page.setViewportSize({ width: 1366, height: 900 });
    await page.screenshot({ path: path.join(output, 'payments-desktop-populated-1366x900.png'), fullPage: true });
    const regionBox1366 = await region1440.boundingBox(); const buttonBox1366 = await button1440.boundingBox();
    paymentLayoutBounds.push({ viewport: '1366x900', region: regionBox1366, action: buttonBox1366 });
    assert.ok(buttonBox1366.x + buttonBox1366.width <= regionBox1366.x + regionBox1366.width + 1, '1366px reconcile action must fit inside the ledger viewport.');
    await page.setViewportSize({ width: 1024, height: 768 });
    await page.screenshot({ path: path.join(output, 'payments-tablet-1024x768.png'), fullPage: true });
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await check('mobile retains every column in a keyboard-accessible horizontal table region', async () => {
    const region = page.locator('.payments-table-scroll'); await region.focus();
    for (let index = 0; index < 36; index++) await page.keyboard.press('ArrowRight');
    await expect(page.locator('.payments-table thead th')).toHaveCount(8);
    await page.locator('.payment-reconcile-form button').first().focus();
    await page.screenshot({ path: path.join(output, 'payments-mobile-populated-390x844.png'), fullPage: true });
    await page.setViewportSize({ width: 320, height: 844 });
    await page.screenshot({ path: path.join(output, 'payments-narrow-320x844.png'), fullPage: true });
    await page.setViewportSize({ width: 812, height: 375 });
    await page.screenshot({ path: path.join(output, 'payments-landscape-812x375.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
  });
  await check('required/whitespace/maxlength note validation preserves correction and dialog identity; cancel/Escape send zero POST', async () => {
    const form = page.locator('.payment-reconcile-form').first(); const note = form.locator('[name="note"]'); const trigger = form.locator('button[type="submit"]');
    await note.fill(''); await trigger.click(); await expect(page.locator('#paymentReview')).toBeHidden();
    await note.fill('   '); await trigger.click();
    await expect(page.locator('#paymentReview')).toBeHidden(); await expect(form.locator('.payment-note-error')).toBeVisible();
    await expect(note).toHaveAttribute('aria-invalid', 'true');
    await note.fill('Đã kiểm tra synthetic'); await trigger.click();
    await expect(form.locator('.payment-note-error')).toBeHidden(); await expect(note).not.toHaveAttribute('aria-invalid');
    await expect(page.locator('#paymentReviewReceipt')).toHaveText('UXQA-RCPT-PAID-01');
    await expect(page.locator('#paymentReviewAmount')).toHaveText('135.000 đ');
    await expect(page.locator('#paymentReviewNote')).toContainText('synthetic');
    await page.screenshot({ path: path.join(output, 'payments-review-open-mobile-390x844.png'), fullPage: true });
    await expect(page.locator('#paymentReviewTitle')).toBeFocused();
    await page.keyboard.press('Shift+Tab'); await expect(page.locator('#paymentConfirm')).toBeFocused();
    await page.keyboard.press('Tab'); await expect(page.locator('#paymentCancel')).toBeFocused();
    await page.keyboard.press('Shift+Tab'); await expect(page.locator('#paymentConfirm')).toBeFocused();
    await page.keyboard.press('Tab'); await expect(page.locator('#paymentCancel')).toBeFocused();
    await page.keyboard.press('Shift+Tab'); await expect(page.locator('#paymentConfirm')).toBeFocused();
    await page.keyboard.press('Escape'); await expect(trigger).toBeFocused();
    await trigger.click(); await page.locator('#paymentCancel').click(); await expect(trigger).toBeFocused();
    assert.equal(intercepted.filter(item => item.screen === 'payments').length, 0);
    await note.focus(); await note.fill(''); await note.pressSequentially('x'.repeat(501)); assert.equal((await note.inputValue()).length, 500);
    await note.fill('Đã kiểm tra synthetic'); await trigger.click();
  });
  await check('payment delayed redirect shows busy then synthetic server flash success', async () => {
    holdNextPost = true; responseMode = 'success';
    const pending = new Promise(resolve => { pendingReady = resolve; });
    await page.locator('#paymentConfirm').click(); await pending;
    await expect(page.locator('.payments-ledger')).toHaveAttribute('aria-busy', 'true');
    await expect(page.locator('#paymentsFeedback')).toHaveAttribute('data-state', 'pending');
    await expect(page.locator('.payment-reconcile-form button').first()).toBeDisabled();
    await page.screenshot({ path: path.join(output, 'payments-pending-mobile-390x844.png'), fullPage: true });
    pendingResolver(); pendingResolver = null; pendingReady = null;
    await expect(page.locator('#paymentsFeedback')).toHaveAttribute('data-state', 'success');
    await expect(page.locator('#paymentsFeedbackMessage')).toContainText('synthetic');
    assert.equal(intercepted.filter(item => item.screen === 'payments' && item.mode === 'success').length, 1);
  });
  for (const [mode, message] of [['forbidden', 'Phiên đăng nhập hoặc quyền'], ['session', 'Phiên đăng nhập hoặc quyền'], ['unknown', 'Không xác nhận được kết quả'], ['missing-flash', 'Không xác nhận được kết quả']]) {
    const before = intercepted.length;
    await page.goto('/payments'); responseMode = mode;
    const form = page.locator('.payment-reconcile-form').first(); await form.locator('[name="note"]').fill(`Synthetic ${mode}`); await form.locator('button[type="submit"]').click(); await page.locator('#paymentConfirm').click();
    await expect(page.locator('#paymentsFeedbackMessage')).toContainText(message);
    await expect(form.locator('button[type="submit"]')).toBeDisabled();
    assert.equal(intercepted.length, before + 1, `Expected exactly one ${mode} interception.`);
    await page.screenshot({ path: path.join(output, `payments-${mode}-1440x1000.png`), fullPage: true });
  }
  await check('payments route-aborted network failure locks reconciliation without a proxy request or replay', async () => {
    const beforeProxy = intercepted.length; const beforeAborts = routeAbortedMutations.length;
    const unroute = await routeAbortNextMutation(page, 'payments');
    await page.goto('/payments'); responseMode = 'success';
    const form = page.locator('.payment-reconcile-form').first();
    await form.locator('[name="note"]').fill('Synthetic network failure');
    await form.locator('button[type="submit"]').click(); await page.locator('#paymentConfirm').click();
    await expect(page.locator('#paymentsFeedbackMessage')).toContainText('Mất kết nối');
    await expect(form.locator('button[type="submit"]')).toBeDisabled();
    assert.equal(routeAbortedMutations.length, beforeAborts + 1);
    assert.equal(intercepted.length, beforeProxy, 'The route-aborted request must not reach the loopback proxy.');
    await page.screenshot({ path: path.join(output, 'payments-network-aborted-1440x1000.png'), fullPage: true });
    await unroute();
  });
  await page.goto('/payments?fixture=empty');
  await check('true empty ledger remains distinct from filtered no-match', async () => {
    await expect(page.locator('.payment-row')).toHaveCount(0); await expect(page.locator('.payments-empty').first()).toBeVisible();
    await page.screenshot({ path: path.join(output, 'payments-empty-1440x1000.png'), fullPage: true });
  });
}
