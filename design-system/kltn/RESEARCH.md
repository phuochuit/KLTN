# Research, provenance và verification

[MASTER](MASTER.md) là quyết định thiết kế; file này chỉ giải thích input/giới hạn, không system cạnh tranh. Ngày khảo sát:2026-10-25. Source snapshot ở [inventory](SCREEN-INVENTORY.md).

## G4B-CARD-FE — hậu kiểm (2026-11-01)

Research và quyết định trước Build được giữ tại [cards override](pages/cards.md), với output local `.artifacts/tool-runs/20261101T092034Z-g4b-card-fe-research-c5843bf3/`. Sau Build, Playwright quan sát H2 synthetic desktop/mobile và refinement dialog; screenshots tại `.artifacts/test-runs/20261101T125558Z-cards-flash-browser-e3c1b0b1/`. Đây là review hậu kiểm, không phải nghiệm thu người dùng; nhánh feedback thành công mới chưa được thử lại qua browser POST/interception, như [testing strategy](../../docs/testing/strategy.md) ghi rõ.

Targeted follow-up: UX search `table horizontal scroll action discoverability` trả cả khuyến nghị tránh overflow trang lẫn cho phép cuộn bảng responsive; áp dụng vùng cuộn có nhãn và cột thao tác sticky, không tạo hệ thống mới. Browser fixture và ảnh mới tại `.artifacts/test-runs/20261101T131313Z-cards-targeted-harness-cc45c221/`; review này đã quan sát success/error giả lập, thay thế giới hạn browser trước đó nhưng không chứng minh transaction hoặc nghiệm thu.

## Group 4A redesign — implementation research (2026-10-27)

This section records actual UI UX Pro Max searches performed before the approved Group 4A build. It does not replace MASTER or claim runtime/usability validation.

### Searches and decisions

The installed project-local skill is `.opencode/skills/ui-ux-pro-max/scripts/search.py` (Python 3.12.10). `--help` was read before use. Generator output was printed only; **no `--persist`, `--force`, installation, configuration or network changes** were made.

| Actual search | Result | Decision |
| --- | --- | --- |
| `property operations administration readable light data workflows --design-system --project-name "LumiPark Group 4A" --format markdown --density 7` | Glassmorphism matched, but pattern was Hero-Centric, teal/emerald palette, Cinzel/Josefin, marketing hero and property-photo/virtual-tour advice. | Reject the output for this operational admin task. Preserve existing LumiPark MASTER, Be Vietnam Pro and source-backed data hierarchy. |
| Retry: `property administration records --design-system --project-name "LumiPark Group 4A" --format markdown --density 7` | Same mismatched hero/teal/luxury direction. | No generator result was persisted; design comes from approved Group 4A task and focused findings below. |
| `long form draft error recovery --domain ux --max-results 5` | Error recovery, announced errors, submit feedback, truncation and associated labels. | Retain the form in the current document for known errors; label feedback and provide a clear recovery path. |
| `focusable error summary validation --domain ux --max-results 5` | Focusable linked error summary, inline field errors, live announcement and recovery (High). | Use a focusable summary after failed submission and field-linked errors; do not move focus on each blur. |
| `server search pagination data table --domain ux --max-results 5` | Table overflow, no-results guidance and autocomplete. | Use stable server pagination/search from the approved plan; responsive table region remains labeled. Search recommendation does not justify a fake autocomplete. |
| `accessible authentication password validation --domain ux --max-results 5` | Password managers/paste, show/hide, blur validation, accessible names and stable layout (Critical/High). | Preserve password-manager/paste and improve independent show/hide/error feedback without changing policy. |
| `responsive table form validation semantic controls --stack html-tailwind --max-results 8` | v4 semantic tokens, mobile-first/responsive spacing, long-label handling, focus and breakpoint review. | Apply selectively to existing Tailwind v4; do not introduce a framework or replace the approved CSS/token architecture. |
| `keyboard focus form validation --stack html-tailwind --max-results 6` | Focus-visible plus visible focus state. | Preserve visible keyboard focus and inspect actual rendered contrast/clipping. |
| `family resident record administration --domain product --max-results 5` | No direct match. Retry `CRM contact management` matched CRM & Client Management. | Use only the general record-search/profile idea; reject sales pipeline/marketing dashboard. |

### Pattern comparison and chosen Group 4A direction

- Households/residents: list-first with appended form, permanent split view, modal/drawer, and a read-first contextual workspace were compared. Choose a single active search list and an in-main household/resident workspace, then a focused editor. This makes household membership/quota visible and avoids a long modal or repeated object picker.
- Vehicles: table with appended form, modal wizard, and list → read-only profile → focused editor were compared. Choose the latter to make household/registered owner/per-vehicle authorization/quota readable before technical vehicle/document fields.
- Security audit: dashboard/timeline/table were compared. Keep the source-backed event table central, because time/actor/action/result comparison and the real 25-row server paging are the task; no decorative metrics.
- Settings/password: retain short forms rather than impose record-workspace patterns.

The approved implementation details are in [Management core](pages/management-core.md). These decisions preserve source semantics: household code/member normalized CCCD/vehicle normalized plate upserts; resident move behavior; active-vehicle quota rules; current audit filter semantics; and current password/session lifecycle. Visual review remains required after rendering.

### Password route follow-up — 2026-10-29

The project-local UI UX Pro Max searches were run before finalizing the `/account/password` visual pass; results informed presentation and interaction only, not authentication policy or acceptance:

| Actual search | Result | Use / limit |
| --- | --- | --- |
| `glassmorphism password security form --domain style -n 5` | Glassmorphism recommends layered light, blur and depth; its notes call for text contrast, keyboard focus and reduced-motion support. | Applied translucent/blurred outer card, opaque password controls, visible focus and reduced-motion CSS. This is not a rendered contrast audit. |
| `password validation field linked errors --domain ux -n 5` | Focusable error summary, password visibility and password-manager/paste findings. | Kept a focusable server error alert, inline live policy/confirmation feedback, independent named visibility buttons, and password-manager guidance. No screen-reader conformance claim. |
| `eye eye-off visibility --domain icons -n 5` | Eye/eye-slash matches were listed under Phosphor. | Used the repository's locally built Lucide eye/eye-off SVGs instead; no package or network asset was added. |
| `responsive focus-visible controls --stack html-tailwind` | Focus-visible, focus states and breakpoint visibility guidance. | Used native CSS focus and responsive reflow; no Tailwind stack migration. |

The visual and behavioral observations are recorded separately from design decisions in the [Management core implementation notes](pages/management-core.md). Browser screenshots prove only the stated isolated synthetic review; user acceptance and assistive-technology testing remain open.

#### Final password copy refinement — 2026-10-29

The installed UI UX Pro Max UX search `password validation length plain language` returned general password-visibility, line-length and inline-validation findings; it did not provide a Vietnamese password-policy copy rule. The `html-tailwind` search `password field helper error copy` returned no database match. These findings were used narrowly: keep the control discoverable, make validation actionable, and do not imply a stack-specific recommendation where none was found. Copy was then checked against the existing source policy, not inferred from search output.

The final helper says a new password needs at least 12 characters and notes that dấu/emoji can make the maximum arrive sooner. The front end continues to enforce the existing minimum of 12 Java UTF-16 code units and maximum of 72 UTF-8 bytes; the template presents the existing backend length rejection in plain Vietnamese. No policy or backend behavior changed. Final Edge/Playwright observations are recorded in [Management core](pages/management-core.md) and are limited to an isolated H2 synthetic account; screenshots are captured with blank password fields. They are not user acceptance or screen-reader/accessibility conformance evidence.

### `/roles` — G4B-ROLE-FE post-implementation review (2026-10-29)

This is an as-built description after implementation, not a claim that this record preceded the design/build or that the user accepted the UI. The page uses one role directory and one contextual editor; permission controls are grouped by operational area instead of repeated as an unstructured checkbox wall. These choices keep role identity, system/assignment limits, draft state and session impact in the same review context. Styling is page-scoped and inherits the approved Light Glassmorphism 2.0 direction and dashboard shell; it does not change role policy or create a second design system. Detailed as-built states and invariants are in the [roles override](pages/security-roles.md).

The screen presents immutable post-create role code/prefix, system and assigned-role enablement limits, an explicitly valid empty permission set, `MANAGEMENT` bootstrap/bypass caveats, and session revocation on every role update. It keeps existing permission names, POST/CSRF, backend authorization and the other pages unchanged. This is UI wording/state presentation, not a replacement for server enforcement.

| Executed check | Result / boundary |
| --- | --- |
| `npm run build` | PASS for the existing Spring frontend asset build. |
| `node --check` for `security-roles.js` and the local Playwright review script | PASS. |
| Playwright/Edge, run `20261029T070642Z-roles-fe-4e9a82bf` | Script exit 0; 14 recorded review checks across 1440×900, 1280×720, 390×844 and 320×740; no horizontal page overflow in captures, no page/console errors. Screenshot/report paths and individual states are in the roles override and local run directory. |
| Synthetic Spring browser harness | Surefire report: `LoginBrowserHarnessTest` 1/1, 0 failures/errors/skips. The Playwright review and the creation of the empty-permission role used isolated H2; ANPR was mocked. The wrapper left `logs/harness.exit` empty, so no numeric Maven exit is claimed here. |

This is visual Build feedback, not live E2E or deep ROLE-V. Session-revocation/authorization matrices, no-JS, screen-reader and broader accessibility conformance, physical mobile/other browser engines, live DB and user visual acceptance remain unverified or pending. No search-generator output or numeric contrast computation is represented as evidence for this pass.

### `/account-management` — G4B-ACC-FE1 post-implementation process/evidence (2026-10-29)

This records actual chronology and review inputs, not a retroactive design basis. The required `ui-ux-pro-max` skill was loaded **after** the account UI implementation. Its local, non-persisted searches were then run only to inform post-implementation review: `keyboard focus modal --domain ux -n 5`, `responsive table search --domain ux -n 5`, and `responsive table form layout --stack html-tailwind`. Outputs and command summary are under `.artifacts/tool-runs/20261029T093730Z-uiux-acc-fe1-review-b5fc6c37/`. They supplied general review prompts, not prior decisions; they do not satisfy the plan's pre-build skill workflow and do not establish accessibility conformance. No generator output was persisted or used to overwrite MASTER.

The implemented direction is documented as-built in the [account override](pages/account-management.md): retain the approved LumiPark shell, keep directory/data controls readable and near-opaque, and use page-scoped presentation. It does not claim those choices were derived from the post-build searches. Executed test and browser evidence, the initial disclosure defect/fix, and remaining verification gaps are recorded in the override and [testing strategy](../../docs/testing/strategy.md). Review remains synthetic and awaits user UI/UX acceptance.

### `/account-management` — scoped FE1 visual redesign (2026-10-29)

This later redesign pass used the required skill workflow before editing this visual iteration: `ui-ux-pro-max` led UX research and `design-system` supported it. The original implementation's post-build skill chronology above remains an accurate historical deviation; this pass does not rewrite it. Existing [MASTER](MASTER.md) and the account override remained the design authorities.

The pre-edit Edge baseline was captured against the isolated H2 synthetic browser fixture at `.artifacts/test-runs/20261029T101456Z-acc-fe1-baseline-browser-6b59a072/account-management-visual-review/`. It made the crowded row-action hierarchy, actions at the far edge of a horizontally scrolled table, detached collapsed create disclosure, and top-left dialog anchoring visible. The first redesign choice keeps the semantic table and native POST/CSRF contracts, makes employee identity plus profile the pinned/primary column, groups security actions under a row disclosure, locates the create disclosure within the directory, and uses an employee-contextual right-side profile editor. Bulk selection/editing was rejected: the installed search's suggestion was low-severity and no approved bulk contract exists.

| Actual local UI UX Pro Max search | Finding used narrowly | Decision / limit |
| --- | --- | --- |
| `data table column priority action hierarchy account directory` (`ux`) | Mobile table overflow; semantic heading hierarchy; bulk-edit suggestion marked Low. | Keep a labeled horizontal scroller and semantic table headings; reject bulk controls without an approved contract. |
| `admin directory search filters status empty state user list` (`ux`) | Helpful empty/no-result copy and explicit submit feedback; autocomplete and deep-link findings were generic. | Preserve the loaded-directory boundary and helpful result feedback; do not invent remote search, URL state or new endpoints. |
| `profile editor contextual panel draft discard save validation` (`ux`) | Stable layout, meaningful contextual status and focusable linked error summary. | Keep the existing dirty-draft/focus behavior, identify the selected employee/status, and avoid layout-shifting presentation. This search did not prove the existing POST error path's accessibility. |
| `responsive data table action column horizontal scroll accessibility` (`html-tailwind`) | Existing stack guidance on loading feedback and reduced motion; no specific action-column recipe. | Keep the repository's Tailwind v4/vanilla CSS setup; no stack migration or generator persistence. |

The research artifacts and command/exit summary are `.artifacts/tool-runs/20261029T101522Z-uiux-acc-fe1-targeted-research/`. Those are local research inputs, not a replacement for rendered review. The actual final screenshots, browser result and remaining gaps are recorded in the [account override](pages/account-management.md); none is human visual acceptance or full accessibility validation.

### Final interaction refinement — rendered observation (2026-10-29)

After the redesign render, a slow manual refresh still looked idle and active/disabled button states lacked enough distinction. Edge 156 also supported customizable selects, but the first keyboard experiment showed the customized picker did not commit a selection using the expected native key sequence. The final implementation therefore treats the popup styling as progressive enhancement for pointer use and restores `appearance: auto` on `:focus-visible`; the rendered keyboard check then changed the selected role, while browsers without `appearance: base-select` keep their native picker. The refresh treatment reports pending, successful and stale/error states without changing the GET route or polling interval.

This is a post-render refinement record, not a retroactive pre-build rationale or a new MASTER token/pattern. Evidence, inspected captures and exact limits are in the [account override](pages/account-management.md) and the final test entry in [testing strategy](../docs/testing/strategy.md). Browser observations are limited to Edge with synthetic H2 data; they do not establish assistive-technology conformance or user acceptance.

The subsequent FE1 quality correction used the existing MASTER primary white-on-blue and native-select fallback rules. Before editing, targeted `button contrast interactive states` and `select keyboard options` UX searches returned contrast, state feedback and keyboard focus guidance; the generic suggestion to lower disabled opacity was rejected in favor of the MASTER's opaque disabled tokens. The actual CSS specificity conflict and rendered verification are documented in the [account override](pages/account-management.md). These findings did not create a new design system or modify shared Group 4A components.

### `/account-management` — design-system-driven FE1 finalization (2026-10-29; pre-build basis)

This design direction was researched and recorded in the canonical account override before changing application source for the finalization pass. Existing LumiPark MASTER and Group 4A patterns remain authoritative; no generated palette or new component system was persisted.

| Actual local UI UX Pro Max search | Verified result | Decision / limit |
| --- | --- | --- |
| `account directory readable table hierarchy` (`ux`) | Readable font size (High), horizontal scroll/card handling for tables, contrast and heading sequence. | Keep the semantic table and labeled scroller; establish a name-first hierarchy and readable role-specific sizes. The generic “16px body on mobile” is not applied indiscriminately to every badge/metadata label. |
| `action menu modal focus dismissal` (`ux`) | Visible focus on modal controls (High), focus-appearance and focus-not-obscured guidance. | Preserve visible keyboard focus and prevent an open row disclosure from remaining behind/reappearing after the modal. The search did not produce a menu dismissal implementation rule. |
| Retry `row action disclosure close modal` (`ux`) | Only the same generic visible-focus result. | Treat menu lifecycle as an observed local interaction, not a search-backed recipe; use native disclosure + native modal semantics, not a custom menu role. |
| `responsive table overflow scroll` (`html-tailwind`) | Responsive padding, compact-label overflow, and stable wrapping/truncation guidance. | Keep content wraps and the existing labeled scroller; no table-to-card duplication or Tailwind/JavaScript widget migration. |
| `form editor label hierarchy readable` (`ux`) | Label association, submit feedback, contrast, and readable text. | Preserve actual `<label>` associations, use the MASTER's hierarchy, and keep server outcomes distinct without inventing inline server-validation behavior. |

The pre-edit Playwright/Edge baseline `.artifacts/test-runs/20261029T135527Z-acc-design-baseline-51ec3728/design-baseline/` used the isolated H2 synthetic fixture; it made no account POSTs and captured no temporary secret. At 1440px, directory heading was 17.92px, filter label 11.52px, table body 12.32px, secondary cell text 10.88px, and action labels 11.2px. At 320px, action labels fell to 10.56px. The three summary cards plus repeated count preceded the table. Baseline captures also showed the decorative hero sequence, a name/ID/username/password-status/action bundle in the pinned employee cell, and a dialog overlaying a still-open row disclosure; the disclosure remained open after editor close on desktop. These are direct rendered observations and computed CSS values, not an accessibility conformance result.

The hierarchy, compact loaded-list summary, 14/20 table and control text, 12/18 truly secondary metadata, 16/24 primary identity and field controls, 44px target, preserved table scroll, native-select fallback and close-disclosure-before-editor decisions are now in the [account override](pages/account-management.md). The wider Group 4A table baseline is 14px body/12.8px header with 44px native-control geometry; it supports consistency without changing shared styles. Rendered post-build evidence will be documented separately and cannot retroactively turn this basis into human acceptance.

### Account action dropdown — G4B-ACC-FE1 final fix (2026-10-29; pre-build basis)

Before changing application source, the existing markup/CSS/JS and account override were inspected. The action panel was a normal-flow child of an open row `<details>`, explaining the row expansion; no reusable Web popover implementation was found. Local UI UX Pro Max searches were run and retained under `.artifacts/tool-runs/20261029T153000Z-uiux-account-action-popover-1a66028f/`: the UX results repeat table-overflow/focus guidance, and the retry/HTML-tailwind results do not establish a specific anchored-menu implementation. MASTER §7 and the account override remain the design authority; no generator output was persisted.

**Chosen approach:** declarative native button → `popover="auto"` for the actions, retaining semantic disclosure state and ordinary form/button keyboard order without imposing `role="menu"`; top-layer rendering prevents clipping by the table/scroller, and page-local fixed-coordinate placement flips/clamps to the viewport and updates on scroll/resize. Auto popover behavior supplies light dismissal, Escape handling, single-open behavior and invoker focus navigation; lifecycle synchronization handles the open-state attribute and action/editor handoffs. Preserve existing confirmation, CSRF, POST routes and permissions. Avoid CSS anchor positioning so required placement does not depend on its browser support.

Official source checked before implementation: [MDN Using the Popover API](https://developer.mozilla.org/en-US/docs/Web/API/Popover_API/Using) and [MDN `popover` global attribute](https://developer.mozilla.org/en-US/docs/Web/HTML/Global_attributes/popover), which document top-layer rendering, auto dismissal/stack behavior, declarative invokers, implicit accessibility/focus navigation and toggle events. These sources support the browser primitive, not the custom positioning code or an accessibility-conformance claim. The targeted design and verification limits are in the [account override](pages/account-management.md).

**Post-build observation:** the final scoped browser review is `.artifacts/test-runs/20261029T154619Z-acc-action-popover-playwright-539c964c/account-action-popover-review/review.json` (Playwright + Maven wrapper PASS; focused Spring harness 1/1). Desktop/mobile first/last placement, overflow beyond the nested table scroller, page/table scrolling, resize, keyboard/dismissal/focus, cancel-only confirmations and editor transition passed; row heights stayed stable, with no page/console errors or mutation POSTs. Inspected final captures and limitations are linked in the [account override](pages/account-management.md). Focused frontend tests are 11/11 (`20261029T154248Z-acc-action-popover-focused-57218abe`); the fresh Tailwind build exited 0 and emitted the popover rules (`20261029T154849Z-acc-action-popover-build-469e5ce9`). This remains Edge + synthetic H2 rendered evidence, not accessibility conformance, live E2E or user acceptance.

### `/account-management` — sessions, shifts and polling — G4B-ACC-FE2 (2026-10-29; pre-build basis)

Before FE2 application-source edits, the local UI UX Pro Max searches were run and their outputs/arguments retained under `.artifacts/tool-runs/20261029T160652Z-acc-fe2-uiux-research-retry-5d4e0995/`. The retry `stale data freshness retry dashboard` returned no relevant guidance; the dialog query returned only broad focus/live-status guidance; the date/time query returned linked inline-error and focusable-summary guidance; the `html-tailwind` responsive-table search returned wrapping/overflow guidance. No generator output or unverified freshness prescription was persisted. MASTER §§8–11, Plan §134–140, current controller/service contracts and a new browser baseline drove the design.

The pre-build Edge review is `.artifacts/test-runs/20261029T161213Z-acc-fe2-baseline-826b6419/account-fe2-baseline/baseline.json`: desktop 1440×1000 and mobile 390×844; the mobile session and shift tables measured 738px/1175px inside a 390px viewport. With the existing Web-session confirmation open, a successful live poll detached its submitting form while the dialog remained open. The shift form's independent draft/focus survived. Review data is isolated H2 synthetic accounts and mocked ANPR, with no mutation POST; these are observed frontend facts, not deployed authorization evidence.

The FE2 design decisions were recorded in [the account override](pages/account-management.md) before changing source: never imply SessionRegistry/active-session data proves a person is online; disclose the existing query/source limits; show the time of the **displayed** snapshot separately from a newly received but deferred update; retain displayed data on transient network/server failure; distinguish redirect/401 expiry, 403 access loss, network rejection and HTTP failure; preserve 3-second GET-only polling, non-overlap and hidden-tab pause. Both live fragments are applied atomically only after open dialogs and live-region focus are clear; when a dialog closes, focus is matched to the same session action if still present, otherwise the relevant heading. Shift time validation is limited to the existing start/end ordering and 24-hour server maximum; eligibility and overlap remain server authoritative. Data/form surfaces stay near-opaque within the LumiPark glass/chrome layer; mobile retains the semantic, horizontally scrollable table rather than duplicate cards.

Platform checks consulted: [MDN `HTMLDialogElement` close event](https://developer.mozilla.org/en-US/docs/Web/API/HTMLDialogElement/close_event) (non-bubbling; attach per dialog), [`showModal()`](https://developer.mozilla.org/en-US/docs/Web/API/HTMLDialogElement/showModal) (modal inertness), [`fetch()`](https://developer.mozilla.org/en-US/docs/Web/API/Window/fetch) (HTTP statuses resolve and must be classified, while network failures reject), and [`visibilitychange`](https://developer.mozilla.org/en-US/docs/Web/API/Document/visibilitychange_event) (stop background UI work while hidden). These document browser primitives, not the FE2 lifecycle implementation or accessibility conformance.

### G4B-ACC-FE2 — post-build rendered review (2026-10-29)

The final Playwright/Edge report `.artifacts/test-runs/20261029T163720Z-acc-fe2-final-review-0a11600b/account-fe2-final-review/review.json` records **17/17** focused interaction checks on isolated loopback Spring/H2 `create-drop`, synthetic accounts and mocked ANPR. The rendered desktop/mobile widths, semantic horizontal scrollers, shift duration boundary, poll/focus/dialog deferral, hidden-tab pause, non-overlap, stale/network/auth states and cancel-without-POST are summarized with inspected captures in the [account override](pages/account-management.md). The corresponding focused Spring browser harness passed `LoginBrowserHarnessTest` 1/1; this is not live E2E, ACC-V or accessibility conformance.

Focused frontend `npm test` passed 25/25 (`20261029T164405Z-acc-fe2-frontend-tests-6be5b6d0`), and standalone asset build exited 0 (`20261029T164411Z-acc-fe2-frontend-build-344a43dd`). A later 390×844 browser diagnostic (`20261029T164225Z-acc-fe2-skip-link-diag-417a5887`) resolved the skip-link text seen in a full-page screenshot: its live viewport rectangle is above the screen while focus is elsewhere and moves into view only on focus; the text appears in the full-page fixed-position capture, not the viewport capture. No shared shell change was made. This is post-build evidence, not a retroactive pre-build design basis or human acceptance; user UI/UX acceptance remains pending.

#### Final shift-input and icon refinement — rendered observation (2026-10-29)

An Edge baseline showed the browser/OS-owned `datetime-local` presentation remained month/day with AM/PM even when the document was Vietnamese; adding an input `lang` or setting Playwright's browser locale to `vi-VN` did not make that native UI reliably render the requested fixed format. The narrow correction uses explicitly labeled `dd/MM/yyyy HH:mm` text input, strict calendar and 24-hour parsing, and hidden ISO-local minute fields for the existing backend contract. No custom picker or timezone conversion was added; the native inputs remain a no-JavaScript fallback. Start/end order and the current 24-hour maximum remain validated, and eligibility/overlap remain server-authoritative.

The ↗ glyphs were inert decorative spans styled like controls. They are now non-interactive, assistive-technology-hidden local Lucide `clock-3` assets beside the shift headings. UI UX Pro Max searches were used as narrow review prompts only: local UX searches about native date/time locale behavior and decorative heading arrows returned broad locale/input-label/icon-semantics guidance, not a native-control format guarantee or implementation prescription. Captured command output is under `.artifacts/tool-runs/20261029T170924Z-acc-fe2-final-ux-research-da184234/`.

Focused frontend tests passed **21/21** (`20261029T172013Z-acc-fe2-refinement-focused-tests-05f305c5`); `npm run build` passed (`20261029T172018Z-acc-fe2-refinement-frontend-build-897c179b`). Edge/Playwright review and the focused isolated H2 browser harness passed in `20261029T172839Z-acc-fe2-refinement-final-playwright-2899fa66`; the JSON report, screenshots and precise assertions are recorded in the [account override](pages/account-management.md). An interrupted earlier harness attempt is retained as interrupted and is not counted. This evidence does not include ACC-V, full regression, live E2E, no-JS runtime or user acceptance.

## Requirements synthesis

### Fluid form + restrained materials

Người dùng muốn co giãn cả form lẫn hai cột, không chỉ ép hero khi viewport hẹp. Đổi track480/440px sang1.15fr/1fr, form max544px; padding và heading dùng CSS clamp. Không đổi chiều cao input52px/font input16px hoặc ép panel stretch theo viewport. Đây là Thymeleaf + Tailwindv4 hiện có; áp dụng phần responsive/styling phù hợp, không thêm React/shadcn hoặc rewrite toàn bộ utilities.

Browser resize cùng page qua17 widths960→1600, bước40px: form≈387→544px, padding28.8→40px, không nhảy width giữa các desktop breakpoint. Chuyển một cột dưới960px vẫn là thay đổi layout có chủ đích; cap544px là giới hạn đọc, không claim tăng liên tục vô hạn. Kiểm6 viewport desktop×4 feedback states không extra scroll; mobile header gap24–40px, target≥48px. Lớp sau tắt trên mobile, desktop giảm angle/độ lộ/reflection; nền mobile neutral hóa phần dưới. SVG chỉ làm rõ glazing và chân công trình; giữ logo/xe/cây.

Ảnh baseline từ lượt trước giữ nguyên để so cùng1440×900 và375×812, ảnh430×932 kiểm cân nền sau form. Nhận xét thị giác vẫn cần người dùng duyệt;17 nhóm browser và14 focused Spring tests không là nghiệm thu thẩm mỹ, thiết bị thật hoặc live AI/DB E2E.

### Final polish — visual comparison, not test counts

Giữ baseline cùng1440×900 và375×812 từ lượt trước, so trực tiếp với render mới. Desktop main lên khoảng44px tại1440×900, gap hai cột thu32–48px, dải ánh sáng nối hai vùng; glass32–60%/blur20px có reflection chéo hẹp và mép lớp sau rõ hơn. Mobile không căn giữa dọc: panel top≈100px ở375×812, thay≈178px; header gap24–28px trên320/375/390/430, không thu target52px. Đây là quan sát/đo cụ thể, không thay người dùng nghiệm thu thẩm mỹ.

Vẽ lại SUV: roof/windshield/cabin/cửa/gương/đèn hậu/tire arches tách rõ, góc sau-ba-phần-tư hướng cổng; sửa arrow đường cùng hướng xe, tăng bóng chân công trình/xe/mặt đường. Cây có tán phân nhánh và bồn neo chân, không oval nổi. Logo gateway P làm rõ trụ/mái và khoảng âm làn vào, không đổi wordmark. Tất cả vẫn là vector gốc, không photoreal/image-generated.

Glass trong hơn yêu cầu foreground tối hơn: sample white32% trên backdrop stress `#9CABDC` cho muted mới `#42506B`4.767:1 và primary `#3042AA`4.970:1. Không nhận đây là đo mọi pixel hoặc chứng nhận WCAG. Overflow9px do halo xoay tại1024px trong iteration đầu đã được xử lý bằng đổi vị trí halo, không giấu body overflow. Giữ report failed và screenshot trước/sau; chỉ lượt cuối có16 nhóm browser PASS. Login behavior/API/security không đổi.

### Tinh chỉnh nền mở + một cụm kính

Người dùng từ chối khung lớn bao toàn màn hình và nội dung phụ gây cuộn; duyệt giữ bố cục A với chiều sâu/nền của B. Bỏ footer/caption/feature notes/logo lặp; giới hạn form480px, một panel blur20px với một lớp trang trí lệch không blur. Cảnh mới `parking-environment.svg` là vector gốc liên tục, không photoreal/image-generation; không hứa cùng chất lượng 3D reference.

UX search `viewport overflow responsive` trả viewport-meta/table, không đủ làm hướng dẫn chiều cao Login; retry `focus not obscured` trả enhanced AAA, nhận mục tiêu không che focus nhưng không gọi đó là chứng nhận AA. Dùng CSS layout theo nội dung + breakpoint chiều cao và kiểm browser trực tiếp, không persist generator hoặc đổi engine/data skill.

Đo số học composition tại white alpha46% trên `#A6C1F4`: muted cũ chỉ4.324:1 và primary cũ4.394:1 nên đổi muted `#505D7C` (4.821:1), primary `#3548BA` (5.531:1). Border/input alpha78% trên cùng glass3.274:1. Đây là sample vật liệu giới hạn backdrop đã chọn, không audit mọi pixel, trình duyệt/OS hoặc WCAG certification.

Browser regression bổ sung kiểm `scrollHeight/scrollWidth`, CTA trong viewport, không body overflow hidden, 6 desktop sizes ×4 trạng thái (default/error/throttled/passwordChanged). Mobile320px và viewport thấp812×375 phải cho cuộn khi cần và focus tới CTA được. Kiểm forced-colors bỏ blur, không có enclosing-page/nested-stage border. Lượt đầu test mới tái hiện overflow cũ; thử render trung gian phải restart harness do Thymeleaf cache giữ template cũ, không lấy screenshot lẫn CSS mới/HTML cũ làm kết quả cuối. Lớp trang trí lệch gây2px tràn ngang ở320px đã được thu inset, không giấu scrollbar.

### Login phương án A — reference-driven rebuild

Ba ảnh mới được người dùng cung cấp và xem trực tiếp trong hội thoại. Bố cục hai cột, cyan–blue–violet, navy typography và P ribbon thay hướng teal/mint/Bento. Tài liệu là draft chờ duyệt render, không generator-persist hoặc khóa palette toàn Web.

Installed searches thực chạy: `glassmorphism light technology --domain style -n 2` trả đúng Glassmorphism; nhận layered material/blur10–20px, không áp alpha10–30% cho form. `technology blue violet --domain color -n 2` trả Sleep Tracker/Diary lệch product, **loại**; retry `SaaS --domain color -n 1` trả Micro SaaS, chỉ tham khảo light indigo, không lấy emerald CTA vì khác ảnh. `Vietnamese Friendly --domain typography -n 1` trả Be Vietnam Pro/Noto Sans; giữ Be Vietnam Pro local đã có, không thêm font/network. Không coi số lượng dataset hay metadata performance là chứng minh chất lượng render.

Ảnh kiến trúc hiện là SVG gốc do agent dựng, **không phải ảnh AI-generated/photoreal 3D**. Không có công cụ tạo ảnh khả dụng trong phiên; không gọi API bên ngoài hoặc tự cài generator. Hero art còn là gap so với reference. Logo active icon/favicon mới; full/lockup/reversed cũ chưa phải bộ identity mới. Kiểm chứng browser cô lập không thay nghiệm thu thị giác của người dùng.

Kiểm tra số học cho palette Login mới (không thay bảng baseline cũ bên dưới): text navy/white16.144:1, muted/white5.890:1, muted/canvas5.195:1. Control border ban đầu `#8896B8` chỉ2.957:1 trên white nên đổi `#7B89AB`:3.495:1 trên white,3.320:1 trên `#F7F9FF`. Text trắng trên CTA tối thiểu5.520:1 qua257 mẫu nội suy sRGB giữa hai đầu gradient. Đây là kiểm token, không đo mọi pixel composed/anti-alias trong screenshot hoặc chứng nhận WCAG.

### LumiPark Login — 2026-10-26
Build không chạy generator/--persist để overwrite MASTER. Đã chạy installed search cho `accessible authentication` (ux), `corporate readable` (typography), `glassmorphism` (style), `form focus` (html-tailwind), và supporting design logo search `lettermark negative space`. Stack search lần này có match Forms/Focus states và Focus-visible; không sửa kết quả0 của lần nghiên cứu cũ thành một thành công lịch sử.

Nhận password-manager/paste, accessible names/focus và limited motion. Corporate Trust gợi Lexend/Source Sans3, nhưng Login chọn system sans để bỏ font blocking/network và không thêm font chỉ cho brand. Glass dataset alpha10–30% được điều chỉnh lên88% cho form readable; không coi metadata cost low là benchmark. LP SVG dựng vector trực tiếp, không gọi Gemini generator PNG/external API. Đã đối chiếu official Tailwind CLI/theme và Lucide static docs; pin Tailwind/CLI4.3.3, Lucide-static1.61.0 bằng lockfile. Semantic tokens kế thừa MASTER, không tạo hệ riêng.

Ảnh reference không tìm thấy trong workspace truy cập được. Không claim đã phân tích ảnh, Dark QA hoặc rendered WCAG conformance. Chi tiết quyết định triển khai tại [Login override](pages/login.md).
Productivity/operations admin Web cho ban quản lý chung cư, đọc bảng và form dài trên desktop, có responsive hỗ trợ tablet/mobile. Stack source Spring MVC/Thymeleaf + CSS + vanilla JS; target đã chốt Tailwind v4/Lucide. Không React/shadcn/framework migration. Light Glassmorphism2.0/Bento/pastel blue-lavender là user choice; reference image không truy cập được, không có image-derived analysis.

## Skill workflow thực đã chạy
Skill project-local `ui-ux-pro-max`, entry `.opencode/skills/ui-ux-pro-max/scripts/search.py`; Python3.12.10. Đã đọc `--help`; không cài lại skill/dependencies, không sửa scripts/config/MCP. Output root `.` là **ngoại lệ theo yêu cầu rõ của task**, để persist vào `design-system/kltn/`, không internal default. Chưa có MASTER/context cũ nên không overwrite user work, không dùng --force.

```powershell
python .opencode/skills/ui-ux-pro-max/scripts/search.py "property management dashboard glassmorphism" --design-system -p KLTN -f markdown --density 7
python .opencode/skills/ui-ux-pro-max/scripts/search.py "internal admin dashboard glassmorphism" --design-system -p KLTN -f markdown --density 7
python .opencode/skills/ui-ux-pro-max/scripts/search.py "internal admin dashboard glassmorphism" --design-system --persist -p KLTN -f markdown --density 7 --output-dir .
```

Generator persisted MASTER theo Master+pages structure. Sau đó đã biên tập lại **file do chính lượt này tạo**, loại đề xuất lệch brief; không giữ raw generator như một approved system thứ hai.4 page overrides viết theo actual source; không generator-per-screen hàng loạt. `--motion` không dùng để tránh snippet GSAP ngoài nhu cầu; motion research dùng UX guidance và CSS nguyên tắc.

| Search (flags thêm sau script) | Kết quả đã kiểm / dùng thế nào |
| --- | --- |
| `property management dashboard glassmorphism --design-system` | Glassmorphism phù hợp; Hero-Centric/teal/Cinzel+Josefin thiên marketing/luxury property **loại bỏ** |
| `internal admin dashboard glassmorphism --design-system` (retry hẹp) | Glassmorphism + Fira dashboard data phù hợp một phần; Smart Home/IoT classification, dark palette và Operations Landing vẫn lệch. Không có verified full admin blueprint; dùng curated admin composition dựa source, không persist raw dark direction như chuẩn |
| `glassmorphism --domain style -n 2` | styles.csv → Glassmorphism; blur10–20px tham khảo; alpha15–30% **không phù hợp dense Light admin**, nâng86–98% để đọc rõ |
| `bento grid dashboard --domain style -n 2` | Bento Box Grid + Data-Dense Dashboard; nhận modular spans/spacing, loại scale1.02/masonry/fixed-height và export mặc định không có source |
| `dashboard readable sans --domain typography -n 2` | Dashboard Data Fira Code/Fira Sans; Corporate Trust Lexend/Source Sans3. Chọn Source Sans3 cả heading/body, tùy chọn Lexend sau visual review; không mono headings |
| `revenue monthly comparison --domain chart -n 2` | radar/geographic off-topic → **không dùng** |
| `time series trend --domain chart -n 2` (retry) | Trend Over Time/line + accessible table fallback hợp lệ; monthly discrete comparison chọn bars phù hợp source, không radar/geo; không auto chọn chart library |
| `error summary validation --domain ux -n 2` | Web Forms/Focusable Error Summary + Error Messages, High. Nhận linked summary + inline errors + focus sau submit |
| `keyboard focus modal --domain ux -n 2` | Focus States + Focus Not Obscured Enhanced; phân biệt enhanced AAA không tự nhận là AA requirement; thiết kế vẫn ưu tiên focus fully visible |
| `responsive forms tables --stack html-tailwind -n 3` |0 kết quả |
| `responsive --stack html-tailwind -n 3` (retry) |0 kết quả, **không có verified stack database match**. Dùng hướng dẫn Tailwind chính thức và web layout chung có nhãn proposal, không bịa stack Thymeleaf alias |

Đã tham khảo supporting `design-system` token-architecture và tailwind-integration reference. Reference Tailwind đó dùng v3 config/`@tailwind`/shadcn examples; **không áp nguyên trạng cho v4**. MASTER dùng CSS-first theo docs chính thức dưới đây. Dataset performance metadata không phải benchmark Web thật.

## Official integration references
Đã fetch/read các trang dưới trong phiên này; là nguồn hướng tích hợp, không bằng chứng dependency đã cài hoặc build đã chạy:

- [Tailwind CLI installation](https://tailwindcss.com/docs/installation/tailwind-cli): package CLI riêng, import tailwindcss, build static stylesheet, không runtime compiler.
- [Tailwind theme variables](https://tailwindcss.com/docs/theme): top-level @theme, @theme inline khi alias variables, namespace tạo utility classes. Tài liệu online trả v4.3 lúc đọc; version production phải pin/review khi Phase2 bắt đầu, không tự nâng project hiện tại.
- [Tailwind class detection](https://tailwindcss.com/docs/detecting-classes-in-source-files): nguồn cần kiểm lại tại triển khai để pin đúng minor; scan/static full class strategy là handoff, không build-verified.
- [Lucide vanilla guide](https://lucide.dev/guide/packages/lucide) (redirect tới guide Lucide hiện hành): hỗ trợ vanilla/local SVG, không cần React; ISC license. Tên/API theo version được chọn, chưa tải package/icons.
- [WCAG2.2](https://www.w3.org/TR/WCAG22/): reference QA target, không claim audit này là certification.

Hai link class-detection/WCAG là references cho triển khai, không ghi rằng đã làm browser conformance test hoặc fetch toàn bộ chuẩn. Không tải external font/asset hoặc operational media.

## Numeric contrast verification
Script local `.artifacts/agent-work/web-ui-phase1/verify-docs.py` tính relative luminance sRGB (linearize kênh), ratio `(Lmax+.05)/(Lmin+.05)`. Normal text threshold4.5, control-boundary3. Alpha composition tính `alpha*white + (1-alpha)*backdrop` trước luminance. Đây là design-token samples, không screenshot sampling.

Các cặp được kiểm: body/white, muted/white và soft, primary normal/hover/active trên white, primary/selected, accent/white, success/warning/danger/info trên soft tương ứng, control-border/white và soft, disabled-text/bg.36 samples glass: alpha.86/.92/.98 ×3 gradient stops ×4 foregrounds (body/muted/primary/control-border). Chỉ đúng khi backdrop giới hạn palette không chứa ảnh/dark accent; G3 overlay thực opaque fallback và modal isolation vẫn QA riêng.

Kết quả execution Python ngày2026-10-25:15 cặp opaque đều đạt threshold đã đặt;36 glass samples đạt threshold theo vai trò. Giá trị đại diện:

| Pair | Ratio (làm tròn3 số) |
| --- | --- |
| body/white |15.944:1 |
| muted/white; muted/soft |6.358:1;5.826:1 |
| white/primary; white/hover; white/active |5.700:1;7.519:1;9.666:1 |
| primary/selected; accent/white |4.914:1;6.096:1 |
| success/soft; warning/soft; danger/soft; info/soft |6.761:1;6.427:1;5.977:1;8.015:1 |
| control-border/white; control-border/soft |3.625:1;3.322:1 (non-text threshold3) |
| disabled text/bg |5.373:1 |
| minimum trong36 glass samples |3.542:1 (control boundary; text samples so riêng threshold4.5) |

Không tuyên bố Dark contrast, hover/selection screenshot, forced colors, typography glyphs hay rendered material đã PASS từ tính toán này.

### `/account-management` — Directory & Profile Editor refinement (2026-10-29; pre-build research)

This is the research basis for a newly authorized G4B-ACC-FE1 refinement. It supersedes the previous directory/editor presentation proposal only; earlier executed evidence remains historical, and no user acceptance is inferred. The canonical proposal was recorded in the [account override](pages/account-management.md) before application-source edits.

The latest FE1 baseline screenshots were directly inspected from `.artifacts/test-runs/20261029T141404Z-acc-final-playwright-07e8627a/account-finalization/`: the desktop directory and mobile table show a tall employee cell stacking name, employee code, username, a repeated “Không yêu cầu đổi mật khẩu” line and actions; the table's other values occupy separate, loosely balanced columns. The desktop profile-editor capture uses a literal `×` close glyph and leaves the save action available in the unchanged state; the dirty capture shows the existing keep/discard guard. This is a visual observation of the synthetic H2 fixture, not a fresh runtime measurement, accessibility result or human acceptance.

| Actual local UI UX Pro Max search | Finding used narrowly | Decision / limit |
| --- | --- | --- |
| `account directory table hierarchy status actions compact rows` (`ux`) | Semantic labels distinguish state badges from value/action chips; responsive table overflow should be handled; submit feedback and heading sequence matter. | Keep the real table and explicit values/actions; compact identity content and retain feedback. No generated account-table blueprint was returned. |
| `responsive table column priority action buttons mobile overflow` (`ux`) | Horizontal scroll or a card layout can address table overflow; touch targets and overflow behavior need review. | Choose the MASTER-consistent labeled horizontal scroller, retain all columns and pin employee identity; do not duplicate into cards. Keep action column reachable at the table's right edge and state that in the scroll hint. |
| `form dirty state save disabled unchanged loading feedback` (`ux`) | Distinguish loading/disabled feedback and preserve associated labels. The generic search suggests opacity for disabled controls. | Use explicit unchanged/dirty/saving messages and disable a no-op save; retain MASTER's opaque disabled tokens rather than applying opacity. |
| `modal close icon button accessible name focus target` (`ux`) | Use native button semantics, a visible focus indicator and web-sized targets; findings warn not to overstate platform-specific touch dimensions. | Use the existing local Lucide asset pipeline for an X icon with an accessible button name and 44px target; preserve native dialog and draft guard. |
| `responsive table form state` (`html-tailwind`) | Generic v4 findings on responsive padding, parent-state styling and busy/disabled button feedback. | Apply only the scoped CSS/vanilla-JS behavior; no Tailwind/JS framework migration or generated state machinery. |
| `close dismiss x icon accessible button` (`icons`) | `x` is a likely close glyph; meaningful controls need a name, while decorative icons should be hidden from assistive technology. | The icon is decorative inside the named close button. The generator's React/Phosphor example is not used; use repository-pinned local Lucide. |

Search output, Python 3.12.10 command context and individual JSON results are retained in `.artifacts/tool-runs/20261029T143432Z-uiux-acc-refine-ef065a8d/`. Results were generic and partly mismatched to this server-rendered application; LumiPark MASTER, Group 4A's semantic table guidance, source-backed account fields and the observed baseline take precedence. No palette, framework or component-library recommendation was persisted.

### Mobile editor footer correction — follow-up basis (2026-10-29; before correction)

The fresh mobile editor capture at `.artifacts/test-runs/20261029T144639Z-acc-refine-playwright-32d46b3c/account-directory-editor-review/profile-editor-mobile-390x844.png` showed the sticky action footer visibly covering the “Email” label at the initial scroll position. This is direct rendered evidence of overlap, not merely a hypothetical focus issue. The account override now records the proposed responsive correction before its source edit: retain the sticky footer on desktop; put it back in normal flow on narrow screens so every form field remains visible and the user scrolls through to the final actions.

The follow-up UI UX Pro Max search used Python 3.12.10 and is retained in `.artifacts/tool-runs/20261029T144927Z-uiux-mobile-editor-footer-8993b2b6/`: `mobile dialog sticky footer obscures form fields` (`ux`) returned a generic fixed-navigation warning, while `dialog sticky footer scroll inset` (`html-tailwind`) returned no match. A narrower retry, `focus not obscured by sticky dialog footer` (`ux`), returned the Web guidance “Keep the entire focused component unobscured by author-created content” and “Offset sticky UI with scroll-padding and dismiss or move persistent overlays.” It also distinguished WCAG 2.2 AA minimum focus visibility from the enhanced AAA criterion; no conformance claim is made. This supports removing the mobile overlay rather than introducing compensation that would leave the visible Email label under it. The search is generic; the screenshot and project-specific responsive decision take precedence.

Post-build review `.artifacts/test-runs/20261029T145154Z-acc-mobile-footer-playwright-74216631/account-directory-editor-review/review.json` confirms the correction in isolated H2/Edge: Email label/input visible without footer overlap at initial mobile scroll, and address plus both actions visible after scrolling to the end. Ten screenshots were captured; no page errors or account POSTs occurred. The prior rendered review and its exact screenshot finding remain historical evidence; runtime details and limits are in the account override and [`docs/testing/strategy.md`](../../docs/testing/strategy.md).

### `/registrations` — G4B-REG-FE pre-build research (2026-10-31)

This basis was recorded in the [registration override](pages/registration.md) before application-source edits, under the separately authorized Frontend Build. It inherits LumiPark MASTER/Group 4A rather than introducing another visual system. Current-source observations are not acceptance evidence: the old page uses `app.css` and inline JavaScript, applies QR fields immediately, omits a visible review/CSRF/status flow, and has no page-specific responsive treatment. The approved `/registrations` form remains one multipart POST; the backend contract and success routing are unchanged.

| Actual local UI UX Pro Max search | Finding used narrowly | Decision / limit |
| --- | --- | --- |
| `registration form grouped fields error recovery` (`ux`) | Required indicators and concrete error recovery are called out for forms. | Keep text labels and show recovery guidance beside real errors; do not invent field-level server errors. |
| `unknown submit outcome prevent retry` (`ux`) | Submit feedback and a disabled/loading button reduce repeated clicks. | Show a pending state; application/API contract, not this generic search, determines that an unknown POST outcome must not be retried. |
| `error summary field validation focus` (`ux`) | Web error summary guidance recommends a focusable summary linked to invalid controls, alongside inline messages. | Use this for client-detectable constraints only. The current server handler returns a general message, so do not fabricate field mappings. |
| `registration long form section progress` (`ux`) | A progress cue is useful for longer forms. | Use the approved numbered sections and real anchor links; do not hide sections or introduce a multi-submit wizard. |
| `responsive semantic form controls` (`html-tailwind`) | Tailwind v4 guidance favors existing semantic theme tokens over repeated raw palette utilities. | Reuse the existing token/theme and dashboard shell; no dependency or shared-token change. |
| `QR autofill preview explicit apply` (`ux`) | Search returned only generic browser-autofill advice, not QR review/apply guidance. | Keep QR review/apply because the registration override requires it; this search is not evidence for identity verification. |
| `camera upload source preview replace` and `camera permission denied upload fallback` (`ux`) | No targeted result. | Follow the approved media override and browser API lifecycle requirements; do not present the generic search as validation. |

The search ran with the installed local UI UX Pro Max script/Python 3.12.10; command results are retained at `.artifacts/tool-runs/20261031T100047Z-g4b-reg-fe-uiux-7e91a66a/`. Additional official browser references consulted before implementation: [MDN `getUserMedia`](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia) for user-initiated/secure-context camera access and stream lifecycle; [MDN `URL.createObjectURL`](https://developer.mozilla.org/en-US/docs/Web/API/URL/createObjectURL_static) and [`URL.revokeObjectURL`](https://developer.mozilla.org/en-US/docs/Web/API/URL/revokeObjectURL_static) for preview URL ownership; [MDN `Request.credentials`](https://developer.mozilla.org/en-US/docs/Web/API/Request/credentials) for same-origin fetch credential behavior. These sources inform browser mechanics, not application authorization or business acceptance.

Project-source checkpoint before implementation: `/registrations` is protected by the existing `VEHICLE_MANAGE`/`ROLE_MANAGEMENT` route; `/api/cccd/**` separately requires `RESIDENT_MANAGE`/`ROLE_MANAGEMENT`, and CSRF remains enabled for QR POST. `ManagementCoreWriteExceptionHandler` currently returns general HTML errors (`400`, `409`, `413`, `503`); the first three are known outcomes, while `503` explicitly says the write result is unknown and not safe to replay. `AdminController.saveRegistration` already owns the success flash and grant-based `/cards?vehicleId=…` versus `/vehicles?vehicleId=…` redirect. Therefore the frontend will reuse the rendered form CSRF token for same-origin QR/write requests, preserve values only while the current document remains loaded, and never alter backend routing, permission or write semantics.

Before application-source edits, an isolated Spring/H2 + Edge baseline was captured in `.artifacts/test-runs/20261031T100621Z-g4b-reg-fe-baseline-8c351043/registrations-baseline/`. At 1440×900, 390×844 and 320×740, the report records one form, one generated `_csrf` field, zero page errors and no document-width overflow. Direct inspection found the legacy generic header and crowded media actions; the 390px/320px renders squeezed the form into four narrow columns, making labels and controls difficult to scan/use. No QR/camera action or registration POST was performed in this baseline. Surefire XML records the isolated `LoginBrowserHarnessTest` as 1/1 with zero failures/errors/skips and Maven stdout says `BUILD SUCCESS`; the enclosing PowerShell wrapper nevertheless exited 1 because its `Start-Process` exit-code read was blank. Therefore this baseline is recorded as an observed render, not a wrapper-level PASS, application verification or user acceptance. The exact screenshots/review and harness logs remain local evidence; no operational data was used.

The first post-build recovery capture (`20261031T102825Z-g4b-reg-fe-ui-review-complete-ba747fa5/registration-post-review/unknown-save-outcome.png`) showed the unknown-outcome alert and disabled submit action disagreeing: the alert prohibited resend while the button still said “Đang lưu hồ sơ…” and retained its success checkmark. The follow-up UI-only decision, recorded in the registration override before this correction, is to use the disabled label “Không gửi lại” and hide the checkmark for pending/unknown states; known errors restore the normal enabled action. This corrects state semantics without changing backend behavior or retry policy.

#### Build and rendered review (2026-10-31)

- **Scoped source:** `registrations.html`, `registration.mjs`, `registration-model.mjs`, `registration.css`, the frontend build manifest/local icons, and the registration active-nav state in `dashboard-shell.html`; plus focused model tests. No backend, API, RBAC/CSRF policy, schema, business service, `/households` or `/vehicles` redesign was changed.
- **Focused checks/build:** `.artifacts/test-runs/20261031T102959Z-g4b-reg-fe-refinement-checks-dbaf56d9/`: three application/test JS syntax checks plus the local browser-review-script syntax check exited 0; registration model tests **4/4**; frontend asset build exited 0 and emitted `registration.css` into the run's isolated `build-output/`.
- **Final synthetic visual/interaction review:** `.artifacts/test-runs/20261031T103012Z-g4b-reg-fe-ui-review-refined-3f172d6a/registration-post-review/review.json` records **30/30 checks**, Playwright exit 0, zero page errors and zero fixture route errors. It covered skip-link/tab order, mobile navigation dialog, focused client error summary, camera denied fallback, fake camera capture/track cleanup, upload replacing capture, object-URL replacement/reset cleanup, QR review-before-apply, manual-edit raw-data clearing, QR 400 and absent-CSRF behavior, QR camera capture without silent apply, vehicle-image preview, mocked known 409 retention and unknown 503 no-replay lock, and one successful synthetic multipart POST including CSRF followed by mocked `/cards?vehicleId=987654`.
- **Responsive observations:** no page-width overflow and the expected two/one-column field and media grids at 1440×900, 768×1024, 390×844, 320×740 and 812×375. Inspected the final [desktop](../../.artifacts/test-runs/20261031T103012Z-g4b-reg-fe-ui-review-refined-3f172d6a/registration-post-review/desktop-final.png), [mobile](../../.artifacts/test-runs/20261031T103012Z-g4b-reg-fe-ui-review-refined-3f172d6a/registration-post-review/mobile-final.png), [QR review](../../.artifacts/test-runs/20261031T103012Z-g4b-reg-fe-ui-review-refined-3f172d6a/registration-post-review/qr-review-desktop.png), and corrected [unknown-outcome state](../../.artifacts/test-runs/20261031T103012Z-g4b-reg-fe-ui-review-refined-3f172d6a/registration-post-review/unknown-save-outcome.png). The 400/409/503 console errors are the deliberately mocked response cases, not unexpected application errors.
- **Harness boundary:** isolated loopback Spring/H2 `LoginBrowserHarnessTest` Surefire XML is **1/1**, zero failures/errors/skips; Maven stdout contains `BUILD SUCCESS`. The PowerShell `Start-Process` process exit read remained blank, so it is not reported as numeric Maven exit PASS. QR and all registration POST outcomes were intercepted by synthetic Playwright routes; no operational DB/media, physical camera, real QR endpoint, or live enrollment/write was exercised. This is Frontend Build visual feedback, not REG-V, live E2E, or functional acceptance.
- **Workflow deviation:** pre-build source/contract review, local focused UI UX searches, and this design basis were recorded before application-source changes. However, the `ui-ux-pro-max` skill itself was loaded after implementation had started (and `ui-styling` later still); that is a process deviation and the late load does not retroactively satisfy the pre-build skill workflow. Its checklist was used for post-build review only. No accessibility-conformance or human acceptance claim is made.
- **Refinement:** the first unknown-outcome screenshot showed “Đang lưu hồ sơ…” and a checkmark beside an error that prohibited resend. The pre-source correction decision is in the registration override; the fresh final render now uses disabled “Không gửi lại” without the success check while preserving the normal enabled action for a known error.

The basis above is pre-build research plus observed baseline; the execution evidence is explicitly separated here. User UI/UX acceptance remains pending, and the fuller REG-V matrix remains unrun.
