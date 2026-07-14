# 53. Gemini 모델 교체 + 라이브 AI 브릿지 구현 결과 (2026-07-14)

> 범위: 문서 `44`(Gemini 모델 교체 프롬프트)를 실행한 시점부터 이번 세션 종료까지 실제로 한 일 전부.
> 함께 볼 것: `44_클로드코드_Gemini모델교체_프롬프트.md`(원 지시서), `51_검토_백엔드_상세.md`(§3·§5, 교체 전 구조).
> 표기: ✅ 완료·검증 / 🔴 미완 / ⚠️ 주의

---

## 1. 한 줄 요약

① `44` 문서 지시대로 **Gemini 모델을 3.5-flash → 3.1-flash-lite로 교체**하고 실호출로 검증했다.
② 검증 중 원석이 기존 설계("사전 생성 캐시를 그대로 서빙")가 실제 의도와 다르다는 걸 지적 — **"AI 추천 버튼을 누를 때 유저 데이터(성적·졸업요건·자격증)가 하나라도 바뀌었으면 캐시를 쓰지 말고 그 순간 최신 데이터로 AI를 다시 돌려라"** 로 요구사항을 재정의했다.
③ 이 요구사항에 맞춰 **Spring ↔ passport-ai 라이브 브릿지를 신규 구현**했다(기존엔 미구현 상태였음 — `51` 문서 §5에서 "스트레치"로 남겨뒀던 부분). 데이터 변경 여부는 유저 입력 전체의 SHA-256 지문으로 판정한다.
④ 성적·자격증·졸업요건 세 가지 변경 케이스를 각각 실서버 curl로 재분석 트리거를 확인했고, 테스트로 넣은 데모 데이터는 원복했다.
⑤ **이번에 만든/고친 파일은 전부 미커밋** — 기존 "전체 미커밋" 리스크(`50`·`52` 문서)에 그대로 합류.

---

## 2. STEP A — Gemini 모델 교체 (문서 `44` 실행)

`44` 문서의 STEP 1~5를 순서대로 실행했다.

| STEP | 내용 | 결과 |
|---|---|---|
| 1 | `python batch.py models`로 실제 사용 가능한 모델 목록 확인 | `models/gemini-3.1-flash-lite`(안정판) 존재 확인. 참고로 `-preview`판도 별도 존재 |
| 2 | `passport-ai/.env`의 `GEMINI_MODEL` 한 줄만 교체 | `gemini-3.5-flash` → `gemini-3.1-flash-lite`. **`GEMINI_API_KEY` 줄은 미접촉** |
| 3 | `python batch.py demo`로 구동 검증 | `source=live`, 데모 학생 4방향(FAST_GRAD/GRADE_SAFE(thin)/EXAM_SOLO(caution)/TEAM_ACTIVE(thin)), 각 추천 `reasons` 3개·`category` 포함, 후보 밖 코드 없음(환각 없음). `cache/202312345.json` 새 출력으로 갱신됨 |
| 4 | temperature 조절(선택) | 지시대로 **보류**(0.3 유지) |
| 5 | 보고 + `.env` 커밋 제외 확인 | `git check-ignore passport-ai/.env` → 정상 무시 확인. **커밋 안 함**(지시대로) |

추가로 [.env.example](../../passport-ai/.env.example)의 예시 모델명도 새 모델로 갱신했다(이 파일은 커밋 가능 대상).

### 이 단계에서 발견한 결함(경고, 치명적 아님)
- `google-genai` 라이브러리가 `Warning: non-text parts in the response: ['thought_signature']`를 매 호출 출력. 3.x 계열이 thought 파트를 반환해서 나오는 것이고, 텍스트 파싱 자체는 `source=live`로 정상 완료됨 — 동작 영향 없음.

### 자바 쪽 회귀 확인
- `gradlew compileJava` 통과.
- `gradlew test`(단위 테스트, `GpaCalculatorTest`/`DiagnosisServiceTest`)는 **이 PC의 한글 사용자 홈 경로(`C:\Users\송원석`) 때문에 Gradle 테스트 워커가 `GradleWorkerMain`을 못 찾고 실행 자체가 안 됨** — 코드 문제가 아니라 환경 문제(팀 문서에도 "`gradlew test` 대신 bootRun+curl" 안내가 이미 있었음). 대신 서버(`bootRun`)를 띄우고 로그인 → `GET /recommendations` curl로 새 모델이 생성한 캐시가 계약 v2.1 구조(`directions`/`thin`/`caution`/`reasons`/`category`) 그대로 정상 서빙되는 걸 E2E로 확인.

---

## 3. STEP B — 요구사항 재정의: "라이브 재분석" 확인 대화

`44` 작업 완료 보고 후 원석이 다음을 확인차 질문 → 대화로 정리됨.

> "우리는 캐시로 계속 서빙하는 게 아니라, 버튼 누를 때마다 AI가 최신 데이터로 추천해주는 방식이다. 캐시는 '비교하는 동안 결과가 안 바뀌는 것'에만 쓰는 거다."

기존 코드(`RecommendationService.resolve()`)를 다시 읽어 사실을 확인한 결과:
- 기존 로직은 **`cache/{studentId}.json` 파일 존재 여부만** 보고 있었음. 성적·요건 변경과 전혀 무관하게 파일이 있으면 무조건 그 내용을 반환.
- 캐시 파일은 사람이 터미널에서 `python batch.py demo`를 수동 실행해야만 갱신됨. Spring 안에 "유저 데이터가 바뀌었으니 캐시 무효화" 로직 자체가 없었음.
- 이는 `51` 문서 §5에 **의도된 설계(발표 안전용)**로 명시돼 있었던 부분 — 이번 확인으로 그 결정을 뒤집고 **라이브 브릿지 구현**으로 방향을 바꿈.

최종 확정된 요구사항(원석 정정 포함):
- 데이터 **무변화** → 이전 결과 그대로(같은 결과 반복 OK, AI 미호출).
- 성적·졸업요건·자격증 등 데이터가 **하나라도 변화** → 캐시를 버리고 **5방향·추천과목 전부** 최신 데이터로 새로 분석(일부만 바뀌어도 되고, 전부 달라져도 무방 — "부분 변경 유지" 제약 없음).
- 한 번 생성된 응답 안에서 5방향을 비교하는 동안은 결과 유지(응답 하나에 5방향이 다 담기는 기존 계약 구조로 자동 충족, 별도 구현 불필요).

---

## 4. STEP C — 라이브 브릿지 구현

### 4.1 신규 파일

**[passport-ai/bridge.py](../../passport-ai/bridge.py)** (신규)
- Spring이 stdin으로 유저 데이터 JSON을 주면, `core.diagnosis.from_payload` → `core.recommend.recommend()` 실행 후 stdout으로 결과 JSON 하나만 출력(로그는 전부 stderr).
- Spring DB의 수강 이력엔 과목코드가 없고 과목명만 있어, `data/courses.json` 이름 색인으로 과목명→코드 매핑을 수행. 카탈로그에 없는 과목명은 성향분석에서만 빠지고 학점 계산엔 영향 없음(그건 이미 Spring 진단이 끝낸 값을 그대로 씀 — 원칙 1 준수, AI가 사실 판정을 하지 않음).
- Windows 콘솔 인코딩(cp949)과 무관하게 stdin/stdout/stderr를 UTF-8로 강제.

**[src/main/java/com/passport/recommendation/service/PassportAiBridge.java](../../src/main/java/com/passport/recommendation/service/PassportAiBridge.java)** (신규)
- `ProcessBuilder`로 `python bridge.py`를 `passport-ai/` 디렉터리에서 subprocess 실행.
- payload를 stdin으로 write, stdout/stderr는 별도 스레드(`CompletableFuture`)로 비동기 소비(파이프 버퍼 참으로 인한 데드락 방지).
- 타임아웃 60초, 실패(비정상 종료·타임아웃·파싱 실패) 시 `Optional.empty()` 반환 — 폴백 판단은 호출자 몫.

### 4.2 수정 파일

**[src/main/java/com/passport/recommendation/service/RecommendationService.java](../../src/main/java/com/passport/recommendation/service/RecommendationService.java)** (전면 수정)

- **`GET /recommendations`**: 조회 전용으로 그대로 유지 — AI 호출 없이 마지막 캐시(없으면 규칙 폴백) 서빙.
- **`POST /recommendations`** (핵심 변경):
  1. 프로필의 **지문(fingerprint)**을 계산 — 수강 이력 전체(과목명·학점·구분·성적·연도·학기·재수강) + 인증 상태(어학/봉사/논문) + 유저 저장 졸업요건(학점 요건 15개 필드·필수구분·핵심교양 5영역·인증 5분야 대상여부·졸업시험유형) + 프로필(학과·학번·입학연도)을 정규화 문자열로 이어붙여 SHA-256 해시.
  2. `cache/{studentId}.fp`에 저장된 이전 지문과 비교.
     - **캐시는 있는데 지문 파일이 없음**(지문 도입 이전 캐시, 예: 사전 생성 데모) → 캐시 그대로 반환 + 지문만 기록(안전 마이그레이션, 데모 캐시 안 깨짐).
     - **지문 일치** → 캐시 그대로 반환(AI 미호출).
     - **지문 불일치 또는 캐시 없음** → `PassportAiBridge.generate()`로 라이브 재생성. `source=live`일 때만 새 지문 저장(AI가 일시 실패해 폴백이 나온 경우엔 지문을 안 갱신해서 다음 클릭에 자동 재시도되게 함).
  3. 브릿지 실패 시 안전망: 기존 캐시 → 그마저 없으면 기존 규칙 폴백(`ruleFallback`, 과목 창작 안 함) — 발표 사고 방지 원칙 유지.
- 새 의존성 주입: `PassportAiBridge`, `CourseRepository`, `CertificationRepository`, `UserRequirementRepository` (전부 기존 패키지의 기존 리포지토리, 신규 리포지토리 없음).

**[src/main/resources/application.yml](../../src/main/resources/application.yml)** (2줄 추가)
```yaml
passport-ai:
  cache-dir: ${PASSPORT_AI_CACHE_DIR:passport-ai/cache}
  python-command: ${PASSPORT_AI_PYTHON:python}   # 신규
  dir: ${PASSPORT_AI_DIR:passport-ai}             # 신규
```

### 4.3 바꾸지 않은 것
- `passport-ai/core/` 내부 로직(candidates·directions·prompt·gemini·validate·fallback·cache·recommend) — 무변경. `recommend()`는 원래도 매 호출 시 `cache/{studentId}.json`을 덮어쓰도록 이미 만들어져 있었어서(`recommend.py:44 write_cache`), 브릿지만 새로 연결하면 됐음.
- 기존 캐시 서빙·규칙 폴백 코드 경로 — 그대로 유지, 브릿지 실패 시 안전망으로 재사용.
- `RecommendationController` — 엔드포인트·시그니처 무변경.

---

## 5. STEP D — E2E 검증 (실서버 curl, 데모 계정)

서버(`gradlew bootRun`)를 띄우고 `demo1@passport.ac.kr`로 로그인해 profile 1(학번 `202312345`)에 대해 순서대로 검증.

| # | 조작 | POST /recommendations 결과 | 판정 |
|---|---|---|---|
| 1 | (최초, 지문 없음) | 기존 캐시 서빙 + `.fp` 최초 기록 | ✅ 안전 마이그레이션 |
| 2 | 아무 변경 없이 재클릭 | 동일 `generatedAt` | ✅ 캐시 유지 |
| 3 | **과목 추가**(운영체제 A) | `generatedAt` 변경, `source=live`, 6.4초 | ✅ 재분석 |
| 4 | 변경 없이 재클릭 | 3번과 동일 `generatedAt` | ✅ 캐시 유지 |
| 5 | 과목 삭제(원복) | `generatedAt` 재변경, `source=live` | ✅ 재분석 |
| 6 | **자격증 변경**(LANGUAGE=PASS 최초 등록) | `generatedAt` 변경, 5.8초 | ✅ 재분석 |
| 7 | 변경 없이 재클릭 | 6번과 동일 | ✅ 캐시 유지 |
| 8 | **졸업요건 최초 저장**(15개 학점 필드 + 핵심교양 5영역 + 인증 5분야 + 졸업시험유형) | `generatedAt` 변경 | ✅ 재분석 |
| 9 | 졸업요건 필드 1개만 변경(전공선택 36→42학점) | `generatedAt` 재변경 | ✅ 재분석 |
| 10 | 변경 없이 재클릭 | 9번과 동일 | ✅ 캐시 유지 |

**결론: 성적·졸업요건·자격증 세 가지 데이터 유형 모두 — 변경 시에만 재분석, 무변경 시엔 캐시 유지가 실측으로 확인됨.**

### 부작용 — 실데이터 방향 개수 한계 (기존 한계, 재확인)
- 데모 JSON(`data/demo_diagnosis.json`)에는 교양 영역 미이수 정보가 있어 4방향(FAST_GRAD/GRADE_SAFE/EXAM_SOLO/TEAM_ACTIVE)이 나왔지만, **실제 유저 데이터로 라이브 생성하면 방향이 2개(FAST_GRAD·MAJOR_DEEP)만 나옴.** Spring DB·진단에 "핵심교양 과목↔영역" 매핑 데이터가 없어(`51` 문서 §7의 기존 한계) `buildPayload()`가 `lackingAreas`를 항상 빈 배열로 넘기기 때문. 이 매핑 데이터를 확보하기 전까진 라이브 생성분은 항상 2방향에 머무름 — **원석 확인 필요 항목**(발표에서 "왜 2방향만 나오냐" 질문 대비).

---

## 6. STEP E — 테스트 데이터 원복

E2E 검증을 위해 데모 계정(profile 1)에 넣은 테스트용 자격증·요건은 삭제 API가 없어 H2 DB에 직접 SQL로 정리:
- `certifications`(profile_id=1) 전부 삭제
- `user_graduation_requirements` + `user_requirement_core_liberal`(profile_id=1) 삭제
- `courses`(profile_id=1) 0건 확인(테스트용으로 추가했던 과목은 API로 이미 삭제 완료된 상태였음)
- `python batch.py demo`로 데모 캐시(`cache/202312345.json`)를 4방향 상태로 재생성
- `cache/202312345.fp` 삭제 후 재클릭 1회로 현재(원복된) DB 상태와 지문 재동기화

**최종 확인**: `GET/POST /profiles/1/recommendations` → `source=live`, 4방향(FAST_GRAD/GRADE_SAFE/EXAM_SOLO/TEAM_ACTIVE) 정상. 발표 데모 재현성 훼손 없음.

---

## 7. 최종 반영 파일 목록 (전부 미커밋)

| 파일 | 상태 |
|---|---|
| `passport-ai/.env` | 수정(GEMINI_MODEL만, 커밋 대상 아님 — gitignore) |
| `passport-ai/.env.example` | 수정(예시 모델명 갱신, 커밋 가능) |
| `passport-ai/bridge.py` | **신규** |
| `passport-ai/cache/202312345.json` | 재생성(4방향, 최신 모델 출력) |
| `src/main/java/com/passport/recommendation/service/PassportAiBridge.java` | **신규** |
| `src/main/java/com/passport/recommendation/service/RecommendationService.java` | 전면 수정 |
| `src/main/resources/application.yml` | 2줄 추가(`python-command`, `dir`) |

`git status`로 확인 시 `passport-ai/`와 `src/main/java/com/passport/recommendation/`이 통째로 미추적(`??`) 상태 — 기존 "전체 미커밋" 리스크(`50`·`52` 문서 §1)에 그대로 포함됨. 이번 세션에서 별도로 커밋하지 않았음(원석 지시 대기).

---

## 8. 남은 것 / 원석·팀 결정 필요

1. **🔴 커밋/푸쉬** — 여전히 최우선 미해결. `52` 문서 §5 명령 그대로 유효, 이번 신규 파일들도 포함해서 커밋 필요.
2. **⚠️ 실데이터 라이브 생성 시 2방향만 나오는 한계**(§5 참고) — 핵심교양 과목↔영역 매핑 데이터 확보 전까지는 구조적 한계. 발표 시연 시 데모 계정(캐시된 4방향)으로 보여줄지, 실데이터 계정(2방향)도 함께 보여줄지 확인 필요.
3. **⚠️ 브릿지 응답 시간** — 실측 5.8~6.4초/회. 발표 중 라이브로 여러 번 재클릭하면 그때마다 대기 시간 발생(캐시 히트 시엔 즉시). FE 로딩 UI(문서 `29`/`29b`) 반영이 이 대기시간 체감에 중요해짐.
4. **Gemini 무료 한도**: 3.1-flash-lite 하루 ~150회. 라이브 재생성이 이제 실제로 도니 리허설·발표 중 호출 횟수가 예상보다 늘어날 수 있음 — 리허설 때 소비량 감안.
5. 기존 미결정 목록(`52` 문서 §3)은 이번 작업과 무관하게 그대로 유효 — STEP E 판정 방식(AND vs 단독), FE `semesterGpa` 반영, 프로필 최초 생성 화면 등.
