# Release Notes

## v1.0.0 (2026-03-28)

첫 번째 프로덕션 릴리스. 잡플래닛 크롤링 시스템 + 크롬 익스텐션 + AWS 인프라 구축 완료.

### Features

- **크롤링 시스템**
  - Selenium 기반 잡플래닛 자동 로그인 및 쿠키 인증
  - Reactive WebClient로 비동기 대량 크롤링 (동시성 최대 200)
  - 회사 50만개 대상 리뷰 수집 (회사당 최대 3개)
  - 401 에러 시 자동 재인증 + 지수 백오프 재시도

- **멀티모듈 아키텍처**
  - `module-app` - 크롤링 관리 서버 (포트 8080)
  - `module-api` - 크롬 익스텐션 API 서버 (포트 8081)
  - `module-crawler` - 크롤링 비즈니스 로직
  - `module-domain` - JPA 엔티티, Repository
  - `module-http-client` - 선언적 WebClient 인터페이스

- **크롬 익스텐션 (Manifest V3)**
  - 잡코리아 채용 공고에 잡플래닛 평점 배지 자동 표시
  - 배지 클릭 시 리뷰 드롭다운 (장점/단점/경영진에게 바라는 점)
  - 잡플래닛 리뷰 페이지 바로가기 링크
  - pg_trgm 기반 회사명 유사도 매칭

- **AWS 인프라 (Terraform)**
  - EC2 Spot (t3.small) + PostgreSQL 16 단일 서버 구성
  - Cloudflare DNS 프록시로 서버 IP 숨김
  - Nginx 리버스 프록시 + API Key 인증
  - S3 일일 DB 백업 (30일 보관)
  - Security Group 최소 권한 원칙 적용

### Tech Stack

| 항목 | 기술 |
|------|------|
| Language | Kotlin 2.0, Java 21 |
| Framework | Spring Boot 3.4, WebFlux |
| Database | PostgreSQL 16, HikariCP |
| Crawling | Selenium, Reactor Netty |
| Infra | AWS EC2 Spot, Terraform, Nginx |
| CDN/DNS | Cloudflare (HTTPS, DDoS 방어) |
| Extension | Chrome Manifest V3 |

### Infrastructure

```
크롬 익스텐션 -> https://crawling-planet.cc (Cloudflare)
                   -> Nginx (API Key 검증)
                      -> module-api (:8081) -> PostgreSQL
관리자 -> EC2 직접 접속 -> module-app (:8080) -> 크롤링 실행
```

- 월 예상 비용: ~$12 (Spot) / ~$25 (On-Demand)
- 서울 리전 (ap-northeast-2)
- EBS 30GB gp3 (암호화)

---

## v0.4.0 (2026-04-04)

### 기능 개선
- **Cloudflare TLS 우회**: JobplanetApiService를 WebClient(Reactor Netty)에서 OkHttp로 전환 — Cloudflare TLS 핑거프린팅 차단(403) 해소, 크롤링 재개
- **크롤링 완료 후 평점 자동 업데이트**: `startCrawling()` 완료 시 `updateAllRatings()` 자동 체이닝

### 보안 수정
- Auth 필터 타이밍 공격 취약점 수정: `!=` → `MessageDigest.isEqual()` (constant-time 비교)
- Auth 필터 fail-open → fail-closed: 토큰 미설정 시 503 반환
- Chrome Extension `content.js` innerHTML XSS → DOM API 전환
- `/api/crawling/range` 입력 범위 검증 추가 (≤ 100,000)

### 인프라/운영
- EC2 t3.small 메모리 최적화: JVM Xms 축소, G1GC, HikariCP 50→20, 2GB 스왑
- PostgreSQL max_connections=30, effective_cache_size=512MB
- deploy.yml JOBPLANET_EMAIL/PASSWORD 환경변수 누락 수정 (재배포 시 로그인 실패 방지)
- GitHub Secret EC2_HOST 신규 IP(54.116.115.111)로 업데이트

---

## Changelog

### 2026-03-28
- `b5a8f8a` feat: AWS 인프라 구성 및 크롬 익스텐션 보안 강화
- `923fc0c` docs: README 작성
- `3f358d2` feat: 익스텐션 API 응답에 jobplanetId 추가
- `e656eeb` feat: 크롬 익스텐션 개선 - 잡코리아 셀렉터 확장 및 잡플래닛 링크 추가
- `8ab59cd` refactor: module-api 분리 및 컨트롤러 정리
- `6a7b38d` feat: 회사 상세 정보 배치 업데이트 및 DB 인덱스 최적화
- `299bcbf` fix: 잡플래닛 API 리뷰 DTO 필드 매핑 수정
- `d528e31` feat: 크롤링 인증 개선 및 속도 최적화

### 2026-02-18
- `57bf1da` feat: 크롤링 시스템 리팩토링 + 크롬 익스텐션 구현
- `e36d423` feat: 데이터베이스 및 크롤링 서비스 개선
- `3367df9` feat: 빌드 설정 및 애플리케이션 설정 변경
- `8662685` feat: 인프라 설정 추가
- `2eb6fde` feat: 크롤링 컨트롤러 추가
- `e2f5dd1` feat: 크롤러 서비스 리팩토링
- `07de075` feat: 도메인 모듈 추가
- `ed328f7` feat: HTTP 클라이언트 모듈 추가
- `d479172` feat: 초기 프로젝트 설정 및 크롤러 기능 추가
