const API_BASE = "https://crawling-planet.cc";
const API_KEY = "81ac015245127b2ddb1b0bfd2362d700bb6232128f2e99f2f7ddf9e5945944a9";

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
    const url = `${API_BASE}/api/ext/company/search?name=${encodeURIComponent(companyName)}`;

    const response = await fetch(url, {
      method: "GET",
      headers: { Accept: "application/json", "X-API-Key": API_KEY },
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
    const response = await fetch(`${API_BASE}/api/ext/company/search?name=test`, {
      method: "GET",
      headers: { Accept: "application/json", "X-API-Key": API_KEY },
    });

    if (response.ok) {
      return { connected: true };
    }
    return { connected: false, status: response.status };
  } catch (err) {
    return { connected: false, message: err.message };
  }
}
