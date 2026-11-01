import { classifyPricingResponse } from './pricing-model.mjs';

const forms = [...document.querySelectorAll('.pricing-form')];
const dialog = document.getElementById('pricingReview');
const feedback = document.getElementById('pricingFeedback');
const message = document.getElementById('pricingFeedbackMessage');
const reload = document.getElementById('pricingReload');
const navigation = document.getElementById('navigationDialog');
let selectedForm = null;
let locked = false;
let posting = false;

document.getElementById('openNavigation')?.addEventListener('click', () => navigation.showModal());
document.getElementById('closeNavigation')?.addEventListener('click', () => navigation.close());
navigation?.addEventListener('click', event => { if (event.target === navigation) navigation.close(); });

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
      detail.textContent = `${input.value} ${label.querySelector('.pricing-input span').textContent}`;
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
  } finally { posting = false; }
});
