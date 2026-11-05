(() => {
    const area = document.getElementById('auth-toast-area');
    if (!area) return;

    const platformLabel = { WINFORM: 'WinForm', WEB: 'Web' };
    let cursor = null;
    let timer = null;
    let running = false;

    function clock(ms) {
        if (!ms) return '';
        const moment = new Date(ms);
        const hh = String(moment.getHours()).padStart(2, '0');
        const mm = String(moment.getMinutes()).padStart(2, '0');
        return ' · ' + hh + ':' + mm;
    }

    function appendToast(text, outgoing) {
        const toast = document.createElement('div');
        toast.className = 'auth-toast' + (outgoing ? ' auth-toast-out' : '');
        toast.textContent = text;
        area.appendChild(toast);
        requestAnimationFrame(() => toast.classList.add('show'));
        window.setTimeout(() => {
            toast.classList.remove('show');
            window.setTimeout(() => toast.remove(), 320);
        }, 4000);
    }

    function describe(item) {
        const label = platformLabel[item.platform] || 'Web';
        const parts = [];
        if (item.position) parts.push(item.position);
        parts.push(item.fullName || item.username);
        if (item.shiftName) parts.push(item.shiftName);
        const verb = item.action === 'LOGOUT' ? ' đã đăng xuất khỏi ' : ' đã đăng nhập ';
        return parts.join(' - ') + verb + label + clock(item.occurredAt);
    }

    function present(items) {
        // Gộp theo từng người: nếu vừa đăng xuất rồi đăng nhập lại thì chỉ báo trạng thái mới nhất.
        const latest = new Map();
        for (const item of items) latest.set(item.platform + '|' + item.username, item);
        [...latest.values()]
            .sort((a, b) => a.id - b.id)
            .forEach(item => appendToast(describe(item), item.action === 'LOGOUT'));
    }

    async function poll() {
        if (running || document.hidden) return;
        running = true;
        try {
            const url = cursor === null
                ? '/auth-activity'
                : '/auth-activity?afterId=' + encodeURIComponent(cursor);
            const response = await fetch(url, {
                credentials: 'same-origin',
                cache: 'no-store',
                headers: { Accept: 'application/json' }
            });
            // Phiên đã bị kết thúc từ nơi khác: đưa trình duyệt về trang đăng nhập ngay.
            if (response.redirected
                && new URL(response.url, window.location.origin).pathname === '/login') {
                const target = new URL(response.url, window.location.origin);
                if (!target.search) target.search = '?ended=1';
                window.location.assign(target.toString());
                return;
            }
            if (response.status === 401) {
                window.location.assign('/login?ended=1');
                return;
            }
            if (!response.ok) return;
            const data = await response.json();
            if (typeof data.cursor === 'number') cursor = data.cursor;
            const events = data.events || [];
            if (events.length) present(events);
        } catch (_) {
            // Mạng tạm thời gián đoạn: giữ mốc cũ và thử lại ở nhịp sau.
        } finally {
            running = false;
        }
    }

    window.ParkingToast = {
        show(text, tone) { appendToast(String(text || ''), tone === 'out'); }
    };

    poll();
    timer = window.setInterval(poll, 5000);
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            if (timer) { window.clearInterval(timer); timer = null; }
            return;
        }
        if (timer) return;
        // Quay lại tab: bỏ qua sự kiện đã xảy ra trong lúc tab bị ẩn để không báo muộn.
        cursor = null;
        poll();
        timer = window.setInterval(poll, 5000);
    });
})();
