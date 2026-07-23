// ---------- 로그인(본인 선택) ----------

async function renderLoginScreen() {
    const statusEl = document.getElementById('login-status');
    const listEl = document.getElementById('login-member-list');
    statusEl.textContent = '명단을 불러오는 중...';
    listEl.innerHTML = '';
    try {
        const members = await api('/api/members');
        state.allMembers = members;
        statusEl.textContent = '';
        const officers = members.filter(m => m.officer);
        const regulars = members.filter(m => !m.officer);

        if (members.length === 0) {
            listEl.innerHTML = `<p class="text-xs font-bold text-gray-400 text-center py-8">등록된 부원이 없습니다.</p>`;
            return;
        }
        if (officers.length) {
            listEl.insertAdjacentHTML('beforeend', `<p class="text-xs font-black text-toss-subText mb-2">임원진</p>`);
            officers.forEach(m => listEl.insertAdjacentHTML('beforeend', loginMemberButton(m)));
        }
        if (regulars.length) {
            listEl.insertAdjacentHTML('beforeend', `<p class="text-xs font-black text-toss-subText mb-2 mt-5">부원</p>`);
            regulars.forEach(m => listEl.insertAdjacentHTML('beforeend', loginMemberButton(m)));
        }
    } catch (e) {
        statusEl.textContent = `명단을 불러오지 못했습니다: ${e.message}`;
        listEl.innerHTML = `<button onclick="renderLoginScreen()" class="w-full bg-white text-toss-blue font-black text-sm py-3 rounded-2xl border border-blue-100 shadow-sm">다시 시도</button>`;
    }
}

function loginMemberButton(m) {
    return `<button onclick="selectLoginMember(${m.id})" class="w-full flex items-center space-x-3 bg-white p-3.5 rounded-2xl shadow-sm border border-gray-100 active:scale-[0.98] transition-transform mb-2">
        <div class="w-9 h-9 rounded-full bg-blue-50 flex items-center justify-center text-toss-blue font-black text-sm shrink-0">${m.name.charAt(0)}</div>
        <span class="font-black text-toss-text">${m.name}</span>
        <span class="text-[10px] font-bold text-toss-subText bg-gray-50 px-2 py-1 rounded-full border border-gray-100">${m.part}</span>
        ${m.position ? `<span class="ml-auto text-[10px] font-black text-toss-blue bg-blue-50 px-2 py-1 rounded-full">${m.position}</span>` : ''}
    </button>`;
}

function selectLoginMember(id) {
    state.member = state.allMembers.find(m => m.id === id);
    localStorage.setItem('soripae_member', JSON.stringify(state.member));
    startApp();
}

function logout() {
    if (!confirm('다른 계정으로 전환할까요?')) return;
    localStorage.removeItem('soripae_member');
    location.reload();
}
