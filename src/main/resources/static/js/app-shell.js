// ---------- 앱 진입 ----------

async function startApp() {
    document.getElementById('login-screen').classList.add('hidden');
    document.getElementById('app-root').classList.remove('hidden');
    document.getElementById('header-name').textContent = state.member.name;
    document.getElementById('header-avatar-img').textContent = state.member.name.charAt(0);
    document.getElementById('header-date').textContent = formatTodayKorean();
    document.getElementById('nav-admin').classList.toggle('hidden', !state.member.officer);
    switchTab('home');
    await Promise.all([loadFineSummary(), loadCalendar(), loadEventBanner()]);
}

function handleTabClick(targetTab) {
    if (targetTab === 'admin') {
        if (!state.isAdminAuthenticated) {
            openPwModal();
            return;
        }
        switchTab('admin');
        loadAdminQueue();
        loadAdminRoster();
        return;
    }
    switchTab(targetTab);
    if (targetTab === 'certify') { loadMemberHistory(); loadCertifySchedules(); }
    if (targetTab === 'home') { loadFineSummary(); loadCalendar(); loadEventBanner(); }
    if (targetTab === 'song') { loadMySongs(); }
}

function switchTab(tabName) {
    document.querySelectorAll('.tab-content').forEach(el => {
        el.classList.add('hidden');
        el.classList.remove('active');
    });
    document.querySelectorAll('.nav-btn').forEach(el => {
        el.classList.remove('text-toss-blue');
        el.classList.add('text-gray-400');
    });
    document.getElementById(`tab-${tabName}`).classList.remove('hidden');
    document.getElementById(`tab-${tabName}`).classList.add('active');
    document.getElementById(`nav-${tabName}`).classList.remove('text-gray-400');
    document.getElementById(`nav-${tabName}`).classList.add('text-toss-blue');
}

function switchAdminMenu(menu) {
    const buttons = { queue: document.getElementById('btn-admin-queue'), face: document.getElementById('btn-admin-face'), event: document.getElementById('btn-admin-event') };
    const views = { queue: document.getElementById('admin-view-queue'), face: document.getElementById('admin-view-face'), event: document.getElementById('admin-view-event') };

    Object.keys(views).forEach(key => {
        const active = key === menu;
        views[key].classList.toggle('hidden', !active);
        buttons[key].className = active
            ? "flex-1 py-2.5 text-xs font-black bg-white text-toss-text rounded-xl shadow-sm transition-all"
            : "flex-1 py-2.5 text-xs font-black text-gray-400 rounded-xl transition-all";
    });

    if (menu === 'queue') loadAdminQueue();
    else if (menu === 'face') loadAdminRoster();
    else loadAdminEvents();
}

function openPwModal() {
    document.getElementById('admin-pw-input').value = '';
    document.getElementById('overlay-heavy').classList.remove('hidden');
    document.getElementById('admin-pw-modal').classList.remove('hidden');
    setTimeout(() => {
        document.getElementById('overlay-heavy').style.opacity = '1';
        const modal = document.getElementById('admin-pw-modal');
        modal.style.opacity = '1';
        modal.style.transform = 'translate(-50%, -50%) scale(1)';
    }, 10);
}

function closePwModal() {
    document.getElementById('overlay-heavy').style.opacity = '0';
    const modal = document.getElementById('admin-pw-modal');
    modal.style.opacity = '0';
    modal.style.transform = 'translate(-50%, -50%) scale(0.95)';
    setTimeout(() => {
        document.getElementById('overlay-heavy').classList.add('hidden');
        modal.classList.add('hidden');
    }, 300);
}

async function verifyAdminPw() {
    const pw = document.getElementById('admin-pw-input').value;
    try {
        await api('/api/admin/auth', { method: 'POST', body: JSON.stringify({ password: pw }) });
        state.isAdminAuthenticated = true;
        document.getElementById('admin-lock-icon').classList.remove('text-gray-300');
        document.getElementById('admin-lock-icon').classList.add('text-toss-blue', 'fa-unlock');
        closePwModal();
        showToast('임원진 인증이 완료되었습니다.');
        switchTab('admin');
        loadAdminQueue();
        loadAdminRoster();
    } catch (e) {
        showToast('비밀번호가 틀렸습니다.');
    }
}

function closeAllModals() {
    document.querySelectorAll('.overlay-bg').forEach(el => el.style.opacity = '0');
    document.querySelectorAll('.bottom-sheet').forEach(sheet => {
        sheet.style.transform = 'translate(-50%, 100%)';
    });
    setTimeout(() => {
        document.querySelectorAll('.overlay-bg').forEach(el => el.classList.add('hidden'));
    }, 300);
}

function openSheet(id) {
    document.getElementById('overlay').classList.remove('hidden');
    setTimeout(() => {
        document.getElementById('overlay').style.opacity = '1';
        document.getElementById(id).style.transform = 'translate(-50%, 0)';
    }, 10);
}
