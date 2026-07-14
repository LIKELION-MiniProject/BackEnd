"""
진입점 ① — 캐시 생성 / 점검용 CLI. (발표는 실시간 호출 없이 이 캐시로 재현)

사용법:
  python batch.py demo          # 데모 학생으로 추천 1건 생성 → cache/ 저장 + 출력
  python batch.py models        # 이 API 키가 쓸 수 있는 Gemini 모델 목록 출력(GEMINI_MODEL 확정용)
  python batch.py candidates    # 데모 학생의 후보 축소 결과만 확인(AI 호출 없음)
"""
import sys
import json

try:
    from dotenv import load_dotenv
    load_dotenv()  # passport-ai/.env 의 GEMINI_API_KEY, GEMINI_MODEL 로드
except ImportError:
    pass

from core.diagnosis import from_demo
from core.candidates import build_candidates
from core.recommend import recommend


def cmd_demo():
    facts = from_demo()
    print(f"[진단] studentKey={facts.studentKey} 미이수영역={facts.lackingAreaNos} "
          f"부족학점={facts.shortCredits} gpa={facts.gpa} 이수={len(facts.completedCodes)}과목")
    result = recommend(facts)
    print(f"[결과] source={result.source} generatedAt={result.generatedAt}")
    print(json.dumps(result.to_dict(), ensure_ascii=False, indent=2))


def cmd_candidates():
    facts = from_demo()
    cands = build_candidates(facts)
    print(f"후보 {len(cands)}개 (미이수영역 {facts.lackingAreaNos} 기반):")
    for c in cands:
        print(f"  {c.courseCode} {c.courseName} [{c.detailCategory}] {c.credit}학점 | {c.traits_text()}")


def cmd_models():
    from core.gemini import list_models
    print("사용 가능한 모델:")
    for name in list_models():
        print(" -", name)


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "demo"
    {"demo": cmd_demo, "candidates": cmd_candidates, "models": cmd_models}.get(cmd, cmd_demo)()


if __name__ == "__main__":
    main()
