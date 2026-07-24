// ---------- 임원진: 사진 승인 대기열 ----------

async function loadAdminQueue() {
    try {
        const list = await api('/api/attendance-records/pending');
        renderAdminRequests(list);
    } catch (e) {
        showToast(e.message);
    }
}

function renderAdminRequests(list) {
    const el = document.getElementById('admin-request-list');
    el.innerHTML = '';
    document.getElementById('pending-count').textContent = `${list.length}건`;

    if (list.length === 0) {
        document.getElementById('admin-empty-state').classList.remove('hidden');
        return;
    }
    document.getElementById('admin-empty-state').classList.add('hidden');

    list.forEach(req => {
        const autoLate = req.suggestedLateMinutes || 0;
        const photoSrc = req.photoUrl ? (req.photoUrl.startsWith('http') ? req.photoUrl : API_BASE + req.photoUrl) : '';
        el.insertAdjacentHTML('beforeend', `
            <div class="bg-toss-card rounded-[28px] overflow-hidden shadow-toss border border-gray-100" id="req-card-${req.id}">
                <div class="h-48 bg-gray-100 relative">
                    <img src="${photoSrc}" class="w-full h-full object-cover">
                    <div class="absolute bottom-3 left-3 bg-white/90 backdrop-blur-md px-3.5 py-2.5 rounded-xl text-xs font-black flex flex-col space-y-1 shadow-sm border border-gray-100/50">
                        <span class="text-toss-subText">예정 스케줄: ${formatTime(req.scheduledStartTime)}</span>
                        <span class="${autoLate > 0 ? 'text-toss-red' : 'text-toss-green'}">제출 시각: ${formatDateTime(req.submittedAt)}</span>
                    </div>
                </div>
                <div class="p-6">
                    <div class="flex justify-between items-center mb-4">
                        <span class="text-xl font-black text-toss-text">${req.memberName}</span>
                    </div>
                    <div class="bg-red-50/50 p-4 rounded-2xl flex items-center justify-between mb-5 border border-red-100/50">
                        <label class="text-sm font-black text-toss-red flex flex-col">
                            <span>지각 (분) 계산기</span>
                            <span class="text-[10px] font-bold text-red-400 mt-0.5">업로드 시간 기준 자동 계산됨</span>
                        </label>
                        <div class="flex items-center bg-white px-3 py-2 rounded-xl shadow-sm border border-red-100">
                            <input type="number" id="late-min-${req.id}" min="0" value="${autoLate}" class="w-12 text-right bg-transparent text-toss-red font-black text-lg outline-none">
                            <span class="text-xs font-black text-toss-red ml-1">분</span>
                        </div>
                    </div>
                    <div class="flex space-x-3">
                        <button onclick="handleReject(${req.id})" class="flex-[1] py-4 bg-gray-100 text-toss-subText font-black rounded-2xl active:bg-gray-200 transition-colors">반려</button>
                        <button onclick="handleApprove(${req.id})" class="flex-[2] py-4 bg-toss-blue text-white font-black rounded-2xl shadow-[0_4px_14px_rgba(49,130,246,0.3)] active:bg-blue-600 transition-colors">승인 확정</button>
                    </div>
                </div>
            </div>
        `);
    });
}

async function handleApprove(id) {
    const lateMinutes = parseInt(document.getElementById(`late-min-${id}`).value) || 0;
    try {
        await api(`/api/attendance-records/${id}/approve`, { method: 'PATCH', body: JSON.stringify({ lateMinutes }) });
        showToast('승인 완료');
        await loadAdminQueue();
    } catch (e) {
        showToast(e.message);
    }
}

async function handleReject(id) {
    try {
        await api(`/api/attendance-records/${id}/reject`, { method: 'PATCH' });
        showToast('반려 처리됨');
        await loadAdminQueue();
    } catch (e) {
        showToast(e.message);
    }
}

// ---------- 임원진: 대면 출석 체크 ----------

async function loadAdminRoster() {
    try {
        const [members, todayRecords, todaySchedules] = await Promise.all([
            api('/api/members'),
            api(`/api/attendance-records?date=${todayIso()}`),
            api(`/api/schedules?date=${todayIso()}`),
        ]);
        state.allMembers = members;
        state.todayRecordByMember = {};
        todayRecords
            .filter(r => r.status !== 'REJECTED')
            .forEach(r => { state.todayRecordByMember[r.memberId] = r; });
        state.todayScheduleByMember = {};
        todaySchedules.forEach(s => { state.todayScheduleByMember[s.memberId] = s; });
        renderAdminRoster();
    } catch (e) {
        showToast(e.message);
    }
}

function renderAdminRoster() {
    const el = document.getElementById('admin-roster-list');
    el.innerHTML = '';

    const registered = state.allMembers.filter(m => state.todayScheduleByMember[m.id]);
    const notRegistered = state.allMembers.filter(m => !state.todayScheduleByMember[m.id]);

    if (registered.length > 0) {
        el.insertAdjacentHTML('beforeend', `<h4 class="text-xs font-black text-toss-blue px-1 mb-2">오늘 등록 (${registered.length}명)</h4>`);
        registered.forEach(m => renderRosterCard(el, m));
    }
    if (notRegistered.length > 0) {
        el.insertAdjacentHTML('beforeend', `<h4 class="text-xs font-black text-gray-400 px-1 mb-2 ${registered.length > 0 ? 'mt-5' : ''}">미등록 (${notRegistered.length}명)</h4>`);
        notRegistered.forEach(m => renderRosterCard(el, m));
    }
}

function renderRosterCard(el, m) {
    const record = state.todayRecordByMember[m.id];
    const schedule = state.todayScheduleByMember[m.id];
    el.insertAdjacentHTML('beforeend', record ? rosterLockedCard(m, record) : rosterActionCard(m, schedule));
}

function rosterActionCard(m, schedule) {
    const scheduleBadge = schedule
        ? `<span class="text-[10px] font-black text-toss-blue bg-blue-50 px-2 py-0.5 rounded-md ml-1">${formatTime(schedule.startTime)}</span>`
        : '';
    return `
        <div class="p-4 bg-gray-50 border border-gray-100 rounded-2xl flex flex-col space-y-3">
            <div class="flex justify-between items-center">
                <div class="flex items-center space-x-3 cursor-pointer active:scale-95 transition-transform" onclick="openMemberDetail(${m.id})">
                    <div class="w-10 h-10 bg-white shadow-sm rounded-full flex items-center justify-center text-sm font-black text-toss-blue border border-gray-100">${m.name.charAt(0)}</div>
                    <span class="font-black text-toss-text text-base">${m.name}<span class="text-[10px] font-bold text-toss-subText ml-1">${m.part}</span>${scheduleBadge} <i class="fa-solid fa-chevron-right text-[10px] text-gray-400 ml-1"></i></span>
                </div>
                <div class="flex space-x-1.5">
                    <button onclick="setRosterStatus(${m.id}, 'PRESENT')" id="btn-att-${m.id}" class="px-3 py-2 text-xs font-black rounded-xl bg-white border border-gray-200 text-gray-400 transition-colors">출석</button>
                    <button onclick="setRosterStatus(${m.id}, 'LATE')" id="btn-late-${m.id}" class="px-3 py-2 text-xs font-black rounded-xl bg-white border border-gray-200 text-gray-400 transition-colors">지각</button>
                    <button onclick="setRosterStatus(${m.id}, 'ABSENT')" id="btn-abs-${m.id}" class="px-3 py-2 text-xs font-black rounded-xl bg-white border border-gray-200 text-gray-400 transition-colors">결석</button>
                </div>
            </div>
            <div id="late-input-wrap-${m.id}" class="hidden flex items-center justify-end space-x-2 pt-2 border-t border-gray-200">
                <span class="text-xs font-bold text-toss-red">지각 시간:</span>
                <input type="number" id="roster-late-min-${m.id}" placeholder="0" class="w-16 p-2 text-right bg-white border border-red-200 rounded-lg outline-none font-black text-toss-red text-sm focus:border-toss-red">
                <span class="text-xs font-bold text-toss-red">분</span>
                <button onclick="saveLate(${m.id})" class="ml-2 px-3 py-1.5 bg-red-50 text-toss-red border border-red-100 rounded-lg text-xs font-black active:scale-95">확인</button>
            </div>
        </div>
    `;
}

function rosterLockedCard(m, record) {
    const meta = statusMeta(record.status, record.lateMinutes);
    const methodLabel = record.method === 'PHOTO' ? '사진 인증' : '대면 체크';
    return `
        <div class="p-4 bg-gray-50 border border-gray-100 rounded-2xl flex items-center justify-between opacity-80">
            <div class="flex items-center space-x-3 cursor-pointer active:scale-95 transition-transform" onclick="openMemberDetail(${m.id})">
                <div class="w-10 h-10 bg-white shadow-sm rounded-full flex items-center justify-center text-sm font-black text-toss-blue border border-gray-100">${m.name.charAt(0)}</div>
                <span class="font-black text-toss-text text-base">${m.name}<span class="text-[10px] font-bold text-toss-subText ml-1">${m.part}</span></span>
            </div>
            <div class="flex items-center space-x-2">
                <span class="text-[10px] font-black px-3 py-1.5 rounded-lg border whitespace-nowrap ${meta.badge}">${methodLabel} · ${meta.label}</span>
                <button onclick="event.stopPropagation(); unlockRosterCard(${m.id})" class="w-7 h-7 flex items-center justify-center bg-white rounded-full text-gray-400 hover:text-toss-blue shadow-sm border border-gray-100 shrink-0" title="잘못 처리했다면 다시 선택">
                    <i class="fa-solid fa-pen text-[10px]"></i>
                </button>
                <button onclick="event.stopPropagation(); deleteRosterRecord(${m.id}, ${record.id})" class="w-7 h-7 flex items-center justify-center bg-white rounded-full text-gray-400 hover:text-toss-red shadow-sm border border-gray-100 shrink-0" title="완전히 삭제하고 미등록으로 되돌리기">
                    <i class="fa-solid fa-xmark text-[10px]"></i>
                </button>
            </div>
        </div>
    `;
}

/** 임원이 대면 체크를 잘못 눌렀을 때 다시 선택할 수 있도록 잠금을 풀어준다. 실제 저장은 재선택 시 face-check API가 덮어쓴다. */
function unlockRosterCard(memberId) {
    delete state.todayRecordByMember[memberId];
    renderAdminRoster();
}

/** 잘못 처리한 기록을 서버에서 완전히 삭제해 해당 부원을 미등록 상태로 되돌린다. */
async function deleteRosterRecord(memberId, recordId) {
    try {
        await api(`/api/attendance-records/${recordId}`, { method: 'DELETE' });
        delete state.todayRecordByMember[memberId];
        renderAdminRoster();
        showToast('기록을 삭제했습니다.');
    } catch (e) {
        showToast(e.message);
    }
}

function resetRosterButtons(memberId) {
    ['att', 'late', 'abs'].forEach(key => {
        document.getElementById(`btn-${key}-${memberId}`).className = "px-3 py-2 text-xs font-black rounded-xl bg-white border border-gray-200 text-gray-400 transition-colors";
    });
    document.getElementById(`late-input-wrap-${memberId}`).classList.add('hidden');
    document.getElementById(`btn-late-${memberId}`).textContent = '지각';
}

async function setRosterStatus(memberId, status) {
    resetRosterButtons(memberId);

    if (status === 'LATE') {
        document.getElementById(`btn-late-${memberId}`).className = "px-3 py-2 text-xs font-black rounded-xl bg-orange-400 text-white shadow-sm transition-colors";
        document.getElementById(`late-input-wrap-${memberId}`).classList.remove('hidden');
        document.getElementById(`roster-late-min-${memberId}`).focus();
        return;
    }

    try {
        await api('/api/attendance-records/face-check', {
            method: 'PUT',
            body: JSON.stringify({
                memberId, practiceDate: todayIso(),
                scheduledStartTime: CORE_START, scheduledEndTime: CORE_END,
                result: status, lateMinutes: null
            })
        });
        showToast(status === 'PRESENT' ? '출석 처리되었습니다.' : '결석 처리되었습니다.');
        await loadAdminRoster();
    } catch (e) {
        showToast(e.message);
    }
}

async function saveLate(memberId) {
    const min = parseInt(document.getElementById(`roster-late-min-${memberId}`).value);
    if (!min || min <= 0) return showToast('지각 시간을 입력해주세요.');
    try {
        await api('/api/attendance-records/face-check', {
            method: 'PUT',
            body: JSON.stringify({
                memberId, practiceDate: todayIso(),
                scheduledStartTime: CORE_START, scheduledEndTime: CORE_END,
                result: 'LATE', lateMinutes: min
            })
        });
        showToast(min >= 60 ? '60분 이상 지각으로 결석 처리되었습니다.' : `${min}분 지각 처리 완료`);
        await loadAdminRoster();
    } catch (e) {
        showToast(e.message);
    }
}

// ---------- 임원진: 행사 관리 ----------

async function loadAdminEvents() {
    try {
        const list = await api('/api/club-events');
        renderAdminEventList(list);
    } catch (e) {
        showToast(e.message);
    }
}

function renderAdminEventList(list) {
    const el = document.getElementById('event-list');
    el.innerHTML = '';
    if (list.length === 0) {
        el.innerHTML = `<div class="p-4 bg-gray-50 rounded-xl text-center text-xs font-bold text-gray-400">등록된 행사가 없습니다.</div>`;
        return;
    }
    list.forEach(ev => {
        el.insertAdjacentHTML('beforeend', `
            <div class="flex justify-between items-center bg-blue-50 p-3.5 rounded-xl border border-blue-100/50">
                <span class="text-sm font-black text-toss-blue">${ev.eventDate} · ${ev.title}</span>
                <button onclick="deleteClubEvent(${ev.id})" class="w-7 h-7 bg-white rounded-full flex items-center justify-center text-gray-400 hover:text-toss-red shadow-sm transition"><i class="fa-solid fa-xmark text-xs"></i></button>
            </div>
        `);
    });
}

async function addClubEvent() {
    const eventDate = document.getElementById('event-date-input').value;
    const title = document.getElementById('event-title-input').value.trim();
    if (!eventDate || !title) return showToast('날짜와 행사명을 입력해주세요.');
    try {
        await api('/api/club-events', { method: 'POST', body: JSON.stringify({ eventDate, title }) });
        document.getElementById('event-title-input').value = '';
        await loadAdminEvents();
        await loadEventBanner();
        showToast('행사가 등록되었습니다.');
    } catch (e) {
        showToast(e.message);
    }
}

async function deleteClubEvent(id) {
    try {
        await api(`/api/club-events/${id}`, { method: 'DELETE' });
        await loadAdminEvents();
        await loadEventBanner();
    } catch (e) {
        showToast(e.message);
    }
}

// ---------- 임원진: 합주 곡 관리 ----------

async function loadAdminSongs() {
    try {
        const [members, songs] = await Promise.all([
            state.allMembers.length ? Promise.resolve(state.allMembers) : api('/api/members'),
            api('/api/songs'),
        ]);
        state.allMembers = members;
        state.selectedSongMemberIds = new Set();
        renderSongMemberPicker();
        renderAdminSongList(songs);
    } catch (e) {
        showToast(e.message);
    }
}

function renderSongMemberPicker() {
    const el = document.getElementById('song-member-picker');
    el.innerHTML = state.allMembers.map(m => `
        <button type="button" onclick="toggleSongMemberPick(${m.id})" id="pick-member-${m.id}" class="px-3 py-1.5 text-xs font-bold rounded-full border bg-white border-gray-200 text-gray-400 transition-colors">${m.name}</button>
    `).join('');
}

function toggleSongMemberPick(memberId) {
    const btn = document.getElementById(`pick-member-${memberId}`);
    if (state.selectedSongMemberIds.has(memberId)) {
        state.selectedSongMemberIds.delete(memberId);
        btn.className = "px-3 py-1.5 text-xs font-bold rounded-full border bg-white border-gray-200 text-gray-400 transition-colors";
    } else {
        state.selectedSongMemberIds.add(memberId);
        btn.className = "px-3 py-1.5 text-xs font-bold rounded-full border bg-toss-blue border-toss-blue text-white transition-colors";
    }
}

function renderAdminSongList(list) {
    const el = document.getElementById('admin-song-list');
    el.innerHTML = '';
    if (list.length === 0) {
        el.innerHTML = `<div class="p-4 bg-gray-50 rounded-xl text-center text-xs font-bold text-gray-400">등록된 곡이 없습니다.</div>`;
        return;
    }
    list.forEach(song => {
        el.insertAdjacentHTML('beforeend', `
            <div class="flex items-center bg-blue-50 p-3.5 rounded-xl border border-blue-100/50">
                <span class="text-sm font-black text-toss-blue">${song.title}</span>
            </div>
        `);
    });
}

async function addSong() {
    const title = document.getElementById('song-title-input').value.trim();
    if (!title) return showToast('곡 제목을 입력해주세요.');
    if (state.selectedSongMemberIds.size === 0) return showToast('참여할 부원을 1명 이상 선택해주세요.');
    try {
        await api('/api/songs', {
            method: 'POST',
            body: JSON.stringify({ title, memberIds: Array.from(state.selectedSongMemberIds) })
        });
        document.getElementById('song-title-input').value = '';
        await loadAdminSongs();
        showToast('곡이 등록되었습니다.');
    } catch (e) {
        showToast(e.message);
    }
}

async function openMemberDetail(memberId) {
    try {
        const detail = await api(`/api/members/${memberId}`);
        document.getElementById('detail-name').textContent = `${detail.name} (${detail.part})`;
        document.getElementById('detail-avatar').textContent = detail.name.charAt(0);
        document.getElementById('detail-unpaid').textContent = detail.unpaidFine.toLocaleString() + '원';

        const list = document.getElementById('detail-history-list');
        list.innerHTML = '';
        if (detail.recentHistory.length === 0) {
            list.innerHTML = `<div class="text-center text-xs font-bold text-gray-400 py-6">최근 기록이 없습니다.</div>`;
        } else {
            detail.recentHistory.forEach(r => {
                const meta = statusMeta(r.status, r.lateMinutes);
                list.insertAdjacentHTML('beforeend', `
                    <div class="flex justify-between items-center bg-white p-3.5 rounded-xl border border-gray-100 shadow-sm mb-2">
                        <span class="text-sm font-black text-toss-text">${r.practiceDate}</span>
                        <span class="text-[10px] font-black px-2.5 py-1 rounded-md border ${meta.badge}">${meta.label}</span>
                    </div>
                `);
            });
        }
        openSheet('member-detail-sheet');
    } catch (e) {
        showToast(e.message);
    }
}
