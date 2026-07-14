"""
진단 사실(Facts) 조립. 두 경로:
  - from_payload : Spring 내부 계약 {studentKey, diagnosis{lackingAreas, shortCredits, gpa}, strengths[], history[]}
  - from_demo    : data/ 데모 파일(student + result + history)로 Facts 생성 (batch 데모용)
사실은 이미 확정된 값을 받는다. 여기서 졸업 판정을 새로 하지 않는다.
"""
import json
from pathlib import Path

from .models import Facts, Strength
from .prompt import AREA_NAME

DATA = Path(__file__).parent.parent / "data"

CODE_TO_NO = {
    "LANGUAGE_CULTURE": 1, "HISTORY_PHILOSOPHY": 2, "SOCIETY_ECONOMY": 3,
    "SCIENCE_NATURE": 4, "ARTS_CULTURE": 5, "BASIC_SCIENCE": 6,
}


def _area_to_no(value):
    if isinstance(value, int):
        return value if 1 <= value <= 6 else None
    s = str(value).strip()
    if s.isdigit():
        return int(s)
    if s in CODE_TO_NO:
        return CODE_TO_NO[s]
    for no, name in AREA_NAME.items():        # 한글명 매칭 (번호로 매칭하므로 문화/문학 차이 무관)
        if s in name or name.split("(")[0] in s:
            return no
    return None


def from_payload(payload: dict) -> Facts:
    di = payload.get("diagnosis", {}) or {}
    lacking = [n for n in (_area_to_no(a) for a in (di.get("lackingAreas") or [])) if n]
    strengths = [Strength(s["tag"], float(s["score"])) for s in payload.get("strengths", [])]
    history = payload.get("history", []) or []
    completed = set(payload.get("completedCodes", [])) | {str(h.get("courseCode")) for h in history}
    return Facts(
        studentKey=str(payload.get("studentKey", "unknown")),
        lackingAreaNos=lacking,
        shortCredits=dict(di.get("shortCredits", {})),
        gpa=di.get("gpa"),
        strengths=strengths,
        completedCodes=completed,
        history=history,
    )


def from_demo() -> Facts:
    student = json.load(open(DATA / "demo_student.json", encoding="utf-8"))
    result = json.load(open(DATA / "demo_diagnosis.json", encoding="utf-8"))
    hist_doc = json.load(open(DATA / "demo_history.json", encoding="utf-8"))
    history = hist_doc.get("history", [])

    core = result.get("result", {}).get("coreLiberal", {})
    lacking = [n for n in (_area_to_no(a) for a in core.get("missingAreas", [])) if n]

    completed = {str(c.get("courseCode")) for c in student.get("completedCourses", []) if c.get("courseCode")}
    completed |= {str(h.get("courseCode")) for h in history}

    return Facts(
        studentKey=str(student.get("studentId", "demo")),
        lackingAreaNos=lacking,
        shortCredits={},
        gpa=student.get("gpa"),
        strengths=[],
        completedCodes=completed,
        history=history,
    )
