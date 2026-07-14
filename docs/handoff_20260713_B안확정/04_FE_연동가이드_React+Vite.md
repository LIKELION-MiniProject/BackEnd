# 04. FE 연동 가이드 (React + Vite) — 인서용

## 0) 한 줄 원칙

**방향 탭 전환은 프론트에서 처리한다. 백엔드를 다시 부르지 않는다.** 추천 요청 1번이면 응답 안에 5방향이 각각의 과목까지 통째로 들어온다. 탭 누르면 그 배열에서 골라 카드만 다시 그린다.

## 1) 왜 이렇게 하나 (인서가 알아야 할 배경)

- AI(Gemini)는 최초 1회만 호출돼 5방향 결과를 모두 만들어 캐시에 저장한다.
- 그래서 탭 전환은 서버 왕복이 필요 없다 → **즉각 전환 + 항상 동일 + 발표 중 네트워크 사고 없음**.
- 결과적으로 인서는 "방향 선택값을 서버로 보내는" 코드를 짤 필요가 없다(작업량 감소).

## 2) 작업 순서 (why/how)

| 순서 | 작업 | 왜 | 어떻게 |
|---|---|---|---|
| 1 | 목업 JSON 3종 붙여넣기 | 서버 없이 화면 먼저 | BE가 준 live/cache/fallback JSON을 `src/mocks/`에 둠 |
| 2 | AI분석 페이지 레이아웃 | 디자인 시안 반영 | 06 문서 UI 스펙대로: 사이드바·헤더배지·(통계행)·방향탭·카드5 |
| 3 | directions[] → 탭 렌더 | 방향 노출 | 배열 순서·개수대로 탭, 기본선택=defaultDirectionId |
| 4 | 선택 방향 → 카드 렌더 | 방향별 추천 | 선택 directionId의 recommendations 5개를 카드로 |
| 5 | caution 빨간 글씨 | 편향 경고 | caution!=null이면 라벨 아래 빨간 텍스트 |
| 6 | 상태 4종 | 로딩·에러·폴백 | 정상 / 'source==fallback'이면 "기본 추천" 배지 / 로딩 / 빈·에러 |
| 7 | 실서버 연동 | 목업→실데이터 | GET `/api/v1/profiles/{id}/recommendations`, Bearer 토큰 |

## 3) 응답 소비 방법 (핵심 3가지)

1. **탭 = directions 배열 그대로.** 탭 라벨=`name`, 설명=`description`. 배열 길이만큼만 렌더(빈 탭 없음).
2. **기본 선택 = `defaultDirectionId`.** 첫 진입 시 이 방향 탭이 활성.
3. **`caution` 처리.** 각 방향 객체의 `caution`이 `null`이면 아무것도 안 그리고, 문자열이면 **탭/라벨 아래 빨간 글씨**로 그 문구를 그대로 출력. 절대 프론트에서 문구를 하드코딩하지 않는다(서버가 준다).

각 과목 카드: `courseName`, `credit`, `reasons[3]`(3줄, 색상 칩은 06 문서 색 매핑).

## 4) React + Vite 구조 제안

```
src/
  api/recommendations.js      // GET/POST 래퍼 (axios/fetch + Authorization)
  mocks/
    reco.live.json
    reco.cache.json
    reco.fallback.json
  pages/AiAnalysis.jsx        // AI 분석 페이지 (탭 상태 useState)
  components/
    DirectionTabs.jsx         // directions -> 탭, caution 빨간글씨
    RecoCard.jsx              // 과목 카드 (reasons 3칩)
    StatSummary.jsx           // (선택) 상단 통계 카드
    Badge.jsx                 // "기본 추천" 배지 (source==fallback)
    States.jsx                // 로딩/빈/에러
```

상태 관리는 페이지 로컬 `useState`로 충분(선택 directionId 하나). 전역 스토어 불필요.

## 5) API 호출 (Vite dev)

- Base URL: `/api/v1` (Vite 프록시 또는 절대경로). 토큰: `Authorization: Bearer <JWT>`.
- **CORS**: BE가 origin `http://localhost:5173`·`5174` 둘 다 허용하도록 CorsConfig 넣음 → 인서 dev 포트가 무엇이든 동작.
- 예:
```js
// api/recommendations.js
export const getRecommendations = (profileId, token) =>
  fetch(`/api/v1/profiles/${profileId}/recommendations`, {
    headers: { Authorization: `Bearer ${token}` }
  }).then(r => r.json());   // -> { success, data, message }
```
- 개발 초기엔 목업 JSON을 import해서 화면부터 완성 → 서버 준비되면 교체.

## 6) 목업으로 개발 시작 (서버 없이)

BE가 전달하는 `reco.live.json` 등을 그대로 import:
```js
import mock from "../mocks/reco.live.json";
const data = mock.data;                 // { recommendations, directions, defaultDirectionId, source }
const [dir, setDir] = useState(data.defaultDirectionId);
const active = data.directions.find(d => d.directionId === dir);
```
`active.recommendations`를 카드로, `active.caution`이 있으면 빨간 글씨.

## 7) 상태 4종 처리

- **정상**: directions 렌더.
- **기본 추천 배지**: `data.source === "fallback"`이면 상단에 "기본 추천"(AI 실패 시 규칙 추천) 배지.
- **로딩**: 요청 중 스켈레톤/스피너.
- **빈·에러**: `success===false` 또는 directions 비었을 때 안내 + 재시도.

## 8) 디자인 시안 참고 (06 문서에 상세)

신영 PNG 기준: 좌측 고정 사이드바(Pass Port 로고 + 홈/성적/AI분석/마이), 헤더 "AI 분석" + 우상단 "AI 추천 완료" 초록 배지, (일부 변형) 상단 4개 통계 카드, 방향 탭(3~5), 5장 과목 카드(번호·과목명·학점/구분·이유 3칩: 파랑·파랑·노랑). 탭 라벨 문구는 디자인 PNG를 최종 기준으로.
