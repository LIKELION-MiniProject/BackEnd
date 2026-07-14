# 33. BE STEP C 검토 결과 & 푸쉬 판단 (코드/작업 정리 1)

> 클로드코드가 STEP C(Spring recommend + CORS)를 구현한 뒤, 실제 호스트 코드를 전면 검토한 결과. (검토일 2026-07-14)

---

## 1) 이전 요청사항 반영 확인 (Fable 검토 6건 + 계약 v2.1) — ✅ 전부 반영

| 항목 | 반영 위치 | 상태 |
|---|---|---|
| thin: 0개만 제외, <5는 thin+개수 그대로, 방향 밖 보충 금지 | `core/validate.py`(b.courses만 순회), `core/directions.py`(classify) | ✅ |
| reasons 순서 [성향·특성·요건] 고정 | `validate.py`, `fallback.py`, DTO 주석 | ✅ |
| caution = EXAM_SOLO·TEAM_ACTIVE 전용, thin이면 null | `core/directions.py` `effective_caution()` | ✅ |
| category(이수구분) 필드 | `models.Reco.category`, `RecoItemDto.category` | ✅ |
| 최상위 recommendations 미러(하위호환) | `models.Result.recommendations`, `RecommendationResponse` | ✅ |
| cache=live 시각동일, 배지는 fallback만 | DTO 주석·계약 v2.1 | ✅ |
| 라벨(졸업요건 집중형 등) | `directions.py` 상수 | ✅ |

- passport-ai core는 STEP B + 검토 반영본 그대로 유지됨(클로드코드가 되돌리지 않음). `_synctest.py` 삭제 확인.

## 2) Spring recommendation 패키지 검토 — ✅ 견고

- `RecommendationController`: `POST`/`GET /api/v1/profiles/{profileId}/recommendations`, `@AuthenticationPrincipal AuthUser`, `ApiResponse` 래퍼. ✅
- `RecommendationService`:
  - `resolve()` = `profileService.findOwnedProfile(profileId, userId)`(**소유권 검증**) → `readCache(studentId)` → 없으면 `ruleFallback`. ✅
  - `readCache`: `passport-ai/cache/{studentId}.json`(v2)을 `ObjectMapper`로 `RecommendationResponse`에 역직렬화. 파싱 실패 시 폴백. ✅
  - `ruleFallback`: **과목을 지어내지 않고** 진단(diagnosis)의 부족 이수구분 top5를 안내(courseCode=null). "실제 과목 추천은 캐시가 있어야만" — 원칙 1(창작 금지) 준수. ✅
  - `sanitize`: passport-ai 파일명 규칙과 동일(영숫자/-/_). ✅
- DTO 3종(`RecommendationResponse`/`DirectionDto`/`RecoItemDto`)이 캐시 JSON 키와 정확히 일치. Spring Boot 기본 Jackson이 record + `FAIL_ON_UNKNOWN_PROPERTIES=false`라 안전. ✅
- 참조 메서드 실재 확인: `ProfileService.findOwnedProfile`, `Profile.studentId`, `DiagnosisService.diagnose`, `DiagnosisResponse.categories()/CategoryProgress.shortfall()/category()` 모두 존재. ✅
- `CorsConfig`: `/api/v1/**`, origin 5173·5174, `Authorization`·`Content-Type`, allowCredentials. ✅
- `application.yml`: `passport-ai.cache-dir: ${PASSPORT_AI_CACHE_DIR:passport-ai/cache}` 추가됨. ✅

## 3) 결과 일관성(비결정성) 검증 — ✅ 안정적

**질문할 때마다 추천이 바뀌지 않는다.** 이유:
- Spring `GET`/`POST` 둘 다 `resolve()` → **정적 캐시 파일(`cache/{studentId}.json`)을 그대로 서빙**. 요청 시점에 AI(Gemini)를 호출하지 않는다.
- 따라서 같은 프로필로 몇 번을 호출해도 **동일한 JSON** 반환 = 완전 결정적.
- 비결정성(추천이 달라짐)은 오직 `python batch.py demo`로 **캐시를 재생성할 때만** 발생. 운영/발표 중에는 캐시가 고정이라 안정적.
- `POST`도 현재는 재생성이 아니라 캐시 서빙(주석에 TODO 명시). 즉 데모 안전.
- ⚠️ 단, 캐시가 없으면 `ruleFallback`(진단 기반, 이 역시 결정적)로 안정. 실제 과목명 추천을 보이려면 **발표 전 `python batch.py demo`(live)로 데모 학생 캐시를 v2로 생성**해두면 됨(이미 v2 포맷 확인됨).

## 4) 목업 과목명 관련 (실데이터 사용 여부) — 상황 정리

- **BE/실API는 실데이터 사용**: 추천 과목은 `passport-ai/data/courses.json`(실제 개설과목 182건)에서 나온다. 우리가 만든 목업 `docs/.../mocks/reco.*.json`도 이 실데이터의 실제 과목명(국토와자연의이해·우주의이해 등)으로 작성됨.
- **주의 대상 = FE 화면의 임시 과목명**: 이전 FE 세션의 컴포넌트 내장 예시(대학영어·머신러닝 등)는 **화면 개발용 placeholder**로 실데이터 아님. → FE 실연동 시 이 내장 예시를 **실 API 응답 또는 우리 목업 3종으로 교체**하면 해결.
- 참고: 추천 과목 카탈로그는 **courses.json(파일 데이터)** 소관이고, H2 DB는 회원·프로필·수강이력·인증을 저장. 둘은 별개이며 둘 다 "우리 데이터"다.

## 5) 푸쉬 판단 — ✅ 푸쉬 가능

안전 점검 통과:
- `requirements.txt` = `google-genai==1.38.0` 고정 ✅
- `.env`는 `.gitignore`로 제외(add 대상 아님) ✅
- 추적/신규 파일에 API 키(AQ./AIza) 노출 없음 ✅
- 데모 캐시 `202312345.json`이 v2 포맷 ✅
- 참조 메서드·DTO 정합 ✅

**권장(푸쉬 직전 1회)**: 로컬에서 서버 기동 스모크 테스트.
```powershell
cd C:\Users\송원석\Documents\LikeLion_MiniProject_Team_3
# JWT 환경변수 필요 (예시)
$env:JWT_SECRET="dev-secret-please-change-32bytes-minimum-000"
$env:JWT_EXPIRATION="3600000"
.\gradlew bootRun
# 다른 터미널에서 curl로 signup→login→(profile 생성)→recommendations GET 확인
```
- ⚠️ `gradlew test`는 한글 경로 이슈 가능 → `bootRun` + curl 권장.

## 6) 푸쉬 명령어 (원석, Windows)
```powershell
cd C:\Users\송원석\Documents\LikeLion_MiniProject_Team_3
git add -A
# 시크릿 최종 확인
git check-ignore passport-ai\.env          # 경로 출력=정상
git status | Select-String "\.env$"         # 아무것도 안 나와야 정상
git diff --cached | Select-String "AQ\.|AIza"  # 아무것도 안 나와야 정상
# 커밋(기능 블록 단위 권장)
git commit -m "feat(reco): B안 5방향 추천 구현 — passport-ai directions + Spring recommend + CORS (계약 v2.1)"
git commit -am "chore: 핸드오프 문서 및 FE 목업 추가"
git push -u origin main                     # 첫 seed push는 main 1회 예외(7/12 회의)
```
- 인증 막히면: IntelliJ GitHub 로그인 / Git Credential Manager / PAT(repo, 조직 SSO Authorize).

## 7) 남은 리스크·확인 (푸쉬 후)
- [ ] 로컬 `bootRun` + curl 스모크(위 §5) — 실제 직렬화·소유권·CORS 확인.
- [ ] 발표 데모 계정 캐시 v2 생성(live) + (권장) 전공 부족 2번째 계정으로 5방향 다 보이게.
- [ ] 노출 Gemini 키 재발급(7/16 전).
- [ ] H2 인메모리 → 영속성 필요 여부 판단(34 문서).
