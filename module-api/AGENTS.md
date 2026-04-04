<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-01 | Updated: 2026-04-05 -->

# module-api

## Purpose
Chrome Extension이 소비하는 회사 검색/리뷰 조회 REST API 서버 (포트 8081). pg_trgm 유사도 검색으로 회사명 퍼지 매칭을 지원하며, `crawling-planet.cc` 도메인을 통해 외부에 노출된다.

## Key Files

| File | Description |
|------|-------------|
| `ApiApplication.kt` | Spring Boot 메인 클래스 (포트 8081) |
| `controller/ExtensionApiController.kt` | 회사 검색 및 리뷰 조회 엔드포인트 |
| `config/ApiKeyFilter.kt` | `X-API-Key` 헤더 기반 인증 필터 |
| `config/CorsConfig.kt` | jobkorea.co.kr 도메인 CORS 허용 |
| `config/DatabaseIndexInitializer.kt` | pg_trgm 인덱스 초기화 |
| `src/test/.../ExtensionApiControllerE2ETest.kt` | E2E 테스트 |
| `src/main/resources/application-prod.yml` | 프로덕션 설정 |

## For AI Agents

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/ext/company/search?name={회사명}` | pg_trgm 유사도 검색, 가장 유사한 1건 반환 |
| GET | `/api/ext/company/{id}/reviews` | 내부 DB id로 회사+리뷰 조회 |

### Response Structure
```json
{
  "companyId": 123,
  "jobplanetId": 407823,
  "companyName": "삼성전자",
  "rating": 3.7,
  "industry": "전기/전자",
  "logoUrl": "https://...",
  "reviewCount": 3,
  "reviews": [
    {
      "id": 1,
      "rating": 4.0,
      "summary": "...",
      "pros": "...",
      "cons": "...",
      "toManagement": "...",
      "reviewYear": "2025",
      "occupationName": "개발",
      "employStatusName": "현직"
    }
  ]
}
```

### Working In This Directory
- `jobplanetId` 응답 포함 — Chrome Extension이 잡플래닛 링크 생성에 사용:
  `https://www.jobplanet.co.kr/companies/{jobplanetId}/reviews/`
- `findMostSimilarByName(name)`: pg_trgm similarity threshold 0.3 이상인 1건만 반환 (결과 없으면 `null`)
- `findTop3ByCompanyIdOrderByReviewCreatedAtDesc`: 최신 리뷰 3개만 반환
- module-app(8080)과 다른 서버 — 두 서버가 동일 DB를 공유

### Testing Requirements
- `ExtensionApiControllerE2ETest`: 실제 PostgreSQL 필요 (pg_trgm)
- `./gradlew :module-api:test`

## Dependencies

### Internal
- `module-domain` (CompanyRepository, ReviewRepository)

### External
- Spring Boot Web
- Spring Data JPA
- PostgreSQL (pg_trgm)

<!-- MANUAL: -->
