"""파이프라인 공통 데이터 구조. 사실(과목/진단/성적)은 서버·규칙이 소유, AI는 코드+이유(2개)만 생성."""
from __future__ import annotations
from dataclasses import dataclass, field, asdict
from typing import Optional


@dataclass(frozen=True)
class Course:
    """병합 데이터(data/courses.json)의 한 과목."""
    courseCode: str
    courseName: str
    credit: int
    category: str                 # MAJOR / LIBERAL
    detailCategory: Optional[str] # 원본 세부구분 (예: '4영역 과학과 자연', 'MAJOR_ELECTIVE')
    coreAreaNo: Optional[int]     # 핵심교양 1~6, 아니면 None
    coreAreaCode: Optional[str]   # LANGUAGE_CULTURE ... , 아니면 None
    evaluation: dict = field(default_factory=dict)  # {exam, assignment, teamProject, ...} 값: 많음/보통/없음/None
    gradingStyle: Optional[str] = None
    examType: Optional[str] = None
    questionType: Optional[str] = None
    materialAllowed: Optional[str] = None
    examMethod: Optional[str] = None

    def traits_text(self) -> str:
        """프롬프트 후보표·특성 이유에 넣을 요약. 실데이터만, 미상(None)은 생략."""
        parts = []
        labels = {
            "teamProject": "팀플", "practice": "실습", "presentation": "발표",
            "exam": "시험", "assignment": "과제", "quiz": "쪽지시험", "attendance": "출석",
        }
        for key, label in labels.items():
            v = self.evaluation.get(key)
            if v and v != "없음":          # '많음'/'보통'만 표기
                parts.append(f"{label} {v}")
        if self.gradingStyle:
            parts.append(f"성적 {self.gradingStyle}")
        return ", ".join(parts) if parts else "특성정보 없음"


@dataclass(frozen=True)
class Strength:
    tag: str
    score: float


@dataclass
class Facts:
    """추천 입력 = 이미 확정된 진단 사실 (AI 는 이걸 바꾸지 않는다)."""
    studentKey: str
    lackingAreaNos: list[int] = field(default_factory=list)   # 미이수 핵심교양 영역 번호(1~6)
    shortCredits: dict = field(default_factory=dict)          # 부족 학점 {'MAJOR_ELECTIVE': 6, ...}
    gpa: Optional[float] = None
    strengths: list[Strength] = field(default_factory=list)   # 선택
    completedCodes: set[str] = field(default_factory=set)     # 이수 과목코드(후보 제외)
    history: list[dict] = field(default_factory=list)         # [{courseCode, grade}] — 성향분석 입력(성적 포함)


@dataclass(frozen=True)
class Reco:
    courseCode: str
    courseName: str
    credit: int
    reasons: list[str]                    # 추천 이유 3개(성향① + 특성② + 요건/전공③)
    category: Optional[str] = None        # 이수구분 라벨(전공필수/전공선택/교양 등) — 계약 v2.1


@dataclass
class Direction:
    """방향성 1개 = 라벨·설명·경고 + 그 방향의 추천 과목."""
    directionId: str                      # FAST_GRAD 등 (코드 고정값)
    name: str                             # 표시명(디자인 라벨)
    description: str
    caution: Optional[str]                # 편향 경고(빨간 글씨). null 이면 미표시
    recommendations: list[Reco]
    thin: bool = False                    # 매칭 과목이 적은 방향(FE 얇게 표시)


@dataclass
class Result:
    """v2 결과. directions 가 본체, 최상위 recommendations 는 default 방향 미러(하위호환)."""
    directions: list[Direction]
    defaultDirectionId: str
    source: str          # live | cache | fallback
    generatedAt: str     # ISO-8601
    persona: Optional[dict] = None   # {type,label,description,strategies[3],summary[3]} — additive(계약 v2.1)

    @property
    def recommendations(self) -> list[Reco]:
        """하위호환 미러 = defaultDirection 의 추천(없으면 첫 방향)."""
        for d in self.directions:
            if d.directionId == self.defaultDirectionId:
                return d.recommendations
        return self.directions[0].recommendations if self.directions else []

    def to_dict(self) -> dict:
        return {
            "recommendations": [asdict(r) for r in self.recommendations],
            "directions": [
                {
                    "directionId": d.directionId,
                    "name": d.name,
                    "description": d.description,
                    "caution": d.caution,
                    "thin": d.thin,
                    "recommendations": [asdict(r) for r in d.recommendations],
                }
                for d in self.directions
            ],
            "defaultDirectionId": self.defaultDirectionId,
            "source": self.source,
            "generatedAt": self.generatedAt,
            "persona": self.persona,
        }
