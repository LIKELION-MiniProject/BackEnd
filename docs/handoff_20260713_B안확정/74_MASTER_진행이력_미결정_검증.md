# 74. MASTER 인계 ⑤ — 진행 이력 · 미결정 · 다음 작업 · 검증법

> 이 세션 전체의 시간순 요약 + 팀 결정 필요 항목 + 다음 할 일 + 검증(재현) 방법. (다른 계정이 "왜 이렇게 됐나"를 이해하게)

---

## 1. 진행 이력 (시간순, 전체)
1. **초기 맥락(00~07 인계)**: PassPort = 졸업진단(규칙)+AI 과목추천(B안). 방향성 선택 B안 팀 채택.
2. **STEP 0~1**: 레포 연결·현황 확인. `requirements.txt` `google-genai==1.38.0` 고정(1.39+ 클라이언트 조기종료 버그). `.gitignore` 데모캐시 예외.
3. **문서 세트(00~08 B안확정)** 작성: 아키텍처·계약 v2·BE설계·프롬프트·FE가이드·UI·GitHub.
4. **STEP B**: passport-ai directions 확장(directions.py 신설 + core 7파일). 5방향·thin·caution·category. 규칙 로직 실데이터 검증.
5. **Fable 검토 6건 → 계약 v2.1**: thin 정책·reasons 순서·caution(thin이면 null)·라벨·category·cache=live·배지=fallback만. 실데이터 목업 3종(mocks/) 생성.
6. **STEP C(클로드코드)**: Spring recommendation 패키지 + CorsConfig. 검토·검증(문서 33).
7. **인증/DB 확인**: 이미 완비(H2, JWT, signup/login/me). AUTH 명세 확정.
8. **v4(20~25, 원준 계정)**: FE 완성 + requirement 유저저장(STEP B/C) + 수강 스키마 보정 + 대시보드 확장(STEP D). 검증: 연결 레포에 전부 실재, 미커밋.
9. **신영 요구서**: 에러/로딩 배치(29) + 비전공자용 쉬운 버전(29b, 성인 톤).
10. **검토 3종(50~52)**: 현황·BE·FE 종합.
11. **STEP E**: 인증 5분야 진단 연결(대상→완료 규칙, AND) + H2 파일영속(MERGE 시드). 호스트 검증(fulfilled false→true 토글, PowerShell UTF-8 픽스). 문서 43.
12. **Gemini 교체 + 라이브 브릿지(53, 다른 세션)**: 3.5-flash→3.1-flash-lite(.env). Spring↔passport-ai 브릿지(SHA-256 지문, POST=변경시 재분석/GET=캐시). E2E 검증. `cache/*.fp` gitignore.
13. **GitHub 병합**: push 거부(리모트 main=템플릿만, 별개 히스토리) → `feat/backend-full`로 push → **PR #1로 main 병합 완료**.
14. **FE 가이드(54 개발자용·55 비전공자용)** + 인계 문서(60·61·70~74).

## 2. 검증된 사실 (근거)
- STEP B 규칙: 데모 학생 4방향(FAST_GRAD/GRADE_SAFE(thin)/EXAM_SOLO(caution)/TEAM_ACTIVE(thin)), MAJOR_DEEP 제외 — 실데이터 재현.
- STEP C: Spring recommend 컴파일·소유권·DTO 정합·결정성 검토 통과.
- STEP E: bootRun 컴파일·기동, `graduationCertification.fulfilled` 대상→완료 시 false→true.
- 라이브 브릿지: 성적·자격증·요건 변경 시에만 재분석(각 5.8~6.4초), 무변경 시 캐시 — 실측(53 §5).
- GitHub main 병합 완료(코드+템플릿, 충돌 없음).
- 푸쉬 안전: `.env`·`data/`·`*.fp`·`*.mv.db` gitignore 무시, 키 노출 없음.

## 3. GitHub 상태
- 리모트 `origin` = `github.com/LIKELION-MiniProject/BackEnd.git`. `main`에 PR #1 병합 완료.
- 로컬 정리: `git checkout main; git pull origin main`. (역할 끝난 feat 브랜치 정리 선택)
- 이후 브랜치 전략: `feat/`·`fix/` + PR, **main 직접 push 금지**(첫 seed 예외는 소진).

## 4. 미결정 (원석/팀)
| # | 결정 | 메모 |
|---|---|---|
| 1 | STEP E 판정: 기존인증 AND 5분야 vs 5분야 단독 | DiagnosisService eligible 1줄 |
| 2 | 라이브 실데이터 방향 2개 한계 | 핵심교양 과목↔영역 매핑 데이터 확보 or 시연은 데모 |
| 3 | 시연 계정(데모 4방향 vs 실데이터 2방향) | 발표 스토리 |
| 4 | Gemini 키 재발급 | 7/16 전(노출됨) |
| 5 | FE semesterGpa·프로필 온보딩 화면 | FE 즉시 |
| 6 | 통계카드 3종 출처 | 인서 확인→보고 |
| 7 | 브릿지 5~6초 대기 | 로딩 UI + 리허설 호출량 |
| 8 | DB 영속 유지 vs 인메모리 | 현재 파일 영속 |

## 5. 다음 작업 (우선순위)
1. 🔴 **FE 연동**(인서) — 73/54/55 순서. semesterGpa·프로필 온보딩 먼저.
2. ⚠️ Gemini 키 재발급(7/16 전).
3. ⚠️ STEP E 판정·시연 방향 결정.
4. 🟡 신영 디자인(29b) 반영.
5. 데모 시딩(발표 재현). (전공 부족 2번째 계정으로 5방향 다 보이게 — 선택)
6. 7/15 게이트 → 7/16 리허설(1회 네트워크 차단 폴백) → 7/17 20:00 제출(README) → 7/20 발표.

## 6. 검증(재현) 방법 — 서버 켜고 curl (호스트)
```powershell
# 서버 (환경변수 필요)
$env:JWT_SECRET="dev-secret-32bytes-이상-000"; $env:JWT_EXPIRATION="3600000"; .\gradlew bootRun
# 새 터미널
$login = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/auth/login" -ContentType "application/json" -Body ([Text.Encoding]::UTF8.GetBytes((@{email="demo1@passport.ac.kr";password="Passport1!"}|ConvertTo-Json)))
$h = @{ Authorization = "Bearer $($login.data.accessToken)" }
$pf = (Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/me" -Headers $h).data.profileId
# 성적 추가 → POST 재분석(generatedAt 바뀜) / 무변경 → 캐시 유지 (라이브 브릿지)
```
- 데모 계정: demo1@passport.ac.kr / demo2@passport.ac.kr, 비번 `Passport1!`.

### PowerShell / 환경 팁 (실전에서 겪은 것)
- 한글 바디는 `[Text.Encoding]::UTF8.GetBytes($json)`로 보내기(안 그러면 enum 파싱 500).
- 예약변수 금지: `$pid`(프로세스ID)·`$host`·`$error` → `$profileId` 등 다른 이름.
- 새 터미널은 변수 없음 → 로그인부터 다시.
- `curl`은 PowerShell에선 Invoke-WebRequest 별칭 → `curl.exe` 쓰거나 Invoke-RestMethod.
- `gradlew bootRun`의 `80% EXECUTING`은 정상(서버 실행 중). 종료는 Ctrl+C(Y) 또는 포트 종료(`Get-NetTCPConnection -LocalPort 8080 ... Stop-Process`).
- git `index.lock`/`config.lock` 뜨면 그 잠금파일 삭제(일반 파일, -Force 불필요).
- 한글 콘솔: `[Console]::OutputEncoding=[Text.Encoding]::UTF8`(응답 한글 깨짐은 표시 문제, 데이터는 정상).
- 마운트(코워크)는 기존 파일 덮어쓰기 미반영 → 검토는 Read 도구(호스트).

## 7. 추가 첨부 권장 (새 코워크 시작 시)
- **`docs/handoff_20260713_B안확정/` 폴더 전체**(마스터 70~74 + 세부 + `mocks/`).
- **연결 레포 전체** 또는 GitHub `main` clone/pull(코드 정본).
- 핵심 함께: `71`(계약·규칙)·`72`(BE)·`73`(FE)·`53`(브릿지)·`27`.
