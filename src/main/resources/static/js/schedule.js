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
