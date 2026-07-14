"""
규칙 기반 폴백. AI 전멸(429/503/파싱실패)해도 3개 이유를 갖춘 방향별 추천을 보장.
reasons = [성향①(서버) + 특성② + 요건/전공③]  전부 규칙 생성.
"""
from .models import Facts, Course, Reco, Direction
from .profile import LearningProfile
from .directions import DirectionBucket, category_label, effective_caution

AREA_NAME = {
    1: "언어와 문학(1영역)", 2: "역사와 철학(2영역)", 3: "사회와 경제(3영역)",
    4: "과학과 자연(4영역)", 5: "예술과 문화(5영역)", 6: "기초과학(6영역)",
}


def _trait_reason(c: Course) -> str:
    t = c.traits_text()
    if t == "특성정보 없음":
        base = "평가 방식 정보가 적어 부담이 크지 않은 편이에요."
    else:
        base = f"이 과목은 {t} 형태로 진행돼요."
    if c.gradingStyle == "너그러움":
        base += " 학점도 후하게 주시는 편이에요."
    return base


def _req_reason(c: Course, facts: Facts) -> str:
    if c.coreAreaNo in facts.lackingAreaNos:
        return f"미이수 핵심교양 '{AREA_NAME.get(c.coreAreaNo)}'을(를) 채워 졸업요건에 직접 기여해요."
    if (c.detailCategory or "").upper() == "MAJOR_ELECTIVE":
        return "빅데이터·AI 전공선택 학점을 채울 수 있는 전공 과목이에요."
    return "부족한 요건을 보완하는 데 도움이 되는 과목이에요."


def _reasons(c: Course, facts: Facts, profile: LearningProfile) -> list[str]:
    return [profile.fit_reason(c), _trait_reason(c), _req_reason(c, facts)]


def ensure_three(reasons: list[str], c: Course, facts: Facts) -> list[str]:
    """reasons 를 정확히 3개로. 부족하면 규칙 이유로 채운다(AI가 1개만 준 경우 대비)."""
    out = [r for r in reasons if r and r.strip()]
    pads = [_trait_reason(c), _req_reason(c, facts)]
    i = 0
    while len(out) < 3 and i < len(pads):
        if pads[i] not in out:
            out.append(pads[i])
        i += 1
    return out[:3]


def rule_reco(c: Course, facts: Facts, profile: LearningProfile) -> Reco:
    return Reco(courseCode=c.courseCode, courseName=c.courseName, credit=c.credit,
                reasons=_reasons(c, facts, profile), category=category_label(c))


def rule_directions(facts: Facts, buckets: list[DirectionBucket],
                    profile: LearningProfile) -> list[Direction]:
    """AI 전멸 시: 방향별 규칙 top5(이유 3개, category 포함)로 directions 구성."""
    out: list[Direction] = []
    for b in buckets:
        recos = [rule_reco(c, facts, profile) for c in b.courses[:5]]
        out.append(Direction(directionId=b.spec.directionId, name=b.spec.name,
                             description=b.spec.description, caution=effective_caution(b),
                             recommendations=recos, thin=b.thin))
    return out
