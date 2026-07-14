"""
AI 응답 검증 + 환각 차단 + 방향별 이유 조립.
- courseCode 가 해당 방향 후보 밖이면 폐기, 중복 제거
- 이름·학점·category 는 서버가 후보에서 역참조
- reasons = [성향①(서버 규칙)] + AI 이유 2개 → 총 3개 (부족하면 규칙으로 채움)
- 방향별 유효 5개 미만이면 그 방향 규칙 top5 로 보충
"""
from .models import Reco, Direction
from .gemini import DirectionsResponse
from .profile import LearningProfile
from .models import Facts
from .directions import DirectionBucket, category_label, effective_caution
from .fallback import rule_reco, ensure_three


def validate_directions(raw: DirectionsResponse, buckets: list[DirectionBucket],
                        profile: LearningProfile, facts: Facts) -> tuple[list[Direction], int]:
    """AI 응답 → 방향별 Direction 리스트. 반환: (directions, AI유효건수)."""
    ai_by_id = {blk.directionId: blk for blk in raw.directions}
    directions: list[Direction] = []
    ai_valid_total = 0

    for b in buckets:
        cand_index = {c.courseCode: c for c in b.courses}
        recos: list[Reco] = []
        seen: set[str] = set()

        blk = ai_by_id.get(b.spec.directionId)
        if blk:
            for item in blk.items:
                code = item.courseCode
                if code not in cand_index or code in seen:   # 환각 차단 + 중복 제거
                    continue
                seen.add(code)
                c = cand_index[code]
                ai_reasons = [r.strip() for r in item.reasons if r and r.strip()][:2]
                reasons = ensure_three([profile.fit_reason(c)] + ai_reasons, c, facts)
                recos.append(Reco(courseCode=c.courseCode, courseName=c.courseName,
                                  credit=c.credit, reasons=reasons, category=category_label(c)))
                ai_valid_total += 1
                if len(recos) >= 5:
                    break

        # 보충은 '이 방향 매칭 후보(b.courses)' 안에서만. 방향 밖 과목으로 5개를 채우지 않는다
        # (thin 방향은 매칭 개수 그대로 유지 — UI thin 박스 "N개만" 안내와 일치).
        if len(recos) < 5:
            for c in b.courses:
                if len(recos) >= 5:
                    break
                if c.courseCode in seen:
                    continue
                seen.add(c.courseCode)
                recos.append(rule_reco(c, facts, profile))

        directions.append(Direction(directionId=b.spec.directionId, name=b.spec.name,
                                    description=b.spec.description, caution=effective_caution(b),
                                    recommendations=recos, thin=b.thin))

    return directions, ai_valid_total
