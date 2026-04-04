(function () {
  "use strict";

  const COMPANY_SELECTORS = [
    // 잡코리아 채용공고 목록
    "a.company-link",
    // 잡코리아 기존 셀렉터
    ".coName",
    ".name a[href*='/Corp/']",
    "a[href*='/Corp/']",
    "a[href*='/Co_Read/']",
    // 잡코리아 채용 상세
    ".company-name",
    ".corp-name a",
  ];

  const reviewCache = new Map();
  let activeDropdown = null;
  let processTimer = null;

  function init() {
    processCompanyElements();
    observeDynamicContent();
  }

  function observeDynamicContent() {
    const observer = new MutationObserver((mutations) => {
      let shouldProcess = false;
      for (const mutation of mutations) {
        if (mutation.addedNodes.length > 0) {
          shouldProcess = true;
          break;
        }
      }
      if (shouldProcess) {
        // 디바운스: DOM 변경이 연속으로 발생할 때 한 번만 처리
        clearTimeout(processTimer);
        processTimer = setTimeout(processCompanyElements, 300);
      }
    });

    observer.observe(document.body, { childList: true, subtree: true });
  }

  function processCompanyElements() {
    const selector = COMPANY_SELECTORS.join(", ");
    const elements = document.querySelectorAll(selector);

    elements.forEach((el) => {
      if (el.dataset.jpBadgeAttached) return;
      el.dataset.jpBadgeAttached = "true";

      const companyName = extractCompanyName(el);
      if (!companyName || companyName.length < 2) return;

      const badge = createBadge(companyName);
      insertBadgeAfter(el, badge);

      loadReviewData(companyName, badge);
    });
  }

  function extractCompanyName(el) {
    const text = (el.textContent || "").trim();
    // (주), (유), (사) 등은 유지하되 불필요한 공백 정리
    return text.replace(/\s+/g, " ").substring(0, 100);
  }

  function createBadge(companyName) {
    const badge = document.createElement("span");
    badge.className = "jp-review-badge jp-loading";
    badge.textContent = "...";
    badge.dataset.company = companyName;
    return badge;
  }

  function insertBadgeAfter(el, badge) {
    const parent = el.parentElement;
    // TD 셀 안의 링크(테이블 리스트)는 배지를 TD 맨 끝에 삽입 (절대위치 하트 버튼과 겹침 방지)
    if (parent && parent.tagName === "TD") {
      badge.style.display = "block";
      badge.style.marginLeft = "0";
      badge.style.marginTop = "2px";
      badge.style.width = "fit-content";
      parent.appendChild(badge);
    } else {
      el.appendChild(badge);
    }
  }

  async function loadReviewData(companyName, badge) {
    if (reviewCache.has(companyName)) {
      const cached = reviewCache.get(companyName);
      updateBadge(badge, cached);
      return;
    }

    try {
      const response = await chrome.runtime.sendMessage({
        type: "FETCH_COMPANY_REVIEWS",
        companyName,
      });

      if (response && !response.error && response.data) {
        reviewCache.set(companyName, response.data);
        updateBadge(badge, response.data);
      } else {
        reviewCache.set(companyName, null);
        showNoReviewBadge(badge);
      }
    } catch {
      showNoReviewBadge(badge);
    }
  }

  function updateBadge(badge, data) {
    badge.classList.remove("jp-loading");

    if (!data) {
      showNoReviewBadge(badge);
      return;
    }

    // rating이 없어도 리뷰가 있으면 표시
    if (data.rating) {
      const rating = parseFloat(data.rating).toFixed(1);
      badge.textContent = `${rating} ★`;
      badge.classList.add("jp-has-reviews");
      badge.title = `잡플래닛 평점: ${rating} (리뷰 ${data.reviewCount || 0}개)`;
    } else if (data.reviews && data.reviews.length > 0) {
      badge.textContent = `리뷰 ${data.reviewCount || data.reviews.length}개`;
      badge.classList.add("jp-has-reviews");
      badge.title = "클릭하여 잡플래닛 리뷰 보기";
    } else {
      showNoReviewBadge(badge);
      return;
    }

    badge.addEventListener("click", (e) => {
      e.preventDefault();
      e.stopPropagation();
      toggleDropdown(badge, data);
    });
  }

  function showNoReviewBadge(badge) {
    badge.classList.remove("jp-loading");
    badge.classList.add("jp-no-reviews");
    badge.textContent = "리뷰 없음";
    badge.title = "잡플래닛 리뷰를 찾을 수 없습니다";
  }

  function toggleDropdown(badge, data) {
    if (activeDropdown) {
      activeDropdown.remove();
      activeDropdown = null;
    }

    const dropdown = createDropdown(data);
    document.body.appendChild(dropdown);
    positionDropdown(dropdown, badge);
    activeDropdown = dropdown;

    setTimeout(() => {
      document.addEventListener("click", closeDropdownOnOutsideClick);
    }, 0);
  }

  function closeDropdownOnOutsideClick(e) {
    if (activeDropdown && !activeDropdown.contains(e.target) && !e.target.closest(".jp-review-badge")) {
      activeDropdown.remove();
      activeDropdown = null;
      document.removeEventListener("click", closeDropdownOnOutsideClick);
    }
  }

  function positionDropdown(dropdown, badge) {
    const rect = badge.getBoundingClientRect();
    const dropdownWidth = 400;

    let left = rect.left + window.scrollX;
    if (left + dropdownWidth > window.innerWidth) {
      left = window.innerWidth - dropdownWidth - 16;
    }
    if (left < 8) left = 8;

    dropdown.style.top = `${rect.bottom + window.scrollY + 6}px`;
    dropdown.style.left = `${left}px`;
  }

  function createDropdown(data) {
    const dropdown = document.createElement("div");
    dropdown.className = "jp-review-dropdown";

    const header = document.createElement("div");
    header.className = "jp-dropdown-header";

    const companyInfo = document.createElement("div");
    companyInfo.className = "jp-company-info";

    const nameEl = document.createElement("h3");
    nameEl.className = "jp-company-name";
    nameEl.textContent = data.companyName || "회사명";

    const metaEl = document.createElement("div");
    metaEl.className = "jp-company-meta";

    const ratingStr = data.rating ? parseFloat(data.rating).toFixed(1) : "-";
    const industryStr = data.industry || "";
    const reviewCountStr = data.reviewCount ? ` · 리뷰 ${data.reviewCount}개` : "";
    const ratingLargeSpan = document.createElement("span");
    ratingLargeSpan.className = "jp-rating-large";
    ratingLargeSpan.textContent = `${ratingStr} ★`;
    metaEl.appendChild(ratingLargeSpan);
    if (industryStr) {
      metaEl.appendChild(document.createTextNode(` · `));
      const indSpan = document.createElement("span");
      indSpan.textContent = industryStr;
      metaEl.appendChild(indSpan);
    }
    if (reviewCountStr) {
      metaEl.appendChild(document.createTextNode(reviewCountStr));
    }

    companyInfo.appendChild(nameEl);
    companyInfo.appendChild(metaEl);

    const closeBtn = document.createElement("button");
    closeBtn.className = "jp-close-btn";
    closeBtn.textContent = "✕";
    closeBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      dropdown.remove();
      activeDropdown = null;
    });

    header.appendChild(companyInfo);
    header.appendChild(closeBtn);
    dropdown.appendChild(header);

    const reviews = data.reviews || [];
    if (reviews.length === 0) {
      const empty = document.createElement("div");
      empty.className = "jp-no-review-msg";
      empty.textContent = "등록된 리뷰가 없습니다.";
      dropdown.appendChild(empty);
    } else {
      const reviewList = document.createElement("div");
      reviewList.className = "jp-review-list";

      reviews.slice(0, 3).forEach((review) => {
        reviewList.appendChild(createReviewCard(review));
      });

      dropdown.appendChild(reviewList);
    }

    const footer = document.createElement("div");
    footer.className = "jp-dropdown-footer";
    if (data.jobplanetId) {
      const link = document.createElement("a");
      link.href = `https://www.jobplanet.co.kr/companies/${data.jobplanetId}/reviews/`;
      link.target = "_blank";
      link.className = "jp-view-all-link";
      link.textContent = "잡플래닛에서 전체 리뷰 보기 →";
      footer.appendChild(link);
    } else {
      const poweredBy = document.createElement("span");
      poweredBy.className = "jp-powered-by";
      poweredBy.textContent = "Powered by 잡플래닛";
      footer.appendChild(poweredBy);
    }
    dropdown.appendChild(footer);

    return dropdown;
  }

  function createReviewCard(review) {
    const card = document.createElement("div");
    card.className = "jp-review-card";

    const reviewHeader = document.createElement("div");
    reviewHeader.className = "jp-review-header";

    const rating = review.rating ? parseFloat(review.rating).toFixed(1) : "-";
    const reviewRatingSpan = document.createElement("span");
    reviewRatingSpan.className = "jp-review-rating";
    reviewRatingSpan.textContent = `${rating} ★`;
    reviewHeader.appendChild(reviewRatingSpan);

    if (review.occupationName || review.employStatusName) {
      const meta = document.createElement("span");
      meta.className = "jp-review-meta";
      const parts = [];
      if (review.occupationName) parts.push(review.occupationName);
      if (review.employStatusName) parts.push(review.employStatusName === "true" || review.employStatusName === true ? "현직" : "전직");
      meta.textContent = parts.join(" · ");
      reviewHeader.appendChild(meta);
    }

    if (review.summary || review.title) {
      const title = document.createElement("span");
      title.className = "jp-review-title";
      title.textContent = review.summary || review.title;
      reviewHeader.appendChild(title);
    }

    card.appendChild(reviewHeader);

    if (review.pros) {
      card.appendChild(createReviewSection("장점", review.pros, "jp-pros"));
    }
    if (review.cons) {
      card.appendChild(createReviewSection("단점", review.cons, "jp-cons"));
    }
    if (review.toManagement) {
      card.appendChild(createReviewSection("경영진에게 바라는 점", review.toManagement, "jp-suggestion"));
    }

    return card;
  }

  function createReviewSection(label, text, className) {
    const section = document.createElement("div");
    section.className = `jp-review-section ${className}`;

    const labelEl = document.createElement("span");
    labelEl.className = "jp-section-label";
    labelEl.textContent = label;

    const textEl = document.createElement("p");
    textEl.className = "jp-section-text";
    textEl.textContent = text;

    section.appendChild(labelEl);
    section.appendChild(textEl);
    return section;
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
