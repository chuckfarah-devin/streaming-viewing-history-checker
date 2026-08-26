# Version 1.1 — Step 14: UI/UX Specification and Design Proposal

**Status:** Proposed (not implemented)  
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
    ├── Captured review
    ├── OCR result
    └── Result / Ambiguous / NotWatched
```

The flow is correct. Version 1.1 improves **visual hierarchy, grouping, wording, and emotional polish** without changing the routes or matching logic.

---

## 2. Proposed Version 1.1 information architecture

```
Home
├── Scan TV (primary)
├── Search History
├── Recently watched
├── Netflix History (import + manage)
│   ├── Import Tier 1
│   ├── Import Tier 2
│   └── Reimport / Delete
├── Active Profile (always visible)
└── Settings

Camera flow:
Preview → Capture → Review → Match → History Result

Search flow:
Search → (Ambiguous) → History Result

Result:
History Result (movie or series) → Episode detail (series only)
```

No new screens are added except an optional **Settings** screen. Existing screens are redesigned.

---

## 3. Visual design system

### 3.1 Design principles

1. **Dark-first for TV rooms.** The default theme is dark; light is supported but secondary.
2. **Restrained color.** One cinematic accent, one warm neutral, semantic colors for states only.
3. **Readable at distance.** Large headlines, comfortable body type, clear separation.
4. **Honest data.** Never present an estimate as a fact (no percentages, no "completed").
5. **Touch-friendly.** Minimum 48dp tappable areas, large buttons, no tiny controls.

### 3.2 Color palette

| Role | Dark theme | Light theme | Usage |
|---|---|---|---|
| Background | `#0D0F12` | `#F7F5F2` | Screen background |
| Surface | `#161A1F` | `#FFFFFF` | Cards, sheets |
| Surface variant | `#1E2329` | `#F0EBE6` | Secondary cards, hover/press |
| Primary | `#F5B041` | `#D4891A` | Primary action, success, highlights |
| On primary | `#121212` | `#FFFFFF` | Text on primary buttons |
| Secondary | `#7D8C9B` | `#5A6A78` | Secondary actions, captions |
| Tertiary | `#C49A6C` | `#8F6A3E` | Accent highlights, profile avatars |
| Error | `#E57373` | `#C62828` | Errors, destructive actions |
| On error | `#121212` | `#FFFFFF` | Text on error containers |
| Warning | `#F5B041` | `#D4891A` | Warnings, needs attention |
| On surface | `#E6E1DB` | `#1C1B1F` | Main text on dark surfaces |
| On surface variant | `#9DA4AB` | `#5E5C5A` | Secondary text, metadata |

**Cinematic rationale:** The background is a very dark warm slate, not pure black, to reduce eye strain in dim rooms. The primary amber is distinct from Netflix red and remains visible at low screen brightness.

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
| `FilledButton` (Primary) | Main action: Scan TV, Search, View history |
| `TonalButton` (Surface tint) | Secondary: Import, Switch profile |
| `OutlinedButton` | Tertiary: Try again, Back, Delete data |
| `TextButton` | Inline: Learn more, manual correction |

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
| **Success** | Subtle checkmark + amber primary color; avoid confetti or celebratory animations |
| **Warning** | Amber `WarningAmber` icon + `Warning` container with `onWarning` text |
| **Error** | Red `ErrorOutline` icon + `errorContainer`; explicit next action |

### 3.9 Accessibility

- All buttons and cards have `contentDescription` or are `focusable`.
- Minimum touch target: **48dp × 48dp**.
- Body text contrast: minimum 4.5:1; large text: 3:1.
- `contentColorFor` used for all container colors.
- Support system font size and `TalkBack`.
- Respect `isLayoutDirectionRtl`.
- Haptic feedback for capture and successful match (configurable).

---

## 4. Screen-by-screen UX specification

### 4.1 First launch / onboarding

A one-time screen that explains the app's purpose.

- **Copy:**
  - Headline: "Have I watched this?"
  - Body: "Point your phone at a TV, scan the title, and search your imported Netflix viewing history."
  - CTA: "Get started" → Home.
- **Design:** Large headline, illustration or icon, single primary button. No Netflix logo.

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
|  Recently watched              |
|  ┌-----------------------------|
|  │ The Irishman    2021-03-17  |
|  │ Stranger Things 2022-07-04  |
|  └-----------------------------|
+--------------------------------+
```

- **Primary action:** `Scan TV Screen` is the hero button.
- **Profile chip:** top-left, always visible if multiple profiles are known. Tapping opens profile sheet.
- **Empty state:** when no records exist, show a friendly empty illustration and an `Import your Netflix history` button.

### 4.3 Netflix history import

A single entry point for import with two tier cards.

```
+--------------------------------+
|  Import Netflix history        |
+--------------------------------+
|  ┌-- Quick import (Tier 1) ----┐
|  │ Download from Netflix →      |
|  │ Account → Viewing Activity   |
|  │ [ Choose CSV ]               |
|  └------------------------------┘
|  ┌-- Full export (Tier 2) -----┐
|  │ Richer data, takes longer.   |
|  │ Download from Netflix →      |
|  │ Privacy → Download info      |
|  │ [ Choose ZIP or CSV ]        |
|  └------------------------------┘
+--------------------------------+
```

- **Idle:** both cards show a short description and an action button.
- **Loading:** a `LinearProgressIndicator` and text explaining the stage.
- **Success (Tier 1):** "X records imported." If rows were skipped, a non-blocking warning card.
- **Success (Tier 2):** "X records upgraded, Y records added." plus profile picker when needed.
- **Already imported:** informative card with `Try another file`.
- **Failure:** red error card with the message and `Try again`.

### 4.4 Profile selection and family switching

A bottom sheet or full-screen list:

- Each row shows a profile circle with initials, the profile name, and a checkmark when active.
- A row at the bottom: "All profiles" (aggregate search) if it makes sense.
- Selected profile immediately updates the active profile and is reflected on Home.

### 4.5 Camera capture

Full-screen preview with a stronger framing guide.

- **Preview:** dark overlay outside a rounded 16:9-ish frame; only the frame area is clear.
- **Instruction:** "Center the show or movie title in the frame".
- **Capture button:** large floating action button with a camera icon, 72dp touch target.
- **Captured review:** show the captured image with `Retake` and `Read text` buttons.

### 4.6 OCR processing and result

Replace the diagnostics-heavy `OcrResultView` with a consumer-facing result card.

```
+--------------------------------+
|  Recognized title              |
|                                |
|  The Watcher                   |  (headlineLarge)
|  Confident match               |  (label, amber)
|                                |
|  [   View history   ]          |  (filled)
+--------------------------------+
```

**States:**

- **Confident match:** show the matched title as a hero, a "Confident match" label, and a `View history` button. A small `Not the right title?` text link triggers ambiguity or manual search.
- **Ambiguous match:** "We found a few possibilities. Which one did you mean?" followed by a vertically stacked list of candidate cards. Each card shows title, record count, and content type as a subtle chip (Movie / Series). A `Search manually` text button at the bottom.
- **No match (OCR succeeded, no history):** "No title in your imported history matches closely enough." Explain this is not the same as "never watched." Buttons: `Try again`, `Search manually`, `Back`.
- **No text recognized:** "We couldn't read any text. Try a clearer photo." Buttons: `Try again`, `Back`.
- **Recognition error:** "Something went wrong reading the image." Buttons: `Try again`, `Back`.

Remove the visible diagnostics panel from the main flow. Move it to a hidden developer mode (Settings → Diagnostics) if needed.

### 4.7 Manual search

- A search bar with a clear icon and a `Search` action.
- If a recent search list is desired, add `Recent searches` below the bar (deferred; note as such).
- The result states mirror the OCR flow: Confident, Ambiguous, No match.

### 4.8 Confident known-title result

```
+--------------------------------+
|  < Back                        |
|                                |
|  The Irishman                  |  (headlineLarge)
|  In your imported Netflix      |
|  history                       |  (titleMedium, amber)
|                                |
|  Last viewed March 7, 2026     |
|  1 viewing record              |
+--------------------------------+
|  [Watched for 28m]             |  (if Tier 2)
|  [Reached 29m]                 |  (if Tier 2)
+--------------------------------+
```

- **Headline:** the matched title.
- **Status line:** `In your imported Netflix history` in amber.
- **Metadata:** `Last viewed [date]`; `X viewing record(s)`.
- **Tier 2 fields (if available):**
  - `Watched for X` (most recent session duration)
  - `Reached X` (latest bookmark of most recent session)
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
|  ▼ Season 1: Chapter One       |
|    2022-07-04                  |
|  ▲ Season 2: Chapter One       |
|    2022-07-05                  |
+--------------------------------+
```

- **Series insight card:** the three required values, shown as a group with labels.
- **Episode list:** collapsible by default? Proposed: expanded to show the most recent 3, with a `Show all` / `Show less` affordance. Each row shows `Season X: Episode title` and `view date`.
- **Repeated episode badge:** if a single episode has multiple records, show a small `×3` (e.g.) pill without implying completion.

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

- Avoid "You never watched this."
- Use the approved wording.

### 4.11 Ambiguous recognition

See 4.6.

### 4.12 Recognition failure and retry

See 4.6.

### 4.13 Settings

New screen (lightweight):

- Active profile (tap to switch)
- Import Netflix history (Tier 1 / Tier 2)
- Delete all imported history → confirmation dialog
- Network-assisted OCR (disabled / not implemented; greyed out)
- Diagnostics (hidden if not in dev mode; contains raw OCR text)
- About / data attribution

### 4.14 Empty states

| Context | Headline | Body | CTA |
|---|---|---|---|
| No history imported | "No history yet" | "Import your Netflix viewing history to get started." | `Import Netflix history` |
| No match | "No previous viewing found" | "This title was not found in your imported Netflix history." | `Try a different title` |
| No text | "No text recognized" | "Try taking another photo with the title clearly visible." | `Try again` |
| Error | "Something went wrong" | Technical message, short. | `Try again` |

### 4.15 Offline behavior

- All screens work offline after import.
- A `No network` banner appears only if a network-requiring feature (not present in v1.1) is invoked.
- Settings toggle for Google Vision is visible but disabled, with explanatory copy: "Google Cloud Vision fallback is not enabled in this version."

---

## 5. Result-screen copy guide

Use these exact phrases.

### Allowed wording

- `In your imported Netflix history`
- `No previous viewing found in your imported Netflix history`
- `Watched for 28m`
- `Reached 29m`
- `Last viewed March 7, 2026`
- `2 viewing records`
- `6 distinct episodes`
- `2 seasons represented`
- `Most recent session`
- `Episode details`

### Avoided wording

- `You never watched this`
- `Completed`
- `100% watched`
- `Finished`
- `Watched 100%`
- `Total time watched`

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
- `Watched for X` for the most recent session
- `Reached X` for the most recent session's latest bookmark
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
- Show `X viewing records` with a list of each session's date, `Watched for`, and `Reached`.
- Use a `×N` badge on the title/episode when there are repeated records.

For a series:
- The series summary shows total `viewing occurrences`.
- The episode list shows individual view dates and repeated-session counts.

---

## 7. Profile switching behavior

- The active profile is shown as a chip in the Home top bar and on the result screen.
- Tapping the chip opens a bottom sheet / full-screen list.
- Changing the profile immediately updates the active profile in `ProfileRepository`.
- Result screens and searches use the active profile filter.
- If a title is not found for the active profile, the app does **not** automatically suggest another profile; it states `No previous viewing found in your imported Netflix history`. A non-intrusive `Check a different profile?` text link may be offered in a future version.

---

## 8. Movie and series result layouts

### Movie result (wireframe)

```
+--------------------------------+
|  < Result                      |
|                                |
|  The Irishman                  |  headlineLarge
|  In your imported Netflix      |  titleMedium (amber)
|  history                       |
|                                |
|  Last viewed March 17, 2021    |
|  1 viewing record              |
|                                |
|  Watched for 2h 8m             |  Tier 2 only
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
|  17 distinct episodes watched  |
|  2 seasons represented         |
+--------------------------------+
|  Episodes watched              |
|  Season 1: Chapter One         |
|    2022-07-04                  |
|  Season 2: Chapter One         |
|    2022-07-05                  |
+--------------------------------+
```

---

## 9. Accessibility requirements

1. All interactive elements >= 48dp.
2. `contentDescription` on all icons and image content.
3. Dynamic font scaling support.
4. High contrast for body and labels.
5. No color-only information; pair color with text/icon.
6. `TalkBack` reads the result title and status together.
7. `BottomSheet` and `Dialog` use `ModalBottomSheet` / `AlertDialog` for focus trap.
8. `isScreenReaderFocusable` on result status cards.

---

## 10. Implementation sequence (subsequent v1.1 steps)

| Step | Scope | Notes |
|---|---|---|
| **Step 14.5** | Design tokens and theme | Implement `Color.kt`, `Type.kt`, `Shape.kt`; add dark/light theme; no screen changes. |
| **Step 15** | Home, import, profile, settings | Redesign Home and Import screens; add Settings screen; keep same ViewModels. |
| **Step 16** | Camera and OCR result | New `CameraScreen` overlays and `OcrResultView` consumer UI; move diagnostics to dev mode. |
| **Step 17** | Search and result screens | Redesign `SearchScreen`, `ResultScreen`, `AmbiguousScreen`; add episode list polish. |
| **Step 18** | Final pass, animations, accessibility | Animations, haptics, `contentDescription` sweep, contrast check, S21 QA. |

No matching, parsing, or import logic changes in any of these steps.

---

## 11. Restrictions preserved

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

---

## 12. Deliverable files

1. `specs/Version_1.1_Step14_UIUX_Proposal.md` (this file)
2. `specs/Version_1.1_Step14_Specification_Revisions.md` (proposed spec deltas, to be created)

This document is a design and planning deliverable only. No production code has been changed on the `version-1.1-step14` branch.
