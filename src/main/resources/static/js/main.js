// ---------- 시작 ----------

function initScheduleTimeSelects() {
    const hourSel = document.getElementById('sched-hour');
    const minuteSel = document.getElementById('sched-minute');
    for (let h = 0; h < 24; h++) {
        const v = String(h).padStart(2, '0');
        hourSel.insertAdjacentHTML('beforeend', `<option value="${v}" ${v === '15' ? 'selected' : ''}>${v}시</option>`);
    }
    ['00', '10', '20', '30', '40', '50'].forEach(m => {
        minuteSel.insertAdjacentHTML('beforeend', `<option value="${m}">${m}분</option>`);
    });
}

window.addEventListener('DOMContentLoaded', async () => {
    initScheduleTimeSelects();
    const saved = localStorage.getItem('soripae_member');
    if (saved) {
        state.member = JSON.parse(saved);
        await startApp();
    } else {
        await renderLoginScreen();
    }
});

if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'));
}
