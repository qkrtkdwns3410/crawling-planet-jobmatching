const testBtn = document.getElementById("testBtn");
const statusEl = document.getElementById("status");

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
