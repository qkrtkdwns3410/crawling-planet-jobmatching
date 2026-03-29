document.addEventListener("DOMContentLoaded", () => {
  chrome.storage.sync.get({ apiKey: "", apiBase: "https://crawling-planet.cc" }, (items) => {
    document.getElementById("apiKey").value = items.apiKey;
    document.getElementById("apiBase").value = items.apiBase;
  });

  document.getElementById("saveBtn").addEventListener("click", () => {
    const apiKey = document.getElementById("apiKey").value.trim();
    const apiBase = document.getElementById("apiBase").value.trim() || "https://crawling-planet.cc";

    chrome.storage.sync.set({ apiKey, apiBase }, () => {
      document.getElementById("msg").textContent = "저장되었습니다.";
      setTimeout(() => { document.getElementById("msg").textContent = ""; }, 2000);
    });
  });
});
