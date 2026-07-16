# PassPort 백엔드 워크플로우

> 코드 정독 기준으로 정리한 백엔드 동작 문서 (2026-07-16).
> 실제 소스(`src/main/java/com/passport/**`, `passport-ai/**`)에서 확인한 내용만 담았다. 값이 코드와 어긋나면 코드가 우선.

---

## 1. 개요

수강 이력을 입력하면 졸업 요건 달성도를 **규칙 기반으로 진단**하고, 부족 요건을 바탕으로 다음 학기 과목을 **AI가 추천**하는 학사 대시보드. 대상 학과는 빅데이터 인공지능 전공 1개 하드코딩.

**핵심 경계 원칙**: 사실 판정(졸업 가능 여부·학점·GPA·요건 충족)은 전부 Java 규칙 코드가 결정론적으로 계산한다. AI(Gemini)는 ① 추천 과목 선정 ② 추천 이유 설명에만 관여한다. 학습 성향(persona) 분류조차 AI가 아니라 규칙 템플릿이다.

**기술 스택**: Java 21 · Spring Boot · Spring Security + JWT(액세스 토큰) · BCrypt · JPA(H2 파일 DB, `ddl-auto: update`) · AI 연동은 Python 서브프로세스(`passport-ai`) 브릿지.

---

## 2. 요청 처리 파이프라인 (공통)

모든 API는 아래 공통 경로를 지난다.

```
클라이언트
  │  Authorization: Bearer <JWT>
  ▼
CORS (CorsConfig, /api/v1/** — localhost:5173,5174 허용)
  ▼
JwtAuthenticationFilter  ── 토큰 검증(JwtTokenProvider) →
  │                          성공 시 AuthUser(id,email)를 SecurityContext에 주입
  ▼
SecurityConfig 인가 규칙
  · OPTIONS /**            → 무조건 허용(프리플라이트)
  · /auth/signup, /auth/login → permitAll
  · /h2-console/**         → permitAll
  · 그 외 전부             → 인증 필수
  ▼
Controller  ── @AuthenticationPrincipal AuthUser 로 로그인 유저 수신, 얇게 유지
  ▼
Service     ── 비즈니스 로직 + 소유권 검증(본인 리소스만)
  ▼
Repository (JPA)
  ▼
ApiResponse<T> { success, data, message } 래퍼로 응답
```

**인증 방식**: 세션 없는 STATELESS. 토큰은 HMAC 서명(`jwt.secret`), 만료 `jwt.expiration`. 필터는 유효 토큰이면 `AuthUser`를 SecurityContext에 넣고, 없거나 무효면 그냥 통과시킨 뒤 인가 단계에서 차단(진입점: `JwtAuthenticationEntryPoint` 401 / 권한거부: `JwtAccessDeniedHandler` 403).

**예외 처리**: 서비스는 `BusinessException(ErrorCode)`를 던지고 `GlobalExceptionHandler`(@RestControllerAdvice)가 일관된 `ApiResponse.error` 포맷으로 변환. 주요 `ErrorCode`: `EMAIL_DUPLICATED`, `INVALID_CREDENTIALS`, `USER_NOT_FOUND`, `PROFILE_NOT_FOUND`, `PROFILE_ALREADY_EXISTS`, `PROFILE_ACCESS_DENIED`, `COURSE_NOT_FOUND`, `REQUIREMENT_NOT_FOUND`, `INVALID_INPUT_VALUE`.

**소유권 검증**: `/profiles/{id}/**` 계열은 `ProfileService.findOwnedProfile(profileId, userId)`를 거쳐 토큰의 User가 해당 Profile 소유자인지 확인한다. 타인 리소스 접근은 `PROFILE_ACCESS_DENIED`.

---

## 3. 엔드포인트 지도

Base URL `/api/v1`. 비고의 **규칙**=AI 미개입 결정론 계산, **AI**=Gemini 경유.

| 도메인 | 메서드·경로 | 권한 | 종류 |
|---|---|---|---|
| Auth | `POST /auth/signup` · `POST /auth/login` | 비회원 | — |
| Auth | `POST /auth/logout` · `GET /auth/me` | 회원 | — |
| Profile | `POST /profiles` · `GET /profiles/{id}` · `PATCH /profiles/{id}` | 회원 | — |
| Course | `POST·GET /profiles/{id}/courses` · `PUT·DELETE …/courses/{cid}` | 회원 | — |
| Certification | `GET·PUT /profiles/{id}/certifications` | 회원 | — |
| Diagnosis | `GET /profiles/{id}/diagnosis` | 회원 | 규칙 |
| GPA Trend | `GET /profiles/{id}/gpa-trend` | 회원 | 규칙 |
| Recommendation | `POST·GET /profiles/{id}/recommendations` | 회원 | AI |
| Requirement(기준) | `GET /requirements/{deptCode}` | 회원 | 하드코딩 |
| Requirement(유저) | `GET·PUT /profiles/{id}/requirements` | 회원 | 규칙 |
| Dashboard | `GET /profiles/{id}/dashboard` | 회원 | 집계 |

> `/profiles/{id}/requirements`(유저 졸업요건 입력)는 초기 명세 19개에는 없던, 졸업요건 입력 화면(STEP D)용 추가 엔드포인트다.

---

## 4. 도메인별 워크플로우

### 4.1 인증 (auth)

- **회원가입** `POST /auth/signup`: 이메일 중복 검사(`EMAIL_DUPLICATED`) → 비밀번호 BCrypt 해시 → User 저장.
- **로그인** `POST /auth/login`: 이메일 조회 → BCrypt 매칭 실패 시 `INVALID_CREDENTIALS` → 성공 시 JWT 발급. 응답 `LoginResponse{accessToken, tokenType:"Bearer", userId, nickname}`.
- **내 정보** `GET /auth/me`: `MeResponse{userId, email, nickname, profileId}`. **`profileId`가 null이면 아직 프로필 미생성 → FE가 온보딩으로 분기**, 있으면 대시보드로.
- **로그아웃** `POST /auth/logout`: MVP는 클라이언트 토큰 폐기 수준(서버 블랙리스트 없음).

### 4.2 프로필 (profile)

User와 **1:1**. `POST /profiles`는 이미 프로필이 있으면 `PROFILE_ALREADY_EXISTS`. 생성 시 받는 필드는 `deptCode·studentId·admissionYear·name` 4개.

`PATCH /profiles/{id}`는 마이페이지 표시 전용 필드 7개를 추가로 갱신한다(전부 nullable, 진단 로직 미연결): `grade`, `currentSemester`, `enrollmentStatus`, `expectedGraduationYear`, `doubleMajorType`, `additionalMajor`, `advisorProfessor`. 두 enum은 JSON에서 **한글 라벨**로 직렬화(@JsonValue)된다.

| enum | JSON 값 |
|---|---|
| `EnrollmentStatus` | `"재학생"` / `"휴학생"` / `"졸업 예정"` |
| `DoubleMajorType` | `"해당 없음"` / `"복수전공"` / `"융복합전공"` |

### 4.3 수강 이력 (course) — persona 트리거 지점

`Course{name, credit(double), category, grade, year, semester, retake}`, Profile와 N:1.

- **학점 검증**: 0 이상 30 이하 **0.5 단위**만 허용(0.5학점 과목 대응). 위반 시 `INVALID_INPUT_VALUE`.
- **CRUD 소유권**: 모든 작업이 `findOwnedProfile`(+수정/삭제는 `findByIdAndProfileId`) 검증.
- **★ persona 트리거**: `create·update·delete` 3곳 모두 DB 반영 직후 `personaService.refresh(profile)`를 호출한다. 즉 **성적이 바뀔 때마다 학습 성향이 규칙 기반으로 재계산·저장**된다(4.9 참고).

### 4.4 졸업 인증 (certification)

어학(LANGUAGE)·봉사(VOLUNTEER)·논문(THESIS) 3종의 상태(PASS/FAIL/NOT_SUBMITTED)를 조회/일괄 갱신. 계정 인증과 무관한 "졸업 요건 체크" 데이터. 진단에서 요건별 충족 판정에 쓰인다.

### 4.5 졸업 요건 (requirement) — 규칙 판정의 기준값

두 소스를 하나로 합쳐 `EffectiveRequirement`로 해석한다.

```
RequirementResolutionService.resolve(profile)
  ├─ 유저가 요건을 입력했으면(UserGraduationRequirement 존재)
  │     → EffectiveRequirement.fromUser(user, 하드코딩폴백)
  │       · 학점 기준·핵심교양 대상 수·졸업시험 유형·인증 5분야는 유저 입력값
  │       · 인증 필수여부·최소 GPA는 하드코딩 폴백값
  └─ 없으면 → EffectiveRequirement.fromHardcoded(BigdataAiRequirement)
```

**하드코딩 기준(BigdataAiRequirement)**: 총 130학점 / 전공필수 21 / 전공선택 39 / 교양필수 14 / 교양선택 21 / 자유선택 35 / 최소 GPA 2.0.

`GET /requirements/{deptCode}`는 이 하드코딩 기준을, `GET·PUT /profiles/{id}/requirements`는 유저 입력 요건을 다룬다.

### 4.6 졸업 진단 (diagnosis) — 규칙, AI 미개입

`GET /profiles/{id}/diagnosis`. `EffectiveRequirement` + 수강 이력 + 인증 상태를 비교해 5개 축을 계산하고, 전부 AND로 최종 졸업 가능 여부를 낸다.

| 축 | 계산 |
|---|---|
| 총 이수학점 | 이수 인정(`isCreditEarned`) 학점 합 vs 총 요건 학점 |
| 이수구분별 | 6개 `CourseCategory`별 학점 합 vs 각 요건 |
| 인증 3종 | 필수 여부 × PASS 여부 |
| GPA | `Σ(학점×환산)/Σ(GPA대상 학점)` (P/NP 제외) vs 최소 GPA |
| 졸업인증 5분야 | 유저가 '대상(TARGET)'으로 표시한 분야가 전부 완료여야 충족(미입력 시 null=영향 없음) |

```
eligible = 학점OK && 카테고리OK && 인증OK && GPA충족 && 졸업인증5분야OK
```

GPA 계산은 `GpaCalculator`(course 도메인)에 있고 진단·GPA트렌드가 공유한다. GPA 대상 과목이 없으면 GPA는 null(0으로 나누기 방지).

### 4.7 성적 트렌드 (gpa) — 규칙

`GET /profiles/{id}/gpa-trend`. 세 가지를 반환한다.

- **학기별 GPA**(`semesters`): (year, semester)로 묶어 학기별 GPA·이수학점. 시간순 정렬.
- **다음 학기 예측**(`predicted=true` 1건): 실제 GPA가 있는 학기 기준 단순 선형 외삽 — 2학기 이상이면 최근 변화폭을 그대로 연장, 1학기면 그 값 유지. 0.0~4.5로 클램프. 데이터 없으면 예측 안 함.
- **영역별 GPA**(`categoryGpa`): 전공(필수+선택)·기초(MAJOR_BASIC)·교양(필수+선택)·기타(자유선택) 4그룹 평균. 그룹에 과목 없으면 null.

### 4.8 AI 과목 추천 (recommendation) — AI, 지문 기반 캐싱

`RecommendationService`가 유저 데이터 **지문(fingerprint)**으로 재생성 여부를 판단한다.

- **`GET /recommendations`** (조회 전용): AI를 부르지 않고 `cache/{studentId}.json`을 서빙. 캐시 없으면 규칙 폴백.
- **`POST /recommendations`** (AI 분석 버튼):
  1. 현재 데이터(수강·요건·인증·프로필) 지문을 계산해 저장된 지문과 비교.
  2. **변화 없음** → 캐시 그대로(AI 미호출, 같은 결과 유지).
  3. **변화 있음/첫 생성** → `PassportAiBridge.generate()`로 `bridge.py` 라이브 실행 → 5방향 전체 재생성, 결과를 캐시에 저장. `source:"live"`일 때만 지문 갱신(AI 일시 실패 시 다음 클릭에서 자동 재시도).
  4. 브릿지 실패 시 안전망: 기존 캐시 → 규칙 폴백(발표 사고 방지).

응답 `RecommendationResponse{directions, recommendations, defaultDirectionId, source, generatedAt, persona}`. `source`는 `live | cache | fallback`. **persona는 항상 `withFreshPersona()`로 DB 최신값을 우선 덮어쓴다** — 캐시에 박제된 스냅샷보다 최신 성적 반영값이 우선.

### 4.9 학습 성향 (persona) — 규칙, 상시 DB 저장

원석 추가 요청으로 만든, "성적이 바뀔 때마다 규칙으로 재분석해 DB에 저장하고 홈·AI분석 화면에 상시 노출"하는 경로.

```
CourseService.create/update/delete
   └─ personaService.refresh(profile)          [최선노력 best-effort]
        └─ PersonaBridge.analyze(payload)  ── ProcessBuilder → persona.py (Gemini 없음, 20s 타임아웃)
             └─ 규칙 4패턴 분류 → {type,label,description,strategies[3],summary[3]}
        └─ ProfilePersona(1:1) upsert  →  profile_personas 테이블
```

- 분류는 100% 규칙(Gemini 미호출)이라 성적 변경마다 가볍게 재계산해도 부담 없음. AI 추천(무거운 5~6초 경로)과 완전 분리.
- **best-effort**: 재분석 실패해도 예외를 던지지 않고 로그만 — 수강 저장이 성향 분석 실패로 막히지 않게.
- **조회**: `personaService.get(profileId)`는 DB 저장값 우선, 없으면 추천 캐시(`cache/{studentId}.json`)의 persona 스냅샷으로 폴백, 둘 다 없으면 empty(FE 미표시).
- **노출**: 홈 대시보드(`DashboardResponse.persona`)와 AI 추천 응답(`RecommendationResponse.persona`)이 공유. 4패턴 = `안정 성장형(indiv_strong)` / `협업 활동형(collab_strong)` / `균형 성장형(balanced)` / `탐색형(none)`.

### 4.10 홈 대시보드 (dashboard) — 집계

`GET /profiles/{id}/dashboard`. 진단·GPA트렌드·요건·persona를 한 번에 모아 홈 화면용으로 집계.

- **전체 이수율**(`overallProgress`): 이수학점 ÷ 총 요건 학점 %(0~100 클램프).
- **요건별 통합 카테고리**(`categories`): 총이수/전공/교양/핵심교양/필수과목/졸업인증/졸업시험을 상태(진행중/완료/미확인)와 함께.
- 최신 학기 요약(신청·이수 학점·학기 GPA·과목 목록), `gpaTrend`(예측 포인트 포함), `persona`(DB 값).

> 한계(코드 주석 명시): 과목↔핵심교양 영역 매핑 데이터가 없어 핵심교양 `current`는 "완료 영역 수"가 아니라 "유저가 대상으로 신고한 영역 수" 근사치다.

---

## 5. AI 연동 아키텍처 (Spring ↔ passport-ai)

Spring은 Gemini를 직접 부르지 않는다. **Python 서브프로세스**를 stdin/stdout(JSON, UTF-8)으로 호출하는 브릿지 2개를 쓴다.

| 브릿지(Java) | 진입점(Python) | Gemini | 타임아웃 | 용도 |
|---|---|---|---|---|
| `PassportAiBridge` | `bridge.py` | 사용 | 60s | 5방향 과목 추천 생성(라이브). 결과를 `cache/{studentKey}.json`에 저장 |
| `PersonaBridge` | `persona.py` | 미사용 | 20s | 학습 성향 규칙 분류만. 성적 변경마다 호출 |

- **payload 계약**: `{studentKey, history:[{courseName, grade}], (bridge는 diagnosis 추가)}`. Spring DB엔 과목코드가 없어 Python이 `data/courses.json` 이름 색인으로 courseCode를 채운다. 카탈로그에 없는 과목명은 성향분석에서만 빠짐(에러 아님).
- **사실 계산 분리**: 부족학점·GPA 같은 사실값은 Spring 진단 결과(`shortCredits`, `gpa`)를 그대로 넘기고 AI는 그 위에서 과목 선정·이유 설명만 한다.
- **recommend() 내부**: persona(규칙 템플릿) 계산 → Gemini 생성 → 응답 검증(`validate_directions`) → 유효 방향 수가 기준 이상이면 `source:live`, 아니면 규칙 폴백(`rule_directions`) → 항상 캐시에 저장.
- **키 관리**: `passport-ai/.env`(`GEMINI_API_KEY`, `GEMINI_MODEL`). git 미포함, Spring/Java는 키 미접촉.

---

## 6. 데이터 모델 (관계)

```
User (1) ──1:1── (1) Profile ──1:N── Course
                      │  1:N       Certification
                      │  1:1       ProfilePersona        (profile_personas)
                      │  1:1       UserGraduationRequirement (유저 입력 요건)
                      └ deptCode → GraduationRequirement (하드코딩, DB 아님)
```

- persona는 `@ElementCollection` 2개(`profile_persona_strategies`, `profile_persona_summary`)를 `@OrderColumn`으로 순서 보존.
- `Grade` enum이 GPA 환산값과 이수/GPA 대상 여부(`isCreditEarned`, `isGpaEligible`)를 함께 보유. P/NP는 GPA 대상에서 제외.

---

## 7. 설정·인프라 요약

| 항목 | 값 |
|---|---|
| DB | H2 파일 모드 `jdbc:h2:file:./data/passportdb;AUTO_SERVER=TRUE`, `ddl-auto: update` |
| 시드 | `data.sql`(MERGE) — 데모 유저 2명(demo1/demo2)만. 프로필·수강은 시드 안 함 |
| 포트 | 8080, H2 콘솔 `/h2-console`(개발용, 배포 시 off 예정) |
| 환경변수 | `JWT_SECRET`, `JWT_EXPIRATION`, (선택) `PASSPORT_AI_CACHE_DIR/PYTHON/DIR` |
| CORS | 현재 `localhost:5173,5174` 하드코딩(배포 대비 외부화는 STEP 2 예정) |

---

## 8. 규칙 vs AI 경계 (한 눈에)

| 구분 | 담당 | 예 |
|---|---|---|
| **규칙(Java, 결정론)** | 사실 판정 전부 | 졸업 가능 여부, 학점·카테고리 충족, GPA, 인증 판정, GPA 추세·예측, **학습 성향 분류**, 대시보드 집계 |
| **AI(Gemini)** | 추천·설명만 | 다음 학기 과목 선정, 추천 이유 문장 |

> 이 경계는 프로젝트 1원칙이다. AI가 사실 판정에 개입하지 않고, persona조차 규칙 템플릿이라는 점이 핵심.
