# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# 빌드
./gradlew build

# 앱 실행 (local 프로필 기본)
./gradlew :module-app:bootRun

# 테스트 전체 실행
./gradlew test

# 특정 모듈 테스트
./gradlew :module-crawler:test

# 특정 테스트 클래스 실행
./gradlew :module-crawler:test --tests "com.crawling.planet.crawler.SomeTest"

# DB 기동 (PostgreSQL 16 + pgAdmin)
docker-compose up -d
```

## Architecture

잡플래닛(Jobplanet) 회사 리뷰를 크롤링하여 PostgreSQL에 저장하고, 잡코리아(JobKorea)에서 리뷰를 볼 수 있는 Chrome 확장 프로그램에 데이터를 제공하는 시스템.

### 모듈 의존성

```
module-app → module-crawler → module-domain
                            → module-http-client
```

- **module-app**: Spring Boot 진입점. REST 컨트롤러(`/api/crawling/*`, `/api/ext/*`). 크롤링 작업은 Reactor `subscribe()`로 백그라운드 실행.
- **module-crawler**: 크롤링 핵심 로직. Reactor(Flux/Mono) 기반 비동기 처리, 설정 가능한 concurrency/delay. Selenium으로 잡플래닛 로그인 후 쿠키 기반 인증. 리뷰 API는 OkHttp(Cloudflare TLS 우회), 평점 API는 WebClient. 401 발생 시 자동 토큰 갱신.
- **module-domain**: JPA 엔티티(`Company`, `Review`, `CrawlingProgress`, `CrawlingErrorLog`), Repository, DTO(`JobplanetApiResponse` 등). `kotlin-jpa` 플러그인으로 엔티티 allOpen 처리.
- **module-http-client**: 커스텀 선언적 HTTP 클라이언트 프레임워크. `@WebClientInterface` + `@EnableWebClients`로 인터페이스 기반 WebClient 프록시를 자동 생성. Feign과 유사한 패턴이지만 WebFlux 기반.
- **module-chrome-ext**: 잡코리아 페이지에서 잡플래닛 리뷰를 보여주는 Chrome Extension (Manifest V3).

### 핵심 흐름

1. `JobplanetLoginService`가 Selenium(headless Chrome)으로 잡플래닛 로그인 → 쿠키/토큰 획득 → `CookieTokenStore`에 저장
2. `JobplanetCrawlingService`가 회사 ID 범위를 Flux로 순회하며 `JobplanetApiService`를 통해 리뷰 API 호출
3. `ReviewDataService`가 API 응답을 파싱하여 Company/Review 엔티티로 변환 후 저장 (회사당 최대 3개 리뷰)
4. Chrome 확장이 `/api/ext/company/search?name=` 엔드포인트로 회사명 유사 검색 (pg_trgm)

### 주요 설정

- 크롤링 파라미터: `jobplanet.api.*` (concurrency, delayMs, startCompanyId, endCompanyId 등) - `JobplanetApiProperties`
- 인증 정보: `jobplanet.auth.*` (email, password, headless 등) - `JobplanetAuthProperties`
- DB: PostgreSQL, `spring.jpa.hibernate.ddl-auto=update`, `init-db.sql`로 추가 인덱스/뷰 생성

## 문서 업데이트 규칙 (필수)

코드 변경 시 아래 문서를 반드시 함께 업데이트해야 합니다:

- **IMPROVEMENTS.md**: 성능 개선, 버그 수정, 아키텍처 변경 등 기술적 개선사항을 한글로 상세 기록. 문제 → 원인 → 해결 → 결과 구조로 작성.
- **README.md**: 프로젝트 구조, API 엔드포인트, 실행 방법 등이 변경되면 반영.
- **RELEASE.md**: 버전 릴리스 시 변경사항, 기능 추가, 버그 수정 내역 기록.

이 규칙은 모든 커밋에 적용됩니다. 문서 업데이트 없이 코드만 커밋하지 마세요.

## 인프라 정보

- **AWS**: EC2 Spot t3.small (서울 리전), Terraform 관리 (`infra/terraform/`), 2GB 스왑 추가
- **도메인**: `crawling-planet.cc` (Cloudflare DNS + Proxy + Flexible SSL)
- **Nginx**: 리버스 프록시, API Key 인증, CORS, IP 제한
- **배포**: GitHub Actions 수동 트리거 (`workflow_dispatch`) 또는 SCP + systemd restart
- **DB 백업**: S3 일일 pg_dump (cron, 30일 보관)

## 브랜치 전략

- `master`: 프로덕션 (EC2에 배포되는 버전)
- `feature/*`: 기능 개발 브랜치 → PR → master merge
- master에 push해도 자동 배포 안 됨 (수동 배포)

## Tech Stack

- Kotlin 2.0.21 / Java 21
- Spring Boot 3.4.1 (Web + WebFlux + Data JPA)
- PostgreSQL 16 (pg_trgm 확장)
- Reactor (Flux/Mono) for async crawling
- OkHttp 4.12.0 for Cloudflare TLS bypass (JobplanetApiService)
- Selenium + WebDriverManager for browser-based auth
- kotlin-logging (oshai)
