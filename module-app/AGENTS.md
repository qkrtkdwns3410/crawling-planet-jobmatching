<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-01 | Updated: 2026-04-05 -->

# module-app

## Purpose
크롤링 작업을 제어하는 REST API 서버 (포트 8080). Spring Boot 진입점이며, 크롤링 작업을 백그라운드에서 실행하고 진단 정보를 제공한다. Nginx 리버스 프록시 뒤에서 운영.

## Key Files

| File | Description |
|------|-------------|
| `CrawlingPlanetApplication.kt` | Spring Boot 메인 클래스 (`@SpringBootApplication`) |
| `controller/CrawlingController.kt` | 크롤링 제어 REST API 엔드포인트 |
| `config/CrawlingAuthFilter.kt` | API 키 인증 필터 |
| `config/CorsConfig.kt` | CORS 허용 설정 |
| `config/DatabaseIndexInitializer.kt` | 앱 시작 시 DB 인덱스 생성 (pg_trgm 포함) |
| `dto/CrawlingResponses.kt` | `CrawlResponse`, `StatusResponse` DTO |
| `src/test/.../CrawlingControllerE2ETest.kt` | 컨트롤러 E2E 테스트 |
| `src/main/resources/application.yml` | 공통 설정 |
| `src/main/resources/application-local.yml` | 로컬 설정 (concurrency=200, delay=10ms) |
| `src/main/resources/application-prod.yml` | 프로덕션 설정 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/main/kotlin/.../controller/` | REST API 컨트롤러 |
| `src/main/kotlin/.../config/` | 필터, CORS, DB 초기화 |
| `src/main/kotlin/.../dto/` | 응답 DTO |
| `src/main/resources/` | 프로파일별 설정 파일 |

## For AI Agents

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/crawling/status` | DB 회사/리뷰 수 조회 |
| POST | `/api/crawling/company/{companyId}` | 단일 회사 동기 크롤링 |
| POST | `/api/crawling/range?startId=&endId=` | 범위 크롤링 (백그라운드) |
| POST | `/api/crawling/start` | 전체 크롤링 시작 (백그라운드) |
| POST | `/api/crawling/update-reviews` | DB 회사 기반 리뷰 수집 (백그라운드) |
| POST | `/api/crawling/update-ratings` | 회사 평점/산업/로고 업데이트 (백그라운드) |
| GET | `/api/crawling/diagnostics` | 진단 스냅샷 조회 |

### Background Execution Pattern
긴 작업(range, start, update-*)은 HTTP 타임아웃을 피하기 위해 즉시 200 반환 후 백그라운드 실행:
```kotlin
crawlingService.crawlRange(startId, endId)
    .subscribe(onNext = { ... }, onError = { ... })
return ResponseEntity.ok(mapOf("status" to "started", "message" to "..."))
```

### Working In This Directory
- `diagnosticsService.recordJobStarted/Finished()` 호출로 작업 추적
- `loginService.loginIfNeeded()`: 모든 크롤링 엔드포인트 시작 전 호출
- 인증: `CrawlingAuthFilter`가 `X-API-Key` 헤더 검증 (prod 환경)
- 포트: 기본 8080, Nginx가 80/443 → 8080으로 프록시

### Testing Requirements
- `CrawlingControllerE2ETest`: 실제 DB 연결 필요
- 로컬 테스트: `./gradlew :module-app:test`

## Dependencies

### Internal
- `module-domain` (Repository)
- `module-crawler` (서비스 클래스들)

### External
- Spring Boot Web + WebFlux
- Spring Data JPA

<!-- MANUAL: -->
