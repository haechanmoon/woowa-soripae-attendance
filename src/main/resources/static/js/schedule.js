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

function renderMySchedules(list) {
    const el = document.getElementById('my-schedule-list');
    el.innerHTML = '';
    if (list.length === 0) {
        el.innerHTML = `<div class="p-5 bg-gray-50 rounded-2xl border border-gray-100 text-center text-xs font-bold text-gray-400">등록된 스케줄이 없습니다.</div>`;
        return;
    }
    list.forEach(s => {
        const text = `${DAY_LABEL[s.dayOfWeek]}요일 ${formatTime(s.startTime)} - ${formatTime(s.endTime)}`;
        el.insertAdjacentHTML('beforeend', `
            <div class="flex justify-between items-center bg-blue-50 p-4 rounded-2xl border border-blue-100/50">
                <span class="text-sm font-black text-toss-blue"><i class="fa-solid fa-check mr-2 opacity-50"></i>${text}</span>
                <button onclick="deleteSchedule(${s.id})" class="w-8 h-8 bg-white rounded-full flex items-center justify-center text-gray-400 hover:text-toss-red shadow-sm transition"><i class="fa-solid fa-xmark text-sm"></i></button>
            </div>
        `);
    });
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
        `${shortDate(data.weekStart)} ~ ${shortDate(data.weekEnd)} · 연인원 ${data.totalCount}명`;
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
