"""
학습 성향 분석 (규칙 기반). 과거 성적 × 과목 특성의 상관으로 '이유①(성향 맞춤)'을 만든다.

원칙:
  - 민감한 판단이므로 AI 가 아니라 규칙이 '정해진 부드러운 문구'로 생성한다.
  - 절대 학생을 깎아내리지 않는다. '협업 능력 부족' 같은 직설 표현 금지.
    → 잘한 쪽은 '강점'으로 칭찬, 약한 쪽은 '선택적 도전/대안 제시'로 순화.
  - 성적 데이터가 부족하면 신호 없음(has_signal=False) → 중립 문구.
"""
from dataclasses import dataclass
from statistics import mean
from typing import Optional

from .models import Course

GRADE_POINTS = {
    "A+": 4.5, "A": 4.0, "B+": 3.5, "B": 3.0,
    "C+": 2.5, "C": 2.0, "D+": 1.5, "D": 1.0, "F": 0.0,
    # P / NP 는 성적 산정 제외 → dict 에 없음
}

COLLAB_KEYS = ("teamProject", "presentation", "practice")   # 협업/발표형
INDIV_KEYS = ("exam", "assignment", "quiz")                 # 개별평가형
GAP = 0.4  # 두 그룹 평균 차이가 이 이상이면 '뚜렷한 성향'으로 본다


def _leaning(course: Course) -> Optional[str]:
    """과목을 협업형/개별형으로 분류. 둘 다/둘 다 아님이면 None."""
    ev = course.evaluation or {}
    collab = any(ev.get(k) in ("많음", "보통") for k in COLLAB_KEYS)
    indiv = any(ev.get(k) in ("많음", "보통") for k in INDIV_KEYS)
    if collab and not indiv:
        return "collab"
    if indiv and not collab:
        return "indiv"
    if collab and indiv:
        return "mixed"
    return None


# persona 블록(type/label/description/strategies/summary) — 전부 고정 규칙 템플릿(긍정 프레이밍).
# AI가 생성하지 않는다(원칙 1). 이름(OOO님)은 포함하지 않음 — FE가 프로필 이름과 조합해 표시.
_PERSONA_TEMPLATES: dict[str, dict] = {
    "indiv_strong": {
        "type": "INDIV_STRONG",
        "label": "안정 성장형",
        "description": "발표와 팀플이 조금 부담되는 유형이에요. 대신 과제·실습형 과목에서 강점을 보이며, "
                       "꾸준히 준비하면 안정적인 성과를 낼 수 있어요.",
        "strategies": [
            "과제·실습 중심의 학습을 꾸준히 유지하기",
            "발표 연습으로 자신감 키우기 (짧은 발표부터 도전)",
            "팀 프로젝트는 역할을 미리 정하고 소통 계획 세우기",
        ],
        "summary": [
            "개별 평가(시험·과제) 과목에서 안정적인 성과를 내고 있어요.",
            "팀플·발표 경험을 조금씩 늘리면 더 고른 성장을 기대할 수 있어요.",
            "지금의 학습 루틴을 꾸준히 유지하는 것이 좋은 전략이에요.",
        ],
    },
    "collab_strong": {
        "type": "COLLAB_STRONG",
        "label": "협업 활동형",
        "description": "팀프로젝트·발표가 있는 과목에서 강점을 보이는 유형이에요. 활동형 수업에서 좋은 성과를 낼 수 있어요.",
        "strategies": [
            "팀 프로젝트·발표 기회가 있는 과목을 적극 활용하기",
            "개별 시험·과제 과목은 미리 계획을 세워 대비하기",
            "협업에서 얻은 강점을 포트폴리오로 정리해두기",
        ],
        "summary": [
            "팀플·발표가 포함된 과목에서 좋은 성과를 내고 있어요.",
            "개별 평가 과목도 꾸준히 준비하면 더 균형 잡힌 성장을 할 수 있어요.",
            "협업 경험을 살릴 수 있는 과목을 이어서 선택해보세요.",
        ],
    },
    "balanced": {
        "type": "BALANCED",
        "label": "균형 성장형",
        "description": "다양한 평가 방식의 과목을 고르게 잘 수강해온 유형이에요. 어떤 방식의 수업에도 무리 없이 적응하고 있어요.",
        "strategies": [
            "관심 있는 분야를 넓혀가며 다양한 과목에 도전하기",
            "강점이 뚜렷해지는 영역을 찾아 집중해보기",
            "지금처럼 여러 평가 방식에 고르게 대응하는 습관 유지하기",
        ],
        "summary": [
            "여러 평가 방식의 과목에서 고르게 좋은 성과를 내고 있어요.",
            "아직 뚜렷한 강점 영역을 찾는 중이에요.",
            "다양한 과목 경험이 앞으로의 선택 폭을 넓혀줄 거예요.",
        ],
    },
    "none": {
        "type": "EXPLORING",
        "label": "탐색형",
        "description": "아직 성향을 분석할 만큼의 성적 데이터가 충분하지 않아요. "
                       "다양한 과목을 경험하며 나만의 학습 스타일을 만들어가는 시기예요.",
        "strategies": [
            "다양한 평가 방식의 과목을 경험해보기",
            "과목별 특성(시험·과제·팀플 비중)을 확인하고 선택하기",
            "한 학기 이상 데이터가 쌓이면 더 구체적인 분석을 받아보기",
        ],
        "summary": [
            "아직 분석할 성적 데이터가 충분하지 않아요.",
            "다양한 과목을 수강하며 강점을 찾아가는 단계예요.",
            "다음 학기부터 더 정교한 성향 분석을 받을 수 있어요.",
        ],
    },
}


@dataclass
class LearningProfile:
    has_signal: bool
    indiv_avg: Optional[float]
    collab_avg: Optional[float]
    pattern: str            # 'indiv_strong' | 'collab_strong' | 'balanced' | 'none'

    def persona(self) -> dict:
        """홈·AI분석 홈·진단결과 3화면이 공유하는 persona 블록. 고정 템플릿 복사본(원본 dict 변경 방지)."""
        template = _PERSONA_TEMPLATES[self.pattern]
        return {
            "type": template["type"],
            "label": template["label"],
            "description": template["description"],
            "strategies": list(template["strategies"]),
            "summary": list(template["summary"]),
        }

    def summary(self) -> str:
        if self.pattern == "indiv_strong":
            return "시험·과제 중심의 개별평가 과목에서 특히 안정적인 성적을 받아왔어요."
        if self.pattern == "collab_strong":
            return "팀프로젝트·발표가 있는 과목에서 좋은 성적을 받아왔어요."
        if self.pattern == "balanced":
            return "다양한 평가 방식의 과목을 고르게 잘 수강해왔어요."
        return "아직 성향을 분석할 만큼의 성적 데이터가 충분하지 않아요."

    def fit_reason(self, course: Course) -> str:
        """후보 과목에 대한 '이유①'. 항상 긍정·건설적."""
        lean = _leaning(course)
        if self.pattern == "indiv_strong":
            if lean == "indiv":
                return "그동안 시험·과제 중심 과목에서 강점을 보였는데, 이 과목도 개별평가 위주라 네 스타일과 잘 맞아요."
            if lean in ("collab", "mixed"):
                return "팀 활동 비중이 있는 과목이에요. 협업 경험을 넓히고 싶다면 좋은 도전이고, 안정적인 학점을 원하면 시험 중심 과목이 더 편할 수 있어요."
        if self.pattern == "collab_strong":
            if lean in ("collab", "mixed"):
                return "팀프로젝트·발표가 있는 과목에서 좋은 성적을 받아왔는데, 이 과목도 그런 활동형이라 강점을 살리기 좋아요."
            if lean == "indiv":
                return "차분히 시험·과제로 평가받는 과목이에요. 그동안의 활동형 강점과는 다른 결이지만 무리 없이 소화할 수 있을 거예요."
        # balanced / none / 분류불가
        return "여러 평가 방식의 과목을 두루 경험해왔어요. 이 과목의 평가 방식도 충분히 소화할 수 있을 거예요."


def analyze(history: list[dict], course_index: dict[str, Course]) -> LearningProfile:
    """history: [{courseCode, grade}] . grade 는 'A+'..'F' 문자열."""
    indiv_pts, collab_pts = [], []
    for h in history:
        code = str(h.get("courseCode"))
        grade = h.get("grade")
        if grade not in GRADE_POINTS or code not in course_index:
            continue
        pt = GRADE_POINTS[grade]
        lean = _leaning(course_index[code])
        if lean == "indiv":
            indiv_pts.append(pt)
        elif lean == "collab":
            collab_pts.append(pt)
        elif lean == "mixed":
            indiv_pts.append(pt)
            collab_pts.append(pt)

    ia = round(mean(indiv_pts), 2) if indiv_pts else None
    ca = round(mean(collab_pts), 2) if collab_pts else None

    if ia is None or ca is None or (len(indiv_pts) + len(collab_pts) < 3):
        pattern = "balanced" if (ia or ca) else "none"
        return LearningProfile(bool(ia or ca), ia, ca, pattern)

    if ia - ca >= GAP:
        pattern = "indiv_strong"
    elif ca - ia >= GAP:
        pattern = "collab_strong"
    else:
        pattern = "balanced"
    return LearningProfile(True, ia, ca, pattern)
