# 86 — 캐시 · persona · directions 메커니즘 실측 기록 (2026-07-15)

> **작성 계기** : 게이트 준비 중 82·85 문서에 적힌 캐시 생성 절차가 **실행 불가능**함을 발견. 코드를 읽어 실제 동작을 확정하고, 시연 데이터를 구성한 기록.
> **관련** : [83 현황](83_현황_20260715_게이트당일.md) · [84 AWS](84_AWS배포_실행기록.md) · [85 잔여작업](85_잔여작업_게이트계획.md)

---

## 1. 🔴 정정 — `batch.py`로는 실데이터 캐시를 만들 수 없다

### 잘못된 기록 (82 §3, 85 §2 초판)

```bash
python batch.py 2024132075     # ❌ 이렇게 하면 demo가 돌아간다
```

### 실제 코드 (`batch.py`)

```python
{"demo": cmd_demo, "candidates": cmd_candidates, "models": cmd_models}.get(cmd, cmd_demo)()
```

- 받는 인자는 **`demo` / `candidates` / `models` 3개뿐**
- **모르는 인자는 전부 `cmd_demo()`로 폴백** — 학번을 넣어도 무시된다
- `cmd_demo()`는 `from_demo()`로 `data/demo_*.json`을 읽는다 → studentKey는 항상 **`202312345`**

> **2단계에서 실행한 `python batch.py 202312345`가 성공한 것처럼 보인 이유**: 인자가 무시되고 demo가 돌았는데, demo 데이터의 studentKey가 우연히 `202312345`라서 결과가 맞아떨어졌다. **실데이터로 돌린 게 아니다.**

### ✅ 진짜 경로 — Spring `POST /recommendations`

`bridge.py` 헤더 명시:
> *"캐시: recommend()가 결과를 cache/{studentKey}.json에 항상 저장한다."*

```
POST /api/v1/profiles/{id}/recommendations
  → RecommendationService.generate()
  → PassportAiBridge → bridge.py (stdin: payload JSON)
  → core/recommend.recommend()
  → core/cache.write_cache(studentKey, result)   ← 여기서 cache/{studentKey}.json 생성
```

**결론: 임의 학번의 캐시는 `POST /recommendations` 1회로만 만들 수 있다.** `batch.py`는 데모 전용이다.

---

## 2. 🔴 캐시 재생성 조건 — 지문(fingerprint)

`RecommendationService.generate()` (src/main/java/com/passport/recommendation/service/RecommendationService.java:74~104)

```java
String currentFp = fingerprint(profile);          // 유저 입력 데이터의 지문
Optional<String> storedFp = readFingerprint(studentId);   // cache/{studentId}.fp
Optional<RecommendationResponse> cached = readCache(studentId);

if (cached.isPresent() && storedFp.isEmpty()) {
    writeFingerprint(studentId, currentFp);
    return withFreshPersona(cached.get(), profileId);      // ① 캐시 유지 + 지문만 기록
}
if (cached.isPresent() && currentFp.equals(storedFp.get())) {
    return withFreshPersona(cached.get(), profileId);      // ② 변화 없음 → 캐시 유지
}
// ③ 데이터 변경 또는 첫 생성 → 라이브 재생성
```

### 지문에 포함되는 것 / 안 되는 것

| 구분 | 항목 |
|---|---|
| ✅ 포함 | **유저 입력 데이터** — 수강 이력 · 요건 · 인증 · 프로필 |
| ❌ **미포함** | **카탈로그(`data/courses.json`)** — 과목 특성·학점·영역 등 |

> ### ⚠️ 함정
> **카탈로그를 바꿔도 지문이 그대로라 캐시가 영원히 재생성되지 않는다.** 실제로 특성 데이터를 반영하고 POST 했더니 `generatedAt`이 이전 값 그대로였다.

### 강제 재생성 방법

```bash
# EC2 — 해당 학번 캐시 2개 파일을 모두 삭제
rm -f ~/app/passport-ai/cache/{studentId}.json ~/app/passport-ai/cache/{studentId}.fp
```
그다음 `POST /recommendations` 1회.

> **`.fp`만 지우면 안 된다** — 위 분기 ①(`cached.isPresent() && storedFp.isEmpty()`)에 걸려 **캐시 유지 + 지문만 기록**되고 재생성되지 않는다. **반드시 `.json`과 `.fp`를 함께 삭제.**

### 부수 효과 (시연 시 인지할 것)

- **수강 과목을 추가·삭제하면 지문이 바뀌어 다음 POST에서 자동 재생성**된다 → 시연 시나리오 "과목 라이브 추가"를 하면 **AI가 재호출되고 캐시가 갱신**된다. 의도된 동작이나, 리허설에서 추가한 과목은 그대로 남는다.
- `source`가 `live`일 때만 지문을 저장한다 → AI 일시 실패(폴백) 시 다음 클릭에서 자동 재시도된다.

---

## 3. `source` 값의 의미 (폴백 배지 조건)

`core/recommend.py:26~46`

```python
try:
    ...
    source = "live" if ai_valid >= MIN_AI_VALID else "fallback"
except Exception:
    cached = read_cache(facts.studentKey)
    result = cached or Result(directions=rule_directions(...), source="fallback", ...)
write_cache(facts.studentKey, result)     # 예외 경로에서도 항상 저장
```

| source | 조건 |
|---|---|
| `live` | AI 응답의 유효 항목이 `MIN_AI_VALID` 이상 |
| `fallback` | AI 응답 유효 항목 부족 **또는** AI 예외 + **캐시 없음** → 규칙 기반 |
| (캐시 반환) | AI 예외 + 캐시 있음 → 캐시의 원래 source 유지 |

> ### 폴백 배지("기본 추천") 시연 조건
> **AI 실패 + 캐시 없음**이어야 `fallback`이 뜬다. 정상 상태에서 신규 계정으로 POST 하면 `live`가 나와 **배지가 안 보인다**. 시연하려면 `.env`를 임시 리네임해 AI를 실패시켜야 한다(85 §6 규칙 ②).

---

## 4. 🔴 directions 5방향 — 특성 데이터가 없으면 방향이 사라진다

`core/directions.py`

```python
MIN_PER_DIRECTION = 5   # 이 미만이면 thin
MAX_PER_DIRECTION = 5   # 방향당 노출 과목 수

def classify(candidates, facts):
    for spec in SPECS:                    # [FAST_GRAD, MAJOR_DEEP, GRADE_SAFE, EXAM_SOLO, TEAM_ACTIVE]
        matched = _filter(spec, candidates, facts)
        if not matched:                   # ← 빈 방향은 아예 노출하지 않음
            continue
        thin = len(matched) < MIN_PER_DIRECTION
```

### 방향별 필터 조건

| 방향 | 조건 | 특성 의존 |
|---|---|---|
| `FAST_GRAD` 졸업요건 집중형 | 미이수 핵심영역 우선 + 학점 큰 순 | ❌ 무관 |
| `MAJOR_DEEP` 전공 심화형 | `category == MAJOR` | ❌ 무관 |
| `GRADE_SAFE` 학점 안정형 | `gradingStyle == "너그러움"` and `시험 != 많음` and `팀플 != 많음` | ✅ **성적후함정도 필요** |
| `EXAM_SOLO` 시험·개별평가형 | `시험 ∈ (많음, 보통)` and `팀플 ∈ (없음, None)` | ✅ 필요 |
| `TEAM_ACTIVE` 협업·활동형 | `팀플 ∈ (보통, 많음)` **or** `발표 ∈ (보통, 많음)` | ✅ 필요 |

- `caution`(편향 경고)은 **`EXAM_SOLO`·`TEAM_ACTIVE` 전용**이며, **thin 방향에는 넣지 않는다**(FE thin 박스와 이중 메시지 방지).

### 발견한 데이터 공백

| 구분 | 전체 | 특성 보유 (당초) |
|---|---|---|
| 교양선택 | 140 | 135 |
| 교양필수 | 11 | 5 |
| **전공** | **31** | **0** 🔴 |

**전공 과목 특성이 전부 공란**이라, 부족영역이 전공인 학생(=원석 실데이터)은 후보가 전부 전공 → `GRADE_SAFE`/`EXAM_SOLO`/`TEAM_ACTIVE` 3방향이 0개 매칭 → 제외 → **2방향만 노출**됐다.

> demo(202312345)가 4방향이었던 건 부족영역이 교양(`[4, 6]`)이라 후보가 교양(특성 보유)이었기 때문이다. **데이터 특성에 따라 방향 수가 달라진다.**

---

## 5. ✅ 조치 — 전공 특성 11과목 보강 (2026-07-15)

원석이 실제 수강 경험 기반으로 작성한 Notion 표 + 구술 설명을 `data/raw/courses_eval.csv`에 반영.

> **기능 동결과 무관** — 코드가 아니라 **데이터** 보강이다.

### 반영값

| 교과목명 | 코드 | 시험 | 과제 | 팀플 | 발표 | 출석 | 쪽지 | 실습 | 성적후함 |
|---|---|---|---|---|---|---|---|---|---|
| 통계실무 | 401347 | 많음 | 많음 | 없음 | 없음 | 많음 | 많음 | 많음 | — |
| SW/HW플랫폼설계 | 401348 | 많음 | 없음 | 없음 | 많음 | 보통 | 없음 | 없음 | — |
| 인공지능프로그램 | 401349 | 많음 | 없음 | 없음 | 없음 | 보통 | 없음 | 많음 | — |
| 빅데이터처리 | 401342 | 많음 | 없음 | 없음 | 없음 | 보통 | 없음 | 많음 | — |
| 딥러닝 | 400982 | 많음 | 보통 | 없음 | 없음 | 많음 | 없음 | 보통 | — |
| 데이터마이닝 및 응용실습 | 401345 | 많음 | 없음 | 없음 | 없음 | 보통 | 없음 | 많음 | — |
| 의료DB설계 | 401338 | 많음 | 보통 | 없음 | 없음 | 많음 | 없음 | 많음 | — |
| 클라우드컴퓨팅 | 400980 | 많음 | 없음 | 없음 | 없음 | 보통 | 없음 | 많음 | — |
| 인공지능 플랫폼 설계 | 401346 | 많음 | 보통 | 보통 | 보통 | 많음 | 없음 | 보통 | — |
| **인공지능개론** | 400968 | 보통 | 많음 | 없음 | 많음 | 많음 | 없음 | 보통 | **너그러움** |
| 미적분학 | 401131 | 많음 | 없음 | 없음 | 없음 | 보통 | 없음 | 보통 | — |

**출처**
- 상위 9과목 : 원석 Notion 표(2학년2학기·3학년1학기 전공 과목) — 원본 그대로
- `인공지능개론`·`미적분학` : 원석 구술 설명을 3단계 값(없음/보통/많음)으로 **해석 후 원석 승인**
  - 인공지능개론 — *"시험은 중간만, 기말은 발표(대개 절대평가). 과제는 매시간 수기 제출 + 기말 발표자료. 팀플 없음. 발표 영어 시 가점. 출석 후함. 쪽지 없음. 실습은 S3버킷 특강 1회"* → 시험 보통 / 과제 많음 / 발표 많음 / **성적후함 너그러움**
  - 미적분학 — *"중간·기말 둘 다. 과제·레포트 없음. 팀플 없음. 출석 후하나 지각은 지각. 쪽지 없음. 실습은 특강 경험 정도"* → 시험 많음 / 출석 보통

> ⚠️ **`인공지능플랫폼설계` → 카탈로그 정확명은 `인공지능 플랫폼 설계`(띄어쓰기)**. 시험값이 스크린샷에 `많ㅇ므`로 깨져 있어 `많음`으로 읽음.
> ⚠️ **9과목의 `성적후함정도`는 미확보** — Notion 표에 해당 컬럼이 없다. 7/16에 확보 시 `GRADE_SAFE` thin 해소 가능.

### 반영 절차 (재현용)

```powershell
# 1. CSV 읽기 전용 해제 (원본이 IsReadOnly=True 상태였음)
Set-ItemProperty -Path "passport-ai\data\raw\courses_eval.csv" -Name IsReadOnly -Value $false

# 2. CSV 수정 후 재빌드
cd passport-ai
python build_data.py         # → data/courses.json (182과목, 특성 매칭 182/182)

# 3. EC2 업로드 (courses.json만 있으면 됨 — bridge.py는 courses.json만 읽는다)
scp -i "$env:USERPROFILE\Downloads\PassPort-key.pem" "passport-ai\data\courses.json" ubuntu@15.164.84.176:~/app/passport-ai/data/courses.json

# 4. 캐시 강제 삭제 후 재생성 (§2 참조)
ssh ... "rm -f ~/app/passport-ai/cache/2024132075.json ~/app/passport-ai/cache/2024132075.fp"
# POST /api/v1/profiles/1/recommendations 1회
```

> **백업**: 원본 CSV는 `C:\Users\송원석\AppData\Local\Temp\claude\courses_eval.backup.csv` (32,877B). git 추적 파일이므로 `git checkout --` 로도 복원 가능.
> **미완**: EC2의 `data/raw/courses_eval.csv`는 읽기 전용이라 업로드 실패(`Permission denied`). **런타임에 안 쓰이므로 무해**(`build_data.py`는 로컬 실행). 7/16에 EC2에서 재빌드가 필요하면 `chmod 644` 후 재업로드.

### 결과 — 2방향 → **5방향** ✅

| 방향 | 과목 수 | thin | caution |
|---|---|---|---|
| 졸업요건 집중형 (기본) | 5 | — | — |
| 전공 심화형 | 5 | — | — |
| **학점 안정형** | **1** | **thin** | — |
| **시험·개별평가형** | 5 | — | **"시험 비중이 높아 시험 부담이 클 수 있어요."** |
| **협업·활동형** | **3** | **thin** | — |

`source=live`, persona `균형 성장형` 연동. **thin 배지·caution 문구를 실물로 시연 가능**해졌다.

---

## 6. persona 산출 조건 (실측 확정)

```
persona 브릿지 성공: studentKey=202312345 pattern=none     코드매핑=0과목
persona 브릿지 성공: studentKey=202312345 pattern=none     코드매핑=1과목
persona 브릿지 성공: studentKey=202312345 pattern=none     코드매핑=2과목
persona 브릿지 성공: studentKey=202312345 pattern=balanced 코드매핑=3과목   ← 전환
```

**조건 2가지 — 둘 다 충족해야 패턴이 나온다:**

1. **과목명이 `data/courses.json`과 정확 일치** (`bridge.py`가 공백 트림 후 exact match)
2. **매핑된 과목에 평가특성이 있어야** 함 (특성 보유 과목 **3개 이상**에서 `balanced` 전환 확인)

둘 중 하나라도 안 되면 `EXPLORING`(탐색형) → *"아직 성향을 분석할 만큼의 성적 데이터가 충분하지 않아요"*

- 카탈로그에 없는 과목명은 **미매칭으로 조용히 제외**된다(에러 아님). 학점 계산은 Spring 진단이 이미 끝낸 값을 쓰므로 **진단에는 영향 없음**.

---

## 7. 시연 데이터 최종 상태 (원석 실데이터)

**profileId=1 / studentId=2024132075 / `songwonseok1234@g.eulji.ac.kr`**

### 수강 이력 16과목 (총 39학점)

| 학기 | 과목명(카탈로그 정확명) | 학점 | category | 성적 | 비고 |
|---|---|---|---|---|---|
| 2024-1 | 데이터분석기초 | 3 | MAJOR_REQUIRED | A | ⚠️ 임의 |
| 2024-1 | 객체지향프로그램 | 3 | MAJOR_ELECTIVE | B+ | ⚠️ 임의 |
| 2024-1 | 창의적사고와코딩(MD) | 3 | GE_ELECTIVE | A+ | ⚠️ 임의 |
| 2024-1 | 성과심리학 | 2 | GE_ELECTIVE | B+ | ⚠️ 임의 |
| 2024-1 | 인성과대학생활Ⅰ | 1 | GE_REQUIRED | P | ⚠️ 임의 |
| 2024-1 | 인공지능캡스톤 | 3 | MAJOR_ELECTIVE | A | ⚠️ 임의 · ❌ 카탈로그 없음 |
| 2024-1 | 영어읽기와쓰기 | 2 | GE_REQUIRED | B+ | ⚠️ 임의 · ❌ 카탈로그 없음 |
| 2026-1 | 통계기초 | 3 | MAJOR_REQUIRED | A | ✅ 성적표 |
| 2026-1 | 인공지능수학 | 3 | MAJOR_REQUIRED | B+ | ✅ 성적표 |
| 2026-1 | 운영체제 | 3 | MAJOR_ELECTIVE | A | ✅ 성적표 |
| 2026-1 | 작문과화법 | 2 | GE_REQUIRED | A+ | ✅ 성적표 |
| 2026-1 | 인공지능과컴퓨팅사고(MD) | 2 | GE_REQUIRED | A | ✅ 성적표 |
| 2026-1 | 인성과미래설계Ⅰ | **0** | GE_REQUIRED | P | ✅ 성적표(0.5학점) |
| 2026-1 | 경영학개론 | 3 | GE_ELECTIVE | B+ | ✅ 성적표 |
| 2026-1 | 광고와문화 | 3 | GE_ELECTIVE | B+ | ✅ 성적표 |
| 2026-1 | 데이터분석의기초(MD) | 3 | GE_ELECTIVE | B+ | ✅ 성적표 |

### 🔴 시연 시 유의 — 1학년1학기(2024-1) 성적 7건은 **임의값**

- 성적조회 기간이 아니라 실제 성적을 확보하지 못함 (원석 확인)
- **발표에서 "전부 제 실제 성적입니다"라고 말하면 안 된다.** 2학년1학기(2026-1) 9과목만 실제 성적표 값이다.
- 7/16에 성적 조회가 가능해지면 교체 권장

### 확정된 판단 (원석 결정)

| 항목 | 결정 | 영향 |
|---|---|---|
| 인성과미래설계Ⅰ (성적표 0.5학점, 카탈로그 0학점) | **`credit=0`** — `Course.credit`이 `int`라 0.5 저장 불가 | 총 이수 22 (성적표 22.5) |
| 전공기초 (`데이터분석기초`) | **`= 전공필수`(MAJOR_REQUIRED)** | 전공필수 21학점 요건에 산입. `categoryGpa`의 "기초"는 `null`로 표시됨 |

### 검증된 산출값

| 항목 | 값 |
|---|---|
| persona | **`BALANCED` / 균형 성장형** (탐색형 아님 ✅) |
| GPA | 누적 **3.8158** · 2024-1 `3.875`(17학점) · 2026-1 **`3.7727`**(22학점) · 2026-2 예측 `3.6705` |
| categoryGpa | 전공 3.833 · 교양 3.8 · 기초 `null` · 기타 `null` |
| 진단 | 39/130학점, 달성도 **30%**, GPA 요건(2.0) 충족 |
| 부족 | 전공필수 12 · 전공선택 30 · 교양필수 7 · 교양선택 7 · **자유선택 35** |
| 추천 | 5방향 · `source=live` · reasons 각 3개 |

> **2026-1 GPA `3.7727`은 성적표 계산값(83/22)과 정확히 일치** — 규칙 기반 계산 검증됨.

---

## 8. ⚠️ 미확인 / 후속

| # | 항목 | 비고 |
|---|---|---|
| 1 | **졸업 인증 전부 `NOT_SUBMITTED`** | 어학·봉사 미충족으로 표시 중. 시연에서 인증 화면을 쓸 거면 `PUT /profiles/1/certifications`로 실제 상태 입력 필요 |
| 2 | **`GENERAL_ELECTIVE`(자유선택) 0/35** | 부족 35학점으로 가장 크게 잡힘. **실제 을지대 요건과 일치하는지 미검증** — 요건 수치는 하드코딩값 |
| 3 | 9과목 `성적후함정도` 미확보 | 확보 시 `GRADE_SAFE` thin 해소 |
| 4 | 1학년1학기 성적 임의값 7건 | 성적조회 가능 시 교체 |
| 5 | `광고와문화` 코드 불일치 | 성적표 `114553` vs 카탈로그 `400778`. 이름 매핑이라 동작 무관 |
| 6 | 카탈로그 미등록 과목 2건 | `인공지능캡스톤`, `영어읽기와쓰기` — persona에서만 제외, 진단 영향 없음 |
