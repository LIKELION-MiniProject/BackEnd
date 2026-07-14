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


@dataclass
class LearningProfile:
    has_signal: bool
    indiv_avg: Optional[float]
    collab_avg: Optional[float]
    pattern: str            # 'indiv_strong' | 'collab_strong' | 'balanced' | 'none'

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
