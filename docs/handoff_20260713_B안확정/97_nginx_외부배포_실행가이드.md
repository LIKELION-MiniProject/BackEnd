# 97 — nginx 외부배포 실행 가이드 (경로 1)

> **목표** : 외부 사용자 누구나 `http://15.164.84.176/` 로 PassPort를 접속·사용. FE와 API를 **같은 출처**로 묶어 CORS·mixed-content 없이 동작.
> **원칙** : nginx는 앞단에서 길만 나눈다. **백엔드(Spring) 코드·jar는 전혀 건드리지 않는다** → 코드 동결 유지.
> **설정 파일** : [`deploy/nginx/passport.conf`](../../deploy/nginx/passport.conf)
> **구조** : `브라우저 → EC2:80(nginx) ─┬ /api/... → Spring(:8080)  └ 그 외 → FE dist`

---

## 담당 분담

| 단계 | 담당 | 내용 |
|---|---|---|
| A | **인서** | FE를 `VITE_API_BASE_URL=/api/v1`(상대경로)로 빌드 → `dist/` 전달 |
| B | **원석** | `dist` 업로드 → nginx 설치·설정 → 재시작 |
| C | **원석** | AWS 보안그룹 인바운드 **80 = `0.0.0.0/0`** 추가 |
| D | **전원** | `http://15.164.84.176/` 접속 확인 |

---

## A. [인서] FE 빌드 (상대경로 API)

FE 프로젝트에서:

```bash
# .env.production 에 API 베이스를 상대경로로 지정 (같은 출처라 도메인 불필요)
echo "VITE_API_BASE_URL=/api/v1" > .env.production

npm install
npm run build          # → dist/ 생성
```

> 🔴 **핵심**: API 주소를 `http://15.164.84.176:8080/api/v1` 같은 절대경로가 아니라 **`/api/v1`(상대경로)** 로 둔다. 그래야 nginx가 같은 출처로 프록시하면서 CORS·mixed-content가 사라진다.
> 산출물 `dist/` 폴더를 원석에게 전달(압축 zip 권장).

---

## B. [원석] EC2에 dist 업로드 + nginx 설치·설정

### B-1. dist 업로드 (로컬 PowerShell)

```powershell
# 인서에게 받은 dist 폴더 경로를 <DIST> 자리에 넣는다.
$key = "$env:USERPROFILE\Downloads\PassPort-key.pem"
scp -i $key -r "<DIST>" ubuntu@15.164.84.176:~/web-dist
# 설정 파일도 함께 올린다 (레포 안 파일)
scp -i $key "C:\Users\송원석\Documents\LikeLion_MiniProject_Team_3\deploy\nginx\passport.conf" ubuntu@15.164.84.176:~/passport.conf
```

### B-2. nginx 설치 + 배치 (EC2 SSH)

```bash
ssh -i "$env:USERPROFILE\Downloads\PassPort-key.pem" ubuntu@15.164.84.176   # PowerShell에서 접속
```

접속 후 EC2에서:

```bash
# 1) nginx 설치
sudo apt update && sudo apt install -y nginx

# 2) FE 정적파일을 /var/www/passport 로 이동
#    (/home/ubuntu 아래는 www-data 가 못 읽어 403 나는 트랩이 있어 /var/www 사용)
sudo mkdir -p /var/www/passport
sudo cp -r ~/web-dist/* /var/www/passport/
sudo chown -R www-data:www-data /var/www/passport

# 3) 설정 배치 + 기본 사이트 비활성화
sudo cp ~/passport.conf /etc/nginx/sites-available/passport
sudo ln -sf /etc/nginx/sites-available/passport /etc/nginx/sites-enabled/passport
sudo rm -f /etc/nginx/sites-enabled/default

# 4) 문법 검사 → 반영
sudo nginx -t                     # "syntax is ok / test is successful" 나와야 함
sudo systemctl restart nginx
sudo systemctl enable nginx       # 재부팅 후 자동 기동
```

### B-3. 서버 내부에서 먼저 확인 (포트 열기 전)

```bash
curl -s -o /dev/null -w "FE  %{http_code}\n" http://localhost/
curl -s -o /dev/null -w "API %{http_code}\n" http://localhost/api/v1/requirements/BIGDATA_AI
# FE 200 / API 401(토큰없음이라 정상) 이면 프록시·정적서빙 모두 OK
```

---

## C. [원석] AWS 보안그룹 80번 개방

AWS 콘솔 → EC2 → 인스턴스 `PassPort-Server` → 보안 → 보안 그룹 → 인바운드 규칙 편집:

| 유형 | 프로토콜 | 포트 | 소스 |
|---|---|---|---|
| HTTP | TCP | **80** | `0.0.0.0/0` |

> 저장 즉시 적용. (기존 `22`=내 IP, `8080`=`0.0.0.0/0` 는 유지. 8080은 디버깅용으로 남겨도 되고, 외부공개를 80으로 일원화하려면 닫아도 무방)

---

## D. [전원] 최종 접속 확인

브라우저에서 **`http://15.164.84.176/`** :

- [ ] 로그인 화면이 뜬다 (FE 정적 서빙 OK)
- [ ] 로그인 → 대시보드까지 흐름 정상 (API 프록시 OK)
- [ ] 새로고침해도 화면 유지 (SPA fallback OK)
- [ ] 브라우저 콘솔에 CORS 에러 없음
- [ ] 다른 사람 기기·다른 네트워크에서도 접속됨 (신영님 등)

> ⚠️ `http://`(비HTTPS)라 브라우저 주소창에 "안전하지 않음"이 뜬다 — 정상. 도메인·인증서 없이 IP로 여는 데모라 그렇다. 발표엔 문제없다.

---

## 트러블슈팅

| 증상 | 원인·해결 |
|---|---|
| FE는 뜨는데 API가 **404** | `passport.conf` 의 `proxy_pass` 끝에 `/` 를 붙였는지 확인 → **붙이면 안 됨** (`/api` 가 잘림) |
| 접속 시 **403 Forbidden** | 정적파일이 `/home/ubuntu` 아래 있어 www-data가 못 읽음 → `/var/www/passport` 로 옮겼는지 확인 |
| API가 **502 Bad Gateway** | Spring(:8080)이 안 떠 있음 → `sudo systemctl status passport` 확인 |
| `nginx -t` 실패 | 설정 파일 경로/문법 오류 → 메시지의 줄번호 확인 |
| 브라우저가 접속 자체 안 됨 | 보안그룹 80 미개방(C단계) 또는 nginx 미기동 |
| AI 분석(POST)이 끊김 | `proxy_read_timeout 120s` 반영됐는지 확인 (AI 최대 60초) |

---

## 롤백 (문제 시)

nginx만 끄면 기존 `:8080` 직접 접속으로 즉시 되돌아간다. 백엔드는 안 건드렸으므로 영향 없음.

```bash
sudo systemctl stop nginx      # 필요 시 sudo systemctl disable nginx
# → http://15.164.84.176:8080 (기존 경로) 그대로 살아있음
```
