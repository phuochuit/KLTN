import { classifyPricingResponse, formatPricingValue, pricingValuesEqual } from './pricing-model.mjs';

const forms = [...document.querySelectorAll('.pricing-form')];
const dialog = document.getElementById('pricingReview');
const feedback = document.getElementById('pricingFeedback');
const message = document.getElementById('pricingFeedbackMessage');
const reload = document.getElementById('pricingReload');
const navigation = document.getElementById('navigationDialog');
const pricingList = document.getElementById('pricingList');
let selectedForm = null;
let locked = false;
let posting = false;

document.getElementById('openNavigation')?.addEventListener('click', () => navigation.showModal());
document.getElementById('closeNavigation')?.addEventListener('click', () => navigation.close());
navigation?.addEventListener('click', event => { if (event.target === navigation) navigation.close(); });
dialog.addEventListener('keydown', event => {
  if (event.key !== 'Tab') return;
  const focusable = [...dialog.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])')]
    .filter(element => !element.hidden);
  if (!focusable.length) return;
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const active = document.activeElement;
  const activeIsTabbable = focusable.includes(active);
  if (event.shiftKey && (active === first || !activeIsTabbable)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (active === last || !activeIsTabbable)) {
    event.preventDefault();
    first.focus();
  }
});

function showFeedback(text, kind, canReload = false) {
  feedback.hidden = false;
  feedback.dataset.state = kind;
  feedback.setAttribute('role', kind === 'error' ? 'alert' : 'status');
  message.textContent = text;
  reload.hidden = !canReload;
  feedback.focus();
}

function lockForms() {
  locked = true;
  forms.forEach(form => { form.querySelector('button[type="submit"]').disabled = true; });
}

const originalValues = new Map(forms.map(form => [form, new Map(
  [...form.querySelectorAll('label input[name]')].map(input => [input.name, input.value]),
)]));

for (const form of forms) {
  form.addEventListener('submit', event => {
    event.preventDefault();
    if (locked || posting) return;
    selectedForm = form;
    document.getElementById('pricingReviewType').textContent = `Nhóm xe: ${form.dataset.type}`;
    const values = document.getElementById('pricingReviewValues');
    values.replaceChildren();
    form.querySelectorAll('label').forEach(label => {
      const input = label.querySelector('input');
      const row = document.createElement('div');
      const term = document.createElement('dt');
      const detail = document.createElement('dd');
      term.textContent = label.querySelector('span').textContent;
      const unit = label.querySelector('.pricing-input span').textContent;
      const original = originalValues.get(form).get(input.name);
      detail.textContent = pricingValuesEqual(original, input.value)
        ? `Không đổi · ${formatPricingValue(input.value, unit)}`
        : `${formatPricingValue(original, unit)} → ${formatPricingValue(input.value, unit)}`;
      row.append(term, detail); values.append(row);
    });
    dialog.showModal();
    document.getElementById('pricingReviewTitle').focus();
  });
}
document.getElementById('pricingCancel').addEventListener('click', () => dialog.close());
document.getElementById('pricingConfirm').addEventListener('click', async () => {
  if (!selectedForm || posting || locked || !selectedForm.reportValidity()) return;
  dialog.close();
  posting = true;
  pricingList.setAttribute('aria-busy', 'true');
  forms.forEach(form => { form.querySelector('button[type="submit"]').disabled = true; });
  showFeedback('Đang gửi cập nhật bảng giá đến máy chủ…', 'pending');
  try {
    const response = await fetch(selectedForm.action, { method: 'POST', body: new URLSearchParams(new FormData(selectedForm)), credentials: 'same-origin', redirect: 'follow' });
    const target = new URL(response.url, location.href);
    const html = await response.text();
    const parsed = new DOMParser().parseFromString(html, 'text/html');
    const serverMessage = parsed.querySelector('.pricing-success p')?.textContent.trim();
    const outcome = target.origin === location.origin ? classifyPricingResponse({ status: response.status, redirected: response.redirected, pathname: target.pathname, message: serverMessage }) : 'unknown';
    lockForms();
    if (outcome === 'success') showFeedback(serverMessage, 'success', true);
    else if (outcome === 'blocked') showFeedback('Phiên đăng nhập hoặc quyền hiện tại không cho phép xác nhận kết quả. Tải lại để kiểm tra.', 'error', true);
    else showFeedback('Không xác nhận được kết quả cập nhật. Không gửi lại ngay; tải lại bảng giá để kiểm tra.', 'error', true);
  } catch {
    lockForms();
    showFeedback('Mất kết nối trước khi xác nhận kết quả. Không gửi lại ngay; tải lại bảng giá để kiểm tra.', 'error', true);
  } finally { posting = false; pricingList.setAttribute('aria-busy', 'false'); }
});
