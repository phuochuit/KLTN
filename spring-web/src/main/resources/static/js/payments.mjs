import { matchesPaymentFilter, classifyPaymentResponse } from './payments-model.mjs';

const rows = [...document.querySelectorAll('.payment-row')];
const forms = [...document.querySelectorAll('.payment-reconcile-form')];
const search = document.getElementById('paymentSearch');
const statusFilter = document.getElementById('paymentStatusFilter');
const dialog = document.getElementById('paymentReview');
const feedback = document.getElementById('paymentsFeedback');
const feedbackMessage = document.getElementById('paymentsFeedbackMessage');
const reload = document.getElementById('paymentsReload');
const navigation = document.getElementById('navigationDialog');
let selectedForm = null;
let posting = false;
let locked = false;

document.getElementById('openNavigation')?.addEventListener('click', () => navigation.showModal());
document.getElementById('closeNavigation')?.addEventListener('click', () => navigation.close());
navigation?.addEventListener('click', event => { if (event.target === navigation) navigation.close(); });

function filterRows() {
  let visible = 0;
  rows.forEach(row => {
    const matches = matchesPaymentFilter(row.textContent, row.dataset.status, search.value, statusFilter.value);
    row.hidden = !matches;
    if (matches) visible++;
  });
  document.getElementById('paymentFilterCount').textContent = `${visible} / ${rows.length} giao dịch đã tải`;
  document.getElementById('paymentsNoResults').hidden = rows.length === 0 || visible !== 0;
}
search.addEventListener('input', filterRows);
statusFilter.addEventListener('change', filterRows);
filterRows();

function showFeedback(text, kind, canReload = false) {
  feedback.hidden = false;
  feedback.dataset.state = kind;
  feedback.setAttribute('role', kind === 'error' ? 'alert' : 'status');
  feedbackMessage.textContent = text;
  reload.hidden = !canReload;
  feedback.focus();
}

for (const form of forms) {
  form.addEventListener('submit', event => {
    event.preventDefault();
    if (posting || locked) return;
    selectedForm = form;
    document.getElementById('paymentReviewReceipt').textContent = form.dataset.receipt;
    document.getElementById('paymentReviewNote').textContent = form.elements.note.value.trim();
    dialog.showModal();
    document.getElementById('paymentReviewTitle').focus();
  });
}
document.getElementById('paymentCancel').addEventListener('click', () => dialog.close());
document.getElementById('paymentConfirm').addEventListener('click', async () => {
  if (!selectedForm || posting || locked || !selectedForm.reportValidity()) return;
  dialog.close();
  posting = true;
  forms.forEach(form => { form.querySelector('button').disabled = true; });
  showFeedback('Đang gửi xác nhận đối soát đến máy chủ…', 'pending');
  try {
    const response = await fetch(selectedForm.action, {method:'POST',body:new URLSearchParams(new FormData(selectedForm)),credentials:'same-origin',redirect:'follow'});
    const target = new URL(response.url, location.href);
    const html = await response.text();
    const parsed = new DOMParser().parseFromString(html, 'text/html');
    const serverMessage = parsed.querySelector('.payments-success p')?.textContent.trim();
    const outcome = target.origin === location.origin ? classifyPaymentResponse({status:response.status,redirected:response.redirected,pathname:target.pathname,message:serverMessage}) : 'unknown';
    locked = true;
    if (outcome === 'success') showFeedback(serverMessage, 'success', true);
    else if (outcome === 'blocked') showFeedback('Phiên đăng nhập hoặc quyền hiện tại không cho phép xác nhận kết quả. Tải lại để kiểm tra.', 'error', true);
    else showFeedback('Không xác nhận được kết quả đối soát. Không gửi lại ngay; tải lại danh sách để kiểm tra.', 'error', true);
  } catch {
    locked = true;
    showFeedback('Mất kết nối trước khi xác nhận kết quả. Không gửi lại ngay; tải lại danh sách để kiểm tra.', 'error', true);
  } finally { posting = false; }
});
