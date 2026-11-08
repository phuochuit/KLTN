const navigation = document.querySelector('#navigationDialog');
const openNavigation = document.querySelector('#openNavigation');
const closeNavigation = document.querySelector('#closeNavigation');
const secondaryFilters = document.querySelector('.sessions-secondary-filters');
const compactFilters = matchMedia('(max-width: 47.999rem)');

function syncSecondaryFilters() {
  if (secondaryFilters) secondaryFilters.open = !compactFilters.matches;
}
syncSecondaryFilters();
compactFilters.addEventListener('change', syncSecondaryFilters);

openNavigation?.addEventListener('click', () => {
  navigation?.showModal();
  closeNavigation?.focus();
});
closeNavigation?.addEventListener('click', () => navigation?.close());

document.querySelectorAll('.dashboard-logout').forEach(form => form.addEventListener('submit', async event => {
  event.preventDefault();
  const question = 'Bạn muốn đăng xuất khỏi hệ thống?';
  const accepted = window.ParkingUi ? await window.ParkingUi.confirm(question, 'Đăng xuất') : window.confirm(question);
  if (accepted) form.submit();
}));

/* ---------- Đối chiếu ảnh xe vào / ra ---------- */
const evidenceDialog = document.querySelector('#evidenceDialog');
const evidenceZoomDialog = document.querySelector('#evidenceZoom');

const FACE_RESULT_TEXT = {
  PASS: 'Khuôn mặt trùng khớp với ảnh đăng ký',
  REVIEW: 'Khuôn mặt cần được kiểm tra lại',
  REJECT: 'Khuôn mặt không trùng khớp',
  FAIL: 'Khuôn mặt không trùng khớp',
  NOT_EVALUATED: 'Chưa đối chiếu khuôn mặt'
};

const MATCH_TEXT = {
  MATCH: 'Thông tin xe vào và xe ra trùng khớp.',
  REVIEW: 'Cần kiểm tra lại thông tin xe vào và xe ra.',
  NO_MATCH: 'Thông tin xe vào và xe ra không khớp. Vui lòng kiểm tra lại.'
};

function evidenceTone(status) {
  if (status === 'MATCH') return 'is-match';
  if (status === 'NO_MATCH') return 'is-mismatch';
  return 'is-review';
}

function faceResultText(decision) {
  if (!decision) return FACE_RESULT_TEXT.NOT_EVALUATED;
  return FACE_RESULT_TEXT[String(decision).toUpperCase()] || FACE_RESULT_TEXT.NOT_EVALUATED;
}

function resetEvidenceCards() {
  document.querySelectorAll('[data-evidence-card]').forEach(card => {
    card.classList.remove('has-image');
    const img = card.querySelector('img');
    if (img) {
      img.removeAttribute('src');
      img.hidden = true;
    }
    const empty = card.querySelector('.evidence-empty');
    if (empty) empty.hidden = false;
    const meta = card.querySelector('[data-evidence-meta]');
    if (meta) meta.textContent = '—';
  });
}

function setEvidenceCard(key, base64, metaText) {
  const card = document.querySelector(`[data-evidence-card="${key}"]`);
  if (!card) return;
  const img = card.querySelector('img');
  const empty = card.querySelector('.evidence-empty');
  const hasImage = Boolean(base64);
  if (img && empty) {
    if (hasImage) {
      img.src = `data:image/jpeg;base64,${base64}`;
      img.hidden = false;
      empty.hidden = true;
      card.classList.add('has-image');
    } else {
      img.removeAttribute('src');
      img.hidden = true;
      empty.hidden = false;
      card.classList.remove('has-image');
    }
  }
  const meta = card.querySelector('[data-evidence-meta]');
  if (meta) meta.textContent = metaText || '—';
}

async function loadEvidence(sessionId) {
  const summary = document.querySelector('#evidenceSummary');
  if (!summary) return;
  try {
    const response = await fetch(`/admin-data/sessions/${encodeURIComponent(sessionId)}/evidence-comparison`, {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin'
    });
    const data = await response.json().catch(() => null);
    if (!response.ok || !data) throw new Error('unavailable');

    const status = String(data.matchStatus || 'REVIEW').toUpperCase();
    summary.className = `evidence-summary ${evidenceTone(status)}`;
    summary.textContent = MATCH_TEXT[status] || MATCH_TEXT.REVIEW;
    if (data.manualOverride) summary.textContent += ' Lượt xe đã được xử lý thủ công.';

    const entryPlate = data.entryPlate || 'Chưa đọc được biển số';
    const entryType = data.entryVehicleType ? ` · ${data.entryVehicleType}` : '';
    setEvidenceCard('entryVehicle', data.entryVehicleImageBase64, `${entryPlate}${entryType}`);

    const exitPlate = data.exitPlate || 'Chưa có biển số ra';
    const exitNote = !data.exitPlate
      ? 'Xe chưa ra bãi'
      : (data.plateMatched ? 'Trùng biển số lúc vào' : 'Khác biển số lúc vào');
    const exitType = data.exitVehicleType ? ` · ${data.exitVehicleType}` : '';
    setEvidenceCard('exitVehicle', data.exitVehicleImageBase64, `${exitPlate}${exitType} · ${exitNote}`);

    setEvidenceCard('entryFace', data.entryFaceImageBase64, faceResultText(data.entryFaceDecision));
    setEvidenceCard('exitFace', data.exitFaceImageBase64, faceResultText(data.exitFaceDecision));
  } catch (error) {
    summary.className = 'evidence-summary is-mismatch';
    summary.textContent = 'Chưa tải được ảnh của lượt xe này. Vui lòng đóng hộp thoại và thử lại.';
  }
}

function openEvidenceDialog(button) {
  if (!evidenceDialog) return;
  const sessionId = button.dataset.sessionId;
  const plate = button.dataset.sessionPlate || '';
  const title = document.querySelector('#evidenceTitle');
  const subtitle = document.querySelector('#evidenceSubtitle');
  const summary = document.querySelector('#evidenceSummary');
  if (title) title.textContent = plate ? `Ảnh vào / ra · ${plate}` : 'Ảnh vào / ra';
  if (subtitle) subtitle.textContent = `Lượt xe #${sessionId}`;
  if (summary) {
    summary.className = 'evidence-summary';
    summary.textContent = 'Đang tải ảnh…';
  }
  resetEvidenceCards();
  if (!evidenceDialog.open) evidenceDialog.showModal();
  loadEvidence(sessionId);
}

document.querySelectorAll('[data-session-evidence]').forEach(button => {
  button.addEventListener('click', () => openEvidenceDialog(button));
});

document.querySelectorAll('[data-evidence-close]').forEach(button => {
  button.addEventListener('click', () => evidenceDialog?.close());
});

document.querySelectorAll('[data-evidence-card]').forEach(card => {
  const thumb = card.querySelector('[data-evidence-zoom]');
  thumb?.addEventListener('click', () => {
    const img = card.querySelector('img');
    if (!img || img.hidden || !img.getAttribute('src') || !evidenceZoomDialog) return;
    const caption = document.querySelector('#evidenceZoomCaption');
    const zoomImg = document.querySelector('#evidenceZoomImage');
    if (caption) caption.textContent = card.querySelector('strong')?.textContent || 'Ảnh ghi nhận';
    if (zoomImg) zoomImg.src = img.src;
    evidenceZoomDialog.showModal();
  });
});

document.querySelector('[data-evidence-zoom-close]')?.addEventListener('click', () => evidenceZoomDialog?.close());

[evidenceDialog, evidenceZoomDialog].forEach(dialog => {
  dialog?.addEventListener('click', event => {
    if (event.target === dialog) dialog.close();
  });
});

