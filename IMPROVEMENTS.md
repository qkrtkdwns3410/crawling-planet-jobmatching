# Improvements & Optimizations

기술 개선사항, 성능 튜닝, 아키텍처 결정 사항을 기록합니다.

---

## 향후 개선 예정 (Backlog)

### 영문 회사명 ↔ 한글 회사명 유사도 매칭 미지원

**현상:** 잡코리아에서 `CJ TELENIX`로 표기된 회사가 잡플래닛에는 `(주)씨제이텔레닉스`로 등록되어 있어 pg_trgm 유사도 매칭 실패. trigram은 문자 단위이므로 영문 ↔ 한글 변환을 처리할 수 없음.

**조사 결과:** 잡플래닛 API(reviews/list, landing/header)에 영문 회사명 필드가 없음. 다만 `web_site` 필드(예: `www.cjtelenix.com`)에서 도메인 기반 영문명 추출이 가능할 수 있음.

**검토한 해결 방안:**
1. `web_site` 도메인에서 영문명 추출 → DB에 `english_name` 컬럼 추가 → 영문 검색 시 해당 컬럼도 매칭
2. 검색 시 한글/영문 둘 다 시도 → 첫 번째 실패 시 fallback
3. 회사명 수동 매핑 테이블

**보류 사유:** 대부분 잡코리아에서 한글 회사명을 사용하며, 영문명만 쓰는 경우는 소수. 도메인 → 영문명 변환 정확도도 보장되지 않음.

---

## [2026-03-29] 데이터 — Unknown Company 442건 회사명 복구

### 문제
리뷰 API(`/api/v4/companies/reviews/list`) 응답에서 회사 정보는 `JOB_POSTINGS` 타입 아이템에만 포함됨. 채용공고가 없는 회사는 `JOB_POSTINGS` 아이템이 없어서 회사명을 추출하지 못하고 `Unknown Company (ID: xxx)`로 저장됨. 총 442건 발생.

### 원인
`ReviewDataService.extractCompanyInfo()`가 `JOB_POSTINGS` 아이템에서만 회사명을 추출하는 구조. 채용공고 없는 회사에 대한 fallback이 없었음.

### 해결
- `CompanyRatingUpdateService`의 `LandingHeaderData`에 `name` 필드 추가
- `CompanyRepository.updateCompanyDetails()`에 `name` 파라미터 추가 (`COALESCE`로 null-safe)
- `updateSingleCompanyRating()`에서 header API의 회사명도 함께 업데이트
- 기존 442건은 EC2에서 landing/header API 일괄 호출 스크립트로 복구 완료 (0건 남음)

### 변경 파일
- `module-crawler/.../service/CompanyRatingUpdateService.kt` — `LandingHeaderData.name` 추가, 호출부 수정
- `module-domain/.../repository/CompanyRepository.kt` — `updateCompanyDetails` name 파라미터 추가

---

## [2026-03-29] 보안 — CRITICAL 이슈 2건 수정

### 문제 1: API Key 서버 측 검증 없음
익스텐션 API(`/api/ext/*`)가 Nginx에서만 API Key를 검증하고, Spring 애플리케이션 자체에는 인증이 없었음. Nginx를 우회하여 8081 포트로 직접 접근하면 인증 없이 API 호출 가능.

### 문제 2: 크롤링 API 인증 없음
크롤링 관리 엔드포인트(`POST /api/crawling/*`)에 인증이 전혀 없었음. Security Group + Nginx IP 제한에만 의존하는 단일 방어층 구조.

### 해결
- **module-api**: `ApiKeyFilter` 추가 — `X-API-Key` 헤더를 서버 측에서 검증. OPTIONS preflight는 통과, 키 미일치 시 403 반환. 키는 환경변수(`API_KEY`)로 관리.
- **module-app**: `CrawlingAuthFilter` 추가 — POST 요청에 `Authorization: Bearer {token}` 필수. GET `/status`는 인증 없이 허용. 토큰은 환경변수(`CRAWLING_ADMIN_TOKEN`)로 관리.

### 결과

| 테스트 | 결과 |
|--------|------|
| API Key 없이 `/api/ext/*` 직접 호출 | 403 차단 |
| API Key 포함 호출 | 200 통과 |
| 토큰 없이 크롤링 POST | 403 차단 |
| 토큰 포함 크롤링 POST | 인증 통과 |
| GET `/status` (인증 불필요) | 200 통과 |

### 변경 파일
- `module-api/.../config/ApiKeyFilter.kt` — 신규
- `module-app/.../config/CrawlingAuthFilter.kt` — 신규
- `module-api/src/main/resources/application-prod.yml` — `api.security.key` 추가
- `module-app/src/main/resources/application-prod.yml` — `crawling.admin.token` 추가

---

## [2026-03-29] DB 쿼리 성능 최적화 — pg_trgm GIN 인덱스 활용

### 문제
회사명 유사도 검색(`findMostSimilarByName`)이 데이터 24만건 이상으로 늘어나면서 **~640ms**까지 느려짐. `similarity(name, :searchName) > 0.3` 쿼리가 GIN 인덱스를 무시하고 **전체 테이블 스캔(Parallel Seq Scan)**을 수행.

### 원인
PostgreSQL의 `similarity()` 함수에 임계값 비교(`> 0.3`)를 사용하면 GIN 인덱스를 활용할 수 없음. 플래너가 임계값을 인덱스 스캔에 적용할 방법이 없어서 전체 스캔 + 필터 방식으로 동작.

### 해결
- `WHERE similarity(name, :searchName) > 0.3` → `WHERE name % :searchName`으로 변경
- `%` 연산자는 GIN 인덱스를 활용하며, `pg_trgm.similarity_threshold` 설정값(0.3)을 사용
- GIN 인덱스 재생성: `CREATE INDEX idx_company_name_trgm ON companies USING gin (name gin_trgm_ops)`
- DB 레벨 임계값 설정: `ALTER DATABASE jobplanet SET pg_trgm.similarity_threshold = 0.3`

### 결과
| 지표 | 개선 전 | 개선 후 | 개선율 |
|------|---------|---------|--------|
| 쿼리 실행 시간 | 638ms (Seq Scan) | 64ms (Bitmap Index Scan) | **10배 향상** |
| API 응답 시간 (워밍업 후) | ~1.0s | ~0.22s | **4.5배 향상** |
| 스캔 방식 | Parallel Seq Scan (24만행 전체) | Bitmap Index Scan (589건 후보) | 인덱스 활용 |

### 변경 파일
- `module-domain/.../repository/CompanyRepository.kt` — `%` 연산자 쿼리로 변경

---

## [2026-03-29] 크롤링 파이프라인 — 평점 수집 통합

### 문제
회사 평점(평균 점수, 산업, 로고)은 리뷰 크롤링 완료 후 별도 API(`POST /api/crawling/update-ratings`)를 호출해야 했음. 전체 데이터셋에 대해 두 번 순회해야 하는 비효율.

### 해결
`CompanyRatingUpdateService.updateSingleCompanyRating()`을 `crawlCompany()` 파이프라인에 통합. 리뷰 저장 후 동일한 리액티브 체인에서 landing header API(`/api/v5/companies/{id}/landing/header`)를 호출.

### 결과
- 한 번의 크롤링으로 리뷰 + 평점 + 산업 + 로고 동시 수집
- 별도 `update-ratings` 실행이 불필요해짐
- 추가 지연 없음 — 리뷰 저장 후 같은 리액티브 플로우에서 실행

### 변경 파일
- `module-crawler/.../service/JobplanetCrawlingService.kt` — `updateSingleCompanyRating` 체인 연결
- `module-crawler/.../service/CompanyRatingUpdateService.kt` — `updateSingleCompanyRating()` 메서드 추가

---

## [2026-03-29] 크롬 익스텐션 — 배지 위치 겹침 수정

### 문제
잡코리아 채용 목록 테이블에서 리뷰 배지가 관심기업(하트) 버튼과 겹침. 하트 버튼이 `position: absolute; top: 29px`로 배치되어 있고, 158px 너비의 `TD.tplCo` 셀 안에서 배지와 시각적 충돌 발생.

### 해결
부모 요소 타입에 따른 조건부 배지 삽입:
- **TD 부모** (테이블 리스트): TD의 마지막 자식으로 `display: block` 배지 추가 — 기존 요소 아래에 렌더링
- **기타 부모** (SPAN 등): 요소 내부에 인라인 배지로 추가

### 변경 파일
- `module-chrome-ext/content.js` — `insertBadgeAfter()` 조건 분기 로직

---

## [2026-03-29] 인프라 — Cloudflare SSL + Nginx CORS 수정

### 문제
1. Cloudflare SSL 모드가 "Full"(오리진에 HTTPS 필요)이었으나 EC2는 HTTP만 제공 → 522 Connection Timeout
2. Nginx가 CORS preflight(OPTIONS)에서 API Key 검증을 먼저 실행해 403 반환

### 해결
1. Cloudflare SSL 모드를 "Flexible"로 변경 (Cloudflare에서 HTTPS 종료, 오리진에는 HTTP로 연결)
2. Nginx 설정에서 OPTIONS preflight 처리를 API Key 검증보다 앞에 배치

### 결과
- 크롬 익스텐션이 `https://crawling-planet.cc`로 API 정상 접근 가능
- CORS preflight 성공, 실제 요청은 API Key로 검증

---

## [2026-03-28] AWS 인프라 — 비용 최적화 단일 서버 구성

### 아키텍처
```
크롬 익스텐션 → https://crawling-planet.cc (Cloudflare CDN/SSL/DDoS 방어)
                   → Nginx (포트 80, API Key + CORS + IP 제한)
                      ├── /api/ext/*      → module-api :8081 (공개, API Key 필수)
                      └── /api/crawling/* → module-app :8080 (관리자 IP만 허용)
                   → PostgreSQL 16 (localhost만 리슨)
```

### 설계 결정
| 결정 | 근거 |
|------|------|
| EC2 Spot (t3.small) | 월 ~$12, 크롤링이 간헐적이라 유휴 시간에 CPU 크레딧 축적 |
| 단일 서버 (앱 + DB) | 개인 프로젝트, RDS 비용($15+/월) 절감 및 네트워크 레이턴시 제거 |
| Cloudflare 프록시 | 무료 SSL, CDN, DDoS 방어, 오리진 IP 은닉 |
| Nginx 리버스 프록시 | API Key 검증, CORS 처리, 관리자 엔드포인트 IP 제한 |
| S3 일일 백업 | pg_dump + cron, 30일 보관, 월 ~$0.1 |
| Terraform IaC | 재현 가능한 인프라, 버전 관리 가능 |

### 월간 비용 예상
| 리소스 | 비용 |
|--------|------|
| EC2 t3.small Spot | ~$5.8 |
| EBS 30GB gp3 | $2.4 |
| Public IPv4 | $3.6 |
| S3 백업 | ~$0.1 |
| Cloudflare | 무료 |
| **합계** | **~$12/월** |

---

## [2026-03-28] 보안 강화

| 계층 | 조치 |
|------|------|
| 네트워크 | Security Group: SSH/8080/8081은 관리자 IP만 허용, 포트 80은 Cloudflare용 개방 |
| DNS | Cloudflare 프록시 모드로 EC2 실제 IP 은닉 |
| API | Nginx에서 `X-API-Key` 헤더 검증 |
| 데이터베이스 | `listen_addresses = 'localhost'`, 외부 네트워크 노출 차단 |
| 스토리지 | EBS 암호화 활성화, S3 서버사이드 암호화 (AES256) |
| 관리자 | 크롤링 엔드포인트(`/api/crawling/*`)는 Nginx에서 관리자 IP만 허용 |
| 익스텐션 | manifest.json에 IP 미노출, 도메인 기반 HTTPS 접근만 허용 |
