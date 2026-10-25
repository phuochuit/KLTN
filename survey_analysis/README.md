# Survey scripts — giới hạn môi trường

**Current main `abd6183`, kiểm tra tĩnh ngày2026-10-24:** ngoài README được khôi phục này, không có `analyze_survey.mjs`, `inspect_survey.mjs`, manifest/lock hoặc `node_modules/` trong thư mục main. Các ghi chép dưới là **lịch sử nhánh feature/workspace**, không inventory/dependency evidence hiện hành. Backup source dưới `.artifacts/` không thay source main, không tự khôi phục/cài/chạy survey vì tài liệu nhắc tới.

## Ghi chép lịch sử được giữ lại

`analyze_survey.mjs` và `inspect_survey.mjs` từng là source lịch sử, chưa là workflow được tái lập/duyệt để chạy. Ghi chép cũ nêu chúng hardcode đường dẫn workbook của máy tác giả, đọc range/sheet cố định và có thể in raw responses/PII; không chạy chúng với dữ liệu thật hoặc thu stdout vào report công khai. Không công bố đường dẫn/response gốc để giải thích provenance.

Lần đọc metadata dependency trên workspace cũ ghi `@oai/artifact-tool`2.8.59, `private:true`; chưa có package manifest/lock và nguồn phân phối được xác nhận cho hai script. Không tự tạo một lockfile giả, cài package thay thế hoặc suy private package có thể tải công khai. Ghi chép ngày2026-10-23 từng báo7796 dependency files tracked, giữ nguyên dependency tree và không đổi Git index trong lượt đó. Đây **không phải trạng thái tracking của main**; ignore không untrack file, và guard warning chỉ applicable khi thực sự còn tracked dependencies.

Vì các giới hạn đó, workspace cũ giữ `survey_analysis/` như ngoại lệ, chưa chuyển sang `tools/survey/`; docs-only sync không thực hiện migration hoặc khôi phục dependency tree. Khi được cấp installation contract và input synthetic/consent phù hợp: thêm manifest/lock thực trong phạm vi riêng được duyệt, nhận input tường minh, không log raw data và lưu output vào `.artifacts/tool-runs/` theo [workspace policy](../docs/architecture/workspace-layout.md). Syntax check lịch sử không phải đã chạy phân tích hoặc công nhận kết quả khảo sát. Không có execution/install/data validation mới trong đợt tài liệu này.
