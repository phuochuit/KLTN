# Artifact execution contract

- Reference: `D:\Code\KLTN\Mau De cuong chi tiet KLCN_Huong ung dung_04_07_2026.docx`
- Reference SHA-256: `c1b157aacc738281beb45f0d7e04ef02d472c340c926933d679688b2b70ed745`
- Output: `D:\Code\KhoaLuan\De_cuong_chi_tiet_KLCN_Quan_ly_bai_do_xe_15_20_TRANG.docx`
- Preservation rule: keep the reference file unchanged; preserve its section settings, page size, margins, styles, fonts, headers, page fields, images, rubric table, signatures, and existing layout primitives. The footer page-number paragraph may be shifted slightly left only if the expanded document reaches two-digit page counts and the original position clips the field.
- Editable slots: topic name; objectives; survey scope; project plan and three-person assignment; system analysis; system design; API; eight detailed business workflows; implementation environment; testing/deployment; twelve-week schedule; references. The requirements slot may be expanded with cloned real-list paragraphs from the template to reach 15–20 readable pages.
- Intentionally blank slots: topic code, supervisor details, student details, execution dates, and signatures, because the user did not provide them.
- Method: clone the package; replace mapped text slots and insert cloned level-1 requirement and level-2 detail paragraphs before the environment section in `word/document.xml`; add separate labeled paragraphs within weekly schedule cells; preserve all non-edited package parts.
- Verification: source hash unchanged; output package opens; section/header/style/media parts preserved; footer field codes preserved and fully visible; rendered page count is 15–20; requirements follow the rubric sequence; no dense wall paragraphs; every workflow separately states purpose, actor, inputs, preconditions, numbered steps, exceptions, stored data, output and acceptance criteria; schedule filled for weeks 01–12 with work, deliverable and owner.
