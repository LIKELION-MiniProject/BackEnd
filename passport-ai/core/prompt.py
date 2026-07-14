"""Gemini 프롬프트 조립 (문자열 템플릿만). AI 는 방향별로 이유 2개(특성·요건/전공)만 생성."""
from .models import Facts, Course
from .directions import DirectionBucket

AREA_NAME = {
    1: "언어와 문학(1영역)", 2: "역사와 철학(2영역)", 3: "사회와 경제(3영역)",
    4: "과학과 자연(4영역)", 5: "예술과 문화(5영역)", 6: "기초과학(6영역)",
}

SYSTEM = """너는 을지대학교 빅데이터인공지능 전공 학생의 수강 설계를 돕는 조언자다.
규칙:
1. 아래는 '방향(directionId)별 후보 과목 목록'이다. 각 방향마다 그 방향의 후보 중에서만 courseCode를 고른다. 목록에 없는 코드를 지어내지 않는다.
2. 각 방향마다 최대 5개를 고른다(후보가 5개 미만이면 있는 만큼만).
3. 각 과목마다 서로 다른 관점의 추천 이유 '2개'를 reasons 배열에 쓴다:
   - reasons[0] = 과목 특성 근거 (팀플/시험/실습/발표/성적후함 등 제공된 특성 기반)
   - reasons[1] = 졸업요건 기여 또는 전공(빅데이터·AI) 연관성
   각 이유는 한국어 1문장. 근거 없는 일반론("도움이 됩니다") 금지.
4. 학생을 깎아내리는 표현 금지. 부정적 단정 대신 사실·긍정 위주.
5. 졸업 가능 여부 등 사실 판단은 하지 않는다. 제공된 진단은 확정된 사실로 받아들인다.
6. 지정된 JSON 스키마({directions:[{directionId, items:[{courseCode, reasons}]}]})로만 응답한다. JSON 밖 텍스트 금지."""


def _diag_lines(facts: Facts) -> list[str]:
    lacking = [AREA_NAME.get(n, f"{n}영역") for n in facts.lackingAreaNos]
    short = [f"{k} {v}학점" for k, v in facts.shortCredits.items() if v > 0]
    lines = ["[학생 진단 요약]"]
    lines.append(f"- 미이수 핵심교양 영역: {', '.join(lacking) if lacking else '없음'}")
    lines.append(f"- 부족 학점: {', '.join(short) if short else '없음'}")
    if facts.gpa is not None:
        lines.append(f"- 누적 평점: {facts.gpa}")
    return lines


def _course_row(c: Course) -> str:
    area = AREA_NAME.get(c.coreAreaNo, c.detailCategory or c.category)
    return f"| {c.courseCode} | {c.courseName} | {area} | {c.credit} | {c.traits_text()} |"


def build_prompt(facts: Facts, buckets: list[DirectionBucket], profile_summary: str) -> str:
    lines = _diag_lines(facts)
    lines.append(f"- 학습 성향(참고): {profile_summary}")

    lines.append("\n[방향별 후보 과목]")
    for b in buckets:
        lines.append(f"\n## 방향 {b.spec.directionId} — {b.spec.name}")
        lines.append(f"({b.spec.description})")
        lines.append("| courseCode | courseName | 구분 | 학점 | 특성 |")
        for c in b.courses:
            lines.append(_course_row(c))

    ids = ", ".join(b.spec.directionId for b in buckets)
    lines.append(
        f"\n위 각 방향({ids})마다 그 방향의 후보 중에서 최대 5개를 골라, 과목별 reasons 2개(특성/요건·전공)를 붙여 "
        "directions 배열로 응답하라. 각 방향에서는 반드시 그 방향 후보 목록의 courseCode만 사용한다. "
        "미이수 영역을 채우는 과목을 우선 고려하라."
    )
    return "\n".join(lines)
