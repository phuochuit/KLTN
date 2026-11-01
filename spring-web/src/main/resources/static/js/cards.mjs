import {
  classifyCardWrite,
  cardSuccessMessage,
  formatVietnameseDate,
  inclusiveEndDate,
  parseVietnameseDate,
} from './cards-model.mjs';

const form = document.getElementById('cardForm');
if (form) initializeCardsPage(form);

function initializeCardsPage(form) {
  const byId = id => document.getElementById(id);
  const passType = byId('cardPassType');
  const vehicle = byId('cardVehicleId');
  const status = byId('cardStatus');
  const startDate = byId('cardStartDate');
  const duration = byId('cardDurationDays');
  const monthlyFields = byId('monthlyFields');
  const paymentMethod = byId('cardPaymentMethod');
  const paymentReference = byId('cardPaymentReference');
  const submitButton = byId('cardSubmitButton');
  const submitContent = submitButton.innerHTML;
  const feedback = byId('cardFeedback');
  const feedbackMessage = byId('cardFeedbackMessage');
  const feedbackIcon = byId('cardFeedbackIcon');
  const reloadLink = byId('reloadCardList');
  const reviewDialog = byId('cardReviewDialog');
  const confirmButton = byId('confirmCardWrite');
  const navigation = byId('navigationDialog');
  const state = { posting: false, writeLocked: false };

  const openNavigation = byId('openNavigation');
  const closeNavigation = byId('closeNavigation');
  openNavigation?.addEventListener('click', () => navigation?.showModal());
  closeNavigation?.addEventListener('click', () => navigation?.close());
  navigation?.addEventListener('click', event => {
    if (event.target === navigation) navigation.close();
  });

  const initialDate = startDate.value;
  startDate.type = 'text';
  startDate.inputMode = 'numeric';
  startDate.placeholder = 'dd/mm/yyyy';
  startDate.dataset.dateInput = 'vietnamese';
  startDate.value = formatVietnameseDate(initialDate) || initialDate;

  function isMonthly() {
    return passType.value === 'MONTHLY';
  }

  function selectedVehicle() {
    return vehicle.selectedOptions[0];
  }

  function selectedPaymentMethod() {
    return paymentMethod.selectedOptions[0];
  }

  function monthlyPrice() {
    const amount = Number(selectedVehicle()?.dataset.monthlyPrice);
    return Number.isFinite(amount) && amount > 0 ? amount : null;
  }

  function money(amount) {
    return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(amount)} đ`;
  }

  function setText(id, value) {
    byId(id).textContent = value;
  }

  function setShown(id, shown) {
    byId(id).hidden = !shown;
  }

  function showFeedback(message, kind, { reload = false, focus = false } = {}) {
    feedback.hidden = false;
    feedback.dataset.state = kind;
    feedback.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    feedbackMessage.textContent = message;
    feedbackIcon.src = new URL(kind === 'pending' ? 'clock-3.svg' : kind === 'success' ? 'circle-check.svg' : 'circle-alert.svg', feedbackIcon.src).href;
    reloadLink.hidden = !reload;
    reloadLink.href = window.location.href;
    if (focus) feedback.focus();
  }

  function updateMode() {
    const monthly = isMonthly();
    monthlyFields.hidden = !monthly;
    monthlyFields.querySelectorAll('input, select').forEach(control => {
      control.disabled = !monthly;
    });
    startDate.required = monthly;
    paymentMethod.required = monthly;
    paymentReference.required = monthly && selectedPaymentMethod()?.dataset.requiresReference === 'true';
    setShown('startDateRequired', monthly);
    setShown('paymentMethodRequired', monthly);
    setShown('paymentReferenceRequired', paymentReference.required);
    setShown('previewPeriodRow', monthly);
    setShown('previewPaymentRow', monthly);
    setShown('previewReferenceRow', monthly);
    setShown('reviewPeriodRow', monthly);
    setShown('reviewPaymentRow', monthly);
    setShown('reviewReferenceRow', monthly);
    byId('previewDisclosure').textContent = monthly
      ? 'Khoản thu được ghi nhận trong hệ thống; thông tin này không xác nhận đối soát hoặc thanh toán thành công từ nhà cung cấp bên ngoài.'
      : 'Vé lượt không tạo bản ghi thu gói tháng; phí mỗi lượt tiếp tục theo quy tắc nghiệp vụ hiện hành.';
    byId('reviewDisclosure').textContent = byId('previewDisclosure').textContent;
    updatePreview();
  }

  function periodSummary() {
    const durationValue = duration.value.trim() === '' ? 30 : Number(duration.value);
    const start = parseVietnameseDate(startDate.value);
    const end = start ? inclusiveEndDate(start, durationValue) : null;
    if (!start) return 'Chọn ngày bắt đầu';
    if (!end) return 'Kiểm tra số ngày (1–366)';
    return `${formatVietnameseDate(start)} – ${formatVietnameseDate(end)} (tính cả ngày cuối)`;
  }

  function updatePreview() {
    const monthly = isMonthly();
    const parsedStartDate = parseVietnameseDate(startDate.value);
    startDate.setCustomValidity(monthly && startDate.value && !parsedStartDate
      ? 'Nhập ngày có thật theo định dạng dd/mm/yyyy.'
      : '');
    const option = selectedVehicle();
    const price = monthlyPrice();
    const period = periodSummary();
    const method = selectedPaymentMethod();
    const needsReference = method?.dataset.requiresReference === 'true';
    const vehicleText = option?.dataset.plate
      ? `${option.dataset.plate} · ${option.dataset.owner || 'Chưa có tên chủ xe'} · ${option.dataset.type || ''}`
      : 'Chưa có xe phù hợp';
    const amountText = monthly ? (price === null ? 'Chưa có giá gói' : money(price)) : 'Không thu gói tháng';
    const methodText = method?.textContent.trim() || 'Chưa chọn phương thức';
    const referenceText = needsReference
      ? (paymentReference.value.trim() || 'Cần nhập mã giao dịch')
      : 'Không áp dụng';

    setText('previewVehicle', vehicleText);
    setText('previewPassType', passType.selectedOptions[0]?.textContent.trim() || '—');
    setText('previewStatus', status.selectedOptions[0]?.textContent.trim() || '—');
    setText('previewPeriod', monthly ? period : 'Không áp dụng gói tháng');
    setText('previewAmount', amountText);
    setText('previewPayment', methodText);
    setText('previewReference', referenceText);
    setShown('previewUnavailable', monthly && price === null);
    submitButton.disabled = state.writeLocked || !vehicle.value || (monthly && price === null);
  }

  function copyPreviewToReview() {
    for (const [source, destination] of [
      ['previewVehicle', 'reviewVehicle'],
      ['previewPassType', 'reviewPassType'],
      ['previewStatus', 'reviewStatus'],
      ['previewPeriod', 'reviewPeriod'],
      ['previewAmount', 'reviewAmount'],
      ['previewPayment', 'reviewPayment'],
      ['previewReference', 'reviewReference'],
    ]) {
      setText(destination, byId(source).textContent);
    }
  }

  function serverMessage(html) {
    const parsed = new DOMParser().parseFromString(html, 'text/html');
    return parsed.querySelector('.settings-write-message')?.textContent.trim().slice(0, 400) || null;
  }

  function restoreSubmit() {
    state.posting = false;
    form.removeAttribute('aria-busy');
    submitButton.removeAttribute('aria-busy');
    if (!state.writeLocked) {
      submitButton.disabled = false;
      submitButton.innerHTML = submitContent;
      updatePreview();
    }
  }

  function lockAfterUnknown(message) {
    state.writeLocked = true;
    submitButton.disabled = true;
    submitButton.textContent = 'Không gửi lại';
    byId('cardSubmitHint').textContent = 'Không gửi lại từ biểu mẫu này. Hãy tải lại danh sách và kiểm tra trạng thái trước.';
    showFeedback(`${message} Không gửi lại từ trang này; hãy tải lại danh sách để kiểm tra trạng thái trước khi quyết định bước tiếp theo.`, 'error', { reload: true, focus: true });
  }

  async function submitCard() {
    if (state.posting || state.writeLocked) return;
    state.posting = true;
    form.setAttribute('aria-busy', 'true');
    submitButton.disabled = true;
    submitButton.setAttribute('aria-busy', 'true');
    submitButton.textContent = 'Đang gửi…';
    showFeedback('Đang gửi thao tác. Không đóng trang trong lúc chờ xác nhận từ máy chủ.', 'pending');

    try {
      const parameters = new URLSearchParams(new FormData(form));
      if (isMonthly()) {
        parameters.set('startDate', parseVietnameseDate(startDate.value) || '');
      } else {
        for (const name of ['startDate', 'durationDays', 'paymentMethod', 'paymentReference']) parameters.delete(name);
      }
      const response = await fetch(form.action, {
        method: 'POST',
        body: parameters,
        credentials: 'same-origin',
        redirect: 'follow',
      });
      const target = new URL(response.url, window.location.href);
      const outcome = target.origin === window.location.origin
        ? classifyCardWrite({ status: response.status, redirected: response.redirected, pathname: target.pathname })
        : 'unknown';

      if (outcome === 'success') {
        const html = await response.text();
        const message = cardSuccessMessage(new DOMParser().parseFromString(html, 'text/html'));
        if (!message) {
          lockAfterUnknown('Máy chủ đã chuyển về danh sách nhưng không có thông báo xác nhận.');
          return;
        }
        state.writeLocked = true;
        submitButton.textContent = 'Đã xác nhận';
        byId('cardSubmitHint').textContent = 'Tải lại danh sách để xem dữ liệu mới trước khi thực hiện thao tác khác.';
        showFeedback(message, 'success', { reload: true, focus: true });
        return;
      }

      const text = await response.text();
      const message = serverMessage(text);
      if (outcome === 'retry-safe') {
        showFeedback(`${message || 'Thao tác đã bị từ chối.'} Hãy sửa dữ liệu nếu cần và làm theo hướng dẫn trên trước khi xác nhận lại.`, 'error', { focus: true });
        return;
      }
      if (outcome === 'session') {
        lockAfterUnknown('Phiên đăng nhập đã hết hạn hoặc yêu cầu đã được chuyển tới trang đăng nhập.');
        return;
      }
      if (outcome === 'blocked') {
        lockAfterUnknown('Yêu cầu bị từ chối do phiên đăng nhập hoặc quyền hiện tại.');
        return;
      }
      lockAfterUnknown(message || 'Không xác nhận được kết quả lưu.');
    } catch {
      lockAfterUnknown('Mất kết nối trước khi nhận được xác nhận kết quả lưu.');
    } finally {
      restoreSubmit();
    }
  }

  form.addEventListener('input', updatePreview);
  form.addEventListener('change', updateMode);
  form.addEventListener('submit', event => {
    event.preventDefault();
    if (state.posting || state.writeLocked || submitButton.disabled) return;
    updatePreview();
    copyPreviewToReview();
    reviewDialog.showModal();
    byId('cardReviewHeading').focus();
  });
  byId('cancelCardReview').addEventListener('click', () => reviewDialog.close());
  confirmButton.addEventListener('click', () => {
    if (!form.reportValidity()) return;
    reviewDialog.close();
    submitCard();
  });

  updateMode();
}
