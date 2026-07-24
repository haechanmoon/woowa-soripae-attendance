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

/** 큰 원본 사진을 화면·업로드·임시저장에 알맞게 축소한 JPEG dataURL로 변환한다.
 *  (localStorage 용량 절약 + 업로드 경량화) */
function fileToResizedDataUrl(file, maxDim = 1600, quality = 0.85) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
            const img = new Image();
            img.onload = () => {
                let { width, height } = img;
                if (width > maxDim || height > maxDim) {
                    if (width >= height) { height = Math.round(height * maxDim / width); width = maxDim; }
                    else { width = Math.round(width * maxDim / height); height = maxDim; }
                }
                const canvas = document.createElement('canvas');
                canvas.width = width;
                canvas.height = height;
                canvas.getContext('2d').drawImage(img, 0, 0, width, height);
                resolve(canvas.toDataURL('image/jpeg', quality));
            };
            img.onerror = reject;
            img.src = reader.result;
        };
        reader.onerror = reject;
        reader.readAsDataURL(file);
    });
}

function dataUrlToBlob(dataUrl) {
    const [head, b64] = dataUrl.split(',');
    const mime = (head.match(/:(.*?);/) || [])[1] || 'image/jpeg';
    const bin = atob(b64);
    const arr = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
    return new Blob([arr], { type: mime });
}

/** 고른 사진을 미리보기에 표시하고, 카메라 실행으로 앱이 새로고침돼도 잃지 않도록 임시 저장한다. */
function setCertifyPhoto(dataUrl) {
    state.pendingPhotoDataUrl = dataUrl;
    document.getElementById('photo-preview').src = dataUrl;
    document.getElementById('photo-preview').classList.remove('hidden');
    document.getElementById('upload-prompt').classList.add('hidden');
    try {
        localStorage.setItem('soripae_pending_photo', dataUrl);
        localStorage.setItem('soripae_pending_schedule', document.getElementById('certify-schedule').value || '');
    } catch (e) {
        // 용량 초과 등으로 저장이 안 돼도 현재 세션에서는 제출 가능하므로 무시한다.
    }
}

function clearPendingPhoto() {
    state.pendingPhotoDataUrl = null;
    localStorage.removeItem('soripae_pending_photo');
    localStorage.removeItem('soripae_pending_schedule');
    document.getElementById('photo-preview').classList.add('hidden');
    document.getElementById('upload-prompt').classList.remove('hidden');
}

/** 사진 촬영 후 앱(특히 설치형 삼성 인터넷 PWA)이 새로고침되며 인증 화면을 벗어난 경우,
 *  임시 저장해둔 사진을 인증 화면에 복원해 이어서 제출할 수 있게 한다. */
async function restorePendingPhoto() {
    const dataUrl = localStorage.getItem('soripae_pending_photo');
    if (!dataUrl) return;
    switchTab('certify');
    await loadCertifySchedules();
    loadMemberHistory();
    const saved = localStorage.getItem('soripae_pending_schedule');
    const sel = document.getElementById('certify-schedule');
    if (saved && [...sel.options].some(o => o.value === saved)) sel.value = saved;
    setCertifyPhoto(dataUrl);
    showToast('올려둔 사진을 복원했어요. 인증 요청을 이어서 눌러주세요!');
}

async function previewPhoto(input) {
    if (!input.files || !input.files[0]) return;
    try {
        const dataUrl = await fileToResizedDataUrl(input.files[0]);
        setCertifyPhoto(dataUrl);
    } catch (e) {
        showToast('사진을 불러오지 못했어요. 다시 시도해주세요.');
    }
}

async function submitAttendance() {
    const scheduledVal = document.getElementById('certify-schedule').value;
    const dataUrl = state.pendingPhotoDataUrl || localStorage.getItem('soripae_pending_photo');
    if (!dataUrl) return showToast('인증 사진을 올려주세요.');

    const formData = new FormData();
    formData.append('memberId', state.member.id);
    formData.append('scheduledStartTime', scheduledVal);
    formData.append('photo', dataUrlToBlob(dataUrl), 'attendance.jpg');

    try {
        await api('/api/attendance-records', { method: 'POST', body: formData });
        clearPendingPhoto();
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
