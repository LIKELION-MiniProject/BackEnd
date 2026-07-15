# 72. MASTER 인계 ③ — 백엔드 전체 (엔드포인트 · 도메인 · 파일맵)

> Spring(`src/main/java/com/passport/**`) + passport-ai(`passport-ai/**`). 코드가 정본이며 이 문서는 지도.

---

## 1. 전체 엔드포인트 (`/api/v1`)
| 메서드·경로 | 권한 | 설명 |
|---|---|---|
| POST /auth/signup · /auth/login | 비회원 | 가입(BCrypt)·로그인(JWT) |
| POST /auth/logout · GET /auth/me | 회원 | me→{id,email,nickname,profileId} |
| POST /profiles · GET/PATCH /profiles/{id} | 회원(소유) | 프로필 |
| GET·PUT /profiles/{id}/requirements | 회원(소유) | 유저 졸업요건 저장/조회 |
| GET /requirements/{deptCode} | 회원 | 하드코딩 학과기준 |
| POST/GET/PUT/DELETE /profiles/{id}/courses | 회원(소유) | 수강(성적) |
| GET/PUT /profiles/{id}/certifications | 회원(소유) | 인증 합격여부 |
| GET /profiles/{id}/diagnosis | 회원(소유) | 규칙 진단(+5분야) |
| GET /profiles/{id}/gpa-trend | 회원(소유) | 학기별 GPA |
| GET /profiles/{id}/dashboard | 회원(소유) | 집계(확장) |
| POST/GET /profiles/{id}/recommendations | 회원(소유) | AI 추천(라이브 브릿지/캐시) |
- signup/login만 permitAll, 그 외 Bearer JWT. `/profiles/{id}/**`는 `ProfileService.findOwnedProfile`로 소유권 검증. 래퍼 `{success,data,message}`.

## 2. 도메인 상세
### auth
- `POST /auth/signup {email,password(≥8),nickname}` → BCrypt 해시. `POST /auth/login {email,password}` → `{accessToken,tokenType:"Bearer",userId,nickname}`. `GET /auth/me` → `{id,email,nickname,profileId}`(profileId null이면 온보딩). Security + JwtAuthenticationFilter/Provider/EntryPoint/AccessDeniedHandler, AuthUser.

### profile
- `Profile`(user 1:1, deptCode, studentId, admissionYear, name). `POST /profiles {deptCode,studentId,admissionYear,name}`, `GET/PATCH`, 소유권 검증.

### course (성적 입력·저장의 핵심)
- `Course`(profile N:1, name, credit, category, grade, year(컬럼 year_taken), semester, **retake**). `POST/GET/PUT/DELETE /profiles/{id}/courses`(1건 단위). GpaCalculator.
- `CourseCategory` = MAJOR_REQUIRED/MAJOR_ELECTIVE/**MAJOR_BASIC**/GE_REQUIRED/GE_ELECTIVE/GENERAL_ELECTIVE (JSON=enum명). `getKoreanLabel()`은 대시보드 표기용.
- `Grade` @JsonValue 라벨("A+"등), @JsonCreator가 A0/B0/C0/D0 별칭 정규화. P/NP GPA 제외, F는 GPA 반영·이수 미인정.
- 입력 body: `{name,credit,category(enum명),grade("A+"),year,semester,retake}`.

### certification (합격 여부, 5분야와 별개)
- `Certification`(profile N:1, type{LANGUAGE,VOLUNTEER,THESIS}, status{PASS,FAIL,NOT_SUBMITTED}). `GET/PUT /profiles/{id}/certifications`.

### requirement (유저 졸업요건 저장, 12+파일)
- domain: `RequirementCredits`(학점 15칸 임베더블), `CoreLiberalArea`(핵심교양 1영역, @ElementCollection 5행), `CertMark`(TARGET/NOT_TARGET/DONE=대상/비대상/완료), `RequirementCertificationTargets`(인증 5분야 임베더블), `UserGraduationRequirement`(@Entity, profile 1:1, requiredCourseType 교필/전필, coreLiberal, certification, graduationExam EXAM/EITHER/THESIS/NONE, draft), `EffectiveRequirement`(진단·대시보드가 쓰는 유효요건 record, **certificationTargets 포함**, fromUser/fromHardcoded), `GraduationRequirement`+`BigdataAiRequirement`(하드코딩).
- service: `RequirementResolutionService`(유저→하드코딩 폴백), `UserRequirementService`(GET/PUT upsert, draft 완화·완료검증). dto: `RequirementSaveRequest`·`UserRequirementResponse`(source "user"|"default").
- `GET/PUT /profiles/{id}/requirements` 계약: `{credits{15필드}, requiredCourseType("교필"/"전필"), coreLiberal[5]{areaNo,areaName,courseCount,credit,target}, certification{5분야: 대상/비대상/완료}, graduationExam, draft, source}`.

### diagnosis (규칙 진단)
- `GET /profiles/{id}/diagnosis` → `{deptCode, totalCredit{earned,required,shortfall}, categories[CategoryProgress{category,earnedCredit,requiredCredit,shortfall}], certifications[{type,required,status,fulfilled}], gpa{current,required,fulfilled}, eligibleForGraduation, graduationCertification{areas[5],fulfilled}}`.
- 요건은 `RequirementResolutionService`로 조회(유저 저장값 우선). 내 DB의 과목을 읽어 계산(성적 입력→진단 반영 검증됨). 5분야 판정=71 문서 §5.

### gpa / dashboard
- `GET /gpa-trend` 학기별. `GET /dashboard`(확장): overallProgress·categories[{key,name,current,required,unit,status}]·semester·requestedCredits·earnedCredits·**semesterGpa**·courses[{category(한글),courseName,credit,grade}]. ⚠️ coreLiberal.current=항상0(영역매핑 부재)·requiredCourse.required=null(카탈로그 부재)·exam은 THESIS/EITHER만 판정.

### recommendation (AI, 라이브 브릿지)
- `RecommendationController` POST/GET, 소유권 검증, ApiResponse.
- `RecommendationService`: POST=지문 비교→변경 시 `PassportAiBridge.generate()` 라이브/무변경 시 캐시. GET=조회. 실패 시 캐시→규칙폴백(`ruleFallback`, 진단 부족구분 top5, 과목 창작 안 함). 의존: PassportAiBridge·CourseRepository·CertificationRepository·UserRequirementRepository.
- `PassportAiBridge`: ProcessBuilder로 `python bridge.py`(passport-ai/에서) subprocess, stdin=payload/stdout=결과, stderr 비동기 소비, 타임아웃 60s, 실패 시 Optional.empty.
- dto: RecommendationResponse·DirectionDto·RecoItemDto(category 포함) — 계약 v2.1과 일치.

## 3. passport-ai (Python)
- `core/`: candidates·profile·directions(신설)·prompt·gemini·validate·fallback·cache·recommend·models·loader·diagnosis. `batch.py`(demo/models/candidates). `data/courses.json`(182 실과목), `data/demo_*.json`, `cache/202312345.json`(데모 v2)·`.fp`(지문, gitignore).
- **`bridge.py`(신규)**: stdin JSON(studentKey/diagnosis/history/…) → `diagnosis.from_payload` → `recommend()` → stdout 결과. 과목명→코드 매핑(courses.json 이름색인). UTF-8 강제. 로그는 stderr.
- 모델은 `os.environ["GEMINI_MODEL"]`(.env). requirements.txt google-genai==1.38.0 고정.

## 4. 파일맵 (주요, GitHub main 정본)
- `src/main/java/com/passport/` : auth·profile·course·certification·requirement·diagnosis·gpa·dashboard·recommendation·global(config[SecurityConfig·CorsConfig·RestClient]·security·error·common).
- `src/main/resources/` : application.yml(H2 file·jwt·passport-ai cache-dir/python-command/dir)·data.sql(MERGE 시드).
- `passport-ai/` : core/·bridge.py·batch.py·data/·cache/·requirements.txt·.env(로컬)·.env.example·.gitignore.
- `docs/handoff_20260713_B안확정/` : 마스터 70~74 + 세부 + mocks/.

## 5. 검증(재현)
`gradlew bootRun`(JWT_SECRET/JWT_EXPIRATION 필요) → curl로 signup→login→profile→requirements PUT→courses POST→diagnosis→dashboard→recommendations. ⚠️ `gradlew test`는 한글 홈경로 이슈로 실행 불가 → bootRun+curl. PowerShell 한글 바디는 `[Text.Encoding]::UTF8.GetBytes()`.
