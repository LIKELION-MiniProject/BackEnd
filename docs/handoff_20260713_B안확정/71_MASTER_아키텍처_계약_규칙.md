# 71. MASTER 인계 ② — 아키텍처 · 계약 v2.1 · 규칙 (SSOT)

> AI 추천 아키텍처, 응답 계약, 모든 판정 규칙의 단일 진실원. 코드·FE·노션은 이 문서에 맞춘다.

---

## 1. AI 추천 아키텍처 = B안 + 라이브 브릿지

### 흐름
```
[POST /profiles/{id}/recommendations]  ("AI 분석하기" 버튼)
  ├ 규칙: 유저 데이터(수강+인증+요건+프로필) SHA-256 지문 계산
  ├ cache/{studentId}.fp 의 이전 지문과 비교
  │    ├ 일치       → 캐시(cache/{studentId}.json) 그대로 반환 (AI 미호출)
  │    ├ .fp 없음   → 캐시 반환 + 지문만 기록 (사전 캐시 안전 마이그레이션)
  │    └ 불일치/캐시없음 → 라이브 재생성(bridge.py subprocess) → source=live 면 지문 저장
  └ 브릿지 실패 → 기존 캐시 → 없으면 규칙 폴백(진단 부족구분 top5, 과목 창작 안 함)

[GET /profiles/{id}/recommendations]  조회 전용 — AI 미호출, 마지막 캐시(없으면 규칙 폴백)

[방향 탭/비교]  프론트가 응답의 directions[]에서 표시만 전환 (서버 재호출 없음)
```

### 규칙 vs AI 경계
| 담당 | 항목 |
|---|---|
| **규칙(결정적)** | 졸업진단·부족요건·후보축소(182→~16)·5방향 분류·caution·thin·이유①(성향)·지문·폴백 |
| **AI(1회, 변동)** | 방향별 과목 5개 선택 + 이유②(특성)·이유③(졸업/전공) |

### passport-ai 파이프라인(core/)
`candidates`(축소) → `profile`(성향) → `directions`(5방향 규칙+가드+caution+category_label) → `prompt`/`gemini`(AI 1회, response_schema 강제, temperature 0.3) → `validate`(환각차단+이유①+category+규칙보충) → `fallback`(방향별 규칙 top5) → `cache`(v2, 구버전 호환) → `recommend`(오케스트레이션). 진입 `batch.py demo`.

## 2. 응답 계약 v2.1 (SSOT)
래퍼: `{ success, data, message }` (성공 message=null, 실패 data=null).
```jsonc
data = {
  "recommendations": [ /* defaultDirection 미러(하위호환) */ ],
  "directions": [
    { "directionId":"FAST_GRAD", "name":"졸업요건 집중형", "description":"…",
      "caution": null, "thin": false,
      "recommendations": [
        { "courseCode":"…", "courseName":"…", "credit":3, "category":"전공선택",
          "reasons":["성향 맞춤","과목 특성","졸업·전공 기여"] } ] } ],
  "defaultDirectionId": "FAST_GRAD",
  "source": "live",            // live | cache | fallback
  "generatedAt": "ISO-8601"
}
```

### directionId ↔ name ↔ 규칙 ↔ caution
| directionId | name | 분류 규칙 | caution(비-thin일 때만) |
|---|---|---|---|
| FAST_GRAD | 졸업요건 집중형 | 미이수영역 커버리지+학점효율 | null |
| MAJOR_DEEP | 전공 심화형 | category==MAJOR | null |
| GRADE_SAFE | 학점 안정형 | 성적 너그러움+시험/팀플 부담 낮음 | null |
| EXAM_SOLO | 시험·개별평가형 | 시험 많음·팀플 없음 | "시험 비중이 높아 시험 부담이 클 수 있어요." |
| TEAM_ACTIVE | 협업·활동형 | 팀플 보통/많음 또는 발표 | "팀플·발표 비중이 높아요. 협업 경험을 넓히려는 분께 권하는 선택적 도전이에요." |

## 3. 계약 규칙 (전부 준수)
- **reasons 순서 고정**: `[0]=성향맞춤(규칙), [1]=과목특성(AI), [2]=졸업·전공기여(AI)]`. 폴백 시에도 3개(규칙). FE는 위치로 라벨 렌더.
- **caution**: `null`이면 FE 미표시, 문자열이면 빨간 글씨(문구는 서버가 줌, FE 하드코딩 금지). EXAM_SOLO·TEAM_ACTIVE 전용. **thin 방향이면 caution=null**(thin 안내와 중복 방지 — `directions.py effective_caution`).
- **thin**: 매칭 과목 <5면 `thin=true` + **매칭 개수 그대로**(5개로 패딩 금지). 매칭 0개 방향은 배열에서 **제외**. **보충은 그 방향 후보 안에서만**(방향 밖 과목 금지).
- **category**: 이수구분 라벨(전공필수/전공선택/전공기초/전공/교양). courses.json detailCategory에서 매핑.
- **최상위 recommendations** = defaultDirection 5개 미러(하위호환 — directions 컷돼도 FE 안전).
- **source & 배지**: live/cache는 화면상 동일(정상, 초록 "AI 분석 완료"). **"기본 추천" 배지는 fallback일 때만**(앰버).
- directionId는 코드 고정값(변경 금지). 표시명이 디자인과 다르면 name만 맞춤.

## 4. 데이터 일관성(비결정성) 결론
- 방향 분류·후보는 100% 규칙 → **같은 방향이면 후보 집합 동일**. AI는 그 안 5개 선택·이유 문구만 temperature 0.3으로 변동. → "방향 같으면 대체로 동일, 과목만 좀 다름".
- 라이브 브릿지: 데이터 무변화면 캐시 재사용 → 반복 클릭해도 동일. 변경 시에만 재생성. 더 고정 원하면 gemini.py temperature 0.1/0(선택).

## 5. STEP E — 인증 5분야 진단 판정 규칙
- 저장: PUT /requirements의 `certification`(foreignLangCert/infoProcessing/cpr/socialService/foreignLangExtra, 값 대상/비대상/완료=CertMark TARGET/NOT_TARGET/DONE).
- 판정: **유저가 '대상(TARGET)'으로 표시한 분야가 전부 '완료(DONE)'여야 fulfilled=true.** 비대상·미표기 무시. 대학 필수/택1 규칙은 검증 데이터 없어 창작 안 함.
- eligible 반영: `eligible = creditsOk && certsOk && gpaOk && gradCertOk`. 유저 미저장 시 gradCertOk=true(기존 동작 유지). **현재 기존 인증(LANGUAGE/VOLUNTEER/THESIS) AND 5분야**(보수적). ⚠️ "5분야만"으로 바꾸려면 DiagnosisService eligible 1줄 수정(원석 결정).
- 응답에 `graduationCertification{areas[5]{area,mark}, fulfilled}` 추가(기존 certifications[] 유지).

## 6. DB / 인프라
- H2 **파일 영속**: `jdbc:h2:file:./data/passportdb;AUTO_SERVER=TRUE`, ddl-auto update. `data.sql`은 데모 유저 2명 **MERGE**(재기동 안전). 휘발성 원하면 url을 `jdbc:h2:mem:passportdb`.
- Gemini: `.env`의 `GEMINI_MODEL=gemini-3.1-flash-lite`(하루 ~150회). `requirements.txt` `google-genai==1.38.0` 고정(1.39+ 클라이언트 조기종료 버그 회피).
- CORS: origin `localhost:5173`·`5174` + Authorization.
