(() => {
  const pendingSaveNoticeKey = 'managementCoreSaveNotice';
  let pendingSaveNotice = null;
  try { pendingSaveNotice = sessionStorage.getItem(pendingSaveNoticeKey); } catch { }
  const mainContent = document.querySelector('#coreMain');
  if (pendingSaveNotice && mainContent) {
    const notice = document.createElement('p');
    notice.className = 'notice';
    notice.setAttribute('role', 'status');
    notice.textContent = pendingSaveNotice;
    mainContent.prepend(notice);
    try { sessionStorage.removeItem(pendingSaveNoticeKey); } catch { }
  }
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async (input, init = {}) => {
    const response = await nativeFetch(input, init);
    const method = (init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
    if (method !== 'POST') return response;
    const requestUrl = new URL(input instanceof Request ? input.url : input, location.href);
    if (!['/members', '/vehicles'].includes(requestUrl.pathname)) return response;
    const responsePath = new URL(response.url, location.href).pathname;
    const successMessage = requestUrl.pathname === '/members' && responsePath === '/households'
      ? 'Đã lưu cư dân.'
      : requestUrl.pathname === '/vehicles' && responsePath === '/vehicles' ? 'Đã lưu phương tiện.' : null;
    if (response.redirected && response.ok && successMessage) {
      try { sessionStorage.setItem(pendingSaveNoticeKey, successMessage); } catch { }
    }
    return response;
  };
  const navigation = document.querySelector('#navigationDialog');
  document.querySelector('#openNavigation')?.addEventListener('click', () => navigation?.showModal());
  document.querySelector('#closeNavigation')?.addEventListener('click', () => navigation?.close());
  matchMedia('(min-width: 64rem)').addEventListener('change', event => { if (event.matches && navigation?.open) navigation.close(); });
  document.querySelectorAll('.dashboard-logout').forEach(form => form.addEventListener('submit', event => {
    if (!window.confirm('Bạn muốn đăng xuất khỏi hệ thống?')) event.preventDefault();
  }));
  document.querySelectorAll('.table-search').forEach(input => {
    const body = document.getElementById(input.dataset.target);
    const rows = [...body.querySelectorAll('tr')];
    const result = input.closest('section').querySelector('.table-result');
    const controls = document.createElement('div');
    controls.className = 'management-actions';
    const previous = document.createElement('button'), next = document.createElement('button');
    previous.type = next.type = 'button'; previous.textContent = 'Trang trước'; next.textContent = 'Trang sau';
    controls.append(previous, next); result.after(controls);
    let page = 0;
    function render() {
      const term = input.value.trim().toLocaleLowerCase('vi');
      const matches = rows.filter(row => row.textContent.toLocaleLowerCase('vi').includes(term));
      const pages = Math.max(1, Math.ceil(matches.length / 10));
      page = Math.min(page, pages - 1);
      const visible = new Set(matches.slice(page * 10, page * 10 + 10));
      rows.forEach(row => row.hidden = !visible.has(row));
      previous.disabled = page === 0; next.disabled = page >= pages - 1;
      result.textContent = matches.length ? `${matches.length} kết quả trong danh sách đã tải · Trang ${page + 1}/${pages}` : (term ? 'Không có kết quả phù hợp.' : 'Chưa có dữ liệu.');
    }
    input.addEventListener('input', () => { page = 0; render(); });
    previous.addEventListener('click', () => { page--; render(); });
    next.addEventListener('click', () => { page++; render(); });
    render();
  });
  const form = document.querySelector('#limitForm');
  if (form) {
    const inputs = [...form.querySelectorAll('input[type=number]')];
    const original = inputs.map(input => input.value);
    const status = document.querySelector('#limitDraft');
    const draftState = document.querySelector('#limitDraftState');
    const draftValues = document.querySelector('#limitDraftValues');
    const actionHint = document.querySelector('#limitActionHint');
    const save = document.querySelector('#saveLimitButton');
    const reset = document.querySelector('#resetLimitButton');
    let submitting = false;
    draftState.hidden = false;
    reset.hidden = false;
    function updateDraft(reverted = false) {
      const changed = inputs.some((input, index) => input.value !== original[index]);
      status.dataset.state = changed ? 'draft' : reverted ? 'reverted' : 'saved';
      status.textContent = changed ? 'Có thay đổi chưa lưu' : reverted ? 'Đã khôi phục giá trị đã tải' : 'Đang dùng giá trị đã lưu';
      draftValues.hidden = !changed;
      draftValues.textContent = changed
        ? `Bản nháp: ${inputs[0].value || 'chưa nhập'} xe hai bánh / hộ · ${inputs[1].value || 'chưa nhập'} ô tô / hộ`
        : '';
      actionHint.hidden = false;
      actionHint.textContent = changed
        ? 'Kiểm tra bản nháp, rồi lưu thay đổi hoặc khôi phục về giá trị đã tải.'
        : 'Thay đổi ít nhất một hạn mức để bật thao tác lưu và khôi phục.';
      save.disabled = !changed;
      reset.disabled = !changed;
      return changed;
    }
    form.addEventListener('input', updateDraft);
    form.addEventListener('change', updateDraft);
    reset.addEventListener('click', () => {
      inputs.forEach((input, index) => { input.value = original[index]; });
      updateDraft(true);
      inputs[0]?.focus();
    });
    form.addEventListener('submit', event => {
      if (!updateDraft()) event.preventDefault();
      else submitting = true;
    });
    window.addEventListener('beforeunload', event => {
      if (updateDraft() && !submitting) { event.preventDefault(); event.returnValue = ''; }
    });
    updateDraft();
  }
})();
