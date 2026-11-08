const $ = selector => document.querySelector(selector);
const navigation = $('#navigationDialog');
const slotDialog = $('#slotDialog');
const batchDialog = $('#batchDialog');
const assignVehicleSelect = $('#assignVehicleSelect');
const assignVehicleButton = $('#assignVehicleButton');

assignVehicleSelect.addEventListener('change', () => {
  assignVehicleButton.disabled = !assignVehicleSelect.value;
});

$('#openNavigation')?.addEventListener('click', () => {
  navigation.showModal();
  navigation.querySelector('.close-navigation').focus();
});
$('#closeNavigation')?.addEventListener('click', () => navigation.close());
document.querySelectorAll('.dashboard-logout').forEach(form => form.addEventListener('submit', async event => {
  event.preventDefault();
  const question = 'Bạn muốn đăng xuất khỏi hệ thống?';
  const accepted = window.ParkingUi ? await window.ParkingUi.confirm(question, 'Đăng xuất') : window.confirm(question);
  if (accepted) form.submit();
}));

const singleSlotSection = $('#singleSlotSection');
function toggleSingleSlot(open) {
  singleSlotSection.hidden = !open;
  if (open) $('#singleSlotSection input[name="slotCode"]').focus();
  else $('#toggleSingleSlot').focus();
}
$('#toggleSingleSlot').addEventListener('click', () => toggleSingleSlot(singleSlotSection.hidden));
$('#closeSingleSlot').addEventListener('click', () => toggleSingleSlot(false));
$('#cancelSingleSlot').addEventListener('click', () => toggleSingleSlot(false));
$('#emptyCreateSlot')?.addEventListener('click', () => {
  toggleSingleSlot(true);
  singleSlotSection.scrollIntoView({ block: 'center' });
});

$('#openBatchDialog').addEventListener('click', () => batchDialog.showModal());
document.querySelectorAll('[data-close-batch]').forEach(button => button.addEventListener('click', () => batchDialog.close()));
batchDialog.addEventListener('click', event => {
  if (event.target === batchDialog) batchDialog.close();
});

const tabs = [...document.querySelectorAll('[role="tab"][aria-controls]')];
function activateTab(tab, moveFocus = false) {
  tabs.forEach(item => {
    const selected = item === tab;
    item.setAttribute('aria-selected', String(selected));
    item.tabIndex = selected ? 0 : -1;
    $(`#${item.getAttribute('aria-controls')}`).hidden = !selected;
  });
  if (moveFocus) tab.focus();
}
tabs.forEach((tab, index) => {
  tab.addEventListener('click', () => activateTab(tab));
  tab.addEventListener('keydown', event => {
    let targetIndex;
    if (event.key === 'ArrowRight') targetIndex = (index + 1) % tabs.length;
    else if (event.key === 'ArrowLeft') targetIndex = (index - 1 + tabs.length) % tabs.length;
    else if (event.key === 'Home') targetIndex = 0;
    else if (event.key === 'End') targetIndex = tabs.length - 1;
    else return;
    event.preventDefault();
    activateTab(tabs[targetIndex], true);
  });
});

const text = (selector, value) => { $(selector).textContent = value || ''; };
const show = (selector, visible) => { $(selector).hidden = !visible; };
const closeReleaseConfirmation = () => {
  show('#releaseConfirmation', false);
  $('#showReleaseConfirm').focus();
};
$('#showReleaseConfirm').addEventListener('click', () => {
  show('#releaseConfirmation', true);
  $('#cancelRelease').focus();
});
$('#cancelRelease').addEventListener('click', closeReleaseConfirmation);

const pendingSlotForms = [];
document.querySelectorAll('form[method="post"]').forEach(form => {
  if (!/(^|\/)slots(?:\/|$)/.test(new URL(form.action, window.location.href).pathname)) return;

  const pending = { form, active: false };
  pendingSlotForms.push(pending);
  form.addEventListener('submit', event => {
    if (pending.active) {
      event.preventDefault();
      return;
    }

    const submitter = event.submitter || form.querySelector('button[type="submit"]');
    if (!submitter) return;

    pending.active = true;
    pending.submitter = submitter;
    pending.originalText = submitter.textContent;
    pending.originalMinWidth = submitter.style.minWidth;
    pending.originalAriaDisabled = submitter.getAttribute('aria-disabled');
    pending.originalAriaBusy = form.getAttribute('aria-busy');
    submitter.style.minWidth = `${submitter.getBoundingClientRect().width}px`;
    form.setAttribute('aria-busy', 'true');
    submitter.setAttribute('aria-disabled', 'true');
    submitter.textContent = 'Đang gửi…';
  });
});

window.addEventListener('pageshow', () => {
  pendingSlotForms.forEach(pending => {
    if (!pending.active) return;
    pending.active = false;
    if (pending.originalAriaBusy === null) pending.form.removeAttribute('aria-busy');
    else pending.form.setAttribute('aria-busy', pending.originalAriaBusy);
    pending.submitter.style.minWidth = pending.originalMinWidth;
    if (pending.originalAriaDisabled === null) pending.submitter.removeAttribute('aria-disabled');
    else pending.submitter.setAttribute('aria-disabled', pending.originalAriaDisabled);
    pending.submitter.textContent = pending.originalText;
  });
});

function openSlot(tile) {
  const data = tile.dataset;
  text('#modalSlotTitle', `Ô đỗ ${data.code}`);
  text('#modalLocationBadge', `${data.floor} · ${data.zone} · ${data.vehicleType === 'MOTORBIKE' ? 'Xe hai bánh' : 'Ô tô'}`);
  const status = $('#modalStatusBadge');
  status.textContent = data.statusDesc || data.status;
  status.className = `slot-status-badge is-${(data.status || '').toLowerCase().replaceAll('_', '-')}`;

  const assigned = data.assignedPlate
    ? `${data.assignedPlate} · ${data.assignedOwner || 'Chưa có tên chủ xe'} · Căn ${data.assignedApt || 'chưa có'}`
      + (data.assignedPhone ? ` · ${data.assignedPhone}` : '')
    : 'Chưa cấp cho cư dân';
  const occupied = data.occupiedPlate
    ? `${data.occupiedPlate}${data.entryTime ? ` · Vào lúc ${data.entryTime.replace('T', ' ')}` : ''}`
    : 'Hiện không có xe đang đỗ';
  const borrowed = data.borrowedPlate
    ? `${data.borrowedPlate}${data.borrowedUntil ? ` · Hạn ${data.borrowedUntil.replace('T', ' ')}` : ''}`
      + (data.overdue === 'true' ? ' · Đã quá giờ' : '')
      + (data.borrowNotes ? ` · Ghi chú: ${data.borrowNotes}` : '')
    : 'Không có thiết lập đỗ nhờ';
  text('#infoAssigned', assigned);
  text('#infoOccupied', occupied);
  text('#infoBorrowed', borrowed);

  for (const id of ['assignSlotId', 'borrowSlotId', 'cancelBorrowSlotId', 'dispatchSlotId', 'statusSlotId', 'releaseSlotId']) {
    $(`#${id}`).value = data.id;
  }
  $('#unassignSlotId').value = data.id;
  assignVehicleSelect.value = '';
  assignVehicleButton.disabled = true;
  show('#unassignSlotForm', Boolean(data.assignedPlate));
  $('#borrowedPlateInput').value = data.borrowedPlate || '';
  $('#borrowNotesInput').value = data.borrowNotes || '';
  show('#cancelBorrowForm', Boolean(data.borrowedPlate));
  $('#statusOverrideVal').value = data.statusOverride === 'BLOCKED' ? 'NORMAL' : 'BLOCKED';
  text('#btnToggleBlock', data.statusOverride === 'BLOCKED'
    ? 'Mở lại ô · bỏ trạng thái cấm đỗ'
    : 'Đặt ô vào trạng thái cấm đỗ / bảo trì');
  activateTab(tabs[0]);
  show('#releaseConfirmation', false);
  slotDialog.showModal();
}

document.querySelectorAll('.slot-tile').forEach(tile => tile.addEventListener('click', () => openSlot(tile)));
document.querySelectorAll('[data-close-slot]').forEach(button => button.addEventListener('click', () => slotDialog.close()));
slotDialog.addEventListener('click', event => {
  if (event.target === slotDialog) slotDialog.close();
});
