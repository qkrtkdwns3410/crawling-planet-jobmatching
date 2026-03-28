# Improvements & Optimizations

Technical improvements, performance tuning, and architectural decisions documented for future reference.

---

## [2026-03-29] Database Query Performance — pg_trgm GIN Index Optimization

### Problem
Company name similarity search (`findMostSimilarByName`) degraded to **~640ms** as dataset grew to 241K+ companies. The query used `similarity(name, :searchName) > 0.3` which forced a **Parallel Sequential Scan** across the entire table, ignoring the existing GIN index.

### Root Cause
PostgreSQL's `similarity()` function with a threshold comparison (`> 0.3`) cannot leverage GIN indexes. The planner has no way to push the threshold into the index scan, resulting in a full table scan + filter.

### Solution
- Replaced `WHERE similarity(name, :searchName) > 0.3` with `WHERE name % :searchName`
- The `%` operator is GIN-index-aware and uses `pg_trgm.similarity_threshold` (set to 0.3)
- Recreated GIN index: `CREATE INDEX idx_company_name_trgm ON companies USING gin (name gin_trgm_ops)`
- Set database-level threshold: `ALTER DATABASE jobplanet SET pg_trgm.similarity_threshold = 0.3`

### Result
| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Query execution | 638ms (Seq Scan) | 64ms (Bitmap Index Scan) | **10x faster** |
| API response (warm) | ~1.0s | ~0.22s | **4.5x faster** |
| Scan type | Parallel Seq Scan (241K rows) | Bitmap Index Scan (589 candidates) | Index utilized |

### Files Changed
- `module-domain/.../repository/CompanyRepository.kt` — `%` operator queries

---

## [2026-03-29] Crawling Pipeline — Rating Collection Integration

### Problem
Company ratings (average score, industry, logo) required a separate API call (`POST /api/crawling/update-ratings`) after the main review crawling completed. This meant two full passes over the dataset.

### Solution
Integrated `CompanyRatingUpdateService.updateSingleCompanyRating()` into the `crawlCompany()` pipeline. After saving reviews, the landing header API (`/api/v5/companies/{id}/landing/header`) is called in the same reactive chain.

### Result
- Single crawl pass now collects: reviews + ratings + industry + logo
- Eliminated the need for separate `update-ratings` execution
- No additional latency — header API call runs after review save in the same reactive flow

### Files Changed
- `module-crawler/.../service/JobplanetCrawlingService.kt` — chained `updateSingleCompanyRating`
- `module-crawler/.../service/CompanyRatingUpdateService.kt` — added `updateSingleCompanyRating()` method

---

## [2026-03-29] Chrome Extension — Badge Positioning Fix

### Problem
Review badges overlapped with the favorite (heart) button on JobKorea's job listing table. The heart button uses `position: absolute; top: 29px` inside a 158px-wide `TD.tplCo` cell, causing visual collision with the injected badge.

### Solution
Conditional badge insertion based on parent element type:
- **TD parent** (table listing): Append badge as last child of TD with `display: block` — renders below all existing elements
- **Other parents** (SPAN, etc.): Append inside the element as inline badge

### Files Changed
- `module-chrome-ext/content.js` — `insertBadgeAfter()` conditional logic

---

## [2026-03-29] Infrastructure — Cloudflare SSL + Nginx CORS

### Problem
1. Cloudflare SSL mode was "Full" (expects HTTPS on origin), but EC2 only serves HTTP → 522 Connection Timeout
2. Nginx returned 403 on CORS preflight (OPTIONS) because API key validation ran before CORS handling

### Solution
1. Changed Cloudflare SSL mode to "Flexible" (HTTPS termination at Cloudflare, HTTP to origin)
2. Moved OPTIONS preflight handling before API key validation in Nginx config

### Result
- Chrome extension can now reach the API via `https://crawling-planet.cc`
- CORS preflight succeeds, actual requests validated with API key

---

## [2026-03-28] AWS Infrastructure — Cost-Optimized Single Server

### Architecture
```
Chrome Extension → https://crawling-planet.cc (Cloudflare CDN/SSL/DDoS)
                      → Nginx (port 80, API key + CORS + IP restriction)
                         ├── /api/ext/*      → module-api :8081 (public, API key required)
                         └── /api/crawling/* → module-app :8080 (admin IP only)
                      → PostgreSQL 16 (localhost only)
```

### Design Decisions
| Decision | Rationale |
|----------|-----------|
| EC2 Spot (t3.small) | ~$12/month, crawling is intermittent so burst credits accumulate during idle |
| Single server (app + DB) | Personal project, eliminates RDS cost ($15+/month) and network latency |
| Cloudflare Proxy | Free SSL, CDN, DDoS protection, hides origin IP |
| Nginx reverse proxy | API key validation, CORS, IP restriction for admin endpoints |
| S3 daily backup | pg_dump via cron, 30-day retention, ~$0.1/month |
| Terraform IaC | Reproducible infrastructure, version controlled |

### Monthly Cost Estimate
| Resource | Cost |
|----------|------|
| EC2 t3.small Spot | ~$5.8 |
| EBS 30GB gp3 | $2.4 |
| Public IPv4 | $3.6 |
| S3 backup | ~$0.1 |
| Cloudflare | Free |
| **Total** | **~$12/month** |

---

## [2026-03-28] Security Hardening

| Layer | Measure |
|-------|---------|
| Network | Security Group: SSH/8080/8081 restricted to admin IP, port 80 open for Cloudflare |
| DNS | Cloudflare Proxy mode hides EC2 IP |
| API | `X-API-Key` header validation in Nginx |
| Database | `listen_addresses = 'localhost'`, not exposed to network |
| Storage | EBS encryption enabled, S3 server-side encryption (AES256) |
| Admin | Crawling endpoints (`/api/crawling/*`) restricted to admin IP via Nginx |
| Extension | No hardcoded IPs in manifest.json, domain-based HTTPS only |
