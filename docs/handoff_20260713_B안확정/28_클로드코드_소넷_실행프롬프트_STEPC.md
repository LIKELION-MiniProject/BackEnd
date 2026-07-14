# 28. 클로드코드(Sonnet) 실행 프롬프트 — STEP C (Spring recommend + CORS)

> 클로드코드는 **호스트에서 직접 실행**되므로 python/gradle/git이 모두 동작한다(코워크의 마운트 덮어쓰기·네트워크 제약 없음). 단 팀 규칙상 **main push는 원석이 직접**, `gradlew test`는 한글 경로 이슈로 피하고 서버 기동+curl로 검증.

---
## 붙여넣기 전 준비
- 이 폴더에서 클로드코드 실행: `C:\Users\송원석\Documents\LikeLion_MiniProject_Team_3`
- SSOT 문서: `docs/handoff_20260713_B안확정/` 의 **27(계약 v2.1)**, 26(STEP B 결과), 02·24(BE 설계). 상위 20~25, 00~08도 참조.

---
(★ 여기부터 복사 ★)

너는 PassPort(졸업요건 진단·AI 과목 추천, Java/Spring Boot 3.3.4 + Python passport-ai) 백엔드 작업자다.
`docs/handoff_20260713_B안확정/27_계약_v2.1_확정_검토반영.md` 를 **계약 SSOT**로 삼는다. 아키텍처=B안. 한 STEP 완료 → diff 요약 보고 → 내 확인 후 다음.

## 현재까지 상태 (반영해서 진행)
- STEP B 완료: `passport-ai/core/` 에 5방향 확장(directions.py 신설 + models/gemini/prompt/validate/fallback/cache/recommend 수정). 계약 v2.1(reasons 3개 고정순서, caution=EXAM_SOLO·TEAM_ACTIVE 전용·thin이면 null, thin은 매칭 개수 그대로·방향 밖 보충 금지, category 필드, cache=live 시각동일·배지는 fallback만) 반영됨.
- 인증/DB 이미 완비: `auth` 패키지(signup/login/logout/me), Spring Security+JWT, `User` 엔티티, H2(`jdbc:h2:mem:passportdb`, ddl-auto=update). recommend 구현 시 **기존 profile 패키지의 소유권 검증 패턴과 `global/common/ApiResponse`, `global/security/AuthUser` 를 재사용**한다.
- FE(React19+Vite8) 완성, 실서버 연동만 남음. 목업 3종: `docs/handoff_20260713_B안확정/mocks/`.

## 불변 규칙
1. 사실 판정=규칙, AI=과목선택+이유②③만. 근거 없는 값 창작 금지 — 모르면 "⚠️ 확인 필요"로 남기고 물어라.
2. 응답 래퍼 `{success, data, message}`(기존 `ApiResponse` 사용). recommendation 항목=`{courseCode, courseName, credit, category, reasons[3]}`.
3. 방향 선택/비교는 FE 처리 → **방향별/비교용 엔드포인트 만들지 마라**. B안(1회 생성) 유지.
4. `.env`·시크릿 절대 커밋 금지. `main` push는 내가 직접. `gradlew test` 대신 서버 기동+curl로 검증.

## STEP A — 현행 확인 (보고)
- `passport-ai/core/` 최신 상태 확인 후 `python batch.py demo`로 STEP B 실동작부터 검증:
  ```
  cd passport-ai
  ren cache\202312345.json 202312345.old.json
  ren .env .env.bak && python batch.py demo   # 폴백: source=fallback, 4방향(FAST_GRAD/GRADE_SAFE(thin)/EXAM_SOLO(caution)/TEAM_ACTIVE(thin)), MAJOR_DEEP 없음
  ren .env.bak .env && python batch.py demo    # live: source=live, cache/202312345.json v2 재생성
  ```
  결과가 계약 v2.1과 다르면 STEP C 전에 원인 분석·수정. `core/_synctest.py`가 있으면 삭제.
- Spring: `profile` 패키지의 controller/service에서 소유권 검증(토큰 User가 해당 profile 소유자인지) 방식과 `ApiResponse`, `AuthUser`, 예외(`global/error`) 사용법 확인.

## STEP C — Spring `recommendation` 패키지 + CORS
`src/main/java/com/passport/recommendation/{controller,service,dto}` 신설:
1. **dto (record)**: `RecommendationResponse(List<DirectionDto> directions, List<RecoItemDto> recommendations, String defaultDirectionId, String source, String generatedAt)`, `DirectionDto(String directionId, String name, String description, String caution, boolean thin, List<RecoItemDto> recommendations)`, `RecoItemDto(String courseCode, String courseName, int credit, String category, List<String> reasons)`. (계약 v2.1과 정확히 일치)
2. **service**: `getRecommendations(profileId)` 체인 —
   ① `passport-ai/cache/{studentKey}.json`(v2) 존재 → 읽어서 그대로 서빙(`source`는 파일값; 없거나 파싱 실패 시 ②). studentKey는 profile→studentId 매핑(기존 profile/도메인 확인).
   ② 순수 Java 규칙 폴백: 진단 결과로 부족요건 top5(=FAST_GRAD 상당) 1개 방향 생성, `source="fallback"`. FastAPI 라이브 호출은 TODO 인터페이스만.
   - POST=생성(데모 기준 "사전 생성 캐시 서빙"이면 충분, 재생성 정책 주석)·GET=조회. 둘 다 소유권 검증.
3. **controller**: `POST`/`GET /api/v1/profiles/{id}/recommendations`, `@AuthenticationPrincipal AuthUser`로 소유권 검증(profile 패턴 재사용), `ApiResponse.success(...)`.
4. **CorsConfig** (`global/config`, WebMvcConfigurer): allowedOrigins `http://localhost:5173`,`http://localhost:5174`, allowedHeaders 포함 `Authorization`, methods GET/POST/PUT/DELETE/OPTIONS, allowCredentials true. `SecurityConfig`의 permitAll/authenticated와 충돌 없게(추천은 authenticated).
5. **검증(curl)**: 서버 기동 후
   ```
   # 로그인 → 토큰
   curl -s -X POST localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"email\":\"...\",\"password\":\"...\"}"
   curl -s localhost:8080/api/v1/profiles/{id}/recommendations -H "Authorization: Bearer <JWT>"     # 캐시 서빙
   curl -s -X POST localhost:8080/api/v1/profiles/{id}/recommendations -H "Authorization: Bearer <JWT>"
   curl -s localhost:8080/api/v1/profiles/{남의id}/recommendations -H "Authorization: Bearer <JWT>"  # 403/거부
   ```
   응답이 계약 v2.1 구조(directions·caution·thin·category·source·최상위 미러)와 일치하는지 표로 보고.

## STEP D — 마무리
- E2E curl(회원가입→로그인→프로필→수강이력→진단→GPA→대시보드→추천 생성/조회 + 타인 거부) 표.
- 네트워크 차단(.env 리네임) 상태에서 폴백 경로 재확인(source=fallback).
- 커밋 diff 요약 제시(커밋·push는 내가). 노션 RECO-001/002를 계약 v2.1로 갱신.

## 컷라인
directions가 막히면 최상위 recommendations 단일 추천으로 후퇴(하위호환). 7/14 정오까지 STEP B~C 미해결 시 컷 판단을 기록.

지금 STEP A(실동작 검증)부터 시작해라.

(★ 여기까지 복사 ★)

---
## 참고 — 이미 있어서 STEP C에서 새로 만들지 않는 것
- 인증(signup/login/logout/me), JWT, `User`/`UserRepository`, `SecurityConfig`.
- `global/common/ApiResponse`, `global/security/AuthUser`, `global/error`(BusinessException/ErrorCode/핸들러).
- `profile` 소유권 검증 패턴(recommend가 그대로 차용).
- DB(H2 인메모리) — 재시작 시 초기화되므로 데모는 시딩 필요.
