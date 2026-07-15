# 73. MASTER 인계 ④ — 프론트엔드 · 연동 · 디자인

> FE 현황 + 실 API 연동 순서(무엇을·왜·어떻게) + 신영 디자인 요구. 상세: 54(개발자용)·55(비전공자용)·29b(디자인).
> ⚠️ FE 코드는 **인서의 별도 레포**(BE와 다름). BE는 GitHub main 정본.

---

## 1. FE 현황 (React 19 + Vite 8 + Router 7)
| 화면 | 상태 |
|---|---|
| 로그인/회원가입 | ✅ 폼 |
| 온보딩 라우팅 | ✅ (⚠️ 프로필 생성 화면만 미구현) |
| 졸업요건 입력(사진1) | ✅ 실 API 연동 |
| 수강내역 입력(사진2) | ✅ 정합(한글↔enum, year/semester, retake, A0/B0) |
| 성적 대시보드(사진3) | 🟡 `semesterGpa`만 반영 필요 |
| AI 분석(기본+비교 2방향) | ✅ 화면 (라이브 브릿지 반영 연동 필요) |
- 토큰 localStorage + `Authorization: Bearer`, 401→로그인. dev는 `/api→localhost:8080` 프록시. 실데이터 목업 3종 `mocks/`.

## 2. 연동이란 (요약)
화면(프론트)을 진짜 백엔드에 연결해 실제 데이터가 흐르게 하는 것. **연결 코드는 인서가 작성**, 원석은 백엔드 보장. (쉬운 설명 전체는 55 문서.)

## 3. 연동 순서 — 무엇을·왜·어떻게
| 순서 | 무엇을 | 왜 | 어떻게 |
|---|---|---|---|
| ① | API 클라이언트 | 토큰 자동주입·응답봉투 처리 | `client.js` fetch 래퍼(Authorization, {success,data} 해제, 401→로그인) |
| ② | 로그인→온보딩 분기 | 진입·프로필 유무 판단 | 토큰 저장 → `GET /auth/me` → profileId null이면 프로필 생성으로 |
| ③ | 프로필 생성 화면(신규) | 신규 유저 선행조건(미구현) | `POST /profiles{deptCode:"BIGDATA_AI",studentId,admissionYear,name}` 폼 |
| ④ | 요건·수강 입력 실 API | "내 DB" 생성 | `PUT /requirements`·`POST /courses`. 목업/내장 임시 과목명 제거 |
| ⑤ | 진단/대시보드 | 계산 결과 표시 | GET 연결. **`dash.gpa`→`dash.semesterGpa`** 한 줄 수정 |
| ⑥ | ⭐AI 분석 | 핵심 기능 | 아래 §4 |
| ⑦ | 상태 4종 + E2E | 게이트 통과 | 로딩/정상/기본추천/빈·에러 + 전 흐름 1회 관통 |

## 4. AI 분석 연동 (라이브 브릿지 — 동작이 특별)
- **"분석하기"(POST)** = 성적·요건·자격증 변경 시 **실시간 재분석(5~6초)**, 무변경 시 즉시 캐시. → **로딩 UI 필수.**
- **재방문(GET)** = 마지막 결과 조회(AI 미호출).
- **방향 탭/비교** = 응답 `directions[]`에서 프론트가 전환(서버 재호출 없음).
- 렌더: 탭=`directions[]`·기본=`defaultDirectionId`·`caution`(빨간글씨,null 숨김)·`thin`(얇은방향 안내)·카드=`courseName`+`category·credit학점`+`reasons[0/1/2]`(성향/특성/졸업기여)·`source==fallback`→"기본 추천" 배지.
```js
const data = await api(`/profiles/${pid}/recommendations`, { method:"POST", token }); // ~6초, 로딩표시
setReco(data); setActiveDir(data.defaultDirectionId);
const active = data.directions.find(d => d.directionId === activeDir); // 탭전환은 재호출 없이
```
- ⚠️ **실데이터 라이브는 현재 방향 2개(FAST_GRAD·MAJOR_DEEP)만** — 핵심교양 영역매핑 부재. 데모 캐시는 4방향. 화면은 `directions[]` 길이만큼 렌더하면 자동 대응.

## 5. 상태 4종 (신영 디자인 29b)
| 상태 | 조건 | 표시 |
|---|---|---|
| 로딩 | 요청 중(특히 AI 6초) | 스켈레톤/스피너 |
| 정상 | source live/cache | 초록 "AI 분석 완료" |
| 기본 추천 | source==fallback | 앰버 "기본 추천" 배지 |
| 빈·에러 | 실패/빈배열 | 안내 + [다시 시도] |

## 6. 신영 디자인 요구(29·29b) — 만들 것
- 스켈레톤(카드/표/그래프/탭+카드), 버튼 내부 스피너, 토스트(성공·실패·주의), 에러 카드([다시시도]), 빈 카드, **AI분석 전용**(앰버 "기본추천" 배지·thin 안내박스·빨간 caution), 수강 붙여넣기 진행표시, 로그인/회원가입 인라인 빨간 문구.
- 화면별 로딩·에러 배치는 29b(비전공자용 쉬운 버전) 참고. 탭 라벨·경고 톤·통계카드 포함 여부는 신영 확정.

## 7. FE↔BE 정합 (유일한 불일치)
- ✅ 요건·수강·대시보드 categories·AI 통계카드 키 일치.
- 🔴 학기 GPA만: BE `semesterGpa` ↔ FE `dash.gpa` → FE 1줄 수정.

## 8. 인서가 원석에게 확인할 것
- 상단 통계카드 3종(성적분석%·졸업요건충족률%·선수과목)이 어디서 값을 받는지(진단/GPA/대시보드 API vs 하드코딩) — 추천 API엔 없는 값. 확인 후 원석 보고.
