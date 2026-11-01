export function shiftIntervalError(startsAt, endsAt) {
  if (!startsAt || !endsAt) return null;

  const startsAtMillis = Date.parse(`${startsAt}:00+07:00`);
  const endsAtMillis = Date.parse(`${endsAt}:00+07:00`);
  if (!Number.isFinite(startsAtMillis) || !Number.isFinite(endsAtMillis)) return null;
  if (startsAtMillis >= endsAtMillis) return 'Thời gian kết thúc phải sau thời gian bắt đầu.';
  if (endsAtMillis - startsAtMillis > 24 * 60 * 60 * 1000)
    return 'Một ca làm không được dài quá 24 giờ.';
  return null;
}

export function parseVietnamShiftDateTime(value) {
  if (typeof value !== 'string') return null;
  const match = /^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2})$/.exec(value.trim());
  if (!match) return null;

  const [, dayText, monthText, yearText, hourText, minuteText] = match;
  const day = Number(dayText);
  const month = Number(monthText);
  const year = Number(yearText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  if (year < 1 || month < 1 || month > 12 || hour > 23 || minute > 59) return null;

  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (day < 1 || day > daysInMonth[month - 1]) return null;

  return `${yearText}-${monthText}-${dayText}T${hourText}:${minuteText}`;
}

export function installAccountManagementLive({ documentRef = document, windowRef = window } = {}) {
  const initialStatus = documentRef.getElementById('live-refresh-status');
  if (!initialStatus) return null;

  let refreshing = false;
  let manualRefreshRequested = false;
  let pendingManualRefresh = false;
  let pendingSnapshot = null;
  let terminalState = null;
  let pendingLiveActionFocus = null;

  const liveIds = ['live-refresh-status', 'live-sessions', 'live-shifts'];
  const getLiveNodes = () => liveIds.map(id => documentRef.getElementById(id));

  function setRefreshState(state) {
    const status = documentRef.getElementById('live-refresh-status');
    if (!status) return;
    const message = status.querySelector('#live-refresh-message');
    const button = status.querySelector('#live-refresh-retry');
    const snapshotTime = status.querySelector('#live-snapshot-time');
    status.dataset.state = state;

    const messages = {
      fresh: 'Tự động kiểm tra mỗi 3 giây. Thời điểm trên thuộc dữ liệu đang hiển thị.',
      checking: 'Đang kiểm tra phiên và ca làm…',
      updated: `Đã cập nhật lúc ${snapshotTime?.textContent || 'vừa xong'}.`,
      pending: 'Đã nhận dữ liệu mới; sẽ hiển thị sau khi thao tác đang mở kết thúc.',
      stale: `Không thể cập nhật. Đang hiển thị dữ liệu lúc ${snapshotTime?.textContent || 'gần nhất'}.`,
      expired: 'Phiên đăng nhập đã hết hạn. Đang chuyển tới trang đăng nhập…',
      forbidden: 'Bạn không còn quyền xem dữ liệu phiên và ca. Hãy tải lại trang để kiểm tra quyền.',
    };
    if (message) message.textContent = messages[state] || messages.fresh;
    if (!button) return;
    button.textContent = state === 'forbidden' ? 'Tải lại trang'
      : state === 'stale' ? 'Thử lại'
        : state === 'checking' ? 'Đang cập nhật…' : 'Cập nhật ngay';
    button.disabled = state === 'checking' || state === 'expired';
    if (state === 'checking') button.setAttribute('aria-busy', 'true');
    else button.removeAttribute('aria-busy');
  }

  function isInteractionActive() {
    if (documentRef.querySelector('dialog[open]')) return true;
    const active = documentRef.activeElement;
    return getLiveNodes().some(region => region?.contains(active));
  }

  function focusLiveAction(snapshot, regionId, actionId) {
    if (!actionId) return;
    const updatedRegion = snapshot[regionId];
    const action = [...updatedRegion.querySelectorAll('[data-live-action]')]
      .find(element => element.dataset.liveActionId === actionId);
    if (action) {
      action.focus();
      return;
    }
    documentRef.querySelector(`#${regionId} h2`)?.focus();
  }

  function applyPendingSnapshot(allowReturnedActionFocus = false) {
    if (!pendingSnapshot || terminalState || (isInteractionActive() && !allowReturnedActionFocus)) return;
    const snapshot = pendingSnapshot;
    const shouldAnnounceManual = pendingManualRefresh;
    const nodes = getLiveNodes();
    if (nodes.some(node => !node)) return;

    nodes[0].replaceWith(snapshot['live-refresh-status']);
    nodes[1].replaceWith(snapshot['live-sessions']);
    nodes[2].replaceWith(snapshot['live-shifts']);
    pendingSnapshot = null;
    pendingManualRefresh = false;

    const focus = pendingLiveActionFocus;
    pendingLiveActionFocus = null;
    if (focus) focusLiveAction(snapshot, focus.regionId, focus.actionId);
    setRefreshState(shouldAnnounceManual ? 'updated' : 'fresh');
  }

  function onFocusChange() {
    windowRef.setTimeout(applyPendingSnapshot, 0);
  }

  documentRef.addEventListener('focusout', onFocusChange, true);
  documentRef.querySelectorAll('dialog').forEach(dialog => dialog.addEventListener('close', () => {
    if (documentRef.querySelector('dialog[open]')) return;
    const active = documentRef.activeElement;
    const liveRegion = getLiveNodes().find(region => region?.contains(active));
    const returnedAction = liveRegion && active?.matches('[data-live-action]')
      && pendingLiveActionFocus?.regionId === liveRegion.id
      && pendingLiveActionFocus.actionId === active.dataset.liveActionId;
    applyPendingSnapshot(Boolean(returnedAction));
  }));
  documentRef.addEventListener('submit', event => {
    const form = event.target.closest('[data-live-action-form]');
    if (!form) return;
    pendingLiveActionFocus = {
      regionId: form.dataset.liveActionRegion,
      actionId: event.submitter?.dataset.liveActionId || form.dataset.liveActionId,
    };
  }, true);

  function clearLiveData() {
    for (const regionId of ['live-sessions', 'live-shifts']) {
      const region = documentRef.getElementById(regionId);
      if (!region) continue;
      region.replaceChildren();
    }
    documentRef.getElementById('shift-assignment-form')?.querySelectorAll('button,input,select,textarea')
      .forEach(control => { control.disabled = true; });
    pendingSnapshot = null;
    pendingManualRefresh = false;
    terminalState = 'forbidden';
    setRefreshState('forbidden');
  }

  async function refreshLiveData(manual = false) {
    if (documentRef.hidden || terminalState) return;
    if (refreshing) {
      if (manual) {
        manualRefreshRequested = true;
        setRefreshState('checking');
      }
      return;
    }

    refreshing = true;
    manualRefreshRequested = manual;
    if (manual) setRefreshState('checking');
    try {
      const response = await windowRef.fetch('/account-management/live', {
        credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' },
      });
      const responseUrl = new URL(response.url || windowRef.location.href, windowRef.location.href);
      if ((response.redirected && responseUrl.pathname === '/login') || response.status === 401) {
        terminalState = 'expired';
        setRefreshState('expired');
        windowRef.location.assign('/login');
        return;
      }
      if (response.status === 403) {
        clearLiveData();
        return;
      }
      if (!response.ok) {
        setRefreshState('stale');
        return;
      }

      const responseDocument = new windowRef.DOMParser().parseFromString(await response.text(), 'text/html');
      const snapshot = Object.fromEntries(liveIds.map(id => [id, responseDocument.getElementById(id)]));
      if (Object.values(snapshot).some(node => !node)) {
        setRefreshState('stale');
        return;
      }

      pendingSnapshot = snapshot;
      pendingManualRefresh = pendingManualRefresh || manualRefreshRequested;
      if (isInteractionActive()) setRefreshState('pending');
      else applyPendingSnapshot();
    } catch (_) {
      setRefreshState('stale');
    } finally {
      refreshing = false;
      manualRefreshRequested = false;
      if (documentRef.getElementById('live-refresh-status')?.dataset.state === 'checking')
        setRefreshState('fresh');
    }
  }

  documentRef.addEventListener('click', event => {
    if (!event.target.closest('#live-refresh-retry')) return;
    if (terminalState === 'forbidden') {
      windowRef.location.reload();
      return;
    }
    if (terminalState === 'expired') {
      windowRef.location.assign('/login');
      return;
    }
    refreshLiveData(true);
  });
  windowRef.setInterval(refreshLiveData, 3000);
  documentRef.addEventListener('visibilitychange', () => {
    if (!documentRef.hidden) refreshLiveData();
  });

  return { refresh: refreshLiveData, applyPending: applyPendingSnapshot };
}

function installShiftTimeValidation(documentRef = document) {
  const form = documentRef.getElementById('shift-assignment-form');
  const startsAtDisplay = form?.querySelector('#starts-at-display');
  const endsAtDisplay = form?.querySelector('#ends-at-display');
  const startsAt = form?.elements.startsAt;
  const endsAt = form?.elements.endsAt;
  const startsAtMessage = documentRef.getElementById('shift-start-time-error');
  const endsAtMessage = documentRef.getElementById('shift-end-time-error');
  if (!form || !startsAtDisplay || !endsAtDisplay || !startsAt || !endsAt || !startsAtMessage || !endsAtMessage) return;

  startsAtDisplay.disabled = false;
  startsAtDisplay.required = true;
  endsAtDisplay.disabled = false;
  endsAtDisplay.required = true;
  startsAt.disabled = false;
  endsAt.disabled = false;
  const formatError = 'Nhập theo dd/MM/yyyy HH:mm (24 giờ), ví dụ 31/10/2026 13:45.';

  function updateValidity() {
    const startsAtValue = parseVietnamShiftDateTime(startsAtDisplay.value);
    const endsAtValue = parseVietnamShiftDateTime(endsAtDisplay.value);
    const startsAtError = startsAtDisplay.value.trim() && !startsAtValue ? formatError : '';
    let endsAtError = endsAtDisplay.value.trim() && !endsAtValue ? formatError : '';
    if (startsAtValue && endsAtValue) endsAtError = shiftIntervalError(startsAtValue, endsAtValue) || '';

    startsAt.value = startsAtValue || '';
    endsAt.value = endsAtValue || '';
    startsAtDisplay.setCustomValidity(startsAtError);
    endsAtDisplay.setCustomValidity(endsAtError);
    if (startsAtError) startsAtDisplay.setAttribute('aria-invalid', 'true');
    else startsAtDisplay.removeAttribute('aria-invalid');
    if (endsAtError) endsAtDisplay.setAttribute('aria-invalid', 'true');
    else endsAtDisplay.removeAttribute('aria-invalid');
    startsAtMessage.hidden = !startsAtError;
    startsAtMessage.textContent = startsAtError;
    endsAtMessage.hidden = !endsAtError;
    endsAtMessage.textContent = endsAtError;
    return startsAtError || endsAtError;
  }

  startsAtDisplay.addEventListener('input', updateValidity);
  startsAtDisplay.addEventListener('change', updateValidity);
  endsAtDisplay.addEventListener('input', updateValidity);
  endsAtDisplay.addEventListener('change', updateValidity);
  form.addEventListener('submit', event => {
    const hasBothValues = startsAtDisplay.value.trim() && endsAtDisplay.value.trim();
    const error = updateValidity();
    if (!hasBothValues || error) {
      event.preventDefault();
      (startsAtDisplay.validity.valid ? endsAtDisplay : startsAtDisplay).reportValidity();
    }
  });
  updateValidity();
}

if (typeof document !== 'undefined') {
  installAccountManagementLive();
  installShiftTimeValidation();
}
