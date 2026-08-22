#!/bin/bash
#
# 소리패 접속 통계 리포트 생성기.
#
# nginx access log를 읽어 정적 HTML 하나를 만든다. 브라우저에 심는 분석 스크립트를 쓰지 않는 이유:
# 이 앱은 이미 광고 차단기 때문에 API가 막혀 한참 헤맨 적이 있어(/api/events 사건), 클라이언트에서
# 수집하는 방식은 상당수가 집계에서 빠진다. 서버 로그는 차단당하지 않는다.
#
# 반드시 보정해야 하는 두 가지. 안 하면 숫자가 통째로 틀린다:
#  1) 봇 - 전체 요청의 74%가 404였고, 스캐너 하나가 하루 3만 건을 때린 적도 있다.
#     처음엔 "요청 200건 초과"로 걸렀는데 40~200건짜리 중소형 스캐너가 다 빠져나가
#     새벽 시간대가 부풀었다. 그래서 기준을 뒤집었다 - 아래 '사람' 정의 참고.
#  2) 시각 - EC2 OS는 UTC라 로그도 UTC로 찍힌다(앱만 KST 고정). 변환 안 하면 9시간 어긋난다.
#
# pipefail은 쓰지 않는다. `sort | head -1` 처럼 뒤가 먼저 닫히는 파이프라인에서 앞 명령이
# SIGPIPE(141)로 죽는 게 정상인데 pipefail이 그걸 실패로 잡아 스크립트를 멈춘다.
set -eu

OUT_DIR=/var/www/soripae-stats
OUT="$OUT_DIR/index.html"
DAYS_SHOWN=21

mkdir -p "$OUT_DIR"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# 로그를 한 번 읽어 (KST날짜, KST시각, IP, 경로, 로그인화면여부) 로 정규화한다.
zcat -f /var/log/nginx/access.log* 2>/dev/null | gawk '
BEGIN {
    split("Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec", mn, " ")
    for (i = 1; i <= 12; i++) mon[mn[i]] = i
    OFS = "\t"
}
{
    split(substr($4, 2), a, /[\/:]/)          # $4 = [27/Jul/2026:03:51:30  (UTC)
    if (a[3] == "" || mon[a[2]] == "") next
    # 서버 TZ가 UTC라 mktime 결과가 곧 epoch. 9시간 더해 KST 벽시계로 바꾼다.
    kst = mktime(a[3] " " mon[a[2]] " " a[1] " " a[4] " " a[5] " " a[6]) + 9 * 3600
    if (kst <= 0) next
    path = $7
    sub(/\?.*$/, "", path)                     # 쿼리스트링 제거
    # /members/3/schedules -> /members/{id}/schedules
    # gawk는 치환문에서 \1 역참조를 지원하지 않는다(& 만 됨). 그래서 중간/끝을 나눠 처리한다.
    gsub(/\/[0-9]+\//, "/{id}/", path)
    sub(/\/[0-9]+$/, "/{id}", path)
    print strftime("%Y-%m-%d", kst), strftime("%H", kst), $1, path, ($7 == "/api/members" ? 1 : 0)
}' > "$TMP/norm.tsv"

# '사람'의 정의: 로그인 화면(/api/members)을 한 번이라도 부른 IP.
# 앱을 열면 반드시 부르는 요청이라 사람이면 무조건 찍힌다. 반대로 스캐너는 이 경로를 모른다.
# 요청 건수로 자르지 않고 이 행동 하나로 가르기 때문에, 조용한 소형 스캐너도 새 스캐너도 자동으로 빠진다.
gawk -F'\t' '$5 == 1 { print $3 }' "$TMP/norm.tsv" | sort -u > "$TMP/humans.txt"
gawk -F'\t' 'NR == FNR { h[$1] = 1; next } ($3 in h)' "$TMP/humans.txt" "$TMP/norm.tsv" > "$TMP/human.tsv"
gawk -F'\t' 'NR == FNR { h[$1] = 1; next } !($3 in h)' "$TMP/humans.txt" "$TMP/norm.tsv" > "$TMP/bot.tsv"

# 날짜별 접속자(고유 IP) / 요청 수
gawk -F'\t' '$5 == 1 { s[$1 "\t" $3] = 1 } END { for (k in s) { split(k, p, "\t"); c[p[1]]++ } for (d in c) print d, c[d] }' \
    "$TMP/human.tsv" | sort > "$TMP/visitors.txt"
gawk -F'\t' '{ c[$1]++ } END { for (d in c) print d, c[d] }' "$TMP/human.tsv" | sort > "$TMP/requests.txt"

TODAY=$(TZ=Asia/Seoul date +%Y-%m-%d)
today_v=$(awk -v d="$TODAY" '$1 == d { print $2 }' "$TMP/visitors.txt"); today_v=${today_v:-0}
yest_v=$(awk -v d="$(TZ=Asia/Seoul date -d yesterday +%Y-%m-%d)" '$1 == d { print $2 }' "$TMP/visitors.txt"); yest_v=${yest_v:-0}
avg7=$(grep -v "^$TODAY " "$TMP/visitors.txt" | tail -7 | awk '{ s += $2; n++ } END { printf "%.0f", (n ? s / n : 0) }')
peak_v=$(sort -k2 -rn "$TMP/visitors.txt" | head -1 | awk '{ print $2 + 0 }')
peak_d=$(sort -k2 -rn "$TMP/visitors.txt" | head -1 | awk '{ print substr($1, 6) }')
total_h=$(wc -l < "$TMP/humans.txt")
bot_ips=$(gawk -F'\t' '{ print $3 }' "$TMP/bot.tsv" | sort -u | wc -l)
bot_req=$(wc -l < "$TMP/bot.tsv")
span=$(wc -l < "$TMP/visitors.txt")

# ---------- SVG: 날짜별 접속자 (단일 계열 -> 범례 없음, 선 2px + 10% 면, 끝점만 직접 라벨) ----------
daily_svg() {
    tail -"$DAYS_SHOWN" "$TMP/visitors.txt" | gawk '
    { d[++n] = $1; v[n] = $2; if ($2 > max) max = $2 }
    END {
        if (n == 0) { print "<p class=\"empty\">데이터가 아직 없습니다.</p>"; exit }
        L = 38; R = 8; T = 16; B = 190; W = 720
        if (max < 1) max = 1
        span = (n > 1) ? (W - L - R) / (n - 1) : 0
        for (i = 1; i <= n; i++) {
            x[i] = (n > 1) ? L + (i - 1) * span : (L + W - R) / 2
            y[i] = B - (v[i] / max) * (B - T)
        }
        printf "<svg viewBox=\"0 0 %d 214\" preserveAspectRatio=\"none\" class=\"chart\" role=\"img\" aria-label=\"날짜별 접속자 추이\">\n", W
        # 가로 기준선 3개 - 하드라인, 낮은 대비
        for (g = 0; g <= 2; g++) {
            gy = B - g * (B - T) / 2
            printf "  <line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" class=\"grid\"/>\n", L, gy, W - R, gy
            printf "  <text x=\"%d\" y=\"%.1f\" class=\"ytick\">%d</text>\n", L - 8, gy + 4, int(max * g / 2 + 0.5)
        }
        # 면(10% 워시) + 선(2px)
        path = ""
        for (i = 1; i <= n; i++) path = path sprintf("%s%.1f,%.1f ", (i == 1 ? "M" : "L"), x[i], y[i])
        printf "  <path d=\"M%.1f,%d %sL%.1f,%d Z\" class=\"area\"/>\n", x[1], B, path, x[n], B
        printf "  <path d=\"%s\" class=\"line\"/>\n", path
        # 점 - 각 점에 <title>로 네이티브 툴팁, 히트 영역은 마크보다 크게
        for (i = 1; i <= n; i++) {
            printf "  <g class=\"pt\"><circle cx=\"%.1f\" cy=\"%.1f\" r=\"11\" class=\"hit\"/>", x[i], y[i]
            printf "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"4\" class=\"dot\"/>", x[i], y[i]
            printf "<title>%s · %d명</title></g>\n", d[i], v[i]
        }
        # 직접 라벨은 아껴서 - 마지막 점만
        printf "  <text x=\"%.1f\" y=\"%.1f\" class=\"endlab\">%d명</text>\n", x[n], (y[n] > T + 22 ? y[n] - 12 : y[n] + 20), v[n]
        # x축: 처음/중간/마지막만
        for (i = 1; i <= n; i++) {
            if (i == 1 || i == n || i == int((n + 1) / 2))
                printf "  <text x=\"%.1f\" y=\"208\" class=\"xtick\" text-anchor=\"%s\">%s</text>\n",
                    x[i], (i == 1 ? "start" : (i == n ? "end" : "middle")), substr(d[i], 6)
        }
        print "</svg>"
    }'
}

# ---------- SVG: 시간대별 (막대 24px 상한, 윗모서리 4px 라운드 / 바닥은 각) ----------
hourly_svg() {
    gawk -F'\t' '{ c[$2 + 0]++ } END { for (h = 0; h < 24; h++) print h, c[h] + 0 }' "$TMP/human.tsv" | sort -n | gawk '
    { h[++n] = $1; v[n] = $2; if ($2 > max) max = $2 }
    END {
        L = 38; R = 8; T = 14; B = 150; W = 720; r = 4
        if (max < 1) max = 1
        band = (W - L - R) / 24
        bw = (band - 6 > 24) ? 24 : band - 6      # 슬롯을 꽉 채우지 않는다 - 남는 폭이 공기
        printf "<svg viewBox=\"0 0 %d 174\" preserveAspectRatio=\"none\" class=\"chart\" role=\"img\" aria-label=\"시간대별 요청 분포\">\n", W
        for (g = 0; g <= 2; g++) {
            gy = B - g * (B - T) / 2
            printf "  <line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" class=\"grid\"/>\n", L, gy, W - R, gy
            printf "  <text x=\"%d\" y=\"%.1f\" class=\"ytick\">%d</text>\n", L - 8, gy + 4, int(max * g / 2 + 0.5)
        }
        for (i = 1; i <= n; i++) {
            bx = L + (i - 1) * band + (band - bw) / 2
            bh = (v[i] / max) * (B - T)
            if (bh < 0.5) bh = 0.5
            by = B - bh
            rr = (bh < r) ? bh : r
            printf "  <g class=\"pt\"><path d=\"M%.1f,%.1f V%.1f Q%.1f,%.1f %.1f,%.1f H%.1f Q%.1f,%.1f %.1f,%.1f V%.1f Z\" class=\"bar\"/>",
                bx, B, by + rr, bx, by, bx + rr, by, bx + bw - rr, bx + bw, by, bx + bw, by + rr, B
            printf "<title>%02d시 · %d건</title></g>\n", h[i], v[i]
        }
        for (i = 1; i <= n; i++)
            if (h[i] % 6 == 0 || h[i] == 23)
                printf "  <text x=\"%.1f\" y=\"168\" class=\"xtick\" text-anchor=\"middle\">%02d</text>\n",
                    L + (i - 1) * band + band / 2, h[i]
        print "</svg>"
    }'
}

# 주소만 보면 무슨 화면인지 알 수 없어 한글 설명을 붙인다.
label_for() {
    case "$1" in
        "/") echo "홈 화면" ;;
        "/api/members") echo "로그인 · 이름 선택" ;;
        "/api/members/{id}/schedules") echo "내 스케줄" ;;
        "/api/members/{id}/fines") echo "내 벌금" ;;
        "/api/members/{id}/attendance-records") echo "내 출석 기록" ;;
        "/api/members/{id}") echo "내 정보" ;;
        "/api/performances") echo "공연 · 행사" ;;
        # 옛 주소들. 캐시가 남은 기기가 아직 부르고 있어 404가 난다.
        "/api/events"|"/api/club-events") echo "공연 · 행사 (옛 주소)" ;;
        "/api/attendance-records") echo "출석 기록" ;;
        "/api/attendance-records/pending") echo "사진 승인 대기 · 임원" ;;
        "/api/attendance-records/uncertified") echo "오늘 미인증 · 임원" ;;
        "/api/schedules") echo "오늘 등록자 · 임원" ;;
        "/api/schedules/weekly") echo "요일별 합주 현황" ;;
        "/api/schedules/registration") echo "스케줄 등록 현황 · 임원" ;;
        "/api/schedules/this-week-changes") echo "이번 주 변경 내역 · 임원" ;;
        "/api/schedules/next-week-registration") echo "스케줄 등록 현황 · 임원 (옛 주소)" ;;
        "/api/members/{id}/attendance-records/history") echo "내 지난 기록" ;;
        "/api/songs") echo "곡 목록" ;;
        "/api/songs/"*) echo "곡 상세" ;;
        "/api/admin/login") echo "임원 로그인" ;;
        *) echo "-" ;;
    esac
}

{
cat <<'HEAD'
<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>소리패 접속 통계</title>
<style>
:root {
  color-scheme: light;
  --surface: #fcfcfb; --card: #ffffff; --line: #e7e7e4;
  --ink: #0b0b0b; --ink-2: #52514e; --ink-3: #8b8b85;
  --series: #2a78d6;
}
@media (prefers-color-scheme: dark) {
  :root:where(:not([data-theme="light"])) {
    color-scheme: dark;
    --surface: #17171a; --card: #1f1f22; --line: #2e2e33;
    --ink: #ffffff; --ink-2: #c3c2b7; --ink-3: #8b8b85;
    --series: #3987e5;
  }
}
:root[data-theme="dark"] {
  color-scheme: dark;
  --surface: #17171a; --card: #1f1f22; --line: #2e2e33;
  --ink: #ffffff; --ink-2: #c3c2b7; --ink-3: #8b8b85;
  --series: #3987e5;
}
* { box-sizing: border-box; }
body { margin: 0; padding: 28px 16px 56px; background: var(--surface); color: var(--ink);
       font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif;
       -webkit-font-smoothing: antialiased; }
.wrap { max-width: 760px; margin: 0 auto; }
header { margin-bottom: 24px; }
h1 { font-size: 24px; font-weight: 800; margin: 0 0 6px; letter-spacing: -0.02em; }
.sub { font-size: 13px; color: var(--ink-2); margin: 0; font-weight: 600; line-height: 1.6; }
.gen { font-size: 12px; color: var(--ink-3); margin: 6px 0 0; font-weight: 600; }
.kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin-bottom: 20px; }
.kpi { background: var(--card); border: 1px solid var(--line); border-radius: 18px; padding: 18px 20px; }
.kpi .n { font-size: 34px; font-weight: 800; letter-spacing: -0.03em; line-height: 1.05; }
.kpi .n small { font-size: 15px; font-weight: 700; color: var(--ink-3); margin-left: 2px; }
.kpi .l { font-size: 12px; color: var(--ink-2); font-weight: 700; margin-top: 6px; }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 20px; padding: 22px; margin-bottom: 16px; }
h2 { font-size: 15px; font-weight: 800; margin: 0 0 2px; }
.cap { font-size: 12px; color: var(--ink-3); font-weight: 600; margin: 0 0 16px; }
.chart { width: 100%; height: auto; display: block; overflow: visible; }
.grid { stroke: var(--line); stroke-width: 1; }
.ytick { fill: var(--ink-3); font-size: 11px; font-weight: 700; text-anchor: end; }
.xtick { fill: var(--ink-3); font-size: 11px; font-weight: 700; }
.area { fill: var(--series); opacity: 0.10; }
.line { fill: none; stroke: var(--series); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
.dot { fill: var(--series); stroke: var(--card); stroke-width: 2; }
.bar { fill: var(--series); }
.hit { fill: transparent; }
.pt { cursor: default; }
.pt:hover .dot { r: 6; }
.pt:hover .bar { opacity: 0.72; }
.endlab { fill: var(--ink); font-size: 12px; font-weight: 800; text-anchor: middle; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; font-size: 11px; font-weight: 800; color: var(--ink-3); padding: 0 8px 10px 0;
     border-bottom: 1px solid var(--line); white-space: nowrap; }
td { padding: 11px 8px 11px 0; border-bottom: 1px solid var(--line); font-weight: 700; vertical-align: middle; }
tr:last-child td { border-bottom: 0; }
td.desc { color: var(--ink); }
td.uri { color: var(--ink-3); font-size: 11px; font-weight: 600;
         font-family: ui-monospace, SFMono-Regular, Menlo, monospace; word-break: break-all; }
td.num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; color: var(--ink); }
.note { font-size: 12px; color: var(--ink-3); line-height: 1.7; font-weight: 600; margin: 14px 0 0; }
.empty { font-size: 13px; color: var(--ink-3); font-weight: 600; text-align: center; padding: 24px 0; margin: 0; }
details { margin-top: 4px; }
summary { font-size: 13px; font-weight: 800; cursor: pointer; color: var(--ink-2); padding: 4px 0; }
footer { font-size: 11px; color: var(--ink-3); font-weight: 600; line-height: 1.7; margin-top: 20px; text-align: center; }
</style></head><body><div class="wrap">
HEAD

echo "<header><h1>소리패 접속 통계</h1>"
echo "<p class=\"sub\">서버 접속 기록을 집계한 페이지입니다. 부원들이 언제 얼마나 앱을 쓰는지 보여줍니다.</p>"
echo "<p class=\"gen\">$(TZ=Asia/Seoul date '+%Y년 %-m월 %-d일 %H:%M') 기준 · 매시간 자동 갱신</p>"
[ -f "$OUT_DIR/updates.html" ] && echo '<p style="margin:10px 0 0"><a href="updates.html" style="font-size:12px;font-weight:800;color:var(--series);text-decoration:none">업데이트 내역 보기 →</a></p>'
echo "</header>"

echo '<div class="kpis">'
echo "<div class=\"kpi\"><div class=\"n\">${today_v}<small>명</small></div><div class=\"l\">오늘 접속자</div></div>"
echo "<div class=\"kpi\"><div class=\"n\">${yest_v}<small>명</small></div><div class=\"l\">어제</div></div>"
echo "<div class=\"kpi\"><div class=\"n\">${avg7}<small>명</small></div><div class=\"l\">최근 7일 평균</div></div>"
echo "<div class=\"kpi\"><div class=\"n\">${peak_v}<small>명</small></div><div class=\"l\">최다 · ${peak_d}</div></div>"
echo '</div>'

echo '<div class="card"><h2>날짜별 접속자</h2><p class="cap">최근 '"$DAYS_SHOWN"'일 · 점 위에 올리면 날짜별 수치가 보입니다</p>'
daily_svg
echo '</div>'

echo '<div class="card"><h2>시간대별 이용량</h2><p class="cap">한국 시간 기준 · 전체 기간 요청 수 합계</p>'
hourly_svg
echo '</div>'

echo '<div class="card"><h2>많이 열린 화면</h2><p class="cap">부원들이 실제로 무엇을 보는지</p>'
echo '<table><thead><tr><th>설명</th><th>주소</th><th style="text-align:right">횟수</th></tr></thead><tbody>'
gawk -F'\t' '$4 ~ /^\/api\// || $4 == "/" { c[$4]++ } END { for (p in c) print c[p], p }' "$TMP/human.tsv" \
    | sort -rn | head -12 | while read -r c p; do
        echo "<tr><td class=\"desc\">$(label_for "$p")</td><td class=\"uri\">${p}</td><td class=\"num\">${c}</td></tr>"
    done
echo '</tbody></table></div>'

echo '<div class="card"><details><summary>집계에서 제외한 자동 접속 (봇)</summary>'
echo "<p class=\"note\">인터넷에 공개된 서버는 늘 자동 스캔을 받습니다. 이 기간 동안 <strong>${bot_ips}개 주소에서 ${bot_req}건</strong>이 들어왔고, 위 통계에서 전부 빠져 있습니다.</p>"
echo "<p class=\"note\">사람인지 판별하는 기준은 요청 횟수가 아니라 <strong>로그인 화면을 불렀는지</strong>입니다. 앱을 열면 반드시 거치는 요청이라 사람이면 무조건 남고, 스캐너는 이 주소를 모릅니다. 그래서 새 스캐너가 와도 자동으로 걸러집니다.</p>"
echo '</details></div>'

echo "<footer>보관된 기록 ${span}일치 · 접속자 ${total_h}명 기준<br>"
echo "고유 IP로 세기 때문에 같은 와이파이를 쓰면 여러 명이 한 명으로 합쳐지고, 데이터 통신은 주소가 바뀌어 한 명이 둘로 셀 수 있습니다.</footer>"
echo '</div></body></html>'
} > "$OUT.tmp"

mv "$OUT.tmp" "$OUT"
chmod 644 "$OUT"

# 저장소의 CHANGELOG.md 를 같은 자리에 렌더링한다. EC2는 git pull 로 코드를 받으므로
# 원본은 저장소의 마크다운 하나뿐이고, 배포하면 이 페이지도 자동으로 최신이 된다.
APP_DIR=${APP_DIR:-/home/ec2-user/app}
if [ -f "$APP_DIR/docs/CHANGELOG.md" ] && [ -x "$APP_DIR/ops/render-changelog.py" ]; then
    python3 "$APP_DIR/ops/render-changelog.py" "$APP_DIR/docs/CHANGELOG.md" "$OUT_DIR/updates.html" > /dev/null
    chmod 644 "$OUT_DIR/updates.html"
fi
