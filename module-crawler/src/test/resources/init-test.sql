-- Testcontainers 초기화 스크립트
-- pg_trgm 확장 활성화 (회사명 유사도 검색에 필요)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
