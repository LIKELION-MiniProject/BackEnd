# -*- coding: utf-8 -*-
"""
PassPort 최종 기획 문서 PDF 생성.

원본(2026-07-17 02:35, ReportLab)의 생성 스크립트가 남아있지 않아 재작성했다.
내용은 원본을 그대로 따르되, 코드 대조로 확인된 사실 오류 3건을 수정했다:
  1. 발표일   7/17 → 7/20 (7/17은 코드 제출만)
  2. AI 파싱  구현되지 않은 기능이므로 전 항목에서 삭제 (§3·§4·§5)
  3. 5방향명  실제 코드값과 일치시킴 (졸업요건 집중형 / 시험·개별평가형 / 협업·활동형)
"""
import os

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    KeepTogether, PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle,
)

FONT_DIR = r"C:\Windows\Fonts"
pdfmetrics.registerFont(TTFont("Malgun", os.path.join(FONT_DIR, "malgun.ttf")))
pdfmetrics.registerFont(TTFont("MalgunBd", os.path.join(FONT_DIR, "malgunbd.ttf")))
pdfmetrics.registerFontFamily("Malgun", normal="Malgun", bold="MalgunBd")

NAVY = colors.HexColor("#1e3a8a")
BLUE = colors.HexColor("#2563eb")
GRAY = colors.HexColor("#64748b")
LIGHT = colors.HexColor("#f1f5f9")
BORDER = colors.HexColor("#cbd5e1")

ss = getSampleStyleSheet()


def S(name, **kw):
    base = dict(fontName="Malgun", fontSize=9.5, leading=15, textColor=colors.HexColor("#0f172a"))
    base.update(kw)
    return ParagraphStyle(name, **base)


st_title = S("t", fontName="MalgunBd", fontSize=30, leading=38, textColor=NAVY, alignment=TA_CENTER)
st_sub = S("s", fontSize=13, leading=20, textColor=BLUE, alignment=TA_CENTER)
st_desc = S("d", fontSize=10, leading=17, textColor=GRAY, alignment=TA_CENTER)
st_h1 = S("h1", fontName="MalgunBd", fontSize=15, leading=22, textColor=NAVY, spaceBefore=14, spaceAfter=7)
st_h2 = S("h2", fontName="MalgunBd", fontSize=11, leading=17, textColor=BLUE, spaceBefore=10, spaceAfter=5)
st_body = S("b", spaceAfter=5)
st_cell = S("c", fontSize=9, leading=13.5)
st_cellb = S("cb", fontName="MalgunBd", fontSize=9, leading=13.5)
st_th = S("th", fontName="MalgunBd", fontSize=9, leading=13.5, textColor=colors.white)
st_note = S("n", fontSize=8.5, leading=13.5, textColor=GRAY)
st_bullet = S("bu", fontSize=9.5, leading=15, leftIndent=10, bulletIndent=2, spaceAfter=3)


def P(t, s=st_cell):
    return Paragraph(t, s)


def table(header, rows, widths):
    data = [[P(h, st_th) for h in header]] + [[P(c) for c in r] for r in rows]
    t = Table(data, colWidths=widths, repeatRows=1)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), NAVY),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, LIGHT]),
        ("GRID", (0, 0), (-1, -1), 0.4, BORDER),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
    ]))
    return t


def footer(canvas, doc):
    canvas.saveState()
    if doc.page > 1:
        canvas.setFont("Malgun", 8)
        canvas.setFillColor(GRAY)
        canvas.drawString(20 * mm, 12 * mm, "PassPort · 최종 기획 문서")
        canvas.drawRightString(A4[0] - 20 * mm, 12 * mm, str(doc.page))
    canvas.restoreState()


W = A4[0] - 40 * mm
E = []

# ── 표지 ─────────────────────────────────────────────
E += [
    Spacer(1, 58 * mm),
    Paragraph("PassPort", st_title),
    Spacer(1, 4 * mm),
    Paragraph("졸업으로 가는 여권", st_sub),
    Spacer(1, 12 * mm),
    Paragraph("수강 이력을 입력하면 졸업 요건 달성도를 진단하고,<br/>"
              "다음 학기 과목을 AI가 추천해 주는 개인화 학사 대시보드", st_desc),
    Spacer(1, 38 * mm),
    Paragraph("멋쟁이사자처럼 14기 · 미니프로젝트 3팀", st_desc),
    Spacer(1, 2 * mm),
    Paragraph("최종 기획 문서 · 2026-07-17", st_desc),
    PageBreak(),
]

# ── 1. 서비스 개요 ───────────────────────────────────
E += [
    Paragraph("1. 서비스 개요", st_h1),
    Paragraph(
        "PassPort는 학생이 지금까지 들은 수강 이력을 입력하면, 졸업까지 무엇이 얼마나 부족한지를 "
        "자동으로 진단하고, 다음 학기에 들으면 좋은 과목을 이유와 함께 AI가 추천해 주는 웹 서비스입니다.",
        st_body),
    Spacer(1, 3 * mm),
    table(["항목", "내용"], [
        ["서비스명", "PassPort (졸업으로 가는 여권)"],
        ["대상", "을지대학교 빅데이터인공지능전공 학생 (요건 정확 수치화를 위해 한 학과로 범위 고정)"],
        ["형태", "웹 서비스 (React + Spring Boot)"],
    ], [30 * mm, W - 30 * mm]),
]

# ── 2. 문제 ──────────────────────────────────────────
E += [
    Paragraph("2. 어떤 문제를 푸나", st_h1),
    table(["학생이 겪는 불편", "PassPort의 해결"], [
        ["졸업까지 뭐가 얼마나 부족한지 매번 손으로 계산", "수강 이력만 넣으면 달성도·부족 학점 자동 계산"],
        ["다음 학기에 뭘 들어야 유리한지 감으로 결정", "부족 요건 + 학습 성향 기반으로 AI가 과목을 이유와 함께 추천"],
        ["학사 정보가 여기저기 흩어져 있음", "한 대시보드에 달성도·성적 추이·인증 상태를 모아 표시"],
    ], [W / 2, W / 2]),
]

# ── 3. 핵심 원칙 ─────────────────────────────────────
E += [
    Paragraph("3. 핵심 원칙 — 판정은 규칙, AI는 선택·설명", st_h1),
    Paragraph(
        "졸업 가능 여부·학점·GPA·요건 충족 판정은 전부 규칙 기반 코드로 결정론적으로 계산합니다. "
        "AI에게 사실 판정을 맡기지 않습니다. AI(Gemini)가 관여하는 곳은 딱 한 군데입니다.", st_body),
    Spacer(1, 3 * mm),
    table(["AI가 하는 일", "AI가 하지 않는 일"], [
        ["추천 과목 선택 + 추천 이유 설명<br/>(학습 성향 분석 포함)",
         "학점 계산 · GPA · 졸업 가능 판정 · 요건 충족 여부<br/>(모두 규칙 기반 코드)"],
    ], [W / 2, W / 2]),
    Spacer(1, 3 * mm),
    Paragraph(
        "즉 “학점이 몇 점인지”는 AI가 지어내지 않고, “무슨 과목이 너에게 좋은지”만 AI가 거들도록 설계해 "
        "믿을 수 있으면서도 개인화된 서비스를 지향합니다.", st_note),
]

# ── 4. 주요 기능 ─────────────────────────────────────
E += [
    Paragraph("4. 주요 기능", st_h1),
    table(["구분", "기능"], [
        ["인증", "회원가입 / 로그인 (비밀번호 암호화 저장 + JWT)"],
        ["프로필", "학적 등록 (학과·학번·입학연도, 계정당 1개)"],
        ["수강 이력", "입력 / 조회 / 수정 / 삭제"],
        ["졸업 요건", "학점 기준·핵심교양·인증 분야를 사용자가 직접 등록"],
        ["졸업 인증", "어학·봉사·논문 충족 상태 관리"],
        ["졸업 진단", "수강 이력과 요건 비교 → 달성도·부족 학점 규칙 계산"],
        ["성적 트렌드", "학기별 GPA 추이 + 다음 학기 예측"],
        ["AI 추천", "부족 요건 + 학습 성향 기반 5가지 방향 과목 추천 (이유 포함)"],
        ["대시보드", "달성도 요약·부족 항목·학습 성향을 한눈에"],
    ], [26 * mm, W - 26 * mm]),
]

E += [
    Paragraph("AI 추천의 “5가지 방향” (차별 포인트)", st_h2),
    Paragraph("같은 부족 요건이라도 학생마다 원하는 게 달라, 추천을 5가지 성격으로 나눠 제시합니다.", st_body),
    Spacer(1, 2 * mm),
    table(["방향", "성격"], [
        ["졸업요건 집중형", "부족한 졸업 요건부터 빠르게 채우기"],
        ["전공 심화형", "전공 역량을 깊게"],
        ["학점 안정형", "성적 부담이 적은 과목 위주"],
        ["시험·개별평가형", "팀플보다 개인 시험 위주"],
        ["협업·활동형", "팀 프로젝트·발표 활동 위주"],
    ], [34 * mm, W - 34 * mm]),
    Spacer(1, 3 * mm),
    Paragraph("추천 이유는 학생의 학습 성향(persona) — 예: “균형 성장형” — 을 반영해 개인화됩니다. "
              "해당하는 과목이 적은 방향은 화면에 그 사실을 함께 표시합니다.", st_note),
]

# ── 5. 유저 플로우 ───────────────────────────────────
E += [
    Paragraph("5. 유저 플로우", st_h1),
    table(["단계", "설명"], [
        ["1. 회원가입 → 로그인", "이메일로 가입하고 로그인하면 인증 토큰(JWT)을 받습니다."],
        ["2. 온보딩 분기", "프로필이 없으면 학적 등록 화면으로, 있으면 바로 대시보드로 이동합니다."],
        ["3. 수강 이력 입력", "지금까지 들은 과목을 과목명·학점·이수구분·성적으로 입력합니다."],
        ["4. 졸업 진단", "입력 즉시 “총 몇/130학점, 달성도 몇 %, 어느 구분이 몇 학점 부족”을 규칙으로 계산."],
        ["5. AI 추천", "“AI 분석” 버튼 → 부족 요건 + 성향에 맞춰 5방향으로 과목을 이유와 함께 추천."],
        ["6. 대시보드", "달성도·부족 항목·성적 추이·성향을 모아 표시. 과목 추가 시 진단이 즉시 갱신."],
    ], [34 * mm, W - 34 * mm]),
    Spacer(1, 3 * mm),
    Paragraph("시연 시나리오 (발표에서 보여줄 흐름)", st_h2),
    Paragraph("① 로그인 → 학적 정보　② 졸업 진단/대시보드(달성도 + 학습 성향)　③ AI 추천 5방향　"
              "④ 과목 라이브 추가 → 진단 즉시 반영　⑤ “AI 분석” → 최신 데이터로 재추천 (하이라이트)", st_note),
]

# ── 6. 기술 스택 ─────────────────────────────────────
E += [
    Paragraph("6. 기술 스택 · 시스템 구조", st_h1),
    table(["영역", "선택"], [
        ["백엔드", "Java 21 + Spring Boot 3.3"],
        ["인증", "Spring Security + JWT + BCrypt"],
        ["데이터베이스", "H2 (파일 모드, 영속)"],
        ["AI", "Google Gemini API (Python 컴포넌트 passport-ai 경유)"],
        ["프론트엔드", "React + Vite"],
        ["배포", "AWS EC2 + nginx"],
        ["협업", "GitHub · Notion · Slack"],
    ], [26 * mm, W - 26 * mm]),
]

E += [
    Paragraph("시스템 구조", st_h2),
    Paragraph("• 사용자 브라우저 → nginx(80): /api 요청은 백엔드로, 나머지는 화면(React dist) 서빙.", st_bullet),
    Paragraph("• FE와 API가 같은 출처(same-origin)라 CORS·혼합 콘텐츠 이슈가 원천적으로 없음.", st_bullet),
    Paragraph("• Spring Boot(8080): 인증·진단·GPA·대시보드 등 사실 판정을 전부 규칙으로 계산.", st_bullet),
    Paragraph("• H2 파일 DB: 사용자·프로필·수강·인증 데이터를 파일로 영속 저장 (재시작에도 유지).", st_bullet),
    Paragraph("• passport-ai(Python): 추천/성향 분석 전용. 데이터가 바뀔 때만 호출, 결과는 캐시에 저장해 재사용.", st_bullet),
    Paragraph("코드 구조 (도메인형 수직 분리)", st_h2),
    Paragraph(
        "9개 도메인(auth · profile · course · certification · diagnosis · gpa · recommendation · "
        "requirement · dashboard, +persona)이 각각 controller/service/domain/repository/dto를 자기 안에 "
        "갖는 구조. 총 21개 REST 엔드포인트 중 회원가입·로그인 2개만 비회원 허용, 나머지는 인증 + 본인 리소스 검증.",
        st_body),
]

# ── 7. 팀 ────────────────────────────────────────────
E += [
    Paragraph("7. 팀 구성", st_h1),
    table(["이름", "역할"], [
        ["송원석", "PM + 백엔드"],
        ["이은종", "백엔드"],
        ["황인서", "프론트엔드"],
        ["임신영", "디자인"],
    ], [34 * mm, W - 34 * mm]),
]

# ── 8. 진행 상황 ─────────────────────────────────────
E += [
    Paragraph("8. 진행 상황 · 일정", st_h1),
    Paragraph("완료", st_h2),
    Paragraph("• 백엔드 전체 기능 (인증·프로필·수강·진단·GPA·추천·대시보드·요건)", st_bullet),
    Paragraph("• AWS EC2 배포 + 상시 가동(자동 복구) · nginx 외부 공개 + /api 프록시", st_bullet),
    Paragraph("• AI 추천 캐시 사전 생성 · 전체 시스템(코드·보안·연동·AI) 검토", st_bullet),
    Paragraph("• 프론트 인증·조회 연동 + 프로필 온보딩 구현", st_bullet),
    Paragraph("마무리 단계", st_h2),
    Paragraph("• 프론트 수강 이력 쓰기 연동 · 최종 빌드(dist) 배포 · 전체 흐름 리허설", st_bullet),
    Spacer(1, 3 * mm),
    table(["시점", "일정"], [
        ["~ 7/16", "백엔드·배포·검토 완료, 프론트 연동·온보딩 구현"],
        ["7/17", "코드 제출"],
        ["7/19", "발표 자료(PPT) 제출"],
        ["7/20", "미니프로젝트 발표"],
    ], [26 * mm, W - 26 * mm]),
]

# ── 9. 정직한 고지 ───────────────────────────────────
E += [
    Paragraph("9. 데이터에 대한 정직한 고지", st_h1),
    Paragraph("시연용 데이터 일부는 실측이 아니라 임의값·추정치이며, 발표에서 “전부 실제 데이터”라고 말하지 않습니다.", st_body),
    Paragraph("• 데모 학생의 일부 학기 성적은 성적조회 기간 밖이라 임의값입니다.", st_bullet),
    Paragraph("• 추천 이유에 쓰이는 강의평가 특성 일부는 과목 성격 기반 추정치입니다(실측과 구분 표기).", st_bullet),
    Paragraph("• 졸업 요건 수치는 하드코딩 값이며 일부 항목은 학과 원본 대조가 진행 중입니다.", st_bullet),
    Spacer(1, 3 * mm),
    Paragraph("이렇게 구분해 두는 것 자체가 “판정은 규칙, AI는 보조” 원칙의 연장선입니다 — "
              "근거 없는 값으로 학생을 오도하지 않기 위함입니다.", st_note),
]

out = r"C:\Users\송원석\Documents\LikeLion_MiniProject_Team_3\docs\PassPort_최종기획문서.pdf"
doc = SimpleDocTemplate(
    out, pagesize=A4,
    leftMargin=20 * mm, rightMargin=20 * mm, topMargin=18 * mm, bottomMargin=18 * mm,
    title="PassPort 최종 기획 문서", author="멋쟁이사자처럼 14기 미니프로젝트 3팀",
    subject="PassPort 최종 기획 문서",
)
doc.build(E, onFirstPage=footer, onLaterPages=footer)
print("OK ->", out)
