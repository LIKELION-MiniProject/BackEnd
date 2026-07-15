# 80. MASTER 세션 인계 ① — 이번 세션에서 한 일 전부 (2026-07-15)

> **용도**: 이 대화(코워크 세션)에서 이루어진 모든 작업을 빠짐없이 기록. 70~74 마스터 세트를 읽고 이어받은
> 다음 세션이므로, **70~74는 여전히 유효한 배경 지식**이고 이 문서는 그 위에 "무엇이 더 진행됐는지"를 더한다.
> 짝 문서 `81_MASTER_다음작업_커밋검증_가이드.md`와 함께 본다 — 80=무엇을 했나, 81=다음에 뭘 하나·어떻게 검증하나.

---

## 0. 이 세션이 받은 입력

1. **70~74 마스터 세트**(직전 세션 인계) — 그대로 읽고 시작.
2. **`PassPort_UI정합_AWS배포_전체실행계획_20260714.md`**(원석 업로드) — PART 1(전체 로드맵: PHASE 0~6) + PART 2(오퍼스 붙여넣기 프롬프트: STEP 0~5). 이번 세션은 이 프롬프트의 **STEP 1**을 실행한 것.
3. **신영 UI 목업 스크린샷 6장**(원석 업로드, 이미지) — 마이페이지(조회/수정), AI분석 홈, 성적진단결과, 성적(졸업요건/수강내역), 졸업요건입력, 로그인/회원가입/홈. STEP 1 필드 설계의 근거.
4. **대화 중 추가 지시** — "페르소나는 성적 입력마다 분석해 DB에 저장하고, 홈 화면·AI분석 홈에 노출돼야 한다" (신영 제안, 실행계획 문서에는 없던 요구사항 — 이번 세션에서 새로 반영).

## 1. 작업 A — STEP E(인증 5분야 진단) 코드 검증 + 수정 2건

70~74 인계 직후 "STEP E가 잘 구현되어 있는지 확인해줄래?" 요청에 따라 로컬 레포(`request_cowork_directory`로 연결)를 직접 읽고 검증.

**검증 결과**: `DiagnosisService.buildGraduationCertification`의 판정 로직(`대상(TARGET)`으로 남은 분야가 하나도 없어야 `fulfilled=true`), `eligible = creditsOk && certsOk && gpa.fulfilled() && gradCertOk` AND 조합, `CertMark`/`RequirementCertificationTargets`/`EffectiveRequirement` 데이터 흐름 전부 71 문서 §5 스펙과 **정확히 일치**.

**발견한 개선점 2건, 수정 완료**:

| # | 파일 | 내용 |
|---|---|---|
| 1 | `src/main/java/com/passport/requirement/service/UserRequirementService.java` | `validateComplete`가 `certification` 객체 유무만 검사하고 내부 5개 필드 개별 null은 검사 안 함 → 최종 제출(draft=false) 시 일부만 채우면 조용히 "충족"으로 오판정될 위험. `validateCertificationTargetsComplete()` 추가해 5개 필드 전부 non-null 강제. |
| 2 | `src/test/java/com/passport/diagnosis/service/DiagnosisServiceTest.java` | 5분야 판정 로직을 검증하는 자동화 테스트가 전무(수동 curl 검증만 있었음) → 3개 테스트 추가: ①대상 남은 분야 있으면 불충족 ②전부 완료/비대상이면 충족+eligible 반영 ③유저 요건 미저장시 null. `@BeforeEach`의 `resolve()` 기본 스텁을 테스트에서 재정의하며 Mockito strict stubbing 충돌 나서 `lenient()`로 처리. |

## 2. 작업 B — 실행계획 STEP 1: 신영 UI 정합 백엔드 5건

`PassPort_UI정합_AWS배포_전체실행계획_20260714.md`의 STEP 1을 그대로 실행. **PHASE 0(안전 push)은 원석이 이미 완료한 상태**(대화 중 "깃허브에 푸쉬했어"라고 알려줌 — 단, 이 STEP 1 작업 *이전* 시점의 코드를 push한 것이므로 STEP 1 이후 변경분은 아직 push 안 됨. 81 문서 §1 참고).

### ① Profile 표시용 필드 확장 (마이페이지 조회/수정 화면 근거)
- **파일**: `Profile.java`(entity), `ProfileResponse.java`, `ProfileUpdateRequest.java`, `ProfileService.java`
- **추가 필드**(전부 nullable, 표시 전용 — 진단/요건 로직 미연결): `grade`(학년), `currentSemester`(이수학기), `enrollmentStatus`(재학생/휴학생/졸업 예정 — enum, `@JsonValue` 한글 라벨), `expectedGraduationYear`(졸업예정연도), `doubleMajorType`(해당없음/복수전공/융복합전공 — enum), `additionalMajor`(추가전공명), `advisorProfessor`(지도교수명).
- GET/PATCH(`/api/v1/profiles/{id}`) 양쪽 다 반영. POST(생성)는 손대지 않음(계획에 명시 안 됨 — 기능 동결 원칙).
- 스크린샷 근거: "마이페이지" 조회 화면(기본정보: 학번/입학연도·학년/이수학기·재학여부·졸업예정연도, 전공정보: 학과·복수전공여부·지도교수), 수정 화면(라디오 버튼: 재학생/휴학생/졸업 예정, 해당없음/복수전공/융복합전공).

### ② persona 블록 (규칙 템플릿, AI 미개입)
- **파일**: `passport-ai/core/profile.py`(persona 템플릿+메서드), `core/models.py`(Result.persona 필드), `core/recommend.py`(연결), `core/cache.py`(구버전 캐시 호환 읽기), `recommendation/dto/PersonaDto.java`(신규), `recommendation/dto/RecommendationResponse.java`(additive 필드).
- `LearningProfile.pattern`(기존 성향분석 4패턴: indiv_strong/collab_strong/balanced/none)별로 `{type, label, description, strategies[3], summary[3]}` **고정 템플릿**(긍정 프레이밍, 이름 미포함 — FE가 프로필 이름과 조합) 반환.
- 규칙 폴백(Java `ruleFallback()`)은 성향분석 근거(과목 특성 데이터)가 없어 `persona=null`.
- ⚠️ **이 시점(작업 B)에서는 persona가 "AI 분석하기" 버튼(POST/GET recommendations) 응답에만 있었음.** 홈 화면 노출은 작업 C에서 추가.

### ③ gpa-trend 확장 (categoryGpa + 예측)
- **파일**: `gpa/dto/GpaTrendResponse.java`, `gpa/service/GpaTrendService.java`
- `categoryGpa`: 이수구분을 전공(MAJOR_REQUIRED+MAJOR_ELECTIVE)/기초(MAJOR_BASIC)/교양(GE_REQUIRED+GE_ELECTIVE)/기타(GENERAL_ELECTIVE) 4그룹으로 묶은 평균 GPA. 그룹에 과목 없으면 `gpa=null`.
- `predicted`: `SemesterGpa`에 boolean 필드 추가. 실제 GPA 학기가 있으면 직전 추세를 단순 외삽(2학기 이상이면 최근 변화폭 이어붙임, 1학기면 그 값 유지)해 **다음 학기 예측 1건**을 semesters 배열에 추가(`predicted=true`, `earnedCredit=0`). GPA 데이터가 하나도 없으면 예측 안 함(근거 없는 값 생성 금지).
- `DashboardResponse.gpaTrend`도 같은 리스트를 그대로 쓰므로 예측 포인트가 대시보드에도 흘러들어감(의도된 동작, FE가 `predicted` 플래그로 구분).

### ④ 대시보드 coreLiberal.current 반영
- **파일**: `dashboard/service/DashboardService.java`
- 기존: `categoryView("coreLiberal", "핵심 교양", 0, requirement.coreLiberalTargetCount(), "영역")` — current가 항상 0으로 하드코딩.
- 변경: **current와 required를 스왑** → `current=requirement.coreLiberalTargetCount()`(유저가 요건 입력 화면에서 대상 표시한 영역 수, DB 저장값), `required=5`(고정 상수 — CoreLiberalArea가 항상 5행 스키마라는 사실에 근거, 지어낸 값 아님).
- ⚠️ **한계**: 과목↔영역 매핑 데이터가 없어 "실제 완료한 영역 수"는 여전히 계산 불가. current는 "완료 여부"가 아니라 "유저가 대상으로 신고한 영역 수"의 근사치 — 코드 주석에 명시함. 스크린샷의 "핵심 교양 4/5영역"과 형식은 맞지만 의미는 근사치.

### ⑤ 컷 3건 (인서 전달용 — 코드 아님)
- 엑셀 업로드: FE가 SheetJS로 파싱 후 기존 `POST /courses` 반복 호출(BE 작업 없음).
- 아이디/비밀번호 찾기: 링크 비활성 처리.
- 로그인 폼: 라벨만 "아이디"로 표시, 실제 전송값은 기존 email 그대로.

## 3. 작업 C — persona 상시 저장(DB) 기능 [원석 추가 요청, 신규]

**요청 원문 요지**: "성적이 입력될 때마다 분석해서 데이터베이스에 저장하고, 과목 추천할 때도 사용할 수 있었으면 좋겠다. 홈 화면과 AI 분석 홈에 페르소나가 드러나는 건 당연하다." → 70~74/실행계획 어디에도 없던 요구사항이라 이번에 처음 설계·구현.

### 설계
- persona 분류(`LearningProfile.analyze()`+`.persona()`)는 **100% 규칙 계산, Gemini 호출 없음** → "AI 추천" 버튼(POST /recommendations, 5~6초 걸리는 무거운 경로)과 완전히 분리해서, 수강 이력이 바뀔 때마다 가볍게 재계산해도 부담이 없다고 판단.
- 새 진입점 **`passport-ai/persona.py`**: `bridge.py`(전체 추천)의 축소판. stdin으로 `{studentKey, history}` 받아 과목명→코드 매핑 후 `analyze()`+`persona()`만 실행, stdout으로 persona dict 하나만 출력. `.env`/Gemini 키 로드 없음(필요 없어서) — API 키 상태와 무관하게 항상 동작.
- 새 Java 패키지 **`com.passport.persona`**:
  - `domain/ProfilePersona.java`: `@Entity`, Profile과 1:1(`profile_personas` 테이블). `type`/`label`/`description`(단일 컬럼) + `strategies`/`summary`(`@ElementCollection` List, 각각 `profile_persona_strategies`/`profile_persona_summary` 테이블, `@OrderColumn`으로 순서 보존).
  - `repository/ProfilePersonaRepository.java`: `findByProfileId`.
  - `service/PersonaBridge.java`: `PassportAiBridge`와 같은 구조(ProcessBuilder subprocess)로 `persona.py` 호출. 타임아웃 20초(Gemini 없어 `bridge.py`의 60초보다 짧게).
  - `service/PersonaService.java`:
    - `refresh(Profile profile)`: 재계산 후 DB upsert. **최선노력(best-effort)** — 실패해도 예외를 던지지 않고 로그만 남김(수강 이력 저장 자체가 실패하면 안 되므로).
    - `get(Long profileId)`: DB에 저장값 있으면 그걸 반환. **없으면**(예: `data.sql`로 시드된 데모 계정처럼 `CourseService`를 거치지 않고 직접 INSERT된 경우) `cache/{studentId}.json`(기존 추천 캐시)에 남은 persona 스냅샷으로 폴백. 둘 다 없으면 empty.
- **트리거 연결**: `CourseService.create/update/delete` 3곳 전부에서 저장 직후 `personaService.refresh(profile)` 호출.
- **노출**:
  - `DashboardResponse`(홈 `GET /dashboard`)에 `persona` 필드 추가 — **DB 저장값을 그대로 서빙**(재계산 안 함, 빠름).
  - `RecommendationResponse`(AI분석 `GET/POST /recommendations`)의 `persona`도 `RecommendationService.withFreshPersona()`로 **DB 최신값을 우선 덮어씀** — passport-ai 캐시에 박제된 persona(생성 시점 스냅샷)보다 최신 성적을 반영한 DB 값이 항상 우선하도록. `personaService.get()`이 empty면 원래 응답의 persona를 그대로 둠.
- `PersonaDto`는 `com.passport.recommendation.dto` 패키지에 그대로 둠(원래 작업 B에서 만든 자리) — Dashboard·Persona 서비스가 이걸 재사용. 패키지 경계상 조금 어색하지만(추천 도메인 DTO를 대시보드/성향 서비스가 씀), 새 파일 중복 생성보다 낫다고 판단. 필요하면 나중에 중립 패키지로 이동 가능(파일 삭제 도구가 없어 이번엔 이동 안 함).

### 새로 생기는 DB 테이블 (ddl-auto:update가 자동 생성 — 마이그레이션 스크립트 없음)
- `profiles` 테이블에 컬럼 7개 추가(①에서 이미 설명).
- `profile_personas`(신규 테이블): id, profile_id(FK unique), type, label, description, updated_at.
- `profile_persona_strategies`(신규, `@ElementCollection`): profile_persona_id, position, strategy.
- `profile_persona_summary`(신규, `@ElementCollection`): profile_persona_id, position, summary_line.

## 4. 변경 파일 전체 매니페스트 (이 세션에서 실제로 만진 것만)

### 신규 생성
- `passport-ai/persona.py`
- `src/main/java/com/passport/persona/domain/ProfilePersona.java`
- `src/main/java/com/passport/persona/repository/ProfilePersonaRepository.java`
- `src/main/java/com/passport/persona/service/PersonaBridge.java`
- `src/main/java/com/passport/persona/service/PersonaService.java`
- `src/main/java/com/passport/recommendation/dto/PersonaDto.java`

### 수정 (Java)
- `src/main/java/com/passport/requirement/service/UserRequirementService.java` (작업 A)
- `src/test/java/com/passport/diagnosis/service/DiagnosisServiceTest.java` (작업 A)
- `src/main/java/com/passport/profile/domain/Profile.java` (작업 B①)
- `src/main/java/com/passport/profile/dto/ProfileResponse.java` (작업 B①)
- `src/main/java/com/passport/profile/dto/ProfileUpdateRequest.java` (작업 B①)
- `src/main/java/com/passport/profile/service/ProfileService.java` (작업 B①)
- `src/main/java/com/passport/recommendation/dto/RecommendationResponse.java` (작업 B②, 작업 C에서 재수정 없음)
- `src/main/java/com/passport/recommendation/service/RecommendationService.java` (작업 B②persona=null 추가 + 작업 C withFreshPersona)
- `src/main/java/com/passport/gpa/dto/GpaTrendResponse.java` (작업 B③)
- `src/main/java/com/passport/gpa/service/GpaTrendService.java` (작업 B③)
- `src/main/java/com/passport/dashboard/dto/DashboardResponse.java` (작업 B④에서 열었다가 작업 C에서 persona 필드 추가로 재수정)
- `src/main/java/com/passport/dashboard/service/DashboardService.java` (작업 B④ coreLiberal 스왑 + 작업 C persona 주입)
- `src/main/java/com/passport/course/service/CourseService.java` (작업 C — personaService.refresh 연결)

### 수정 (Python, passport-ai)
- `passport-ai/core/profile.py` (persona 템플릿 4종 + `LearningProfile.persona()`)
- `passport-ai/core/models.py` (`Result.persona` 필드 + `to_dict()`)
- `passport-ai/core/recommend.py` (persona 계산·전달)
- `passport-ai/core/cache.py` (`read_cache`가 persona 보존)

## 5. FE 스크린샷에서 확인한 화면 구조 (참고용 원본 근거)

- **마이페이지(조회)**: 프로필 사진+이름+나이+학년+전공, 기본정보(학번/입학연도, 학년/이수학기, 재학여부, 졸업예정연도), 전공정보(학과, 복수전공·융복합전공 여부, 지도교수). "수정하기" 버튼.
- **마이페이지(수정)**: 위 항목들을 각각 입력폼/라디오로. 재학여부=재학생/휴학생/졸업 예정(라디오), 복수전공여부=해당없음/복수전공/융복합전공(라디오)+추가전공 텍스트.
- **AI 분석 홈**: "성적 진단 결과" 카드(persona 요약: "OOO님은 발표와 팀플이 조금 부담되는 유형이에요" + "진단 결과 자세히 보기") + "AI 분석" 카드("AI가 진단 결과를 바탕으로 과목을 추천해줘요" + "AI 분석 보러가기") + 학기별 GPA 흐름 그래프.
- **성적 진단 결과**: "종합 진단: 안정 성장형"(persona label) + description + "추천 학습 전략"(persona.strategies, 체크마크 3개) + 학기별 GPA 흐름(예측 포인트 포함) + "과목 영역별 강점 분석"(categoryGpa 4개 바) + "한눈에 보는 요약"(persona.summary, 아이콘 3개).
- **성적(졸업요건/수강내역 탭)**: 졸업요건 도넛차트(overallProgress %) + 요건별 이수 현황 리스트(총이수학점/전공/교양/핵심교양/필수과목/졸업인증/졸업시험 — DashboardResponse.categories) + 수강내역 표.
- **졸업요건 입력**: 1.졸업요구학점/이수(전체 학점 기준), 2.필수교과목 이수, 3.핵심교양 이수(5영역 과목수/학점), 4.졸업인증제 현황(5분야 대상/비대상), 5.졸업시험/논문.
- **홈**: "OOO님의 졸업 진행 현황"(overallProgress %) + 남은 목표/이번 학기 우선 목표 + "종합 진단"(persona label+description) + "추천 학습 전략"(persona.strategies) + "AI 분석으로 더 자세히 알아보기" 버튼.
- **로그인/회원가입**: 아이디/비밀번호 폼 — 컷 항목 참고(라벨 "아이디", 아이디/비밀번호 찾기 링크 비활성).

---
> 다음 문서: **`81_MASTER_다음작업_커밋검증_가이드.md`** — 커밋 상태(중요), 검증 방법, 실행계획 대비 현재 위치, 미결정 사항.
