<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-01 | Updated: 2026-04-05 -->

# module-crawler

## Purpose
잡플래닛 크롤링 핵심 로직. Selenium으로 로그인 후 쿠키 기반 인증을 유지하며, Reactor Flux/Mono로 비동기 대량 크롤링을 수행한다. 회사 정보와 리뷰를 PostgreSQL에 저장하고, 회사 상세 정보(평점/산업/로고)도 별도 API로 채운다.

## Key Files

| File | Description |
|------|-------------|
| `auth/JobplanetLoginService.kt` | Selenium headless Chrome으로 잡플래닛 로그인, 전체 쿠키 수집 |
| `auth/CookieTokenStore.kt` | 쿠키 토큰 메모리 저장소 (allCookies Map + `buildCookieString()` 포함) |
| `auth/JobplanetAuthProperties.kt` | 인증 설정 (`jobplanet.auth.*`) |
| `config/WebClientConfig.kt` | WebClient Bean 생성, 쿠키/헤더 동적 주입 필터 |
| `config/JobplanetApiProperties.kt` | API 설정 (`jobplanet.api.*`) |
| `client/JobplanetReviewClient.kt` | 선언적 HTTP 클라이언트 인터페이스 |
| `service/JobplanetCrawlingService.kt` | 크롤링 오케스트레이션 (범위/전체/DB기반) |
| `service/JobplanetApiService.kt` | 잡플래닛 API 직접 호출 |
| `service/ReviewDataService.kt` | API 응답 파싱 → Company/Review 엔티티 저장 |
| `service/CompanyRatingUpdateService.kt` | `/landing/header` API로 회사 평점/산업/로고 업데이트 |
| `diagnostics/CrawlerDiagnosticsService.kt` | API 요청/응답/에러/로그인 이벤트 기록 |
| `runner/CrawlerRunner.kt` | 앱 시작 시 자동 크롤링 트리거 (설정에 따라) |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `auth/` | Selenium 로그인 및 쿠키 관리 |
| `config/` | WebClient 및 API 프로퍼티 설정 |
| `client/` | 선언적 HTTP 클라이언트 인터페이스 |
| `service/` | 크롤링 비즈니스 로직 |
| `diagnostics/` | 진단 및 모니터링 |
| `runner/` | 실행 진입점 |

## For AI Agents

### Crawling Flow
```
LoginService.loginIfNeeded()
    └── Selenium → jobplanet.co.kr 로그인 → 35개 쿠키 수집 → CookieTokenStore 저장

JobplanetCrawlingService.crawlRange(startId, endId)
    └── Flux.range() → flatMap(concurrency=200) → delayElements(10ms)
        └── JobplanetApiService.getCompanyReviews(companyId, page=1)
            └── WebClient GET /api/v4/companies/reviews/list?company_id={id}&page=1
        └── ReviewDataService.saveFromApiResponse()
            └── Company upsert + Review 저장 (최대 3개/회사)
        └── CompanyRatingUpdateService.updateSingleCompanyRating()
            └── WebClient GET /api/v5/companies/{id}/landing/header
```

### API Endpoints Used
| API | 경로 | 응답 크기 | 용도 |
|-----|------|-----------|------|
| reviews/list | `/api/v4/companies/reviews/list?device=desktop&company_id={id}&page=1` | ~50KB | 회사+리뷰 데이터 |
| landing/header | `/api/v5/companies/{id}/landing/header` | ~2KB | 평점/산업/로고 |

### Authentication — CRITICAL
잡플래닛은 `is_review_view_status: true`를 받아야 리뷰 데이터가 내려옴.
이를 위해 **access_token 하나가 아닌 전체 쿠키(~35개)**를 Cookie 헤더로 전송해야 함.

```kotlin
// CookieTokenStore.buildCookieString() — WebClientConfig/JobplanetApiService 양쪽에서 호출
cookieTokenStore.buildCookieString()
// allCookies가 있으면 전체 조인, 없으면 access_token/refresh_token fallback
// → "access_token=xxx; refresh_token=yyy; _ga=zzz; ..." (35개)
```

401 발생 시 자동 재로그인 후 `fetchAndSaveCompany()` 재시도 (`crawlCompany()` 내 중첩 onErrorResume).

### Performance Settings (application-local.yml)
```yaml
jobplanet.api:
  concurrency: 200
  delay-ms: 10
  start-company-id: 1
  end-company-id: 500000
```
- 처리량: ~3,140 회사/분 (concurrency=200, delay=10ms)
- HikariCP `maximum-pool-size`는 50 이상으로 설정 필요

### Working In This Directory
- **ReviewDataService**: API의 `overall` → entity의 `rating`, `title` → `summary` (필드명 다름)
- **CompanyRatingUpdateService**: `rateTotalAvg`(소수점 1자리 문자열) → `averageRating` Double 변환
- 401 에러 → 자동 재로그인 → 재시도 (crawlCompany 내)
- 빈 회사(리뷰 없음)는 스킵하여 불필요한 DB write 방지
- `crawlReviewsForExistingCompanies()`: DB에 이미 있는 회사만 순회 (ID 범위 크롤링보다 4배 빠름)

### Testing Requirements
- `ReviewDataServiceE2ETest`: 실제 잡플래닛 API 호출 (로그인 필요)
- WebClient 단위 테스트: WireMock으로 API 응답 목킹

## Dependencies

### Internal
- `module-domain` (엔티티, Repository)
- `module-http-client` (선언적 HTTP 클라이언트)

### External
- Spring WebFlux (WebClient)
- Reactor + reactor-extra (retry)
- Selenium WebDriver 4.27 + WebDriverManager 5.9.2
- Netty (ReactorClientHttpConnector)

<!-- MANUAL: -->
