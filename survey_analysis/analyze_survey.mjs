import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const source = "C:/Users/Dphuoc1903/Downloads/Câu trả lời khảo sát QL Xe trong chung cư_căn hộ.xlsx";
const input = await FileBlob.load(source);
const workbook = await SpreadsheetFile.importXlsx(input);
const sheet = workbook.worksheets.getItem("Câu trả lời biểu mẫu 1");
const rows = sheet.getRange("A2:P12").values;

const normalize = (v) => String(v ?? "").trim().toLowerCase();
const countWhere = (column, predicate) => rows.filter((r) => predicate(normalize(r[column]))).length;

const result = {
  responses: rows.length,
  vehicle: {
    motorcycle: countWhere(4, (v) => v.includes("xe máy")),
    car: countWhere(4, (v) => v.includes("ô tô")),
  },
  maxVehicles: {
    noStatedLimit: countWhere(7, (v) => v === "không"),
    explicitNumericOrComposition: countWhere(7, (v) => v !== "không" && v !== ""),
  },
  collection: {
    includedInManagementFee: countWhere(8, (v) => v.includes("tính chung")),
    separateParkingCharge: countWhere(8, (v) => v.includes("thu riêng")),
  },
  dailyEntryLimit: {
    unlimited: countWhere(9, (v) => v.includes("không giới hạn")),
  },
  residentControl: {
    card: countWhere(11, (v) => v.includes("thẻ")),
    manualSecurity: countWhere(11, (v) => v.includes("bảo vệ")),
  },
  guestCap: {
    noRuleReported: countWhere(12, (v) => v === "không"),
  },
  technology: {
    card: countWhere(13, (v) => v.includes("thẻ")),
    plateRecognition: countWhere(13, (v) => v.includes("biển số")),
    faceRecognition: countWhere(13, (v) => v.includes("khuôn mặt")),
  },
  variablePricing: {
    yes: countWhere(14, (v) => v === "có"),
    no: countWhere(14, (v) => v === "không"),
    missing: countWhere(14, (v) => v === ""),
  },
};

console.log(JSON.stringify(result, null, 2));
console.log("---PRICE ANSWERS---");
rows.forEach((r, i) => {
  if (normalize(r[15])) console.log(`${i + 2}: ${String(r[15]).replaceAll("\n", " | ")}`);
});
