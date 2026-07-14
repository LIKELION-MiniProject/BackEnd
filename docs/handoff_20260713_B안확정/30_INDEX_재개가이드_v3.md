# 30. PassPort 핸드오프 — INDEX & 재개 가이드 v3 (BE STEP C 완료 반영)

> **용도**: 이 세션(passport-ai 5방향 확장 + Spring recommend 구현·검토)을 **다른 계정 코워크에서 100% 이어받기** 위한 진입 문서.
> **상위**: 기존 `00~08`, `20~28` 세트 + 이 폴더 `21`(계약 v2.0)·`26`(STEP B)·`27`(계약 v2.1)·`28`(클로드코드 프롬프트)는 유효. **상충 시 이 v3 세트(30~34) + 27(계약 v2.1)이 우선.**
> **표기**: ✅ 완료 · 🟡 부분 · 🔴 미완 · ⚠️ 주의

---

## 이 인계 세트 (요청 구성: 내용 3 + 코드/작업 2)

| 파일 | 분류 | 내용 |
|---|---|---|
| **30_INDEX_재개가이드_v3.md** | 내용 | (이 문서) 진입점·현황·재개 순서·파일 지도 |
| **31_진행현황_전체점검_다음작업.md** | 내용 | 전체 상태표·이번 세션 완료분·로드맵·리스크 |
| **32_FE연동_왜_무엇을_어떻게.md** | 내용 | FE(React+Vite) 실 API 연동 절차·엔드포인트 계약·코드 |
| **33_BE_STEPC_검토결과_푸쉬판단.md** | 코드/작업 | Spring recommend 검토·결정성·목업·**푸쉬 판단/명령어** |
| **34_DB_AWS검토_미결정_다음작업.md** | 코드/작업 | H2 vs AWS 검토·미결정·다음 작업 체크리스트 |

> 함께 보는 필수 참조: **27_계약_v2.1**(SSOT), **29_신영_에러_로딩_요구서**, `mocks/reco.*.json`(실데이터 목업 3종).

---

## 한 줄 요약
passport-ai 5방향(B안) 확장과 Spring recommend 패키지·CORS를 **구현·전면검토 완료**했다. 이전 요청(Fable 6건)·계약 v2.1이 코드에 전부 반영됐고, 추천은 **캐시 서빙이라 질문마다 바뀌지 않는다(결정성)**. 인증/DB는 **이미 완비**(H2 인메모리). **푸쉬 가능 상태**이며, 다음은 로컬 스모크 → push → FE 실연동이다.

## 현재 상태 (핵심만)
- BE AI 추천: ✅ passport-ai directions + Spring recommend + CORS + 계약 v2.1 + 데모 캐시 v2.
- 인증/DB: ✅ 완비(H2 인메모리, 재시작 초기화 주의).
- FE: ✅ 화면 완료 / 🔴 실 API 연동 미완(다음 핵심).
- GitHub: 🔴 미푸쉬(✅ 푸쉬 가능 — 33 문서).
- 디자인(에러/로딩/thin/caution): 🟡 신영 산출 대기(29 문서).
- ⚠️ 노출 Gemini 키 7/16 전 재발급.

## 다음 세션 재개 첫 액션
1. 이 세트(30~34) + 27(계약 v2.1) + 29(신영 요구서) 읽고 맥락 복원.
2. **로컬 스모크**: `gradlew bootRun` + curl(33 문서 §5).
3. 이상 없으면 **GitHub push**(33 문서 §6, 원석 Windows).
4. **FE 실연동**: 목업→실 API(32 문서).
5. 신영 디자인(29) 반영 · 노션 v2.1 갱신 · 7/15 게이트 준비.

## 검증된 사실(이 세션)
- 이전 요청 6건 + 계약 v2.1 **전부 반영**(33 문서 §1).
- 추천 **결정적**(Spring이 정적 캐시 서빙, 요청 시 AI 미호출) — 33 문서 §3.
- 추천 과목은 **courses.json 실데이터** 사용. FE 내장 임시 과목명만 교체 필요 — 33 문서 §4.
- 인증·DB 이미 있음(AUTH 명세 확정). AWS는 지금 불필요 — 34 문서 §A.
- 푸쉬 안전(시크릿 없음, requirements 1.38.0 고정, 데모 캐시 v2).

## 환경 제약 (반드시)
- 클로드코드/네이티브는 python·gradle·git 직접 실행 가능. **코워크 마운트는 기존 파일 덮어쓰기·삭제 미반영**(신규 파일만 동기화) → 코워크에서 기존 파일 검토는 Read 도구(호스트) 사용.
- **git 쓰기·main push는 원석이 Windows 네이티브로.** `gradlew test` 대신 bootRun+curl.
- `.env` 등 시크릿 절대 커밋 금지.

## 파일 지도 (BE 핵심)
- Python: `passport-ai/core/` — directions(신설)·models·gemini·prompt·validate·fallback·cache·recommend. 진입 `batch.py demo`.
- Java: `src/main/java/com/passport/recommendation/`(controller·service·dto) + `global/config/CorsConfig.java`. 재사용: `profile`(소유권), `global/common/ApiResponse`, `global/security/AuthUser`, `auth`(인증).
- 데이터: `passport-ai/data/courses.json`(182 실과목), `passport-ai/cache/202312345.json`(데모 v2).
- 계약/목업: `docs/handoff_20260713_B안확정/27_*.md`, `mocks/reco.*.json`.
