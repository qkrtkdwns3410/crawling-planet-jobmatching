const DEFAULT_API_BASE = "https://crawling-planet.cc";

async function getConfig() {
  const result = await chrome.storage.sync.get({
    apiBase: DEFAULT_API_BASE,
    apiKey: "",
  });
  return result;
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
    const { apiBase, apiKey } = await getConfig();
    const url = `${apiBase}/api/ext/company/search?name=${encodeURIComponent(companyName)}`;

    const response = await fetch(url, {
      method: "GET",
      headers: { Accept: "application/json", "X-API-Key": apiKey },
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
    const { apiBase, apiKey } = await getConfig();
    const response = await fetch(`${apiBase}/api/ext/company/search?name=test`, {
      method: "GET",
      headers: { Accept: "application/json", "X-API-Key": apiKey },
    });

    if (response.ok) {
      return { connected: true };
    }
    return { connected: false, status: response.status };
  } catch (err) {
    return { connected: false, message: err.message };
  }
}
