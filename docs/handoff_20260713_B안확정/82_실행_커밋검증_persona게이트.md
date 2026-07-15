# 82. 실행 가이드 — 커밋 · bootRun 검증 · persona 게이트 (2026-07-15)

> 짝 문서 80(무엇을 했나)·81(다음에 뭘 하나)에 이어, **원석이 Windows에서 지금 바로 실행할 것**을 정리.
> 이 세션에서 코드를 정적 리뷰 + persona.py 실제 실행으로 검증했고(아래 §0), 실제 gradle 컴파일은
> 샌드박스 환경 제약(Java 11 + 외부 네트워크 차단)으로 못 했다 — **최종 컴파일·회귀는 §3을 Windows에서 실행해 확인.**

---

## 0. 이 세션 검증 결과 (요약)

| 항목 | 결과 |
|---|---|
| persona 패키지 Java 정합성 | ✅ 설정 키(`passport-ai.cache-dir/python-command/dir`) 존재, `PersonaDto` record shape 일치, `DashboardResponse`·`RecommendationResponse` 재구성 인자 순서 정확(persona가 각 record 마지막 필드), `CourseService.create/update/delete` 3곳 트리거 연결, `CourseRepository.findAllByProfileId`·`Grade.getLabel()` 존재 — **컴파일 걸릴 소지 안 보임** |
| persona.py 런타임 | ✅ 샌드박스에서 직접 실행 → exit 0, stdout JSON이 `PersonaDto`와 정확히 일치, 로그는 stderr 분리. 4개 패턴 템플릿(`indiv_strong→안정 성장형`·`collab_strong→협업 활동형`·`balanced→균형 성장형`·`none→탐색형`) 전부 `type/label/description/strategies[3]/summary[3]` 완비 |
| 실제 gradle 컴파일 | 🔴 미실행 — 환경 제약. §3을 Windows(Java 21)에서 실행 |
| git 커밋·push | 🔴 미실행 — §2 |

---

## 1. ⚠️ 발견 1 정정 — 데모 persona는 정상 플로우에선 뜬다

81 문서/이 세션 초기엔 "캐시에 persona 키가 없어 데모 화면에 persona가 안 뜰 수 있다"고 봤으나, **data.sql 확인 결과 정정한다**:

- `data.sql`은 **유저 2명(demo1/demo2)만 시드**한다. 프로필·수강 이력은 시드하지 않는다. `studentId 202312345`는 코드 어디에도 없고 **추천 캐시 파일명일 뿐**이다.
- 따라서 게이트 시연은 **로그인 → 프로필 생성(studentId=202312345) → 과목 입력(API로) → 진단 → 추천** 순서다.
- 과목을 **API(`POST /courses`)로 입력하면** `CourseService`가 `personaService.refresh()`를 호출 → **DB(`profile_personas`)에 persona 저장** → 홈·AI분석 화면이 그 DB 값을 서빙한다. 즉 **정상 시연 경로에선 persona가 뜬다.**
- 캐시(`202312345.json`)에 persona 키가 없는 것은, "프로필은 있는데 과목을 API CRUD로 한 번도 안 넣은" **폴백 상황에서만** 문제다 — 정상 시연은 여기 해당 안 됨.

**게이트 체크포인트**: 데모 과목은 반드시 화면/`POST /courses`로 입력할 것(SQL 직삽 X). 캐시의 persona는 STEP 3(캐시 재생성) 때 채워지므로, 그 전까지 캐시 폴백 persona는 기대하지 말 것.

---

## 2. 커밋 순서 (원석, Windows) — #1 비가역 리스크 해소

> 코워크는 git 쓰기 권한이 없어 실행 못 함. 아래는 원석이 직접.

```powershell
cd C:\Users\송원석\Documents\LikeLion_MiniProject_Team_3

# (1) 줄바꿈만 다른 파일 확인 — 내용 변화 없으면 diff가 0줄
git diff -b -w --stat

# (2) 줄바꿈만 다른 파일은 되돌려 노이즈 제거(내용 변화 없다고 확인됐을 때만)
#     .github/*, README.md, gradlew.bat, passport-ai/cache/202312345.json,
#     passport-ai/data/raw/courses_eval.csv, postman/*, course/controller/CourseController.java,
#     course/domain/Course.java, global/common/ApiResponse.java, global/error/*, global/security/*
#   git checkout -- <위 파일들>      # 또는 .gitattributes로 line-ending 정책을 잡아 함께 커밋(팀 판단)

# (3) 실제 코드 변경분 커밋 (원하면 3개로 쪼개도 됨)
git add src/main/java/com/passport/profile src/main/java/com/passport/gpa `
        src/main/java/com/passport/dashboard src/main/java/com/passport/recommendation `
        src/main/java/com/passport/requirement src/test/java/com/passport/diagnosis `
        src/main/java/com/passport/persona src/main/java/com/passport/course/service/CourseService.java `
        passport-ai/persona.py passport-ai/core
git commit -m "feat: 신영 UI정합 5건 + persona DB 상시저장 + STEP E 검증수정"

# (4) 인계 문서 커밋
git add "docs/handoff_20260713_B안확정"
git commit -m "docs: 세션 인계 문서 80~82 추가"

# (5) main 직접 push 금지(CLAUDE.md §12) — feat/ 브랜치로 PR 권장
git switch -c feat/ui-align-persona
git push -u origin feat/ui-align-persona
```

> `.env`·`*.mv.db`가 스테이징에 없는지 `git status`로 재확인 후 push.

---

## 3. bootRun + 검증 스크립트 (PowerShell) — 컴파일 + 회귀 확인

### 3-1. 기동 + 새 테이블 생성 확인

```powershell
$env:JWT_SECRET="dev-secret-32bytes-이상-0000000000"; $env:JWT_EXPIRATION="3600000"
.\gradlew bootRun
```

기동 로그에서 아래 DDL이 찍히는지 확인(ddl-auto: update):

- `alter table profiles add column ...` (grade, current_semester, enrollment_status, expected_graduation_year, double_major_type, additional_major, advisor_professor — 7개)
- `create table profile_personas ...`
- `create table profile_persona_strategies ...`
- `create table profile_persona_summary ...`

### 3-2. 시나리오 (다른 터미널)

```powershell
$base = "http://localhost:8080/api/v1"

# 1) 로그인 → 토큰 (응답은 ApiResponse 래퍼: .data.accessToken)
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body ([Text.Encoding]::UTF8.GetBytes((@{email="demo1@passport.ac.kr";password="Passport1!"}|ConvertTo-Json)))
$token = $login.data.accessToken
$h = @{ Authorization = "Bearer $token" }

# 2) 내 정보 — profileId 유무로 온보딩 분기
Invoke-RestMethod -Uri "$base/auth/me" -Headers $h | ConvertTo-Json

# 3) 프로필 생성 (studentId=202312345 로 맞춰야 추천 캐시가 hit)
$prof = Invoke-RestMethod -Method Post -Uri "$base/profiles" -Headers $h -ContentType "application/json" `
  -Body ([Text.Encoding]::UTF8.GetBytes((@{deptCode="BIGDATA_AI";studentId="202312345";admissionYear=2023;name="박서은"}|ConvertTo-Json)))
$pid = $prof.data.id       # (응답 필드명이 다르면 $prof.data | ConvertTo-Json 로 확인)

# 4) 프로필 표시 필드 PATCH — 신규 필드 저장/직렬화 확인
Invoke-RestMethod -Method Patch -Uri "$base/profiles/$pid" -Headers $h -ContentType "application/json" `
  -Body ([Text.Encoding]::UTF8.GetBytes((@{deptCode="BIGDATA_AI";studentId="202312345";admissionYear=2023;name="박서은";
    grade=2;currentSemester=1;enrollmentStatus="재학생";expectedGraduationYear=2029;
    doubleMajorType="해당 없음";additionalMajor=$null;advisorProfessor="김민준"}|ConvertTo-Json)))
Invoke-RestMethod -Uri "$base/profiles/$pid" -Headers $h | ConvertTo-Json   # enrollmentStatus="재학생" 등 한글 라벨로 나오는지

# 5) 과목 등록 → persona 자동 생성 (핵심)
Invoke-RestMethod -Method Post -Uri "$base/profiles/$pid/courses" -Headers $h -ContentType "application/json" `
  -Body ([Text.Encoding]::UTF8.GetBytes((@{name="데이터베이스";credit=3;category="MAJOR_REQUIRED";grade="A+";year=2026;semester=1;retake=$false}|ConvertTo-Json)))
#  → bootRun 로그(stderr)에 "persona 브릿지 성공: [persona] studentKey=202312345 ..." 가 찍히는지 확인.
#    안 찍히면: passport-ai/persona.py 경로, passport-ai.python-command(=python) 설정, python에서 core 임포트 확인.

# 6) 홈 대시보드 — AI 버튼 안 눌러도 persona 떠야 함
(Invoke-RestMethod -Uri "$base/profiles/$pid/dashboard" -Headers $h).data.persona | ConvertTo-Json -Depth 5
#  → {type,label,description,strategies[3],summary[3]} 나오면 성공

# 7) gpa-trend — categoryGpa + predicted 확인
(Invoke-RestMethod -Uri "$base/profiles/$pid/gpa-trend" -Headers $h).data | ConvertTo-Json -Depth 6
#  → categoryGpa 배열, semesters 안에 predicted=true 항목 1개

# 8) AI 추천 — persona가 DB 최신값으로 뜨는지 (캐시 스냅샷보다 우선)
(Invoke-RestMethod -Method Post -Uri "$base/profiles/$pid/recommendations" -Headers $h).data.persona | ConvertTo-Json -Depth 5
```

### 3-3. 합격 기준 (DoD)

- 3-1의 4개 DDL 로그가 모두 찍힌다.
- 4)에서 `enrollmentStatus`/`doubleMajorType`가 **한글 라벨**("재학생"/"해당 없음")로 직렬화된다.
- 5) 직후 로그에 persona 브릿지 성공, `profile_personas`에 row 1건.
- 6) 대시보드에 persona 블록이 뜬다.
- 7) `categoryGpa`(전공/기초/교양/기타)와 `predicted=true` 예측 포인트가 있다.
- 8) 추천 응답의 persona가 6)의 DB 값과 동일하다.

---

## 4. 발견 2 — persona 재계산 트랜잭션 격리 (게이트 후 처리 권장)

**현상**: `CourseService.create/update/delete`(전부 `@Transactional`)가 같은 `@Transactional`인 `PersonaService.refresh`를 호출해 **같은 트랜잭션에 합류**한다.

- 의도는 "성향 분석이 실패해도 수강 저장은 롤백 안 함(best-effort)"인데, refresh 내부 DB write가 실패하면 트랜잭션이 rollback-only로 마킹돼 `catch`로 삼켜도 바깥 커밋이 깨져 **수강 저장까지 롤백**될 수 있다.
- 또 최대 20초 subprocess(persona.py)가 DB 트랜잭션 안에서 돌아 커넥션을 오래 잡는다.

**게이트엔 사실상 무해**(persona.py는 정상 동작 확인됨, 대상 테이블도 단순). 그래서 **게이트 이후**에 손대길 권장.

**⚠️ 단순 `@Transactional(REQUIRES_NEW)` 적용은 주의**: refresh에 넘기는 `Profile`이 바깥 트랜잭션의 관리 엔티티라, 새 트랜잭션 경계에서 detached가 되어 lazy 접근/저장에서 문제날 수 있다. 안전한 방향은 둘 중 하나 —
1. refresh를 **커밋 이후**(`TransactionSynchronization.afterCommit` 또는 `@TransactionalEventListener(AFTER_COMMIT)`)에 `profileId`만 넘겨 별도 트랜잭션으로 실행, 또는
2. refresh를 `REQUIRES_NEW`로 하되 **`profileId`를 받아 새 트랜잭션 안에서 `profileRepository.findById`로 재조회**해 사용.

어느 쪽이든 Windows에서 bootRun+§3-2 5)~6) 재확인 필수.

---

> 우선순위: **§2 커밋(오늘 즉시) → §3 bootRun 검증 → (통과 시) 실행계획 STEP 2** 진행. §4는 게이트 후.
