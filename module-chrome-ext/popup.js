const DEFAULT_API_BASE = "http://localhost:8080";

const apiUrlInput = document.getElementById("apiUrl");
const saveBtn = document.getElementById("saveBtn");
const saveMsg = document.getElementById("saveMsg");
const testBtn = document.getElementById("testBtn");
const statusEl = document.getElementById("status");

document.addEventListener("DOMContentLoaded", () => {
  chrome.storage.sync.get({ apiBase: DEFAULT_API_BASE }, (result) => {
    apiUrlInput.value = result.apiBase;
  });
});

saveBtn.addEventListener("click", () => {
  const url = apiUrlInput.value.trim().replace(/\/+$/, "");
  if (!url) {
    apiUrlInput.value = DEFAULT_API_BASE;
    return;
  }

  chrome.storage.sync.set({ apiBase: url }, () => {
    saveMsg.classList.add("show");
    setTimeout(() => saveMsg.classList.remove("show"), 2000);
  });
});

testBtn.addEventListener("click", async () => {
  statusEl.className = "status testing";
  statusEl.style.display = "block";
  statusEl.textContent = "연결 테스트 중...";

  const response = await chrome.runtime.sendMessage({ type: "TEST_CONNECTION" });

  if (response && response.connected) {
    statusEl.className = "status connected";
    statusEl.textContent = "연결 성공! API 서버가 정상 작동 중입니다.";
  } else {
    statusEl.className = "status disconnected";
    const reason = response?.message || (response?.status ? `HTTP ${response.status}` : "서버에 접근할 수 없습니다");
    statusEl.textContent = `연결 실패: ${reason}`;
  }
});
