// ---------- 스케줄 등록 ----------

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
