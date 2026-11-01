# Handoff — Full Web UI Redesign Phase 2

[MASTER](MASTER.md) · [Inventory](SCREEN-INVENTORY.md) · [Audit](UI-AUDIT.md) · [Research](RESEARCH.md) · [Docs hệ thống](../../docs/README.md)

**Phạm vi duyệt mới 2026-10-26:** chỉ Branding & Login + frontend build foundation tối thiểu, focused tests và isolated browser QA. Không mở rộng pilot sang shell/password/error/long form/dense table như wave1 dự kiến bên dưới. Thứ tự dài hạn và các waves là backlog, không quyền thực thi. Xem [Login override](pages/login.md). Full replacement vẫn là định hướng toàn dự án, không thực hiện đồng thời.

**Login visual rebuild tiếp theo:** visual trước đã được thay bằng LP open-lanes, teal/mint/lime/lavender/peach, Be Vietnam Pro local và glass46–64%/blur24px; [override](pages/login.md) phản ánh thiết kế hiện hành. Đã dùng Playwright render → xem → sửa font path/blur thực tế → cân typography/mobile → render lại; evidence tại `.artifacts/test-runs/20261026-lumipark-visual-v2-final/` (`desktop-final.png`, `mobile-refined.png`, `laptop-final.png`). Tái dùng browser smoke12 nhóm PASS và Node3/3, không thêm suite; các số14 Spring tests ở mục lịch sử bên dưới không được chạy lại trong lượt visual này. H2/mock ANPR, không production E2E; chưa manual screen reader/multi-engine/full accessibility audit. Không thay Java/auth/JS flow hoặc trang khác.

## 1. Architecture đề xuất

Giữ Spring Boot MVC + Thymeleaf SSR, progressive enhancement bằng vanilla JS. Không SPA, không React/shadcn/Vite mới. Backend bindings/routes/security là contract, không acceptance thẩm mỹ. MASTER sở hữu token semantics, shared fragments sở hữu component markup, page JS chỉ orchestration của UI đã có.

Vị trí **dự kiến**, chưa tạo trong Phase1:

| Trách nhiệm | Vị trí proposed / lý do |
| --- | --- |
| CSS input/tokens/components | `spring-web/src/main/frontend/styles/` — tách input khỏi static output |
| Frontend manifest/lock/scripts | `spring-web/package.json`, lockfile — CLI v4 pin, không root tooling/context dependency |
| CSS production output | `spring-web/target/classes/static/css/web-ui.css` — native generated output, không sửa app.css cũ thành nơi chứa build |
| Thymeleaf shell/head/nav/feedback | dưới `spring-web/src/main/resources/templates/`, fragments chia anatomy; page vẫn giữ view names/controller |
| Icon fragments/subset assets | dưới templates/static đúng module, chỉ selected Lucide SVG local, license reviewed |
| Shared JS behaviors | `spring-web/src/main/resources/static/js/` modules cho dialog/nav/form state/refresh, không đổi business endpoints |

Các path này là kiến trúc cần duyệt, không hứa đã có. Có thể tối giản thành1 input CSS thay nhiều file khi spec còn nhỏ; không tạo layer/package abstraction không dùng.

### Tailwind v4 production build
1. Phase2 mới được cài `tailwindcss` + `@tailwindcss/cli` **v4 pin exact** và lockfile. CLI build-time không yêu cầu Node trên máy production chạy Spring nếu artifact đã build đầy đủ. Chưa chọn package version bằng auto-latest.
2. Input có `@import "tailwindcss"`, tokens :root + @theme inline như MASTER; native shared components trong layer components, utility cho page composition. Không copy v3 JS config/content/shadcn ví dụ từ skill.
3. Explicit scan templates và static JS; với input ở `src/main/frontend/styles/web-ui.css`, hướng source có thể `@source "../../resources/templates"; @source "../../resources/static/js";` (resolve từ input CSS). Kiểm lại CLI minor docs và generated output trước chốt. Dynamic `th:classappend` giữ complete strings/mapping, không `bg-${state}`. Include live HTML view và error/auth templates.
4. Proposed command từ `spring-web`: `npx @tailwindcss/cli -i ./src/main/frontend/styles/web-ui.css -o ./target/classes/static/css/web-ui.css --minify`. Trong package script dùng binary local đã lock; CI `npm ci`, không network install lúc request. `--watch` chỉ dev, không package production.
5. Cần hook Maven lifecycle sao cho resource copy rồi CSS generation xong **trước package** (ví dụ process-classes); executable JAR chứa output sau `clean package`. Đừng build CSS trước Maven clean rồi bị xóa. Chọn plugin/toolchain cache version trong scope Phase2 được duyệt, không sửa launcher/CI ngầm. Test build từ clean checkout và Windows PowerShell paths.
6. Không Play CDN, Google-font runtime import bắt buộc hoặc unpinned remote Lucide. Self-host font sau license/glyph check hoặc system fallback ban đầu. Subset icons render server để không thêm bundler chỉ cho icon. Static URLs phải nằm trong matcher đã public (ví dụ font dưới `/css/fonts/`, SVG dưới `/images/`); không mặc định thêm `/fonts/**` rồi nới security, vì fallback hiện deny-all.
7. Tailwind v4 cần modern browsers; chốt browser baseline của ban quản lý trước cài. Nếu máy quá cũ, nêu incompatibility và xin quyết định, không tự hạ v3/migrate stack. Kiểm CSS/backdrop-filter/native dialog fallback thực tế.

## 2. Replacement strategy

- Tạo new shell với head/viewport/tokens và shared nav/topbar/form/table/dialog components. Không sửa controller để áp layout framework; dùng Thymeleaf fragments đã có hệ sinh thái.
- Mỗi wave chuyển **toàn template/screen group** sang shell mới, không load global `app.css` cạnh Tailwind Preflight. Legacy page tạm dùng legacy assets riêng; route/view name không đổi. Auth/error variants cùng token system không buộc authenticated nav.
- Chụp anatomy/state matrix shared trước; sau wave cuối loại mọi app.css/inline styling/JS cũ không còn caller **trong scope duyệt**, bằng inventory caller evidence, không xóa trước vì nghĩ dead. Shared dialog API cũ cần compatibility hoặc migrate callers cùng wave.
- Không thay window.alert async contract bằng native blocking giả. Khi migrate `ParkingUi`, migrate tất cả awaited/non-awaited callers thích hợp và `data-confirm` submit delegation; giữ submitter/hidden fields/CSRF, tránh duplicate POST. No-JS core forms/navigation vẫn hợp lệ; camera/chart enhancements degrade có hướng dẫn.
- Tách DOM initialization khỏi inline handlers khi chuyển page; preserve selectors tới khi tests migration xác nhận. Các live sections cần event delegation và focus-safe replacement, không document-wide reinit lặp.
- Kiểm Git scope sau mỗi wave, không xóa source/route chưa chuyển. Không persistent feature flag/backend route mới chỉ để demo redesign nếu không được duyệt.

## 3. Contract invariants / separate gates

Nguồn kỹ thuật: [Security](../../docs/security/overview.md), [API contracts](../../docs/api/contracts.md), [endpoint inventory](../../docs/api/endpoints.md), [testing](../../docs/testing/strategy.md); implementation cụ thể ở SCREEN-INVENTORY. Docs có đoạn snapshot lịch sử: đối chiếu source trước áp.

- Giữ route/method/view/model names, individual @RequestParam/BatchSlotGenerateRequest và mọi input name/multi-value/enum/hidden ID; không invent DTO-backed th:field.
- Giữ multipart và precedence file/capture; registration→cards selectedVehicleId; owner auto-authorized/cùng hộ/quota; không quảng cáo QR identity verified/consent approved.
- Giá/gói/payment/status/ledger boundaries đúng nguồn; no auto POST retry, no fake settlement/refund/debt/export workflows. Không thay duration30/default hay amount computation. Dashboard fees/subscriptions khác reconciliation ledger.
- Session/CSRF/RBAC giữ server semantics; nav/actions theo currentAuthorities nhưng authorization không ở CSS. Revenue hiện Web session DASHBOARD_VIEW/HISTORY_VIEW/fallback management, không JWT/REVENUE_VIEW theo bảng lịch sử.
- QR CSRF caller là UI interaction fix có thể đề xuất rõ trong Phase2; không tắt security. Nếu browser response HTML/login/413, kiểm status/content type trước parse, message an toàn không expose raw exception.
- Account read broad/data model không minimized: UI grant filtering không sửa confidentiality. Chủ dự án cần quyết định task backend/security riêng nếu muốn đóng gap trước rollout; không tự sửa matcher/controller.
- Registration cross-grant, forbidden login destination, member face evidence permission phải test. UI không tự cấp thêm quyền hoặc đổi login success URL/backend policy.
- Card activation partial-failure/atomicity, incomplete range/time boundary validation, stats lineage/wording, unassigned eligibility và field-error/preserved-values cần backend support: đánh dấu blocked liên quan hoặc duyệt task riêng. Không âm thầm sửa để visual QA pass.
- Giữ forced password change/reset temporary secret/revocation, readonly vs disabled, role code/prefix/system enabled, POST logout. Không đưa PII/temporary password/token vào localStorage, logs, screenshots dùng thật.

## 4. Ordered waves, dependencies và checkpoints

| Wave (sau khi duyệt) | Screens/deliverable | Verify / exit gate |
| --- | --- | --- |
| 0 — approval & harness | ảnh reference, MASTER/tokens/material/fonts, browser baseline, limited-role fixtures, isolated DB/media test; chốt source/backend gaps | người dùng duyệt visual + dependency/build path + môi trường; không dùng DB/media thật |
| 1 — shell/components pilot | shared head/nav/topbar, tokens, buttons/fields/tables/dialog, auth/password/error/recovery; proof trên long form + dense table trước scale | clean CSS build/JAR inclusion, contrast/focus/reflow, auth+CSRF preserved; duyệt một vòng visual reference trước chuyển toàn bộ |
| 2 — overview | dashboard Bento/filters/month drill-down + empty/error/loading | sums/period labels/keyboard/chart table fallback; source financial gaps không nhận PASS giả |
| 3 — hồ sơ | registrations + households/member + vehicles | multipart/QR/camera/edit/reset/owner authorization/quota, cross-grants, private image fallback |
| 4 — tài chính/cấu hình | cards/payments/pricing/settings/sessions | selectedVehicleId, amounts/dates/status/method/reference, PAID reconcile+note, no auto replay, readonly/hours; history/table stress |
| 5 — vận hành | slots all states, detail tabs, batch/single/assign/borrow/dispatch/release | focus/modal/confirm, state legend, filter scope/freshness, all existing action bindings |
| 6 — quản trị | account/live/shift, roles, security-audit | limited grants, temp secret, revoke/reset, refresh pause/focus, permission sets, server pagination |
| 7 — full replacement QA | remove legacy callers/assets có chứng cứ, toàn bộ15 pages +partial/errors | no mixed legacy CSS; all contracts/screens/state matrix reviewed; docs canonical updated; no implicit deploy/commit |

Waves là nhóm ưu tiên, chia PR/slice nhỏ1–5 files khi thực thi; mỗi slice phải có visual/interaction evidence. Scope Phase2 cần duyệt riêng cho removals/dependencies/behavior fixes; không lấy tài liệu này làm lệnh commit/deploy.

## 5. Visual / responsive / accessibility / interaction QA

**Ma trận bắt buộc:** 15 pages +1 live view integration +2 errors; desktop1440/1920, laptop1024/1280, tablet768 (landscape1024), mobile375 và320CSSpx; representative Chromium/Edge và Firefox/WebKit theo browser baseline đã chốt. Test200% zoom/400% reflow, long Vietnamese/long identifiers, empty/single/many rows, large sums, no blur support, forced colors, reduced motion. Dark chưa delivered, không ghi Dark PASS.

**Visual:** compare screenshot approved reference (sau khi cung cấp) và golden screenshots synthetic. Check spacing/type/token/material/padding/radius, no page-wide overflow, no clipping, tables scroll internally, fonts loaded/fallback, correct semantic colors. Measure composed text/control/focus contrast ở backdrop xấu nhất trong palette; minimums MASTER, không chỉ đo hex. Profile scroll/sidebar/filter/large table: không animation/blur gây jank; chưa đặt benchmark PASS khi chưa có target device.

**Accessibility:** keyboard-only toàn nav/forms/chart/slots/dialog/tabs/dropdown; skip link/current page/focus return, Escape/Cancel, modal no nested trap; labels/descriptions/error summary, live announcements không spam, password paste/autocomplete; screen reader NVDA hoặc tương đương trên browser được chọn. Automated axe hoặc tool approved là hỗ trợ, không thay manual checks; chưa cài tool mới trong Phase1.

**Interactions:** GET navigation, POST success/failure+CSRF, session timeout/403/413/network offline, no JSON parse on HTML redirect; server flash retained; no duplicate submit/data loss claim; cancel confirmation không submit. QR and camera upload/capture/stop/reset; owner/member editors reload values; cards and reconcile status; slots mapping all forms; live refresh no focus loss, pause/hidden/stale/resume. Sensitive content dùng fixtures synthetic, screenshot redact secret.

**Tests:** giữ Spring existing regression/security tests theo [tests entry points](../../tests/README.md); có file test không nghĩa đã chạy. Focused MockMvc tests cho view/model/forms/permission contract không chứng minh browser behavior; Playwright chỉ Web với test DB/media harness được duyệt trước. H2/mock ANPR không live integration. Runner root report mới `.artifacts/test-runs/<run-id>/`; không chạy runner/direct cùng suite song song. Không gọi camera thật/seed/reset DB thật tự động.

## 6. Approval checklist trước Phase2

- [ ] Người dùng gửi lại ảnh hoặc chấp nhận mô tả text-only; đối chiếu reference và duyệt concept trước code.
- [ ] Duyệt MASTER palette blue/lavender, Source Sans3/system fallback, alpha86/92/98%, radius24, density, chart/table readability.
- [ ] Duyệt sidebar grouping, data-first management composition,4 overrides; không phát sinh chức năng/export/charging mới.
- [ ] Duyệt Tailwind v4 local production CLI/manifest+lockfile, build hook/output paths, Lucide local subset và font hosting/license/browser baseline. Chưa cài gì.
- [ ] Chốt các UI fixes được làm trong Phase2 (CSRF caller/focus/errors/stale, nhãn kỳ) và backend/security/financial gaps phải tách scope hoặc chặn nghiệm thu liên quan.
- [ ] Cấp test environment synthetic, role fixtures/harness và quyền startup/test rõ ràng; không operational DB/media.
- [ ] Duyệt Wave1 pilot rồi rollout cả Web; Dark là phase tương lai. Không Git write/deploy mặc định.

## 7. Phase1 verification record

Kiểm thực đã chạy ngày2026-10-25:

| Check | Result / boundary |
| --- | --- |
| `python .artifacts/agent-work/web-ui-phase1/verify-docs.py` | PASS:9 design documents, local file links/code fences hợp lệ; cả19 template filenames có trong inventory;15 opaque contrast pairs và36 glass samples đạt threshold theo vai trò. Đây là coverage tên file + số học, không chứng nhận nội dung/runtime |
| `git diff --check` | PASS cho tracked diff;9 tài liệu mới là untracked nên dùng kiểm text/link riêng, không coi lệnh này đã check chúng |
| `git status --short --untracked-files=all`, `git diff --stat` | chỉ docs/README thêm1 navigation link +9 Markdown files mới trong design-system/kltn; không application source/config changes |
| `scripts/check-workspace.ps1` | PASS path/tracking guard, exit0; không phải secret scanner hoặc test UI |
| Independent read-only documentation review | Đã xử lý2 góp ý: confirmation release nêu đủ việc bỏ occupied-session association (không đóng lượt), và central neutral-state mapping trong MASTER |

Phase1 không app startup, build/test suite, browser visual/interaction/a11y/live E2E. Những tiêu chuẩn §5 chưa thực hiện. Root docs index chỉ thêm navigation tới MASTER; requirement/API/security/operations runtime không đổi nên không sửa nội dung canonical đó để giả như redesign đã ship. Không commit/push/merge/stage; local plan/scratch không là deliverable ứng dụng.

## Branding & Login implementation / verification — 2026-10-26

Đã thay riêng login.html, tạo5 original brand SVGs, semantic tokens/Tailwind4.3.3 build và Lucide-static1.61.0 subset; Node/npm lockfile và process-classes hook. Chỉ Login load foundation; Java production/auth/session/CSRF/redirect không sửa. Người dùng duyệt bổ sung Node/npm + npm ci trong đúng Spring job Verify, không đổi trigger/permissions hoặc dispatch workflow. Chi tiết visual quyết định ở [Login override](pages/login.md).

| Gate | Evidence thực / boundary |
| --- | --- |
| Frozen npm ci/build/audit | Thành công local; lifecycle dependency scripts bị tắt. Native npm audit báo0 vulnerabilities; không thay provenance/security review hoàn chỉnh |
| Clean Maven package + focused tests | JDK21:14 tests,0 failures/errors/skips (LoginPageIntegrationTest9 + existing SecurityAuthorizationIntegrationTest5). Fresh report `20261026-lumipark-final-build`; không full suite |
| Node interaction tests |3/3: visibility, native submit/duplicate prevention, pageshow reset; test doubles không browser |
| Browser thật | Playwright qua MCP + system Microsoft Edge runner: final12 groups PASS ở `20261026-lumipark-browser-02`. Desktop1440,1280,1024/mobile375,320/landscape812; empty form bounds/screenshots, Enter/required/visibility, wrong credentials, success, forced-change destination, logout confirmation, throttle, JS-off, reduced-motion và simulated opaque fallback |
| Guard/history browser | Guard dùng response stub204 để quan sát pending UI không để locator action bị navigation wait; browser history recovery được kiểm. Không gọi đây là auth/backend behavior; native auth được kiểm riêng bằng real Spring Security |
| Correct-build provenance | CSS/JS/lockup/favicon/Lucide eye bytes SHA256: JAR = target/classes = HTTP response, file `asset-provenance.json`; không chỉ dựa health |
| Scope/structure | SVG XML parse không active/external references; legacy templates không load foundation; git whitespace/workspace guard và local DS links/fences/contrast checks đạt |

Vòng visual đã sửa headline orphan desktop rồi render lại. Các lượt browser đầu được giữ dưới `20261026-lumipark-browser-01`: sửa harness để bấm confirmation logout hiện có, tách expected legacy favicon error và tránh cộng dồn throttle khi lặp test. Không sửa backend hoặc nới security để PASS. Screenshots chỉ form trống/feedback, không credentials/cookies/CSRF; không traces/request bodies.

**Giới hạn:** browser chạy real Spring + isolated H2/mock ANPR, không target DB/production/live AI E2E; không chạy executable JAR với operational config. JAR static contents đã kiểm; không claim deployed readiness. Legacy destinations còn auto-request `/favicon.ico` gây console404, báo riêng trong sanitized errors, không phải Login favicon SVG. Chưa manual screen reader/NVDA, Firefox/WebKit, browser200%/400% zoom xác nhận hoặc full WCAG conformance/performance benchmark;320CSSpx reflow đã render. Key shortcut zoom không chứng minh mức zoom có hiệu lực. Chưa có ảnh reference, không pixel-match; Dark chưa triển khai. Remote CI chưa dispatch. Các màn hình khác giữ nguyên và chờ review mới.
