const DEFAULT_API_BASE = "http://localhost:8080";

async function getApiBase() {
  const result = await chrome.storage.sync.get({ apiBase: DEFAULT_API_BASE });
  return result.apiBase.replace(/\/+$/, "");
}

chrome.runtime.onMessage.addListener((request, _sender, sendResponse) => {
  if (request.type === "FETCH_COMPANY_REVIEWS") {
    fetchCompanyReviews(request.companyName).then(sendResponse);
    return true;
  }

  if (request.type === "TEST_CONNECTION") {
    testConnection().then(sendResponse);
    return true;
  }
});

async function fetchCompanyReviews(companyName) {
  try {
    const apiBase = await getApiBase();
    const url = `${apiBase}/api/ext/company/search?name=${encodeURIComponent(companyName)}`;

    const response = await fetch(url, {
      method: "GET",
      headers: { Accept: "application/json" },
    });

    if (!response.ok) {
      return { error: true, status: response.status };
    }

    const data = await response.json();

    if (data && data.reviews) {
      data.reviews = data.reviews.slice(0, 3);
    }

    return { error: false, data };
  } catch (err) {
    return { error: true, message: err.message };
  }
}

async function testConnection() {
  try {
    const apiBase = await getApiBase();
    const response = await fetch(`${apiBase}/api/crawling/status`, {
      method: "GET",
      headers: { Accept: "application/json" },
    });

    if (response.ok) {
      return { connected: true };
    }
    return { connected: false, status: response.status };
  } catch (err) {
    return { connected: false, message: err.message };
  }
}
