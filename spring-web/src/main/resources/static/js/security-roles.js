(() => {
  const form = document.querySelector('#roleEditorForm');
  if (!form) return;

  const choices = [...document.querySelectorAll('.role-choice')];
  const permissionInputs = [...form.querySelectorAll('input[name="permissions"]')];
  const enabledField = document.querySelector('#roleEnabledField');
  const enabledInput = document.querySelector('#roleEnabled');
  const enabledLockedValue = document.querySelector('#roleEnabledLockedValue');
  const roleId = document.querySelector('#roleId');
  const displayName = document.querySelector('#roleDisplayName');
  const code = document.querySelector('#roleCode');
  const prefix = document.querySelector('#rolePrefix');
  const description = document.querySelector('#roleDescription');
  const saveButton = document.querySelector('#saveRoleButton');
  const discardPrompt = document.querySelector('#roleDiscardPrompt');
  const managementRequired = new Set(['ACCOUNT_MANAGE', 'ROLE_MANAGE']);
  let selectedRole = null;
  let mode = 'create';
  let baseline = '';
  let pendingAction = null;
  let focusAfterPrompt = null;
  let submitting = false;

  const text = (selector, value) => { document.querySelector(selector).textContent = value; };
  const state = () => JSON.stringify({
    displayName: displayName.value,
    code: code.value,
    prefix: prefix.value,
    description: description.value,
    enabled: enabledInput.checked,
    permissions: permissionInputs.filter(input => input.checked).map(input => input.value).sort(),
  });
  const dirty = () => state() !== baseline;

  function updatePermissionCount() {
    const selected = permissionInputs.filter(input => input.checked).length;
    text('#selectedPermissionCount', String(selected));
    text('#permissionGroupCount', `trong ${permissionInputs.length} quyền hiện có`);
  }

  function updateDraftState() {
    const changed = dirty();
    const status = document.querySelector('#roleDraftState');
    status.classList.toggle('is-dirty', changed);
    status.textContent = changed ? 'Bản nháp chưa lưu' : mode === 'create' ? 'Sẵn sàng tạo vai trò' : 'Đang dùng cấu hình đã tải';
    saveButton.disabled = mode === 'edit' && !changed;
    document.querySelector('#roleFormHint').textContent = changed
      ? 'Rà soát thay đổi; khi lưu, cấu hình quyền mới sẽ được áp dụng.'
      : mode === 'edit' ? 'Chỉnh một mục để bật thao tác lưu.' : 'Kiểm tra mã, tiền tố và quyền trước khi lưu.';
    updatePermissionCount();
  }

  function markBaseline() {
    baseline = state();
    updateDraftState();
  }

  function configureManagementPermissions(isManagement) {
    document.querySelector('#managementRoleNotice').hidden = !isManagement;
    permissionInputs.forEach(input => {
      const isRequired = isManagement && managementRequired.has(input.value);
      if (isRequired) input.checked = true;
      input.disabled = isRequired;
      input.required = isRequired;
      input.closest('.permission-option').classList.toggle('is-required', isRequired);
      let fallback = input.parentElement.querySelector('[data-management-permission-fallback]');
      if (isRequired && !fallback) {
        fallback = document.createElement('input');
        fallback.type = 'hidden';
        fallback.name = 'permissions';
        fallback.value = input.value;
        fallback.dataset.managementPermissionFallback = 'true';
        input.parentElement.append(fallback);
      } else if (!isRequired && fallback) {
        fallback.remove();
      }
    });
  }

  function setEnabledState(role) {
    enabledField.hidden = false;
    enabledInput.checked = role.enabled;
    enabledInput.disabled = role.system || role.accountCount > 0;
    enabledLockedValue.disabled = !enabledInput.disabled;
    enabledLockedValue.value = String(role.enabled);
    text('#roleEnabledLabel', role.enabled ? 'Vai trò đang hoạt động' : 'Vai trò đã ngừng');
    text('#roleEnabledHint', role.system
      ? 'Vai trò hệ thống không thể ngừng sử dụng.'
      : role.accountCount > 0 ? 'Không thể ngừng vai trò khi còn tài khoản được gán.' : 'Bỏ chọn để ngừng cho phép gán vai trò này.');
  }

  function readRole(button) {
    return {
      id: button.dataset.roleId,
      code: button.dataset.code,
      prefix: button.dataset.prefix,
      displayName: button.dataset.displayName,
      description: button.dataset.description,
      enabled: button.dataset.enabled === 'true',
      system: button.dataset.system === 'true',
      accountCount: Number(button.dataset.accountCount),
      permissions: [...button.querySelectorAll('[data-role-permission]')].map(node => node.dataset.rolePermission),
    };
  }

  function loadCreate() {
    mode = 'create';
    roleId.disabled = true;
    roleId.value = '';
    displayName.value = '';
    code.value = '';
    prefix.value = '';
    description.value = '';
    [displayName, code, prefix].forEach(input => { input.readOnly = false; });
    enabledField.hidden = true;
    enabledInput.checked = true;
    enabledInput.disabled = false;
    enabledLockedValue.disabled = true;
    permissionInputs.forEach(input => { input.checked = false; input.disabled = false; input.required = false; input.closest('.permission-option').classList.remove('is-required'); });
    form.action = form.dataset.createAction;
    text('#roleEditorEyebrow', 'VAI TRÒ MỚI');
    text('#roleEditorTitle', 'Tạo vai trò');
    text('#roleEditorDescription', 'Đặt tên, mã nhận diện và quyền cần thiết cho nhóm người dùng.');
    text('#roleSelectionStatus', 'Đang tạo vai trò mới.');
    text('#saveRoleButton', 'Tạo vai trò');
    document.querySelector('#roleSystemBadge').hidden = true;
    document.querySelector('#roleSessionImpact').hidden = true;
    document.querySelector('#managementRoleNotice').hidden = true;
    choices.forEach(choice => choice.setAttribute('aria-pressed', 'false'));
    configureManagementPermissions(false);
    markBaseline();
  }

  function loadRole(button) {
    const role = readRole(button);
    selectedRole = button;
    mode = 'edit';
    roleId.disabled = false;
    roleId.value = role.id;
    displayName.value = role.displayName;
    code.value = role.code;
    prefix.value = role.prefix;
    description.value = role.description;
    [displayName, code, prefix].forEach(input => { input.readOnly = input !== displayName; });
    setEnabledState(role);
    permissionInputs.forEach(input => { input.checked = role.permissions.includes(input.value); });
    configureManagementPermissions(role.code === 'MANAGEMENT');
    form.action = form.dataset.updateAction;
    text('#roleEditorEyebrow', role.system ? 'VAI TRÒ HỆ THỐNG' : 'ĐANG CHỈNH SỬA');
    text('#roleEditorTitle', role.displayName);
    text('#roleEditorDescription', role.description || 'Rà soát cấu hình trước khi lưu thay đổi.');
    text('#roleSelectionStatus', `Đang chỉnh sửa vai trò ${role.displayName}.`);
    text('#saveRoleButton', 'Lưu thay đổi');
    document.querySelector('#roleSystemBadge').hidden = !role.system;
    document.querySelector('#roleSessionImpact').hidden = false;
    text('#roleSessionImpactText', role.accountCount > 0
      ? `Mọi lần cập nhật — kể cả chỉ sửa tên hoặc mô tả — đều thu hồi các phiên của ${role.accountCount} tài khoản đang được gán vai trò này. Họ cần đăng nhập lại.`
      : 'Mọi lần cập nhật — kể cả chỉ sửa tên hoặc mô tả — đều thu hồi phiên của các tài khoản đang được gán vai trò này. Người dùng liên quan cần đăng nhập lại.');
    choices.forEach(choice => choice.setAttribute('aria-pressed', String(choice === button)));
    markBaseline();
  }

  function showDiscardPrompt(action, trigger) {
    pendingAction = action;
    focusAfterPrompt = trigger;
    discardPrompt.hidden = false;
    document.querySelector('#discardRoleDraft').focus();
  }

  function requestAction(action, trigger) {
    if (dirty()) showDiscardPrompt(action, trigger);
    else action();
  }

  function revealActiveNavigation(container) {
    container?.querySelector('.nav-item[aria-current="page"]')?.scrollIntoView({ block: 'center' });
  }

  requestAnimationFrame(() => revealActiveNavigation(document.querySelector('.sidebar')));
  document.querySelector('#openNavigation')?.addEventListener('click', () => {
    requestAnimationFrame(() => revealActiveNavigation(document.querySelector('#navigationDialog')));
  });

  choices.forEach(button => button.addEventListener('click', () => {
    if (button === selectedRole && mode === 'edit') return;
    requestAction(() => loadRole(button), button);
  }));

  document.querySelector('#newRoleButton').addEventListener('click', event => {
    requestAction(() => { selectedRole = null; loadCreate(); displayName.focus(); }, event.currentTarget);
  });

  document.querySelector('#keepRoleDraft').addEventListener('click', () => {
    discardPrompt.hidden = true;
    (focusAfterPrompt || displayName).focus();
    pendingAction = null;
  });

  document.querySelector('#discardRoleDraft').addEventListener('click', () => {
    const action = pendingAction;
    discardPrompt.hidden = true;
    pendingAction = null;
    if (action) action();
  });

  document.querySelector('#cancelRoleChanges').addEventListener('click', event => {
    requestAction(() => selectedRole ? loadRole(selectedRole) : loadCreate(), event.currentTarget);
  });

  document.querySelector('#clearRolePermissions').addEventListener('click', () => {
    permissionInputs.filter(input => !input.disabled).forEach(input => { input.checked = false; });
    updateDraftState();
  });

  form.addEventListener('input', updateDraftState);
  form.addEventListener('change', updateDraftState);
  form.addEventListener('submit', event => {
    if (mode === 'edit' && !dirty()) {
      event.preventDefault();
      return;
    }
    submitting = true;
    form.setAttribute('aria-busy', 'true');
    document.querySelector('#roleSavingState').hidden = false;
    saveButton.disabled = true;
    saveButton.textContent = 'Đang lưu…';
  });

  window.addEventListener('beforeunload', event => {
    if (dirty() && !submitting) {
      event.preventDefault();
      event.returnValue = '';
    }
  });

  const totalAccounts = choices.reduce((sum, choice) => sum + Number(choice.dataset.accountCount), 0);
  text('#systemRolesTotal', String(choices.filter(choice => choice.dataset.system === 'true').length));
  text('#assignedAccountsTotal', String(totalAccounts));
  if (choices.length) loadRole(choices[0]);
  else {
    loadCreate();
    text('#roleDirectoryTitle', 'Danh sách vai trò');
  }
})();
