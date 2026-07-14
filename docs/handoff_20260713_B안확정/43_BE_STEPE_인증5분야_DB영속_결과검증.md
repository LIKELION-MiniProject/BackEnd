# 43. BE STEP E — 인증 5분야 진단 연결 + DB 영속 (결과 & 검증)

> 2026-07-14. 연결 레포에 직접 구현. ⚠️ 코워크 샌드박스는 gradle 컴파일 불가 → **원석 Windows에서 bootRun + curl 검증 필요**.

---

## 1) DB 영속 (H2 파일 모드) — 완료

| 파일 | 변경 |
|---|---|
| `application.yml` | datasource url `jdbc:h2:mem:passportdb` → `jdbc:h2:file:./data/passportdb;AUTO_SERVER=TRUE` (재시작해도 데이터 유지) |
| `data.sql` | `INSERT` → **`MERGE ... KEY(email)`** (매 기동 실행돼도 이메일 unique 충돌 없이 upsert — **파일 모드 재기동 안전**) |
| `.gitignore` | `/data/`, `*.mv.db`, `*.trace.db` 추가(로컬 DB 파일 커밋 금지) |

- ⚠️ **이게 핵심**: 파일 모드로만 바꾸고 data.sql을 그대로 뒀으면 2번째 기동 때 unique 충돌로 서버가 죽었을 것. MERGE로 해결.
- 휘발성으로 되돌리려면 url을 `jdbc:h2:mem:passportdb`로 교체(주석 안내).

## 2) 인증 5분야 진단 연결 — 완료 (규칙 확인 요망)

### 판정 규칙 (창작 없음, 유저 선언 기반)
> **유저가 `대상(TARGET)`으로 표시한 인증 분야가 전부 `완료(DONE)`여야 충족.** `비대상(NOT_TARGET)`·미표기는 무시.
- 대학의 "5중 3 / 심폐소생술·봉사 필수 / 택1" 규칙은 **검증 데이터가 없어(문서 04 §6) 코드로 창작하지 않음.** 유저가 자기 화면(사진1 §4)에서 직접 표시한 대상/완료만 검증.

### 변경 파일
| 파일 | 변경 |
|---|---|
| `requirement/domain/EffectiveRequirement.java` | 필드 `RequirementCertificationTargets certificationTargets`(nullable) 추가. `fromUser`→`user.getCertification()`, `fromHardcoded`→`null` |
| `diagnosis/dto/DiagnosisResponse.java` | 필드 `graduationCertification`(nullable) + 레코드 `GraduationCertificationProgress(areas[], fulfilled)`·`AreaMark(area, mark)` 추가 |
| `diagnosis/service/DiagnosisService.java` | `buildGraduationCertification()` 추가, `eligible`에 AND 반영 |

### 통합 방식 (안전·하위호환)
- 유저 요건 **미저장 시** `certificationTargets=null` → `graduationCertification=null` → **기존 동작·기존 테스트 그대로**.
- 유저 요건 **저장 시** → 5분야 판정을 기존 판정에 **AND**로 추가(= 더 엄격해질 뿐, 잘못 통과시키지 않음).
- 응답에 `graduationCertification` 필드 **추가**(기존 `certifications[]` 유지) → FE 하위호환.

### ⚠️ 원석 확인 1건 (판정 방식)
현재는 **기존 certification(LANGUAGE/VOLUNTEER/THESIS PASS) 판정 + 5분야 판정을 둘 다 AND**로 겁니다(보수적). 만약 팀이 "5분야 저장 시엔 5분야만으로 인증 판정"을 원하면, `DiagnosisService`의 `eligible` 라인에서 `certsOk`를 빼고 `gradCertOk`만 쓰도록 1줄 조정하면 됩니다. → **어느 쪽으로 갈지 알려주면 반영**.

## 3) 호스트 검증 (원석, Windows) — 컴파일·동작 확인 필수

```powershell
cd C:\Users\송원석\Documents\LikeLion_MiniProject_Team_3
$env:JWT_SECRET="dev-secret-change-me-at-least-32-bytes-0001"
$env:JWT_EXPIRATION="3600000"
.\gradlew bootRun
```

### (A) 컴파일/기동 확인
- 기동 로그에 에러 없이 뜨면 OK. `./data/passportdb.mv.db` 파일 생성 확인.

### (B) DB 영속 확인
1. signup으로 계정 생성 → 서버 종료 → **다시 bootRun** → 같은 계정으로 login 성공하면 영속 OK.
2. (재기동 시 data.sql MERGE가 unique 에러 없이 지나가는지 = 서버가 정상 기동하는지)

### (C) 5분야 진단 연결 확인 (curl)
```powershell
# 로그인 → 토큰, 프로필 생성 → profileId
# 1) 요건 저장: 5분야에 대상/완료 섞어서 PUT
curl -X PUT localhost:8080/api/v1/profiles/{id}/requirements -H "Authorization: Bearer <JWT>" -H "Content-Type: application/json" -d '{ ...credits/coreLiberal..., "certification":{"foreignLangCert":"대상","infoProcessing":"비대상","cpr":"완료","socialService":"완료","foreignLangExtra":"비대상"}, "graduationExam":"EXAM", "draft":false }'
# 2) 진단 조회
curl localhost:8080/api/v1/profiles/{id}/diagnosis -H "Authorization: Bearer <JWT>"
```
기대: 응답에 `graduationCertification.areas[5]` + `fulfilled`. 위 예시는 `foreignLangCert=대상`이 아직 완료 아님 → **fulfilled=false**, `eligibleForGraduation=false`. 전부 완료/비대상으로 바꾸면 fulfilled=true.

- ⚠️ `gradlew test` 대신 bootRun+curl. 기존 `DiagnosisServiceTest`는 하드코딩 경로(5분야 null)라 그대로 통과할 것(직접 생성 없음, accessor만).

## 4) 커밋 (검증 후, 원석)
이 변경분은 기존 미커밋 더미에 추가됨. 스모크 통과하면 함께 커밋·push.
```
git add -A
git commit -m "feat(diagnosis): 졸업 인증 5분야 진단 연결(유저 대상→완료 규칙) + H2 파일 영속(MERGE 시드)"
```

## 5) 남은 관련 항목
- `graduationExam`(EXAM) 판정: EXAM 합격 검증 데이터 없어 미판정(대시보드 동일). 데이터 확보 시 별도 연결.
- 위 §2 ⚠️ 판정 방식(AND vs 5분야 단독) 팀 확정.
