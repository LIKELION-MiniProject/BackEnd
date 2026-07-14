"""
후보 축소 (규칙 기반). AI 에 넣기 전에 182과목 → 15~20개로 줄인다.
- 부족 요건(미이수 핵심교양 영역 / 부족 학점 구분)에 해당하는 과목만 남긴다.
- 이미 이수한 과목은 제외.
이 단계가 환각·토큰·응답시간을 모두 줄이는 핵심.
"""
from .models import Facts, Course
from .loader import load_courses

MAX_CANDIDATES = 20

# 부족학점 구분(shortCredits 키) → 과목 detailCategory 매칭
MAJOR_ELECTIVE_TAGS = {"MAJOR_ELECTIVE"}
MAJOR_REQUIRED_TAGS = {"MAJOR_REQUIRED", "MAJOR_BASIC"}


def build_candidates(facts: Facts) -> list[Course]:
    courses = load_courses()
    picked: list[Course] = []
    seen: set[str] = set()

    def add(c: Course):
        if c.courseCode in seen or c.courseCode in facts.completedCodes:
            return
        seen.add(c.courseCode)
        picked.append(c)

    # 1순위: 미이수 핵심교양 영역을 채우는 과목 (가장 확실한 졸업요건 기여)
    for area_no in facts.lackingAreaNos:
        for c in courses:
            if c.coreAreaNo == area_no:
                add(c)

    # 2순위: 부족한 전공/교양 학점 구분에 맞는 과목
    for cat, short in facts.shortCredits.items():
        if short <= 0:
            continue
        for c in courses:
            dc = (c.detailCategory or "").upper()
            if cat in MAJOR_ELECTIVE_TAGS and dc == "MAJOR_ELECTIVE":
                add(c)
            elif cat in MAJOR_REQUIRED_TAGS and dc in MAJOR_REQUIRED_TAGS:
                add(c)

    # 후보가 너무 적으면(예: 부족요건이 거의 없음) 전공선택으로 보충
    if len(picked) < 5:
        for c in courses:
            if (c.detailCategory or "").upper() == "MAJOR_ELECTIVE":
                add(c)

    return picked[:MAX_CANDIDATES]
