"""
추천 오케스트레이션 (batch.py / Spring 공통). B안 = AI 1회로 5방향 전부 생성.
후보축소 → 성향분석 → 방향분류(규칙) → AI(방향별 코드+이유2) → 검증(성향①+AI②③) →
AI 유효 3↑ live / 아니면 규칙폴백. 예외(429/503/파싱) → 캐시 → 규칙폴백. 항상 캐시 저장.
"""
from datetime import datetime, timezone

from .models import Facts, Result
from .candidates import build_candidates
from .directions import classify, pick_default
from .prompt import SYSTEM, build_prompt
from .gemini import generate
from .validate import validate_directions
from .fallback import rule_directions
from .profile import analyze
from .loader import course_index
from .cache import read_cache, write_cache

MIN_AI_VALID = 3   # AI 유효 추천이 이 미만이면 규칙 폴백으로 간주


def _now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def recommend(facts: Facts) -> Result:
    candidates = build_candidates(facts)
    profile = analyze(facts.history, course_index())          # 과거성적×특성 → 성향
    buckets = classify(candidates, facts)                     # 규칙: 5방향 분류(빈 방향 제외)
    default_id = pick_default(buckets)

    try:
        raw = generate(SYSTEM, build_prompt(facts, buckets, profile.summary()))   # AI 1회
        directions, ai_valid = validate_directions(raw, buckets, profile, facts)
        source = "live" if ai_valid >= MIN_AI_VALID else "fallback"
        result = Result(directions=directions, defaultDirectionId=default_id,
                        source=source, generatedAt=_now())
    except Exception:
        cached = read_cache(facts.studentKey)
        result = cached or Result(directions=rule_directions(facts, buckets, profile),
                                  defaultDirectionId=default_id,
                                  source="fallback", generatedAt=_now())

    write_cache(facts.studentKey, result)
    return result
