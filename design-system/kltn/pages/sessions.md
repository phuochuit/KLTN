# Sessions page override

`/sessions` inherits the LumiPark shell, colors and component rules from [MASTER](../MASTER.md). Keep `/payments` in its existing navigation group and location; the history page does not introduce a payment workflow.

## Page structure

- Heading and manual refresh → GET search/filter/sort/page-size controls → result range → full-width semantic history table → server-page navigation.
- Search covers entry plate, exit plate and card code. Status uses the existing session enum; sort is entry time oldest/newest; page sizes are 10/20/50. Optional `from`/`to` ISO dates filter entry time, inclusive of both selected calendar days using a half-open range. Follow the existing JVM-local `LocalDateTime` convention; this UI does not define a new timezone. Invalid or reversed dates show an error and no unfiltered rows. Keep `q/status/from/to/sort/page/size` in the GET URL.
- When a status is selected, offer an explicit “Xem tất cả trạng thái” link that clears only status and preserves search, dates, sort and page size; the general clear-filters action remains separate.
- Keep all nine existing session columns: ID, entry plate, exit plate, detected vehicle type, card, entry/exit time, status and fee. A normalized entry/exit plate difference gets only a neutral “Khác biển số vào” cue; do not change stored/displayed plates or infer an ANPR error. Do not add evidence, camera, payment detail, handover or barrier actions.
- Distinguish “no sessions recorded” from “no sessions match these filters.” Keep the result range and current page visible without announcing every table cell.

## Responsive and keyboard behavior

- Desktop groups the primary search/status/date filters above the list controls; sort and page size sit beside Apply/reset rather than at opposite ends of the panel. Narrow layouts keep primary search/status visible, place dates side-by-side when space allows and stack them at 320px so native date values remain readable. Disclose sort/page size with a native `<details>` control while keeping Apply available. Keep the table in its own named, keyboard-scrollable horizontal region. Do not hide history columns on mobile.
- At desktop/tablet widths keep session ID and entry plate sticky; below 48rem only the entry plate stays sticky, so the compact ID column can scroll away. Allow the remaining columns to scroll rather than clipping the table to the viewport; long entry plates wrap inside the sticky cell.
- Use native GET inputs, buttons and links. Applying filters resets to page 0; pagination and refresh preserve query state so browser back/forward and bookmarks restore the same view.
- Correct an out-of-range page request to the last available page, or page 0 when there are no matches, without dropping query, status or sort.
- Keep visible focus, a skip link, one main heading and `HISTORY_VIEW` / existing management fallback. Navigation visibility is not authorization.

## Post-implementation review — Group 3

- Synthetic H2 browser review (2026-10-27): inspected at 1440×900, 1366×768 and 390×844. At mobile width the secondary controls open/close independently; a synthetic plate search updated the GET URL (`q=QA-HISTORY-05`). Narrow table review retained the ID and entry-plate identifiers while the other existing columns remain available by horizontal scroll.
- Screenshots and synthetic harness report: `.artifacts/test-runs/20261026T185719Z-group3-render-50801148/`.

## Current scoped refinement — 2026-10-27

- Date filtering now adds optional inclusive `from`/`to` entry dates, preserves both in refresh/pagination links, and reports invalid/reversed dates without silently falling back to unfiltered history. Browser Back restores the prior page URL. This remains server-rendered GET filtering.
- On narrow screens the history table keeps the entry plate (not ID) at the left scroll edge, wraps long plate strings, and leaves every column reachable by horizontal scroll. Desktop/tablet retain both sticky identity columns. The nine-column table is unchanged.
- Synthetic H2 + mocked-ANPR Microsoft Edge review: 1440×900, 1366×768, 390×844 and 320px reflow; no page-level horizontal overflow, compact page-size Select stayed 128px after keyboard selection, and the last fee column was reachable at max horizontal scroll. Native `base-select` enhancement was available in this browser; a separate unsupported-browser fallback was not exercised. Evidence: `.artifacts/test-runs/20261027T043649Z-ops-ux-browser-final3-11d018bb/visual-review/`.
