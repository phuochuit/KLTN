# LumiPark — Dashboard & Overview

[MASTER](../MASTER.md) · [Login identity](login.md) · [NV08](../../../docs/requirements/nv08-reporting.md)

## Scope and authority

Login is visually approved by the user and is unchanged. User approved Dashboard implementation with the seven UI/UX Pro Max skills deciding visual details, constrained by real data, current permissions/navigation and Glassmorphism 2.0 + Bento + Light. Dashboard visual acceptance remains pending user review. Canonical design context stays here; do not generate a competing MASTER.

## Coordinated design decisions

- **ui-ux-pro-max:** current-state KPI separated from period analysis; primary stacked revenue comparison, secondary composition, full-width transaction tables. Read actual viewport renders during each slice.
- **design:** asymmetric Bento and gentle canvas depth, not a giant presentation frame. Avoid identical tiles for content of different importance.
- **brand:** reuse approved gateway P and self-hosted Be Vietnam Pro. Navy hierarchy, blue primary and lavender secondary series. No logo redraw or external assets.
- **design-system:** page-scoped `--d-*` material/text/series tokens; independent CSS entry protects Login and legacy pages. Glass is visible via white rim, transmitted pastel color and soft shadow, not a tilted duplicate card.
- **ui-styling:** existing Tailwind v4 compilation, native Thymeleaf/HTML/JS and Lucide static vectors. No React/shadcn or new dependency.
- **banner-design:** heading strip establishes context and observation time, no marketing hero/CTA/filler.
- **slides:** explicit money units, zero-baseline comparison, series labels, readable composition legend and data table alternative. No decorative KPI progress or invented trend percentages.

## Dashboard visual refinement

The supplied desktop/mobile references guide density and hierarchy, not sample numbers or new controls. The Dashboard keeps Be Vietnam Pro and the existing four current-state KPIs, real trend and selected-period breakdown. Page-specific functional text is at least 12px for metadata and generally 13–14px for navigation, labels, controls and tables; major values and section headings carry the hierarchy. The shell uses a quieter blue-to-lavender canvas, a more opaque reading surface for data, and four softly tinted KPI surfaces (blue, lavender, cyan, peach); the first retains a slightly stronger emphasis, without implying a different data state. Icons have small matching backplates; table bodies remain solid. This override does not change Login tokens or branding.

On roomy desktop the period filter is a compact horizontal strip immediately after KPI, bringing the trend into view sooner without making native inputs smaller. At narrower widths it wraps into a labelled form. The chart and period composition remain an asymmetric pair where width permits; they stack in reading order on laptop/mobile. The zero-revenue trend compresses its plot to a selectable month strip while retaining explicit zero/period text and the accessible data table, instead of leaving a large blank chart. Transaction table and operational activity stay full-width with legible headers, tabular amounts and horizontal-scroll access on mobile. No search bar, notifications, invented comparison percentages, or fake runtime revenue from the references are added.

Desktop period selects share the available width evenly across the strip again; narrow layouts can wrap them without shrinking their accessible controls. Dashboard transaction pagination remains fixed at ten rows, with numbered buttons, current-page state, Previous/Next and ellipses for distant pages; there is no rows-per-page selector. The existing backend page-size contract for other clients is unchanged.

## Data and interaction

Current OPEN count, registered vehicles, households, today's revenue and current-month revenue initially come from the server-rendered model and can be refreshed through `/api/dashboard/overview`, with a real server-generated timestamp. Period filters never overwrite these current-state values. Revenue `/trend`, `/summary` and `/transactions` share half-open period aggregation; legacy `/monthly` remains compatible but is not used by this UI because its inclusive boundary could disagree at midnight. Details use DB pagination/search/source/order. Global period totals must not be compared with or replaced by a single page's sum. Monthly totals remain historical fee/payment aggregation, not settlement/reconciliation certification.

Period controls precede the analysis panels and explain their scope. Trend has its own6/12-month control; a fresh mobile view defaults to six recent months, desktop to12; **both show a fixed10 transactions per page** with no row-count selector. Older Dashboard URLs carrying `size` are normalized to10 while retaining other filters and the page index; the existing backend pagination API remains compatible with other clients. Clicking a month updates composition/details in place with an explicit drill-down link, never silently scrolls the document. Chart resize reveals the selected/latest month only within its own scroller. Period, search, source, sorting and page persist in the URL for reload and Back/Forward. Changing period/query/source returns to page1; page controls announce visible range, total count and current page. Refresh overview does not reset analysis state. KPI links respect existing grants and only use filters actually implemented by their destination.

Treat zero, missing data, loading and failure separately. Clear old-period values while loading/error; abort superseded requests. Do not interpret forbidden/session-expired/invalid responses as zero. Render identifiers with text nodes. No synthetic values in production assets; browser fixtures must remain in tests and be disclosed.

Transactions remain disabled until their period summary resolves successfully; a pending/failed period must never reuse a prior label. Same-period pagination keeps table footprint stable while loading to avoid collapsing the page under the focused page control. Previous rows may remain visibly dimmed and explicitly labelled as previous data until a new page succeeds; errors remove them, not present them as current results.

Copy distinguishes current-state KPIs, recent-month trend and selected-period revenue without repeating implementation details in every success message. A period's amount remains global while table search/source filters change only rows; the concise scope note appears next to the transactions. Loading and errors remain in accessible status text, retry stays available; empty filtered results offer a filter reset, while an actually empty period suggests choosing another period. Do not emit redundant success announcements after each page load.

Navigation follows current Thymeleaf authority conditions and current SecurityConfig (DASHBOARD_VIEW/HISTORY_VIEW/ROLE_MANAGEMENT for revenue). No new REVENUE_VIEW grant is implied. Logout remains native POST with CSRF. Dashboard shell is not yet applied to other pages.

## Responsive and accessibility

Use actual available width, including the sidebar. Sidebar becomes a native modal navigation drawer below desktop; drawer supports Escape and focus return. KPI grid reflows; charts/tables retain deliberate internal horizontal scrolling rather than body overflow when labels need space. Always provide explicit periods, zero values and data-table access. No hover-only information or color-only status.

Use the sidebar's thin rounded scrollbar family on the Dashboard page, internal table/chart scrollers, drawer and supported native select pickers; preserve visible overflow/keyboard access and platform fallback. Internal horizontal scrollbars can be slightly thicker than the sidebar; mobile tables include a concise horizontal-scroll cue. Forced-colors uses system scrollbar colors. No restyling of Login or legacy pages.

Laptop panels stack when their available width would crowd month labels; composition reflows horizontally. Sidebar fixes brand and account/logout regions; only navigation scrolls with an inset visible thin scrollbar when needed. It never masks body overflow or makes logout unreachable. Native selects use progressive `appearance:base-select`/`::picker(select)` styling where supported, keeping platform keyboard/popover fallback and browser viewport positioning. No new component library. Source: MDN `https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Selectors/::picker`; browser support must be verified, not assumed. Escape dismisses chart tooltips without moving focus. Without JavaScript, current KPIs/recent sessions remain readable and native navigation is available; charts/filtering need JS. Logout remains native POST/CSRF with browser confirmation, legacy pages keep their application dialog.

Materials have opaque fallback, reduced-transparency and forced-colors support. Motion is limited to state feedback, disabled for reduced motion. WCAG certification and real-device keyboard testing are not implied by browser checks.

## Verification boundary

Browser verification has two distinct paths: isolated Spring/H2 with synthetic accounts/browser-only large-data/error fixtures, and owner-approved connected demo via `dashboard-live.mjs` without revenue mocks or DB seeding. Connected checks compare trend/summary/all DTO pages, duplicate keys, filters/history/refresh and actual opened dropdown/sidebar states. Reports redact account name and row identifiers. A live check never inherits PASS from H2; it requires correct effective datasource and server-readiness. Focused H2 tests do not certify production accounting, MySQL concurrency or ANPR/Desktop E2E. Final visual acceptance belongs to the user.

The money axis remains pinned inside the chart's horizontal scroller; mobile columns may shrink to36CSSpx so a six-month view fits common phones without clipping currency labels. Smaller viewports can still scroll chart content while keeping the axis readable.
