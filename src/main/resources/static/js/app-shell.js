// ---------- 앱 진입 ----------

async function startApp() {
    document.getElementById('login-screen').classList.add('hidden');
    document.getElementById('app-root').classList.remove('hidden');
    document.getElementById('header-name').textContent = state.member.name;
    document.getElementById('header-avatar-img').textContent = state.member.name.charAt(0);
    document.getElementById('header-date').textContent = formatTodayKorean();
    document.getElementById('nav-admin').classList.toggle('hidden', !state.member.officer);
    switchTab('home');
    await Promise.all([loadFineSummary(), loadCalendar(), loadEventBanner(), restoreAdminAuth()]);
    // 사진 촬영 중 앱이 새로고침돼 인증 화면을 벗어났다면, 저장해둔 사진으로 복원한다.
    await restorePendingPhoto();
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
    const buttons = { queue: document.getElementById('btn-admin-queue'), face: document.getElementById('btn-admin-face'), event: document.getElementById('btn-admin-event'), song: document.getElementById('btn-admin-song') };
    const views = { queue: document.getElementById('admin-view-queue'), face: document.getElementById('admin-view-face'), event: document.getElementById('admin-view-event'), song: document.getElementById('admin-view-song') };

    Object.keys(views).forEach(key => {
        const active = key === menu;
        views[key].classList.toggle('hidden', !active);
        // className을 통째로 덮어쓰면 숨겨둔 버튼(예: 곡 관리)의 hidden이 지워져 다시 나타난다.
        // 원래 숨김 상태였으면 유지한다.
        const wasHidden = buttons[key].classList.contains('hidden');
        buttons[key].className = active
            ? "py-2.5 text-xs font-black bg-white text-toss-text rounded-xl shadow-sm transition-all"
            : "py-2.5 text-xs font-black text-gray-400 rounded-xl transition-all";
        if (wasHidden) buttons[key].classList.add('hidden');
    });

    if (menu === 'queue') loadAdminQueue();
    else if (menu === 'face') loadAdminRoster();
    else if (menu === 'event') loadAdminEvents();
    else loadAdminSongs();
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

function markAdminAuthenticated() {
    state.isAdminAuthenticated = true;
    const icon = document.getElementById('admin-lock-icon');
    icon.classList.remove('text-gray-300');
    icon.classList.add('text-toss-blue', 'fa-unlock');
}

/** 저장해둔 임원 비밀번호로 조용히 재인증한다. 새로고침(또는 사진 촬영 후 복귀)해도 임원진 탭에서
 *  매번 비밀번호를 다시 입력하지 않도록 하기 위함. 서버가 최종 확인하므로, 비번이 바뀌었으면
 *  저장분을 폐기하고 다시 묻는다. */
async function restoreAdminAuth() {
    const pw = localStorage.getItem('soripae_admin_pw');
    if (!pw) return;
    try {
        await api('/api/admin/auth', { method: 'POST', body: JSON.stringify({ password: pw }) });
        markAdminAuthenticated();
    } catch (e) {
        localStorage.removeItem('soripae_admin_pw');
    }
}

async function verifyAdminPw() {
    const pw = document.getElementById('admin-pw-input').value;
    try {
        await api('/api/admin/auth', { method: 'POST', body: JSON.stringify({ password: pw }) });
        markAdminAuthenticated();
        localStorage.setItem('soripae_admin_pw', pw);
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
