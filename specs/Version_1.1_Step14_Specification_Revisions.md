# Version 1.1 — Step 14: Proposed Specification Revisions

**Status:** Proposed (for review)  
**Branch:** `version-1.1-step14`  
**Baseline:** `87ae82c` (Phase 1 stable)  

---

## Scope

These are the proposed changes to the approved Business Specification and Technical Specification to support the Version 1.1 UI/UX redesign. They are intentionally limited to presentation, wording, and guidance; no functional matching, parsing, import, or reconciliation logic is changed.

---

## 1. Business Specification — proposed changes

### 1.1 Document control

Proposed new revision row for `specs/01-business-specification.md`:

| Version | Date | Change Summary |
|---|---|---|
| 1.3 (proposed) | 2026-08-26 | UI/UX revision for Version 1.1: added result-screen language, Tier 1/Tier 2 display rules, profile prominence, and explicit prohibited wording. No functional business rules changed. |

### 1.2 Section 5 — Primary User Scenario

**Current wording (v1.2):**

> **Title:** The Irishman  
> **Status:** Previously watched  
> **Last watched:** March 17, 2021  
> **Viewing occurrences:** 1

**Proposed wording (v1.3):**

> **Title:** The Irishman  
> **Status:** In your imported Netflix history  
> **Last viewed:** March 17, 2021  
> **1 viewing record**

Rationale: the phrase `Previously watched` can overstate certainty; `In your imported Netflix history` matches the data boundary.

### 1.3 Section 5 — Series example

**Current wording (v1.2):**

> **Title:** Stranger Things  
> **Status:** Previously watched  
> **Last watched:** July 4, 2022  
> **Viewing occurrences:** 17  
> **Distinct episodes watched:** 17  
> **Seasons represented:** 2

**Proposed wording (v1.3):**

> **Title:** Stranger Things  
> **Status:** In your imported Netflix history  
> **Last viewed:** July 4, 2022  
> **17 viewing occurrences**  
> **17 distinct episodes**  
> **2 seasons represented**

Rationale: same data-boundary wording; removes the `watched` verb from the status line.

### 1.4 Section 6.1 — Import tiers

No change to import tiers or logic. Add a new **non-normative** note:

> *UI note (v1.1):* The import screen shall present Tier 1 and Tier 2 as two visually distinct cards with short, non-technical descriptions. The terms "Tier 1" and "Tier 2" may still appear as technical labels but should not be the primary user-facing wording.

### 1.5 BR-009 — Multiple Viewings

No semantic change. Add **presentation note**:

> The three series values may be grouped under a single "Series insight" heading in the UI, but each value must retain its own label and must never be conflated.

### 1.6 BR-010 — Viewing Progress

No change to the percentage-completion prohibition. Add **non-normative example**:

> Acceptable labels: `Watched for 28m`, `Reached 29m`, `Most recent session 28m`, `Latest bookmark 29m`.  
> Prohibited labels: `28% watched`, `Completed`, `Finished`, `Total time watched`.

### 1.7 BR-011 — No Historical Match

**Current wording:**

> Acceptable wording: "No previous viewing found in your imported Netflix history."

No change to the approved wording. Add UI note:

> The result screen shall include a brief explanation that this means the title was not found in the currently imported data, not that the user has never watched the title. A "Search manually" or "Scan again" action shall be visible.

### 1.8 New section 6.x — Profile prominence (v1.1)

Add a new business requirement:

> **BR-001d — Profile Prominence**
>
> When a Tier 2 export has been imported and multiple profiles are detected, the currently active profile must be visible on the Home screen and on history result screens. The user must be able to switch the active profile with no more than two taps from the Home screen.

This is a **presentation-only** clarification of BR-001b.

### 1.9 New section 6.x — Prohibited result wording (v1.1)

Add an explicit list to BR-011 or a new requirement:

> The application must not display the following absolute phrases unless the imported data genuinely proves the statement:
>
> - "You never watched this"
> - "Completed"
> - "100% watched"
> - "Finished"
>
> Use "No previous viewing found in your imported Netflix history" instead.

---

## 2. Technical Specification — proposed changes

### 2.1 Document control

Proposed new revision row for `specs/02-technical-specification.md`:

| Version | Date | Change Summary |
|---|---|---|
| 1.3 (proposed) | 2026-08-26 | UI/UX revision for Version 1.1: added theme, typography, spacing, and screen-state implementation notes. No data model, matching, or import changes. |

### 2.2 Section 1.2 — Technology Choices

No technology changes. Add a non-normative note:

> *UI note (v1.1):* The existing Jetpack Compose and Material 3 stack are sufficient for the redesign. The proposal adds an expanded `Color.kt`, `Type.kt`, and `Shape.kt` tokens file while keeping the same component library.

### 2.3 New section 7.x — Theme and color tokens

Add a new technical section:

> **7.1 UI Token System**
>
> The application shall define a token-based theme in `ui.theme`:
> - `Color.kt`: `Background`, `Surface`, `SurfaceVariant`, `Primary`, `OnPrimary`, `Secondary`, `Tertiary`, `Error`, `Warning`, and `OnXxx` variants for dark and light.
> - `Type.kt`: Material 3 type scale with the sizes defined in Version 1.1 UI proposal §3.3.
> - `Shape.kt`: `Card`, `Button`, `Chip`, `Sheet` corner radii.
>
> These tokens are for presentation only and must not change any behavior logic.

### 2.4 New section 7.x — Screen state specification

Add a technical mapping for each `*UiState` to the new UI:

> **7.2 Screen State Implementation**
>
> | State | Visual treatment |
> |---|---|
> | `Loading` | Circular / linear progress on a tonal scrim |
> | `Success` | Check icon + success text on `Primary` container |
> | `Warning` | Warning icon on `Warning` container |
> | `Error` | Error icon on `Error` container + explicit retry action |

### 2.5 Section 7 — Viewing Progress and Profiles

No data model or filtering changes. Add a note:

> *UI note (v1.1):* Tier 2 `duration_ms` and `latest_bookmark_ms` are presented as `Watched for X` and `Reached X` on a per-session basis, not summed or converted to percentages. The active profile is displayed via a `ProfileRepository` observer and must not trigger a new query on profile switch.

---

## 3. Unresolved business decisions

| Item | Status | Notes |
|---|---|---|
| **Minimum autoplayed duration threshold** | Unresolved | OI-03 remains open. The UI must not imply a threshold exists until BR-001a is finalized. |
| **Qualitative labels for duration** | Proposed, not approved | Labels such as `Brief activity` or `Substantial viewing` require explicit rules. Do not implement until approved. |
| **Profile auto-suggestion when title not found** | Deferred | A "Try another profile?" prompt is a v1.1+ idea, not in this scope. |
| **Network-assisted OCR consent copy** | Deferred | Google Vision is not in v1.1; consent wording is not needed now. |
| **App icon / brand illustration** | Deferred | Proposed illustration-free, icon-only. Brand assets are not in this step. |

---

## 4. Deferred ideas

| Idea | Deferred to |
|---|---|
| Google Cloud Vision fallback | Step 12 / future phase |
| Additional streaming providers | Post-Phase 1 |
| Percentage completion / progress bars | Never, unless runtime data source is approved |
| Runtime lookup from external catalog | Never, unless explicitly approved |
| Ambiguous candidate thumbnails | Post v1.1 |
| Search suggestions / recent searches | Post v1.1 |
| Episode artwork | Post v1.1 |

---

## 5. Proposed implementation sequence

| Step | Scope | Validation |
|---|---|---|
| **Step 14.5** | Theme tokens only | `Theme.kt` + preview tests; dark/light switch works; no UI regressions. |
| **Step 15** | Home, import, profile, settings | Screenshot tests; profile switching; import states. |
| **Step 16** | Camera and OCR result | Samsung capture test; no diagnostics in main flow; retry works. |
| **Step 17** | Search and result screens | Result wording matches spec; episode list expands; ambiguous selection. |
| **Step 18** | Final pass | Accessibility sweep, haptics, animation, S21 QA, 152+ tests green. |

Each step is a single bounded redesign. No data or matching logic is changed.

---

## 6. Traceability

All Phase 1 requirements remain unchanged. These revisions are additive and presentation-only. The original `01-business-specification.md` and `02-technical-specification.md` must not be overwritten; this file is the proposed v1.3 delta for review.
