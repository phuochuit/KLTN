import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { filterAccounts } from '../../main/resources/static/js/account-management-model.mjs';

const template = readFileSync(new URL('../../main/resources/templates/account-management.html', import.meta.url), 'utf8');
const liveTemplate = readFileSync(new URL('../../main/resources/templates/account-management-live.html', import.meta.url), 'utf8');
const shell = readFileSync(new URL('../../main/resources/templates/dashboard-shell.html', import.meta.url), 'utf8');
const script = readFileSync(new URL('../../main/resources/static/js/account-management.js', import.meta.url), 'utf8');
const liveScript = readFileSync(new URL('../../main/resources/static/js/account-management-live.mjs', import.meta.url), 'utf8');
const styles = readFileSync(new URL('../../main/frontend/styles/account-management.css', import.meta.url), 'utf8');
const build = readFileSync(new URL('../../main/frontend/build.mjs', import.meta.url), 'utf8');

const accounts = [
  { username: 'nv-an', searchText: 'NV-001 Nguyễn An nv-an an@example.invalid', enabled: true },
  { username: 'nv-binh', searchText: 'NV-002 Trần Bình nv-binh binh@example.invalid', enabled: false },
];

test('account search ignores Vietnamese accents and case while preserving loaded order', () => {
  assert.deepEqual(filterAccounts(accounts, 'NGUYEN'), [accounts[0]]);
  assert.deepEqual(filterAccounts(accounts, 'nv-'), accounts);
});

test('account status filter applies only to the locally loaded directory', () => {
  assert.deepEqual(filterAccounts(accounts, '', 'active'), [accounts[0]]);
  assert.deepEqual(filterAccounts(accounts, '', 'locked'), [accounts[1]]);
  assert.deepEqual(filterAccounts(accounts, 'example.invalid', 'locked'), [accounts[1]]);
  assert.deepEqual(filterAccounts(accounts, 'not-found'), []);
});

test('account mutations retain separate POST routes, CSRF and current form field names', () => {
  for (const route of ['create', 'profile', 'state', 'reset-password']) {
    assert.ok(template.includes(`th:action="@{/account-management/${route}}"`), `missing ${route} route`);
  }
  assert.ok(template.includes('id="app-dialog"'), 'account page must retain the shared confirmation dialog contract');
  assert.ok(template.includes('data-confirm='), 'mutating account actions must identify their confirmation copy');
  assert.ok(shell.includes("activePage == 'accounts' ? 'Bạn muốn đăng xuất khỏi hệ thống?'"));
  const directoryStart = template.indexOf('<section id="account-list"');
  const createDisclosureStart = template.indexOf('<details id="createAccountPanel"', directoryStart);
  const createDisclosureEnd = template.indexOf('</details>', template.indexOf('id="createAccountPanel"'));
  const directoryEnd = template.indexOf('</section>', createDisclosureEnd);
  assert.ok(directoryStart >= 0 && createDisclosureStart > directoryStart && createDisclosureEnd < directoryEnd,
    'native create disclosure must sit inside the directory panel, not wrap the directory');
  assert.ok(template.indexOf('<table class="account-table">') > createDisclosureEnd,
    'account rows must stay outside the collapsed create-account disclosure');
  assert.ok(template.includes('th:name="${_csrf.parameterName}" th:value="${_csrf.token}"'));
  for (const field of ['username', 'roleId', 'fullName', 'phone', 'email', 'citizenId', 'dateOfBirth', 'address', 'enabled']) {
    assert.ok(template.includes(`name="${field}"`), `missing ${field} form field`);
  }
  assert.ok(template.includes('<th:block th:if="${canManageAccounts}">'));
});

test('account rows prioritize the employee profile and keep security actions grouped', () => {
  assert.ok(template.includes('<caption class="sr-only">'), 'account table must have an accessible caption');
  assert.ok(template.includes('<th scope="row">'), 'employee identity must be a row header');
  assert.ok(template.includes('class="account-row-menu"'), 'secondary account actions must be grouped in a disclosure');
  assert.ok(template.includes('Tác vụ'), 'the grouped account actions need a visible label');
  assert.ok(template.includes('id="profile-display-name"'), 'profile editor must identify the selected employee');
  assert.ok(template.includes('id="profile-current-state"'), 'profile editor must show the current account state');
  assert.ok(script.includes('profile-current-state'), 'profile editor context must be populated from the selected row');
  assert.match(styles, /\.account-table\s+th:first-child[^}]*position:\s*sticky/,
    'employee identity must remain visible in the mobile table scroller');
  assert.match(styles, /\.account-management-page \.app-dialog \{[^}]*box-sizing:\s*border-box/,
    'the shared confirmation dialog must keep its sizing inside narrow viewports');
  assert.ok(template.includes('class="account-directory-summary"'), 'loaded-list counts should share the directory heading');
  assert.ok(template.includes('Cần đổi mật khẩu'), 'a required password change should remain explicit');
  assert.ok(!template.includes('Mật khẩu đã xác nhận'));
});

test('row security actions use a top-layer auto popover without changing their POST contracts', () => {
  const row = template.match(/<tr th:each="account, accountStat : \$\{accounts\}"[\s\S]*?<\/tr>/)?.[0];
  assert.ok(row, 'account row template must exist');
  assert.match(row, /<button type="button" class="account-row-menu-trigger"[^>]*aria-expanded="false"/,
    'the row disclosure should be a named native button with an initial collapsed state');
  assert.match(row, /popovertarget=\$\{'account-actions-' \+ accountStat\.index\}/,
    'each action trigger should be declaratively associated with its row popover');
  assert.match(row, /th:id="\$\{'account-actions-' \+ accountStat\.index\}"[^>]*popover="auto"/,
    'each action panel should be a unique auto popover');
  assert.match(row, /th:action="@\{\/account-management\/state\}"[\s\S]*?data-confirm=/,
    'lock/unlock must retain its confirmation and POST route');
  assert.match(row, /th:action="@\{\/account-management\/reset-password\}"[\s\S]*?data-confirm=/,
    'password reset must retain its confirmation and POST route');
  const actionForms = [...row.matchAll(/<form[\s\S]*?<\/form>/g)].map(([form]) => form);
  assert.equal(actionForms.length, 2, 'the disclosure should retain exactly the two existing action forms');
  assert.ok(actionForms.every(form => /th:name="\$\{_csrf\.parameterName\}" th:value="\$\{_csrf\.token\}"/.test(form)),
    'the existing CSRF field must remain in the row action forms');
  assert.match(styles, /\.account-row-menu-actions\[popover="auto"\][^{]*\{[^}]*position:\s*fixed/,
    'the popover must use viewport coordinates after entering the browser top layer');
  assert.match(styles, /\.account-row-menu-actions\[popover="auto"\]:popover-open\s*\{[^}]*display:\s*grid/,
    'the closed popover must remain hidden by native popover behavior');
  assert.match(script, /window\.addEventListener\('scroll',[^\n]*true/,
    'an open menu must be repositioned or dismissed during nested scrolling');
  assert.match(script, /window\.addEventListener\('resize'/,
    'an open menu must be repositioned after viewport resizing');
  assert.match(script, /popover\.addEventListener\('focusout'/,
    'keyboard Tab leaving the action popover should dismiss it without stealing the new focus');
  assert.doesNotMatch(row, /<details class="account-row-menu"|<summary/,
    'the in-flow disclosure panel must no longer expand the table row');
});

test('confirmed administration pending feedback stays viewport-visible and resets on history restore', () => {
  assert.match(script, /const adminActionPaths = \[[\s\S]*?'\/account-management\/shifts'[\s\S]*?\];/,
    'shift assignment must share the existing administration pending guard');
  assert.match(script, /if \(!adminSubmissionPending[\s\S]*?event\.stopImmediatePropagation\(\)/,
    'a repeated eligible submit must be stopped in capture before shared confirmation can reopen');
  assert.match(script, /window\.addEventListener\('pageshow'/,
    'history restoration must clear transient pending state');
  assert.match(script, /pendingControlState[\s\S]*?submitterDisabled[\s\S]*?removeAttribute\('disabled'\)/,
    'pageshow must restore the accepted control to its original server-rendered eligibility');
  assert.match(styles, /#admin-submit-status:not\(\[hidden\]\)[^{]*\{[^}]*position:\s*fixed/,
    'pending feedback must remain visible in the current viewport without a scroll jump');
  assert.match(styles, /#admin-submit-status\[hidden\]\s*\{\s*display:\s*none/,
    'the status must remain truly hidden before an accepted submission');
});

test('account directory separates compact employee identity from role, status and row actions', () => {
  assert.ok(template.includes('<th scope="col">Thao tác</th>'), 'row actions need a dedicated table column');
  const row = template.match(/<tr th:each="account, accountStat : \$\{accounts\}"[\s\S]*?<\/tr>/)?.[0];
  assert.ok(row, 'account row template must exist');
  assert.match(row, /<th scope="row">[\s\S]*class="account-person-meta"/,
    'employee identity should keep concise username/code metadata');
  assert.match(row, /class="account-role-cell"/, 'role should remain an explicit table value');
  assert.match(row, /class="account-state-cell"/, 'enabled/locked state should remain explicit');
  assert.match(row, /class="account-row-actions-cell"[\s\S]*class="account-button-primary edit-profile"/,
    'profile and security actions should be associated with their own row action cell');
  assert.match(row, /th:if="\$\{account\.mustChangePassword\}"[\s\S]*Cần đổi mật khẩu/,
    'only an actionable forced-password-change reminder should repeat in the directory');
  assert.doesNotMatch(row, /account-person-mark/, 'do not repeat a decorative, non-data avatar per row');
  assert.doesNotMatch(template, /Không yêu cầu đổi mật khẩu/);
  assert.match(template, /cuộn ngang[\s\S]*thao tác/i, 'mobile guidance should disclose where row actions are');
});

test('profile editor provides Lucide close, guarded cancel, and explicit unchanged/dirty/saving states', () => {
  assert.match(template, /id="close-profile"[^>]*aria-label="Đóng hồ sơ"[\s\S]*images\/lucide\/x\.svg/,
    'the close glyph should use the local Lucide asset inside a named button');
  assert.ok(template.includes('id="cancel-profile"'), 'the editor should offer a visible cancel action');
  assert.ok(template.includes('id="save-profile"'), 'save state must be targetable');
  assert.match(template, /id="save-profile"[^>]*disabled/, 'save starts unavailable until the form is dirty');
  assert.match(template, /id="profile-draft-state"[^>]*role="status"/,
    'draft state should be announced as status, not only shown visually');
  const mobileBreakpoint = styles.indexOf('@media (max-width: 47.999rem) {');
  const mobileBreakpointEnd = styles.indexOf('\n}', mobileBreakpoint);
  assert.ok(mobileBreakpoint >= 0 && mobileBreakpointEnd > mobileBreakpoint);
  assert.match(styles.slice(mobileBreakpoint, mobileBreakpointEnd),
    /\.account-profile-dialog \.account-form-actions\s*\{[^}]*position:\s*static;/,
    'mobile form actions must return to document flow so the sticky bar cannot cover fields');
  assert.match(styles, /\.account-management-page \.account-dialog-close\s*\{[^}]*width:\s*2\.75rem;[^}]*height:\s*2\.75rem/,
    'the close button should retain a 44px target');
  assert.match(styles, /\.account-management-page :is\([^{}]*account-dialog-close[^{}]*\):focus-visible/,
    'the close button should retain visible keyboard focus');
  assert.match(script, /Thông tin chưa thay đổi\./);
  assert.match(script, /Có thay đổi chưa lưu\./);
  assert.match(script, /Đang lưu thay đổi…/);
  assert.match(script, /profileForm\.setAttribute\('aria-busy', 'true'\)/);
  assert.match(script, /profileSaveButton\.disabled = !changed \|\| profileSubmitting/);
  assert.match(script, /if \(state\(\) === profileBaseline\) \{\s*event\.preventDefault\(\)/,
    'a no-change form submission must not post');
  assert.ok(script.includes("const cancelProfileButton = document.getElementById('cancel-profile')")
    && script.includes('cancelProfileButton.addEventListener(\'click\', requestClose)'),
    'Cancel must share the existing draft-guard path');
  assert.ok(script.includes("document.getElementById('close-profile').addEventListener('click', requestClose)"),
    'the Lucide close button must share the same draft-guard path');
  assert.match(build, /'x'\]\)/, 'the local Lucide build must include the close icon asset');
});

test('opening a profile editor closes any row action disclosure first', () => {
  const closeMenus = script.indexOf('closeAccountActionPopovers({ restoreFocus: false })');
  const openEditor = script.indexOf('profileDialog.showModal()');
  assert.ok(closeMenus >= 0 && closeMenus < openEditor,
    'an open row action popover must close before the profile dialog');
});

test('temporary password remains conditional and is not part of live polling or browser storage', () => {
  assert.ok(template.includes('th:if="${canManageAccounts && temporaryPassword != null}"'));
  assert.ok(template.includes('th:text="${temporaryPassword}"'));
  assert.ok(template.includes('th:replace="~{account-management-live :: sessions}"'));
  assert.ok(template.includes('th:replace="~{account-management-live :: shifts}"'));
  assert.ok(template.includes('th:src="@{/js/account-management-live.mjs}"'));
  assert.ok(liveScript.includes("fetch('/account-management/live'"));
  assert.ok(liveScript.includes('windowRef.setInterval(refreshLiveData, 3000)'));
  assert.doesNotMatch(liveScript, /localStorage|sessionStorage|console\.|innerHTML|temporaryPassword/);
});

test('session wording reports system-recorded activity rather than claiming the person is online', () => {
  assert.match(liveTemplate, /Phiên Desktop được ghi nhận/);
  assert.match(liveTemplate, /không khẳng định nhân viên đang có mặt trước thiết bị/);
  assert.match(liveTemplate, /Phiên Web được ghi nhận/);
  assert.match(liveTemplate, /không xác nhận người dùng đang xem trang/);
  assert.match(liveTemplate, /100 phiên Desktop mới tạo gần nhất/);
  assert.match(liveTemplate, /phiên Spring Session/);
  assert.doesNotMatch(liveTemplate, /Trực tuyến|badge-online/);
});

test('live tables remain semantic and expose named keyboard-reachable mobile scrollers', () => {
  for (const label of ['Phiên Desktop được ghi nhận', 'Phiên Web được ghi nhận', 'Danh sách 50 ca làm gần đây']) {
    assert.match(liveTemplate, new RegExp(`role="region" aria-label="${label}[^\"]*" tabindex="0"`));
  }
  assert.equal((liveTemplate.match(/<caption class="sr-only">/g) || []).length, 3);
  assert.match(liveTemplate, /live-scroll-hint[\s\S]*?cuộn ngang/);
  assert.match(liveTemplate, /Tối đa 50 ca|tối đa 50 ca/);
});

test('live snapshots defer both fragments and timestamp while a dialog or live-region focus is active', () => {
  assert.match(liveScript, /const liveIds = \['live-refresh-status', 'live-sessions', 'live-shifts'\]/);
  assert.match(liveScript, /documentRef\.querySelector\('dialog\[open\]'\)/);
  assert.match(liveScript, /region\?\.contains\(active\)/);
  assert.match(liveScript, /pendingSnapshot = snapshot/);
  assert.match(liveScript, /nodes\[0\]\.replaceWith\(snapshot\['live-refresh-status'\]\)[\s\S]*nodes\[1\]\.replaceWith\(snapshot\['live-sessions'\]\)[\s\S]*nodes\[2\]\.replaceWith\(snapshot\['live-shifts'\]\)/);
  assert.match(liveScript, /dialog\.addEventListener\('close', \(\) =>/);
  assert.match(liveScript, /pendingSnapshot = null/);
  assert.match(liveScript, /shouldAnnounceManual \? 'updated' : 'fresh'/);
  assert.match(liveScript, /pending: 'Đã nhận dữ liệu mới; sẽ hiển thị sau khi thao tác đang mở kết thúc\.'/);
  assert.match(liveScript, /const focus = pendingLiveActionFocus/);
  assert.match(liveScript, /applyPendingSnapshot\(Boolean\(returnedAction\)\)/);
  assert.match(liveTemplate, /data-live-action-form data-live-action-region="live-sessions"/);
  assert.match(liveTemplate, /data-live-action-form data-live-action-region="live-shifts"/);
});

test('live refresh classifies expired sessions, lost permissions, HTTP errors and network failures', () => {
  assert.match(liveScript, /response\.status === 401/);
  assert.match(liveScript, /terminalState = 'expired'/);
  assert.match(liveScript, /windowRef\.location\.assign\('\/login'\)/);
  assert.match(liveScript, /response\.status === 403/);
  assert.match(liveScript, /clearLiveData\(\)/);
  assert.match(liveScript, /shift-assignment-form'\)\?\.querySelectorAll\('button,input,select,textarea'\)/);
  assert.match(liveScript, /region\.replaceChildren\(\)/);
  assert.match(liveScript, /if \(!response\.ok\) \{\s*setRefreshState\('stale'\)/);
  assert.match(liveScript, /catch \(_\) \{\s*setRefreshState\('stale'\)/);
  assert.match(liveScript, /terminalState = 'forbidden'/);
  assert.match(liveScript, /if \(documentRef\.hidden \|\| terminalState\) return/);
  assert.match(liveScript, /documentRef\.addEventListener\('visibilitychange'/);
});

test('shift form uses explicit Vietnamese date-time fields with a native no-JS fallback and preserves POST/CSRF', () => {
  assert.match(template, /id="shift-assignment-form" class="shift-form" method="post" th:action="@\{\/account-management\/shifts\}"/);
  assert.match(template, /type="text" id="starts-at-display"[^>]*placeholder="dd\/MM\/yyyy HH:mm"[^>]*disabled/);
  assert.match(template, /type="text" id="ends-at-display"[^>]*placeholder="dd\/MM\/yyyy HH:mm"[^>]*disabled/);
  assert.match(template, /type="hidden" name="startsAt" disabled/);
  assert.match(template, /type="hidden" name="endsAt" disabled/);
  assert.match(template, /<noscript><style>[^<]*shift-date-time-input/);
  assert.match(template, /<noscript><input type="datetime-local" name="startsAt"[^>]*required><\/noscript>/);
  assert.match(template, /<noscript><input type="datetime-local" name="endsAt"[^>]*required><\/noscript>/);
  assert.match(template, /id="shift-start-time-error" class="shift-time-error" role="status" aria-live="polite" hidden/);
  assert.match(template, /id="shift-end-time-error" class="shift-time-error" role="status" aria-live="polite" hidden/);
  assert.match(liveScript, /parseVietnamShiftDateTime/);
  assert.match(template, /dd\/MM\/yyyy HH:mm[\s\S]*31\/10\/2026 13:45[\s\S]*24 giờ/);
  assert.match(template, /giờ Việt Nam \(GMT\+7\)/);
  assert.match(template, /Điều kiện phân công và trùng lịch sẽ được hệ thống kiểm tra khi lưu/);
  assert.match(template, /th:name="\$\{_csrf\.parameterName\}" th:value="\$\{_csrf\.token\}"[\s\S]*name="username"/);
});

test('shift headings use non-interactive Lucide time icons instead of arrow affordances', () => {
  assert.doesNotMatch(`${template}\n${liveTemplate}`, /↗/);
  assert.match(template, /class="shift-section-icon"[^>]*clock-3\.svg[^>]*alt=""[^>]*aria-hidden="true"/);
  assert.match(liveTemplate, /class="shift-section-icon"[^>]*clock-3\.svg[^>]*alt=""[^>]*aria-hidden="true"/);
  assert.match(styles, /\.account-management-page \.shift-section-title\s*\{[^}]*display:\s*flex/);
  assert.match(styles, /\.account-management-page \.shift-section-icon\s*\{[^}]*pointer-events:\s*none/);
  assert.doesNotMatch(styles, /\.live-channel-mark-shift\s*\{/);
});

test('account controls have deliberate pressed states and progressive account-only select styling', () => {
  assert.match(styles, /\.account-management-page :is\([^{}]*account-button-primary[^{}]*\):active:not\(:disabled\)/);
  assert.match(styles, /\.account-management-page \.account-button-primary:disabled,[^}]*box-shadow:\s*none/);
  assert.match(styles, /\.account-management-page[^{}]*select::picker\(select\)/);
  assert.match(styles, /option:checked/);
  assert.match(styles, /@supports\s*\(appearance:\s*base-select\)/);
  assert.match(styles, /select:focus-visible\s*\{\s*appearance:\s*auto;/);
  assert.match(styles, /select:open/);
  assert.doesNotMatch(styles, /\.account-management-page :is\([^{}]*shift-form[^{}]*\) :is\(input,select,textarea\)/,
    'FE1 control sizing must not change the separately scoped FE2 shift form');
  assert.match(styles, /\.account-management-page \.shift-form input,\.account-management-page \.shift-form select,\.account-management-page \.shift-form textarea\s*\{[^}]*min-width:\s*0/,
    'the existing FE2 shift-form control sizing remains intact');
  assert.match(styles, /\.account-management-page \.management-content button\.account-button-primary:disabled\s*\{[^}]*box-shadow:\s*none/,
    'the high-specificity primary-button rule must not leave a shadow on its disabled state');
});
