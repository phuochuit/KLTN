(() => {
    const navigationGroups = [...document.querySelectorAll('.main-nav .nav-group')];
    const closeOtherGroups = active => navigationGroups.forEach(group => {
        if (group !== active) group.removeAttribute('open');
    });
    navigationGroups.forEach(group => {
        let closeTimer = null;
        group.addEventListener('pointerenter', event => {
            if (event.pointerType === 'touch') return;
            if (closeTimer) window.clearTimeout(closeTimer);
            closeOtherGroups(group);
            group.setAttribute('open', '');
        });
        group.addEventListener('pointerleave', event => {
            if (event.pointerType === 'touch') return;
            closeTimer = window.setTimeout(() => {
                group.removeAttribute('open');
                closeTimer = null;
            }, 220);
        });
        group.addEventListener('toggle', () => {
            if (group.open) closeOtherGroups(group);
        });
        group.addEventListener('focusin', () => {
            closeOtherGroups(group);
            group.setAttribute('open', '');
        });
        group.addEventListener('focusout', () => window.setTimeout(() => {
            if (!group.contains(document.activeElement)) group.removeAttribute('open');
        }, 0));
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') navigationGroups.forEach(group => group.removeAttribute('open'));
    });

    const dialog = document.getElementById('app-dialog');
    if (!dialog || typeof dialog.showModal !== 'function') return;

    const title = document.getElementById('app-dialog-title');
    const message = document.getElementById('app-dialog-message');
    const cancel = document.getElementById('app-dialog-cancel');
    const confirm = document.getElementById('app-dialog-confirm');
    let resolveDialog = null;

    function finish(result) {
        if (!dialog.open) return;
        dialog.close();
        const resolve = resolveDialog;
        resolveDialog = null;
        if (resolve) resolve(result);
    }

    function openDialog(options) {
        if (resolveDialog) finish(false);
        title.textContent = options.title || 'Thông báo';
        message.textContent = String(options.message || '');
        confirm.textContent = options.confirmText || 'Đồng ý';
        cancel.textContent = options.cancelText || 'Hủy';
        cancel.hidden = !options.showCancel;
        dialog.classList.toggle('is-warning', Boolean(options.warning));
        dialog.showModal();
        window.setTimeout(() => (options.showCancel ? cancel : confirm).focus(), 0);
        return new Promise(resolve => { resolveDialog = resolve; });
    }

    cancel.addEventListener('click', () => finish(false));
    confirm.addEventListener('click', () => finish(true));
    dialog.addEventListener('cancel', event => { event.preventDefault(); finish(false); });
    dialog.addEventListener('click', event => {
        if (event.target === dialog) finish(false);
    });

    window.ParkingUi = {
        alert(message, heading = 'Thông báo') {
            return openDialog({ title: heading, message, confirmText: 'Đã hiểu', showCancel: false });
        },
        confirm(message, heading = 'Xác nhận thao tác') {
            return openDialog({ title: heading, message, confirmText: 'Xác nhận', showCancel: true, warning: true });
        }
    };

    // Các script cũ gọi alert() sẽ tự dùng hộp thoại mới.
    window.alert = message => window.ParkingUi.alert(message);

    document.addEventListener('submit', async event => {
        const form = event.target.closest('form[data-confirm]');
        if (!form || form.dataset.confirmed === 'true') {
            if (form) delete form.dataset.confirmed;
            return;
        }
        event.preventDefault();
        const accepted = await window.ParkingUi.confirm(
            form.dataset.confirm,
            form.dataset.confirmTitle || 'Xác nhận thao tác'
        );
        if (!accepted) return;
        form.dataset.confirmed = 'true';
        form.requestSubmit(event.submitter || undefined);
    });
})();
