// Opt-in browser check against the isolated LoginBrowserHarnessTest only.
import { createRequire } from 'node:module';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const require = createRequire(path.join(root, 'spring-web/package.json'));
const { chromium } = require('@playwright/test');
const run = path.resolve(process.argv[2] ?? '');
assert.ok(run.startsWith(path.join(root, '.artifacts/test-runs') + path.sep));
const port = (await readFile(path.join(run, 'server.port'), 'utf8')).trim();
assert.match(port, /^\d{1,5}$/);
const fixture = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginTestIsolation.java'), 'utf8');
const password = fixture.match(/PASSWORD = "([^"]+)"/)[1];
const harness = await readFile(path.join(root, 'spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java'), 'utf8');
const username = harness.match(/MANAGER_USERNAME = "([^"]+)"/)[1];
const stage = process.argv[3] ?? 'management-review';
assert.match(stage, /^[a-z0-9-]+$/);
const output = path.join(run, stage);
await mkdir(output);
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const checks = [];
let page;
try {
  page = await browser.newPage({ baseURL: `http://127.0.0.1:${port}` });
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await Promise.all([page.waitForURL(/\/$/), page.locator('#loginSubmit').click()]);
  for (const [name, route] of [['households','/households'],['vehicles','/vehicles'],['audit','/security-audit'],['settings','/settings'],['password','/account/password']]) {
    for (const [label,width,height] of [['desktop',1440,900],['mobile',390,844],['narrow',320,740]]) {
      await page.setViewportSize({ width, height });
      const response = await page.goto(route);
      assert.equal(response.status(), 200, `${name} response`);
      assert.equal(await page.locator('h1').count(), 1, `${name} heading`);
      const bounds = await page.evaluate(() => ({ width: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth }));
      assert.ok(bounds.scroll <= bounds.width + 1, `${name} horizontal page overflow`);
      await page.screenshot({ path: path.join(output, `${name}-${label}.png`), fullPage: true });
      if (name === 'households' || name === 'vehicles') {
        const editor = name === 'households' ? '#householdEditor' : '#vehicleEditor';
        assert.equal(await page.locator(editor).isVisible(), false);
        await page.locator(name === 'households' ? '#newHousehold' : '#newVehicle').click();
        assert.equal(await page.locator(editor).isVisible(), true);
        const editorBounds = await page.evaluate(() => ({ width: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth }));
        assert.ok(editorBounds.scroll <= editorBounds.width + 1, `${name} editor horizontal overflow at ${label}`);
        await page.screenshot({ path: path.join(output, `${name}-editor-${label}.png`), fullPage: true });
        if (name === 'households') {
          await page.locator('#closeHousehold').click();
          const household = page.locator('#householdRows tr').filter({ hasText: 'UXQA-101' });
          await household.getByRole('link', { name: 'Mở hồ sơ' }).click();
          await page.getByRole('heading', { name: /UXQA-HOUSEHOLD/ }).waitFor();
          await page.screenshot({ path: path.join(output, `household-profile-${label}.png`), fullPage: true });
          await page.locator('.edit-household').click();
          assert.equal(await page.locator('#householdForm [name=householdCode]').isEditable(), false);
          await page.locator('#closeHousehold').click();
          await page.locator('#addMember').click();
          assert.equal(await page.locator('#memberEditor').isVisible(), true);
          await page.screenshot({ path: path.join(output, `member-editor-${label}.png`), fullPage: true });
          await page.locator('#closeMember').click();

          await page.goto('/households?mode=residents&q=Synthetic%20browser%20resident');
          const resident = page.locator('#memberRows tr').filter({ has: page.getByText('Synthetic browser resident', { exact: true }) });
          await resident.getByRole('link', { name: 'Mở hồ sơ' }).click();
          await page.getByRole('heading', { name: 'Synthetic browser resident' }).waitFor();
          await page.screenshot({ path: path.join(output, `resident-profile-${label}.png`), fullPage: true });
          assert.equal(await page.locator('#memberEditor').isVisible(), false);
          await page.locator('.edit-member').click();
          assert.equal(await page.locator('#memberEditor').isVisible(), true);
          await page.screenshot({ path: path.join(output, `resident-edit-${label}.png`), fullPage: true });
        }
      }
      checks.push({ name, viewport: label, status: 'PASS' });
    }
  }
  let vehicleData;
  const secondResidentOption = page.locator('#authorizedMemberOptions .check-option').filter({ hasText: 'Synthetic browser resident two' });
  for (const [label,width,height] of [['desktop',1440,900],['mobile',390,844],['narrow',320,740]]) {
    await page.setViewportSize({ width, height });
    await page.goto('/vehicles?q=UXQA-MOTO');
    const vehicle = page.locator('#vehicleRows tr').filter({ hasText: 'UXQA-MOTO-01' });
    assert.equal(await page.locator('#vehicleRows tr').filter({ hasText: 'UXQA-MOTO-02' }).count(), 1);
    assert.equal((await page.locator('main').textContent()).includes('private/synthetic-registration-reference'), false,
      'vehicle pages must not expose stored registration-image paths');
    await vehicle.getByRole('link', { name: 'Mở hồ sơ' }).click();
    await page.getByRole('heading', { name: 'UXQA-MOTO-01' }).waitFor();
    assert.match(await page.locator('.management-record').textContent(), /1 \/ 2 xe hai bánh/,
      'the vehicle profile counts active same-category vehicles while excluding the selected vehicle');
    assert.match(await page.locator('.management-record').textContent(), /Có ảnh giấy đăng ký đã lưu/);
    const profileBounds = await page.evaluate(() => ({ width: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth }));
    assert.ok(profileBounds.scroll <= profileBounds.width + 1, `vehicle profile horizontal overflow at ${label}`);
    await page.screenshot({ path: path.join(output, `vehicle-profile-${label}.png`), fullPage: true });

    const vehicleDataPromise = page.waitForResponse(response => response.url().includes('/admin-data/vehicles/')
      && !response.url().includes('quota-preview') && response.request().method() === 'GET');
    await page.locator('.edit-vehicle').click();
    vehicleData = await (await vehicleDataPromise).json();
    assert.equal(vehicleData.registrationImageAvailable, true);
    assert.equal(Object.hasOwn(vehicleData, 'registrationImagePath'), false,
      'the vehicle editor data contract must not return a private storage reference');
    assert.equal(await page.locator('#vehicleEditor').isVisible(), true);
    assert.equal(await page.locator('#vehicleRows').isVisible(), false, 'the editor replaces the result list while active');
    assert.equal(await page.locator('#vehicleToEdit').count(), 0, 'the editor must not repeat the chosen-vehicle picker');
    await page.locator('#vehicleQuota').getByText(/1 \/ 2 xe đang hoạt động/).waitFor();
    assert.match(await page.locator('#vehicleOwnerIncluded').textContent(), /luôn được tính/);
    assert.equal(await secondResidentOption.isVisible(), false, 'authorized people from another household stay out of the choices');
    const editorBounds = await page.evaluate(() => ({ width: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth }));
    assert.ok(editorBounds.scroll <= editorBounds.width + 1, `vehicle editor horizontal overflow at ${label}`);
    await page.screenshot({ path: path.join(output, `vehicle-editor-profile-${label}.png`), fullPage: true });
    if (label !== 'narrow') await page.locator('#closeVehicle').click();
  }

  await page.locator('#vehicleHousehold').selectOption({ label: 'UXQA-HOUSEHOLD-2 - UXQA-202' });
  await page.locator('#vehicleQuota').getByText(/0 \/ 2 xe đang hoạt động/).waitFor();
  assert.equal(await page.locator('#vehicleOwner').inputValue(), '');
  assert.equal(await secondResidentOption.isVisible(), true, 'changing household scopes choices to the new household');
  await page.locator('#vehicleHousehold').selectOption({ label: 'UXQA-HOUSEHOLD - UXQA-101' });
  await page.locator('#vehicleType').selectOption('CAR');
  await page.locator('#vehicleQuota').getByText(/0 \/ 1 xe đang hoạt động/).waitFor();
  await page.locator('#vehicleType').selectOption('MOTORBIKE');

  await page.locator('[name=brand]').fill('synthetic edit');
  page.once('dialog', dialog => dialog.dismiss());
  await page.locator('#closeVehicle').click();
  assert.equal(await page.locator('[name=brand]').inputValue(), 'synthetic edit');
  page.once('dialog', dialog => dialog.accept());
  await page.locator('#closeVehicle').click();
  assert.equal(await page.locator('#vehicleEditor').isVisible(), false);
  await page.locator('.edit-vehicle').click();
  await page.locator('#registrationImage').setInputFiles({ name: 'not-an-image.txt', mimeType: 'text/plain', buffer: Buffer.from('synthetic invalid image') });
  assert.match(await page.locator('#registrationImageStatus').textContent(), /phải là hình ảnh/);
  assert.equal(await page.locator('#registrationImage').evaluate(input => input.files.length), 0);
  await page.locator('[name=brand]').fill('Browser saved vehicle');
  await Promise.all([
    page.waitForURL(url => url.pathname === '/vehicles' && !url.searchParams.has('vehicleId')),
    page.locator('#saveVehicleButton').click()
  ]);
  await page.locator('.notice').waitFor({ state: 'visible' });
  assert.match(await page.locator('.notice').textContent(), /Đã lưu phương tiện/);
  assert.equal(await page.evaluate(() => sessionStorage.getItem('managementCoreSaveNotice')), null,
    'the transient success notice is removed after it is rendered');
  const savedVehicleData = await page.evaluate(async id => (await fetch(`/admin-data/vehicles/${id}`)).json(), vehicleData.id);
  assert.equal(savedVehicleData.brand, 'Browser saved vehicle', 'the successful response reflects persisted vehicle edits');

  await page.goto('/vehicles?q=not-a-real-vehicle');
  assert.match(await page.locator('#vehicleRows').textContent(), /Không tìm thấy phương tiện phù hợp/);
  await page.goto('/households');
  const household = page.locator('#householdRows tr').filter({ hasText: 'UXQA-101' });
  await household.getByRole('link', { name: 'Mở hồ sơ' }).click();
  await page.locator('.edit-household').click();
  assert.ok(await page.locator('#householdForm [name=householdCode]').inputValue());
  await page.locator('#householdForm [name=apartmentNumber]').fill('draft test');
  page.once('dialog', dialog => dialog.dismiss());
  await page.locator('#closeHousehold').click();
  assert.equal(await page.locator('#householdForm [name=apartmentNumber]').inputValue(), 'draft test');
  page.once('dialog', dialog => dialog.accept());
  await page.locator('#closeHousehold').click();
  assert.equal(await page.locator('#householdEditor').isVisible(), false);

  await page.locator('#addMember').click();
  await page.locator('#memberForm [name=fullName]').fill('Synthetic draft');
  page.once('dialog', dialog => dialog.dismiss());
  await page.locator('#closeMember').click();
  assert.equal(await page.locator('#memberForm [name=fullName]').inputValue(), 'Synthetic draft');
  page.once('dialog', dialog => dialog.accept());
  await page.locator('#closeMember').click();
  assert.equal(await page.locator('#memberEditor').isVisible(), false);
  await page.locator('#newHousehold').click();
  await page.locator('#householdForm [name=householdCode]').fill('QA-UX-CORE');
  await page.locator('#householdForm [name=apartmentNumber]').fill('QA-101');
  await page.locator('#householdForm button[type=submit], #householdForm button:not([type])').click();
  await page.locator('.notice').waitFor({ state: 'visible' });
  assert.match(await page.locator('.notice').textContent(), /QA-UX-CORE/);

  await page.addInitScript(() => {
    const canvas = document.createElement('canvas');
    canvas.width = 640;
    canvas.height = 480;
    const context = canvas.getContext('2d');
    let frame = 0;
    setInterval(() => { context.fillStyle = frame++ % 2 ? '#ddd' : '#eee'; context.fillRect(0, 0, 640, 480); }, 200);
    window.syntheticCameraAllowed = true;
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', {
      configurable: true,
      value: async () => {
        if (!window.syntheticCameraAllowed) throw new DOMException('Permission denied', 'NotAllowedError');
        return canvas.captureStream(1);
      }
    });
  });
  const privateImageRequests = [];
  page.on('request', request => { if (request.url().includes('/face-image')) privateImageRequests.push(request.url()); });
  await page.goto('/households?mode=residents&q=Synthetic%20browser%20resident');
  const resident = page.locator('#memberRows tr').filter({ hasText: 'UXQA-HOUSEHOLD / UXQA-101' });
  await resident.getByRole('link', { name: 'Mở hồ sơ' }).click();
  await page.locator('.edit-member').click();
  await page.waitForFunction(() => !document.querySelector('#memberForm').inert && document.querySelector('#memberId').value !== '');
  await page.locator('#memberForm [name=fullName]').fill('Typed before QR review');
  await page.route('**/api/cccd/scan-qr', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ raw: 'SYNTHETIC-QR-PAYLOAD', citizenId: '999999999999', fullName: 'QR suggested name', message: 'Synthetic QR read' })
  }));
  await page.locator('#cccdQrFile').setInputFiles({ name: 'synthetic-qr.png', mimeType: 'image/png', buffer: Buffer.from('synthetic image') });
  await page.locator('#scanQr').click();
  assert.equal(await page.locator('#memberForm [name=fullName]').inputValue(), 'Typed before QR review', 'scanning must not silently overwrite user-entered data');
  await page.locator('#qrReview').waitFor({ state: 'visible' });
  assert.equal(await page.locator('#qrReviewFields label').filter({ hasText: 'Họ và tên' }).locator('input').isChecked(), false, 'QR must not preselect a manually entered field');
  assert.equal(await page.locator('#qrReviewFields label').filter({ hasText: 'Số CCCD' }).locator('input').isChecked(), true, 'blank fields can be selected for explicit application');
  await page.screenshot({ path: path.join(output, 'resident-qr-review-mobile.png'), fullPage: true });
  await page.locator('#applyQrValues').click();
  assert.equal(await page.locator('#memberForm [name=fullName]').inputValue(), 'Typed before QR review');
  assert.equal(await page.locator('#memberForm [name=citizenId]').inputValue(), '999999999999');
  assert.equal(await page.locator('#memberForm [name=cccdQrRaw]').inputValue(), '', 'raw QR data stays opt-in');

  await page.locator('#cccdQrFile').setInputFiles({ name: 'synthetic-qr.png', mimeType: 'image/png', buffer: Buffer.from('synthetic image') });
  await page.locator('#openQrCamera').click();
  assert.equal(await page.locator('#cccdQrFile').evaluate(input => input.files.length), 0, 'QR upload and camera are mutually exclusive');
  await page.waitForFunction(() => document.querySelector('#qrVideo').videoWidth > 0, null, { timeout: 10000 });
  await page.locator('#captureQr').click();
  await page.locator('#qrReview').waitFor({ state: 'visible' });
  await page.locator('#applyQrRaw').check();
  await page.locator('#applyQrValues').click();
  assert.equal(await page.locator('#memberForm [name=cccdQrRaw]').inputValue(), 'SYNTHETIC-QR-PAYLOAD', 'raw QR data is retained only after explicit opt-in');

  await page.locator('#faceUpload').setInputFiles({ name: 'synthetic-face.png', mimeType: 'image/png', buffer: Buffer.from('synthetic image') });
  assert.match(await page.locator('#faceSourceStatus').textContent(), /ảnh tải lên đã sẵn sàng/i);
  assert.equal(await page.locator('#facePreview').isVisible(), true, 'the preview must identify the selected upload source');
  await page.evaluate(() => { window.syntheticCameraAllowed = false; });
  await page.locator('#openCamera').click();
  assert.match(await page.locator('#faceSourceStatus').textContent(), /không mở được camera/i, 'camera permission denial must remain visible');
  assert.equal(await page.locator('#captureFace').isVisible(), false);
  await page.evaluate(() => { window.syntheticCameraAllowed = true; });
  await page.locator('#faceUpload').setInputFiles({ name: 'synthetic-face.png', mimeType: 'image/png', buffer: Buffer.from('synthetic image') });
  await page.locator('#openCamera').click();
  assert.equal(await page.locator('#faceUpload').evaluate(input => input.files.length), 0, 'face upload and camera are mutually exclusive');
  await page.waitForFunction(() => document.querySelector('#faceVideo').videoWidth > 0, null, { timeout: 10000 });
  await page.locator('#captureFace').click();
  assert.match(await page.locator('#registrationFaceCapture').inputValue(), /^data:image\/jpeg;base64,/);
  assert.match(await page.locator('#faceSourceStatus').textContent(), /ảnh chụp đã sẵn sàng/i);
  await page.screenshot({ path: path.join(output, 'resident-media-preview-mobile.png'), fullPage: true });
  assert.deepEqual(privateImageRequests, [], 'editing a resident must not fetch the private stored face image');

  const saveResponses = [
    { status: 302, headers: { location: '/login' }, body: '' },
    { status: 400, contentType: 'text/html', body: '<main><h1>Kết quả thao tác lưu</h1><p role="alert">Dữ liệu không hợp lệ.</p><p>Hãy sửa dữ liệu rồi gửi lại.</p></main>' },
    { status: 503, contentType: 'text/html', body: '<main><h1>Kết quả thao tác lưu</h1><p role="alert">Không xác nhận được kết quả lưu.</p><p>Không gửi lại ngay.</p></main>' }
  ];
  let saveRequests = 0;
  await page.route('**/members', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    const response = saveResponses[saveRequests++];
    await route.fulfill(response);
  });
  const waitForMemberStatus = text => page.waitForFunction(value =>
    new RegExp(value, 'i').test(document.querySelector('#memberSaveStatus')?.textContent ?? ''), text);
  await page.locator('#saveMemberButton').click();
  await waitForMemberStatus('phiên đăng nhập');
  assert.match(await page.locator('#memberSaveStatus').textContent(), /phiên đăng nhập/i);
  assert.equal(await page.locator('#memberForm [name=fullName]').inputValue(), 'Typed before QR review');
  assert.equal(await page.locator('#memberLoginLink').isVisible(), true);
  await page.locator('#saveMemberButton').click();
  await waitForMemberStatus('không hợp lệ');
  assert.match(await page.locator('#memberSaveStatus').textContent(), /không hợp lệ/);
  assert.equal(await page.locator('#memberForm [name=fullName]').inputValue(), 'Typed before QR review');
  assert.equal(await page.locator('#saveMemberButton').isEnabled(), true, 'a rejected request can be corrected and manually submitted again');
  await page.locator('#saveMemberButton').click();
  await waitForMemberStatus('không xác nhận được kết quả lưu');
  assert.match(await page.locator('#memberSaveStatus').textContent(), /không xác nhận được kết quả lưu/i);
  assert.equal(await page.locator('#saveMemberButton').isEnabled(), false, 'an unknown write outcome must not invite an immediate retry');
  assert.equal(await page.locator('#memberReloadLink').isVisible(), true);
  assert.equal(saveRequests, 3, 'each request follows an explicit operator click; no automatic retry occurs');

  await page.unroute('**/members');
  page.once('dialog', dialog => dialog.accept());
  await page.goto('/households?mode=residents&q=Synthetic%20browser%20resident');
  const savedResident = page.locator('#memberRows tr').filter({ hasText: 'UXQA-HOUSEHOLD / UXQA-101' });
  await savedResident.getByRole('link', { name: 'Mở hồ sơ' }).click();
  await page.locator('.edit-member').click();
  await page.waitForFunction(() => !document.querySelector('#memberForm').inert && document.querySelector('#memberId').value !== '');
  await page.locator('#memberForm [name=fullName]').fill('Saved synthetic resident');
  await page.locator('#openCamera').click();
  await page.waitForFunction(() => document.querySelector('#faceVideo').videoWidth > 0, null, { timeout: 10000 });
  await page.locator('#captureFace').click();
  await Promise.all([
    page.waitForURL(url => url.pathname === '/households'),
    page.locator('#saveMemberButton').click()
  ]);
  await page.locator('.notice').waitFor({ state: 'visible' });
  assert.match(await page.locator('.notice').textContent(), /Đã lưu cư dân/);
  assert.equal(await page.evaluate(() => sessionStorage.getItem('managementCoreSaveNotice')), null,
    'resident success feedback is transient and contains no resident details');
  await page.goto('/security-audit?from=2026-02-02&to=2026-01-01');
  assert.match(await page.locator('[role=alert]').textContent(), /không hợp lệ/);
  const advancedAuditFilters = page.locator('.audit-advanced');
  assert.equal(await advancedAuditFilters.evaluate(details => details.open), false,
    'less frequent audit filters start collapsed on a narrow viewport');
  await advancedAuditFilters.locator('summary').click();
  assert.equal(await page.locator('[name=actor]').isVisible(), true);

  await page.goto('/settings');
  const limitInputs = page.locator('#limitForm input[type=number]');
  const savedTwoWheelers = await limitInputs.nth(0).inputValue();
  const savedCars = await limitInputs.nth(1).inputValue();
  assert.equal(await page.locator('#saveLimitButton').isDisabled(), true, 'unchanged settings cannot be submitted');
  assert.equal(await page.locator('#resetLimitButton').isDisabled(), true, 'reset is disabled without a draft');
  await limitInputs.nth(1).fill('0');
  assert.equal(await limitInputs.nth(1).evaluate(input => input.validity.valid), true, 'zero remains a valid explicit limit');
  assert.equal(await page.locator('#saveLimitButton').isEnabled(), true);
  page.once('dialog', dialog => dialog.dismiss());
  await page.goto('/vehicles').catch(() => {});
  assert.ok(page.url().endsWith('/settings'), 'leaving settings with a draft asks before discarding it');
  await page.locator('#resetLimitButton').click();
  assert.equal(await limitInputs.nth(0).inputValue(), savedTwoWheelers);
  assert.equal(await limitInputs.nth(1).inputValue(), savedCars);
  assert.equal(await page.locator('#saveLimitButton').isDisabled(), true);

  await page.goto('/account/password');
  await page.locator('#currentPassword').fill(password);
  await page.locator('#newPassword').fill('Synthetic-Change-2026');
  await page.locator('#confirmPassword').fill('Synthetic-Change-2026');
  await page.locator('[data-password-target="newPassword"]').click();
  assert.equal(await page.locator('#newPassword').getAttribute('type'), 'text');
  assert.equal(await page.locator('#currentPassword').getAttribute('type'), 'password', 'password visibility controls are independent');
  assert.equal(await page.locator('[data-password-target="newPassword"]').getAttribute('aria-pressed'), 'true');
  await page.locator('[data-password-target="newPassword"]').click();
  assert.equal(await page.locator('#newPassword').getAttribute('type'), 'password');
  assert.match(await page.locator('#passwordLengthStatus').textContent(), /21 đơn vị UTF-16/);
  await page.locator('#confirmPassword').fill('Mismatch-Change-2026');
  assert.match(await page.locator('#confirmPasswordStatus').textContent(), /chưa khớp/);
  await page.locator('#confirmPassword').fill('Synthetic-Change-2026');
  await Promise.all([
    page.waitForURL(/\/login\?passwordChanged/),
    page.locator('#passwordForm button[type=submit]').click()
  ]);
  assert.match(await page.locator('body').textContent(), /Đổi mật khẩu thành công/);

  const forcedUsername = harness.match(/FORCED_CHANGE_USERNAME = "([^"]+)"/)[1];
  await page.locator('#username').fill(forcedUsername);
  await page.locator('#password').fill(password);
  await Promise.all([page.waitForURL(/\/account\/password$/), page.locator('#loginSubmit').click()]);
  await page.setViewportSize({ width: 320, height: 740 });
  const forcedPasswordBounds = await page.evaluate(() => ({ width: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth }));
  assert.ok(forcedPasswordBounds.scroll <= forcedPasswordBounds.width + 1, 'forced password page has no horizontal overflow at 320px');
  await page.screenshot({ path: path.join(output, 'password-forced-narrow.png'), fullPage: true });
  assert.equal(await page.locator('.password-logout-form').isVisible(), true, 'forced password change offers CSRF logout');
  assert.equal(await page.locator('#coreMain').count(), 0, 'forced password page remains standalone without management navigation');
  await page.locator('.password-logout').click();
  await page.waitForURL(/\/login\?logout/);
  checks.push({ name: 'household/resident/vehicle profiles and editors, server search, quota, draft/save recovery, QR/media, audit filters, settings draft/reset, password lifecycle', status: 'PASS' });
  await writeFile(path.join(output, 'checks.json'), JSON.stringify(checks, null, 2));
} catch (error) {
  await writeFile(path.join(output, 'failure.txt'), error?.stack ?? String(error));
  try { await page?.screenshot({ path: path.join(output, 'failure-state.png'), fullPage: true }); } catch { }
  throw error;
} finally { await browser.close(); }
