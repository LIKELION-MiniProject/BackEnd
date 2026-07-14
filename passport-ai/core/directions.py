"""
방향성(5방향) 분류 — 100% 규칙. (B안: AI는 이름·설명·caution을 만들지 않는다)

- 후보(build_candidates 결과 ~16개)를 5방향 전략으로 필터·정렬한다.
- 방향 이름/설명/caution 은 아래 상수로 고정(규칙 템플릿).
- 가드: 방향별 매칭이 MIN_PER_DIRECTION 미만이면 thin=True(FE가 얇게 표시).
        매칭 0개인 방향은 아예 제외(빈 탭 방지).
- caution(편향 경고)은 EXAM_SOLO·TEAM_ACTIVE 두 방향만 값이 있다.
"""
from dataclasses import dataclass, field
from typing import Optional

from .models import Facts, Course

MIN_PER_DIRECTION = 5   # 이 미만이면 thin
MAX_PER_DIRECTION = 5   # 방향당 노출 과목 수


@dataclass(frozen=True)
class DirectionSpec:
    directionId: str
    name: str
    description: str
    caution: Optional[str]


# ── 방향 상수 (directionId 는 코드 고정값, 절대 변경 금지) ──
FAST_GRAD = DirectionSpec(
    "FAST_GRAD", "졸업요건 집중형",
    "부족한 졸업 요건부터 확실하게 채우는 전략이에요.", None)
MAJOR_DEEP = DirectionSpec(
    "MAJOR_DEEP", "전공 심화형",
    "빅데이터·AI 전공 과목으로 실력을 깊게 쌓는 방향이에요.", None)
GRADE_SAFE = DirectionSpec(
    "GRADE_SAFE", "학점 안정형",
    "부담이 적고 학점을 안정적으로 챙기기 좋은 방향이에요.", None)
EXAM_SOLO = DirectionSpec(
    "EXAM_SOLO", "시험·개별평가형",
    "혼자 시험·과제로 승부하는 개별평가 중심 방향이에요.",
    "시험 비중이 높아 시험 부담이 클 수 있어요.")
TEAM_ACTIVE = DirectionSpec(
    "TEAM_ACTIVE", "협업·활동형",
    "팀 활동으로 협업 역량을 넓히는 방향이에요.",
    "팀플·발표 비중이 높아요. 협업 경험을 넓히려는 분께 권하는 선택적 도전이에요.")

SPECS = [FAST_GRAD, MAJOR_DEEP, GRADE_SAFE, EXAM_SOLO, TEAM_ACTIVE]
DEFAULT_DIRECTION_ID = "FAST_GRAD"


@dataclass
class DirectionBucket:
    spec: DirectionSpec
    courses: list[Course] = field(default_factory=list)
    thin: bool = False


# ── 이수구분(category) 라벨: FE 카드 "전공필수 · 3학점" 표기용 ──
def category_label(c: Course) -> str:
    dc = (c.detailCategory or "").upper()
    if dc == "MAJOR_REQUIRED":
        return "전공필수"
    if dc == "MAJOR_ELECTIVE":
        return "전공선택"
    if dc == "MAJOR_BASIC":
        return "전공기초"
    if (c.category or "").upper() == "MAJOR":
        return "전공"
    return "교양"


def _ev(c: Course, key: str):
    return (c.evaluation or {}).get(key)


def _is_grade_safe(c: Course) -> bool:
    # 성적 너그러움 + 시험·팀플 부담이 크지 않음
    return c.gradingStyle == "너그러움" and _ev(c, "exam") != "많음" and _ev(c, "teamProject") != "많음"


def _is_exam_solo(c: Course) -> bool:
    # 시험 비중 있음 + 팀플 없음(개별평가)
    return _ev(c, "exam") in ("많음", "보통") and _ev(c, "teamProject") in ("없음", None)


def _is_team_active(c: Course) -> bool:
    # 팀플 또는 발표 비중 있음
    return _ev(c, "teamProject") in ("보통", "많음") or _ev(c, "presentation") in ("보통", "많음")


def _lacking_first_key(c: Course, facts: Facts):
    # 미이수 핵심교양 영역을 채우는 과목 우선, 그다음 학점 큰 순(졸업 효율)
    return (0 if c.coreAreaNo in facts.lackingAreaNos else 1, -(c.credit or 0))


def _filter(spec: DirectionSpec, candidates: list[Course], facts: Facts) -> list[Course]:
    if spec is FAST_GRAD:
        return sorted(candidates, key=lambda c: _lacking_first_key(c, facts))
    if spec is MAJOR_DEEP:
        majors = [c for c in candidates if (c.category or "").upper() == "MAJOR"]
        return sorted(majors, key=lambda c: -(c.credit or 0))
    if spec is GRADE_SAFE:
        safe = [c for c in candidates if _is_grade_safe(c)]
        return sorted(safe, key=lambda c: _lacking_first_key(c, facts))
    if spec is EXAM_SOLO:
        solo = [c for c in candidates if _is_exam_solo(c)]
        # 시험 '많음' 을 앞으로
        return sorted(solo, key=lambda c: (0 if _ev(c, "exam") == "많음" else 1, *_lacking_first_key(c, facts)))
    if spec is TEAM_ACTIVE:
        team = [c for c in candidates if _is_team_active(c)]
        return sorted(team, key=lambda c: _lacking_first_key(c, facts))
    return list(candidates)


def classify(candidates: list[Course], facts: Facts) -> list[DirectionBucket]:
    """후보를 5방향으로 분류. 매칭 0개 방향은 제외, MIN 미만은 thin."""
    buckets: list[DirectionBucket] = []
    for spec in SPECS:
        matched = _filter(spec, candidates, facts)
        if not matched:                       # 빈 방향은 노출하지 않음
            continue
        thin = len(matched) < MIN_PER_DIRECTION
        buckets.append(DirectionBucket(spec, matched[:MAX_PER_DIRECTION], thin))
    return buckets


def pick_default(buckets: list[DirectionBucket]) -> str:
    ids = [b.spec.directionId for b in buckets]
    if DEFAULT_DIRECTION_ID in ids:
        return DEFAULT_DIRECTION_ID
    return ids[0] if ids else DEFAULT_DIRECTION_ID


def effective_caution(bucket: DirectionBucket):
    """편향 경고(caution)는 EXAM_SOLO·TEAM_ACTIVE 전용.
    단 thin 방향에는 caution 을 넣지 않는다(thin 안내는 FE 의 thin 박스가 담당 — 이중 메시지 방지)."""
    if bucket.thin:
        return None
    return bucket.spec.caution
