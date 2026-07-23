// file://로 직접 열었을 때만 배포된 EC2 API를 바라보고, 그 외(Spring Boot가 서빙하는 경우)엔 같은 오리진(상대경로)을 사용한다.
const API_BASE = location.protocol === 'file:' ? 'http://13.209.69.115:8080' : '';

const state = {
    member: null,
    isAdminAuthenticated: false,
    calYear: new Date().getFullYear(),
    calMonth: new Date().getMonth() + 1,
    calendarRecords: [],
    allMembers: [],
    mySongs: [],
    currentSong: null,
    currentPoll: null,
};

const CORE_START = '13:00';
const CORE_END = '15:00';
const DAY_LABEL = { MONDAY: '월', TUESDAY: '화', WEDNESDAY: '수', THURSDAY: '목', FRIDAY: '금', SATURDAY: '토', SUNDAY: '일' };

async function api(path, options = {}) {
    const isForm = options.body instanceof FormData;
    const headers = isForm ? (options.headers || {}) : { 'Content-Type': 'application/json', ...(options.headers || {}) };
    const res = await fetch(API_BASE + path, { ...options, headers });
    if (!res.ok) {
        let message = `요청 실패 (${res.status})`;
        try {
            const body = await res.json();
            if (body && body.message) message = body.message;
        } catch (e) { /* no json body */ }
        throw new Error(message);
    }
    if (res.status === 204) return null;
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

function formatTime(t) {
    return t ? t.substring(0, 5) : '';
}

function formatDateTime(dt) {
    if (!dt) return '';
    const timePart = dt.split('T')[1] || '';
    return timePart.substring(0, 8);
}

function todayIso() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function formatTodayKorean() {
    const d = new Date();
    const days = ['일', '월', '화', '수', '목', '금', '토'];
    return `${d.getFullYear()}. ${String(d.getMonth() + 1).padStart(2, '0')}. ${String(d.getDate()).padStart(2, '0')} (${days[d.getDay()]})`;
}

function updateClock() {
    const now = new Date();
    const timeStr = now.toLocaleTimeString('ko-KR', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const clockEl = document.getElementById('current-clock');
    const submitEl = document.getElementById('submit-time-text');
    if (clockEl) clockEl.textContent = timeStr;
    if (submitEl) submitEl.textContent = timeStr;
}
setInterval(updateClock, 1000);
updateClock();

function showToast(msg) {
    const toast = document.getElementById('toast');
    toast.textContent = msg;
    toast.style.opacity = '1';
    toast.style.transform = 'translate(-50%, 0)';
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translate(-50%, -20px)';
    }, 2500);
}

function statusMeta(status, lateMinutes) {
    switch (status) {
        case 'PRESENT': return { label: '정상 출석', dot: 'bg-toss-green text-white', badge: 'bg-green-50 text-toss-green border-green-100' };
        case 'LATE': return { label: `지각 (${lateMinutes}분)`, dot: 'bg-orange-400 text-white', badge: 'bg-orange-50 text-orange-500 border-orange-100' };
        case 'ABSENT': return { label: '결석', dot: 'bg-toss-red text-white', badge: 'bg-red-50 text-toss-red border-red-100' };
        case 'PENDING': return { label: '예정 (심사중)', dot: 'border-2 border-toss-blue text-toss-blue bg-blue-50', badge: 'bg-blue-50 text-toss-blue border-blue-100' };
        case 'SCHEDULED': return { label: '예정 (인증 전)', dot: 'border-2 border-dashed border-toss-blue text-toss-blue bg-white', badge: 'bg-blue-50 text-toss-blue border-blue-100' };
        case 'REJECTED': return { label: '반려됨', dot: 'bg-gray-300 text-white', badge: 'bg-gray-100 text-gray-500 border-gray-200' };
        default: return { label: status, dot: 'bg-gray-200 text-white', badge: 'bg-gray-100 text-gray-500 border-gray-200' };
    }
}
