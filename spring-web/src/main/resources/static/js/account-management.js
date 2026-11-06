import { filterAccounts } from './account-management-model.mjs';

const navigation = document.getElementById('navigationDialog');
document.getElementById('openNavigation')?.addEventListener('click', () => navigation?.showModal());
document.getElementById('closeNavigation')?.addEventListener('click', () => navigation?.close());

const createPanel = document.getElementById('createAccountPanel');
document.getElementById('openCreateAccount')?.addEventListener('click', () => {
  if (!createPanel) return;
  createPanel.open = true;
  createPanel.querySelector('input[name="username"]')?.focus();
});

const rows = [...document.querySelectorAll('[data-account-row]')];
const search = document.getElementById('account-search');
const statusFilter = document.getElementById('account-status-filter');
const emptyResults = document.getElementById('account-no-results');
const result = document.getElementById('account-result');
if (search && statusFilter && emptyResults && result) {
  const accounts = rows.map(row => ({ row, enabled: row.dataset.enabled, searchText: row.textContent }));
  function renderAccounts() {
    const matches = new Set(filterAccounts(accounts, search.value, statusFilter.value).map(account => account.row));
    rows.forEach(row => { row.hidden = !matches.has(row); });
    emptyResults.hidden = matches.size !== 0 || rows.length === 0;
    result.textContent = matches.size
      ? `${matches.size} trong ${rows.length} tài khoản đã tải`
      : rows.length ? 'Không tìm thấy kết quả trong danh sách đã tải.' : 'Chưa có tài khoản.';
  }
  search.addEventListener('input', renderAccounts);
  statusFilter.addEventListener('change', renderAccounts);
  renderAccounts();

  document.getElementById('accountActiveCount').textContent = String(accounts.filter(account => account.enabled === 'true').length);
  document.getElementById('accountLockedCount').textContent = String(accounts.filter(account => account.enabled !== 'true').length);
}

const accountActionPopovers = [...document.querySelectorAll('.account-row-menu-actions[popover="auto"]')];
const accountActionTriggers = new Map(accountActionPopovers.map(popover => [
  popover,
  popover.parentElement.querySelector('.account-row-menu-trigger'),
]));

function positionAccountActionPopover(popover, trigger) {
  if (!popover.matches(':popover-open')) return;
  const anchor = trigger.getBoundingClientRect();
  const viewportWidth = document.documentElement.clientWidth;
  const viewportHeight = window.innerHeight;
  if (anchor.bottom <= 0 || anchor.top >= viewportHeight || anchor.right <= 0 || anchor.left >= viewportWidth) {
    popover.hidePopover();
    return;
  }

  const popup = popover.getBoundingClientRect();
  const gutter = 8;
  const left = Math.max(gutter, Math.min(anchor.right - popup.width, viewportWidth - popup.width - gutter));
  const spaceBelow = viewportHeight - anchor.bottom - gutter;
  const spaceAbove = anchor.top - gutter;
  const top = popup.height > spaceBelow && spaceAbove > spaceBelow
    ? anchor.top - popup.height - gutter
    : anchor.bottom + gutter;
  popover.style.left = `${left}px`;
  popover.style.top = `${Math.max(gutter, Math.min(top, viewportHeight - popup.height - gutter))}px`;
}

function closeAccountActionPopovers({ restoreFocus = false } = {}) {
  accountActionPopovers.forEach(popover => {
    if (!popover.matches(':popover-open')) return;
    const trigger = accountActionTriggers.get(popover);
    popover.hidePopover();
    if (restoreFocus && trigger?.isConnected) trigger.focus();
  });
}

accountActionPopovers.forEach(popover => {
  const trigger = accountActionTriggers.get(popover);
  if (!trigger) return;
  popover.addEventListener('toggle', event => {
    const expanded = event.newState === 'open';
    trigger.setAttribute('aria-expanded', String(expanded));
    if (expanded) positionAccountActionPopover(popover, trigger);
  });
  popover.addEventListener('focusout', () => {
    window.setTimeout(() => {
      if (popover.matches(':popover-open') && !popover.contains(document.activeElement)) popover.hidePopover();
    }, 0);
  });
  popover.addEventListener('submit', () => closeAccountActionPopovers({ restoreFocus: true }));
});

const adminSubmitStatus = document.getElementById('admin-submit-status');
let adminSubmissionPending = false;
let pendingControlState = null;
const adminActionPaths = ['/account-management/state', '/account-management/reset-password',
  '/account-management/sessions/desktop/revoke', '/account-management/sessions/web/revoke',
  '/account-management/shifts', '/account-management/shifts/delete'];
document.addEventListener('submit', event => {
  if (!adminSubmissionPending || !(event.target instanceof HTMLFormElement)) return;
  const path = new URL(event.target.action, window.location.href).pathname;
  if (!adminActionPaths.includes(path)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
}, true);
document.addEventListener('submit', event => {
  const form = event.target.closest('form');
  if (!form || event.defaultPrevented || !adminSubmitStatus) return;
  const path = new URL(form.action, window.location.href).pathname;
  if (!adminActionPaths.includes(path)) return;

  if (adminSubmissionPending) {
    event.preventDefault();
    return;
  }

  adminSubmissionPending = true;
  pendingControlState = {
    form,
    formBusy: form.getAttribute('aria-busy'),
    submitter: event.submitter,
    submitterDisabled: event.submitter?.getAttribute('disabled') ?? null,
    submitterBusy: event.submitter?.getAttribute('aria-busy') ?? null,
  };
  form.setAttribute('aria-busy', 'true');
  if (event.submitter) {
    event.submitter.disabled = true;
    event.submitter.setAttribute('aria-busy', 'true');
  }
  adminSubmitStatus.hidden = false;
  adminSubmitStatus.textContent = 'Đang gửi thao tác quản trị. Không gửi lại; hãy chờ phản hồi.';
});

window.addEventListener('pageshow', () => {
  if (!adminSubmissionPending) return;
  const state = pendingControlState;
  adminSubmissionPending = false;
  pendingControlState = null;
  if (state?.form?.isConnected) {
    if (state.formBusy === null) state.form.removeAttribute('aria-busy');
    else state.form.setAttribute('aria-busy', state.formBusy);
  }
  if (state?.submitter?.isConnected) {
    if (state.submitterDisabled === null) state.submitter.removeAttribute('disabled');
    else state.submitter.setAttribute('disabled', state.submitterDisabled);
    if (state.submitterBusy === null) state.submitter.removeAttribute('aria-busy');
    else state.submitter.setAttribute('aria-busy', state.submitterBusy);
  }
  if (adminSubmitStatus) {
    adminSubmitStatus.hidden = true;
    adminSubmitStatus.textContent = '';
  }
});

function repositionAccountActionPopovers() {
  accountActionPopovers.forEach(popover => {
    if (popover.matches(':popover-open'))
      positionAccountActionPopover(popover, accountActionTriggers.get(popover));
  });
}

window.addEventListener('scroll', repositionAccountActionPopovers, true);
window.addEventListener('resize', repositionAccountActionPopovers);
window.visualViewport?.addEventListener('resize', repositionAccountActionPopovers);

const profileDialog = document.getElementById('profile-dialog');
const profileForm = document.getElementById('profile-form');
const discardPrompt = document.getElementById('profile-discard-prompt');
const profileDraftState = document.getElementById('profile-draft-state');
const profileSaveButton = document.getElementById('save-profile');
const cancelProfileButton = document.getElementById('cancel-profile');
const keepDraft = document.getElementById('keep-profile-draft');
let profileBaseline = '';
let profileTrigger = null;
let profileSubmitting = false;

if (profileDialog && profileForm && discardPrompt && profileDraftState && profileSaveButton && cancelProfileButton && keepDraft) {
  const profileFields = ['username', 'fullName', 'phone', 'email', 'citizenId', 'dateOfBirth', 'address', 'roleId'];
  const state = () => JSON.stringify([...profileForm.elements]
    .filter(field => field.name && field.type !== 'hidden')
    .map(field => [field.name, field.value]));
  function updateProfileDraft() {
    const changed = state() !== profileBaseline;
    profileDraftState.textContent = profileSubmitting
      ? 'Đang lưu thay đổi…'
      : changed ? 'Có thay đổi chưa lưu.' : 'Thông tin chưa thay đổi.';
    profileDraftState.classList.toggle('is-dirty', changed && !profileSubmitting);
    profileDraftState.classList.toggle('is-saving', profileSubmitting);
    profileSaveButton.disabled = !changed || profileSubmitting;
  }

  function closeProfile() {
    discardPrompt.hidden = true;
    profileDialog.close();
  }

  function requestClose() {
    if (state() !== profileBaseline) {
      discardPrompt.hidden = false;
      document.getElementById('keep-profile-draft').focus();
      return;
    }
    closeProfile();
  }

  document.querySelectorAll('.edit-profile').forEach(button => button.addEventListener('click', () => {
    closeAccountActionPopovers({ restoreFocus: false });
    profileTrigger = button;
    profileSubmitting = false;
    profileForm.removeAttribute('aria-busy');
    profileSaveButton.removeAttribute('aria-busy');
    profileSaveButton.textContent = 'Lưu thay đổi';
    const row = button.closest('[data-account-row]');
    const enabled = row?.dataset.enabled === 'true';
    for (const field of profileFields) profileForm.elements[field].value = button.dataset[field] || '';
    document.getElementById('profile-display-name').textContent = button.dataset.fullName || button.dataset.username || 'Nhân viên';
    document.getElementById('profile-identity-detail').textContent = `Mã nhân viên: ${button.dataset.employeeCode || 'Chưa cấp'} · Tài khoản: ${button.dataset.username || ''} · Vai trò: ${button.dataset.roleDisplayName || 'Chưa phân vai trò'}`;
    const currentState = document.getElementById('profile-current-state');
    currentState.textContent = enabled ? 'Đang hoạt động' : 'Đã khóa';
    currentState.classList.toggle('is-enabled', enabled);
    currentState.classList.toggle('is-disabled', !enabled);
    profileBaseline = state();
    discardPrompt.hidden = true;
    updateProfileDraft();
    profileDialog.showModal();
    profileForm.elements.fullName.focus();
  }));

  document.getElementById('close-profile').addEventListener('click', requestClose);
  cancelProfileButton.addEventListener('click', requestClose);
  keepDraft.addEventListener('click', () => {
    discardPrompt.hidden = true;
    profileForm.elements.fullName.focus();
  });
  document.getElementById('discard-profile-draft').addEventListener('click', closeProfile);
  profileForm.addEventListener('input', updateProfileDraft);
  profileForm.addEventListener('change', updateProfileDraft);
  profileDialog.addEventListener('cancel', event => { event.preventDefault(); requestClose(); });
  profileDialog.addEventListener('click', event => { if (event.target === profileDialog) requestClose(); });
  profileDialog.addEventListener('close', () => {
    if (profileTrigger?.isConnected) profileTrigger.focus();
    profileTrigger = null;
  });
  profileForm.addEventListener('submit', event => {
    if (state() === profileBaseline) {
      event.preventDefault();
      return;
    }
    profileSubmitting = true;
    profileDraftState.textContent = 'Đang lưu thay đổi…';
    profileDraftState.classList.remove('is-dirty');
    profileDraftState.classList.add('is-saving');
    profileForm.setAttribute('aria-busy', 'true');
    profileSaveButton.disabled = true;
    profileSaveButton.setAttribute('aria-busy', 'true');
    profileSaveButton.textContent = 'Đang lưu…';
  });
  window.addEventListener('beforeunload', event => {
    if (profileDialog.open && state() !== profileBaseline && !profileSubmitting) {
      event.preventDefault();
      event.returnValue = '';
    }
  });
}

document.querySelectorAll('#create-account-form').forEach(form => {
  form.addEventListener('submit', () => {
    const button = form.querySelector('button[type="submit"]');
    if (!button) return;
    button.disabled = true;
    button.textContent = 'Đang tạo…';
  });
});
