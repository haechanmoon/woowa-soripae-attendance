// ---------- 스케줄 등록 ----------

/** 백엔드 ScheduleService.resolveNextOccurrence와 동일한 기준(오늘이 월요일이어도 반드시 다음 주)으로 다음 주 월~일 날짜를 계산한다. */
function nextWeekRange() {
    const now = new Date();
    const day = now.getDay();
    const daysUntilNextMonday = ((1 - day + 7) % 7) || 7;
    const nextMonday = new Date(now);
    nextMonday.setDate(now.getDate() + daysUntilNextMonday);
    const nextSunday = new Date(nextMonday);
    nextSunday.setDate(nextMonday.getDate() + 6);
    return { nextMonday, nextSunday };
}

function renderNextWeekRange() {
    const el = document.getElementById('next-week-range');
    if (!el) return;
    const { nextMonday, nextSunday } = nextWeekRange();
    const fmt = d => `${d.getMonth() + 1}/${d.getDate()}`;
    el.textContent = `${fmt(nextMonday)}(월) ~ ${fmt(nextSunday)}(일)`;
}

const DAY_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** 요일 선택창에 "월요일 (7/28)"처럼 다음 주 실제 날짜를 같이 보여준다. */
function populateScheduleDayOptions() {
    const daySel = document.getElementById('sched-day');
    if (!daySel) return;
    const { nextMonday } = nextWeekRange();
    daySel.innerHTML = '';
    DAY_ORDER.forEach((day, i) => {
        const d = new Date(nextMonday);
        d.setDate(nextMonday.getDate() + i);
        daySel.insertAdjacentHTML('beforeend', `<option value="${day}">${DAY_LABEL[day]}요일 (${d.getMonth() + 1}/${d.getDate()})</option>`);
    });
}

async function openScheduleSheet() {
    await loadSchedules();
    openSheet('schedule-sheet');
}

async function loadSchedules() {
    try {
        const list = await api(`/api/members/${state.member.id}/schedules`);
        renderMySchedules(list);
    } catch (e) {
        showToast(e.message);
    }
}

/** 이번 주 마지막 날(일요일). 이 날까지는 마감이 지나 사유 없이는 못 바꾼다. */
function thisWeekEndIso() {
    const d = new Date();
    const toSunday = (7 - (d.getDay() === 0 ? 7 : d.getDay())) % 7;
    d.setDate(d.getDate() + toSunday);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * 같은 "수요일 13:00"이라도 이번 주와 다음 주는 바꾸는 방법이 다르다.
 * 예전엔 둘이 날짜 없이 똑같이 보여서 본인이 이번 주 미등록인 걸 알아챌 수가 없었다.
 */
function renderMySchedules(list) {
    const weekEnd = thisWeekEndIso();
    state.mySchedules = list;
    renderNextWeekSchedules(list.filter(s => s.practiceDate > weekEnd));
    renderThisWeekSchedules(list.filter(s => s.practiceDate <= weekEnd), weekEnd);
}

function renderNextWeekSchedules(list) {
    const el = document.getElementById('my-schedule-list');
    if (list.length === 0) {
        el.innerHTML = `<div class="p-5 bg-gray-50 rounded-2xl border border-gray-100 text-center text-xs font-bold text-gray-400">등록된 스케줄이 없습니다.</div>`;
        return;
    }
    el.innerHTML = list.map(s => `
        <div class="flex justify-between items-center bg-blue-50 p-4 rounded-2xl border border-blue-100/50">
            <span class="text-sm font-black text-toss-blue"><i class="fa-solid fa-check mr-2 opacity-50"></i>${shortDate(s.practiceDate)} (${DAY_LABEL[s.dayOfWeek]}) ${formatTime(s.startTime)} 시작</span>
            <button onclick="deleteSchedule(${s.id})" class="w-8 h-8 bg-white rounded-full flex items-center justify-center text-gray-400 hover:text-toss-red shadow-sm transition"><i class="fa-solid fa-xmark text-sm"></i></button>
        </div>`).join('');
}

/** 오늘부터 이번 주 일요일까지를 하루씩 그린다. 이미 지난 날은 되돌릴 수 없어 아예 내보내지 않는다. */
function renderThisWeekSchedules(list, weekEnd) {
    const byDate = {};
    list.forEach(s => { byDate[s.practiceDate] = s; });

    const rows = [];
    for (let d = new Date(); ; d.setDate(d.getDate() + 1)) {
        const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
        if (iso > weekEnd) break;
        rows.push(thisWeekRow(iso, byDate[iso]));
    }
    document.getElementById('this-week-schedule-list').innerHTML = rows.join('')
        || `<div class="p-5 bg-gray-50 rounded-2xl border border-gray-100 text-center text-xs font-bold text-gray-400">이번 주는 끝났어요.</div>`;
}

function thisWeekRow(iso, schedule) {
    const day = DAY_LABEL[['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][new Date(iso + 'T00:00:00').getDay()]];
    const editing = state.thisWeekEditing === iso;
    const label = schedule
        ? `<span class="text-sm font-black text-toss-text">${shortDate(iso)} (${day}) <span class="text-toss-blue">${formatTime(schedule.startTime)}</span></span>`
        : `<span class="text-sm font-black text-gray-400">${shortDate(iso)} (${day}) <span class="text-xs font-bold">일정 없음</span></span>`;

    return `
        <div class="p-4 rounded-2xl border ${schedule ? 'bg-white border-gray-200' : 'bg-gray-50 border-gray-100'}">
            <div class="flex justify-between items-center">
                ${label}
                <button onclick="toggleThisWeekEditor('${iso}')" class="px-3 py-1.5 text-[11px] font-black rounded-lg ${editing ? 'bg-gray-200 text-gray-500' : 'bg-toss-blue/10 text-toss-blue'} active:scale-95 transition-transform">
                    ${editing ? '닫기' : (schedule ? '변경' : '등록')}
                </button>
            </div>
            ${editing ? thisWeekEditorHtml(iso, schedule) : ''}
        </div>`;
}

function thisWeekEditorHtml(iso, schedule) {
    const hour = schedule ? schedule.startTime.slice(0, 2) : '13';
    const hours = Array.from({ length: 16 }, (_, i) => String(i + 8).padStart(2, '0'));
    const minutes = ['00', '10', '20', '30', '40', '50'];
    const minute = schedule ? schedule.startTime.slice(3, 5) : '00';

    return `
        <div class="mt-3 pt-3 border-t border-gray-100 space-y-2">
            <div class="flex space-x-1">
                <select id="tw-hour-${iso}" class="flex-1 p-3 bg-white border border-gray-200 rounded-xl outline-none font-black text-toss-text text-sm">
                    ${hours.map(h => `<option value="${h}" ${h === hour ? 'selected' : ''}>${h}</option>`).join('')}
                </select>
                <span class="flex items-center font-black text-toss-text">:</span>
                <select id="tw-minute-${iso}" class="flex-1 p-3 bg-white border border-gray-200 rounded-xl outline-none font-black text-toss-text text-sm">
                    ${minutes.map(m => `<option value="${m}" ${m === minute ? 'selected' : ''}>${m}</option>`).join('')}
                </select>
            </div>
            <input id="tw-reason-${iso}" type="text" maxlength="200" placeholder="사유를 적어주세요 (임원진에게 보입니다)"
                   class="w-full p-3 bg-white border border-gray-200 rounded-xl outline-none font-bold text-toss-text text-xs focus:border-toss-blue">
            <div class="flex space-x-2">
                <button onclick="submitThisWeekChange('${iso}', false)" class="flex-[2] py-3 bg-toss-blue text-white font-black text-sm rounded-xl active:scale-[0.98] transition-transform">
                    ${schedule ? '이 시간으로 변경' : '이 시간으로 등록'}
                </button>
                ${schedule ? `<button onclick="submitThisWeekChange('${iso}', true)" class="flex-1 py-3 bg-red-50 text-toss-red font-black text-sm rounded-xl border border-red-100 active:scale-[0.98] transition-transform">취소</button>` : ''}
            </div>
        </div>`;
}

function toggleThisWeekEditor(iso) {
    state.thisWeekEditing = state.thisWeekEditing === iso ? null : iso;
    renderMySchedules(state.mySchedules);
}

/** cancel이면 시각을 비워 보낸다. 서버는 그걸 "그날 등록을 지워달라"는 뜻으로 읽는다. */
async function submitThisWeekChange(iso, cancel) {
    const reason = document.getElementById(`tw-reason-${iso}`).value.trim();
    if (!reason) {
        showToast('사유를 적어주세요.');
        return;
    }
    const startTime = cancel
        ? null
        : `${document.getElementById(`tw-hour-${iso}`).value}:${document.getElementById(`tw-minute-${iso}`).value}`;
    try {
        await api(`/api/members/${state.member.id}/schedules/this-week`, {
            method: 'POST',
            body: JSON.stringify({ practiceDate: iso, startTime, reason })
        });
        state.thisWeekEditing = null;
        await loadSchedules();
        await loadCalendar();
        showToast(cancel ? '취소했어요. 임원진에게 사유가 전달됩니다.' : '변경했어요. 임원진에게 사유가 전달됩니다.');
    } catch (e) {
        showToast(e.message);
    }
}

async function addSchedule() {
    const dayOfWeek = document.getElementById('sched-day').value;
    const startTime = `${document.getElementById('sched-hour').value}:${document.getElementById('sched-minute').value}`;
    try {
        await api(`/api/members/${state.member.id}/schedules`, {
            method: 'POST',
            body: JSON.stringify({ dayOfWeek, startTime })
        });
        await loadSchedules();
        await loadCalendar();
        showToast('스케줄 등록 및 캘린더 연동 완료!');
    } catch (e) {
        showToast(e.message);
    }
}

async function deleteSchedule(id) {
    try {
        await api(`/api/members/${state.member.id}/schedules/${id}`, { method: 'DELETE' });
        await loadSchedules();
        await loadCalendar();
    } catch (e) {
        showToast(e.message);
    }
}

// ---------- 요일별 합주 현황 ----------

function shortDate(iso) {
    const [, m, d] = iso.split('-').map(Number);
    return `${m}/${d}`;
}

async function loadWeeklySchedule() {
    try {
        renderWeeklySchedule(await api(`/api/schedules/weekly?scope=${state.weekScope}`));
    } catch (e) {
        showToast(e.message);
    }
}

function switchWeekScope(scope) {
    state.weekScope = scope;
    ['THIS', 'NEXT'].forEach(key => {
        document.getElementById(`btn-week-${key}`).className = key === scope
            ? "px-3 py-1.5 text-[11px] font-black bg-white text-toss-text rounded-lg shadow-sm transition-all"
            : "px-3 py-1.5 text-[11px] font-black text-gray-400 rounded-lg transition-all";
    });
    loadWeeklySchedule();
}

function renderWeeklySchedule(data) {
    document.getElementById('weekly-range-label').textContent =
        `${shortDate(data.weekStart)} ~ ${shortDate(data.weekEnd)} · ${data.memberCount}명 등록`;
    const today = todayIso();
    document.getElementById('weekly-day-list').innerHTML =
        data.days.map(day => weeklyDayCard(day, today)).join('');
}

/** 등록이 없는 요일은 한 줄로 눌러 담는다. 7일을 다 그려도 스크롤이 짧아야 한눈에 비교가 된다. */
function weeklyDayCard(day, today) {
    const isToday = day.date === today;
    const isEmpty = day.slots.length === 0;
    const dayColor = day.dayOfWeek === 'SUNDAY' ? 'text-toss-red'
        : day.dayOfWeek === 'SATURDAY' ? 'text-toss-blue' : 'text-toss-text';
    const right = isEmpty
        ? `<span class="text-[11px] font-bold text-gray-300">등록 없음</span>`
        : `<span class="text-[11px] font-black text-toss-blue bg-blue-50 border border-blue-100 px-2 py-0.5 rounded-md">${day.memberCount}명</span>`;

    return `
        <div class="${isEmpty ? 'px-4 py-2.5' : 'p-4'} rounded-2xl border ${isToday ? 'bg-blue-50/40 border-blue-100' : 'bg-gray-50 border-gray-100'}">
            <div class="flex items-center justify-between">
                <span class="text-sm font-black ${dayColor} ${isEmpty ? 'opacity-50' : ''}">
                    ${DAY_LABEL[day.dayOfWeek]}
                    <span class="text-xs font-bold text-toss-subText ml-0.5">${shortDate(day.date)}</span>
                    ${isToday ? `<span class="text-[10px] font-black text-white bg-toss-blue px-1.5 py-0.5 rounded-md ml-1 align-middle">오늘</span>` : ''}
                </span>
                ${right}
            </div>
            ${isEmpty ? '' : `<div class="mt-2.5 space-y-2">${day.slots.map(weeklySlotRow).join('')}</div>`}
        </div>`;
}

/** 같은 시각에 오는 사람을 한 줄로 묶고, 내 이름은 파랗게 강조해 찾기 쉽게 한다. */
function weeklySlotRow(slot) {
    const names = slot.attendees.map(a => {
        const mine = state.member && a.memberId === state.member.id;
        return `<span class="px-2 py-1 text-[11px] font-black rounded-lg border ${mine ? 'bg-toss-blue text-white border-toss-blue' : 'bg-white text-toss-text border-gray-200'}">${a.name}</span>`;
    }).join('');
    return `
        <div class="flex items-start space-x-2">
            <span class="shrink-0 text-[11px] font-black text-toss-blue bg-white border border-blue-100 px-2 py-1 rounded-lg">${formatTime(slot.startTime)}</span>
            <div class="flex flex-wrap gap-1.5">${names}</div>
        </div>`;
}
