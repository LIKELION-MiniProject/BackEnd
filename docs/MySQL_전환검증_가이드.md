# MySQL 전환 검증 가이드 (feat/mysql 브랜치)

> **목적** : 배포 서버(EC2·H2)는 그대로 두고, **로컬에서 앱이 MySQL로 부팅·CRUD 되는지** 검증한다.
> "왜 MySQL 안 썼냐"에 대한 방어 근거 + 실제 전환 경험 확보용. **되돌리기 100% 안전**(배포본 무관).

## 코드 쪽 (이미 완료됨)

| 파일 | 변경 |
|---|---|
| `build.gradle` | `com.mysql:mysql-connector-j` 추가 (H2와 공존) |
| `src/main/resources/application-mysql.yml` | MySQL datasource 프로파일 |
| `src/main/resources/data-mysql.sql` | H2 `MERGE` → MySQL `INSERT ... ON DUPLICATE KEY UPDATE` |

## 손으로 할 일

### 1. MySQL 설치
- https://dev.mysql.com/downloads/installer/ → 큰 쪽(mysql-installer-community) 다운로드 → "No thanks, just start my download"
- 설치 유형 **Custom** → `MySQL Server` + `MySQL Workbench` 선택
- 구성: Config Type = `Development Computer`, 인증 = `Strong Password Encryption`, **root 비밀번호 설정(꼭 기억)**, Windows Service 기본값 유지
- (대안) `winget install Oracle.MySQL`

### 2. DB·계정 생성
`MySQL 8.0 Command Line Client` 실행(root 비번 입력) 또는 Workbench 쿼리창에서:
```sql
CREATE DATABASE passportdb CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'passport'@'localhost' IDENTIFIED BY 'passport1234';
GRANT ALL PRIVILEGES ON passportdb.* TO 'passport'@'localhost';
FLUSH PRIVILEGES;
```

### 3. 앱을 MySQL 프로파일로 실행
프로젝트 폴더에서 PowerShell. `application.yml`이 `JWT_SECRET`·`JWT_EXPIRATION`을
환경변수로 요구하므로(기본값 없음, EC2엔 세팅돼 있음) 로컬에선 함께 넣어준다:
```powershell
$env:SPRING_PROFILES_ACTIVE='mysql'
$env:JWT_SECRET='passportLocalVerifyJwtSecretKeyForMySqlBootCheck0123456789ABCDEF'  # 32바이트 이상
$env:JWT_EXPIRATION='3600000'
.\gradlew.bat bootRun
```
로그에 `Tomcat started on port 8080` + `Started PassportApplication` 이 뜨면 성공.
(MySQL 방언·테이블 자동 생성 DDL도 로그에서 확인됨.)

### 4. CRUD 검증 (두 번째 PowerShell 창)
```powershell
# 로그인 (시드된 데모 계정)
$login = Invoke-RestMethod -Uri http://localhost:8080/api/v1/auth/login -Method Post `
  -ContentType 'application/json' -Body '{"email":"demo1@passport.ac.kr","password":"Passport1!"}'
$token = $login.data.accessToken
$H = @{ Authorization = "Bearer $token" }

# 프로필 생성 (쓰기)
$profile = Invoke-RestMethod -Uri http://localhost:8080/api/v1/profiles -Method Post `
  -ContentType 'application/json' -Headers $H `
  -Body '{"deptCode":"BIGDATA_AI","studentId":"2024000001","admissionYear":2024,"name":"Tester"}'
$pid = $profile.data.id

# 수강 추가 (쓰기)  ※ 인코딩 이슈 회피 위해 영문 과목명으로 검증
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/profiles/$pid/courses" -Method Post `
  -ContentType 'application/json' -Headers $H `
  -Body '{"name":"TestCourse","credit":3,"category":"MAJOR_REQUIRED","grade":"A","year":2024,"semester":1,"retake":false}'

# 조회 (읽기)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/profiles/$pid/courses" -Headers $H | ConvertTo-Json -Depth 5
```

### 5. MySQL에 실제로 들어갔는지 눈으로 확인 (Workbench / CLI)
```sql
USE passportdb;
SELECT * FROM users;
SELECT * FROM profiles;
SELECT * FROM courses;
```
→ 방금 넣은 `Tester` 프로필과 `TestCourse` 행이 보이면 **MySQL 검증 완료**. (발표용 스크린샷 지점)

## 되돌리기
- 앱 종료: bootRun 창에서 `Ctrl+C`
- 평소 H2로 실행: 프로파일 없이 `.\gradlew.bat bootRun` (또는 `$env:SPRING_PROFILES_ACTIVE=''`)
- 동결된 배포본으로: `git checkout main`
- 배포 서버(EC2)는 이 작업 내내 **아무 영향 없음**.
