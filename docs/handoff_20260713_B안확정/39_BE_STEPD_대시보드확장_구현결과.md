# 39. BE STEP D — 성적 대시보드(사진3) 응답 확장 구현 결과

> 작성: 2026-07-14. `38. STEP D — 성적 대시보드(사진3) 응답 확장 · 클로드 코드 프롬프트`에 이어 구현·검증한 기록.
> ⚠️ 이 문서의 "STEP D"는 `37` 문서와 같은 라인(졸업요건·수강이력·대시보드 입력 저장)의 STEP 넘버링이다. `26`/`33` 문서의 "STEP B/C"(passport-ai AI 추천 작업)와는 다른 작업이니 혼동하지 말 것.
> 표기: ✅ 완료 · 🟡 부분 · 🔴 미완 · ⚠️ 주의

---

## 0. 목적

`GET /profiles/{id}/dashboard` 응답에 성적 대시보드(사진3) 화면이 필요로 하는 `overallProgress`·요건별 통합 `categories`·최신 학기 수강 요약을 추가. 기존 7개 필드(`profileId`~`gpaTrend`)는 삭제 없이 유지.

---

## 1. 구현 내용

### 1.1 `EffectiveRequirement` 확장 (`requirement/domain/EffectiveRequirement.java`)

대시보드 계산도 "유저 저장 요건 우선 → 하드코딩 폴백" 경로(`RequirementResolutionService`)를 그대로 타도록, 기존 필드에 4개 추가:

| 필드 | 용도 | `fromHardcoded` | `fromUser` |
|---|---|---|---|
| `majorTotalCredit` | "전공" 카테고리 required | `majorRequiredCredit+majorElectiveCredit` | `credits.majorTotal`(유저 입력값 그대로) |
| `liberalTotalCredit` | "교양" 카테고리 required | `geRequiredCredit+geElectiveCredit` | `credits.liberalTotal`(유저 입력값 그대로) |
| `coreLiberalTargetCount` | "핵심 교양" required | `0`(하드코딩엔 영역 데이터 없음) | `coreLiberal` 중 `target=true` 개수 |
| `graduationExam` | "졸업시험/논문" 상태 판정 | `thesisCertRequired ? THESIS : NONE` | `user.graduationExam` 그대로 |

### 1.2 `CourseCategory` 한글 라벨 (`course/domain/Course.java`)

`getKoreanLabel()`(전공기초/전공필수/전공선택/교양필수/교양선택/자유선택) 추가. **`@JsonValue`가 아니므로 기존 Course/Diagnosis 응답의 `category` 직렬화("MAJOR_BASIC" 등)는 그대로 유지** — 대시보드 `courses[].category` 표기에만 사용.

부수 정리: `recommendation/service/RecommendationService.categoryLabel()`의 중복 switch를 `category.getKoreanLabel()` 호출로 단순화(같은 개념 중복 제거).

### 1.3 `DashboardResponse` 확장 (`dashboard/dto/DashboardResponse.java`)

기존 8개 필드(`profileId`, `deptCode`, `eligibleForGraduation`, `totalCredit`, `shortfallCategories`, `certifications`, `gpa`, `gpaTrend`) 뒤에 추가:

```
overallProgress: int
categories: List<CategoryView>          // {key, name, current, required, unit, status}
semester: String                        // "2026-2학기" 형식, 수강 이력 없으면 null
requestedCredits: int
earnedCredits: int
semesterGpa: Double                     // ⚠️ 아래 1.4 참고
courses: List<CourseLine>               // {category(한글), courseName, credit, grade}
```

`CategoryView.unit`은 `@JsonInclude(NON_NULL)`로 값이 없을 때(졸업인증·졸업시험) 키 자체를 생략 — `current`/`required`는 null이어도 키를 유지(계약 예시의 `exam` 행과 동일하게).

### 1.4 ⚠️ 계약과 다르게 조정한 지점 (팀 확인 필요)

| 항목 | 이슈 | 조치 |
|---|---|---|
| **`gpa` 필드명 충돌** | 기존 최상위 `gpa`가 이미 `{current,required,fulfilled}` 객체 반환 중 — STEP D 계약의 숫자 `gpa`(예: 3.78)를 같은 키에 얹으면 **타입 충돌** | 하위호환 유지를 위해 **`semesterGpa`로 명명**. FE가 이 이름으로 읽도록 조정 필요(또는 팀이 다른 이름 지정) |
| **`coreLiberal.current`** | 과목↔핵심교양 영역 매핑 데이터가 전혀 없음(Course에 영역 정보 없음) | 항상 `0`. `required`(대상 영역 수)만 정확 계산 |
| **`requiredCourse`** | "필수 과목" 총수를 정의하는 과목 카탈로그가 없음 | `required=null`(생성 안 함, 창작 금지), `current`=전공필수+교양필수 이수 건수로 근사 |
| **`exam`(졸업시험/논문)** | `graduationExam=EXAM`(시험)은 합격 여부 추적 데이터가 없음 | `THESIS`/`EITHER`는 기존 `Certification(THESIS)` PASS 여부로 판정. `EXAM`은 항상 "진행중"(검증 불가) |

---

## 2. 검증 (bootRun + curl)

새 프로필로 요건 PUT(전공기초10·전공필수20·전공선택30·교양필수15·교양선택20, `majorTotal:60`·`liberalTotal:35`, 핵심교양 5영역 중 2개 target, `graduationExam:"THESIS"`) 후 2개 학기에 걸쳐 수강 등록, `GET dashboard`로 확인:

| 확인 항목 | 결과 |
|---|---|
| `overallProgress` | 11/130 → `8` (반올림 정확) |
| `categories.major.current` | 9 = 전공기초3 + 전공필수6(B+ 3학점 + A 3학점) — `majorTotal:60` 대비 정확 |
| `categories.liberal.current` | 2 = 교양필수2 (교양선택은 F라 미인정) — `liberalTotal:35` 대비 정확 |
| `categories.coreLiberal` | `current:0`, `required:2`(target 2개) — 설계대로 |
| `categories.requiredCourse` | `current:3`(전공필수2+교양필수1), `required:null`, `unit:"건"` 정상 출력 |
| `categories.certification` | `unit` 키 생략 확인 |
| `categories.exam` | THESIS 인증을 `PASS`로 갱신하기 전 "진행중" → 갱신 후 **"완료"로 전환** 확인 |
| `semester`/`courses` | 최신 학기(2026-2) 자동 판별, 카테고리 한글 라벨·성적 라벨(`A0`→`A` 정규화 포함) 정확 |
| GPA | F학점은 이수 미인정이지만 GPA 분모엔 포함되는 기존 규칙이 신규 카테고리(MAJOR_BASIC)에서도 동일하게 동작 |
| 회귀 | 기존 `totalCredit`/`shortfallCategories`/`certifications`/`gpa`/`gpaTrend` shape·값 변화 없음 |

컴파일: `gradlew compileJava compileTestJava` BUILD SUCCESSFUL. `gradlew test` 실행은 지시대로 하지 않음. 검증 후 로컬 서버 종료.

---

## 3. 변경 파일

**수정**
```
src/main/java/com/passport/requirement/domain/EffectiveRequirement.java
src/main/java/com/passport/course/domain/Course.java                      (한글 라벨 추가분)
src/main/java/com/passport/recommendation/service/RecommendationService.java  (categoryLabel 단순화)
src/main/java/com/passport/dashboard/dto/DashboardResponse.java
src/main/java/com/passport/dashboard/service/DashboardService.java
```

> `37` 문서 기준 STEP B·C 변경분(requirement 패키지 신규 파일 12개, course/diagnosis 관련 수정)은 이 세션에서 추가로 건드리지 않음 — 목록은 `37` 문서 §5 참고.

---

## 4. 남은 작업

| 항목 | 상태 |
|---|---|
| FE `semesterGpa` 필드명 반영(또는 팀이 다른 이름 확정) | 🔴 미결정 — **가장 먼저 확인 필요** |
| STEP E — 졸업 인증 5분야 진단 연결 / DB 영속 | 🔴 미착수(팀 결정 대기) |
| `coreLiberal.current`·`requiredCourse.required` 정확 계산 | 🔴 불가(과목↔영역/카탈로그 데이터 없음 — 데이터 확보 전엔 근사치 유지) |
| git add·commit | 🔴 안 함 (요청 시 진행) |

## 5. 다음 액션 제안

1. `semesterGpa` 네이밍을 FE와 맞출지, 다른 이름(혹은 중첩 구조)으로 바꿀지 결정.
2. STEP E(인증 5분야 연결/DB 영속) 진행 여부 결정.
3. `37`+`39` 변경사항 커밋 여부 결정(원석 Windows에서 push).
