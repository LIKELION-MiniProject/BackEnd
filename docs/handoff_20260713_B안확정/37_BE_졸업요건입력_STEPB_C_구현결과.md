# 37. BE 졸업요건 입력 갭 해소 — STEP B·C 구현 결과

> 작성: 2026-07-14. "36. 백엔드 갭 분석(사진 3화면 기준) + 클로드 코드 실행 프롬프트"에 이어 STEP A(확인)~C(구현)를 클로드 코드가 수행한 기록.
> ⚠️ **주의**: 이 문서의 "STEP B/C"는 **졸업요건·수강이력 입력 저장** 작업 기준이다. `26_BE_STEP_B_구현결과_검증가이드.md`·`33_BE_STEPC_검토결과_푸쉬판단.md`의 "STEP B/C"는 **passport-ai AI 추천(방향성 B안)** 작업 기준으로 서로 다른 라인의 STEP 넘버링이니 혼동하지 말 것.
> 표기: ✅ 완료 · 🟡 부분 · 🔴 미완 · ⚠️ 주의

---

## 0. 배경 — STEP A 확인 결과

`36` 문서의 갭 분석을 실제 코드(`requirement/`, `course/`, `dashboard/`, `application.yml`)와 대조 확인. 전부 사실과 일치했다.

| 화면 | 확인 전 상태 | 판정 |
|---|---|---|
| 졸업 요건 입력(사진1) | `GraduationRequirement`는 JPA 엔티티가 아닌 값 객체, `BigdataAiRequirement`가 학과 기준을 하드코딩. `GET /requirements/{deptCode}` 읽기 전용만 존재, 유저 입력 저장 테이블 없음 | 🔴 신규 구현 필요 |
| 수강 이력(사진2) | `Course` 엔티티·CRUD는 있으나 `MAJOR_BASIC`(전공기초) 없음, `retake` 없음, `Grade`가 `A0/B0` 미인식 | 🟡 보정 필요 |
| 대시보드(사진3) | `GET /profiles/{id}/dashboard` 존재하나 `overallProgress`, 화면 그룹 shape 없음 | 🟡 확장 필요(STEP D, 미착수) |
| DB | H2 인메모리 — 재시작 시 소실 | ⚠️ 미결정(STEP E, 미착수) |

`DiagnosisService.diagnose()`도 확인 결과 `requirementService.getByDeptCode(...)`로 **항상 하드코딩 값만** 사용 — 유저 요건이 진단에 반영될 자리가 없었다는 지적이 정확했다.

---

## 1. STEP B — 졸업 요건 유저 입력 저장 ✅

### 1.1 신규 파일 (`requirement` 패키지)

| 파일 | 역할 |
|---|---|
| `domain/RequirementCredits.java` | 학점 항목 임베더블(총이수/전공필수/전공선택/전공기초/교양필수/교양선택/전공합계/교양합계/교직/자유선택/외부인정/복수전공/부전공/연계전공/융합전공 — 15개 int) |
| `domain/CoreLiberalArea.java` | 핵심교양 1개 영역 임베더블(areaNo·areaName·courseCount·credit·target) — `@ElementCollection`으로 5행 저장 |
| `domain/CertMark.java` | 졸업 인증 5분야의 대상 여부 enum. `TARGET`(대상)/`NOT_TARGET`(비대상)/`DONE`(완료) — `Course.Grade`와 동일한 라벨 직렬화 패턴 |
| `domain/RequirementCertificationTargets.java` | 인증 5분야(foreignLangCert·infoProcessing·cpr·socialService·foreignLangExtra) 임베더블. draft 대비 각 필드 nullable |
| `domain/UserGraduationRequirement.java` | **신규 엔티티**, `profiles`와 1:1(`profile_id` unique). `requiredCourseType`(교필/전필), `coreLiberal`(5행), `certification`, `graduationExam`(EXAM/EITHER/THESIS/NONE), `draft` 보유 |
| `domain/EffectiveRequirement.java` | DiagnosisService가 실제로 쓰는 "유효 요건" 레코드. `fromUser(...)`/`fromHardcoded(...)` 팩토리로 유저 저장값·하드코딩 폴백을 동일 shape로 통일 |
| `repository/UserRequirementRepository.java` | `findByProfileId` |
| `service/RequirementResolutionService.java` | 유저 저장값 있으면 그것, 없으면 `BigdataAiRequirement` 폴백 — `EffectiveRequirement` 반환 |
| `service/UserRequirementService.java` | GET(저장값 또는 기본값 매핑)·PUT(upsert, draft 시 유효성 완화) |
| `dto/RequirementSaveRequest.java` | PUT 요청 DTO. 중첩 record `Credits`/`CoreLiberalItem`/`CertificationTargets` |
| `dto/UserRequirementResponse.java` | GET/PUT 응답 DTO. `source`(`"user"`\|`"default"`) 포함 |
| `controller/UserRequirementController.java` | `GET/PUT /api/v1/profiles/{profileId}/requirements` |

### 1.2 계약 검증 — PART 3 JSON과 실제 응답 비교

curl로 확인한 실제 PUT 응답(요약):

```json
{
  "credits": {"total":140,"majorRequired":24,"majorElective":42,"majorBasic":9,
    "liberalRequired":16,"liberalElective":24,"majorTotal":75,"liberalTotal":40, ...},
  "requiredCourseType": "교필",
  "coreLiberal": [{"areaNo":1,"areaName":"언어와 문학","courseCount":1,"credit":2,"target":true}, ...5개],
  "certification": {"foreignLangCert":"대상","infoProcessing":"완료","cpr":"비대상","socialService":"대상","foreignLangExtra":"비대상"},
  "graduationExam": "EXAM",
  "draft": false,
  "source": "user"
}
```

`36` 문서 PART 3 계약의 키 이름(`liberalRequired`/`liberalElective`, `coreLiberal[5]`, `certification{5필드}`)과 **정확히 일치**하도록 DTO를 설계했다(엔티티 필드명도 동일하게 맞춰 매핑 계층을 단순화).

### 1.3 DiagnosisService 연동

- `DiagnosisService`의 `RequirementService` 직접 의존 → `RequirementResolutionService`로 교체.
- `GraduationRequirement` 타입 사용 → `EffectiveRequirement`로 교체(`majorBasicCredit` 필드 추가).
- `calculateCategoryProgress`에 `MAJOR_BASIC` 카테고리 진단 행 추가.
- **curl로 확인**: 유저가 PUT한 학점 기준(총 140, 전공필수 24 등)이 `GET diagnosis`에 그대로 반영됨 — 하드코딩(130/21/39...) 대신 유저 값 기준으로 shortfall 계산.

⚠️ **의도된 경계(팀 확인 필요)**:
- **인증 필수 여부(어학/봉사/논문)·최소 GPA는 유저 입력 스키마(사진1)에 없는 값**이라, 유저가 요건을 저장해도 `DiagnosisService`는 이 세 값만 계속 `BigdataAiRequirement` 하드코딩을 사용한다.
- `source:"default"` 응답에서 핵심교양 영역명(언어와 문학 등), 인증 5분야 중 3개(정보처리/CPR/외국어비교과), 전공기초·교직·복수전공 등 학점은 하드코딩에 대응 데이터가 없어 **빈 값/0/비대상으로 채웠다**(근거 없는 값 창작 금지 원칙에 따름). 실제 학과 데이터 확정 시 `UserRequirementResponse.fromDefault()` 매핑을 함께 보강해야 한다.
- 인증 5분야(`RequirementCertificationTargets`, "이 요건이 대상인가")는 기존 `certification` 도메인(`PASS-CERT-*`, "학생이 합격했는가")과 **별개 개념**으로 저장만 해두었고 진단 로직에는 아직 연결하지 않았다 — STEP E 스코프.

### 1.4 유효성 검증

- `draft:true` → `credits`/`coreLiberal`/`requiredCourseType`/`certification`/`graduationExam` 없거나 비어 있어도 200 저장.
- `draft:false`(최종 제출) → `coreLiberal`이 5개가 아니거나 필수 항목 누락 시 400 `COMMON-001`("핵심교양은 5개 영역을 모두 입력해야 합니다." 등).

---

## 2. STEP C — 수강 이력 스키마 보정 ✅

| 변경 | 파일 |
|---|---|
| `CourseCategory`에 `MAJOR_BASIC`(전공기초) 추가 | `course/domain/Course.java` |
| `retake`(재수강) boolean 필드 추가 — 생성자·`update()` 반영 | `course/domain/Course.java` |
| `Grade.fromLabel`이 `A0/B0/C0/D0` → `A/B/C/D` 별칭 인식(응답은 항상 정식 라벨로 정규화) | `course/domain/Course.java` |
| `retake` 필드 추가 | `course/dto/CourseCreateRequest.java`, `CourseUpdateRequest.java`, `CourseResponse.java` |
| `retake` 전달 반영 | `course/service/CourseService.java` |

**학기 입력 방식 결정**: 기존 `year:int + semester:int` + 과목 1건씩 POST 방식을 **유지**했다(배치 POST·문자열 학기로 바꾸지 않음). `36` 문서 STEP C-4가 "택1 후 주석 명시"로 남긴 선택지 중, 기존 CRUD 계약을 깨지 않는 쪽을 택함 — FE가 "2026-1학기" 문자열을 `year`/`semester`로 분해해 전송하는 현행 방식 그대로.

### 2.1 부수 발견 — 컴파일 필수 수정

`MAJOR_BASIC` 추가로 `recommendation/service/RecommendationService.java`의 `categoryLabel()`이 `CourseCategory`에 대한 **exhaustive switch**(default 없음)라 컴파일이 깨졌다. `case MAJOR_BASIC -> "전공기초";`를 추가해 해결. (`recommendation` 패키지는 이번 세션 시작 시점에 이미 다른 세션이 작업 중이던 새 미완성 코드 — 이 한 줄만 컴파일 유지를 위해 최소 수정)

---

## 3. 테스트 배선

`DiagnosisServiceTest`가 `@Mock RequirementService`를 직접 사용하고 있어 `DiagnosisService` 생성자 변경(→ `RequirementResolutionService`)과 어긋나는 문제를 발견 → mock 필드와 스텁 라인만 `RequirementResolutionService`/`EffectiveRequirement.fromHardcoded(...)`로 교체. 테스트 로직·assertion은 변경 없음.

`gradlew test` 실행은 지시대로 하지 않았고, `gradlew compileJava compileTestJava`로 컴파일만 확인(BUILD SUCCESSFUL).

---

## 4. E2E 검증 (bootRun + curl)

로컬 H2(`jdbc:h2:mem:passportdb`)로 `gradlew bootRun` 후 아래 흐름을 curl로 확인, 검증 후 서버 종료:

| # | 호출 | 결과 |
|---|---|---|
| 1 | `POST /auth/signup` → `POST /auth/login` | 200, JWT 발급 |
| 2 | `POST /profiles` | 200, profileId=1 |
| 3 | `GET /profiles/1/requirements` (PUT 전) | `source:"default"`, 하드코딩 값 매핑 확인 |
| 4 | `PUT /profiles/1/requirements` (커스텀 값) | 200, 저장값 그대로 echo |
| 5 | `GET /profiles/1/requirements` (PUT 후) | `source:"user"`, 저장값 유지 확인 |
| 6 | `GET /profiles/1/diagnosis` | 카테고리별 required가 유저 값(140/24/42/9/16/24/33) 그대로 반영, `MAJOR_BASIC` 행 존재 |
| 7 | `POST /profiles/1/courses` (`category:"MAJOR_BASIC"`, `grade:"A0"`, `retake:true`) | 응답 `grade:"A"`(정규화), `retake:true` |
| 8 | `GET /profiles/1/diagnosis` (과목 등록 후) | `MAJOR_BASIC` earned=3, shortfall 9→6, GPA=4.0 |
| 9 | `GET /profiles/1/dashboard`, `GET /profiles/1/gpa-trend` | 정상 동작, 회귀 없음 |
| 10 | `PUT requirements` `draft:true` + 빈 값 | 200 저장(완화된 검증) |
| 11 | `PUT requirements` `draft:false` + `coreLiberal:[]` | 400 `COMMON-001` |
| 12 | `GET /profiles/1/recommendations` (MAJOR_BASIC 최대 shortfall 상황) | fallback 추천에 `"category":"전공기초"` 정상 출력 |

---

## 5. 변경 파일 목록 (git status, 커밋 안 함)

**신규**
```
src/main/java/com/passport/requirement/domain/RequirementCredits.java
src/main/java/com/passport/requirement/domain/CoreLiberalArea.java
src/main/java/com/passport/requirement/domain/CertMark.java
src/main/java/com/passport/requirement/domain/RequirementCertificationTargets.java
src/main/java/com/passport/requirement/domain/UserGraduationRequirement.java
src/main/java/com/passport/requirement/domain/EffectiveRequirement.java
src/main/java/com/passport/requirement/repository/UserRequirementRepository.java
src/main/java/com/passport/requirement/service/RequirementResolutionService.java
src/main/java/com/passport/requirement/service/UserRequirementService.java
src/main/java/com/passport/requirement/dto/RequirementSaveRequest.java
src/main/java/com/passport/requirement/dto/UserRequirementResponse.java
src/main/java/com/passport/requirement/controller/UserRequirementController.java
```

**수정**
```
src/main/java/com/passport/diagnosis/service/DiagnosisService.java
src/test/java/com/passport/diagnosis/service/DiagnosisServiceTest.java
src/main/java/com/passport/course/domain/Course.java
src/main/java/com/passport/course/dto/CourseCreateRequest.java
src/main/java/com/passport/course/dto/CourseUpdateRequest.java
src/main/java/com/passport/course/dto/CourseResponse.java
src/main/java/com/passport/course/service/CourseService.java
src/main/java/com/passport/recommendation/service/RecommendationService.java   (컴파일 필수 수정 1줄)
```

> `SecurityConfig.java`·`application.yml`·`CorsConfig.java`·`passport-ai/`·`recommendation/`(나머지 파일)은 이 세션 시작 이전부터 있던 변경/신규 사항이며 이번 STEP B·C 작업 범위가 아니다.

---

## 6. 남은 작업

| 항목 | 상태 |
|---|---|
| STEP D — 대시보드 사진3 shape 확장(`overallProgress`, 통합 카테고리, 학기별 수강표) | 🔴 미착수 |
| STEP E — 졸업 인증 5분야 확장 / DB 영속(H2 file 모드) | 🔴 미착수(팀 결정 대기) |
| git add·commit | 🔴 안 함 (요청 시 진행) |
| 노션/핸드오프 인덱스(`30_INDEX_재개가이드_v3.md`) 갱신 | 🔴 안 함 — 이 문서만 신규 추가, 인덱스 연결은 별도 요청 필요 |

## 7. 다음 액션 제안

1. STEP D(대시보드 확장) 진행 여부 결정.
2. `fromDefault()` 매핑의 ⚠️ 근사치 항목(핵심교양 영역명, 인증 5분야 일부) — 실제 학과 데이터 확보되면 교체.
3. 위 변경사항 커밋 여부 결정(원석 Windows에서 push).
