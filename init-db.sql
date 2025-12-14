-- 잡플래닛 크롤링 데이터베이스 초기화 스크립트

-- 확장 설치 (전문 검색 지원)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 회사 테이블은 JPA가 자동 생성하므로 여기서는 인덱스만 추가
-- (테이블 생성 후 실행 필요)

-- 크롤링 진행 상태 추적 테이블
CREATE TABLE IF NOT EXISTS crawling_progress (
    id SERIAL PRIMARY KEY,
    start_company_id BIGINT NOT NULL,
    end_company_id BIGINT NOT NULL,
    current_company_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    companies_processed BIGINT DEFAULT 0,
    reviews_collected BIGINT DEFAULT 0,
    errors_count BIGINT DEFAULT 0,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- 크롤링 에러 로그 테이블
CREATE TABLE IF NOT EXISTS crawling_error_log (
    id SERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    error_type VARCHAR(100),
    error_message TEXT,
    stack_trace TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_crawling_progress_status ON crawling_progress(status);
CREATE INDEX IF NOT EXISTS idx_crawling_error_log_company_id ON crawling_error_log(company_id);
CREATE INDEX IF NOT EXISTS idx_crawling_error_log_created_at ON crawling_error_log(created_at);

-- 유용한 뷰 생성
CREATE OR REPLACE VIEW v_company_review_stats AS
SELECT 
    c.id,
    c.jobplanet_id,
    c.name,
    c.industry,
    c.average_rating,
    COUNT(r.id) as actual_review_count,
    COUNT(CASE WHEN r.status = 3 THEN 1 END) as normal_review_count,
    COUNT(CASE WHEN r.status = 12 THEN 1 END) as blinded_review_count,
    AVG(r.rating) as calculated_avg_rating
FROM companies c
LEFT JOIN reviews r ON c.id = r.company_id
GROUP BY c.id, c.jobplanet_id, c.name, c.industry, c.average_rating;

COMMENT ON TABLE crawling_progress IS '크롤링 작업 진행 상태 추적';
COMMENT ON TABLE crawling_error_log IS '크롤링 중 발생한 에러 로그';
COMMENT ON VIEW v_company_review_stats IS '회사별 리뷰 통계 뷰';


