// ---------- 홈: 벌금 카드 / 캘린더 ----------

async function loadFineSummary() {
    try {
        const data = await api(`/api/members/${state.member.id}/fines`);
        document.getElementById('fine-total').textContent = data.totalFine.toLocaleString();
        const badges = document.getElementById('fine-badges');
        badges.innerHTML = '';
        if (data.absentCount > 0) {
            badges.insertAdjacentHTML('beforeend', `<span class="text-xs bg-white/20 inline-block px-3 py-1 rounded-full backdrop-blur-sm self-start">결석 ${data.absentCount}회 (${data.absentFine.toLocaleString()}원)</span>`);
        }
        if (data.lateTotalMinutes > 0) {
            badges.insertAdjacentHTML('beforeend', `<span class="text-xs bg-white/20 inline-block px-3 py-1 rounded-full backdrop-blur-sm self-start">지각 총 ${data.lateTotalMinutes}분 (${data.lateFine.toLocaleString()}원)</span>`);
        }
        if (data.absentCount === 0 && data.lateTotalMinutes === 0) {
            badges.insertAdjacentHTML('beforeend', `<span class="text-xs bg-white/20 inline-block px-3 py-1 rounded-full backdrop-blur-sm self-start">지각/결석 없음 🎉</span>`);
        }
    } catch (e) {
        showToast(e.message);
    }
}

async function loadCalendar() {
    try {
        const [records, schedules] = await Promise.all([
            api(`/api/members/${state.member.id}/attendance-records?year=${state.calYear}&month=${state.calMonth}`),
            api(`/api/members/${state.member.id}/schedules`),
        ]);

        // 스케줄만 등록하고 아직 인증(사진/대면)을 안 한 날짜는 실제 기록이 없어 캘린더에서 빠져 보였다.
        // 아직 인증 전인 등록 스케줄을 'SCHEDULED'로 채워서 같이 보여준다.
        const recordKeys = new Set(records.map(r => `${r.practiceDate}_${r.scheduledStartTime}`));
        const scheduledOnly = schedules
            .filter(s => {
                const [y, m] = s.practiceDate.split('-').map(Number);
                return y === state.calYear && m === state.calMonth;
            })
            .filter(s => !recordKeys.has(`${s.practiceDate}_${s.startTime}`))
            .map(s => ({
                practiceDate: s.practiceDate,
                memberName: state.member.name,
                status: 'SCHEDULED',
                lateMinutes: 0,
                scheduledStartTime: s.startTime,
                scheduledEndTime: s.endTime,
            }));

        state.calendarRecords = [...records, ...scheduledOnly];
        renderCalendar();
    } catch (e) {
        showToast(e.message);
    }
}

function changeMonth(delta) {
    state.calMonth += delta;
    if (state.calMonth > 12) { state.calMonth = 1; state.calYear++; }
    if (state.calMonth < 1) { state.calMonth = 12; state.calYear--; }
    loadCalendar();
}

function renderCalendar() {
    document.getElementById('calendar-title').textContent = `${state.calMonth}월`;
    const grid = document.getElementById('calendar-grid');
    grid.innerHTML = '';

    const firstDay = new Date(state.calYear, state.calMonth - 1, 1).getDay();
    const daysInMonth = new Date(state.calYear, state.calMonth, 0).getDate();
    for (let i = 0; i < firstDay; i++) grid.innerHTML += `<div></div>`;

    const byDay = {};
    state.calendarRecords.forEach(r => {
        const day = parseInt(r.practiceDate.split('-')[2], 10);
        byDay[day] = r;
    });

    for (let day = 1; day <= daysInMonth; day++) {
        const record = byDay[day];
        let classes = "text-sm font-black text-toss-text w-8 h-8 mx-auto flex items-center justify-center rounded-full cursor-pointer transition";
        let clickEvt = '';
        if (record) {
            const meta = statusMeta(record.status, record.lateMinutes);
            classes += " " + meta.dot + " shadow-sm active:scale-90";
            clickEvt = `onclick="openCalSheet(${day})"`;
        }
        grid.innerHTML += `<div class="${classes}" ${clickEvt}>${day}</div>`;
    }
}

function openCalSheet(day) {
    const record = state.calendarRecords.find(r => parseInt(r.practiceDate.split('-')[2], 10) === day);
    if (!record) return;
    document.getElementById('cal-date-title').textContent = `${state.calMonth}월 ${day}일`;
    document.getElementById('cal-member-name').textContent = `${record.memberName} 님의 합주 기록입니다.`;
    const meta = statusMeta(record.status, record.lateMinutes);
    const statusEl = document.getElementById('cal-status');
    statusEl.textContent = meta.label;
    statusEl.className = 'text-base font-black px-3 py-1 rounded-lg border ' + meta.badge;
    document.getElementById('cal-time').textContent = `${formatTime(record.scheduledStartTime)} - ${formatTime(record.scheduledEndTime)}`;
    openSheet('cal-sheet');
}
