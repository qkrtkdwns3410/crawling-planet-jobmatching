# Crawling Planet - Jobplanet Review Matching

> **[Release Notes (v1.0.0)](RELEASE.md)** - 2026-03-28

잡코리아 채용 공고에서 잡플래닛 회사 리뷰를 바로 확인할 수 있는 크롤링 시스템 + 크롬 익스텐션 프로젝트

## 프로젝트 구조

```
crawling-planet-jobmatching/
├── module-app/          # 크롤링 관리 서버 (포트 8080)
├── module-api/          # 익스텐션 API 서버 (포트 8081)
├── module-crawler/      # 잡플래닛 크롤링 로직
├── module-domain/       # JPA 엔티티, Repository
├── module-http-client/  # WebClient 선언적 인터페이스
├── module-chrome-ext/   # 크롬 익스텐션
└── docker-compose.yml   # PostgreSQL + pgAdmin
```

## 기술 스택

- **Kotlin 2.0** + **Spring Boot 3.4** (Java 21)
- **WebFlux (Reactor)** - 비동기 크롤링
- **JPA/Hibernate** + **PostgreSQL 16**
- **Selenium** - 잡플래닛 로그인 자동화
- **pg_trgm** - 회사명 유사도 검색
- **Chrome Extension (Manifest V3)** - 잡코리아 연동

## 실행 방법

### 1. PostgreSQL 실행

```bash
docker compose up -d postgres
```

### 2. 환경변수 설정

```bash
export JOBPLANET_PASSWORD="잡플래닛 비밀번호"
```

잡플래닛 계정 이메일은 `application-local.yml`에서 설정합니다.

### 3. 크롤링 서버 실행 (module-app)

```bash
./gradlew :module-app:bootRun
```

### 4. API 서버 실행 (module-api)

```bash
./gradlew :module-api:bootRun
```

### 5. 크롬 익스텐션 설치

1. `chrome://extensions` 접속
2. 개발자 모드 활성화
3. "압축해제된 확장 프로그램을 로드합니다" 클릭
4. `module-chrome-ext` 폴더 선택

## API 엔드포인트

### 크롤링 관리 (module-app, :8080)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/crawling/status` | 크롤링 상태 조회 |
| POST | `/api/crawling/company/{id}` | 단일 회사 크롤링 |
| POST | `/api/crawling/range?startId=&endId=` | 범위 크롤링 (백그라운드) |
| POST | `/api/crawling/start` | 전체 크롤링 시작 |
| POST | `/api/crawling/update-reviews` | DB 회사 기반 리뷰 수집 |
| POST | `/api/crawling/update-ratings` | 회사 상세 정보 업데이트 (rating, industry, logo) |

### 익스텐션 API (module-api, :8081)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/ext/company/search?name=` | 회사명으로 검색 (유사도 매칭) |
| GET | `/api/ext/company/{id}/reviews` | 회사 ID로 리뷰 조회 |

## 크롤링 흐름

```
1. Selenium 로그인 → 쿠키 획득
2. /api/v4/companies/reviews/list API로 회사 + 리뷰 수집
   - 회사 정보 저장 (있으면 업데이트, 없으면 insert)
   - 리뷰 3개씩 저장 (rating, summary, pros, cons, scores)
3. /api/v5/companies/{id}/landing/header API로 회사 상세 업데이트
   - average_rating, industry, logo_url 채우기
```

## 데이터 규모

- 회사: ~241,000개
- 리뷰: ~485,000개 (회사당 최대 3개)
- rating 채워진 비율: 98.9%

## 크롤링 성능 설정

`application-local.yml`에서 조정 가능:

```yaml
jobplanet:
  api:
    concurrency: 200    # 동시 요청 수
    delay-ms: 10        # 요청 간 딜레이
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # DB 커넥션 풀
```

| 설정 | 속도 |
|------|------|
| concurrency 10, delay 200ms | ~240 회사/분 |
| concurrency 50, delay 30ms | ~1,080 회사/분 |
| concurrency 200, delay 10ms | ~3,140 회사/분 |
