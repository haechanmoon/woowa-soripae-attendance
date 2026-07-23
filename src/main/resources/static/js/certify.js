// ---------- 인증하기 ----------

async function loadCertifySchedules() {
    const sel = document.getElementById('certify-schedule');
    sel.innerHTML = '';
    let todaysSlots = [];
    try {
        const list = await api(`/api/members/${state.member.id}/schedules`);
        todaysSlots = list.filter(s => s.practiceDate === todayIso());
    } catch (e) {
        showToast(e.message);
    }

    const hasCore = todaysSlots.some(s => formatTime(s.startTime) === CORE_START);
    if (!hasCore) {
        sel.insertAdjacentHTML('beforeend', `<option value="${CORE_START}">${CORE_START} - ${CORE_END} (코어타임)</option>`);
    }
    todaysSlots
        .slice()
        .sort((a, b) => a.startTime.localeCompare(b.startTime))
        .forEach(s => {
            const start = formatTime(s.startTime);
            const label = start === CORE_START
                ? `${start} - ${formatTime(s.endTime)} (코어타임)`
                : `${start} - ${formatTime(s.endTime)}`;
            sel.insertAdjacentHTML('beforeend', `<option value="${start}">${label}</option>`);
        });
}

function previewPhoto(input) {
    if (input.files && input.files[0]) {
        const reader = new FileReader();
        reader.onload = function (e) {
            document.getElementById('photo-preview').src = e.target.result;
            document.getElementById('photo-preview').classList.remove('hidden');
            document.getElementById('upload-prompt').classList.add('hidden');
        };
        reader.readAsDataURL(input.files[0]);
    }
}

async function submitAttendance() {
    const scheduledVal = document.getElementById('certify-schedule').value;
    const fileInput = document.getElementById('photo-upload');
    if (!fileInput.files || !fileInput.files[0]) return showToast('인증 사진을 터치하여 올려주세요.');

    const formData = new FormData();
    formData.append('memberId', state.member.id);
    formData.append('scheduledStartTime', scheduledVal);
    formData.append('photo', fileInput.files[0]);

    try {
        await api('/api/attendance-records', { method: 'POST', body: formData });
        fileInput.value = '';
        document.getElementById('photo-preview').classList.add('hidden');
        document.getElementById('upload-prompt').classList.remove('hidden');
        showToast('인증 요청 완료! 임원 승인을 기다려주세요.');
        await loadMemberHistory();
        await loadCalendar();
        switchTab('home');
    } catch (e) {
        showToast(e.message);
    }
}

function historyStatusMeta(status, lateMinutes) {
    switch (status) {
        case 'PENDING': return { label: '심사중', badge: 'text-gray-500 bg-gray-100 border-gray-200' };
        case 'PRESENT': return { label: '승인됨', badge: 'text-toss-green bg-green-50 border-green-100' };
        case 'LATE': return { label: `승인됨 (${lateMinutes}분 지각)`, badge: 'text-toss-green bg-green-50 border-green-100' };
        case 'ABSENT': return { label: '결석 처리', badge: 'text-toss-red bg-red-50 border-red-100' };
        case 'REJECTED': return { label: '반려됨', badge: 'text-toss-red bg-red-50 border-red-100' };
        default: return { label: status, badge: 'text-gray-500 bg-gray-100 border-gray-200' };
    }
}

async function loadMemberHistory() {
    try {
        const list = await api(`/api/members/${state.member.id}/attendance-records/history`);
        renderMemberHistory(list);
    } catch (e) {
        showToast(e.message);
    }
}

function renderMemberHistory(list) {
    const el = document.getElementById('member-history-list');
    el.innerHTML = '';
    if (list.length === 0) {
        el.innerHTML = '<p class="text-sm font-bold text-gray-400 text-center py-8 bg-gray-50 rounded-3xl border border-gray-100">아직 인증 내역이 없습니다.</p>';
        return;
    }
    list.forEach(r => {
        const meta = historyStatusMeta(r.status, r.lateMinutes);
        const photoSrc = r.photoUrl ? (r.photoUrl.startsWith('http') ? r.photoUrl : API_BASE + r.photoUrl) : 'https://placehold.co/100x100/E8F3FF/3182F6?text=%20';
        el.insertAdjacentHTML('beforeend', `
            <div class="flex items-center justify-between p-4 bg-white rounded-[24px] shadow-sm border border-gray-100">
                <div class="flex items-center space-x-4">
                    <img src="${photoSrc}" class="w-14 h-14 rounded-2xl object-cover border border-gray-100">
                    <div>
                        <p class="text-sm font-black text-toss-text mb-1">${formatTime(r.scheduledStartTime)} - ${formatTime(r.scheduledEndTime)}</p>
                        <p class="text-[11px] font-bold text-toss-subText bg-gray-50 inline-block px-2 py-0.5 rounded-md">${r.practiceDate}</p>
                    </div>
                </div>
                <span class="px-3 py-1.5 rounded-lg text-[10px] font-black border ${meta.badge}">${meta.label}</span>
            </div>
        `);
    });
}
