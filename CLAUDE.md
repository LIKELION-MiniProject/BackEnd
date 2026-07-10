# PassPort — Claude Code 개발 명세 (CLAUDE.md)

> **용도** : Claude Code가 이 저장소에서 코드를 작성할 때 항상 참조하는 컨텍스트 파일.
> 이 문서는 **무엇을·어떤 원칙으로·어떤 순서로** 만들지를 규정한다. 여기 적힌 원칙과 계약을 어기는 코드는 작성하지 않는다.
> **갱신일** : 2026-06-30

---

## 0. 프로젝트 한 줄 요약

수강 이력을 입력하면 → **졸업 요건 달성도를 진단**하고 → **다음 학기 과목을 AI가 추천**해주는 개인화 학사 대시보드. 서비스명 = **PassPort**.

- 대상 학과 : **빅데이터 인공지능 전공** 1개만 하드코딩 (요건 수치화 용이)
- 플랫폼 : **Web 전용**
- 팀 : PM+BE(원석), BE(은종), FE 1인(인서), 디자인(신영)
- 마감 : 코드 제출 7/17 22:00 · 발표 7/20

---

## 1. 절대 원칙 (반드시 준수 — 위반 코드 금지)

1. **사실 판정은 규칙 기반 코드, AI는 파싱·추천·설명에만.**
   - 졸업 가능 여부 / 학점 계산 / GPA / 요건 충족 판정 → **전부 Java 코드로 결정론적 계산**. AI에게 절대 위임하지 않는다.
   - AI가 관여하는 지점은 오직 ① 수강 이력 텍스트 파싱 ② 추천 과목 + 추천 이유 설명. 그 외 없음.
2. **모든 AI 호출은 백엔드 경유 + 데모 결과 캐싱.**
   - 프런트에서 Gemini 직접 호출 금지. 발표용 결과는 사전 생성·캐싱하고 `429/503` 시 캐시로 폴백.
   - AI 콘텐츠는 조회 시마다가 아니라 **생성 시점에 1회** 만든다.
3. **키는 환경변수만.** API 키·JWT 서명 키를 `application.yml`이나 코드에 하드코딩하거나 커밋하지 않는다. `application.yml`은 `${ENV_VAR}` 참조만 사용.
4. **API 명세가 단일 기준(SSOT).** 엔드포인트/DTO 계약을 바꾸려면 명세부터 고친다. FE가 이 계약에 의존한다.
5. **완성도 우선, 스코프 확장 금지.** 기능을 넓히지 말고 적은 기능을 진짜 서비스 수준으로. 신규 기능은 7/10 게이트 이후 + PM 승인.

---

## 2. 기술 스택 (고정)

| 영역 | 선택 |
|---|---|
| 언어/프레임워크 | Java + Spring Boot |
| 인증 | Spring Security + JWT(액세스 토큰만) + BCrypt |
| AI 연동 | Spring `RestClient` → Google Gemini API |
| DB | (팀 확정값 사용 — 미확정 시 개발은 H2, 운영은 결정 후) |
| 빌드 | Gradle 가정 (팀 실제값 우선) |
| 버전관리 | GitHub · `main` + `feat/` 브랜치 · main 직접 push 금지 |

> ⚠️ **DB·빌드툴 등 이미 팀에서 정한 값이 저장소에 있으면 그것을 최우선한다.** 이 문서의 가정보다 실제 프로젝트 설정이 우선.

---

## 3. 패키지 구조 (도메인형)

```
com.passport
├─ PassportApplication.java
├─ global
│  ├─ config          # SecurityConfig, RestClientConfig, WebConfig
│  ├─ security        # JWT 필터·프로바이더·UserDetails
│  ├─ error           # 예외·핸들러(@RestControllerAdvice)·ErrorCode
│  └─ common          # 공통 응답 래퍼, 페이징 등
├─ auth               # 회원가입/로그인/로그아웃/내정보
│  ├─ controller  ├─ service  ├─ dto
├─ profile            # 프로필 CRUD (User와 1:1)
│  ├─ controller  ├─ service  ├─ domain(entity)  ├─ repository  ├─ dto
├─ course             # 수강 이력 CRUD + 파싱
├─ certification      # 졸업 인증 체크(어학·봉사·논문)
├─ diagnosis          # 졸업 진단 (규칙)
├─ gpa                # 성적 트렌드 (규칙)
├─ recommendation     # AI 추천
├─ requirement        # 요건 기준 (하드코딩)
├─ dashboard          # 홈 집계
└─ ai                 # Gemini 클라이언트·프롬프트 빌더·응답 검증 (recommendation/course가 사용)
```

원칙 : **도메인별 수직 분리.** 각 도메인은 `controller / service / domain / repository / dto`를 자기 패키지 안에 둔다.

---

## 4. 도메인 엔티티

> JPA 엔티티 기준 설계안. **팀 원본 `API_명세서.md`에 상세 필드가 있으면 그것과 대조해 확정할 것.**

### User (계정)

| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long PK | |
| email | String, unique, not null | 로그인 ID |
| password | String, not null | **BCrypt 해시 저장** |
| nickname | String, not null | 회원가입 시 입력 (팀 API_명세서 PASS-AUTH-001 확인) |
| createdAt | LocalDateTime | |

- User ↔ Profile = **1:1** (계정당 프로필 1개)

### Profile (학사 프로필)

| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long PK | |
| user | User (1:1, FK unique) | |
| deptCode | String | 요건 매칭 키 (예: 빅데이터 인공지능 전공 코드) |
| studentId | String | 학번 |
| admissionYear | int | 입학연도 (요건 판정에 영향 가능) |
| name | String | (선택) |

- Profile ↔ Course = 1:N
- Profile ↔ Certification = 1:N

### Course (수강 이력)

| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long PK | |
| profile | Profile (N:1) | |
| name | String | 과목명 |
| credit | int | 학점 |
| category | `CourseCategory` enum | 이수 구분 |
| grade | `Grade` enum | 성적 |
| year | int | 이수 연도 |
| semester | int | 학기 (1 또는 2) |

### Certification (졸업 인증 체크)

| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long PK | |
| profile | Profile (N:1) | |
| type | `CertificationType` enum | LANGUAGE/VOLUNTEER/THESIS |
| status | `CertificationStatus` enum | PASS/FAIL/NOT_SUBMITTED |

### Requirement (요건 기준 — 하드코딩)

- **DB 엔티티가 아니어도 됨.** 학과별 졸업 요건을 상수/설정으로 보유(예: `requirement/BigdataAiRequirement`).
- 보유 값(예시) : 총 이수학점, 전공필수·전공선택·교양필수·교양선택·자유선택 최소학점, 인증 요건(어학/봉사/논문 필수 여부), 최소 GPA 등.
- 실제 수치는 **팀이 확보·검증한 학과 요건 데이터로 채운다** (현재 데이터 확보 미완).

---

## 5. Enum 정의

```java
// 이수 구분
enum CourseCategory {
    MAJOR_REQUIRED, MAJOR_ELECTIVE,
    GE_REQUIRED, GE_ELECTIVE,      // 교양 필수 / 교양 선택
    GENERAL_ELECTIVE               // 자유 선택
}

// 성적 (GPA 환산값 포함). P/NP는 GPA 계산에서 제외
enum Grade {
    A_PLUS(4.5), A(4.0), B_PLUS(3.5), B(3.0),
    C_PLUS(2.5), C(2.0), D_PLUS(1.5), D(1.0), F(0.0),
    P(null), NP(null);   // GPA 계산 제외
    // 표기: "A+" 등은 JSON 직렬화 시 매핑 처리
}

// 인증 유형 / 상태
enum CertificationType   { LANGUAGE, VOLUNTEER, THESIS }
enum CertificationStatus { PASS, FAIL, NOT_SUBMITTED }
```

> GPA 계산 규칙 : `Σ(학점 × 성적환산) / Σ(GPA대상 학점)`. **P/NP 과목은 분자·분모 모두에서 제외.**

---

## 6. REST API 명세 (9 도메인 / 19 엔드포인트)

- **Base URL** : `/api/v1` · Content-Type : `application/json; charset=UTF-8`
- **인증** : `POST /auth/signup`, `POST /auth/login`만 비회원 허용. **그 외 전부 `Authorization: Bearer <JWT>` 필수.**
- **소유 검증** : `/profiles/{id}/**` 접근 시 토큰의 User가 해당 Profile 소유자인지 검증(타인 리소스 접근 차단).
- **API ID 접두어** : `PASS-`

| # | 도메인 | 메서드·경로 | 종류 |
|---|---|---|---|
| 1 | Auth | `POST /auth/signup` · `POST /auth/login` · `POST /auth/logout` · `GET /auth/me` | — |
| 2 | Profile | `POST /profiles` · `GET /profiles/{id}` · `PATCH /profiles/{id}` | — |
| 3 | Course | `POST /profiles/{id}/courses` · `GET /profiles/{id}/courses` · `PUT /profiles/{id}/courses/{cid}` · `DELETE /profiles/{id}/courses/{cid}` | — |
| 4 | Certification | `GET /profiles/{id}/certifications` · `PUT /profiles/{id}/certifications` | — |
| 5 | Diagnosis | `GET /profiles/{id}/diagnosis` | **규칙** |
| 6 | GPA Trend | `GET /profiles/{id}/gpa-trend` | **규칙** |
| 7 | Recommendation | `POST /profiles/{id}/recommendations` · `GET /profiles/{id}/recommendations` | **AI** |
| 8 | Requirement | `GET /requirements/{deptCode}` | 하드코딩 |
| 9 | Dashboard | `GET /profiles/{id}/dashboard` | 집계 |

### 응답 규칙

- 추천(`recommendations`) 응답에는 `cached` 필드 포함, `429/503` 시 캐시 폴백.
- 진단·GPA·대시보드는 **규칙 기반 계산 결과만** 반환(AI 개입 없음).
- 디자이너 IA의 "밸런스 조정 / 성향 큐레이션"은 추천 API 파라미터(`focus`, `preferredDays` 등)로 흡수.
- 상세 요청/응답 본문은 팀 원본 `API_명세서_v2.md`를 SSOT로 대조.

---

## 7. 인증 구현 방향 (Spring Security + JWT 최소 구성)

- 회원가입 : 이메일 중복 체크 → BCrypt 해시 저장.
- 로그인 : 이메일/비밀번호 검증 → **JWT 액세스 토큰 발급**(refresh 토큰은 MVP 범위 밖).
- 로그아웃 : MVP는 클라이언트 토큰 폐기 수준(서버 블랙리스트는 범위 밖, 필요 시만).
- `GET /auth/me` : 토큰의 User + 연결된 `profileId` 반환. **`profileId` 유무로 FE가 온보딩/대시보드 분기.**
- SecurityConfig : `signup`/`login`만 permitAll, 나머지 authenticated. JWT 필터를 `UsernamePasswordAuthenticationFilter` 앞에 등록.
- 서명 키·만료시간은 환경변수/`application.yml`(`${...}`)로.

---

## 8. Gemini 연동 방향 (W2 작업 — 지금은 구조만)

- 구조 : `RestClientConfig`(빈) → 요청/응답 DTO(Java `record`) → `GeminiClient` → 프롬프트 빌더(`CourseRecommendService` 등) → Controller.
- 호출 헤더 : `x-goog-api-key` = 환경변수.
- **AI 응답은 JSON 강제 프롬프트 + 스키마 검증 + 재시도/폴백**. 파싱 실패 시 캐시 또는 안전한 기본값.
- 사실 계산은 절대 AI에 맡기지 않는다(원칙 1 재확인).

### ⚠️ 모델명 — 확정 전 (config로 분리)

- 후보 : 기본 flash-lite 계열 / 폴백 flash 계열.
- 인수인계 문서엔 `gemini-3.1-flash-lite` / `gemini-3.5-flash`, 이전 기록엔 `gemini-2.5-flash-lite` / `gemini-2.5-flash`로 **불일치**.
- 정확한 모델 스트링은 **AI Studio에서 실제 사용 가능 모델을 확인 후 확정**한다. 코드에는 하드코딩하지 말고 `gemini.model.default` / `gemini.model.fallback` 설정값으로 주입.

---

## 9. 환경 변수 (`application.yml`에서 `${...}`로만 참조)

```
GEMINI_API_KEY        # Gemini API 키
GEMINI_MODEL_DEFAULT  # 기본 모델명 (확정 후)
GEMINI_MODEL_FALLBACK # 폴백 모델명 (확정 후)
JWT_SECRET            # JWT 서명 키
JWT_EXPIRATION        # 토큰 만료(ms)
DB_URL / DB_USERNAME / DB_PASSWORD  # DB 확정 시
```

> `.gitignore`에 `application-local.yml`, `.env` 등 시크릿 파일 포함 확인. 시크릿은 절대 커밋 금지.

---

## 10. 구현 순서 (W1 = 로그인→수강입력 수직 슬라이스 관통)

의존성 순서대로 진행. **FE 언블록을 위해 각 도메인은 API 계약 확정 → mock 응답이라도 먼저 열어준다.**

1. **스캐폴딩 + 엔티티** — 프로젝트 셋업, `global` 공통(에러 핸들러·응답 래퍼), `User`/`Profile`/`Course`/`Certification` 엔티티·리포지토리, DB 스키마.
2. **인증** — signup/login/logout/me, SecurityConfig, JWT 필터, BCrypt.
3. **프로필** — `POST /profiles`, `GET/PATCH /profiles/{id}` + 소유 검증.
4. **수강 도메인** — courses CRUD. (여기까지가 W1 목표 = 화면 관통)
5. (W2) 진단·GPA 규칙 로직 — 가장 복잡, **단위 테스트 동반**.
6. (W2) Gemini 연동 → AI 추천.
7. (W2~3) 대시보드 집계, 캐싱·폴백, 데모 시딩.

각 단계 완료 기준 = **컨트롤러가 실제 응답을 반환하고 최소 테스트가 통과.**

---

## 11. 범위 밖 / 하지 말 것

- ❌ 다학과 지원 (한 학과만 하드코딩)
- ❌ refresh 토큰 / 소셜 로그인 / 관리자 기능
- ❌ 챗봇, 발표 중 실시간 AI 호출
- ❌ FE에서 Gemini 직접 호출
- ❌ AI에게 졸업 판정·학점 계산 위임
- ❌ 시크릿 커밋, `application.yml`에 키 하드코딩
- ❌ 명세 없이 API 계약 임의 변경

---

## 12. 코드 컨벤션

- 커밋 : `feat:` / `fix:` 접두어, 작은 단위로 자주.
- 브랜치 : `main` + `feat/<도메인>`, **main 직접 push 금지**(팀 Sourcetree/GUI 사용).
- DTO는 Java `record` 우선. 엔티티를 컨트롤러에 직접 노출하지 않는다(요청/응답 DTO 분리).
- 예외는 `global/error`의 공통 핸들러로 일관 처리(에러 코드 + 메시지 포맷 통일).
- 비즈니스 로직은 Service에, Controller는 얇게.
- 모든 사용자 대면 메시지·주석은 한국어 허용, 식별자(클래스·필드)는 영어.

---

> 이 문서는 Claude Code의 상시 참조용 명세다. 저장소에 이미 존재하는 팀 실제 설정(빌드툴·DB·`API_명세서_v2.md`)이 이 문서의 가정과 다르면 **실제 설정을 우선**한다.

---
---

# 부록 — 명세 보강 (기능 · API 상세 · 매핑)

> 아래 부록은 본 명세(0~12장)를 **보강**하는 참조 자료다. 4~6장(엔티티·Enum·API)과 함께 본다.
> 값이 충돌하면 팀 원본 `API_명세서_v2.md`를 SSOT로 따른다. 기능/엔드포인트 ID는 도메인 토큰을 공유한다(예: `PASS-DIAG-*` ↔ 진단 도메인).

## 부록 A. 기능 명세 (Feature 목록 · 우선순위)

> 구현할 기능 전체 스코프와 우선순위. **`필수`** = W1~W2 핵심 경로(안1·안2 공통), **`권장`** = 여유 시 또는 안2 전용.
> AI 파싱(`PASS-COURSE-001`)은 안1·안2 공통, AI 추천(`PASS-RECO-001`)은 **안2 전용**(7/10 게이트에서 빠질 수 있음).

| 기능 ID | 도메인(EPIC) | 우선순위 | 기능명 | 설명 | 비고 |
|---|---|---|---|---|---|
| PASS-AUTH-001 | 인증 | 필수 | 회원가입 | 이메일·닉네임·비밀번호·학과 정보 입력, 정보 활용 동의 후 가입 | BCrypt 해시 |
| PASS-AUTH-002 | 인증 | 필수 | 로그인 | 이메일·비밀번호 검증 후 JWT 액세스 토큰 발급 | JWT 발급 |
| PASS-AUTH-003 | 인증 | 필수 | 로그아웃 | 저장된 토큰 제거 | 클라이언트 토큰 폐기 |
| PASS-AUTH-004 | 인증 | 필수 | 내 정보 조회 | 회원 정보 + `profileId` 유무 반환 | 온보딩/대시보드 분기 |
| PASS-PROFILE-001 | 프로필 | 필수 | 프로필 등록 | 학과·학번 등 학적 정보 등록 | User와 1:1 |
| PASS-PROFILE-002 | 프로필 | 필수 | 프로필 조회 | 프로필 정보 조회 | 본인 소유 검증 |
| PASS-PROFILE-003 | 프로필 | 권장 | 프로필 수정 | 학과·학번 등 수정 | 본인 소유 검증 |
| PASS-COURSE-001 | 수강 이력 | 필수 | 수강 이력 입력 | 과목·학점·이수구분·성적 입력(드롭다운/복붙) | 복붙은 **AI 파싱** |
| PASS-COURSE-002 | 수강 이력 | 필수 | 수강 이력 조회 | 입력한 수강 이력 목록 조회 | |
| PASS-COURSE-003 | 수강 이력 | 권장 | 수강 이력 수정 | 개별 수강 항목 수정 | |
| PASS-COURSE-004 | 수강 이력 | 권장 | 수강 이력 삭제 | 개별 수강 항목 삭제 | |
| PASS-CERT-001 | 졸업 인증 | 필수 | 졸업 인증 체크 조회 | 어학·봉사·논문 충족 상태 조회 | 계정 인증과 무관 |
| PASS-CERT-002 | 졸업 인증 | 필수 | 졸업 인증 체크 입력 | 상태(Pass/Fail/미제출) 입력·갱신 | |
| PASS-DIAG-001 | 졸업 진단 | 필수 | 졸업 요건 진단 | 수강 이력·요건 기준 비교, 달성도·부족 학점 계산 | **규칙 기반** |
| PASS-GPA-001 | 성적 트렌드 | 권장 | 성적 트렌드 분석 | 학기별 GPA 추이 계산 | 규칙 기반 |
| PASS-RECO-001 | AI 추천 | 권장 | AI 과목 추천 | 부족 요건 기반 다음 학기 과목 + 이유 추천 | **AI · 안2 전용** |
| PASS-REQ-001 | 요건 기준 | 권장 | 졸업 요건 기준 제공 | 학과별 요건 기준(필수 학점·인증 등) 제공 | 한 학과 하드코딩 |
| PASS-DASH-001 | 대시보드 | 필수 | 홈 대시보드 | 달성도 요약·부족 항목 집계 표시 | 집계 |

## 부록 B. API 엔드포인트 상세 (권한 · 종류 · 비고)

> 6장 표의 엔드포인트별 상세. 경로는 Base URL `/api/v1` 하위.
> **권한** — `비회원` = 인증 전 허용 / `회원` = `Authorization: Bearer <JWT>` 필수(+ `/profiles/{id}/**`는 본인 소유 검증).

| API ID | method | 경로 | 권한 | 종류 | 비고 |
|---|---|---|---|---|---|
| PASS-AUTH-001 | POST | `/auth/signup` | 비회원 | — | 이메일 중복 체크, BCrypt 해시 |
| PASS-AUTH-002 | POST | `/auth/login` | 비회원 | — | JWT 액세스 토큰 발급 |
| PASS-AUTH-003 | POST | `/auth/logout` | 회원 | — | 클라이언트 토큰 폐기 |
| PASS-AUTH-004 | GET | `/auth/me` | 회원 | — | User + `profileId` 반환 (온보딩 분기) |
| PASS-PROFILE-001 | POST | `/profiles` | 회원 | — | User와 1:1 |
| PASS-PROFILE-002 | GET | `/profiles/{id}` | 회원 | — | 본인 소유 |
| PASS-PROFILE-003 | PATCH | `/profiles/{id}` | 회원 | — | 본인 소유 |
| PASS-COURSE-001 | POST | `/profiles/{id}/courses` | 회원 | — | 복붙 입력은 AI 파싱 |
| PASS-COURSE-002 | GET | `/profiles/{id}/courses` | 회원 | — | |
| PASS-COURSE-003 | PUT | `/profiles/{id}/courses/{cid}` | 회원 | — | |
| PASS-COURSE-004 | DELETE | `/profiles/{id}/courses/{cid}` | 회원 | — | |
| PASS-CERT-001 | GET | `/profiles/{id}/certifications` | 회원 | — | 어학·봉사·논문 상태 |
| PASS-CERT-002 | PUT | `/profiles/{id}/certifications` | 회원 | — | 상태 일괄 갱신 |
| PASS-DIAG-001 | GET | `/profiles/{id}/diagnosis` | 회원 | 규칙 | AI 개입 없음 |
| PASS-GPA-001 | GET | `/profiles/{id}/gpa-trend` | 회원 | 규칙 | 학기별 GPA |
| PASS-RECO-001 | POST | `/profiles/{id}/recommendations` | 회원 | AI | 생성 시 1회 호출 |
| PASS-RECO-002 | GET | `/profiles/{id}/recommendations` | 회원 | AI | `cached` 필드 · 429/503 캐시 폴백 |
| PASS-REQ-001 | GET | `/requirements/{deptCode}` | 회원 | 하드코딩 | 한 학과 요건 기준 |
| PASS-DASH-001 | GET | `/profiles/{id}/dashboard` | 회원 | 집계 | 달성도 요약 |

## 부록 C. Grade ↔ JSON 표기 · GPA 환산

> 5장 `Grade` enum의 직렬화 매핑. 요청/응답 JSON은 **표기 문자열**(예: `"A+"`)을 쓰고, 내부 계산은 **환산값**을 쓴다.

| enum 상수 | JSON 표기 | GPA 환산 |
|---|---|---|
| A_PLUS | `"A+"` | 4.5 |
| A | `"A"` | 4.0 |
| B_PLUS | `"B+"` | 3.5 |
| B | `"B"` | 3.0 |
| C_PLUS | `"C+"` | 2.5 |
| C | `"C"` | 2.0 |
| D_PLUS | `"D+"` | 1.5 |
| D | `"D"` | 1.0 |
| F | `"F"` | 0.0 |
| P | `"P"` | GPA 제외 |
| NP | `"NP"` | GPA 제외 |

- **GPA** = `Σ(학점 × 환산) / Σ(GPA 대상 학점)`. `P`/`NP`는 분자·분모 모두에서 제외.
- 직렬화는 `@JsonValue`/`@JsonCreator` 또는 컨버터로 enum ↔ 표기 문자열을 매핑한다.

## 부록 D. 데모 시딩 / 안정화 (발표 재현성)

> 원칙 2(캐싱) · 10장(시딩) 보강. 발표는 **실시간 AI 호출 없이** 시드 + 캐시로 재현한다.

- **예시 학생 시드** : 빅데이터 인공지능 전공 프로필 1건 + 수강 이력 세트를 시드해, 입력 없이 결과를 바로 보여주는 데모 경로를 만든다.
- **추천 캐싱** : AI 추천 결과는 사전 생성해 캐시에 저장. 발표 중 `GET /recommendations`는 캐시를 반환.
- **폴백 검증** : `429/503` 발생 시 캐시 폴백 경로가 정상 동작하는지 반드시 테스트(발표 사고 방지).
- **E2E 리허설** : 제출 전 로그인→입력→진단→추천 전체 흐름을 최소 1회 통과 확인.

---

> **부록 요약** : 부록 A(무엇을 만드나·우선순위) → 부록 B(엔드포인트 계약) → 부록 C(성적/ GPA 규칙) → 부록 D(발표 안정화). 본문 원칙 1·2를 항상 우선하며, 상세 계약은 팀 `API_명세서_v2.md`와 대조해 확정한다.
