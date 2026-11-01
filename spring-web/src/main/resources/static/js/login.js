(() => {
    const form = document.getElementById('loginForm');
    const password = document.getElementById('password');
    const toggle = document.getElementById('togglePassword');
    const icon = document.getElementById('passwordIcon');
    const submit = document.getElementById('loginSubmit');
    const label = document.getElementById('submitLabel');
    const status = document.getElementById('submitStatus');
    let submitting = false;

    toggle.hidden = false;
    toggle.addEventListener('click', () => {
        const visible = password.type === 'password';
        password.type = visible ? 'text' : 'password';
        toggle.setAttribute('aria-pressed', String(visible));
        toggle.setAttribute('aria-label', visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
        icon.src = visible ? icon.dataset.eyeOff : icon.dataset.eye;
    });
    form.addEventListener('submit', event => {
        if (submitting) {
            event.preventDefault();
            return;
        }
        submitting = true;
        submit.disabled = true;
        form.setAttribute('aria-busy', 'true');
        label.textContent = 'Đang đăng nhập…';
        status.textContent = 'Đang gửi thông tin đăng nhập.';
        // Keep both credentials and CSRF enabled for the native POST.
    });
    window.addEventListener('pageshow', () => {
        submitting = false;
        submit.disabled = false;
        form.removeAttribute('aria-busy');
        label.textContent = 'Đăng nhập';
        status.textContent = '';
        password.type = 'password';
        toggle.setAttribute('aria-pressed', 'false');
        toggle.setAttribute('aria-label', 'Hiện mật khẩu');
        icon.src = icon.dataset.eye;
    });
    // No client-side credential storage: the existing redirect does not return a username.
    document.getElementById('login-error')?.focus();
})();
