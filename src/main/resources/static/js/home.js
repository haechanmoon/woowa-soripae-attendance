// ---------- 홈: 벌금 카드 / 캘린더 / 행사 배너 ----------

async function loadEventBanner() {
    try {
        state.clubEvents = await api('/api/performances');
        renderEventBanner();
    } catch (e) {
        // 배너는 부가 기능이라 실패해도 조용히 무시한다.
    }
}

function renderEventBanner() {
    const banner = document.getElementById('event-banner');
    const today = todayIso();
    const upcoming = (state.clubEvents || [])
        .filter(ev => ev.eventDate >= today)
        .sort((a, b) => a.eventDate.localeCompare(b.eventDate))[0];
    if (!upcoming) {
        banner.classList.add('hidden');
        return;
    }
    const dday = daysBetween(today, upcoming.eventDate);
    document.getElementById('event-banner-title').textContent = upcoming.title;
    document.getElementById('event-banner-dday').textContent = dday === 0 ? 'D-DAY' : `D-${dday}`;
    const [, m, d] = upcoming.eventDate.split('-').map(Number);
    document.getElementById('event-banner-date').textContent = `${m}/${d}`;
    banner.classList.remove('hidden');
}

function daysBetween(fromIso, toIso) {
    return Math.round((new Date(toIso) - new Date(fromIso)) / 86400000);
}

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
    // 출석/스케줄은 캘린더 본체라 반드시 필요하다.
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

    // 행사 강조(별표)는 부가 기능이다. 광고 차단기 등으로 이 요청이 막혀도
    // 캘린더 본체는 이미 떴으니, 실패해도 조용히 무시한다(토스트 X).
    try {
        const events = await api('/api/performances');
        state.calendarEvents = events.filter(ev => {
            const [y, m] = ev.eventDate.split('-').map(Number);
            return y === state.calYear && m === state.calMonth;
        });
        renderCalendar();
    } catch (e) {
        // 행사 강조 실패는 무시한다.
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
    const eventByDay = {};
    (state.calendarEvents || []).forEach(ev => {
        const day = parseInt(ev.eventDate.split('-')[2], 10);
        eventByDay[day] = ev;
    });

    const today = new Date();
    const isCurrentMonth = today.getFullYear() === state.calYear && today.getMonth() + 1 === state.calMonth;

    for (let day = 1; day <= daysInMonth; day++) {
        const record = byDay[day];
        const event = eventByDay[day];
        const isToday = isCurrentMonth && day === today.getDate();
        let classes = "text-sm font-black text-toss-text w-8 h-8 mx-auto flex items-center justify-center rounded-full cursor-pointer transition relative";
        let clickEvt = '';
        if (record) {
            const meta = statusMeta(record.status, record.lateMinutes);
            classes += " " + meta.dot + " shadow-sm active:scale-90";
            clickEvt = `onclick="openCalSheet(${day})"`;
        } else if (event) {
            classes += " border-2 border-amber-400 text-amber-500 bg-amber-50 active:scale-90";
            clickEvt = `onclick="openCalSheet(${day})"`;
        }
        if (isToday) classes += " ring-2 ring-toss-blue ring-offset-1";
        const star = event ? `<i class="fa-solid fa-star absolute -top-1.5 -right-1.5 text-[8px] text-amber-500 bg-white rounded-full p-0.5 shadow-sm"></i>` : '';
        grid.innerHTML += `<div class="${classes}" ${clickEvt}>${day}${star}</div>`;
    }
}

function openCalSheet(day) {
    const record = state.calendarRecords.find(r => parseInt(r.practiceDate.split('-')[2], 10) === day);
    const event = (state.calendarEvents || []).find(ev => parseInt(ev.eventDate.split('-')[2], 10) === day);
    if (!record && !event) return;

    document.getElementById('cal-date-title').textContent = `${state.calMonth}월 ${day}일`;

    const eventRow = document.getElementById('cal-event-row');
    if (event) {
        document.getElementById('cal-event-title').textContent = event.title;
        eventRow.classList.remove('hidden');
    } else {
        eventRow.classList.add('hidden');
    }

    const attendanceBlock = document.getElementById('cal-attendance-block');
    if (record) {
        document.getElementById('cal-member-name').textContent = `${record.memberName} 님의 합주 기록입니다.`;
        const meta = statusMeta(record.status, record.lateMinutes);
        const statusEl = document.getElementById('cal-status');
        statusEl.textContent = meta.label;
        statusEl.className = 'text-base font-black px-3 py-1 rounded-lg border ' + meta.badge;
        document.getElementById('cal-time').textContent = `${formatTime(record.scheduledStartTime)} - ${formatTime(record.scheduledEndTime)}`;
        attendanceBlock.classList.remove('hidden');
    } else {
        document.getElementById('cal-member-name').textContent = '';
        attendanceBlock.classList.add('hidden');
    }

    openSheet('cal-sheet');
}
