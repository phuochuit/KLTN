import {
  MAX_IMAGE_BYTES,
  classifyRegistrationWrite,
  formatVietnameseDate,
  formatVietnameseDateInput,
  imageFileError,
  parseVietnameseDate,
  qrFieldValues,
} from './registration-model.mjs';

const form = document.querySelector('#registrationForm');
if (form) initializeRegistration(form);

function initializeRegistration(form) {
  const byId = id => document.getElementById(id);
  const csrf = form.querySelector('input[name="_csrf"]');
  const feedback = byId('registrationFeedback');
  const errorSummary = byId('registrationErrors');
  const errorGroups = byId('registrationErrorGroups');
  const saveButton = byId('saveRegistrationButton');
  const saveIcon = saveButton.querySelector('img');
  const saveLabel = saveButton.querySelector('span');
  const originalSaveLabel = saveLabel.textContent;
  const qrInput = byId('cccdQrFile');
  const qrStatus = byId('qrStatus');
  const qrPreview = byId('qrPreview');
  const qrPreviewFrame = byId('qrPreviewFrame');
  const qrPreviewFallback = byId('qrPreviewFallback');
  const qrReview = byId('qrReview');
  const qrReviewFields = byId('qrReviewFields');
  const qrRaw = byId('cccdQrRaw');
  const faceInput = byId('faceUpload');
  const dateFields = [byId('dateOfBirth'), byId('cccdIssueDate')];
  const dateHints = new Map([
    [byId('dateOfBirth'), byId('dateOfBirthHint')],
    [byId('cccdIssueDate'), byId('cccdIssueDateHint')],
  ]);
  const faceCapture = byId('registrationFaceCapture');
  const facePreview = byId('facePreview');
  const facePreviewFrame = byId('facePreviewFrame');
  const facePreviewFallback = byId('facePreviewFallback');
  const registrationImage = byId('registrationImage');
  const registrationImageStatus = byId('registrationImageStatus');
  const registrationImagePreview = byId('registrationImagePreview');
  const registrationImagePreviewFrame = byId('registrationImagePreviewFrame');
  const registrationImagePreviewFallback = byId('registrationImagePreviewFallback');
  const state = {
    previewUrls: { qr: null, face: null, vehicle: null },
    pendingQr: null,
    appliedQrSnapshot: null,
    qrController: null,
    qrRequestId: 0,
    writeLocked: false,
    validationAttempted: false,
  };

  dateFields.forEach(control => {
    const isoValue = control.value;
    control.type = 'text';
    control.value = formatVietnameseDate(isoValue);
    control.inputMode = 'numeric';
    control.maxLength = 10;
    control.placeholder = 'dd/MM/yyyy';
    control.autocomplete = 'off';
    control.dataset.dateInput = 'vietnamese';
    dateHints.get(control).textContent = 'Nhập hoặc dán ngày theo định dạng dd/MM/yyyy.';
    syncDateValidity(control);
  });

  function setMediaStatus(element, message, kind = 'info') {
    element.textContent = message;
    element.dataset.state = kind;
    element.setAttribute('role', kind === 'error' ? 'alert' : 'status');
  }

  function setFeedback(message, kind, { focus = false } = {}) {
    feedback.replaceChildren();
    const text = document.createElement('p');
    text.textContent = message;
    feedback.append(text);
    feedback.dataset.state = kind;
    feedback.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    feedback.hidden = false;
    if (focus) feedback.focus();
  }

  function fieldLabel(control) {
    const label = control.closest('label')?.querySelector('.registration-field-label');
    const text = [...(label?.childNodes ?? [])]
      .filter(node => node.nodeType === Node.TEXT_NODE)
      .map(node => node.textContent)
      .join(' ')
      .trim();
    return text || 'Trường dữ liệu';
  }

  function describe(control, id) {
    const ids = new Set((control.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean));
    ids.add(id);
    control.setAttribute('aria-describedby', [...ids].join(' '));
  }

  function clearDescription(control, id) {
    const ids = (control.getAttribute('aria-describedby') || '').split(/\s+/).filter(value => value && value !== id);
    if (ids.length) control.setAttribute('aria-describedby', ids.join(' '));
    else control.removeAttribute('aria-describedby');
  }

  function syncDateValidity(control) {
    if (!dateFields.includes(control)) return;
    const value = control.value.trim();
    control.setCustomValidity(value && !parseVietnameseDate(value)
      ? 'Nhập ngày hợp lệ theo định dạng dd/MM/yyyy.'
      : '');
  }

  function formatDateEntry(control) {
    const start = control.selectionStart ?? control.value.length;
    const digitsBeforeCursor = control.value.slice(0, start).replace(/\D/g, '').length;
    const formatted = formatVietnameseDate(control.value) || formatVietnameseDateInput(control.value);
    control.value = formatted;
    const separatorsBeforeCursor = digitsBeforeCursor > 4 ? 2 : digitsBeforeCursor > 2 ? 1 : 0;
    const cursor = Math.min(digitsBeforeCursor + separatorsBeforeCursor, formatted.length);
    control.setSelectionRange(cursor, cursor);
  }

  function clearFieldError(control) {
    const error = control.closest('.registration-field')?.querySelector(`[data-error-for="${control.id}"]`);
    if (error) error.remove();
    control.removeAttribute('aria-invalid');
    clearDescription(control, `${control.id}-error`);
  }

  function validationMessage(control) {
    const validity = control.validity;
    const label = fieldLabel(control);
    if (validity.customError) return control.validationMessage;
    if (validity.valueMissing) return `${label} không được để trống.`;
    if (validity.typeMismatch) return `${label} chưa đúng định dạng.`;
    if (validity.tooShort) return `${label} cần có ít nhất ${control.minLength} ký tự.`;
    if (validity.tooLong) return `${label} không được vượt quá ${control.maxLength} ký tự.`;
    if (validity.rangeUnderflow) return `${label} không được nhỏ hơn ${control.min}.`;
    if (validity.rangeOverflow) return `${label} không được lớn hơn ${control.max}.`;
    if (validity.badInput) return `${label} chưa phải giá trị hợp lệ.`;
    return `${label} cần được kiểm tra lại.`;
  }

  function setFieldError(control, message) {
    const field = control.closest('.registration-field');
    if (!field) return;
    let error = field.querySelector(`[data-error-for="${control.id}"]`);
    if (!error) {
      error = document.createElement('small');
      error.className = 'registration-field-error';
      error.dataset.errorFor = control.id;
      error.id = `${control.id}-error`;
      field.append(error);
    }
    error.textContent = message;
    control.setAttribute('aria-invalid', 'true');
    describe(control, error.id);
  }

  function refreshFaceRequirement() {
    const hasFile = Boolean(faceInput.files?.length);
    const hasCapture = Boolean(faceCapture.value);
    faceInput.required = !hasCapture;
    faceInput.setCustomValidity(hasFile || hasCapture ? '' : 'Tải ảnh lên hoặc chụp ảnh khuôn mặt đăng ký.');
  }

  function formControls() {
    return [...form.querySelectorAll('input:not([type="hidden"]), select, textarea')]
      .filter(control => !control.disabled);
  }

  function collectInvalidControls() {
    refreshFaceRequirement();
    dateFields.forEach(syncDateValidity);
    return formControls().filter(control => !control.checkValidity());
  }

  function updateSummary(invalidControls, focus = false) {
    invalidControls.forEach(control => setFieldError(control, validationMessage(control)));
    const openGroups = new Set([...errorGroups.querySelectorAll('details[open]')].map(group => group.dataset.group));
    const groups = [
      { id: 'householdSection', label: 'Hộ/căn hộ', controls: [] },
      { id: 'residentSection', label: 'Người đăng ký', controls: [] },
      { id: 'vehicleSection', label: 'Phương tiện', controls: [] },
    ];
    invalidControls.forEach(control => {
      const group = groups.find(item => item.id === control.closest('section')?.id);
      group?.controls.push(control);
    });
    const activeGroups = groups.filter(group => group.controls.length);
    const keepOpen = activeGroups.some(group => openGroups.has(group.id));
    errorGroups.replaceChildren();
    activeGroups.forEach((group, index) => {
      const details = document.createElement('details');
      details.className = 'registration-error-group';
      details.dataset.group = group.id;
      details.open = openGroups.has(group.id) || (!keepOpen && index === 0);
      const summary = document.createElement('summary');
      summary.append(document.createTextNode(group.label));
      const count = document.createElement('span');
      count.className = 'registration-error-count';
      count.textContent = `${group.controls.length} lỗi`;
      summary.append(count);
      const list = document.createElement('ul');
      group.controls.forEach(control => {
        const item = document.createElement('li');
        const link = document.createElement('a');
        link.href = `#${control.id}`;
        link.dataset.targetId = control.id;
        link.textContent = fieldLabel(control);
        item.append(link);
        list.append(item);
      });
      details.append(summary, list);
      errorGroups.append(details);
    });
    errorSummary.querySelector('p').textContent = invalidControls.length
      ? `${invalidControls.length} trường cần được bổ sung hoặc điều chỉnh trước khi lưu.`
      : 'Các trường cần xử lý được gom theo từng phần của biểu mẫu.';
    errorSummary.hidden = invalidControls.length === 0;
    if (focus && invalidControls.length) errorSummary.focus();
  }

  errorGroups.addEventListener('click', event => {
    const link = event.target.closest('a[data-target-id]');
    if (!link) return;
    const control = byId(link.dataset.targetId);
    if (!control) return;
    event.preventDefault();
    control.focus();
  });

  function releasePreview(kind) {
    if (state.previewUrls[kind]) window.URL.revokeObjectURL(state.previewUrls[kind]);
    state.previewUrls[kind] = null;
    const { image, frame, fallback } = previewTarget(kind);
    image.onload = null;
    image.onerror = null;
    image.removeAttribute('src');
    image.hidden = true;
    fallback.hidden = true;
    frame.hidden = true;
  }

  function previewTarget(kind) {
    if (kind === 'qr') return { image: qrPreview, frame: qrPreviewFrame, fallback: qrPreviewFallback };
    if (kind === 'face') return { image: facePreview, frame: facePreviewFrame, fallback: facePreviewFallback };
    return { image: registrationImagePreview, frame: registrationImagePreviewFrame, fallback: registrationImagePreviewFallback };
  }

  function fileDescription(file, fallbackName = 'Ảnh đã chọn') {
    const size = file.size < 1024 * 1024
      ? `${Math.max(1, Math.round(file.size / 1024))} KB`
      : `${(file.size / (1024 * 1024)).toFixed(1)} MB`;
    return `${file.name || fallbackName} · ${size}`;
  }

  function previewErrorStatus(kind, file) {
    const description = fileDescription(file, kind === 'qr' ? 'Ảnh camera QR' : 'Ảnh camera');
    const message = `Không thể xem trước ${description}; ảnh vẫn được giữ làm nguồn đã chọn.`;
    if (kind === 'qr') setMediaStatus(qrStatus, `${message} Ảnh QR chỉ dùng để đọc dữ liệu, không gửi cùng hồ sơ.`, 'error');
    else if (kind === 'face') setMediaStatus(byId('faceStatus'), `${message} Ảnh vẫn sẵn sàng gửi khi lưu.`, 'error');
    else {
      registrationImageStatus.textContent = `${message} Ảnh vẫn sẵn sàng gửi khi lưu.`;
      registrationImageStatus.dataset.state = 'error';
    }
  }

  function showFilePreview(kind, file) {
    releasePreview(kind);
    const { image, frame, fallback } = previewTarget(kind);
    const source = window.URL.createObjectURL(file);
    state.previewUrls[kind] = source;
    fallback.textContent = 'Đang tạo bản xem trước…';
    fallback.dataset.state = 'loading';
    fallback.hidden = false;
    image.onload = () => {
      if (state.previewUrls[kind] !== source) return;
      fallback.hidden = true;
    };
    image.onerror = () => {
      if (state.previewUrls[kind] !== source) return;
      image.hidden = true;
      fallback.textContent = 'Không thể xem trước ảnh này. Tệp vẫn được giữ nếu hợp lệ.';
      fallback.dataset.state = 'unavailable';
      fallback.hidden = false;
      previewErrorStatus(kind, file);
    };
    image.src = source;
    image.hidden = false;
    frame.hidden = false;
  }

  const cameras = {
    qr: {
      video: byId('qrVideo'),
      canvas: byId('qrCanvas'),
      frame: qrPreviewFrame,
      open: byId('openQrCamera'),
      capture: byId('captureQr'),
      stop: byId('stopQrCamera'),
      status: qrStatus,
      constraints: { video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false },
      generation: 0,
      stream: null,
    },
    face: {
      video: byId('faceVideo'),
      canvas: byId('faceCanvas'),
      frame: facePreviewFrame,
      open: byId('openFaceCamera'),
      capture: byId('captureFace'),
      stop: byId('stopFaceCamera'),
      status: byId('faceStatus'),
      constraints: { video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false },
      generation: 0,
      stream: null,
    },
  };

  function stopCamera(camera) {
    camera.generation++;
    camera.stream?.getTracks().forEach(track => track.stop());
    camera.stream = null;
    camera.video.pause();
    camera.video.srcObject = null;
    camera.video.hidden = true;
    camera.capture.hidden = true;
    camera.stop.hidden = true;
    camera.stop.textContent = 'Hủy / dừng';
    camera.open.disabled = false;
    camera.open.hidden = false;
    const preview = camera.frame.querySelector('img');
    preview.hidden = !preview.getAttribute('src');
    camera.frame.hidden = preview.hidden;
  }

  async function startCamera(camera) {
    const generation = ++camera.generation;
    camera.open.disabled = true;
    camera.stop.hidden = false;
    camera.stop.textContent = 'Hủy yêu cầu';
    setMediaStatus(camera.status, 'Đang yêu cầu quyền mở camera…');
    try {
      const hostname = window.location.hostname;
      const localOrigin = hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]';
      if (!window.isSecureContext && !localOrigin) throw new Error('secure-context');
      const getUserMedia = window.navigator.mediaDevices?.getUserMedia?.bind(window.navigator.mediaDevices);
      if (!getUserMedia) throw new Error('unsupported');
      const stream = await getUserMedia(camera.constraints);
      if (camera.generation !== generation) {
        stream.getTracks().forEach(track => track.stop());
        return;
      }
      camera.stream = stream;
      camera.video.srcObject = stream;
      camera.frame.querySelector('img').hidden = true;
      camera.video.hidden = false;
      camera.frame.hidden = false;
      await camera.video.play();
      if (camera.generation !== generation) return;
      camera.open.hidden = true;
      camera.open.disabled = false;
      camera.capture.hidden = false;
      camera.stop.textContent = 'Dừng camera';
      setMediaStatus(camera.status, camera === cameras.qr
        ? 'Đưa mã QR vào giữa khung hình, giữ thẳng và tránh phản sáng.'
        : 'Đặt khuôn mặt trong khung hình rồi chọn chụp.');
    } catch (error) {
      if (camera.generation !== generation) return;
      camera.stream?.getTracks().forEach(track => track.stop());
      camera.stream = null;
      camera.video.srcObject = null;
      camera.video.hidden = true;
      camera.capture.hidden = true;
      camera.stop.hidden = true;
      camera.open.hidden = false;
      camera.open.disabled = false;
      const preview = camera.frame.querySelector('img');
      preview.hidden = !preview.getAttribute('src');
      camera.frame.hidden = preview.hidden;
      const message = error.message === 'unsupported'
        ? 'Trình duyệt không hỗ trợ camera tại đây.'
        : error.message === 'secure-context'
          ? 'Camera cần kết nối bảo mật. Hãy tải ảnh lên hoặc nhập thông tin thủ công.'
          : error.name === 'NotAllowedError'
            ? 'Camera chưa được cấp quyền. Bạn có thể tải ảnh lên thay thế.'
            : error.name === 'NotFoundError'
              ? 'Không tìm thấy camera. Bạn có thể tải ảnh lên thay thế.'
              : 'Không mở được camera. Hãy thử tải ảnh lên hoặc nhập thông tin thủ công.';
      setMediaStatus(camera.status, message, 'error');
    }
  }

  function clearQrReview() {
    state.pendingQr = null;
    qrReviewFields.replaceChildren();
    qrReview.hidden = true;
  }

  function clearAppliedQr() {
    state.appliedQrSnapshot = null;
    qrRaw.value = '';
  }

  function beginQrScan() {
    state.qrController?.abort();
    state.qrController = null;
    state.qrRequestId++;
    clearQrReview();
    clearAppliedQr();
  }

  function renderQrReview(data) {
    const fields = qrFieldValues(data);
    state.pendingQr = { data, fields };
    qrReviewFields.replaceChildren();
    fields.forEach(({ name, label, value }) => {
      const row = document.createElement('div');
      const term = document.createElement('dt');
      const description = document.createElement('dd');
      term.textContent = label;
      description.textContent = ['dateOfBirth', 'cccdIssueDate'].includes(name)
        ? formatVietnameseDate(value) || value
        : value;
      row.append(term, description);
      qrReviewFields.append(row);
    });
    qrReview.hidden = false;
    byId('applyQr').disabled = fields.length === 0;
    setMediaStatus(qrStatus, fields.length
      ? 'Đã đọc QR, hãy kiểm tra nội dung rồi chọn áp dụng.'
      : 'QR đã đọc nhưng không có trường dữ liệu để áp dụng.', fields.length ? 'success' : 'error');
  }

  async function scanQrBlob(blob) {
    if (!blob) {
      setMediaStatus(qrStatus, 'Không có ảnh để đọc. Hãy chọn ảnh hoặc mở camera.', 'error');
      return;
    }
    beginQrScan();
    if (!csrf?.value) {
      setMediaStatus(qrStatus, 'Không tìm thấy token bảo vệ. Hãy tải lại trang rồi thử lại.', 'error');
      return;
    }
    const controller = new AbortController();
    const requestId = state.qrRequestId;
    state.qrController = controller;
    const button = byId('scanQr');
    button.disabled = true;
    setMediaStatus(qrStatus, 'Đang đọc ảnh QR…');
    try {
      const body = new FormData();
      body.append('file', blob, 'cccd-qr.jpg');
      body.append('_csrf', csrf.value);
      const response = await fetch('/api/cccd/scan-qr', {
        method: 'POST',
        body,
        credentials: 'same-origin',
        signal: controller.signal,
      });
      if (requestId !== state.qrRequestId) return;
      const responseUrl = new URL(response.url, window.location.href);
      if (response.redirected && /\/login\/?$/.test(responseUrl.pathname)) {
        setMediaStatus(qrStatus, 'Phiên đăng nhập đã hết hạn. QR chưa được đọc; bạn có thể nhập thông tin thủ công.', 'error');
        return;
      }
      const contentType = response.headers.get('content-type') || '';
      const data = contentType.includes('application/json') ? await response.json() : null;
      if (!response.ok || !data || response.redirected) {
        const message = response.status === 403
          ? 'Không có quyền đọc QR hoặc yêu cầu bảo vệ bị từ chối. Bạn vẫn có thể nhập thông tin thủ công.'
          : typeof data?.message === 'string' ? data.message : 'Không đọc được QR. Hãy thử ảnh khác hoặc nhập thông tin thủ công.';
        setMediaStatus(qrStatus, message.slice(0, 280), 'error');
        return;
      }
      renderQrReview(data);
    } catch (error) {
      if (error.name !== 'AbortError' && requestId === state.qrRequestId) {
        setMediaStatus(qrStatus, 'Không đọc được QR do lỗi kết nối. Hãy thử lại hoặc nhập thông tin thủ công.', 'error');
      }
    } finally {
      if (requestId === state.qrRequestId) {
        button.disabled = !qrInput.files?.length;
        if (state.qrController === controller) state.qrController = null;
      }
    }
  }

  function applyQrValues() {
    if (!state.pendingQr) return;
    const { data, fields } = state.pendingQr;
    fields.forEach(({ name, value }) => {
      const control = form.elements.namedItem(name);
      if (!control) return;
      control.value = dateFields.includes(control) ? formatVietnameseDate(value) || value : value;
      syncDateValidity(control);
      if (control.checkValidity()) clearFieldError(control);
      else if (state.validationAttempted) setFieldError(control, validationMessage(control));
    });
    const raw = typeof data.raw === 'string' && data.raw.length <= 1000 ? data.raw : '';
    qrRaw.value = raw;
    state.appliedQrSnapshot = fields.map(({ name, value }) => ({ name, value }));
    clearQrReview();
    if (state.validationAttempted) updateSummary(collectInvalidControls());
    setMediaStatus(qrStatus, 'Dữ liệu đã điền vào các trường. Ảnh QR không gửi cùng hồ sơ; hãy kiểm tra thông tin trước khi lưu.', 'success');
  }

  function syncAppliedQrWithManualEdits(control) {
    if (!state.appliedQrSnapshot?.some(entry => entry.name === control.name)) return;
    const changed = state.appliedQrSnapshot.some(entry => {
      const field = form.elements.namedItem(entry.name);
      const value = dateFields.includes(field) ? parseVietnameseDate(field.value) || field.value : field.value;
      return value !== entry.value;
    });
    if (changed) {
      clearAppliedQr();
      setMediaStatus(qrStatus, 'Bạn đã chỉnh sửa thông tin đã áp dụng; dữ liệu QR gốc sẽ không gửi kèm.');
    }
  }

  function validateSelectedImage(input, label) {
    const file = input.files?.[0];
    const error = imageFileError(file);
    if (error) {
      input.value = '';
      setFieldError(input, `${label}: ${error}`);
      return false;
    }
    clearFieldError(input);
    return true;
  }

  qrInput.addEventListener('change', () => {
    beginQrScan();
    stopCamera(cameras.qr);
    releasePreview('qr');
    const selectedFile = qrInput.files?.[0];
    const valid = validateSelectedImage(qrInput, 'Ảnh QR');
    byId('scanQr').disabled = !valid || !qrInput.files?.length;
    if (valid && qrInput.files?.length) {
      showFilePreview('qr', qrInput.files[0]);
      setMediaStatus(qrStatus, `${fileDescription(qrInput.files[0])}. Ảnh chỉ dùng để đọc QR, không gửi cùng hồ sơ. Chọn “Đọc ảnh QR” để xem dữ liệu trước khi áp dụng.`);
    } else if (!selectedFile) {
      setMediaStatus(qrStatus, 'Chọn ảnh QR hoặc mở camera. Bạn cũng có thể điền CCCD thủ công.');
    } else {
      setMediaStatus(qrStatus, 'Ảnh QR không hợp lệ. Hãy chọn ảnh khác hoặc nhập thông tin thủ công.', 'error');
    }
  });

  byId('scanQr').addEventListener('click', () => {
    const file = qrInput.files?.[0];
    if (!file) {
      setMediaStatus(qrStatus, 'Hãy chọn ảnh có mã QR trên CCCD trước.', 'error');
      return;
    }
    scanQrBlob(file);
  });

  byId('applyQr').addEventListener('click', applyQrValues);
  byId('discardQr').addEventListener('click', () => {
    clearQrReview();
    clearAppliedQr();
    setMediaStatus(qrStatus, 'Đã bỏ dữ liệu QR chưa áp dụng.');
  });

  faceInput.addEventListener('change', () => {
    stopCamera(cameras.face);
    faceCapture.value = '';
    releasePreview('face');
    const selectedFile = faceInput.files?.[0];
    const valid = validateSelectedImage(faceInput, 'Ảnh khuôn mặt');
    refreshFaceRequirement();
    if (valid && faceInput.files?.length) {
      showFilePreview('face', faceInput.files[0]);
      setMediaStatus(byId('faceStatus'), `${fileDescription(faceInput.files[0])} đã sẵn sàng gửi khi lưu.`);
    } else if (!selectedFile) {
      setMediaStatus(byId('faceStatus'), 'Chưa có ảnh. Camera chỉ bật khi bạn chọn mở; nếu không cấp quyền, hãy tải ảnh lên.');
    } else {
      setMediaStatus(byId('faceStatus'), 'Ảnh khuôn mặt không hợp lệ. Hãy chọn ảnh khác hoặc chụp trực tiếp.', 'error');
    }
    if (state.validationAttempted) updateSummary(collectInvalidControls());
  });

  registrationImage.addEventListener('change', () => {
    releasePreview('vehicle');
    const selectedFile = registrationImage.files?.[0];
    const valid = validateSelectedImage(registrationImage, 'Ảnh giấy đăng ký xe');
    registrationImageStatus.textContent = valid && registrationImage.files?.length
      ? `${fileDescription(registrationImage.files[0])} đã sẵn sàng gửi cùng hồ sơ.`
      : selectedFile ? 'Ảnh không hợp lệ; hãy chọn tệp khác.' : 'Chưa chọn ảnh.';
    registrationImageStatus.dataset.state = valid && selectedFile ? 'info' : selectedFile ? 'error' : 'info';
    if (valid && registrationImage.files?.length) showFilePreview('vehicle', registrationImage.files[0]);
    if (state.validationAttempted) updateSummary(collectInvalidControls());
  });

  byId('openQrCamera').addEventListener('click', () => {
    beginQrScan();
    stopCamera(cameras.qr);
    releasePreview('qr');
    qrInput.value = '';
    byId('scanQr').disabled = true;
    startCamera(cameras.qr);
  });
  byId('openFaceCamera').addEventListener('click', () => startCamera(cameras.face));
  byId('stopQrCamera').addEventListener('click', () => {
    stopCamera(cameras.qr);
    setMediaStatus(qrStatus, 'Camera dừng. Bạn có thể chọn ảnh QR hoặc nhập thông tin thủ công.');
  });
  byId('stopFaceCamera').addEventListener('click', () => {
    stopCamera(cameras.face);
    setMediaStatus(byId('faceStatus'), 'Camera dừng. Bạn có thể tải ảnh lên thay thế.');
  });

  byId('captureFace').addEventListener('click', () => {
    const camera = cameras.face;
    const image = captureDataUrl(camera.video, camera.canvas, 1280);
    if (!image) {
      setMediaStatus(byId('faceStatus'), 'Chưa nhận được khung hình. Hãy đợi camera sẵn sàng hoặc tải ảnh lên.', 'error');
      return;
    }
    faceInput.value = '';
    faceCapture.value = image;
    releasePreview('face');
    facePreview.onload = () => { facePreviewFallback.hidden = true; };
    facePreview.onerror = () => {
      facePreview.hidden = true;
      facePreviewFallback.textContent = 'Không thể xem trước ảnh chụp; ảnh vẫn được giữ làm nguồn đã chọn.';
      facePreviewFallback.dataset.state = 'unavailable';
      facePreviewFallback.hidden = false;
      setMediaStatus(byId('faceStatus'), 'Ảnh chụp từ camera vẫn sẵn sàng gửi khi lưu nhưng không thể xem trước.', 'error');
    };
    facePreview.src = image;
    facePreview.hidden = false;
    facePreviewFrame.hidden = false;
    clearFieldError(faceInput);
    refreshFaceRequirement();
    stopCamera(camera);
    setMediaStatus(byId('faceStatus'), 'Ảnh chụp từ camera đã sẵn sàng gửi khi lưu. Mở lại camera hoặc tải ảnh khác để thay thế.');
    if (state.validationAttempted) updateSummary(collectInvalidControls());
  });

  byId('captureQr').addEventListener('click', async () => {
    const camera = cameras.qr;
    const blob = await captureBlob(camera.video, camera.canvas, 1600);
    stopCamera(camera);
    if (!blob) {
      setMediaStatus(qrStatus, 'Chưa nhận được ảnh QR. Hãy thử chụp lại hoặc chọn ảnh từ thiết bị.', 'error');
      return;
    }
    if (blob.size > MAX_IMAGE_BYTES) {
      setMediaStatus(qrStatus, 'Ảnh camera vượt quá 10 MB. Hãy chụp lại gần hơn hoặc chọn ảnh khác.', 'error');
      return;
    }
    qrInput.value = '';
    showFilePreview('qr', blob);
    scanQrBlob(blob);
  });

  function captureDataUrl(video, canvas, maxSide) {
    if (!video.videoWidth || !video.videoHeight) return null;
    const scale = Math.min(1, maxSide / Math.max(video.videoWidth, video.videoHeight));
    canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
    canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
    const context = canvas.getContext('2d');
    if (!context) return null;
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/jpeg', .78);
  }

  function captureBlob(video, canvas, maxSide) {
    if (!video.videoWidth || !video.videoHeight) return Promise.resolve(null);
    const scale = Math.min(1, maxSide / Math.max(video.videoWidth, video.videoHeight));
    canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
    canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
    const context = canvas.getContext('2d');
    if (!context) return Promise.resolve(null);
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    return new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', .9));
  }

  function serverMessage(html) {
    const parsed = new DOMParser().parseFromString(html, 'text/html');
    return parsed.querySelector('.settings-write-message')?.textContent.trim().slice(0, 400) || null;
  }

  function lockAfterUnknown(message) {
    state.writeLocked = true;
    saveButton.disabled = true;
    saveIcon.hidden = true;
    saveLabel.textContent = 'Không gửi lại';
    setFeedback(`${message} Không gửi lại từ trang này. Trang không có chức năng tra cứu trạng thái; dữ liệu vẫn còn trong biểu mẫu đang mở. Nếu rời trang, thông tin và tệp đã chọn có thể bị mất.`, 'error', { focus: true });
  }

  async function submitRegistration(event) {
    event.preventDefault();
    if (state.writeLocked || saveButton.disabled) return;
    state.validationAttempted = true;
    const invalid = collectInvalidControls();
    updateSummary(invalid, invalid.length > 0);
    if (invalid.length) return;
    if (!csrf?.value) {
      setFeedback('Không tìm thấy token bảo vệ biểu mẫu. Hãy tải lại trang trước khi gửi.', 'error', { focus: true });
      return;
    }

    form.setAttribute('aria-busy', 'true');
    saveButton.disabled = true;
    saveButton.setAttribute('aria-busy', 'true');
    saveIcon.hidden = true;
    saveLabel.textContent = 'Đang lưu hồ sơ…';
    setFeedback('Đang lưu hồ sơ. Không đóng trang trong lúc chờ phản hồi.', 'pending');
    try {
      const response = await fetch(form.action, {
        method: 'POST',
        body: registrationFormData(),
        credentials: 'same-origin',
      });
      const target = new URL(response.url, window.location.href);
      const outcome = target.origin === window.location.origin
        ? classifyRegistrationWrite({ status: response.status, redirected: response.redirected, pathname: target.pathname })
        : 'unknown';

      if (outcome === 'success') {
        setFeedback('Đã lưu hồ sơ. Đang mở bước tiếp theo…', 'success');
        window.location.assign(target.href);
        return;
      }
      if (['validation-error', 'conflict', 'upload-error'].includes(outcome)) {
        const serverText = serverMessage(await response.text());
        const guidance = outcome === 'validation-error'
          ? 'Dữ liệu chưa hợp lệ. Hãy kiểm tra các trường được đánh dấu.'
          : outcome === 'conflict'
            ? 'Dữ liệu đã thay đổi hoặc xung đột. Hãy rà soát thông tin trước khi gửi lại.'
            : 'Tệp tải lên vượt giới hạn cho phép. Hãy chọn tệp phù hợp trước khi gửi lại.';
        form.removeAttribute('aria-busy');
        saveButton.removeAttribute('aria-busy');
        saveButton.disabled = false;
        saveIcon.hidden = false;
        saveLabel.textContent = originalSaveLabel;
        setFeedback(`${serverText ? `${serverText} ` : ''}${guidance} Thông tin và tệp đã chọn vẫn còn trên trang này.`, 'error', { focus: true });
        return;
      }
      if (outcome === 'blocked') {
        lockAfterUnknown('Yêu cầu lưu bị từ chối do phiên đăng nhập hoặc quyền hiện tại.');
        return;
      }
      if (outcome === 'session') {
        lockAfterUnknown('Phiên đăng nhập đã hết hạn; yêu cầu không được tiếp tục tới biểu mẫu đăng ký.');
        return;
      }
      const message = response.ok ? 'Không xác định được trang đích sau khi lưu.'
        : serverMessage(await response.text()) || 'Không xác nhận được kết quả lưu.';
      lockAfterUnknown(message);
    } catch {
      lockAfterUnknown('Mất kết nối trước khi nhận được xác nhận kết quả lưu.');
    } finally {
      form.removeAttribute('aria-busy');
      saveButton.removeAttribute('aria-busy');
      if (!state.writeLocked && !saveButton.disabled) saveLabel.textContent = originalSaveLabel;
    }
  }

  function registrationFormData() {
    const data = new FormData(form);
    dateFields.forEach(control => {
      data.set(control.name, control.value ? parseVietnameseDate(control.value) : '');
    });
    return data;
  }

  form.noValidate = true;
  form.addEventListener('input', event => {
    const control = event.target;
    if (!control.matches('input, select, textarea')) return;
    if (dateFields.includes(control)) formatDateEntry(control);
    syncAppliedQrWithManualEdits(control);
    if (control === faceInput) refreshFaceRequirement();
    syncDateValidity(control);
    if (control.checkValidity()) clearFieldError(control);
    else if (state.validationAttempted) setFieldError(control, validationMessage(control));
    if (state.validationAttempted) updateSummary(collectInvalidControls());
  });
  form.addEventListener('change', event => {
    const control = event.target;
    if (!control.matches('input, select, textarea')) return;
    if (dateFields.includes(control)) formatDateEntry(control);
    syncAppliedQrWithManualEdits(control);
    if (control === faceInput) refreshFaceRequirement();
    syncDateValidity(control);
    if (control.checkValidity()) clearFieldError(control);
    else if (state.validationAttempted) setFieldError(control, validationMessage(control));
    if (state.validationAttempted) updateSummary(collectInvalidControls());
  });
  form.addEventListener('submit', submitRegistration);
  form.addEventListener('reset', () => {
    queueMicrotask(() => {
      Object.values(cameras).forEach(stopCamera);
      state.qrController?.abort();
      state.qrController = null;
      state.qrRequestId++;
      clearQrReview();
      clearAppliedQr();
      Object.keys(state.previewUrls).forEach(releasePreview);
      state.validationAttempted = false;
      errorSummary.hidden = true;
      errorGroups.replaceChildren();
       form.querySelectorAll('[aria-invalid="true"]').forEach(control => clearFieldError(control));
       setMediaStatus(qrStatus, 'Chọn ảnh QR hoặc mở camera. Bạn cũng có thể điền CCCD thủ công.');
       setMediaStatus(byId('faceStatus'), 'Chưa có ảnh. Camera chỉ bật khi bạn chọn mở; nếu không cấp quyền, hãy tải ảnh lên.');
       registrationImageStatus.textContent = 'Chưa chọn ảnh.';
       feedback.hidden = true;
       refreshFaceRequirement();
       dateFields.forEach(syncDateValidity);
    });
  });

  window.addEventListener('beforeunload', event => {
    if (!state.writeLocked) return;
    event.preventDefault();
    event.returnValue = '';
  });

  const navigation = byId('navigationDialog');
  byId('openNavigation')?.addEventListener('click', () => {
    if (navigation?.showModal) navigation.showModal();
  });
  byId('closeNavigation')?.addEventListener('click', () => navigation?.close());
  window.matchMedia('(min-width: 64rem)').addEventListener('change', event => {
    if (event.matches && navigation?.open) navigation.close();
  });
  document.querySelectorAll('.dashboard-logout').forEach(logout => logout.addEventListener('submit', event => {
    if (!window.confirm('Bạn muốn đăng xuất khỏi hệ thống?')) event.preventDefault();
  }));

  window.addEventListener('pagehide', () => {
    Object.values(cameras).forEach(stopCamera);
    state.qrController?.abort();
    Object.keys(state.previewUrls).forEach(releasePreview);
    faceCapture.value = '';
  });
}
