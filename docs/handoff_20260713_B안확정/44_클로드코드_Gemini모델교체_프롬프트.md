# 44. 클로드코드(Sonnet) — Gemini 모델 교체 프롬프트 (3.5-flash → 3.1-flash-lite)

> 배경: gemini-3.5-flash 무료 한도(하루 2회)가 너무 적어, 하루 150회인 **gemini-3.1-flash-lite**로 교체.
> 우리 구조상 모델명은 `passport-ai/core/gemini.py`가 `os.environ["GEMINI_MODEL"]`로 읽으므로 **.env 한 줄 변경 + 검증**이면 끝(코드 수정 없음).

---
(★ 여기부터 복사해서 클로드코드에 붙여넣기 ★)

너는 PassPort 프로젝트의 백엔드 작업자다. `passport-ai`(Python) AI 추천 파이프라인의 **Gemini 모델을 gemini-3.5-flash → gemini-3.1-flash-lite로 교체**하는 작업만 한다. 아래를 순서대로 하고, 각 단계 결과를 보고한 뒤 다음으로 넘어가라.

## 배경/제약 (반드시 지킬 것)
- 이유: 3.5-flash 무료 한도가 하루 2회로 너무 적음. 3.1-flash-lite는 하루 약 150회라 충분.
- 우리 AI 작업은 "규칙이 만든 방향별 후보에서 5개 선택 + 이유 2줄"의 가벼운 제약 작업이고, `response_schema`(구조화 JSON)로 강제 + `validate` + 규칙 폴백이 있어 flash-lite로 충분하다.
- **모델명은 `passport-ai/core/gemini.py`에서 `os.environ["GEMINI_MODEL"]`로 읽는다 → 코드 수정 없이 `passport-ai/.env`의 `GEMINI_MODEL` 한 줄만 바꾼다.**
- ⚠️ **`.env`의 API 키(`GEMINI_API_KEY`) 값은 절대 출력·수정·커밋하지 마라.** `GEMINI_MODEL` 줄만 건드린다. `.env`는 `.gitignore`에 있어 커밋되지 않아야 한다(확인).
- `requirements.txt`의 `google-genai==1.38.0` 고정은 그대로 둔다(모델 교체와 무관).

## STEP 1 — 현재 사용 가능한 정확한 모델 문자열 확인
```
cd passport-ai
python batch.py models
```
- 출력 목록에서 **`gemini-3.1-flash-lite`** (또는 `gemini-3.1-flash-lite-preview` 등 실제 존재하는 정확한 문자열)를 찾아 보고하라.
- ⚠️ 핸드오프 문서에 3.1-flash-lite / 2.5-flash-lite 표기 혼선이 있었으니, **추측하지 말고 이 목록에 실제로 뜬 문자열**을 쓴다. 3.1-flash-lite 계열이 목록에 없으면 멈추고 나(원석)에게 어떤 lite 모델들이 보이는지 물어라.

## STEP 2 — .env의 GEMINI_MODEL 교체
- `passport-ai/.env`에서 `GEMINI_MODEL=...` 줄만 STEP 1에서 확인한 문자열로 교체:
  ```
  GEMINI_MODEL=gemini-3.1-flash-lite
  ```
- `GEMINI_API_KEY` 줄은 절대 건드리지 않는다. 변경 후 `GEMINI_MODEL` 줄만(키 제외) 보여주며 확인.

## STEP 3 — 구동 검증 (가장 중요)
```
cd passport-ai
python batch.py demo
```
기대 결과:
- 출력에 **`source: live`** (새 모델로 실제 호출 성공).
- `directions`에 5방향(데모 학생은 MAJOR_DEEP 제외되어 4방향: FAST_GRAD / GRADE_SAFE(thin) / EXAM_SOLO(caution) / TEAM_ACTIVE(thin)).
- 각 추천에 `reasons` 3개, `category` 포함. 후보 밖 courseCode 없음.
- 그리고 이때 `passport-ai/cache/202312345.json`이 **flash-lite 출력으로 새로 갱신**된다(데모 캐시 = Spring이 서빙하는 파일).

만약 `source: fallback`이 뜨면: (a) 키/모델 문자열 문제인지, (b) 파싱 실패인지 원인부터 분석해 보고하라. 코드를 함부로 고치지 말 것 — flash-lite가 스키마를 못 맞추는 경우가 반복되면 그때 방안을 논의한다.

## STEP 4 — (선택) 일관성 조절
- 원석 요청: "같은 방향이면 결과가 대체로 비슷해야 한다(과목은 좀 달라도 OK)." 현재 이미 방향·후보는 규칙이라 일관적이고, `gemini.py`의 `temperature=0.3`으로 선택만 약간 변동한다.
- 더 고정적이길 원하면 `gemini.py` `generate()`의 `"temperature": 0.3`을 `0.1`로 낮출 수 있다(선택). **지금은 바꾸지 말고, 원석이 원할 때만.**

## STEP 5 — 보고
- 사용한 정확한 모델 문자열, `python batch.py demo`의 `source` 값, 방향/이유/category 정상 여부를 표로 보고.
- `.env`가 여전히 `.gitignore`에 있어 커밋 대상이 아님을 확인(`git check-ignore passport-ai/.env`).
- 커밋은 하지 마라(.env는 어차피 커밋 안 됨). 모델 교체는 로컬 .env만의 변화라 별도 커밋이 필요 없다. (팀원 공유가 필요하면 `.env.example`의 예시 주석만 새 모델명으로 갱신하고, 그건 커밋 가능.)

지금 STEP 1부터 시작해라.

(★ 여기까지 복사 ★)
