# 89 — FE 작업 지시서 (수강 입력 · AI 분석 연동)

> **대상** : 인서님 · **작성** : 원석 + Claude · **날짜** : 2026-07-15 저녁
> **배경** : 성적 화면에서 수강 내역을 아무리 고쳐도 **서버에 저장되지 않아** 홈·진단·AI분석이 안 바뀐다. 원인은 **FE가 쓰기 API를 호출하지 않기 때문**이다.
> **관련** : [88 연동 가이드](88_FE_EC2_연동_실행가이드.md) (먼저 `.env.local` 설정이 끝나 있어야 함)

---

## 0. 먼저 — 지금 무슨 일이 벌어지고 있나

원석이 서버를 직접 검사한 결과다.

| 확인 | 결과 |
|---|---|
| 서버에 저장된 수강 이력 | **16과목** (원석이 터미널로 넣은 것) |
| 서버 `courses` 테이블 INSERT 총 횟수 | **16건** — 그 16과목뿐 |
| **FE에서 입력한 뒤 INSERT 횟수** | **0건** 🔴 |
| 화면에 보이던 `과학기술과나눔` | 서버에 **없음** |

```
[성적 화면]  서버에서 GET으로 읽어옴 → 화면에서 수정 → ❌ 서버로 안 보냄 → 로컬 메모리에만 존재
[홈 배너]    서버에서 GET으로 읽음   → 서버는 안 바뀌었으니 → 그대로 39/130, 30%
```

**두 화면이 서로 다른 데이터를 보고 있다.** 성적 화면은 "로컬에서 고친 값", 홈은 "서버의 진짜 값".

### ✅ 백엔드는 문제가 없다 (검증 완료)

원석이 서버에서 직접 테스트했다:

| 테스트 | 결과 |
|---|---|
| `POST` 과목 추가 | id=17 생성 ✅ |
| `PUT` 성적 수정 (C+ → A+) | A+로 반영 ✅ |
| `GET` 재조회 | 서버에 저장 확인 ✅ |
| **대시보드 즉시 반영** | 39 → **42학점**, 30% → **32%** ✅ |
| `DELETE` 삭제 | 원상복구 ✅ |

**즉 API를 부르기만 하면 전부 동작한다. 백엔드 수정은 없다 → API 계약도 안 바뀐다.**

---

## 1. 오늘 할 작업 5개 (요약)

| # | 작업 | 난이도 |
|---|---|---|
| 1 | 수강 입력 화면 진입 시 **서버에서 내 수강 내역 불러오기** (`GET`) | 쉬움 |
| 2 | 추가·수정·삭제를 **서버에 저장** (`POST`/`PUT`/`DELETE`) | 보통 |
| 3 | 저장 후 **홈·진단 화면 갱신** | 쉬움 |
| 4 | **"AI 분석" 버튼** → `POST` + 로딩 표시 | 보통 |
| 5 | **"졸업 요건 입력하기" 버튼 숨기기** | 쉬움 |

---

## 2. 준비 — 프로그램 열기

### 2-1. VS Code 열기

1. **VS Code** 실행
2. 메뉴 **`파일(File)` → `폴더 열기(Open Folder)`**
3. **프론트엔드 저장소 폴더** 선택 (안에 `package.json`이 있는 폴더)

### 2-2. `.env.local` 확인 (88 문서에서 이미 했다면 건너뜀)

최상위에 **`.env.local`** 파일이 있고 내용이 아래와 같은지 확인:
```
VITE_API_BASE_URL=http://15.164.84.176:8080/api/v1
```
없으면 만들고 저장 → **터미널에서 `Ctrl+C` 후 `npm run dev` 재시작** (환경파일은 재시작해야 읽힌다)

### 2-3. Chrome 열고 개발자도구 준비

1. **Chrome** → `http://localhost:5173` (🔴 반드시 `http`)
2. **`F12`** → **`Network`** 탭을 **열어둔 채로** 작업 (이게 성공/실패 판별 기준이다)

---

## 3. 【작업 1】 수강 내역 불러오기 (`GET`)

> **목표** : 성적 화면에 들어가면 **내 실제 데이터 16과목**이 떠 있어야 한다.

### 3-1. 고칠 파일 찾기

VS Code에서 **`Ctrl + Shift + F`** (전체 검색) 후 아래를 차례로 검색:

| 검색어 | 찾는 것 |
|---|---|
| `수강 내역` | 성적 화면 컴포넌트 |
| `수강` | 관련 파일 전체 |
| `courses` | 이미 API를 부르는 곳이 있는지 |

→ 보통 `src/pages/Grade*.jsx`, `src/pages/Score*.jsx`, `src/components/Course*.jsx` 같은 파일이다.

### 3-2. 확인할 것

파일을 열고 **수강 목록 데이터가 어디서 오는지** 본다:

```jsx
// 🔴 이런 게 있으면 = 하드코딩된 가짜 데이터 (이게 문제)
const [courses, setCourses] = useState([
  { name: '작문과화법', credit: 2, grade: 'A0' },
  { name: '과학기술과나눔', credit: 2, grade: 'A+' },
]);

// ✅ 이렇게 바꿔야 한다 = 서버에서 가져오기
const [courses, setCourses] = useState([]);
useEffect(() => {
  fetch(`${import.meta.env.VITE_API_BASE_URL}/profiles/${profileId}/courses`, {
    headers: { Authorization: `Bearer ${token}` },
  })
    .then((r) => r.json())
    .then((res) => setCourses(res.data));   // ← 응답이 { success, data, message } 구조
}, [profileId]);
```

### 3-3. `profileId`는 어디서 오나

로그인 후 **`GET /auth/me`** 응답에 들어있다:
```json
{"success":true,"data":{"userId":3,"email":"...","nickname":"송원석","profileId":1},"message":null}
```
→ `res.data.profileId` 를 저장해두고 쓴다. (원석 계정은 **`profileId = 1`**)

### 3-4. 성공 판별

성적 화면 진입 → Network 탭에 **`courses` 요청이 뜨고**, 화면에 **16과목**(2024-1 7개 + 2026-1 9개)이 보이면 성공.

---

## 4. 【작업 2】 추가·수정·삭제를 서버에 저장

> **목표** : 화면에서 고친 게 **서버에 저장**돼야 한다. **이게 핵심 작업이다.**

### 4-1. API 3개

| 동작 | 메서드 · 경로 |
|---|---|
| **추가** | `POST /profiles/{profileId}/courses` |
| **수정** | `PUT /profiles/{profileId}/courses/{courseId}` |
| **삭제** | `DELETE /profiles/{profileId}/courses/{courseId}` |

### 4-2. 요청 본문 (추가·수정 공통)

```json
{
  "name": "통계기초",
  "credit": 3,
  "category": "MAJOR_REQUIRED",
  "grade": "A",
  "year": 2026,
  "semester": 1,
  "retake": false
}
```

> 🔴 **`PUT`은 전체 필드를 다 보내야 한다.** 바뀐 필드만 보내는 부분 수정이 아니다.

### 4-3. 값 목록 (이 값만 허용)

**`category` (이수 구분)**

| 화면 표시 | 보낼 값 |
|---|---|
| 전공필수 | `MAJOR_REQUIRED` |
| 전공선택 | `MAJOR_ELECTIVE` |
| 교양필수 | `GE_REQUIRED` |
| 교양선택 | `GE_ELECTIVE` |
| 자유선택 | `GENERAL_ELECTIVE` |

> ⚠️ **전공기초는 `MAJOR_REQUIRED`로 보낸다** (팀 합의). 별도 값 없음.

**`grade` (성적)** — 문자열 그대로

```
"A+"  "A"  "B+"  "B"  "C+"  "C"  "D+"  "D"  "F"  "P"  "NP"
```

> ⚠️ 화면에 **`A0`**로 표시하고 있다면 서버에는 **`"A"`**로 보내야 한다. `A0`를 그대로 보내면 **400 에러**가 난다.
> ⚠️ `P`·`NP`는 GPA 계산에서 제외된다(정상 동작).

**`credit` (학점)** — **정수만 가능**

> ⚠️ **0.5 같은 소수점은 못 넣는다**(서버가 `int`). 0.5학점 과목은 **`0`**으로 넣기로 팀 합의했다.

### 4-4. 코드 예시

```jsx
const API = import.meta.env.VITE_API_BASE_URL;
const authHeaders = {
  'Content-Type': 'application/json',
  Authorization: `Bearer ${token}`,
};

// 추가
async function addCourse(course) {
  const res = await fetch(`${API}/profiles/${profileId}/courses`, {
    method: 'POST',
    headers: authHeaders,
    body: JSON.stringify(course),
  });
  if (!res.ok) throw new Error(await res.text());
  await reload();            // ← 저장 후 목록 재조회 (작업 3)
}

// 수정
async function updateCourse(courseId, course) {
  const res = await fetch(`${API}/profiles/${profileId}/courses/${courseId}`, {
    method: 'PUT',
    headers: authHeaders,
    body: JSON.stringify(course),   // 전체 필드
  });
  if (!res.ok) throw new Error(await res.text());
  await reload();
}

// 삭제
async function deleteCourse(courseId) {
  const res = await fetch(`${API}/profiles/${profileId}/courses/${courseId}`, {
    method: 'DELETE',
    headers: authHeaders,
  });
  if (!res.ok) throw new Error(await res.text());
  await reload();
}
```

### 4-5. 성공 판별

과목 하나 수정 → **저장** 클릭 → Network 탭에:
- **`PUT` 요청이 뜨고 초록색(200)** → 성공
- **아무 요청도 안 뜸** → 저장 버튼이 API를 안 부름 (**현재 상태**)
- **빨간색 4xx** → 요청은 갔는데 거절됨 → 응답 본문을 원석에게 공유

---

## 5. 【작업 3】 저장 후 홈·진단 갱신

> **목표** : 과목을 고치면 **홈 배너 숫자가 바로 바뀌어야** 한다.

서버는 이미 즉시 반영한다(원석 테스트에서 39→42학점, 30%→32% 확인). **FE가 다시 안 물어봐서** 화면이 안 바뀌는 것이다.

```jsx
async function reload() {
  // 1) 수강 목록 다시 가져오기
  const c = await fetch(`${API}/profiles/${profileId}/courses`, { headers: authHeaders });
  setCourses((await c.json()).data);

  // 2) 대시보드(홈 배너) 다시 가져오기
  const d = await fetch(`${API}/profiles/${profileId}/dashboard`, { headers: authHeaders });
  setDashboard((await d.json()).data);
}
```

> 홈 화면이 다른 컴포넌트면 — 전역 상태(Context/zustand 등)에 대시보드를 두거나, 홈 진입 시마다 `GET /dashboard`를 호출하면 된다.

**성공 판별** : 과목 추가 → 홈으로 이동 → **총 이수학점·달성도 %가 바뀌어 있으면** 성공.

---

## 6. 【작업 4】 "AI 분석" 버튼

> **목표** : 버튼을 누르면 **Gemini가 5방향으로 재분석**하고 근거까지 보여준다.

### 6-1. 🔴 호출 규약 (PM 확정 — 반드시 지킬 것)

| 화면 동작 | 호출 | 서버가 하는 일 |
|---|---|---|
| **분석 화면 진입(조회)** | **`GET`** `/profiles/{id}/recommendations` | 캐시 반환 (AI 미호출, 즉시) |
| **"AI 분석" 버튼 클릭** | **`POST`** `/profiles/{id}/recommendations` | 수강 이력이 **바뀌었으면 Gemini 재분석**(수 초) / **안 바뀌었으면 캐시 즉시 반환** |

- ❌ **화면 진입 시 `POST`를 부르면 안 된다.** 조회는 반드시 `GET`
- 🔴 **버튼 클릭 시 로딩 표시 필수** — 재분석은 **수 초** 걸린다
- ✅ **변동이 없으면 즉시 응답한다. 로딩이 안 보이는 게 정상이다** (버그 아님)

```jsx
async function onAnalyzeClick() {
  setLoading(true);                     // 🔴 필수
  try {
    const res = await fetch(`${API}/profiles/${profileId}/recommendations`, {
      method: 'POST',
      headers: authHeaders,
    });
    setResult((await res.json()).data);
  } finally {
    setLoading(false);
  }
}
```

### 6-2. 응답 구조

```jsonc
{
  "directions": [                    // 방향 탭 (현재 5개)
    {
      "directionId": "FAST_GRAD",    // FAST_GRAD | MAJOR_DEEP | GRADE_SAFE | EXAM_SOLO | TEAM_ACTIVE
      "name": "졸업요건 집중형",
      "description": "...",
      "thin": false,                 // true → "이 방향에 맞는 과목이 적어요" 박스
      "caution": null,               // 편향 경고 문구 (EXAM_SOLO·TEAM_ACTIVE 전용, null 가능)
      "recommendations": [
        {
          "courseCode": "401348",
          "courseName": "SW/HW플랫폼설계",
          "credit": 3,
          "category": "전공필수",
          "reasons": ["...", "...", "..."]   // 항상 3개, 순서 고정: 성향 → 특성 → 졸업기여
        }
      ]
    }
  ],
  "defaultDirectionId": "FAST_GRAD", // 기본 선택 탭
  "persona": {                       // 학습 성향
    "type": "BALANCED",
    "label": "균형 성장형",
    "description": "...",
    "strategies": ["...", "...", "..."],   // 3개
    "summary": ["...", "...", "..."]       // 3개
  },
  "source": "live"                   // "fallback"일 때만 "기본 추천" 배지 표시
}
```

### 6-3. 지금 서버에 들어있는 실제 값 (화면에서 이게 보여야 정상)

| 방향 탭 | 과목 수 | thin | caution |
|---|---|---|---|
| 졸업요건 집중형 (기본 선택) | 5 | — | — |
| 전공 심화형 | 5 | — | — |
| **학점 안정형** | **1** | **✅ thin 박스** | — |
| **시험·개별평가형** | 5 | — | **✅ "시험 비중이 높아 시험 부담이 클 수 있어요."** |
| **협업·활동형** | **3** | **✅ thin 박스** | — |

persona = **`균형 성장형`(BALANCED)**

---

## 7. 【작업 5】 "졸업 요건 입력하기" 버튼 숨기기

> **결정 완료 (원석)** : **숨긴다.**

### 왜

백엔드는 졸업 요건을 **하드코딩**한다(빅데이터 인공지능 전공 1개). **조회만 가능**하고(`GET /requirements/BIGDATA_AI`), **사용자가 요건을 입력해 저장하는 API가 없다.** 지금 이 버튼을 눌러도 저장될 곳이 없다.

### 무엇을

성적 화면 왼쪽 카드 전체:
```
"아직 등록된 졸업 요건이 없어요.
 우리 학교의 졸업 요건을 입력하고 충족 현황을 한눈에 확인해보세요.
 [ 졸업 요건 입력하기 → ]"
```

**찾는 법**: `Ctrl + Shift + F` → **`졸업 요건 입력하기`** 검색

**조치**: 해당 카드/버튼을 **주석 처리하거나 렌더링에서 제외**한다. (삭제보다 주석 권장 — 발표 후 되살릴 수 있게)

> 💡 그 자리가 비어 보이면, 오른쪽 **수강 내역 카드를 넓히거나** 홈의 진단 요약을 옮겨오는 것도 방법이다. 신영님과 상의.

---

## 8. 최종 검증 체크리스트

`songwonseok1234@g.eulji.ac.kr` 로 로그인해서 순서대로:

| # | 확인 | 기대 결과 |
|---|---|---|
| 1 | 성적 화면 진입 | **16과목**이 뜬다 (2024-1 7개 + 2026-1 9개) |
| 2 | 2026-1학기 필터 | 9과목 / 신청 22학점 / **학기 GPA 3.77** |
| 3 | 과목 성적 하나 수정 → 저장 | Network에 **`PUT` 200** |
| 4 | 새로고침(F5) | **수정한 값이 유지된다** 🔴 (이게 진짜 저장됐다는 증거) |
| 5 | 홈으로 이동 | **총 이수학점·달성도 %가 바뀌어 있다** |
| 6 | 과목 추가 → 홈 | 학점이 늘어나 있다 |
| 7 | 추가한 과목 삭제 → 홈 | 원래대로 돌아온다 |
| 8 | AI 분석 화면 진입 | `GET` 호출 · **5방향 탭** 표시 · 즉시 |
| 9 | **AI 분석 버튼** 클릭 | `POST` 호출 · **로딩 표시** · 수 초 후 결과 |
| 10 | 각 과목의 추천 이유 | **3개씩** 표시 |
| 11 | persona 카드 | **균형 성장형** + 전략 3개 |
| 12 | thin 박스 / caution | 학점 안정형·협업 활동형에 thin / 시험·개별평가형에 caution |
| 13 | 졸업 요건 입력 카드 | **안 보인다** |

> **4번이 가장 중요하다.** 새로고침 후에도 값이 남아있으면 서버 저장이 성공한 것이다. 사라지면 아직 로컬 상태만 고친 것이다.

---

## 9. 에러 대응표

| 증상 | 원인 → 조치 |
|---|---|
| **저장해도 새로고침하면 사라짐** | API를 안 부름 → 작업 2 |
| **홈 숫자가 안 바뀜** | 저장 후 `GET /dashboard` 재조회 안 함 → 작업 3 |
| 요청이 **`localhost:8080`** 으로 감 | dev 서버 재시작 안 함 → `Ctrl+C` 후 `npm run dev` |
| 요청이 **옛 IP**로 감 | 코드에 주소 하드코딩 → `Ctrl+Shift+F`로 `:8080` 검색해 제거 |
| **400 Bad Request** | `grade`에 `A0` 보냄 → **`"A"`**로 / `credit`에 소수점 → 정수로 / `category` 값 오타 |
| **401 Unauthorized** | 토큰 만료(1시간) 또는 옛 토큰 → F12 → Application → Local Storage 비우고 재로그인 |
| **403 Forbidden** | 남의 `profileId`로 요청 → `GET /auth/me`의 `profileId` 사용 |
| **404 COURSE_NOT_FOUND** | 없는 `courseId`로 PUT/DELETE → 목록을 다시 불러온 뒤 재시도 |
| **CORS 에러** | `localhost:5173`이 아닌 주소에서 페이지를 엶 → `http://localhost:5173`으로 접속 |
| **Mixed Content 차단** | https 페이지에서 http API 호출 → `http`로 접속 |
| 로그인은 되는데 **화면이 빔** | `demo1`으로 로그인함(프로필 없음) → `songwonseok1234@...` 사용 |

### 막히면

원석이 **서버 로그를 실시간으로 보고 있다**(`sudo journalctl -u passport -f`).
- **로그에 요청이 찍힘** → 서버 도달 → 백엔드/데이터 문제
- **로그에 아무것도 안 찍힘** → 요청이 서버에 도달 못함 → FE 설정 문제

증상 + Network 탭 스크린샷을 공유하면 즉시 갈린다.

---

## 10. 참고 — 계정

| 계정 | 비밀번호 | 용도 |
|---|---|---|
| `songwonseok1234@g.eulji.ac.kr` | *(원석에게 확인)* | **시연 주 계정** · profileId=1 · 16과목 · 5방향 |
| `demo1@passport.ac.kr` | `Passport1!` | 스모크 테스트 (프로필 없음 → `profileId: null`) |
| `demo2@passport.ac.kr` | `Passport1!` | 폴백 배지 시연 예정 (프로필 없음) |
