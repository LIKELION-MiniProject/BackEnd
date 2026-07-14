"""과목 데이터 로딩 + 조회 헬퍼. data/courses.json(병합본)을 단일 소스로 읽는다."""
import json
from pathlib import Path
from functools import lru_cache

from .models import Course

DATA = Path(__file__).parent.parent / "data"


@lru_cache(maxsize=1)
def load_courses() -> list[Course]:
    raw = json.load(open(DATA / "courses.json", encoding="utf-8"))
    return [Course(**c) for c in raw]


@lru_cache(maxsize=1)
def course_index() -> dict[str, Course]:
    """courseCode -> Course. 검증 단계에서 이름·학점 역참조에 사용."""
    return {c.courseCode: c for c in load_courses()}
