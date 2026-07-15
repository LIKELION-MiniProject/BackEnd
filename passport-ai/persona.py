"""
진입점 ③ — 성향(persona) 전용 브릿지. 수강 이력(성적)이 바뀔 때마다 Spring(CourseService)이 호출한다.

bridge.py(AI 5방향 추천 전체)와 달리 Gemini 호출이 전혀 없는 100% 규칙 계산이라
훨씬 가볍고 빠르며, API 키 상태와 무관하게 항상 동작한다(원칙 1 — AI는 추천/설명에만, 성향 분류는 규칙).

동작: stdin으로 payload(JSON) 수신 → 과목명→과목코드 매핑 → analyze() → LearningProfile.persona()
      → stdout으로 persona dict "하나만" 출력. (Spring이 stdout 전체를 파싱하므로 로그는 전부 stderr로)

payload:
  { "studentKey": "202312345", "history": [ {"courseName": "데이터베이스", "grade": "A+"}, ... ] }

stdout:
  { "type": "...", "label": "...", "description": "...", "strategies": ["...", "...", "..."], "summary": ["...", "...", "..."] }

Spring DB의 수강 이력에는 과목코드가 없어(과목명뿐) bridge.py와 동일하게 data/courses.json
이름 색인으로 courseCode를 채운다. 카탈로그에 없는 과목명은 성향분석에서만 빠진다(에러 아님).
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

    from core.profile import analyze
    from core.loader import course_index, load_courses

    payload = json.load(sys.stdin)

    # 과목명 → 과목코드 매핑 (정확 일치, 공백 트림) — bridge.py와 동일 규칙
    name_index: dict[str, str] = {}
    for c in load_courses():
        name_index.setdefault(c.courseName.strip(), c.courseCode)

    mapped_history: list[dict] = []
    unmatched = 0
    for h in payload.get("history") or []:
        code = h.get("courseCode") or name_index.get(str(h.get("courseName", "")).strip())
        if code:
            mapped_history.append({"courseCode": str(code), "grade": h.get("grade")})
        else:
            unmatched += 1

    learning_profile = analyze(mapped_history, course_index())
    persona = learning_profile.persona()

    print(f"[persona] studentKey={payload.get('studentKey')} pattern={learning_profile.pattern} "
          f"코드매핑={len(mapped_history)}과목 미매칭={unmatched}과목", file=sys.stderr)

    json.dump(persona, sys.stdout, ensure_ascii=False)
    sys.stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
