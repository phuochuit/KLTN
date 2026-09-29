import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const source = "C:/Users/Dphuoc1903/Downloads/Câu trả lời khảo sát QL Xe trong chung cư_căn hộ.xlsx";
const input = await FileBlob.load(source);
const workbook = await SpreadsheetFile.importXlsx(input);

const sheets = await workbook.inspect({
  kind: "workbook,sheet,table",
  include: "id,name,values,formulas",
  maxChars: 12000,
  tableMaxRows: 8,
  tableMaxCols: 12,
  tableMaxCellChars: 180,
});
console.log(sheets.ndjson);

const sheetList = await workbook.inspect({ kind: "sheet", include: "id,name", maxChars: 4000 });
console.log("---SHEETS---");
console.log(sheetList.ndjson);
