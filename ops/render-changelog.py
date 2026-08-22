#!/usr/bin/env python3
"""docs/CHANGELOG.md 를 통계 페이지와 같은 모양의 HTML로 바꾼다.

원본을 저장소의 마크다운 하나로 두기 위한 스크립트다. EC2는 git pull 로 코드를 받으므로
CHANGELOG.md 가 서버에 이미 들어와 있고, 이걸 그 자리에서 렌더링하면 문서를 두 벌 관리할 일이 없다.

의존성 없이 표준 라이브러리만 쓴다. 서버에 pip 패키지를 새로 얹지 않기 위해서다.
CHANGELOG 가 쓰는 문법(제목/구분선/목록/굵게/코드/링크)만 처리한다.
"""
import html
import re
import sys
from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))


def inline(text: str) -> str:
    """굵게, 인라인 코드, 링크. 이스케이프를 먼저 하고 태그를 넣어야 원문의 <, & 가 깨지지 않는다."""
    out = html.escape(text)
    out = re.sub(r"`([^`]+)`", r"<code>\1</code>", out)
    out = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", out)
    # 저장소 안의 다른 마크다운을 가리키는 링크는 이 페이지에서 열 수 없어 텍스트만 남긴다.
    out = re.sub(r"\[([^\]]+)\]\((?!https?://)[^)]+\)", r"\1", out)
    out = re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", r'<a href="\2">\1</a>', out)
    return out


def render(md: str) -> str:
    parts, buf = [], []

    def flush_list():
        if buf:
            parts.append("<ul>" + "".join(f"<li>{inline(x)}</li>" for x in buf) + "</ul>")
            buf.clear()

    for raw in md.splitlines():
        line = raw.rstrip()
        if line.startswith("- "):
            buf.append(line[2:])
            continue
        flush_list()
        if not line:
            continue
        if line.startswith("### "):
            parts.append(f"<h3>{inline(line[4:])}</h3>")
        elif line.startswith("## "):
            parts.append(f'<h2 class="entry">{inline(line[3:])}</h2>')
        elif line.startswith("# "):
            parts.append(f"<h1>{inline(line[2:])}</h1>")
        elif set(line) == {"-"} and len(line) >= 3:
            pass  # 구분선은 항목 제목이 이미 나누므로 버린다
        else:
            parts.append(f"<p>{inline(line)}</p>")
    flush_list()
    return "\n".join(parts)


STYLE = """
:root { color-scheme: light;
  --surface:#fcfcfb; --card:#ffffff; --line:#e7e7e4;
  --ink:#0b0b0b; --ink-2:#52514e; --ink-3:#8b8b85; --series:#2a78d6; }
@media (prefers-color-scheme: dark) { :root:where(:not([data-theme="light"])) {
  color-scheme: dark;
  --surface:#17171a; --card:#1f1f22; --line:#2e2e33;
  --ink:#ffffff; --ink-2:#c3c2b7; --ink-3:#8b8b85; --series:#3987e5; } }
:root[data-theme="dark"] {
  color-scheme: dark;
  --surface:#17171a; --card:#1f1f22; --line:#2e2e33;
  --ink:#ffffff; --ink-2:#c3c2b7; --ink-3:#8b8b85; --series:#3987e5; }
*{box-sizing:border-box}
body{margin:0;padding:28px 16px 56px;background:var(--surface);color:var(--ink);
  font-family:-apple-system,BlinkMacSystemFont,"Apple SD Gothic Neo","Malgun Gothic",sans-serif;
  -webkit-font-smoothing:antialiased;line-height:1.7}
.wrap{max-width:760px;margin:0 auto}
.back{display:inline-block;font-size:12px;font-weight:800;color:var(--series);
  text-decoration:none;margin-bottom:18px}
h1{font-size:24px;font-weight:800;margin:0 0 6px;letter-spacing:-.02em}
h2.entry{font-size:16px;font-weight:800;margin:0 0 14px;padding-bottom:12px;
  border-bottom:1px solid var(--line)}
h3{font-size:13px;font-weight:800;margin:18px 0 6px;color:var(--ink-2)}
p{font-size:13px;font-weight:600;color:var(--ink-2);margin:0 0 10px}
ul{margin:0 0 4px;padding-left:18px}
li{font-size:13px;font-weight:600;color:var(--ink-2);margin-bottom:7px}
strong{color:var(--ink);font-weight:800}
code{background:var(--line);padding:1px 5px;border-radius:4px;font-size:11px;
  font-family:ui-monospace,SFMono-Regular,Menlo,monospace}
a{color:var(--series)}
.card{background:var(--card);border:1px solid var(--line);border-radius:20px;
  padding:22px;margin-bottom:14px}
.gen{font-size:12px;color:var(--ink-3);font-weight:600;margin:0 0 22px}
"""


def main() -> int:
    src, dst = sys.argv[1], sys.argv[2]
    try:
        with open(src, encoding="utf-8") as f:
            md = f.read()
    except FileNotFoundError:
        print(f"CHANGELOG를 찾을 수 없습니다: {src}", file=sys.stderr)
        return 1

    body = render(md)
    # "## " 항목마다 카드 하나로 감싼다. 첫 덩어리(제목+안내문)는 카드 밖에 둔다.
    chunks = body.split('<h2 class="entry">')
    head = chunks[0]
    cards = "".join(f'<div class="card"><h2 class="entry">{c}</div>' for c in chunks[1:])
    stamp = datetime.now(KST).strftime("%Y년 %-m월 %-d일 %H:%M")

    out = f"""<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>소리패 업데이트 내역</title>
<style>{STYLE}</style></head><body><div class="wrap">
<a class="back" href="./">← 접속 통계로</a>
{head}
<p class="gen">{stamp} 기준 · 배포할 때마다 갱신됩니다</p>
{cards}
</div></body></html>
"""
    with open(dst, "w", encoding="utf-8") as f:
        f.write(out)
    print(f"업데이트 내역 생성: {dst} ({len(out)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
