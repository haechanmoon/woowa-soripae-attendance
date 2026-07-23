// ---------- 합주 시간표 ----------

async function loadMySongs() {
    try {
        state.mySongs = await api(`/api/members/${state.member.id}/songs`);
        const listEl = document.getElementById('song-list');
        listEl.innerHTML = '';
        document.getElementById('song-empty-state').classList.toggle('hidden', state.mySongs.length > 0);
        state.mySongs.forEach(song => listEl.insertAdjacentHTML('beforeend', songCard(song)));
    } catch (e) {
        showToast(e.message);
    }
}

function songCard(song) {
    return `<button onclick="openSongSheet(${song.id})" class="w-full text-left bg-toss-card rounded-[24px] p-5 shadow-toss active:scale-[0.98] transition-transform flex items-center justify-between">
        <div>
            <p class="font-black text-toss-text text-base">${song.title}</p>
            ${song.artist ? `<p class="text-xs font-bold text-toss-subText mt-1">${song.artist}</p>` : ''}
        </div>
        <i class="fa-solid fa-chevron-right text-gray-300"></i>
    </button>`;
}

async function openSongSheet(songId) {
    try {
        state.currentSong = await api(`/api/songs/${songId}`);
        state.currentPoll = null;
        try {
            state.currentPoll = await api(`/api/songs/${songId}/polls/latest`);
        } catch (e) { /* 아직 조율이 없는 곡 */ }
        renderSongSheet();
        openSheet('song-sheet');
    } catch (e) {
        showToast(e.message);
    }
}

function isVocalForCurrentSong() {
    return state.currentSong.members.some(m => m.id === state.member.id && m.part === '보컬');
}

function renderSongSheet() {
    const song = state.currentSong;
    document.getElementById('song-sheet-title').textContent = song.title;
    document.getElementById('song-sheet-artist').textContent = song.artist || '';
    document.getElementById('song-sheet-members').innerHTML = song.members.map(m =>
        `<span class="text-[11px] font-bold text-toss-subText bg-gray-50 px-2.5 py-1.5 rounded-full border border-gray-100">${m.name}${m.part ? ` · ${m.part}` : ''}</span>`
    ).join('');
    document.getElementById('song-sheet-poll-area').innerHTML = renderPollArea();
}

function renderPollArea() {
    const poll = state.currentPoll;
    const iAmVocal = isVocalForCurrentSong();

    if (!poll || poll.status !== 'OPEN') {
        let confirmedHtml = '';
        if (poll && poll.status === 'CONFIRMED') {
            const slot = poll.slots.find(s => s.confirmed);
            confirmedHtml = `<div class="bg-green-50 border border-green-100 rounded-2xl p-4 mb-4">
                <p class="text-[11px] font-black text-toss-green mb-1"><i class="fa-solid fa-circle-check mr-1"></i>확정된 합주 시간</p>
                <p class="text-sm font-black text-toss-text">${formatSlotRange(slot)}</p>
                ${iAmVocal ? `<button onclick="cancelConfirmedPoll()" class="w-full mt-3 py-2.5 text-xs font-black rounded-xl bg-white text-toss-red border border-red-100 active:scale-95">확정 취소하고 다시 정하기</button>` : ''}
            </div>`;
        }
        if (iAmVocal) {
            return confirmedHtml + createPollFormHtml();
        }
        return confirmedHtml + `<p class="text-xs font-bold text-toss-subText text-center py-6">보컬이 합주 시간 후보를 올리면 여기에서 확인할 수 있어요.</p>`;
    }

    return poll.slots.map(slot => slotCardHtml(slot, iAmVocal)).join('');
}

async function cancelConfirmedPoll() {
    try {
        state.currentPoll = await api(`/api/polls/${state.currentPoll.pollId}/unconfirm`, {
            method: 'POST',
            body: JSON.stringify({ memberId: state.member.id })
        });
        renderSongSheet();
        showToast('확정을 취소했어요. 다른 후보 시간을 다시 골라주세요.');
    } catch (e) {
        showToast(e.message);
    }
}

function pollHourOptions() {
    let html = '';
    for (let h = 8; h < 24; h++) {
        const v = String(h).padStart(2, '0');
        html += `<option value="${v}">${v}시</option>`;
    }
    return html;
}

function pollMinuteOptions() {
    return ['00', '10', '20', '30', '40', '50'].map(m => `<option value="${m}">${m}분</option>`).join('');
}

function pollSlotGroupHtml() {
    return `<div class="poll-slot-group grid grid-cols-3 gap-1.5">
        <input type="date" min="${todayIso()}" value="${todayIso()}" class="poll-slot-date p-3 bg-white border border-gray-200 rounded-xl outline-none font-bold text-toss-text text-xs focus:border-toss-blue">
        <select class="poll-slot-hour p-3 bg-white border border-gray-200 rounded-xl outline-none font-bold text-toss-text text-xs focus:border-toss-blue">${pollHourOptions()}</select>
        <select class="poll-slot-minute p-3 bg-white border border-gray-200 rounded-xl outline-none font-bold text-toss-text text-xs focus:border-toss-blue">${pollMinuteOptions()}</select>
    </div>`;
}

function createPollFormHtml() {
    return `<div class="bg-gray-50 p-5 rounded-3xl border border-gray-100">
        <p class="text-sm font-black text-toss-text mb-1">합주 시간 후보 올리기</p>
        <p class="text-[11px] font-bold text-toss-subText mb-4">후보 시간을 2~4개 정도 올려서 팀원들의 가능 여부를 받아보세요. (1시간 고정)</p>
        <div id="poll-slot-inputs" class="space-y-2 mb-3">
            ${pollSlotGroupHtml()}
        </div>
        <button onclick="addPollSlotInput()" class="w-full text-xs font-black text-toss-blue py-2.5 mb-3">+ 후보 시간 추가</button>
        <button onclick="submitCreatePoll()" class="w-full bg-toss-blue text-white font-black text-base py-4 rounded-2xl shadow-[0_4px_14px_rgba(49,130,246,0.2)] active:scale-[0.98] transition-transform">
            조율 시작하기
        </button>
    </div>`;
}

function addPollSlotInput() {
    const container = document.getElementById('poll-slot-inputs');
    if (container.children.length >= 4) { showToast('후보 시간은 최대 4개까지 올릴 수 있어요.'); return; }
    container.insertAdjacentHTML('beforeend', pollSlotGroupHtml());
}

function toLocalIso(date) {
    const pad = n => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`;
}

async function submitCreatePoll() {
    const groups = Array.from(document.querySelectorAll('.poll-slot-group'));
    const slots = groups
        .map(g => {
            const date = g.querySelector('.poll-slot-date').value;
            if (!date) return null;
            const hour = g.querySelector('.poll-slot-hour').value;
            const minute = g.querySelector('.poll-slot-minute').value;
            const start = new Date(`${date}T${hour}:${minute}:00`);
            const end = new Date(start.getTime() + 60 * 60 * 1000);
            return { startAt: toLocalIso(start), endAt: toLocalIso(end) };
        })
        .filter(Boolean);

    if (slots.length === 0) return showToast('후보 시간을 1개 이상 입력해주세요.');

    try {
        state.currentPoll = await api(`/api/songs/${state.currentSong.id}/polls`, {
            method: 'POST',
            body: JSON.stringify({ creatorMemberId: state.member.id, slots })
        });
        renderSongSheet();
        showToast('합주 시간 조율을 시작했어요.');
    } catch (e) {
        showToast(e.message);
    }
}

function formatSlotRange(slot) {
    const days = ['일', '월', '화', '수', '목', '금', '토'];
    const pad = n => String(n).padStart(2, '0');
    const start = new Date(slot.startAt);
    const end = new Date(slot.endAt);
    return `${start.getMonth() + 1}/${start.getDate()}(${days[start.getDay()]}) ${pad(start.getHours())}:${pad(start.getMinutes())}~${pad(end.getHours())}:${pad(end.getMinutes())}`;
}

function slotCardHtml(slot, iAmVocal) {
    const myResponse = slot.responses.find(r => r.memberId === state.member.id);
    const myAvailability = myResponse ? myResponse.availability : null;
    const availableCount = slot.responses.filter(r => r.availability === 'AVAILABLE').length;
    const total = slot.responses.length;

    const chips = slot.responses.map(r => {
        let cls = 'bg-gray-100 text-gray-400';
        let icon = '';
        if (r.availability === 'AVAILABLE') { cls = 'bg-green-50 text-toss-green border border-green-100'; icon = '<i class="fa-solid fa-check mr-1"></i>'; }
        else if (r.availability === 'UNAVAILABLE') { cls = 'bg-red-50 text-toss-red border border-red-100'; icon = '<i class="fa-solid fa-xmark mr-1"></i>'; }
        return `<span class="text-[10px] font-bold px-2 py-1 rounded-full ${cls}">${icon}${r.memberName}</span>`;
    }).join('');

    return `<div class="bg-gray-50 p-4 rounded-2xl border border-gray-100 mb-3">
        <div class="flex justify-between items-start mb-2">
            <p class="text-sm font-black text-toss-text">${formatSlotRange(slot)}</p>
            <span class="text-[10px] font-black text-toss-blue bg-blue-50 px-2 py-1 rounded-full">${availableCount}/${total} 가능</span>
        </div>
        <div class="flex flex-wrap gap-1.5 mb-3">${chips}</div>
        <div class="flex space-x-2">
            <button onclick="respondSlot(${slot.slotId}, 'AVAILABLE')" class="flex-1 py-2.5 text-xs font-black rounded-xl border transition-colors ${myAvailability === 'AVAILABLE' ? 'bg-toss-green text-white border-toss-green' : 'bg-white text-gray-400 border-gray-200'}">가능</button>
            <button onclick="respondSlot(${slot.slotId}, 'UNAVAILABLE')" class="flex-1 py-2.5 text-xs font-black rounded-xl border transition-colors ${myAvailability === 'UNAVAILABLE' ? 'bg-toss-red text-white border-toss-red' : 'bg-white text-gray-400 border-gray-200'}">불가능</button>
        </div>
        ${iAmVocal ? `<button onclick="confirmSlot(${slot.slotId})" class="w-full mt-2 py-2.5 text-xs font-black rounded-xl bg-gray-800 text-white active:bg-gray-900">이 시간으로 확정</button>` : ''}
    </div>`;
}

async function respondSlot(slotId, availability) {
    try {
        state.currentPoll = await api(`/api/polls/${state.currentPoll.pollId}/slots/${slotId}/responses`, {
            method: 'PUT',
            body: JSON.stringify({ memberId: state.member.id, availability })
        });
        renderSongSheet();
    } catch (e) {
        showToast(e.message);
    }
}

async function confirmSlot(slotId) {
    if (!confirm('이 시간으로 합주 일정을 확정할까요?')) return;
    try {
        state.currentPoll = await api(`/api/polls/${state.currentPoll.pollId}/confirm`, {
            method: 'POST',
            body: JSON.stringify({ memberId: state.member.id, slotId })
        });
        renderSongSheet();
        showToast('합주 시간이 확정되었습니다.');
    } catch (e) {
        showToast(e.message);
    }
}
