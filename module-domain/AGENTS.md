<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-01 | Updated: 2026-04-05 -->

# module-domain

## Purpose
JPA 엔티티, Repository 인터페이스, API 응답 DTO를 담당하는 도메인 계층. 다른 모든 모듈이 이 모듈에 의존하며, 비즈니스 로직은 포함하지 않는다.

## Key Files

| File | Description |
|------|-------------|
| `entity/Company.kt` | 회사 엔티티 — jobplanetId(UK), name, averageRating, industry, logoUrl 등 |
| `entity/Review.kt` | 리뷰 엔티티 — rating, pros/cons, 개별 점수(growth/salary/worklife/culture/management), ReviewStatus enum |
| `entity/CrawlingProgress.kt` | 크롤링 범위별 진행 상태 추적 |
| `entity/CrawlingErrorLog.kt` | 크롤링 에러 로그 |
| `repository/CompanyRepository.kt` | 회사 CRUD + pg_trgm 유사도 검색 + 네이티브 쿼리 업데이트 |
| `repository/ReviewRepository.kt` | 리뷰 CRUD + 상태 필터링 + Top3 조회 |
| `dto/JobplanetApiResponse.kt` | 잡플래닛 API 응답 역직렬화 DTO |
| `build.gradle.kts` | 의존성: Spring Data JPA, PostgreSQL, Validation, Jackson |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/main/kotlin/.../entity/` | JPA 엔티티 클래스 |
| `src/main/kotlin/.../repository/` | Spring Data JPA Repository 인터페이스 |
| `src/main/kotlin/.../dto/` | 외부 API 응답 DTO |

## For AI Agents

### Entity Key Fields
**Company**
```kotlin
jobplanetId: Long        // 잡플래닛 회사 ID (unique)
name: String             // 회사명
averageRating: Double?   // 평균 평점 (landing/header API로 채움)
industry: String?        // 산업 분류
logoUrl: String?         // 로고 URL
reviewCount: Int         // 저장된 리뷰 수
```

**Review**
```kotlin
jobplanetReviewId: Long  // 잡플래닛 리뷰 ID (unique)
status: Int              // 3=정상, 12=게시중단 (ReviewStatus enum)
rating: Double?          // 총점 — API의 `overall` 필드에서 매핑
growthScore: Double?     // API의 `score.advancementRating`
salaryScore: Double?     // API의 `score.compensationRating`
workLifeBalanceScore: Double? // API의 `score.worklifeBalanceRating`
cultureScore: Double?    // API의 `score.cultureRating`
managementScore: Double? // API의 `score.managementRating`
summary: String?         // API의 `title` 필드에서 매핑
```

### Critical DTO Mapping (잡플래닛 API → Entity)
잡플래닛 실제 API 응답 필드명과 엔티티 필드명이 다름:

| API JSON 필드 | DTO 필드 | Entity 필드 |
|---|---|---|
| `overall` | `overall` | `rating` |
| `title` | `title` | `summary` |
| `score.advancementRating` | `score.advancementRating` | `growthScore` |
| `score.compensationRating` | `score.compensationRating` | `salaryScore` |
| `score.worklifeBalanceRating` | `score.worklifeBalanceRating` | `workLifeBalanceScore` |
| `helpful_count` | `helpfulCount` | `likeCount` |
| `message_to_management` | `messageToManagement` | `toManagement` |

### Repository Special Queries
```kotlin
// pg_trgm 유사도 검색 (threshold 0.3 이상)
companyRepository.findSimilarByName("삼성전자", 5)

// 리뷰 부족 회사 조회 (DB 기반 크롤링용)
companyRepository.findCompaniesNeedingReviews(PageRequest.of(0, 1000))

// 평점 없는 회사 조회 (rating 업데이트용)
companyRepository.findCompaniesNeedingRatingUpdate(pageable)

// 최신 리뷰 3개 조회 (Extension API용)
reviewRepository.findTop3ByCompanyIdOrderByReviewCreatedAtDesc(companyId)
```

### Working In This Directory
- `kotlin-jpa` 플러그인으로 엔티티 allOpen 처리됨 — 별도 `open` 키워드 불필요
- `@PreUpdate`로 `updatedAt` 자동 갱신
- equals/hashCode는 비즈니스 키(`jobplanetId`, `jobplanetReviewId`) 기준으로 구현됨
- DTO 변경 시 반드시 `ReviewDataService.kt`의 매핑 로직도 함께 확인

### Testing Requirements
- Repository 테스트는 실제 PostgreSQL 필요 (pg_trgm 네이티브 쿼리 때문)
- TestContainers로 PostgreSQL 16 + pg_trgm 확장 초기화 필요

## Dependencies

### Internal
- 없음 (최하위 모듈)

### External
- Spring Data JPA
- PostgreSQL Driver
- Hibernate Validator
- Jackson (JSON 역직렬화)

<!-- MANUAL: -->
