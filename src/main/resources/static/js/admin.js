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

/**
 * 임원이 그날 처리하지 못하고 넘어간 연습을 뒤늦게 정리할 수 있도록 날짜를 고른다.
 * 서버는 처음부터 임의 날짜를 받았고(face-check의 practiceDate), 화면만 오늘로 묶여 있었다.
 * 아직 오지 않은 날은 체크할 것이 없으므로 오늘까지만 허용한다.
 */
function changeRosterDate(iso) {
    if (!iso) return;
    state.rosterDate = iso > todayIso() ? todayIso() : iso;
    loadAdminRoster();
}

function shiftRosterDate(delta) {
    changeRosterDate(shiftIso(rosterDateIso(), delta));
}

function resetRosterDate() {
    changeRosterDate(todayIso());
}

/** 지난 날짜를 보고 있을 때는 오늘 것을 만지는 줄 알고 잘못 누르지 않도록 눈에 띄게 알린다. */
function renderRosterDateBar() {
    const iso = rosterDateIso();
    const isToday = isRosterToday();

    const input = document.getElementById('roster-date-input');
    input.value = iso;
    input.max = todayIso();

    const next = document.getElementById('btn-roster-next');
    next.disabled = isToday;
    next.classList.toggle('opacity-30', isToday);
    document.getElementById('btn-roster-today').classList.toggle('hidden', isToday);

    const note = document.getElementById('roster-date-note');
    note.textContent = isToday ? `오늘 · ${formatDateKorean(iso)}` : `지난 날짜를 정리하는 중 · ${formatDateKorean(iso)}`;
    note.className = isToday
        ? 'text-xs font-bold text-toss-blue mt-3 text-center'
        : 'text-xs font-black text-orange-500 mt-3 text-center';

    document.getElementById('uncertified-title').textContent = `${rosterDayLabel()} 미인증`;
    document.getElementById('roster-title').textContent = isToday ? '오늘의 대면 출석 명단' : `${shortDate(iso)} 대면 출석 명단`;
    document.getElementById('roster-subtitle').textContent = isToday
        ? '하루 한 번 체크하면 출석 인정'
        : '그날 처리하지 못한 출석을 지금 채워 넣을 수 있어요';
}

async function loadAdminRoster() {
    renderRosterDateBar();
    const date = rosterDateIso();
    try {
        const [members, todayRecords, todaySchedules] = await Promise.all([
            api('/api/members'),
            api(`/api/attendance-records?date=${date}`),
            api(`/api/schedules?date=${date}`),
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
    loadRegistrationStatus();
    loadThisWeekChanges();
    loadUncertified();
}

/** 그날 오기로 해놓고 인증을 안 올린 사람. 자동 처리하지 않고 임원이 보고 판단하도록 목록만 띄운다. */
async function loadUncertified() {
    try {
        renderUncertified(await api(`/api/attendance-records/uncertified?date=${rosterDateIso()}`));
    } catch (e) {
        // 부가 정보라 실패해도 조용히 무시한다.
    }
}

function renderUncertified(list) {
    document.getElementById('uncertified-label').textContent =
        list.length === 0 ? `${rosterDayLabel()} 등록자는 모두 인증했어요` : `${list.length}명이 아직 인증하지 않았어요`;

    const el = document.getElementById('uncertified-list');
    if (list.length === 0) {
        el.innerHTML = `<p class="text-sm font-black text-toss-green text-center py-4 bg-green-50 rounded-2xl border border-green-100">모두 인증 완료 🎉</p>`;
        return;
    }
    const isToday = isRosterToday();
    const nowMinutes = new Date().getHours() * 60 + new Date().getMinutes();
    el.innerHTML = list.map(m => {
        const [h, min] = m.scheduledStartTime.split(':').map(Number);
        // 지난 날짜는 연습이 이미 끝나 '경과'가 의미 없다. 그날 오기로 한 시각만 적는다.
        const elapsed = isToday ? nowMinutes - (h * 60 + min) : null;
        const pending = elapsed !== null && elapsed < 0; // 아직 시작 전이면 '안 온 사람'이 아니다
        const when = elapsed === null
            ? `<span class="text-[10px] font-black text-toss-red">${formatTime(m.scheduledStartTime)} 등록 · 인증 없음</span>`
            : pending
                ? `<span class="text-[10px] font-bold text-toss-subText">${formatTime(m.scheduledStartTime)} 예정</span>`
                : `<span class="text-[10px] font-black text-toss-red">${formatTime(m.scheduledStartTime)} · ${Math.floor(elapsed / 60)}시간 ${elapsed % 60}분 경과</span>`;
        return `
            <div class="flex items-center justify-between p-3.5 rounded-2xl border ${pending ? 'bg-gray-50 border-gray-100' : 'bg-red-50/50 border-red-100'}">
                <div class="flex items-center space-x-2 min-w-0">
                    <span class="text-sm font-black text-toss-text">${m.name}</span>
                    <span class="text-[10px] font-bold text-toss-subText">${m.part}</span>
                </div>
                ${when}
            </div>`;
    }).join('');
}

/** 마감이 일요일이라 주 초의 "다음 주 전원 미등록"은 당연한 상태다. 처음엔 서버가 요일에 맞는 주를 골라준다. */
async function loadRegistrationStatus() {
    const query = state.registrationScope ? `?scope=${state.registrationScope}` : '';
    try {
        renderRegistrationStatus(await api(`/api/schedules/registration${query}`));
    } catch (e) {
        // 부가 정보라 실패해도 조용히 무시한다.
    }
}

function switchRegistrationScope(scope) {
    if (state.registrationScope === scope) return;
    state.registrationScope = scope;
    state.registrationExpanded = false; // 주를 바꾸면 다시 요약부터 본다
    loadRegistrationStatus();
}

/** 펼치기는 이미 받아둔 목록을 보여주기만 하면 되므로 서버를 다시 부르지 않는다. */
function toggleRegistrationList() {
    state.registrationExpanded = !state.registrationExpanded;
    renderRegistrationStatus(state.registrationData);
}

function renderRegistrationStatus(data) {
    state.registrationData = data;
    state.registrationScope = data.scope;
    ['THIS', 'NEXT'].forEach(key => {
        document.getElementById(`btn-reg-${key}`).className = key === data.scope
            ? "px-3 py-1.5 text-[11px] font-black bg-white text-toss-text rounded-lg shadow-sm transition-all"
            : "px-3 py-1.5 text-[11px] font-black text-gray-400 rounded-lg transition-all";
    });

    const total = data.registered.length + data.notRegistered.length;
    const missing = data.notRegistered.length;
    document.getElementById('registration-range-label').textContent =
        `${shortDate(data.weekStart)} ~ ${shortDate(data.weekEnd)}${registrationDeadlineNote(data)}`;

    const el = document.getElementById('registration-summary');
    if (total === 0) {
        el.innerHTML = `<p class="text-sm font-bold text-gray-400 text-center py-4 bg-gray-50 rounded-2xl border border-gray-100">아직 곡에 배정된 부원이 없어요.</p>`;
        return;
    }
    if (missing === 0) {
        el.innerHTML = `<p class="text-sm font-black text-toss-green text-center py-4 bg-green-50 rounded-2xl border border-green-100">${total}명 전원 등록 완료 🎉</p>`;
        return;
    }
    el.innerHTML = registrationSummaryBar(data, total, missing) + registrationNameList(data, missing);
}

/** 마감 전(다음 주)에만 남은 날을 알려준다. 이번 주는 이미 지나 손쓸 수 없다. */
function registrationDeadlineNote(data) {
    if (data.scope !== 'NEXT') return ' · 마감된 주';
    return data.daysUntilDeadline === 0 ? ' · 오늘 자정 마감' : ` · 일요일 마감까지 ${data.daysUntilDeadline}일`;
}

/**
 * 접힌 상태에서도 "몇 명 중 몇 명"이 바로 보이게 한다.
 * 색은 인원수가 아니라 급한 정도로 정한다. 마감이 코앞인 다음 주만 빨갛고, 여유 있거나 이미 지난 주는 무채색이다.
 */
function registrationSummaryBar(data, total, missing) {
    const tone = data.urgent
        ? { text: 'text-toss-red', bar: 'bg-toss-red', chip: 'text-toss-red bg-red-50 border-red-100' }
        : { text: 'text-toss-text', bar: 'bg-gray-400', chip: 'text-gray-500 bg-gray-100 border-gray-200' };
    const arrow = state.registrationExpanded ? '▲' : '▼';

    return `
        <button onclick="toggleRegistrationList()" class="w-full p-4 bg-gray-50 border border-gray-100 rounded-2xl text-left active:scale-[0.99] transition-transform">
            <div class="flex items-center justify-between mb-2.5">
                <span class="text-sm font-black text-toss-text">등록 <span class="${tone.text}">${data.registered.length}</span><span class="text-toss-subText">/${total}</span></span>
                <span class="flex items-center space-x-1.5">
                    <span class="px-2 py-0.5 text-[11px] font-black border rounded-md ${tone.chip}">미등록 ${missing}명</span>
                    <span class="text-[10px] text-gray-400">${arrow}</span>
                </span>
            </div>
            <div class="h-1.5 bg-gray-200 rounded-full overflow-hidden">
                <div class="h-full ${tone.bar} rounded-full transition-all" style="width: ${Math.round(data.registered.length / total * 100)}%"></div>
            </div>
        </button>`;
}

function registrationNameList(data, missing) {
    if (!state.registrationExpanded) return '';
    const chip = data.urgent
        ? { box: 'text-toss-red bg-red-50 border-red-100', part: 'text-red-300' }
        : { box: 'text-gray-600 bg-gray-50 border-gray-200', part: 'text-gray-400' };
    return `
        <div class="mt-3">
            <p class="text-[11px] font-black text-toss-subText px-1 mb-2">미등록 ${missing}명</p>
            <div class="flex flex-wrap gap-2">${data.notRegistered.map(m =>
                `<span class="px-3 py-1.5 text-xs font-black border rounded-full ${chip.box}">${m.name}<span class="text-[10px] font-bold ml-1 ${chip.part}">${m.part}</span></span>`
            ).join('')}</div>
        </div>`;
}

/** 마감 후 이번 주를 바꾼 사람들. 승인할 것이 아니라 "물어볼 거리"를 모아 보여준다. */
async function loadThisWeekChanges() {
    try {
        renderThisWeekChanges(await api('/api/schedules/this-week-changes'));
    } catch (e) {
        // 부가 정보라 실패해도 조용히 무시한다.
    }
}

const CHANGE_KIND = {
    ADDED: { text: '늦게 등록', tone: 'text-toss-blue bg-blue-50 border-blue-100' },
    MOVED: { text: '시간 변경', tone: 'text-gray-500 bg-gray-100 border-gray-200' },
    // 가기로 해놓고 접은 쪽이라 임원이 가장 눈여겨봐야 한다.
    CANCELED: { text: '취소', tone: 'text-toss-red bg-red-50 border-red-100' },
};

function renderThisWeekChanges(list) {
    document.getElementById('this-week-changes-label').textContent =
        list.length === 0 ? '마감 후 바꾼 사람이 없어요' : `${list.length}건 · 사유를 보고 판단해주세요`;

    const el = document.getElementById('this-week-changes-list');
    if (list.length === 0) {
        el.innerHTML = `<p class="text-sm font-bold text-gray-400 text-center py-4 bg-gray-50 rounded-2xl border border-gray-100">변경 없음</p>`;
        return;
    }
    el.innerHTML = list.map(c => {
        const kind = CHANGE_KIND[c.kind];
        const when = c.previousStartTime && c.newStartTime
            ? `${formatTime(c.previousStartTime)} → ${formatTime(c.newStartTime)}`
            : formatTime(c.newStartTime || c.previousStartTime);
        return `
            <div class="p-4 rounded-2xl border bg-gray-50 border-gray-100">
                <div class="flex items-center justify-between mb-1.5">
                    <span class="flex items-center space-x-2 min-w-0">
                        <span class="text-sm font-black text-toss-text">${c.memberName}</span>
                        <span class="text-[10px] font-bold text-toss-subText">${c.part}</span>
                    </span>
                    <span class="px-2 py-0.5 text-[10px] font-black border rounded-md ${kind.tone}">${kind.text}</span>
                </div>
                <p class="text-[11px] font-bold text-toss-subText mb-2">${shortDate(c.practiceDate)} · ${when}</p>
                <p class="text-xs font-bold text-toss-text bg-white px-3 py-2 rounded-xl border border-gray-100">${c.reason}</p>
            </div>`;
    }).join('');
}

function toggleRosterNoSchedule() {
    state.rosterNoScheduleExpanded = !state.rosterNoScheduleExpanded;
    renderAdminRoster();
}

function renderAdminRoster() {
    const el = document.getElementById('admin-roster-list');
    el.innerHTML = '';

    const scheduled = state.allMembers.filter(m => state.todayScheduleByMember[m.id]);
    // 오늘 일정이 없을 뿐 이번 주 미등록자와는 다르다. 대부분이 여기 들어가 명단을 덮어버리므로 접어둔다.
    const noSchedule = state.allMembers.filter(m => !state.todayScheduleByMember[m.id]);

    if (scheduled.length > 0) {
        el.insertAdjacentHTML('beforeend', `<h4 class="text-xs font-black text-toss-blue px-1 mb-2">${rosterDayLabel()} 등록 (${scheduled.length}명)</h4>`);
        scheduled.forEach(m => renderRosterCard(el, m));
    }
    if (noSchedule.length === 0) {
        return;
    }
    el.insertAdjacentHTML('beforeend', `
        <button onclick="toggleRosterNoSchedule()" class="w-full flex items-center justify-between px-3 py-2.5 bg-gray-50 border border-gray-100 rounded-xl ${scheduled.length > 0 ? 'mt-5' : ''} active:scale-[0.99] transition-transform">
            <span class="text-xs font-black text-gray-400">${rosterDayLabel()} 일정 없음 (${noSchedule.length}명)</span>
            <span class="text-[10px] text-gray-400">${state.rosterNoScheduleExpanded ? '▲' : '▼'}</span>
        </button>
        <div id="roster-no-schedule" class="space-y-4 ${state.rosterNoScheduleExpanded ? 'mt-4' : 'hidden'}"></div>`);

    const box = document.getElementById('roster-no-schedule');
    noSchedule.forEach(m => renderRosterCard(box, m));
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
                memberId, practiceDate: rosterDateIso(), result: status, lateMinutes: null
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
                memberId, practiceDate: rosterDateIso(), result: 'LATE', lateMinutes: min
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
        const list = await api('/api/performances');
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
        await api('/api/performances', { method: 'POST', body: JSON.stringify({ eventDate, title }) });
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
        await api(`/api/performances/${id}`, { method: 'DELETE' });
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

// ---------- 임원진: 정산 (지각비 미납부자 공지) ----------

const SETTLEMENT_FIELD_KEYS = {
    'settlement-event-title': 'soripae_settlement_event_title',
    'settlement-officer-name': 'soripae_settlement_officer_name',
    'settlement-bank-name': 'soripae_settlement_bank_name',
    'settlement-account-number': 'soripae_settlement_account_number',
    'settlement-account-holder': 'soripae_settlement_account_holder',
};

/** 행사명/총무 이름/계좌는 기기별로 다음에도 또 쓰므로 localStorage에 남겨 다시 입력하지 않게 한다. */
function restoreSettlementInputs() {
    Object.entries(SETTLEMENT_FIELD_KEYS).forEach(([id, storageKey]) => {
        const saved = localStorage.getItem(storageKey);
        if (saved) document.getElementById(id).value = saved;
    });
}

function persistSettlementInputs() {
    Object.entries(SETTLEMENT_FIELD_KEYS).forEach(([id, storageKey]) => {
        localStorage.setItem(storageKey, document.getElementById(id).value.trim());
    });
}

async function loadAdminSettlement() {
    restoreSettlementInputs();
    try {
        state.unpaidFines = await api('/api/members/unpaid-fines');
        state.excludedUnpaidFineIds = new Set();
        renderUnpaidFineList();
        updateNoticePreview();
    } catch (e) {
        showToast(e.message);
    }
}

function renderUnpaidFineList() {
    const el = document.getElementById('settlement-list');
    const emptyState = document.getElementById('settlement-empty-state');
    const includedCount = state.unpaidFines.filter(f => !state.excludedUnpaidFineIds.has(f.memberId)).length;
    document.getElementById('settlement-count').textContent = includedCount;

    if (state.unpaidFines.length === 0) {
        el.innerHTML = '';
        emptyState.classList.remove('hidden');
        document.getElementById('btn-settlement-toggle-all').classList.add('hidden');
        return;
    }
    emptyState.classList.add('hidden');
    document.getElementById('btn-settlement-toggle-all').classList.remove('hidden');
    document.getElementById('btn-settlement-toggle-all').textContent = includedCount === 0 ? '전체 선택' : '전체 해제';

    el.innerHTML = state.unpaidFines.map(f => {
        const checked = !state.excludedUnpaidFineIds.has(f.memberId);
        return `
            <label class="flex items-center justify-between p-3.5 rounded-2xl border ${checked ? 'bg-blue-50/50 border-blue-100' : 'bg-gray-50 border-gray-100 opacity-60'} cursor-pointer">
                <span class="flex items-center space-x-2 min-w-0">
                    <input type="checkbox" ${checked ? 'checked' : ''} onchange="toggleUnpaidFineExclude(${f.memberId})" class="w-4 h-4 accent-toss-blue shrink-0">
                    <span class="text-sm font-black text-toss-text">${f.name}</span>
                    <span class="text-[10px] font-bold text-toss-subText">${f.part}</span>
                </span>
                <span class="text-sm font-black text-toss-red">${f.amount.toLocaleString()}원</span>
            </label>`;
    }).join('');
}

function toggleUnpaidFineExclude(memberId) {
    if (state.excludedUnpaidFineIds.has(memberId)) {
        state.excludedUnpaidFineIds.delete(memberId);
    } else {
        state.excludedUnpaidFineIds.add(memberId);
    }
    renderUnpaidFineList();
    updateNoticePreview();
}

function toggleAllUnpaidFine() {
    const includedCount = state.unpaidFines.filter(f => !state.excludedUnpaidFineIds.has(f.memberId)).length;
    state.excludedUnpaidFineIds = includedCount === 0 ? new Set() : new Set(state.unpaidFines.map(f => f.memberId));
    renderUnpaidFineList();
    updateNoticePreview();
}

function buildNoticeText() {
    const eventTitle = document.getElementById('settlement-event-title').value.trim() || '[행사명]';
    const officerName = document.getElementById('settlement-officer-name').value.trim() || '[총무 이름]';
    const bankName = document.getElementById('settlement-bank-name').value.trim() || '[은행]';
    const accountNumber = document.getElementById('settlement-account-number').value.trim() || '[계좌번호]';
    const accountHolder = document.getElementById('settlement-account-holder').value.trim() || '[예금주]';

    const names = state.unpaidFines
        .filter(f => !state.excludedUnpaidFineIds.has(f.memberId))
        .map(f => `${f.name} ${f.amount.toLocaleString()}원`)
        .join('\n');

    return `‼️${eventTitle} 지각비 미납부자 명단 공지‼️\n\n`
        + `안녕하세요! 소리패 총무 ${officerName} 입니다😊\n`
        + `현재 기준 ${eventTitle} 연습 지각비 미납부자 명단 안내 드립니다\n`
        + `아래 명단에 이름이 있는 부원은 보는 즉시 지각비를 입금해주시기 바랍니다! \n\n`
        + `*지각비는 공연 뒤풀이 회식에 보태 사용할 예정입니다 \n\n`
        + `${names || '(선택된 인원 없음)'}\n\n`
        + `${accountNumber} ${bankName} ${accountHolder}`;
}

function updateNoticePreview() {
    persistSettlementInputs();
    document.getElementById('settlement-preview').value = buildNoticeText();
}

async function copyNoticeText() {
    const includedCount = state.unpaidFines.filter(f => !state.excludedUnpaidFineIds.has(f.memberId)).length;
    if (includedCount === 0) return showToast('선택된 인원이 없습니다.');

    const missing = Object.keys(SETTLEMENT_FIELD_KEYS).filter(id => !document.getElementById(id).value.trim());
    if (missing.length > 0) return showToast('행사명 · 총무 이름 · 계좌 정보를 모두 입력해주세요.');

    try {
        await navigator.clipboard.writeText(buildNoticeText());
        showToast('공지 문구를 복사했습니다.');
    } catch (e) {
        showToast('복사에 실패했습니다. 직접 선택해서 복사해주세요.');
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
