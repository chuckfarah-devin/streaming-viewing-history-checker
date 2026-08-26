# Version 1.1 — Step 14: UI/UX Specification and Design Proposal

**Status:** Approved in direction, subject to corrections integrated  
**Branch:** `version-1.1-step14`  
**Baseline commit:** `87ae82c`  
**Target:** Android (Samsung S21-class), API 26–36, Jetpack Compose  

---

## 1. Audit of current Phase 1 UI

The Phase 1 application is functionally complete but visually and structurally an engineering prototype. The following issues are addressed by this proposal.

| Area | Current state | Usability impact |
|---|---|---|
| **Theme** | Basic Material 3 light/dark with a single blue primary and no supporting palette | No cinematic mood; looks like a stock template; lacks hierarchy on dark backgrounds |
| **Home** | Vertical button list with plain text labels; profile chip is small | Primary action is not visually dominant; scan vs search vs import compete equally |
| **Result screen** | Headline + repeated `LabeledValue` rows; content type printed as a label | Feels like a data dump; episode list is not visually connected to the series hero |
| **Camera** | Full-screen preview with a weak corner guide; post-capture shows a raw bitmap | No clear framing affordance; result is a diagnostic readout, not a consumer result |
| **OCR result** | `Diagnostics` card always visible; match score exposed; candidate list raw | Confuses users and developers; score values are meaningless to users |
| **Import** | Plain text and buttons; no visual tier distinction | Users do not understand the difference between Tier 1 and Tier 2 at a glance |
| **Profile switching** | Simple `ListItem` | Works, but active profile is not prominent enough outside the picker |
| **Ambiguity** | Card list with small text and `contentType` names | Looks technical; no clear guidance when recognition is uncertain |
| **Empty / error states** | Centered plain text and buttons | Not reassuring; does not guide next step clearly |
| **Typography/spacing** | Default Material type scale; inconsistent padding (24dp vs 16dp) | Does not feel finished or readable at TV-viewing distance |

### Information architecture (current)

```
Home
├── Import (Tier 1)
├── Import Full (Tier 2)
├── Search
├── Profile Select
└── Scan TV
    ├── Camera preview
    ├── Capture
    ├── Recognize
    └── Result / Ambiguous / NotWatched
```

The flow is correct. Version 1.1 improves **visual hierarchy, grouping, wording, and emotional polish** without changing the routes or matching logic.

---

## 2. Proposed Version 1.1 information architecture

```
Home
├── Scan TV (primary)
├── Search History
├── Netflix History (import + manage)
│   ├── Import Tier 1
│   ├── Import Tier 2
│   └── Reimport / Delete
├── Active Profile (always visible)
└── Settings

Camera flow:
Preview → Capture → recognize → History Result

Search flow:
Search → (Ambiguous) → History Result

Result:
History Result (movie or series) → Episode detail (series only)
```

No new screens are added except an optional **Settings** screen. Existing screens are redesigned. A confident camera match proceeds directly to the normal history result; no intermediate `View history` step is introduced.

---

## 3. Visual design system

### 3.1 Design principles

1. **Dark-first for TV rooms.** Default to the Android system theme; manual selector deferred if scope grows.
2. **Restrained color.** One cinematic primary, distinct semantic colors for success, warning, and error.
3. **Readable at distance.** Large headlines, comfortable body type, clear separation.
4. **Honest data.** Never present an estimate as a fact (no percentages, no "completed").
5. **Touch-friendly.** Minimum 48dp tappable areas, large buttons, no tiny controls.

### 3.2 Color palette

| Role | Dark theme | Light theme | Usage |
|---|---|---|---|
| Background | `#0D0F12` | `#F7F5F2` | Screen background |
| Surface | `#161A1F` | `#FFFFFF` | Cards, sheets |
| Surface variant | `#1E2329` | `#F0EBE6` | Secondary cards, hover/press |
| Primary | `#F5B041` | `#D4891A` | Primary actions and selected states |
| On primary | `#121212` | `#FFFFFF` | Text on primary buttons |
| Success | `#66BB6A` | `#2E7D32` | Confirmed history/result success |
| On success | `#121212` | `#FFFFFF` | Text on success elements |
| Success container | `#1B3C1E` | `#C8E6C9` | Success cards/pills |
| On success container | `#A5D6A7` | `#1B5E20` | Text on success containers |
| Warning | `#FBC02D` | `#F9A825` | Ambiguity or attention states |
| On warning | `#121212` | `#121212` | Text on warning elements |
| Warning container | `#3B2E00` | `#FFF9C4` | Warning cards/pills |
| On warning container | `#FFF176` | `#5D4037` | Text on warning containers |
| Error | `#E57373` | `#C62828` | Errors, destructive actions |
| On error | `#121212` | `#FFFFFF` | Text on error elements |
| Secondary | `#7D8C9B` | `#5A6A78` | Secondary actions, captions |
| Tertiary | `#C49A6C` | `#8F6A3E` | Accent highlights, profile avatars |
| On surface | `#E6E1DB` | `#1C1B1F` | Main text on dark surfaces |
| On surface variant | `#9DA4AB` | `#5E5C5A` | Secondary text, metadata |

**Cinematic rationale:** The background is a very dark warm slate, not pure black, to reduce eye strain in dim rooms. The primary amber is distinct from Netflix red. Success and warning have their own hues and container tones so they do not compete with primary buttons.

### 3.3 Typography

Use the default Material 3 type scale but with adjusted sizes for an entertainment app. Keep the same `MaterialTheme.typography` roles to avoid code churn.

| Role | Font size | Line height | Weight | Usage |
|---|---|---|---|---|
| `displayLarge` | 52sp | 60sp | 400 | Onboarding hero |
| `displayMedium` | 40sp | 48sp | 400 | Home brand title |
| `headlineLarge` | 32sp | 40sp | 500 | Result title |
| `headlineMedium` | 26sp | 32sp | 500 | Screen title |
| `headlineSmall` | 22sp | 28sp | 500 | Card title, empty headline |
| `titleLarge` | 20sp | 26sp | 500 | Section headers |
| `titleMedium` | 18sp | 24sp | 500 | Sub-section headers |
| `titleSmall` | 16sp | 22sp | 500 | Card subhead, badge text |
| `bodyLarge` | 16sp | 24sp | 400 | Primary body |
| `bodyMedium` | 14sp | 20sp | 400 | Secondary body, metadata |
| `bodySmall` | 12sp | 16sp | 400 | Captions, footer, hints |
| `labelLarge` | 14sp | 20sp | 500 | Buttons |
| `labelMedium` | 12sp | 16sp | 500 | Chips, small badges |

### 3.4 Spacing system

Base grid: **4dp**.

| Token | Value | Usage |
|---|---|---|
| `xs` | 4dp | Tight insets, icon padding |
| `sm` | 8dp | Between related items inside a card |
| `md` | 16dp | Standard card padding, screen body horizontal margin |
| `lg` | 24dp | Section separation, vertical list spacing |
| `xl` | 32dp | Hero to content separation |
| `xxl` | 48dp | Major screen sections |

Screen body: `horizontal = 16dp`, with `16dp` top and `24dp` bottom.

### 3.5 Shape and elevation

- Cards: `RoundedCornerShape(16dp)`
- Bottom sheets / dialog: `RoundedCornerShape(28dp)`
- Buttons: `RoundedCornerShape(12dp)`
- Chips: `RoundedCornerShape(8dp)`
- Elevation 0 (no shadows in dark theme) or `1dp` outline.

Use **tonal cards** and **color blocks** instead of shadows for hierarchy.

### 3.6 Buttons and chips

| Style | Use |
|---|---|
| `FilledButton` (Primary) | Main action: Scan TV, Search |
| `TonalButton` (Surface tint) | Secondary: Import, Switch profile |
| `OutlinedButton` | Tertiary: Try again, Back, Delete data |
| `TextButton` | Inline: Not the right title?, manual correction |

Minimum button height: **48dp**. Large primary buttons on Home: **56dp**.

### 3.7 Icons

Continue using Material 3 icons. Proposed iconography:

- `CameraAlt` or `Videocam` for scan
- `Search` for manual
- `History` for import
- `Person` / `AccountCircle` for profile
- `Settings` for settings
- `CheckCircle` for success
- `ErrorOutline` for error
- `WarningAmber` for warning
- `Replay` for retry
- `ExpandMore` / `ExpandLess` for episode list

### 3.8 Loading, success, warning, error states

| State | Visual treatment |
|---|---|
| **Loading** | Circular progress on a tinted scrim; inline skeletons not needed |
| **Success** | Green checkmark / `CheckCircle` on `Success` container; success text |
| **Warning** | `WarningAmber` icon on `Warning` container |
| **Error** | `ErrorOutline` icon on `Error` container; explicit next action |

### 3.9 Accessibility

- Minimum touch target: **48dp × 48dp** for all interactive elements.
- Use meaningful `contentDescription` for **actionable and informative** icons and images.
- Set `contentDescription = null` for decorative icons whose meaning is already conveyed by adjacent text or parent semantics.
- Merge child semantics where a card or button should be read as one coherent element.
- Dynamic font scaling support.
- Body text contrast: minimum 4.5:1; large text: 3:1.
- `contentColorFor` used for all container colors.
- Support system font size and `TalkBack`.
- Respect `isLayoutDirectionRtl`.
- Haptic feedback for capture and successful match (configurable).

---

## 4. Screen-by-screen UX specification

### 4.1 First launch / onboarding

A one-time screen that explains the app.

- **Copy:**
  - Headline: "Have I watched this?"
  - Body: "Point your phone at a TV, scan the title, and search your imported Netflix viewing history."
  - CTA: "Get started" → Home.
- **Design:** Large headline, simple icon, single primary button. No Netflix logo.

### 4.2 Home

```
+--------------------------------+
|  Streaming History             |  (displayMedium)
|  Have I watched this before?   |  (bodyLarge, muted)
+--------------------------------+
|  [Active profile chip]         |
+--------------------------------+
|  [         Scan TV Screen      |  (filled primary, 56dp)
|            (camera icon)       |
|                                |
+--------------------------------+
|  [  Search history  ]          |  (outlined)
+--------------------------------+
|  [  Import Netflix history  ]  |  (tonal)
+--------------------------------+
|  [  Settings  ]                |  (text)
+--------------------------------+
```

- **Primary action:** `Scan TV Screen` is the hero button.
- **Profile chip:** top-left, always visible if multiple profiles are known. Tapping opens the profile sheet.
- **Empty state:** when no records exist, show a friendly empty illustration and an `Import your Netflix history` button.
- **No "Recently watched" section in v1.1.** Recent-history list, sorting rules, and navigation are deferred to a separate approved feature.

### 4.3 Netflix history import

A single entry point for import with two tier cards.

```
+--------------------------------+
|  Import Netflix history        |
+--------------------------------+
|  ┌-- Quick import (Tier 1) ----┐
|  │ Download from Netflix →      |
|  │ Account → Viewing Activity   |
|  │ [ Choose CSV file ]          |
|  └------------------------------┘
|  ┌-- Full export (Tier 2) -----┐
|  │ Richer data, takes longer.   |
|  │ Download from Netflix →      |
|  │ Privacy → Download info      |
|  │ [ Choose CSV file ]          |
|  └------------------------------┘
+--------------------------------+
```

- **Idle:** both cards show a short description and an action button. The Tier 2 button accepts only the `ViewingActivity.csv` format actually supported by the current implementation; ZIP is not advertised in v1.1.
- **Loading:** a `LinearProgressIndicator` and text explaining the stage.
- **Success (Tier 1):** "X records imported." If rows were skipped, a non-blocking warning card.
- **Success (Tier 2):** "X records upgraded, Y records added." plus profile picker when needed.
- **Already imported:** informative card with `Try another file`.
- **Failure:** red error card with the message and `Try again`.

### 4.4 Profile selection and family switching

A bottom sheet or full-screen list:

- Each row shows a profile circle with initials, the profile name, and a checkmark when active.
- Selected profile immediately updates the active profile and is reflected on Home.
- **No "All profiles" option in v1.1.** The approved Tier 2 behavior uses one active profile.

### 4.5 Camera capture

Full-screen preview with a stronger framing guide.

- **Preview:** dark overlay outside a rounded 16:9-ish frame; only the frame area is clear.
- **Instruction:** "Center the show or movie title in the frame".
- **Capture button:** large floating action button with a camera icon, 72dp touch target.
- **Captured review:** show the captured image with `Retake` and `Read text` buttons.

### 4.6 OCR processing and result

The OCR result screen is a consumer-facing **intermediate state that resolves to a normal history result**. A confident match proceeds **directly** to `ResultScreen`.

**States:**

- **Confident match:** the matched title is shown with a short confirmation and an automatic `View history` transition. No user tap is required to proceed. Include a subtle `Not the right title?` text link for manual correction or ambiguity. The result screen also provides `Scan again` and `Search manually`.
- **Ambiguous match:** "We found a few possibilities. Which one did you mean?" followed by a vertically stacked list of candidate cards. Each card shows title, record count, and a `Series` chip **only** if the candidate is confidently classified as a series. `UNKNOWN` records show no content-type chip. A `Search manually` text button at the bottom.
- **Recognition uncertain (OCR succeeded, no confident match):** "We couldn't confidently identify the title." Explain this is a recognition problem, not a history result. Buttons: `Try again`, `Search manually`, `Back`.
- **No text recognized:** "We couldn't read the title. Try taking another photo." Buttons: `Try again`, `Back`.
- **Processing error:** "Something went wrong reading the image." Buttons: `Try again`, `Back`.

Remove the visible `Diagnostics` panel from the main flow. Move it to a hidden developer/debug build mode (Settings → Diagnostics) if needed.

### 4.7 Manual search

- A search bar with a clear icon and a `Search` action.
- Result states mirror the OCR flow: Confident, Ambiguous, No match.

### 4.8 Confident known-title result

```
+--------------------------------+
|  < Back                        |
|                                |
|  The Irishman                  |  (headlineLarge)
|  In your imported Netflix      |
|  history                       |  (titleMedium, success)
|                                |
|  Last viewed March 7, 2026     |
|  1 viewing record              |
+--------------------------------+
|  Most recent session: 28m      |  (if Tier 2)
|  Reached: 29m                  |  (if Tier 2)
+--------------------------------+
```

- **Headline:** the matched title.
- **Status line:** `In your imported Netflix history` in the success color.
- **Metadata:** `Last viewed [date]`; `X viewing record(s)`.
- **Tier 2 fields (if available):**
  - `Most recent session: X` (duration of the most recent session)
  - `Reached: X` (latest bookmark of the most recent session)
  - `Watched for X` is acceptable where context is clear, but prefer `Most recent session: X` where there is a risk of overstatement.
  - Repeated-viewing list if more than one record.
- **Movie layout:** no episode list.
- **Series layout:** see 4.9.

### 4.9 Series summary and episode list

```
+--------------------------------+
|  Stranger Things               |  (headlineLarge)
|  In your imported Netflix      |
|  history                       |
|                                |
|  Last viewed July 4, 2022      |
+--------------------------------+
|  ┌-- Series insight -----------┐
|  │ 17 viewing occurrences       |
|  │ 17 distinct episodes         |
|  │ 2 seasons represented        |
|  └------------------------------┘
+--------------------------------+
|  Episodes watched              |
|  Season 1: Chapter One         |
|    2022-07-04                  |
|  Season 2: Chapter One         |
|    2022-07-05                  |
|  [ Show all episodes ]         |
+--------------------------------+
```

- **Series insight card:** the three required values, shown as a group with labels.
- **Episode list (initial state):** show the three most recently viewed distinct episodes, newest first.
- **Episode list (expanded):** `Show all episodes` reveals remaining episodes, grouped by season in deterministic order (Season 1 ascending, then episodes in the order they appear in history; repeated view dates preserved within each episode).
- **Episode row:** `Season X: Episode title` and `view date`. A `×N` pill is shown when an episode has multiple records, without implying completion.
- **Repeated episodes:** preserve all viewing dates inside the episode detail; do not collapse repeated views into a single line unless the user explicitly expands for detail.

### 4.10 Confident title absent from history

```
+--------------------------------+
|  The Matrix                    |  (headlineLarge)
|  No previous viewing found     |  (headlineSmall)
|  in your imported Netflix      |
|  history                       |  (bodyMedium, muted)
+--------------------------------+
|  [  Search manually  ]         |
|  [  Scan again  ]              |
+--------------------------------+
```

- Use only the approved wording.
- The title must first be confidently identified before this state is shown.

### 4.11 Ambiguous recognition

See 4.6.

### 4.12 Recognition failure and retry

See 4.6.

### 4.13 Settings

New screen (lightweight):

- Active profile (tap to switch)
- Import Netflix history (Tier 1 / Tier 2)
- Delete all imported history → confirmation dialog
- Theme: `Follow system` / `Dark` / `Light` (manual selector only if Step 14.5 scope allows; otherwise `Follow system` only)
- Diagnostics (available only in a clearly separated developer/debug build mode; not shown in consumer builds)
- About / data attribution

**No Google Cloud Vision toggle in v1.1.** That feature remains deferred and must not appear as a disabled consumer-facing option.

### 4.14 Empty states

| Context | Headline | Body | CTA |
|---|---|---|---|
| No history imported | "No history yet" | "Import your Netflix viewing history to get started." | `Import Netflix history` |
| No match | "No previous viewing found" | "This title was not found in your imported Netflix history." | `Try a different title` |
| No text | "We couldn't read the title" | "Try taking another photo with the title clearly visible." | `Try again` |
| Recognition uncertain | "We couldn't confidently identify the title" | "Try a clearer photo or search manually." | `Try again` / `Search manually` |
| Error | "Something went wrong" | Technical message, short. | `Try again` |

### 4.15 Offline behavior

- All screens work offline after import.
- No network-requiring feature is exposed in v1.1, so no offline banner is necessary.

---

## 5. Result-screen copy guide

### Preferred wording

- `In your imported Netflix history`
- `No previous viewing found in your imported Netflix history`
- `Most recent session: 28m`
- `Watched for 28m`
- `Reached 29m`
- `Last viewed March 7, 2026`
- `2 viewing records`
- `6 distinct episodes`
- `2 seasons represented`
- `Episode details`

### Avoided wording

- `You never watched this`
- `Completed`
- `100% watched`
- `Finished`
- `Watched 100%`
- `Total time watched`
- Qualitative labels such as `Brief activity` or `Substantial viewing`

---

## 6. Tier 1 and Tier 2 presentation

### 6.1 Tier 1 (title + date only)

Show:
- Title
- `In your imported Netflix history` (if found)
- `Last viewed [date]`
- `X viewing record(s)`
- `All viewing dates` (collapsed if more than 4)

Do **not** show:
- Duration
- Bookmark
- `Reached`
- Profile name
- Device type

Do **not** label missing Tier 2 data as an error or "Unknown."

### 6.2 Tier 2 (full fields)

Show additional fields when present:
- Active profile name
- Most recent viewing date
- `Most recent session: X` (duration of most recent session)
- `Reached: X` (latest bookmark of most recent session)
- Repeated viewing records (count + list)
- Series statistics
- Episode details (expandable)

Show these as secondary, collapsed by default:
- Device type
- Autoplay status
- Raw attributes

### 6.3 Repeated-session semantics

For a movie or an individual episode:
- Do **not** sum durations.
- Show `X viewing records` with a list of each session's date, `Most recent session`, and `Reached`.
- Use a `×N` badge on the title/episode when there are repeated records.

For a series:
- The series summary shows total `viewing occurrences`.
- The episode list shows individual view dates and repeated-session counts.

---

## 7. Profile switching behavior

- The active profile is shown as a chip in the Home top bar and on history result screens.
- Tapping the chip opens a bottom sheet / full-screen list.
- Changing the active profile immediately updates the active profile in `ProfileRepository` and **reactively refreshes active search and result data using the newly selected profile**.
- Profile switching may issue a filtered database query or update a reactive Room/Flow query. It must not require reimporting or reparsing the Netflix file.
- Result screens and searches use the active profile filter.
- **No "All profiles" option in v1.1.** If a title is not found for the active profile, the app states `No previous viewing found in your imported Netflix history`.

---

## 8. Movie and series result layouts

### Movie result (wireframe)

```
+--------------------------------+
|  < Result                      |
|                                |
|  The Irishman                  |  headlineLarge
|  In your imported Netflix      |  titleMedium (success)
|  history                       |
|                                |
|  Last viewed March 17, 2021    |
|  1 viewing record              |
|                                |
|  Most recent session: 2h 8m    |  Tier 2 only
|  Reached 2h 9m                 |  Tier 2 only
+--------------------------------+
```

### Series result (wireframe)

```
+--------------------------------+
|  < Result                      |
|                                |
|  Stranger Things               |  headlineLarge
|  In your imported Netflix      |
|  history                       |
|                                |
|  Last viewed July 4, 2022      |
+--------------------------------+
|  Series insight                |
|  17 viewing occurrences        |
|  17 distinct episodes          |
|  2 seasons represented         |
+--------------------------------+
|  Episodes watched              |
|  Season 1: Chapter One         |
|    2022-07-04                  |
|  Season 2: Chapter One         |
|    2022-07-05                  |
|  [ Show all episodes ]         |
+--------------------------------+
```

---

## 9. Accessibility requirements

1. Minimum touch target **48dp × 48dp**.
2. Use `contentDescription` for **actionable and informative** icons and images; set `null` for decorative icons.
3. Merge child semantics where a card or button should be read as one coherent element.
4. Dynamic font scaling support.
5. High contrast for body and labels.
6. No color-only information; pair color with text/icon.
7. `TalkBack` reads the result title and status together.
8. `BottomSheet` and `Dialog` use `ModalBottomSheet` / `AlertDialog` for focus trap.
9. `isScreenReaderFocusable` on result status cards.

---

## 10. Theme behavior

- Default to the **Android system theme** (`isSystemInDarkTheme`).
- Settings may offer `Follow system`, `Dark`, and `Light`.
- If a manual selector materially expands Step 14.5 scope, retain `Follow system` for Version 1.1 and defer the manual selector.
- The visual design is dark-first: colors, contrast, and spacing are tuned for dim TV-viewing environments.

---

## 11. Implementation sequence (subsequent v1.1 steps)

| Step | Scope | Notes |
|---|---|---|
| **Step 14.5** | Specification integration and theme tokens | Apply approved spec revisions to canonical specs; implement `Color.kt`, `Type.kt`, `Shape.kt`; default to system theme; no screen changes. |
| **Step 15** | Home, import, profile, and Settings | Redesign Home and Import screens; add Settings screen; keep same ViewModels. |
| **Step 16** | Camera and OCR consumer flow | New `CameraScreen` overlays and `OcrResultView` consumer UI; confident match goes directly to `ResultScreen`; move diagnostics to dev mode. |
| **Step 17** | Manual search and history-result screens | Redesign `SearchScreen`, `ResultScreen`, `AmbiguousScreen`; episode list shows three most recent + `Show all episodes`, grouped by season. |
| **Step 18** | Final pass | Accessibility sweep, haptics, `contentDescription` check, contrast check, S21 QA. |

No matching, parsing, or import logic changes in any of these steps.

---

## 12. Restrictions preserved

This proposal does **not**:

- Change production matching behavior
- Change fuzzy-match thresholds
- Change import or reconciliation logic
- Implement Google Vision
- Add an external runtime/catalog API
- Add percentage completion
- Remove Tier 1 or Tier 2 support
- Add another streaming provider
- Use Netflix-trademarked branding
- Refactor unrelated architecture
- Display UNKNOWN content as a `Movie` chip
- Add an `All profiles` aggregate option

---

## 13. Deliverable files

1. `specs/Version_1.1_Step14_UIUX_Proposal.md` (this file)
2. `specs/Version_1.1_Step14_Specification_Revisions.md`

The proposed revisions will also be recorded as v1.3 revision entries in the canonical Business and Technical Specifications once the corrections are approved. This document is a design and planning deliverable only. No production code has been changed on the `version-1.1-step14` branch.
