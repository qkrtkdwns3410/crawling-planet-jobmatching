<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-01 | Updated: 2026-04-01 -->

# module-chrome-ext

## Purpose
잡코리아(jobkorea.co.kr) 채용 공고 페이지에서 회사명 옆에 잡플래닛 평점 배지를 표시하고, 클릭 시 리뷰 드롭다운을 보여주는 Chrome Extension (Manifest V3).

## Key Files

| File | Description |
|------|-------------|
| `manifest.json` | Extension 설정 (Manifest V3, host_permissions, content_scripts) |
| `content.js` | Content Script — 잡코리아 페이지에 주입되어 배지/드롭다운 DOM 생성 |
| `background.js` | Service Worker — API 요청 중계 (`FETCH_COMPANY_REVIEWS` 메시지 처리) |
| `popup.html` | Extension 팝업 UI |
| `popup.js` | 팝업 로직 |
| `styles.css` | 배지/드롭다운 스타일 |
| `icons/` | 16/48/128px 아이콘 |

## For AI Agents

### Content Script Flow
```
document_idle 시작
    └── init()
        ├── processCompanyElements()   // 현재 DOM의 회사 링크에 배지 부착
        └── observeDynamicContent()    // MutationObserver — 동적 로딩된 요소 처리 (debounce 300ms)

회사 요소 발견 시:
    └── loadReviewData(companyName, badge)
        └── chrome.runtime.sendMessage({ type: "FETCH_COMPANY_REVIEWS", companyName })
            └── background.js → fetch("https://crawling-planet.cc/api/ext/company/search?name=...")
                └── updateBadge(badge, data) 또는 showNoReviewBadge(badge)

배지 클릭 시:
    └── toggleDropdown(badge, data)
        └── createDropdown(data) — 회사 정보 + 리뷰 3개 카드 + 잡플래닛 링크
```

### Company Selectors (COMPANY_SELECTORS)
```javascript
"a.company-link"       // 잡코리아 채용공고 목록
".coName"
".name a[href*='/Corp/']"
"a[href*='/Corp/']"
"a[href*='/Co_Read/']"
".company-name"
".corp-name a"
```

### Key Design Decisions
- **배지 중복 방지**: `el.dataset.jpBadgeAttached = "true"` 플래그로 한 번만 부착
- **캐싱**: `reviewCache` Map으로 동일 회사명 중복 API 호출 방지
- **MutationObserver 디바운스**: 300ms 딜레이로 연속 DOM 변경 처리 최소화
- **TD 셀 처리**: 테이블 목록에서 배지가 절대위치 요소와 겹치지 않도록 별도 삽입
- **잡플래닛 링크**: `data.jobplanetId` 있으면 `https://www.jobplanet.co.kr/companies/{id}/reviews/`

### Working In This Directory
- Content Script는 `document_idle`에 실행 (DOM 완성 후)
- Background Service Worker가 cross-origin 요청 중계 (Content Script에서 직접 API 호출 불가)
- API 서버 도메인: `crawling-planet.cc` (host_permissions에 등록됨)
- 로컬 테스트: Chrome → `chrome://extensions` → 개발자 모드 → 압축 해제된 확장 프로그램 로드
- **수정 시 확인할 것**: 잡코리아 CSS 셀렉터는 배포 업데이트 시 변경될 수 있음

### Testing Requirements
- 로컬 테스트: `module-api` 서버를 localhost:8081로 실행 후 Chrome Extension에서 API URL 변경
- 잡코리아 실제 페이지(jobkorea.co.kr)에서 Extension 로드 후 배지 표시 확인

## Dependencies

### Internal
- `module-api` 서버 (API 호출)

### External
- Chrome Extension API (Manifest V3)
- `chrome.runtime.sendMessage` / `onMessage`

<!-- MANUAL: -->
