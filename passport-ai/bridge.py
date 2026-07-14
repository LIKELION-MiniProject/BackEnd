"""
진입점 ② — Spring 브릿지. 유저 데이터가 변경됐을 때 Spring(RecommendationService)이 호출한다.

동작: stdin으로 payload(JSON) 수신 → 과목명→과목코드 매핑 → from_payload → recommend()
      → stdout으로 결과 JSON "하나만" 출력. (Spring이 stdout 전체를 파싱하므로 로그는 전부 stderr로)

payload (Spring 내부 계약 — core/diagnosis.from_payload 형식):
  {
    "studentKey": "202312345",
    "diagnosis": { "lackingAreas": [], "shortCredits": {"MAJOR_ELECTIVE": 6}, "gpa": 3.62 },
    "history":   [ {"courseName": "데이터베이스", "grade": "A+"}, ... ]
  }

Spring DB의 수강 이력에는 과목코드가 없어(과목명뿐) 여기서 data/courses.json 이름 색인으로
courseCode를 채운다. 카탈로그에 없는 과목명은 성향분석·이수제외에서만 빠진다(에러 아님) —
학점 계산은 이미 Spring 진단이 끝낸 값(shortCredits)을 그대로 쓰므로 영향 없다.

캐시: recommend()가 결과를 cache/{studentKey}.json에 항상 저장한다. 이 시점의 결과는
      (live든 규칙 폴백이든) 방금 받은 "최신 데이터" 기반이므로 이전 캐시보다 항상 새롭다.
"""
import sys
import json


def main() -> int:
    # Windows 콘솔 인코딩(cp949)과 무관하게 파이프는 항상 UTF-8 (Spring도 UTF-8로 읽는다)
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
        sys.stdin.reconfigure(encoding="utf-8")
    except Exception:
        pass

    # .env(GEMINI_API_KEY, GEMINI_MODEL) 로드는 core 임포트보다 먼저 (batch.py와 동일한 순서)
    try:
        from dotenv import load_dotenv
        load_dotenv()
    except ImportError:
        pass

    from core.diagnosis import from_payload
    from core.recommend import recommend
    from core.loader import load_courses

    payload = json.load(sys.stdin)

    # 과목명 → 과목코드 매핑 (정확 일치, 공백 트림)
    name_index: dict[str, str] = {}
    for c in load_courses():
        name_index.setdefault(c.courseName.strip(), c.courseCode)

    mapped_history: list[dict] = []
    completed: set[str] = set(str(c) for c in payload.get("completedCodes") or [])
    unmatched = 0
    for h in payload.get("history") or []:
        code = h.get("courseCode") or name_index.get(str(h.get("courseName", "")).strip())
        if code:
            completed.add(str(code))
            mapped_history.append({"courseCode": str(code), "grade": h.get("grade")})
        else:
            unmatched += 1
    payload["history"] = mapped_history
    payload["completedCodes"] = sorted(completed)

    facts = from_payload(payload)
    print(f"[bridge] studentKey={facts.studentKey} shortCredits={facts.shortCredits} "
          f"gpa={facts.gpa} 코드매핑={len(mapped_history)}과목 미매칭={unmatched}과목", file=sys.stderr)

    result = recommend(facts)
    print(f"[bridge] source={result.source} directions={len(result.directions)} "
          f"generatedAt={result.generatedAt}", file=sys.stderr)

    json.dump(result.to_dict(), sys.stdout, ensure_ascii=False)
    sys.stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
