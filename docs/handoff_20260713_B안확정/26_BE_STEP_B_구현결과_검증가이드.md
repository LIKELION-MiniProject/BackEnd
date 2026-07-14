# 26. BE STEP B — passport-ai 방향성 확장 구현 결과 & 검증 가이드

> 작성: 2026-07-14. passport-ai directions(B안) 코드 구현 완료 기록 + 호스트 검증 방법.
> ⚠️ 이 세션 샌드박스 마운트 제약으로 코드 실행 검증은 못 함 → **원석 Windows에서 실행 검증 필요**.

---

## 1) 수정/신설한 파일 (8개, `passport-ai/core/`)

| 파일 | 변경 | 내용 |
|---|---|---|
| `directions.py` | **신설** | 5방향 규칙 분류 + 가드(thin) + caution(④⑤) + `category_label`(이수구분). directionId 고정값. `classify()`·`pick_default()` |
| `models.py` | 수정 | `Reco.category` 추가, `Direction` dataclass 신설, `Result`를 v2(directions·defaultDirectionId + 최상위 recommendations 미러 property)로 재구성, `to_dict()` v2 |
| `gemini.py` | 수정 | 응답 스키마를 `{directions:[{directionId, items:[{courseCode, reasons}]}]}`로. AI 1회 유지, temperature 0.3 |
| `prompt.py` | 수정 | 방향별 후보 테이블을 담은 프롬프트(`build_prompt(facts, buckets, summary)`) |
| `validate.py` | 수정 | 방향별 환각차단 + 이유①(성향) + category 채움 + 5개 미만 규칙 보충, `(directions, AI유효건수)` 반환 |
| `fallback.py` | 수정 | `rule_reco`/`rule_directions`(방향별 규칙 top5, 이유3·category), `ensure_three` |
| `cache.py` | 수정 | v2 저장/조회 + **구버전(단일 recommendations) 캐시 하위호환** |
| `recommend.py` | 수정 | 방향 오케스트레이션(AI유효 3↑ → live / 아니면 fallback / 예외 → 캐시 → 규칙폴백) |

> 진입점 `batch.py`는 그대로 동작(`result.to_dict()`가 v2 구조 반환). `core/_synctest.py`는 마운트 점검용 임시 파일 — **커밋 전 삭제 권장**(삭제가 EPERM이라 세션에서 못 지움).

---

## 2) 파이프라인 (구현된 흐름)

```
build_candidates(facts)            # 규칙: 182 → ~16
  → analyze(history)               # 규칙: 성향(개별/협업)
  → directions.classify(...)       # 규칙: 5방향 분류(빈 방향 제외, MIN<5 → thin)
  → generate(prompt)               # AI 1회: 방향별 과목 pick + 이유②③
  → validate_directions(...)       # 환각차단 + 이유①(성향) + category + 규칙보충
  → Result(source=live/fallback)   # AI유효<3 이면 fallback
  (예외 429/503/파싱 → read_cache → 없으면 rule_directions)
  → write_cache                    # 항상 저장(v2)
```

---

## 3) 방향 분류 규칙 (directions.py)

| directionId | name | 규칙 | caution |
|---|---|---|---|
| FAST_GRAD | 졸업요건 집중형 | 미이수영역 우선 + 학점 큰 순 | null |
| MAJOR_DEEP | 전공 심화형 | `category=="MAJOR"` | null |
| GRADE_SAFE | 학점 안정형 | `gradingStyle=="너그러움"` & 시험/팀플 부담 낮음 | null |
| EXAM_SOLO | 시험·개별평가형 | `exam∈{많음,보통}` & `teamProject∈{없음,None}` | "시험 비중이 높아 시험 부담이 클 수 있어요." |
| TEAM_ACTIVE | 협업·활동형 | `teamProject∈{보통,많음}` 또는 `presentation∈{보통,많음}` | "팀플·발표 비중이 높아요. 협업 경험을 넓히려는 분께 권하는 선택적 도전이에요." |

- **가드**: 매칭 0개 방향은 제외(빈 탭 방지), MIN_PER_DIRECTION(5) 미만은 `thin=True`.
- 이름·설명·caution은 규칙 템플릿 고정(AI에게 안 시킴).
- `category_label`: MAJOR_REQUIRED→전공필수 / MAJOR_ELECTIVE→전공선택 / MAJOR_BASIC→전공기초 / MAJOR→전공 / 그 외→교양.

---

## 4) ⚠️ 검증 한계 (정직 고지)

- 이 세션 샌드박스 마운트는 **신규 파일만 동기화**하고 **기존 파일 덮어쓰기·삭제(rm)는 반영하지 않음**(EPERM).
- 따라서 수정한 core 파일 7개는 **호스트(원석 PC)에는 완전 저장**됐으나 샌드박스에서 `python`으로 실행 불가.
- pydantic도 샌드박스에 없음(pip 프록시 403) → AI 경로 실행 불가.
- **파일 간 import·시그니처·계약 일치는 전수 코드리뷰로 확인.** 런타임 검증은 아래 호스트 명령으로 원석이 수행.

---

## 5) 호스트 검증 명령 (Windows — IntelliJ 터미널/PowerShell)

### (1) 규칙(폴백) 경로 — 구버전 캐시를 먼저 비켜야 새 로직이 보임
```powershell
cd C:\Users\송원석\Documents\LikeLion_MiniProject_Team_3\passport-ai
Rename-Item cache\202312345.json 202312345.old.json
python batch.py demo
```

**기대 결과 (데모 학생 202312345)**
- `directions` = **FAST_GRAD, GRADE_SAFE(thin), EXAM_SOLO, TEAM_ACTIVE(thin)** 4개.
- **MAJOR_DEEP 없음** (전공 후보 0 → 정상, 가드 동작 증거).
- `defaultDirectionId: "FAST_GRAD"`.
- 각 항목에 `category`(교양) · `reasons` 3개.
- `EXAM_SOLO`·`TEAM_ACTIVE`에 `caution` 문자열 존재.
- `source: "fallback"` (키 없이 예외→규칙) 또는 키 있으면 `live`.

### (2) live 경로 (.env 키 있는 상태) — v2 캐시 재생성
```powershell
python batch.py demo
```
- `source: "live"`, `cache\202312345.json` 이 v2 포맷(directions 포함)으로 새로 저장.

### (3) 후보 축소만 확인 (AI 호출 없음)
```powershell
python batch.py candidates
```

### 데이터 근거 (예상 분포)
- 데모 미이수영역 = 4·6영역(교양) → 후보 16개 전부 교양.
- 그중: `gradingStyle=="너그러움"` 2개(GRADE_SAFE thin), `exam 많음/보통 & 팀플 없음` ~10개(EXAM_SOLO 정상), `팀플/발표` ~3개(TEAM_ACTIVE thin), 전공 0개(MAJOR_DEEP 제외).

---

## 6) 검증 체크리스트 (Definition of Done)

- [ ] `python batch.py demo`(캐시 비운 상태) → 4방향, MAJOR_DEEP 없음, thin 2개.
- [ ] 각 recommendation에 `category` 포함, `reasons` 정확히 3개.
- [ ] `EXAM_SOLO`/`TEAM_ACTIVE`에 `caution` 문자열, 나머지 null.
- [ ] 최상위 `recommendations` = FAST_GRAD의 5개와 동일(미러).
- [ ] live 1회 호출 → `source:live`, 후보 밖 courseCode 없음.
- [ ] 캐시 v2 저장 후 재조회 → `source:cache`, 구조 동일.
- [ ] 에러 없으면 STEP C(Spring recommend + CORS)로 진행.
- [ ] `core/_synctest.py` 삭제.

---

## 7) 에러 시
- 전체 트레이스백을 그대로 전달. 코드 수정보다 **원인 분석 먼저**(import 경로/필드명/None 가드 등).
- 특히 확인: `data/courses.json`의 실제 키명(`evaluation.exam`, `teamProject`, `presentation`, `gradingStyle`, `category`, `detailCategory`, `coreAreaNo`)이 코드 가정과 일치하는지.
