# 60. 핸드오프 INDEX v5 — 40~42 인계 이후 ~ GitHub 병합까지 (2026-07-14)

> **용도**: 다른 계정 코워크에서 이어받기 위한 진입 문서. **범위 = 문서 40~42(v4 인계) 시점부터 이번 세션(STEP E + Gemini 교체 + 라이브 브릿지 + GitHub 병합)까지.**
> **짝 문서**: `61_다음작업_FE연동_재개가이드.md`.
> **상위 유효 문서**: 이 폴더 27(계약 v2.1)·43(STEP E)·44(Gemini)·50~55(검토·FE)·53(브릿지). 상충 시 이 v5(60~61)가 우선.
> 표기: ✅ 완료·검증 / 🔴 미완 / ⚠️ 주의

---

## 1. 이 구간 한 줄 요약
40~42(v4)를 인계받아 **실제 코드 상태를 전면 검증**했고, **STEP E(인증 5분야 진단 연결 + H2 파일영속)**를 구현·호스트 검증했다. 이어 다른 세션이 **Gemini 3.5-flash→3.1-flash-lite 교체 + Spring↔passport-ai 라이브 브릿지**(데이터 변경 시에만 실시간 재분석)를 구현했고, 마지막으로 **그동안의 전 작업을 GitHub `main`에 PR로 병합 완료**했다. 남은 핵심은 **프론트엔드 연동**.

## 2. 이 구간에 실제로 한 일 (시간순)
1. **v4(40~42) 검증** — 연결 레포에 recommendation·requirement·dashboard·course·cors·passport-ai 전부 실재 확인. 전부 미커밋이던 상태.
2. **STEP E 구현·검증**(문서 43):
   - DB 영속: `application.yml` H2 `mem`→`file`(`jdbc:h2:file:./data/passportdb;AUTO_SERVER=TRUE`), `data.sql` INSERT→**MERGE**(재기동 안전), `.gitignore`에 `/data/`·`*.mv.db` 추가.
   - 인증 5분야 진단 연결: `EffectiveRequirement.certificationTargets` + `DiagnosisResponse.graduationCertification` + `DiagnosisService.buildGraduationCertification()`. **규칙 = 유저가 '대상(TARGET)'으로 표시한 분야가 전부 '완료(DONE)'여야 충족**(대학 규칙 창작 안 함). 기존 판정에 **AND**로 추가(하위호환).
   - 호스트 검증: `gradlew bootRun` 기동, `PUT /requirements`(PowerShell UTF-8 바이트로 전송) 성공, `GET /diagnosis`의 `graduationCertification.fulfilled`가 대상→완료 변경 시 **false→true 토글 확인**.
3. **Gemini 모델 교체 + 라이브 브릿지**(문서 53, 다른 세션):
   - `.env`의 `GEMINI_MODEL`을 `gemini-3.1-flash-lite`로(하루 ~150회). 코드 무변경(env로 주입). `batch.py demo` `source=live` 확인.
   - **라이브 브릿지 신규**: `passport-ai/bridge.py`(stdin JSON→recommend→stdout), `PassportAiBridge.java`(ProcessBuilder subprocess). `RecommendationService` 전면 수정: **POST**는 유저 데이터(수강+인증+요건+프로필)의 **SHA-256 지문**을 이전과 비교해, 바뀌었으면 라이브 재생성·안 바뀌었으면 캐시. **GET**은 조회 전용. `cache/{studentId}.fp`에 지문 저장. `application.yml`에 `python-command`·`dir` 추가.
   - E2E: 성적·자격증·졸업요건 변경 시에만 재분석(각 5.8~6.4초), 무변경 시 캐시 유지 실측 확인. 테스트 데이터 원복.
4. **GitHub 병합**:
   - `cache/*.fp`를 `.gitignore`에 추가(로컬 지문 커밋 방지).
   - 전 작업 커밋 → `git push origin main` 거부됨(리모트 main엔 README/이슈·PR 템플릿만 있고 **공통 조상 없는 별개 히스토리**).
   - **작업을 `feat/backend-full` 브랜치로 push**(안전 확보) → **PR #1로 `main`에 병합 완료**(리모트 템플릿 + 백엔드 코드 결합).
   - 현재 `origin/main` = 병합 완료 상태.

## 3. 현재 상태표
| 영역 | 상태 |
|---|---|
| GitHub `main` 병합 | ✅ 완료(PR #1). 그동안 전 작업 반영 |
| STEP E(5분야 진단·DB 영속) | ✅ 구현·호스트 검증 |
| Gemini 3.1-flash-lite | ✅ 교체·live 확인(.env, 로컬) |
| 라이브 브릿지(변경 시 재분석) | ✅ 구현·E2E 검증 |
| 인증/도메인/추천/요건/대시보드 | ✅ (누적) |
| **FE 연동** | 🔴 다음 핵심(문서 54·55) |
| FE `semesterGpa` 반영 | 🔴 한 줄 |
| 프로필 최초 생성 화면 | 🔴 미구현 |
| Gemini 키 재발급 | ⚠️ 7/16 전 |
| 라이브 실데이터 방향 2개 한계 | ⚠️ 핵심교양 영역매핑 부재 |
| STEP E 판정(AND vs 5분야 단독) | ⚠️ 원석 결정 |

## 4. 검증된 사실(이 구간)
- STEP E 자바 변경 **컴파일·기동 통과**(bootRun), `graduationCertification` 토글 확인.
- 라이브 브릿지 **데이터 변경 시에만 재분석** 실측(문서 53 §5 표).
- GitHub `main`에 **PR 병합 완료**(코드+템플릿, 충돌 없음 — 파일 겹침 없음).
- 푸쉬 안전: `.env`·`data/`·`*.fp`·`*.mv.db` 전부 gitignore 무시, 키 노출 없음.

## 5. 파일 지도 (이 구간 신규/변경)
- BE 자바: `recommendation/service/PassportAiBridge.java`(신규)·`RecommendationService.java`(전면수정)·`global/config/CorsConfig.java`, `diagnosis/service/DiagnosisService.java`·`dto/DiagnosisResponse.java`, `requirement/domain/EffectiveRequirement.java`, `resources/application.yml`·`data.sql`.
- passport-ai: `bridge.py`(신규), `.env`(모델, 로컬)·`.env.example`, `cache/202312345.json`(재생성), `.gitignore`.
- 문서: `43`(STEP E)·`44`(Gemini)·`50~52`(검토 3종)·`53`(브릿지)·`54`(FE 개발자용)·`55`(FE 비전공자용)·이 세트 `60~61`.

## 6. 재개 첫 액션 → `61` 문서
1. 이 문서 + `61` + `54/55`(FE) + `27`(계약) 읽기.
2. 로컬 main 동기화: `git checkout main; git pull origin main`.
3. **FE 연동**(인서 주도) 시작 — `54`(개발자용)/`55`(쉬운) 순서대로.
4. 원석 결정 대기: STEP E 판정 방식, 시연 방향(데모 4방향 vs 실데이터 2방향), Gemini 키 재발급.

## 7. 환경 제약(반드시)
- 코워크 마운트는 **기존 파일 덮어쓰기·삭제 미반영**(신규 파일만 동기화) → 기존 파일 검토는 Read 도구(호스트).
- **git 쓰기·push·`gradlew test`는 원석 Windows 네이티브.** 검증은 bootRun+curl. `.env` 시크릿 커밋 금지.
- PowerShell 팁: 한글 바디는 `[Text.Encoding]::UTF8.GetBytes()`, 예약변수(`$pid` 등) 금지, 새 터미널은 로그인부터, `bootRun` 80%는 정상(서버 실행중), 잠금파일(index.lock/config.lock)은 삭제로 해결.
