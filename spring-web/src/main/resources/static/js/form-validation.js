(() => {
    let messageOpen = false;

    function fieldLabel(field) {
        const byFor = field.id
            ? document.querySelector(`label[for="${CSS.escape(field.id)}"]`)
            : null;
        const wrapping = field.closest('label');
        const raw = byFor?.textContent || wrapping?.textContent
            || field.getAttribute('aria-label') || field.name || 'trường này';
        return raw.replace(/\s+/g, ' ').trim().replace(/[.:：]+$/, '');
    }

    function validationMessage(field) {
        const label = fieldLabel(field);
        const validity = field.validity;
        if (validity.valueMissing) {
            const action = field.matches('select, input[type="checkbox"], input[type="radio"]')
                ? 'chọn' : 'nhập';
            return `Vui lòng ${action} ${label}.`;
        }
        if (validity.tooShort) {
            return `${label} cần có ít nhất ${field.minLength} ký tự.`;
        }
        if (validity.tooLong) {
            return `${label} không được vượt quá ${field.maxLength} ký tự.`;
        }
        if (validity.rangeUnderflow) {
            return `${label} phải từ ${field.min} trở lên.`;
        }
        if (validity.rangeOverflow) {
            return `${label} không được vượt quá ${field.max}.`;
        }
        return `${label} chưa đúng định dạng. Vui lòng kiểm tra lại.`;
    }

    function showStandaloneDialog(message) {
        let dialog = document.getElementById('validation-dialog');
        if (!dialog) {
            const style = document.createElement('style');
            style.textContent = `
                .validation-dialog { width:min(92vw,430px); padding:0; border:0; border-radius:14px;
                    color:#172033; background:#fff; box-shadow:0 24px 70px #07182f55; }
                .validation-dialog::backdrop { background:#0f172a99; }
                .validation-dialog-body { padding:24px 26px 12px; }
                .validation-dialog h2 { margin:0 0 10px; color:#17365d; font:700 21px/1.3 Segoe UI,Arial,sans-serif; }
                .validation-dialog p { margin:0; font:16px/1.55 Segoe UI,Arial,sans-serif; }
                .validation-dialog-actions { display:flex; justify-content:flex-end; padding:14px 26px 22px; }
                .validation-dialog button { min-width:105px; min-height:42px; margin:0; padding:8px 18px;
                    border:0; border-radius:8px; color:#fff; background:#2e74b5; font:700 15px Segoe UI,Arial,sans-serif; cursor:pointer; }
                .validation-dialog button:focus-visible { outline:3px solid #7db7e8; outline-offset:2px; }
            `;
            document.head.appendChild(style);
            dialog = document.createElement('dialog');
            dialog.id = 'validation-dialog';
            dialog.className = 'validation-dialog';
            dialog.innerHTML = '<div class="validation-dialog-body"><h2>Thông tin chưa đầy đủ</h2>'
                + '<p></p></div><div class="validation-dialog-actions"><button type="button">Đã hiểu</button></div>';
            document.body.appendChild(dialog);
        }

        dialog.querySelector('p').textContent = message;
        const button = dialog.querySelector('button');
        return new Promise(resolve => {
            const finish = () => {
                if (dialog.open) dialog.close();
                resolve();
            };
            button.onclick = finish;
            dialog.oncancel = event => { event.preventDefault(); finish(); };
            dialog.showModal();
            window.setTimeout(() => button.focus(), 0);
        });
    }

    async function showMessage(field) {
        if (messageOpen) return;
        messageOpen = true;
        const message = validationMessage(field);
        try {
            if (window.ParkingUi?.alert) {
                await window.ParkingUi.alert(message, 'Thông tin chưa đầy đủ');
            } else {
                await showStandaloneDialog(message);
            }
        } finally {
            messageOpen = false;
            field.focus({ preventScroll: false });
        }
    }

    document.addEventListener('invalid', event => {
        event.preventDefault();
        void showMessage(event.target);
    }, true);
})();
