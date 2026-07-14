# PassPort — 대화 인계 노트 (Handoff)

> **용도**: 이 대화를 새 Claude Code 세션에서 이어가기 위한 요약입니다. 새 세션을 시작할 때
> 이 파일을 읽어달라고 하면, 지금까지의 진행 상황·결정 이유·미해결 이슈를 그대로 이어받을 수 있습니다.
> 프로젝트 자체의 개발 명세는 [`CLAUDE.md`](CLAUDE.md)가 SSOT입니다 — 이 문서는 그 이후의
> **진행 기록**입니다.
> **최종 갱신**: 2026-07-07

---

## 0. 지금 상태 한 줄 요약

이번 세션에서 **"AI 모델(Gemini) 연동 전까지의 모든 것"**을 목표로, CLAUDE.md 10장 W1~W2 순서의
**2~7단계를 전부 구현 완료**했습니다: 인증(JWT), 기존 Profile/Course/Certification에 소유권 검증,
Requirement(하드코딩 요건), Diagnosis(졸업 진단 규칙), GPA Trend, Dashboard 집계. **남은 건 6단계
Gemini 연동(AI 추천)뿐**이며, 이번 세션에서는 의도적으로 손대지 않았습니다. 서버를 실제로 띄워
회원가입→로그인→프로필→수강입력→인증체크→진단→GPA→대시보드 전체 흐름을 curl로 직접 검증 완료.

---

## 1. 프로젝트 개요 (자세한 건 CLAUDE.md 참고)

- **PassPort**: 수강 이력을 입력하면 졸업 요건 진단 + AI 과목 추천을 해주는 학사 대시보드
- 대상 학과 1개(빅데이터 인공지능 전공) 하드코딩, Web 전용
- 팀: PM+BE(원석 = 이 대화의 사용자), BE(은종), FE(인서), 디자인(신영)
- 마감: 코드 제출 7/17 22:00, 발표 7/20
- 기술 스택: Java 21, Spring Boot 3.3.4, Gradle 9.0.0, Spring Data JPA/Hibernate, H2(개발용), Lombok,
  **Spring Security + JJWT 0.12.6 (이번 세션에 추가)**
- 절대 원칙(CLAUDE.md 1장): 졸업 판정·GPA 계산은 전부 규칙 기반 Java 코드로, AI는 파싱·추천에만 관여

---

## 2. 이번 세션에서 한 일 (시간순)

사용자가 "AI 모델 연동 전까지의 모든 것"을 만들어달라고 요청 → 8개 작업으로 쪼개서 순서대로 진행.

1. **졸업요건 실제 수치 확인 질문**: Diagnosis 로직이 의존하는 학과 요건 수치가 아직 미확보 상태임을
   확인(HANDOFF 이전 버전에 기록됨). 사용자에게 물어봤고, "아직 없음 — 임시값으로 구조 완성"으로
   결정. `BigdataAiRequirement`에 TODO 주석과 함께 placeholder 수치(총 130학점 등)를 채워 넣음.
   **실제 수치가 확정되면 이 클래스 하나만 교체하면 되도록 설계함.**
2. **Auth 도메인 구현**: `build.gradle`에 `spring-boot-starter-security` + `jjwt-api/impl/jackson
   0.12.6` 추가. `global/security` 패키지(JwtTokenProvider, JwtAuthenticationFilter, AuthUser principal,
   JwtAuthenticationEntryPoint(401), JwtAccessDeniedHandler(403)) + `global/config/SecurityConfig`.
   `auth/{dto,service,controller}` 신규 작성 — `POST /auth/signup·login·logout`, `GET /auth/me`.
   로그인은 Spring의 AuthenticationManager를 쓰지 않고 **직접 PasswordEncoder.matches()로 비교하는
   수동 방식**을 선택함(MVP 스코프에 UserDetailsService까지는 불필요하다고 판단).
3. **JWT_SECRET/JWT_EXPIRATION 환경변수 원칙 준수**: CLAUDE.md 원칙 3("application.yml은 `${ENV_VAR}`
   참조만") 때문에 **기본값(default)을 코드에 넣지 않음**. 로컬 실행 시 반드시 환경변수를 직접
   설정해야 서버가 뜸 (6장 "재개 방법" 참고).
4. **데모 계정 비밀번호를 BCrypt 해시로 교체**: 기존 `data.sql`의 `{demo-not-hashed}` 평문을 실제
   BCrypt 해시로 교체. 해시를 만들 때 `./gradlew test`가 이 환경에서 실행 자체가 안 되는 걸 발견해서
   (아래 11번 참고) 임시로 gradle 캐시의 jar를 직접 `javac`/`java -cp`로 실행해 해시를 뽑아냄. 데모
   두 계정(`demo1`, `demo2`) 평문 비밀번호는 **`Passport1!`**로 통일.
5. **기존 Profile/Course/Certification에 소유권 검증 추가**: `ProfileCreateRequest`에서 `userId`
   필드를 제거하고 토큰의 인증 주체(`AuthUser`)에서 가져오도록 변경(더 이상 아무 userId나 넘겨서
   남의 이름으로 프로필을 만들 수 없음). `ProfileService.findOwnedProfile(profileId, userId)`를
   공용 소유권 검증 메서드로 만들어 Course/Certification 서비스가 `ProfileRepository` 대신
   `ProfileService`를 통해 재사용하도록 리팩터링(로직 중복 방지). 컨트롤러 3개 모두
   `@AuthenticationPrincipal AuthUser`로 인증 주체를 받도록 변경.
6. **Requirement 도메인**: `requirement/{domain,dto,service,controller}` 신규. `GraduationRequirement`
   레코드(총 이수학점·전공필수/선택·교양필수/선택·자유선택·인증요건·최소GPA) + `BigdataAiRequirement`
   상수 클래스(placeholder, 위 1번 참고) + `GET /requirements/{deptCode}`.
7. **Diagnosis 도메인**: `diagnosis/{dto,service,controller}` 신규. Course 이수 이력 + Certification +
   Requirement를 조합해 카테고리별 이수학점 대비 부족분, 인증 충족 여부, GPA 충족 여부, 전체 졸업
   가능 여부(`eligibleForGraduation`)를 계산. `Course.Grade`에 `isCreditEarned()`를 추가해서
   "F는 GPA엔 반영되지만 학점은 미인정, P는 학점 인정되지만 GPA 제외"라는 CLAUDE.md 부록C 규칙을
   정확히 구현.
8. **GPA 계산 로직 공용화**: `course/service/GpaCalculator`를 새로 만들어 Diagnosis와 GPA Trend
   두 도메인이 "Σ(학점×환산)/Σ(GPA대상학점), P/NP 제외" 규칙을 중복 구현하지 않고 공유하도록 함.
9. **GPA Trend 도메인**: `gpa/{dto,service,controller}` 신규. `GET /profiles/{id}/gpa-trend` —
   (year, semester)별로 그룹핑해 학기별 GPA·이수학점 + 전체 누적 GPA 반환.
10. **Dashboard 도메인**: `dashboard/{dto,service,controller}` 신규. Diagnosis + GPA Trend 결과를
    재사용(중복 계산 안 함)해서 홈 화면용으로 재구성 — 전체 달성도, 부족한 이수 구분만 추려낸 목록,
    인증 상태, GPA, 학기별 트렌드를 한 번에 반환.
11. **환경 문제 발견: `./gradlew test` 실행 불가**: 이 컴퓨터의 사용자 폴더 경로(`C:\Users\송원석\...`)에
    포함된 한글 때문에, Gradle이 테스트를 실행할 때 JVM 워커 프로세스의 classpath 인코딩이 깨져서
    `Could not find or load main class worker.org.gradle.process.internal.worker.GradleWorkerMain`
    에러로 무조건 실패함. `compileJava`/`compileTestJava`(컴파일)는 정상 동작 — **오직 `test` 태스크의
    실행(포크된 JVM 기동) 단계만 깨짐**. `sun.jnu.encoding=MS949` vs `file.encoding=UTF-8` 불일치가
    원인으로 보여 `gradle.properties`에 `-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8`을 추가하고,
    `chcp 65001`(콘솔 코드페이지 변경)까지 시도했지만 **해결 안 됨**. OS/JDK 레벨의 고질적 버그로
    판단해 이번 세션에서는 더 파고들지 않음(아래 4장 TODO 참고).
12. **단위 테스트는 작성하되 실행은 못 함**: 11번 문제 때문에 `GpaCalculatorTest`,
    `DiagnosisServiceTest`를 작성했고 **컴파일은 검증**(`./gradlew compileTestJava` 통과)했지만,
    **로컬에서 실제로 실행해서 assert가 통과하는지는 확인하지 못함**. 대신 실제 서버를 띄워서 같은
    시나리오를 curl로 수동 계산 검증(아래 3장 참고) — 계산 결과가 소수점까지 정확히 일치함을 확인.
13. **전체 E2E 검증 (curl)**: `JWT_SECRET`/`JWT_EXPIRATION` 환경변수를 넣고 `./gradlew bootRun`으로
    서버를 실제 기동 → 아래 시나리오를 전부 실제 HTTP 호출로 확인:
    - 로그인(데모 계정) → `GET /auth/me`(profileId null) → 프로필 생성 → 수강 이력 6건 등록(P/F 포함)
      → 인증 상태 갱신 → 진단/GPA트렌드/대시보드/요건조회 정상 응답, **GPA·학점 계산 결과를 손으로도
      재계산해서 일치 확인**(예: 누적GPA 3.2142857142857144)
    - 401(토큰 없이 접근), 403(남의 프로필 접근), 409(중복 이메일 가입, 중복 프로필 생성), 400(짧은
      비밀번호 회원가입), 404(존재하지 않는 학과 코드) 에러 케이스 전부 정상 확인
    - **한글이 포함된 요청 본문을 curl 커맨드에 직접 넣으면 인코딩이 깨지는 현상 재확인**(이전
      세션에도 기록됨) — 서버/코드 버그 아님. UTF-8로 인코딩된 파일을 `--data-binary @file`로 넘기면
      한글도 정상 처리됨을 확인(원인: Bash 툴이 명령 문자열을 시스템 로캘로 넘기는 문제로 추정).
14. **Postman 컬렉션 전면 개편**: `postman/PassPort.postman_collection.json`에 `0. Auth` 폴더
    추가(회원가입/로그인/로그아웃/내정보) + 컬렉션 레벨 Bearer 인증(`{{accessToken}}`) 설정. 로그인
    요청에 테스트 스크립트를 넣어서 **로그인만 실행하면 `accessToken`이 컬렉션 변수에 자동 저장**되고
    이후 모든 요청에 자동으로 붙게 함. `4. Requirement`, `5. Diagnosis`, `6. GPA Trend`,
    `7. Dashboard` 폴더 신규 추가. Profile 생성 바디에서 `userId` 필드 제거(토큰에서 유도되므로).

---

## 3. 실제 계산 검증 예시 (재현 가능)

데모 계정(`demo1@passport.ac.kr`, 비번 `Passport1!`)으로 아래 수강 이력을 등록하고 진단을 조회하면:

| 과목 | 학점 | 구분 | 성적 |
|---|---|---|---|
| Data Structures | 3 | MAJOR_REQUIRED | A+ |
| Algorithms | 3 | MAJOR_REQUIRED | A |
| Machine Learning | 3 | MAJOR_ELECTIVE | B+ |
| Calculus | 3 | GE_REQUIRED | B |
| Writing | 2 | GE_ELECTIVE | P |
| Failed Elective | 2 | GENERAL_ELECTIVE | F |

- 이수 인정 학점 합계 = 14 (F 2학점만 제외, P는 포함)
- 누적 GPA = 3.2142857142857144 (P 제외, F는 0점으로 반영: (13.5+12+10.5+9+0)/14)
- 학기별 GPA: 2021-1학기 3.75, 2021-2학기 4.0, 2022-1학기 3.5, 2022-2학기 0.0

이 숫자들이 `DiagnosisResponse`/`GpaTrendResponse` 응답과 정확히 일치함을 확인했습니다(2장 13번).
새 세션에서 로직을 바꿀 일이 있으면 이 표로 다시 검증해보면 빠릅니다.

---

## 4. 아직 안 끝난 것 / 팀 확인이 필요한 것 (TODO)

- **`./gradlew test` 실행 불가 (신규, 우선순위 높음)**: 2장 11번 참고. 한글 사용자 폴더 경로 때문에
  Gradle 테스트 워커가 뜨지 못함. 팀원 중 한글이 없는 경로(예: `C:\dev\PassPort`)에서 클론해서
  실행하면 정상 동작할 가능성이 높음 — **다른 팀원 PC에서 `./gradlew test`를 한 번 확인해달라고
  요청 필요**. CI(GitHub Actions 등)를 쓴다면 거기서는 문제 없을 것으로 예상(경로에 한글 없음).
- **폴더명 변경 미완료**: `ClaudeCode` → `PassPort` 이름 변경 시도가 이전 세션에서 "resource busy"로
  실패한 채 남아있음. 이번 세션엔 손대지 않음. (참고: 폴더명을 바꿔도 상위 경로의 `송원석`은 그대로라
  위 테스트 이슈는 해결 안 될 가능성이 높음 — 별개 문제로 취급할 것)
- **졸업요건 실제 수치 미확보**: `requirement/BigdataAiRequirement.java`에 TODO 주석과 함께
  placeholder 값(총 130학점 등)을 넣어둠. 은종님이 실제 수치를 주면 **그 파일 하나만 교체**하면 됨.
- **DB 미확정**: 지금은 H2(인메모리, 개발용)만 사용. 실제 운영 DB 확정 필요 (CLAUDE.md 2장)
- **Gemini 모델명 미확정 + AI 추천 미구현**: 이번 세션 스코프에서 의도적으로 제외. CLAUDE.md 8장 —
  AI Studio에서 실제 사용 가능한 모델 확인 필요.
- **단위 테스트 실행 미확인**: `GpaCalculatorTest`, `DiagnosisServiceTest` 작성 완료·컴파일 확인
  완료했지만 위 환경 문제로 로컬에서 실행해서 통과하는지는 못 봤음. 다른 환경에서 한 번 실행해서
  확인 필요.
- **Spring Security 관련 인프런 강의**: 이전 세션 TODO였고 아직 조사 안 함. 필요하면 요청.
- **git 커밋 0개**: 여전히 커밋 안 되어 있음(이번 세션도 커밋 여부를 안 물어봐서 안 함). 사용자에게
  확인 필요.
- **Postman 컬렉션 실사용 검증**: JSON 문법은 확인했지만 Postman 앱에서 실제로 Import해서 클릭
  테스트는 안 해봄(curl로는 전부 확인함). 다음에 Postman으로 한 번 열어서 로그인→변수 자동 저장이
  잘 되는지 확인 권장.
- **UserDetailsServiceAutoConfiguration 경고 로그**: 서버 기동 시 "Using generated security password"
  경고가 뜸(Spring Boot가 UserDetailsService 빈이 없어서 자동 생성한 기본 계정, 실제로는 안 쓰임).
  기능상 문제 없고 무시해도 되지만, 신경 쓰이면 더미 `UserDetailsService` 빈을 하나 등록해서 끌 수
  있음(지금은 굳이 안 함 — 스코프 확장 금지 원칙).

---

## 5. 다음 할 일 (원석·BE, CLAUDE.md 10장 순서대로)

1. (선택) 위 4장 "`./gradlew test` 실행 불가" 문제를 한글 없는 경로에서 재현/해결
2. 은종님에게 받은 실제 졸업요건 수치로 `BigdataAiRequirement.java` 교체
3. **6단계: Gemini 연동 → AI 추천** — 이번 세션에서 유일하게 남겨둔 CLAUDE.md 10장 항목.
   `ai` 패키지(Gemini 클라이언트·프롬프트 빌더) + `recommendation` 도메인 신규 필요.
   모델명 확정 먼저 필요 (CLAUDE.md 8장)
4. (W2~3) 캐싱·폴백 검증(429/503 시 캐시 반환), 데모 시딩 확장, 발표 리허설(CLAUDE.md 부록 D)

---

## 6. 재개 방법 (실무)

### 서버 실행 — ⚠️ 이번 세션부터 환경변수 2개가 반드시 필요합니다

인증(JWT)이 추가되면서 `JWT_SECRET`/`JWT_EXPIRATION` 환경변수 없이는 서버가 기동되지 않습니다
(CLAUDE.md 원칙 3에 따라 코드에 기본값을 넣지 않았습니다).

```bash
# Git Bash / macOS / Linux
cd "C:\Users\송원석\Documents\ClaudeCode"
JWT_SECRET="dev-only-passport-jwt-secret-key-please-change-in-production-32bytes-min" JWT_EXPIRATION=3600000 ./gradlew bootRun
```

```powershell
# PowerShell
$env:JWT_SECRET="dev-only-passport-jwt-secret-key-please-change-in-production-32bytes-min"
$env:JWT_EXPIRATION="3600000"
.\gradlew.bat bootRun
```

- `http://localhost:8080` — 데모 회원 2명(`demo1@passport.ac.kr`, `demo2@passport.ac.kr`, 비밀번호
  둘 다 `Passport1!`)이 자동 시드됨. `Profile`/`Course`/`Certification`은 비어있는 상태로 시작
- H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:passportdb`, 계정 `sa`, 비번 없음)
- 로그인 응답의 `accessToken`을 `Authorization: Bearer <token>` 헤더로 넣어야 `/auth/signup`,
  `/auth/login` 외 모든 API 호출 가능
- **주의**: 이 세션에서 띄웠던 서버 프로세스는 세션 종료 시 죽였습니다. 새 세션에서는 다시 위
  명령으로 실행해야 함

### 테스트 실행 — 이 환경에서는 안 됨 (4장 TODO 참고)

`./gradlew test`는 이 컴퓨터에서 실행 자체가 실패합니다. `./gradlew compileTestJava`로 컴파일까지만
확인 가능. 실제 실행 결과가 필요하면 한글 없는 경로의 다른 PC나 CI에서 돌려보세요.

### git 상태

```bash
cd "C:\Users\송원석\Documents\ClaudeCode" && git status
```

- 브랜치 `main`, 커밋 0개, 전부 untracked 상태 (2026-07-07 기준, 이번 세션도 커밋 안 함)

### Postman

`postman/PassPort.postman_collection.json`을 Import → `0. Auth` 폴더의 "로그인" 요청을 먼저 실행하면
`accessToken`이 자동 저장되어 나머지 요청에 바로 쓸 수 있습니다. 상세 사용법은
`docs/PassPort_Postman_매뉴얼.pdf` 참고(단, 이 PDF는 인증 붙기 전 버전이라 로그인 절차는 반영 안 돼
있음 — 필요하면 갱신 요청).

---

## 7. 작업 스타일 메모 (사용자 선호 — 다음 세션에서 참고)

- **응답은 한국어로.**
- 큰 작업 전에는 옵션을 제시하며 `AskUserQuestion`으로 방향을 먼저 확인받는 걸 선호함. 이번
  세션에서도 "졸업요건 실제 수치 확보 여부"를 먼저 물어봤고, "임시값으로 구조 완성"을 선택받아
  진행함 — **불확실한 사실(팀만 아는 데이터)은 추측하지 말고 반드시 먼저 물어볼 것.**
- **파일 수를 줄이는 것보다 로직 중복 제거를 우선**: 이번 세션에서 GPA 계산 규칙을
  `GpaCalculator`로, 소유권 검증을 `ProfileService.findOwnedProfile`로 공용화해서 Diagnosis/GPA/
  Course/Certification 여러 도메인이 같은 로직을 복붙하지 않게 함 — 이런 리팩터링 방향은 이전
  세션의 "파일 수를 줄이자"는 피드백과 결이 같음(불필요한 중복/분산을 싫어함).
- 코드를 만들면 **반드시 실행해서 검증하길 기대함** — 이번 세션도 서버를 실제로 띄우고 curl로
  회원가입부터 대시보드까지 전체 플로우를 검증했고, GPA/학점 계산 결과를 손으로 재계산해서
  숫자까지 맞춰봄(3장 참고). 이 습관을 계속 유지할 것.
- **환경 문제를 만나면 근본 원인을 파고들되, 해결 안 되면 솔직히 인정하고 대안으로 전환**: 이번
  세션에서 `./gradlew test` 실행 불가 문제를 여러 방법(인코딩 설정, chcp, TEMP 경로 변경)으로
  시도했지만 못 고쳤고, 이를 숨기지 않고 사용자에게 알린 뒤 curl 기반 검증으로 대체함. 이 판단이
  이번 세션에서 특별히 피드백받은 적은 없지만, 이전 세션 메모("사실 확인이 필요한 요청은 추측하지
  말고 검증")와 같은 맥락으로 판단해 적용함.
- **환경변수만 사용하는 원칙(CLAUDE.md 원칙 3)을 로컬 편의보다 우선**: JWT_SECRET에 기본값을 넣으면
  `./gradlew bootRun`이 바로 되니 편하지만, 명세에 "환경변수만"이라고 명시되어 있어 기본값을 넣지
  않음. 대신 실행 방법을 문서에 명확히 남김(6장). 이런 "명세가 불편해도 명세를 따른다"는 태도를
  유지할 것.

---

## 8. 새 세션에서 이어가는 법

새 대화를 시작할 때 이렇게 말하면 됩니다:

> "`HANDOFF.md`랑 `CLAUDE.md` 읽고 이어서 작업해줘. [원하는 다음 작업 설명]"

`CLAUDE.md`가 "무엇을 만들 것인가"(불변 스펙)이고, 이 `HANDOFF.md`가 "여기까지 뭘 했고 뭐가
남았나"(진행 기록)입니다. 이 문서는 매 세션 끝에 갱신하는 걸 권장합니다.
