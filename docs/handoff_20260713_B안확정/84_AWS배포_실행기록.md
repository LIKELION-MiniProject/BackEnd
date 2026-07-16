# 84 — AWS 배포 실행기록 (재현 가능한 전체 절차)

> **작성** : 2026-07-15 · **상태** : ✅ 배포 완료, 외부 검증 6/6 통과
> **서버** : `http://15.164.84.176:8080`
> **관련** : 현황 요약은 [83](83_현황_20260715_게이트당일.md), 잔여 작업은 [85](85_잔여작업_게이트계획.md)

⚠️ **이 문서에는 시크릿(API 키·JWT_SECRET·비밀번호·pem)이 일절 포함되지 않는다.** 값이 필요한 자리는 `<...>`로 표기.

---

## 1. 인프라 스펙 (확정값)

| 항목 | 값 | 선택 이유 |
|---|---|---|
| 리전 | 서울 `ap-northeast-2` (AZ `2a`) | |
| 인스턴스 | `PassPort-Server` / **t3.small** (2 vCPU, **2 GiB**) | t3.micro(1GiB)는 **JVM + Python 서브프로세스 동시 구동 시 OOM 위험**. 게이트 당일 리스크 회피. 실측 269M/2GiB로 여유 확인 |
| AMI | **Ubuntu Server 24.04 LTS** (x86) | 22.04는 프리티어 제외, 26.04는 과도하게 최신 |
| 스토리지 | 30 GiB gp3 | EBS 프리티어 월 30GB 한도 내. 8GB는 OS만으로 절반 소진 |
| 키페어 | **`PassPort-key.pem`** (RSA) | ⚠️ 파일명 주의 (`passport.pem` 아님). git 폴더 밖 `~/Downloads`에 보관 |
| **탄력적 IP** | **`15.164.84.176`** | 재부팅 시 IP 변경 방지 (FE·게이트 주소 고정). **인스턴스에 연결된 상태면 무료** |
| 보안그룹 | `22` = 내 IP / `8080` = `0.0.0.0/0` | |
| 내부 IP | `172.31.1.73` | |

---

## 2. 블록 A — EC2 생성 · SSH 접속

### 로컬 사전 준비 (PowerShell)

```powershell
# pem 권한 잠그기 — 이 과정 없으면 SSH가 키를 거부
icacls "$env:USERPROFILE\Downloads\PassPort-key.pem" /inheritance:r /grant:r "${env:USERNAME}:R"
```

> **함정**: `"$env:USERNAME:R"`로 쓰면 PowerShell이 `env:USERNAME:R` 전체를 변수명으로 파싱해 빈 값이 되고 `"/grant:r" 매개 변수가 잘못되었습니다` 에러. **`${env:USERNAME}:R`** 로 중괄호 필수.

### 접속

```powershell
ssh -i "$env:USERPROFILE\Downloads\PassPort-key.pem" ubuntu@15.164.84.176
```

- 첫 접속 시 `Are you sure you want to continue connecting (yes/no/[fingerprint])?` → **`yes`** (전체 입력)
- 성공: `ubuntu@ip-172-31-1-73:~$`

### 트러블슈팅

| 증상 | 원인 |
|---|---|
| `Warning: Identity file ... not accessible` | pem 파일명/경로 오류 (`PassPort-key.pem`이 정확한 이름) |
| `Connection timed out` | 보안그룹 22번 소스 IP 불일치 (내 IP 변경 시 재설정) |
| `Permission denied (publickey)` | 사용자명 오류 — Ubuntu는 반드시 `ubuntu@` |
| `UNPROTECTED PRIVATE KEY FILE` | `icacls` 재실행 |

---

## 3. 블록 B — 서버 준비

```bash
# 1) Java 21
sudo apt update && sudo apt install -y openjdk-21-jre-headless

# 2) Python (venv 필수)
sudo apt install -y python3-pip python3-venv
python3 -m venv ~/venv && ~/venv/bin/pip install google-genai==1.38.0 pydantic python-dotenv

# 3) 확인
java -version && ~/venv/bin/python -c "import google.genai, pydantic, dotenv; print('python deps OK')"

# 4) 앱 디렉터리 (⚠️ scp 전에 반드시 생성 — 없으면 scp 실패)
mkdir -p ~/app
```

**결과**: `openjdk version "21.0.11"` / `python deps OK`

> **왜 venv인가**: Ubuntu 24.04는 시스템 pip 설치를 차단(`externally-managed-environment`). systemd에서 `~/venv/bin/python`을 파이썬 경로로 주입한다.

---

## 4. 블록 C — 빌드 · 업로드

### 4-1. 로컬 빌드 (PowerShell)

```powershell
cd C:\Users\송원석\Documents\LikeLion_MiniProject_Team_3
.\gradlew bootJar
# → build\libs\passport-0.0.1-SNAPSHOT.jar (53,097,863 bytes)
```

### 4-2. 스테이징 (.env 제외가 핵심)

```powershell
$stage = "$env:TEMP\passport-upload"
Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item -Recurse passport-ai "$stage\passport-ai"
Remove-Item -Force "$stage\passport-ai\.env" -ErrorAction SilentlyContinue
Get-ChildItem -Recurse -Force -Directory "$stage" -Filter "__pycache__" | Remove-Item -Recurse -Force
```

**검증 (필수)**

```powershell
Get-ChildItem -Recurse -Force $stage -Filter ".env" | Select-Object FullName   # → 아무것도 안 나와야 정상
Get-ChildItem "$stage\passport-ai\cache"                                        # → 202312345.json, .fp
```

실제 결과: `.env` 없음 ✅ / cache 2개(`202312345.fp` 64B, `202312345.json` 12,210B) ✅ / `.env.example`·`.gitignore`는 포함(시크릿 아님) / `data/raw`에 `courses_areas.json`, `courses_eval.csv` 포함 ✅

### 4-3. 업로드

```powershell
scp -i "$env:USERPROFILE\Downloads\PassPort-key.pem" "build\libs\passport-0.0.1-SNAPSHOT.jar" ubuntu@15.164.84.176:~/app/passport.jar
scp -i "$env:USERPROFILE\Downloads\PassPort-key.pem" -r "$env:TEMP\passport-upload\passport-ai" ubuntu@15.164.84.176:~/app/
```

### 4-4. 서버 측 확인

```bash
ls -la ~/app/ && ls ~/app/passport-ai/ && ls ~/app/passport-ai/cache/
ls -a ~/app/passport-ai/ | grep -x "\.env" && echo "❌ .env 있음!" || echo "✅ .env 없음"
```

결과: `passport.jar` 53,097,863B(로컬과 일치) ✅ / passport-ai 전체 ✅ / cache 2개 ✅ / **`.env` 없음** ✅

---

## 5. 블록 C-2 — `.env` 작성 (원석 전용, Claude 미접촉)

### 로컬 항목명 확인 (값 노출 없이)

```powershell
Get-Content "...\passport-ai\.env" -Encoding UTF8 |
  Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*\s*=' } |
  ForEach-Object { ($_ -split '=')[0].Trim() }
# → GEMINI_API_KEY, GEMINI_MODEL
```

> **함정**: `-Encoding UTF8` 없이 읽으면 PowerShell 5.1이 시스템 코드페이지로 읽어 한글 주석이 깨지고 항목이 주석에 섞여 보인다. `Where-Object` 필터로 주석 제외 필수.

### 서버에 작성

```bash
nano ~/app/passport-ai/.env
# 내용 (값은 로컬에서 복사):
#   GEMINI_API_KEY=<로컬과 동일한 값>
#   GEMINI_MODEL=<로컬과 동일한 값>
# 저장: Ctrl+O → Enter → Ctrl+X

chmod 600 ~/app/passport-ai/.env
ls -l ~/app/passport-ai/.env && grep -oE '^[A-Za-z_]+' ~/app/passport-ai/.env
```

**결과**: `-rw-------` 104바이트 / `GEMINI_API_KEY`, `GEMINI_MODEL` ✅

### 로드 확인 (Gemini 미호출)

```bash
cd ~/app/passport-ai && ~/venv/bin/python -c "
from dotenv import load_dotenv; import os
load_dotenv()
k=os.getenv('GEMINI_API_KEY'); m=os.getenv('GEMINI_MODEL')
print('API_KEY:', 'OK(len=%d)'%len(k) if k else '없음')
print('MODEL:', m if m else '없음')"
```

**결과**: `API_KEY: OK(len=53)` / **`MODEL: gemini-3.1-flash-lite`** ✅

> ### 왜 로컬과 같은 키를 썼나
> 2단계에서 `batch.py`가 그 키로 `source=live` 성공 → **동작이 검증된 키**. 게이트 당일에 미검증 키를 끼우면 실패 시 원인이 키인지 서버 설정인지 구분 불가. **변수를 하나만 바꿔야 디버깅이 된다.**
>
> **로컬 `.env`와 EC2 `.env`는 완전히 별개 파일이며 동기화되지 않는다.** 코드에 키는 박혀있지 않고(CLAUDE.md 원칙 3) 실행 시점에 `.env`를 읽을 뿐이다.

---

## 6. 블록 D — systemd 등록

### 6-1. JWT 환경파일 (시크릿 자동 생성)

```bash
cat > ~/app/app.env <<EOF
JWT_SECRET=$(openssl rand -base64 48)
JWT_EXPIRATION=3600000
EOF
chmod 600 ~/app/app.env
ls -l ~/app/app.env && grep -oE '^[A-Za-z_]+' ~/app/app.env
```

**결과**: `-rw-------` 99바이트 / `JWT_SECRET`, `JWT_EXPIRATION` ✅
→ `openssl rand`로 서버에서 직접 생성하므로 **Claude·문서 어디에도 값이 남지 않는다.** 유닛 파일이 아닌 별도 600 파일에 분리(`EnvironmentFile`).

### 6-2. 유닛 파일

```bash
sudo tee /etc/systemd/system/passport.service > /dev/null <<'EOF'
[Unit]
Description=PassPort Spring Boot
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/app
EnvironmentFile=/home/ubuntu/app/app.env
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SPRING_H2_CONSOLE_ENABLED=false"
Environment="PASSPORT_AI_CACHE_DIR=/home/ubuntu/app/passport-ai/cache"
Environment="PASSPORT_AI_DIR=/home/ubuntu/app/passport-ai"
Environment="PASSPORT_AI_PYTHON=/home/ubuntu/venv/bin/python"
ExecStart=/usr/bin/java -Xmx1g -jar /home/ubuntu/app/passport.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
```

**설정 근거**

| 항목 | 이유 |
|---|---|
| `WorkingDirectory=/home/ubuntu/app` | `application.yml`의 H2 경로가 `./data/passportdb`(상대경로) → `/home/ubuntu/app/data/`에 생성 |
| `SPRING_H2_CONSOLE_ENABLED=false` | **아래 §6-3 참조 — 보안상 필수** |
| `PASSPORT_AI_PYTHON` | venv 파이썬 주입 (시스템 python엔 google-genai 없음) |
| `-Xmx1g` | 2GiB 중 1GiB를 JVM에, 나머지는 Python 서브프로세스용 |
| `Restart=always` | 게이트 중 크래시 시 5초 후 자동 복구 |

### 6-3. 🔴 h2-console 차단 — 코드 수정 없이 해결

**발견한 문제**

1. **`application-prod.yml`이 존재하지 않는다** → `SPRING_PROFILES_ACTIVE=prod`만으로는 아무 효과 없음
2. `application.yml`에 `spring.h2.console.enabled: true`
3. `SecurityConfig:45`에 **`.requestMatchers("/h2-console/**").permitAll()`**

→ 8080이 `0.0.0.0/0`으로 열려 있으므로 **인터넷 누구나 H2 DB 콘솔 접근 가능**한 상태였다.

**해결**: 기능 동결 원칙을 지키기 위해 **코드·설정 파일을 건드리지 않고** Spring relaxed binding을 이용한 환경변수 override 사용.

```
Environment="SPRING_H2_CONSOLE_ENABLED=false"   # = spring.h2.console.enabled
```

콘솔 서블릿 자체가 등록되지 않으므로 `permitAll`은 무의미해진다. **검증 결과 차단 확인** ✅

> 브라우저 접근 시 404가 아니라 `{"code":"COMMON-004","message":"서버 내부 오류가 발생했습니다."}`(500)가 반환된다. 전역 예외 핸들러가 미매핑 경로를 잡아서 생기는 현상으로, **콘솔 미노출이라는 목적은 달성**. 게이트 범위에선 문제없음(기능 동결).
>
> **게이트 후 권장**: `application-prod.yml`을 만들어 h2 콘솔 비활성 + `SecurityConfig`의 `/h2-console/**` permitAll을 dev 프로파일 전용으로 분리.

### 6-4. 기동

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now passport
sudo systemctl status passport --no-pager
sudo journalctl -u passport -n 20 --no-pager | grep -E "Started PassportApplication|Tomcat started|ERROR"
```

**결과** ✅
- `Active: active (running)`, Main PID 4287, `enabled`
- 메모리 **269.0M** (peak 274.3M)
- `Tomcat started on port 8080 (http)`
- `Started PassportApplication in 13.165 seconds`
- **ERROR 없음**

> `Using generated security password: ...` 로그는 **무시해도 된다** — Spring Security 기본 유저용 임시 비번인데 우리는 JWT 인증을 쓰므로 미사용.

### 6-5. 내부 로그인 테스트

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo1@passport.ac.kr","password":"Passport1!"}' | head -c 200; echo
```
→ `{"success":true,"data":{"accessToken":"eyJhbGciOiJIUzI1NiJ9..."` ✅

---

## 7. 블록 E — 외부 검증 (6/6 통과)

| # | 항목 | 방법 | 결과 |
|---|---|---|---|
| 1 | 외부 로그인 | PowerShell → EIP | ✅ JWT 발급, 한글 `데모유저1` |
| 2 | `auth/me` | | ✅ `profileId` 반환 |
| 3 | **인증 차단** | 토큰 없이 `auth/me` | ✅ **HTTP 401** |
| 4 | 요건 API | `GET /requirements/BIGDATA_AI` | ✅ `totalCredit:130`, `majorRequiredCredit:21`, `majorElectiveCredit:39`, `geRequiredCredit:14` … |
| 5 | **h2-console** | 브라우저 | ✅ **차단** (DB 미노출) |
| 6 | UTF-8 | | ✅ 정상 |

### 검증용 PowerShell 헬퍼 (재사용)

> PowerShell 5.1은 `Invoke-RestMethod`가 JSON 응답을 UTF-8로 디코딩하지 못해 **한글이 깨진다**(`ë°ëª¨...`). 아래처럼 **RawContentStream을 수동 디코딩**해야 한글 라벨 검증이 가능하다.

```powershell
$base = "http://15.164.84.176:8080/api/v1"
function J($m,$u,$h,$b){
  $p=@{Method=$m;Uri=$u;Headers=$h;UseBasicParsing=$true}
  if($b){$p.ContentType="application/json";$p.Body=[Text.Encoding]::UTF8.GetBytes($b)}
  try{$r=Invoke-WebRequest @p}
  catch{ $resp=$_.Exception.Response
         if($resp){$sr=New-Object IO.StreamReader($resp.GetResponseStream()); return "HTTP $($resp.StatusCode.value__): $($sr.ReadToEnd())"}
         return "ERR: $($_.Exception.Message)" }
  return [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
}
$t = (J "Post" "$base/auth/login" $null '{"email":"...","password":"..."}' | ConvertFrom-Json).data.accessToken
$h = @{Authorization="Bearer $t"}
```

> **함정**: `$pid`는 PowerShell 예약 변수(read-only)라 대입 시 `Cannot overwrite variable PID` 에러 후 엉뚱한 값이 들어간다. **`$myProfileId` 등 다른 이름** 사용.

---

## 8. 운영 명령 모음 (게이트 중 사용)

```bash
# 상태 확인
sudo systemctl status passport --no-pager

# 실시간 로그 (persona 브릿지 확인용)
sudo journalctl -u passport -f

# persona 브릿지 로그만
sudo journalctl -u passport --no-pager | grep -E "persona 브릿지"

# 재시작 (설정 변경 시)
sudo systemctl restart passport

# 메모리 확인
free -h && sudo systemctl status passport --no-pager | grep Memory
```

### jar 재배포 절차 (코드 수정이 생긴 경우)

```powershell
# 로컬
.\gradlew bootJar
scp -i "$env:USERPROFILE\Downloads\PassPort-key.pem" "build\libs\passport-0.0.1-SNAPSHOT.jar" ubuntu@15.164.84.176:~/app/passport.jar
```
```bash
# 서버
sudo systemctl restart passport && sleep 20 && sudo systemctl status passport --no-pager
```

> ⚠️ H2 파일 DB(`~/app/data/`)는 재배포해도 유지된다. **초기화하려면** `sudo systemctl stop passport && rm -rf ~/app/data && sudo systemctl start passport` (입력한 실데이터가 전부 사라지므로 게이트 전엔 금지).

---

## 9. 작업 창 구분 (혼동 방지)

| 프롬프트 | 정체 | 실행 대상 |
|---|---|---|
| `ubuntu@ip-172-31-1-73:~$` | **AWS 서버** (SSH 창) | `ls`, `mkdir`, `sudo`, `nano`, `curl`, `systemctl` |
| `PS C:\Users\송원석\...>` | **내 PC** (PowerShell 창) | `cd C:\...`, `Copy-Item`, `scp`, `Get-ChildItem`, `.\gradlew` |

> 실제로 PowerShell 명령을 SSH 창에 붙여넣어 `command not found`가 난 사례가 있었다(피해 없음). **창 2개를 동시에 쓰므로 매번 프롬프트 확인.**
> 여러 줄 붙여넣기는 두 셸 모두 정상 동작하나, `&&`로 이어지지 않은 줄은 **앞 명령이 실패해도 뒤가 실행**되므로 붙여넣은 뒤 위로 스크롤해 에러를 확인할 것.

---

## 10. 비용 메모

| 항목 | 상태 |
|---|---|
| t3.small | 프리티어 여부 ⚠️ **콘솔 배지로 확인 필요** (클래식 12개월 프리티어는 micro까지가 정설이나, 2025년 크레딧 기반 신규 프리티어면 커버될 수 있음). 유료여도 발표(7/20)까지 **$3 안팎** |
| EBS 30GB | 프리티어 월 30GB 한도 내 |
| **탄력적 IP** | 인스턴스에 **연결된 상태면 무료**. ⚠️ **할당만 하고 미사용 시 과금** |

> **발표(7/20) 종료 후**: 인스턴스 종료 + **탄력적 IP 릴리스**(미연결 EIP 과금 방지).
