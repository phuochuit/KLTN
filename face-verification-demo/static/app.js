const form = document.querySelector("#compare-form");
const submitButton = document.querySelector("#submit-button");
const loading = document.querySelector("#loading");
const result = document.querySelector("#result");
const errorBox = document.querySelector("#error-box");
const health = document.querySelector("#health");
const fields = ["cccd", "registration", "realtime"];

const labels = {
  cccdVsRegistration: "CCCD ↔ Đăng ký",
  realtimeVsRegistration: "Realtime ↔ Đăng ký",
  realtimeVsCccd: "Realtime ↔ CCCD",
};

const statuses = {
  MATCH: "Khớp",
  REVIEW: "Gần ngưỡng",
  NO_MATCH: "Không khớp",
};

function showPreview(key) {
  const input = document.querySelector(`#${key}`);
  const preview = document.querySelector(`#preview-${key}`);
  const picker = input.closest(".picker");
  input.addEventListener("change", () => {
    if (!input.files?.[0]) return;
    preview.src = URL.createObjectURL(input.files[0]);
    picker.classList.add("has-image");
    document.querySelector(`#quality-${key}`).textContent = "Ảnh đã sẵn sàng để kiểm tra.";
  });
}

fields.forEach(showPreview);

async function checkHealth() {
  try {
    const response = await fetch("/api/health");
    const data = await response.json();
    if (data.modelsReady) {
      health.className = "health ready";
      health.textContent = "● Mô hình đã sẵn sàng";
    } else {
      health.className = "health error";
      health.textContent = "● Chưa tải mô hình";
    }
  } catch {
    health.className = "health error";
    health.textContent = "● Không kết nối được API";
  }
}

function renderQuality(quality) {
  for (const [key, item] of Object.entries(quality)) {
    const warning = item.warnings.length
      ? `<div class="warning">⚠ ${item.warnings.join(" · ")}</div>`
      : "<div>✓ Chất lượng cơ bản đạt</div>";
    document.querySelector(`#quality-${key}`).innerHTML =
      `${warning}<div>Sáng: ${item.brightness} · Nét: ${item.sharpness} · Face: ${(item.detectionScore * 100).toFixed(1)}%</div>`;
  }
}

function renderResult(data) {
  const configuration = {
    PASS: { title: "ĐẠT – ba ảnh nhất quán", icon: "✓" },
    REVIEW: { title: "CẦN NHÂN VIÊN KIỂM TRA", icon: "!" },
    REJECT: { title: "KHÔNG ĐẠT", icon: "×" },
  }[data.decision];

  result.className = `result ${data.decision.toLowerCase()}`;
  result.hidden = false;
  document.querySelector("#decision-title").textContent = configuration.title;
  document.querySelector("#decision-icon").textContent = configuration.icon;
  document.querySelector("#decision-message").textContent = data.message;
  document.querySelector("#scores").innerHTML = Object.entries(data.comparisons).map(([key, value]) => `
    <div class="score">
      <strong>${labels[key]}</strong>
      <span class="score-value">${value.score.toFixed(3)}</span>
      <small>${statuses[value.status]}</small>
    </div>
  `).join("");
  document.querySelector("#threshold-note").textContent =
    `Ngưỡng demo: khớp ≥ ${data.thresholds.match}; cần xem xét từ ${data.thresholds.review} đến dưới ${data.thresholds.match}. ` +
    "Điểm là cosine similarity, không phải phần trăm xác suất. Liveness: chưa kiểm tra.";
  renderQuality(data.quality);
  result.scrollIntoView({ behavior: "smooth", block: "start" });
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  errorBox.hidden = true;
  result.hidden = true;
  submitButton.disabled = true;
  loading.hidden = false;
  try {
    const response = await fetch("/api/compare", { method: "POST", body: new FormData(form) });
    const data = await response.json();
    if (!response.ok) throw new Error(data.detail || "Không thể xử lý ảnh.");
    renderResult(data);
  } catch (error) {
    errorBox.textContent = error.message;
    errorBox.hidden = false;
  } finally {
    submitButton.disabled = false;
    loading.hidden = true;
  }
});

form.addEventListener("reset", () => {
  setTimeout(() => {
    fields.forEach((key) => {
      const preview = document.querySelector(`#preview-${key}`);
      preview.removeAttribute("src");
      preview.closest(".picker").classList.remove("has-image");
      document.querySelector(`#quality-${key}`).textContent = "";
    });
    result.hidden = true;
    errorBox.hidden = true;
  });
});

checkHealth();

