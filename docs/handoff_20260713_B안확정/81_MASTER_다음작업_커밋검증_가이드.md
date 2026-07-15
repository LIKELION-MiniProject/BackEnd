# 81. MASTER 세션 인계 ② — 커밋·검증·다음 작업 (2026-07-15)

> 짝 문서 `80_MASTER_세션인계_STEP1_persona.md`(무엇을 했나)와 함께 본다. 이 문서는 **지금 당장 뭘 해야 하나**.
> 표기: ✅ 완료 / 🟡 부분·확인필요 / 🔴 미완 / ⚠️ 주의

---

## 1. ⚠️ 가장 중요 — 아직 커밋도 push도 안 됨

이 세션(80 문서 작업 A·B·C) 전부 **로컬 워킹트리에만 있고 git add/commit/push는 안 했다.** 코워크는 git 쓰기 권한이 없어(71/74 문서의 환경 제약 그대로) 실행 못 함 — **원석이 Windows 로컬에서 직접 커밋해야 함.**

세션 시작 시점 `git status` 확인 결과, 워킹트리에 **이 세션이 만들지 않은 변경분도 이미 섞여 있었다**:

| 구분 | 파일 | 내용 |
|---|---|---|
| **이 세션 변경(진짜 코드 변경)** | 80 문서 §4의 신규 6개 + 수정 17개 | 실제 로직 변경. 커밋 대상. |
| **이 세션 이전부터 있던 변경(내용은 동일, 줄바꿈만 다름)** | `.github/ISSUE_TEMPLATE/*`, `README.md`, `gradlew.bat`, `passport-ai/cache/202312345.json`, `passport-ai/data/raw/courses_eval.csv`, `postman/PassPort.postman_collection.json`, `course/controller/CourseController.java`, `course/domain/Course.java`, `global/common/ApiResponse.java`, `global/error/ErrorCode.java`, `global/error/GlobalExceptionHandler.java`, `global/security/SecurityErrorResponseWriter.java` | `git diff -b -w`(공백/줄바꿈 무시)로 비교하면 **diff가 0줄** — 순수 CRLF↔LF 줄바꿈 차이만 있고 실제 내용 변경은 없음. 이 세션이 만든 게 아니다(이 파일들은 이번 대화에서 Read/Edit로 건드린 적이 없음). Windows(CRLF)와 리포 커밋본의 줄바꿈 설정 차이로 보임 — `core.autocrlf` 설정 확인 권장. |
| **미추적(untracked) 문서** | `docs/handoff_20260713_B안확정/54,55,60,61,70~74*.md`, `.Rhistory` | 직전 세션들이 만든 인계 문서 — 아직 한 번도 커밋 안 된 상태. 코드는 아니지만 팀 자산이니 같이 커밋 권장. `.Rhistory`는 R 콘솔이 실수로 만든 빈 파일로 보임(삭제해도 무방, 원석 판단). |

**권장 커밋 순서**(원석):
1. `git diff -b -w`로 위 "줄바꿈만 다른" 파일들이 정말 내용 변화 없는지 한 번 더 직접 확인.
2. 줄바꿈만 다르면 `git checkout -- <그 파일들>`로 되돌리거나(불필요한 diff 노이즈 제거), 아니면 이번 기회에 `.gitattributes`로 line ending 정책을 정해서 함께 커밋(팀 판단).
3. 실제 코드 변경분(80 문서 §4)을 `git add`, 예: `feat: 신영 UI정합 5건 + persona DB 상시저장 + STEP E 검증수정` 커밋 메시지로(CLAUDE.md 컨벤션: `feat:`/`fix:` 접두어, 작은 단위로 — 원하면 STEP E 수정/UI정합5건/persona 3개 커밋으로 쪼개도 됨).
4. `docs/handoff_20260713_B안확정/` 미추적 문서들(54,55,60,61,70~74,80,81)도 `docs: 세션 인계 문서 추가` 등으로 커밋.
5. `main` 직접 push 금지 원칙(CLAUDE.md §12) — `feat/`, `fix/` 브랜치로 나눠 PR 하거나, 이미 seed 예외를 썼다면(74 문서 §3) 팀 협의.

## 2. 검증 방법 — 아직 아무것도 실행/컴파일 안 해봄

코워크 샌드박스엔 **Java 11만 있고 프로젝트는 Java 21 필요**(`build.gradle` toolchain) — 이번 세션에서 만든 코드는 **한 번도 컴파일해보지 못했다.** 로직은 기존 컨벤션과 꼼꼼히 대조했지만, Windows 로컬에서 아래를 반드시 실행해 확인할 것.

```powershell
$env:JWT_SECRET="dev-secret-32bytes-이상-000"; $env:JWT_EXPIRATION="3600000"; .\gradlew bootRun
```

**⚠️ 새 테이블 생성 확인**: `profiles` 테이블에 컬럼 7개 추가 + `profile_personas`/`profile_persona_strategies`/`profile_persona_summary` 3개 테이블 신규. `ddl-auto: update`라 자동 생성되지만, 기동 로그에서 `create table profile_personas` 등이 찍히는지 확인(H2 파일 DB라 기존 데이터 유지되는지도 같이 확인).

**확인 시나리오** (로그인 → profileId 확보는 74 문서 §6과 동일):
```powershell
# 1) 프로필 표시 필드 PATCH — 신규 필드 저장되는지
Invoke-RestMethod -Method Patch -Uri "http://localhost:8080/api/v1/profiles/$profileId" -Headers $h -ContentType "application/json" `
  -Body ([Text.Encoding]::UTF8.GetBytes((@{deptCode="BIGDATA_AI";studentId="202312345";admissionYear=2023;name="테스터";
    grade=2;currentSemester=1;enrollmentStatus="재학생";expectedGraduationYear=2027;
    doubleMajorType="해당 없음";additionalMajor=$null;advisorProfessor="김민준"}|ConvertTo-Json)))
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/profiles/$profileId" -Headers $h   # GET으로 재확인

# 2) 과목 등록 → persona 자동 생성 확인 (POST /courses 직후 DB에 profile_personas row 생기는지)
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/profiles/$profileId/courses" -Headers $h -ContentType "application/json" `
  -Body ([Text.Encoding]::UTF8.GetBytes((@{name="데이터베이스";credit=3;category="MAJOR_REQUIRED";grade="A+";year=2026;semester=1;retake=$false}|ConvertTo-Json)))

# 3) 홈(대시보드)에 persona 뜨는지 — AI 분석 버튼 안 눌러도 나와야 함
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/profiles/$profileId/dashboard" -Headers $h | ConvertTo-Json -Depth 6

# 4) gpa-trend에 categoryGpa·predicted 확인
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/profiles/$profileId/gpa-trend" -Headers $h | ConvertTo-Json -Depth 6

# 5) AI 추천 응답에도 persona가 DB 최신값으로 뜨는지(캐시 스냅샷보다 우선하는지)
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/profiles/$profileId/recommendations" -Headers $h | ConvertTo-Json -Depth 6
```

**로그로 확인할 것**: 과목 등록(2번) 시 `PersonaBridge` subprocess 로그(`persona 브릿지 성공: [persona] studentKey=...`)가 stderr로 찍히는지 — 안 찍히면 `passport-ai/persona.py` 경로·`python-command` 설정 확인.

## 3. 실행계획(PART 1) 대비 현재 위치

| PHASE/STEP | 내용 | 상태 |
|---|---|---|
| PHASE 0 / STEP 0 | 안전 push | ✅ 원석 완료(이 세션 *이전* 상태 기준 — STEP1/persona는 아직 미포함, §1 참고) |
| PHASE 1 / STEP 1 | UI 정합 백엔드 5건 | ✅ 코드 작성 완료, 🔴 **커밋 안 됨**, 🔴 **bootRun 검증 안 됨** |
| (추가) persona DB 상시저장 | 원석 신규 요청 | ✅ 코드 작성 완료, 🔴 커밋·검증 안 됨 |
| PHASE 2 / STEP 2 | 배포 대비 설정(CORS 외부화, application-prod.yml, PASSPORT_AI_CACHE_DIR 확인) | 🔴 미착수 |
| PHASE 2 / STEP 3 | Gemini 키 재발급·모델 확정·데모 캐시 재생성(persona 포함 최종 1회) | 🔴 미착수 — ⚠️ persona 스키마가 바뀌었으니(캐시 JSON에 `persona` 키 추가) 캐시 재생성은 **STEP1 커밋·머지 이후**로 미루는 게 안전 |
| PHASE 3~4 / STEP 4 | AWS EC2 생성·배포 | 🔴 미착수 |
| PHASE 5 | AI 추천 연계 검증(외부 curl) | 🔴 미착수 |
| PHASE 6 / STEP 5 | FE 연동·게이트 | 🔴 미착수 (FE는 별도 레포·인서 담당) |

**다음 액션 우선순위**:
1. 🔴 이 세션 변경분 커밋(§1) — 없으면 다음 세션이 처음부터 다시 봐야 함.
2. 🔴 bootRun + 위 curl 시나리오로 회귀 확인(§2).
3. STEP 2(배포 대비 설정) 진행.
4. STEP 3에서 캐시 재생성할 때 persona 필드 포함되는지 함께 확인.
5. 74 문서 §4의 기존 미결정 5건(STEP E AND/단독 판정, 실데이터 방향 2개 한계, 시연 계정, Gemini 키 재발급, 통계카드 3종 출처)은 **여전히 미해결** — 이번 세션에서 다루지 않았음.

## 4. 이번 세션에서 내가 판단해서 정한 것들 (근거 없으면 확인 요청)

아래는 스펙 문서에 명시적 지시가 없어 합리적으로 추정·결정한 부분. 팀 확인 필요하면 알려달라고 요청했던 지점들:

| # | 결정 | 근거/이유 |
|---|---|---|
| 1 | `coreLiberal.current` = `coreLiberalTargetCount()`(대상 표시 영역 수), `required` = 고정 5 | 과목↔영역 매핑 데이터 부재. "완료 여부"가 아니라 "유저가 신고한 대상 영역 수" 근사치 — 실제 이수 여부와 다를 수 있음. |
| 2 | GPA 예측(predicted) 계산식 = 직전 두 학기 변화폭을 그대로 연장(단순 선형 외삽), 4.5/0.0으로 클램프 | "단순 외삽"이라는 계획 문구 그대로 구현. 더 정교한 회귀 등은 안 씀(과설계 방지). |
| 3 | categoryGpa 4그룹 매핑(전공=필수+선택, 기초=MAJOR_BASIC, 교양=필수+선택, 기타=자유선택) | 스크린샷의 "전공/교양/기초/기타" 4칸과 Course.CourseCategory enum을 대조해 추정. **실제 라벨 문구·그룹핑이 신영 디자인 의도와 정확히 맞는지는 미확인.** |
| 4 | persona 트리거 범위 = 수강 이력(Course) CRUD만 | 원석이 "성적들을 바탕으로"라고 명시해서 Course만 연결. 인증(Certification)·졸업요건(UserGraduationRequirement) 변경 시엔 refresh 안 함 — 필요하면 추가 연결 가능. |
| 5 | `PersonaDto` 위치를 `recommendation.dto`에 그대로 둠 | 파일 삭제 도구가 없어 새 패키지로 옮기면 기존 파일이 orphan으로 남음 — 재사용 쪽을 택함. 아키텍처적으로 더 깔끔한 위치(예: `com.passport.persona.dto`)로 옮기고 싶다면 다음 세션에서 정리 가능. |
| 6 | persona 재계산 실패 시 최선노력(예외 흡수, 로그만) | 수강 이력 저장이라는 "핵심 기능"이 성향 분석이라는 "부가 기능" 실패로 막히면 안 된다고 판단(원칙 5: 완성도 우선). |
| 7 | Dashboard의 persona는 DB 값만, RecommendationResponse의 persona는 DB 우선 + 캐시 폴백 | Dashboard는 "항상 최신"이 중요, Recommendation은 이미 캐시/폴백 체계가 있어 거기에 자연스럽게 얹음. |

## 5. 새 세션 시작 시 첨부할 것

- **`docs/handoff_20260713_B안확정/` 폴더 전체**(70~74 + 80~81 + mocks/) — 이 두 문서(80/81)가 최신.
- **연결 레포 폴더 전체**(`LikeLion_MiniProject_Team_3`) 또는 커밋된 GitHub `main`(§1 커밋 이후).
- 핵심 함께 볼 것: `71`(계약·규칙 SSOT), `80`(이번 세션 변경사항), `81`(이 문서 — 커밋상태·검증법), `PassPort_UI정합_AWS배포_전체실행계획_20260714.md`(원본 실행계획, STEP 2부터 이어감).
- ⚠️ **커밋 전이라면 반드시 "아직 push 안 된 상태"라고 새 세션에 알려줄 것** — 안 그러면 GitHub main만 보고 이 세션 작업이 없는 걸로 오해함.
