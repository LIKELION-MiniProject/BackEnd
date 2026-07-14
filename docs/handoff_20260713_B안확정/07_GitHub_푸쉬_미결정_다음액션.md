# 07. GitHub 푸쉬 명령어 · 미결 결정 · 다음 액션

## A. GitHub push (리모트 이미 연결됨 확인)

> 리모트 `origin` = `github.com/LIKELION-MiniProject/BackEnd.git` 연결 확인. push는 **원석이 Windows(Git Bash/IntelliJ)에서 직접**(샌드박스는 git 쓰기·네트워크 차단).
> 현재: 커밋 1개(초기)만 있고 **`passport-ai/` 전체 untracked**, Spring 파일 8개 수정 미스테이징. 이번 세션에서 `requirements.txt`(1.38.0 고정)·`passport-ai/.gitignore`(데모캐시 예외) 수정됨.

### 1) 신원 설정 (최초 1회)
```bash
git config --global user.name "본인이름"
git config --global user.email "meotjini1201@gmail.com"
```

### 2) 스테이징
```bash
cd C:/Users/송원석/Documents/LikeLion_MiniProject_Team_3
git add -A
```

### 3) 시크릿 최종 검증 (push 전 필수)
```bash
git check-ignore passport-ai/.env          # → 경로 출력되면 정상(무시됨)
git status | grep -i "\.env$"               # → 아무것도 안 나와야 정상
git diff --cached | grep -Ei "AQ\.|AIza"    # → 아무것도 안 나와야 정상(키 없음)
```
- `git status` 스테이징 목록에 `.env`가 보이면 **절대 커밋 금지**.

### 4) 커밋 (기능 블록 단위 권장)
```bash
git commit -m "feat: passport-ai AI 추천 모듈 추가 (후보축소·성향분석·Gemini·폴백·캐시)" -- passport-ai/ docs/
git commit -am "fix: Spring 공통 응답/에러 처리 및 Course 도메인 정리"
```
> 한 번에: `git add -A && git commit -m "..."` 도 가능.

### 5) push (첫 seed push는 main 1회 예외 — 7/12 회의 확정)
```bash
git fetch origin        # 인증·권한 정상이면 에러 없음 (403이면 collaborator 초대 확인)
git push -u origin main
```

### 6) 확인
`github.com/LIKELION-MiniProject/BackEnd`에서 `passport-ai/`·`docs/`·커밋 노출 확인.

### 인증이 막히면
- IntelliJ: `Settings > Version Control > GitHub > + > Log In with GitHub`(브라우저).
- Git Bash: `git push` 시 Credential Manager가 브라우저 로그인 창을 띄움.
- PAT: `github.com > Settings > Developer settings > Tokens(classic) > repo 스코프` 생성, 조직 SSO면 "Configure SSO → Authorize", push 시 비번 자리에 토큰.

## B. 미결 결정 (원석/팀 판단 대기)

| # | 결정 | 권장/메모 |
|---|---|---|
| 1 | 추천 재호출 시 재생성 vs 유지 | 기본: POST=재생성, GET=캐시 |
| 2 | 성적 입력이 과목별인지 | 인서 확인(결합 A 전제) |
| 3 | 상단 통계 카드 넣을지 | 진단/대시보드 API 필요 — 일정 따라 선택 |
| 4 | 방향 탭 라벨 최종 문구 | 신영 PNG 기준 확정 |
| 5 | 429/503 표면화 방식 | 폴백 + "기본 추천" 배지 |
| 6 | 매일 21시 Slack 3줄 보고 | 미합의 |
| 7 | Gemini 키 재발급 시점 | 7/16 리허설 전 |

## C. 다음 액션 체크리스트

### 즉시 (원석)
- [ ] GitHub 커밋·push (위 A) — **최대 리스크, 4일째 이월**
- [ ] `requirements.txt` 고정은 완료 → 팀원 `pip install -r requirements.txt` 안내
- [ ] AUTH 로그인 API 명세 확정 → 인서 전달(연동 선행조건)

### 마감(7/17) 전 필수
- [ ] passport-ai directions 확장 (03 문서 프롬프트)
- [ ] Spring recommend 패키지 + CORS(5173·5174)
- [ ] FE 목업 JSON 3종 전달 + 인서 AI분석 페이지 연동(04·05·06)
- [ ] 노션 RECO-001/002 v2 갱신(reasons 3개 + directions + caution)
- [ ] E2E curl(+타인 접근 거부)

### 게이트·리허설
- [ ] 7/15 21:00 게이트: 화면에서 로그인→진단→AI추천 1회 통과
- [ ] 7/16 리허설 3회(1회 인터넷 차단 → 폴백 검증) + 키 재발급 후 새 키로
- [ ] 7/17 20:00 제출(2시간 버퍼) + README 3줄

### 발표 팁
- 데모 학생(202312345)은 전공 후보가 없어 `MAJOR_DEEP` 방향이 얇아짐 → **전공 부족 두 번째 데모 계정(박서은 페르소나형)** 시딩하면 5탭이 다 보이는 화면으로 발표 가능(7/16 시딩 때 반영).
- 스토리: "AI 변동성을 전략 선택으로 전환" + "의존성 고정으로 재현성 확보".
