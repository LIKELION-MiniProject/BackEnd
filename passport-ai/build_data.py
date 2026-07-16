"""
build_data.py — 원천 데이터 2개를 교과목코드로 합쳐 파이프라인용 courses.json 생성.

입력:
  data/raw/courses_eval.csv    (과목별 평가 특성: 시험/과제/팀플/발표/출석/쪽지/실습, 성적후함, 시험유형 등)
  data/raw/courses_areas.json  (과목별 구분·세부영역: MAJOR/LIBERAL, detailCategory)

출력:
  data/courses.json            (병합본 — 후보 필터가 읽는 단일 소스)

원칙:
  - 미태깅(빈칸)은 '없음'이 아니라 null 로 둔다. ('없음'은 확인된 사실, null 은 미상)
  - 핵심교양 영역은 한글 이름이 파일마다 다르므로(문화/문학) '영역 번호'로 매칭한다.
"""
import csv
import json
import re
from pathlib import Path

BASE = Path(__file__).parent
RAW = BASE / "data" / "raw"

# 핵심교양 1~6영역 → 표준 코드 (graduation-requirements.json 의 code 와 일치)
AREA_CODE = {
    1: "LANGUAGE_CULTURE",   # 1영역 (요건 파일은 '언어와 문화', 과목 파일은 '언어와 문학' — 번호로 매칭)
    2: "HISTORY_PHILOSOPHY", # 2영역 역사와 철학
    3: "SOCIETY_ECONOMY",    # 3영역 사회와 경제
    4: "SCIENCE_NATURE",     # 4영역 과학과 자연
    5: "ARTS_CULTURE",       # 5영역 예술과 문화
    6: "BASIC_SCIENCE",      # 6영역 기초과학
}

# CSV 컬럼 인덱스 → 평가 특성 키
EVAL_COLS = {
    "exam": 6,          # ①시험
    "assignment": 7,    # ②과제
    "teamProject": 8,   # ③팀플
    "presentation": 9,  # ④발표
    "attendance": 10,   # ⑤출석
    "quiz": 11,         # ⑥쪽지시험
    "practice": 12,     # ⑦실습
}


def cell(row, i):
    v = row[i].strip() if i < len(row) and row[i] is not None else ""
    return v or None  # 빈칸 → null (미상)


def load_eval_by_key():
    """
    키는 (교과목명, 교과목코드) 쌍이다. 코드 단독으로는 서로 다른 과목이 같은 코드를 쓰는 경우
    (정밀의료/BM프로젝트=400990, K-콘텐츠새로읽기/대인관계론=401441, 웰니스문화의이해/광고와문화=400778)
    뒤 행이 앞 행을 덮어써서 엉뚱한 평가특성이 붙는다. 반대로 이름 단독으로는
    실용영어청취및말하기(400301/114511)가 겹친다. 쌍으로 하면 182과목 전부 1:1로 맞는다.
    """
    path = RAW / "courses_eval.csv"
    out = {}
    with open(path, encoding="utf-8-sig") as f:
        rows = list(csv.reader(f))
    for row in rows[1:]:
        if len(row) < 6 or not row[4].strip():
            continue
        key = (row[3].strip(), row[4].strip())
        if key in out:
            print(f"  ⚠️ 중복 키 무시: {key}")
            continue
        out[key] = {
            "evaluation": {k: cell(row, i) for k, i in EVAL_COLS.items()},
            "gradingStyle": cell(row, 13),   # ⑧성적후함정도 (보통/너그러움)
            "examType": cell(row, 16),       # 시험유형_횟수
            "questionType": cell(row, 18),   # 문항유형
            "materialAllowed": cell(row, 19),# 자료허용
            "examMethod": cell(row, 20),     # 시험방식
        }
    return out


def area_of(detail_category):
    """detailCategory('1영역 언어와 문학' 등)에서 핵심교양 영역 번호/코드 추출."""
    if not detail_category:
        return None, None
    m = re.match(r"\s*([1-6])\s*영역", detail_category)
    if m:
        no = int(m.group(1))
        return no, AREA_CODE[no]
    return None, None


def main():
    areas = json.load(open(RAW / "courses_areas.json", encoding="utf-8"))
    evals = load_eval_by_key()

    merged, matched = [], 0
    for c in areas:
        code = str(c.get("courseCode", "")).strip()
        name = str(c.get("courseName", "")).strip()
        detail = c.get("detailCategory")
        area_no, area_code = area_of(detail)
        ev = evals.get((name, code))
        if ev:
            matched += 1
        merged.append({
            "courseCode": code,
            "courseName": c.get("courseName"),
            "credit": c.get("credit"),
            "category": c.get("category"),          # MAJOR / LIBERAL
            "detailCategory": detail,               # 원본 세부구분
            "coreAreaNo": area_no,                  # 1~6 (핵심교양만), 아니면 null
            "coreAreaCode": area_code,              # LANGUAGE_CULTURE ... , 아니면 null
            "evaluation": (ev or {}).get("evaluation", {k: None for k in EVAL_COLS}),
            "gradingStyle": (ev or {}).get("gradingStyle"),
            "examType": (ev or {}).get("examType"),
            "questionType": (ev or {}).get("questionType"),
            "materialAllowed": (ev or {}).get("materialAllowed"),
            "examMethod": (ev or {}).get("examMethod"),
        })

    out = BASE / "data" / "courses.json"
    json.dump(merged, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    print(f"병합 완료: {len(merged)}과목 → {out}")
    print(f"평가 특성 매칭: {matched}/{len(merged)}")
    print(f"핵심교양(1~6영역) 과목: {sum(1 for m in merged if m['coreAreaNo'])}")


if __name__ == "__main__":
    main()
