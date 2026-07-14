# 02. BE — RECO 외부 계약 v2 & core 코드 수정 설계

## A. 외부 계약 v2 (SSOT — 노션·FE 공유 기준)

### 엔드포인트
| method | 경로 | 종류 | 비고 |
|---|---|---|---|
| POST | `/api/v1/profiles/{id}/recommendations` | AI(1회 생성) | 빈 바디, Bearer 필수, 소유권 검증, 재호출=재생성·덮어쓰기 |
| GET | `/api/v1/profiles/{id}/recommendations` | 캐시 조회 | 빈 바디, Bearer 필수, 소유권 검증 |

응답 래퍼(팀 확정): `{success, data, message}` (성공 message=null, 실패 data=null).

### data 구조 (v2)
```json
{
  "recommendations": [
    { "courseCode": "401556", "courseName": "생성형AI의이해와활용", "credit": 2,
      "reasons": ["성향 맞춤(규칙)", "과목 특성(AI)", "졸업 기여(AI)"] }
  ],
  "directions": [
    {
      "directionId": "FAST_GRAD",
      "name": "졸업요건 집중형",
      "description": "부족한 졸업 요건부터 확실하게 채우는 전략이에요.",
      "caution": null,
      "recommendations": [ { "courseCode": "...", "courseName": "...", "credit": 3,
                            "reasons": ["...", "...", "..."] } ]
    },
    {
      "directionId": "TEAM_ACTIVE",
      "name": "협업·활동형",
      "description": "팀 활동으로 협업 역량을 넓히는 방향이에요.",
      "caution": "팀플·발표 비중이 높아요. 협업 경험을 넓히려는 분께 권하는 선택적 도전이에요.",
      "recommendations": [ /* 5개 */ ]
    }
  ],
  "defaultDirectionId": "FAST_GRAD",
  "source": "live",
  "generatedAt": "2026-07-13T21:00:00+09:00"
}
```

### 계약 규칙
- 최상위 `recommendations` = `defaultDirectionId` 방향의 5개 **미러** → 기존 단일 추천 계약과 **하위호환**(directions 컷돼도 FE 안전).
- `directions`는 **가드 통과 방향만** 포함. FE는 배열 길이만큼 탭 렌더(빈 탭 없음). 5방향 노출 방침이지만 매칭 과목이 극히 적은 방향은 얇게 표시하거나 안내.
- `caution`: `null`이면 FE 미표시, 문자열이면 라벨 아래 **빨간 글씨**. BE가 규칙으로 채움.
- `source`: `live` | `cache` | `fallback`. `fallback`이면 FE가 "기본 추천" 배지.
- `reasons`: 정확히 3개 = [성향①(규칙) · 특성②(AI) · 졸업기여③(AI)]. 폴백 시 3개 모두 규칙.

### directionId ↔ 표시명(name) 매핑 (name은 디자인 라벨과 일치시킬 것)
| directionId | name(디자인) | 분류 규칙 | caution |
|---|---|---|---|
| `FAST_GRAD` | 졸업요건 집중형 | 미이수 영역 커버리지 + 학점 효율 | null |
| `MAJOR_DEEP` | 전공 심화형 | 구분 MAJOR + AI·데이터 연관 | null |
| `GRADE_SAFE` | 학점 안정형 | 성적 너그러움 + 시험·팀플 부담 낮음 | null |
| `EXAM_SOLO` | 시험·개별평가형 | 시험 많음 중심 · 팀플 없음 | "시험 비중이 높아 시험 부담이 클 수 있어요." |
| `TEAM_ACTIVE` | 협업·활동형 | 팀플 보통/많음 또는 발표 | "팀플·발표 비중이 높은 선택적 도전이에요." |

> ⚠️ 디자인 시안의 탭 라벨(예: "학점 관리형")과 위 name이 다르면 **디자인 라벨을 따른다**(신영 PNG 확인). directionId는 코드 고정값이라 절대 바꾸지 않는다.

## B. passport-ai `core/` 파일별 수정 설계

현행 파이프라인: `candidates → profile → gemini(1회) → validate → fallback → cache`, 출력=단일 `recommendations[5]`. 여기에 **방향(directions) 계층**을 얹는다.

### 1. `directions.py` (신설) — 방향 분류 100% 규칙
```
DIRECTIONS = [FAST_GRAD, MAJOR_DEEP, GRADE_SAFE, EXAM_SOLO, TEAM_ACTIVE]  # id·name·description·caution 상수

def classify(candidates, facts) -> list[DirectionBucket]:
    # 각 방향별 필터+정렬 규칙으로 후보를 담는다.
    #  FAST_GRAD : coreAreaNo in facts.lackingAreaNos 우선, 학점효율 정렬
    #  MAJOR_DEEP: category=="MAJOR"
    #  GRADE_SAFE: gradingStyle=="너그러움" and 시험/팀플 부담 낮은 것
    #  EXAM_SOLO : evaluation.exam in ("많음","보통") and teamProject=="없음"
    #  TEAM_ACTIVE: teamProject in ("보통","많음") or presentation
    # 가드: len(bucket) < 5 → 규칙 top5로 보충 시도, 그래도 부족하면 얇게(플래그)
    # caution: 상수에서 방향별로 결정(④⑤만 문자열)
```
- 방향 이름·설명·caution은 **규칙 템플릿 고정**(AI에게 안 시킴).

### 2. `models.py`
- `Direction` dataclass 추가: `directionId, name, description, caution: Optional[str], recommendations: list[Reco]`.
- `Result`에 `directions: list[Direction]`, `defaultDirectionId: str` 추가.
- `to_dict()`에 최상위 `recommendations`(default 방향 미러) + `directions` + `defaultDirectionId` 직렬화.

### 3. `prompt.py` + `gemini.py` — AI 호출 총 1회 유지
- 입력: 가드 통과 방향별 후보(방향당 최대 10개)를 하나의 프롬프트로.
- 출력 스키마 변경: `{directions:[{directionId, items:[{courseCode, reason2, reason3}]}]}`.
- `response_schema` 강제, `temperature=0.3`. 후보 밖 코드 폐기.
- `gemini.py`의 `RecoResponse` 대신 방향 구조 스키마(`DirectionReco`) 정의.

### 4. `validate.py` — 방향별 검증
- 방향별로 후보 밖/중복 폐기, 유효 5개 미만이면 그 방향은 규칙 top5로 채움.
- 이유① = `profile.fit_reason(course)` (결합 A: 성향으로 방향 내 정렬 + 이유① 개인화). 기존 함수 재사용.

### 5. `fallback.py` + `cache.py` — v2 포맷
- `rule_top5`를 방향별로 호출하는 `rule_directions(facts, buckets, profile)` 추가.
- AI 전멸 시 방향별 규칙 top5(이유 3개 템플릿)로 `directions` 구성.
- `cache.py`: 저장/로드를 v2 구조(directions 포함)로. `read_cache` 시 source="cache".

### 6. `recommend.py` — 오케스트레이션
```
candidates = build_candidates(facts)
buckets = directions.classify(candidates, facts)      # 규칙
profile = analyze(facts.history, course_index())      # 규칙
try:
    raw = generate(SYSTEM, build_prompt(facts, buckets, profile.summary()))  # AI 1회
    directions = validate_directions(raw, buckets, profile, facts)
    result = Result(directions=directions, default=..., source="live", ...)
except Exception:
    cached = read_cache(facts.studentKey)
    result = cached or Result(rule_directions(facts, buckets, profile), source="fallback", ...)
write_cache(facts.studentKey, result)
```

### 7. 검증 순서 (필수)
1. **키 없이 폴백 먼저**: `python batch.py demo` → 방향별 5개·이유 3개·가드 동작 확인.
2. **데모 학생(202312345) 검증**: 미이수영역이 4·6영역(교양)뿐 → 후보가 교양 위주 → `MAJOR_DEEP` 방향이 가드로 얇아지거나 제외되는지 확인(가드 정상 동작 증거).
3. 그다음 live(`.env` 키 있는 상태).

## C. Spring `recommendation` 패키지 (신설)

- `controller`: `POST`/`GET /profiles/{id}/recommendations`. 소유권 검증(기존 profile 패턴 재사용), `ApiResponse` 래퍼.
- `service`: 체인 = ① `passport-ai/cache/{studentKey}.json`(v2) 서빙 → 없으면 ② **순수 Java 규칙 폴백**(방향 최소 1개=FAST_GRAD 상당의 부족요건 top5, source=fallback). FastAPI 라이브는 인터페이스만 TODO.
- `dto`: v2 응답 record(RecommendationResponse, DirectionDto, RecoItemDto).
- POST는 데모 기준 "사전 생성된 캐시 서빙"으로 충분(재생성 정책은 주석으로 명시).
- **CorsConfig**(WebMvcConfigurer): origin `http://localhost:5173`·`http://localhost:5174` 둘 다, `Authorization` 헤더 허용.
- **방향 선택용 별도 엔드포인트 불필요**(FE가 directions[]에서 선택 표시).
- 검증: 서버 기동 → curl로 캐시 있음/없음 두 경로 + 타인 프로필 접근 거부.
