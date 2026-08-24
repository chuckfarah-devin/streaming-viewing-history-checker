# Technical Specification
## Streaming Viewing History Checker
### Phase 1 — Netflix / Android

**Document Status:** v1.2 — APPROVED TECHNICAL BASELINE  
**Controls:** Business Specification v1.2 — APPROVED BUSINESS BASELINE  
**Development Method:** Specification-Driven Development (SDD)  
**Prepared:** 2026-08-23

---

## Revision History

| Version | Date | Change Summary |
|---|---|---|
| 1.0 | 2026-08-23 | Initial proposed Technical Specification for Phase 1 |
| 1.1 | 2026-08-23 | (1) Replaced dedup-key with separate session-key and file-fingerprint concepts; revised idempotency and reconciliation to preserve same-day repeat viewings. (2) Added UNKNOWN content type; parser no longer defaults to MOVIE for unrecognized titles. (3) Updated targetSdk to API 36 / Android 16. (4) Hardened Vision API key guidance. (5) Fixed short-title matching rule and removed contradicting example. (6) Consistency pass: data model, pseudocode, test cases, terminology. |
| 1.2 | 2026-08-23 | Fixed Tier 1 ↔ Tier 2 date reconciliation: reconciliation now uses a ±1-day UTC/local-time window as a fallback when no exact date match exists, preventing UTC midnight boundary differences from manufacturing false additional viewing occurrences. Updated reconciliation pseudocode, same-day repeat example (viewingOccurrences = 2, not 3), Tier-1-after-Tier-2 counting logic, integration tests, and explanatory text. |

---

## Contents

1. [Architecture](#1-architecture)
2. [Normalized Viewing-History Data Model](#2-normalized-viewing-history-data-model)
3. [Netflix Import](#3-netflix-import)
4. [Title and Series Matching](#4-title-and-series-matching)
5. [Camera and OCR](#5-camera-and-ocr)
6. [Optional Network Recognition](#6-optional-network-recognition)
7. [Viewing Progress and Profiles](#7-viewing-progress-and-profiles)
8. [Open Business Decisions](#8-open-business-decisions)
9. [Testing Approach](#9-testing-approach)
10. [Proposed Implementation Sequence](#10-proposed-implementation-sequence)

---

# 1. Architecture

## 1.1 Summary

Phase 1 is a single-user, local-first Android application. All personal data remains on the device. The architecture is deliberately kept simple: standard Jetpack components, a single local database, and a small set of clearly bounded responsibilities.

## 1.2 Technology Choices

| Concern | Choice | Rationale |
|---|---|---|
| Language | Kotlin | Standard modern Android language |
| Minimum SDK | API 26 (Android 8.0) | Covers all modern Samsung phones; supports all required APIs |
| Target SDK | API 36 (Android 16) | Current Android target; starts the project on the latest release rather than requiring an immediate update after implementation begins |
| UI | Jetpack Compose | Declarative UI; avoids XML fragment complexity for a small app |
| Architecture pattern | MVVM + Repository | Simple, well-understood, testable; appropriate for this scale |
| Dependency injection | Hilt | Lightweight; improves testability without boilerplate; pairs naturally with Compose and ViewModel |
| Local persistence | Room (SQLite) | Jetpack ORM; supports FTS4 for full-text search; well-tested on Android |
| File import | Android Storage Access Framework (SAF) | System file picker; no additional permissions required |
| Camera | CameraX | Jetpack camera abstraction; handles Samsung-specific hardware differences |
| On-device OCR | ML Kit Text Recognition V2 (bundled) | Works offline; ~4 MB bundled model; real-time capable for Latin script |
| Network OCR fallback | Google Cloud Vision API (REST) | Higher accuracy for stylized or partially obscured text |
| Fuzzy matching | fuzzywuzzy-kotlin | Kotlin port of well-tested Python library; Levenshtein and token-sort algorithms |
| Threading | Kotlin Coroutines + Flow | Standard Android async model; integrates naturally with Room and ViewModel |
| Build system | Gradle with version catalog | Reproducible dependency management |

## 1.3 Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Jetpack Compose UI                       │
│  HomeScreen │ ImportScreen │ CameraScreen │ SearchScreen │   │
│                         ResultScreen                         │
└──────────────────────┬──────────────────────────────────────┘
                       │ observes StateFlow
┌──────────────────────▼──────────────────────────────────────┐
│                       ViewModels                             │
│   ImportViewModel │ LookupViewModel │ SearchViewModel        │
└──┬───────────────────┬────────────────────┬─────────────────┘
   │                   │                    │
   ▼                   ▼                    ▼
┌──────────┐   ┌──────────────┐   ┌───────────────────────┐
│  Import  │   │   Camera /   │   │   Viewing History     │
│  Module  │   │   OCR        │   │   Repository          │
│          │   │   Module     │   │                       │
│ Tier1    │   │              │   │ TitleMatcher          │
│ Parser   │   │ TextRecognizer│  │ SeriesParser          │
│ Tier2    │   │ (interface)  │   │ TitleNormalizer       │
│ Parser   │   │   │      │   │   └───────────────────────┘
│ Reconcil-│   │   ▼      ▼   │               │
│ er       │   │ MlKit  Vision│         ┌─────▼──────┐
└────┬─────┘   │ Impl   Impl  │         │    Room    │
     │         └──────────────┘         │  Database  │
     │                                  │  + FTS4    │
     └──────────────────────────────────►            │
                                        └────────────┘
```

## 1.4 Key Design Rules

- **OCR isolation:** `TextRecognizer` is an interface. ML Kit and Vision API are separate implementations. Title matching and history lookup have no direct dependency on OCR provider details.
- **Netflix isolation:** Netflix-specific CSV parsing is confined to the import module. The rest of the application operates on the normalized data model, not on Netflix's raw field names.
- **Provider field:** All data model records carry a `provider` field (`"Netflix"` in Phase 1). A future provider's importer would produce the same normalized records without changing the matching or storage layers.
- **Local first:** No network call occurs except the explicitly optional Vision API fallback. No viewing-history data ever leaves the device.

---

# 2. Normalized Viewing-History Data Model

## 2.1 Primary Table: `viewing_records`

Each row represents one viewing session from the imported Netflix history.

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `id` | INTEGER | NO | Primary key, auto-generated |
| `provider` | TEXT | NO | `"Netflix"` in Phase 1; reserved for future providers |
| `raw_title` | TEXT | NO | Original title string from Netflix export, always preserved |
| `display_title` | TEXT | NO | Title as shown to user (series name for SERIES; raw title for MOVIE or UNKNOWN) |
| `normalized_title` | TEXT | NO | Lowercased, punctuation-normalized, for matching |
| `content_type` | TEXT | NO | `SERIES`, `MOVIE`, or `UNKNOWN`; see §4.2 |
| `series_name` | TEXT | YES | Series name extracted from episode title; null when content_type ≠ SERIES |
| `normalized_series_name` | TEXT | YES | Normalized form of series name; null when content_type ≠ SERIES |
| `season_label` | TEXT | YES | Raw season label: `"Season 1"`, `"Part 2"`, etc.; null for non-SERIES |
| `season_number` | INTEGER | YES | Extracted integer season number; null if not determinable |
| `episode_title` | TEXT | YES | Episode title; null for non-SERIES records |
| `view_date` | TEXT | NO | ISO 8601 date `YYYY-MM-DD`; from Tier 1 Date or extracted from Tier 2 start time |
| `start_time_utc` | TEXT | YES | Full UTC timestamp `YYYY-MM-DD HH:MM:SS`; Tier 2 only |
| `duration_ms` | INTEGER | YES | Session duration in milliseconds; Tier 2 only |
| `bookmark_ms` | INTEGER | YES | Last stop position in ms (Bookmark field); Tier 2 only |
| `latest_bookmark_ms` | INTEGER | YES | Furthest position in ms (Latest Bookmark field); Tier 2 only |
| `profile_name` | TEXT | YES | Netflix profile name; Tier 2 only |
| `is_hidden` | INTEGER | NO | `0` or `1`; `1` if Netflix export flags "View was hidden" |
| `is_autoplayed` | INTEGER | NO | `0` or `1`; `1` if autoplayed with no user interaction (Tier 2 only; always `0` for Tier 1) |
| `attributes_raw` | TEXT | YES | Unparsed Netflix Attributes field; Tier 2 only |
| `device_type` | TEXT | YES | Device type from Tier 2 export; informational only |
| `source_tier` | INTEGER | NO | `1` or `2`; records which import format provided this row's data |
| `import_id` | INTEGER | NO | References `import_batches.id` of the batch that created this row |
| `session_key` | TEXT | NO | Stable unique identifier for this viewing session; see §3.4 |

## 2.2 Supporting Table: `import_batches`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | Primary key, auto-generated |
| `imported_at` | TEXT | ISO 8601 timestamp of import |
| `source_tier` | INTEGER | `1` or `2` |
| `source_file_name` | TEXT | File name of imported file (not path) |
| `file_fingerprint` | TEXT | SHA-256 hash of the file's full content; used to detect re-import of the same file |
| `record_count` | INTEGER | Rows imported in this batch |

## 2.3 Full-Text Search Table: `viewing_records_fts` (FTS4)

A Room `@Fts4` virtual table backed by `viewing_records`. Indexes `normalized_title` and `normalized_series_name` for fast prefix and token matching.

Queries against FTS4 use Room's `MATCH` syntax, returning matching `id` values that are then joined to `viewing_records`.

## 2.4 Design Notes

- **content_type:** Three possible values: `SERIES` (confidently parsed series record), `MOVIE` (reserved; not assigned during Phase 1 import — see §4.2), `UNKNOWN` (title that does not match any series pattern; the default for non-SERIES records). The MOVIE value exists for potential future use when a reliable movie-identification signal is available.
- **session_key vs. import_id:** `session_key` identifies the viewing session itself and is stable across re-imports of the same source data. `import_id` records which batch created this row and changes if a row is re-inserted. Do not conflate them.
- **Tier 1 compatibility:** All Tier 2-specific columns (`start_time_utc`, `duration_ms`, `bookmark_ms`, `latest_bookmark_ms`, `profile_name`, `is_autoplayed`, `attributes_raw`, `device_type`) are nullable. A Tier 1 import simply leaves them null.
- **Hidden records:** `is_hidden = 1` records are included in all lookups. The field is preserved for metadata purposes only and does not filter results (BR-001c).
- **Provider extensibility:** Adding a future provider requires only a new parser that produces rows conforming to this schema. The matching and display layers are unchanged.
- **Schema migration:** Room's `Migration` API will be used for any schema changes after initial deployment. Migration paths must be defined before any schema change is shipped.

---

# 3. Netflix Import

## 3.1 Tier 1 — Simple Viewing Activity CSV

**Obtaining the file:** Netflix website → Account → Viewing Activity → Download All  
**Delivered as:** A CSV file (typically named `NetflixViewingHistory.csv`)

**Expected header:**
```
Title,Date
```

**Expected date format:** `M/D/YYYY` (e.g., `3/17/2021`). This format has been observed in US-locale exports. It may vary by Netflix account region. If the date column cannot be parsed with the expected format, the import must attempt ISO 8601 (`YYYY-MM-DD`) as a fallback before rejecting the file.

**Row example:**
```
"The Irishman","3/17/2021"
"Stranger Things: Season 1: Chapter One: The Vanishing of Will Byers","7/4/2022"
```

**Parsing rules:**
1. Detect and skip the header row.
2. Each non-blank row must have exactly 2 fields after CSV unquoting.
3. The `Title` field must not be blank.
4. The `Date` field must parse to a valid date.
5. Rows that fail either check are skipped and counted as parse errors; they do not abort the import.
6. After import, the summary reports: records imported, records skipped.

**Validation:** If the header row does not match `Title,Date` (case-insensitive), reject the file with a user-readable error before processing any rows.

**Tier 1 limitations to communicate to user:**
- No session duration
- No stop position
- No profile information
- No autoplayed filtering (all records included)
- Multiple same-day viewings of the same title are preserved as imported but cannot be independently verified from Tier 1 data alone

## 3.2 Tier 2 — Netflix Full Data Export

**Obtaining the file:** Netflix website → Account → Privacy → Download your personal information  
**Delivered as:** A ZIP archive containing multiple folders and files  
**Required file:** `CONTENT_INTERACTION/ViewingActivity.csv`

**Supported import input:** The user may select either the raw `ViewingActivity.csv` file, or the ZIP archive. If a ZIP is selected, the app will locate `CONTENT_INTERACTION/ViewingActivity.csv` within it.

**Expected header (column order may vary; columns identified by name):**
```
Profile Name, Start Time, Duration, Attributes, Title, Supplemental Video Type,
Device Type, Bookmark, Latest Bookmark, Country
```

> **Validation dependency:** The exact Tier 2 column names and format should be confirmed against a representative real Netflix export before finalizing the Tier 2 parser. The field names listed above are drawn from public Netflix data-export documentation and user-shared analyses, but Netflix may change them without notice. If the parser cannot locate expected columns by name, it must reject the file with a clear error rather than silently importing malformed data.

**Field mapping to data model:**

| Netflix Field | Data Model Column | Notes |
|---|---|---|
| Profile Name | `profile_name` | Preserved as-is |
| Start Time | `start_time_utc`, `view_date` | Stored as UTC string; `view_date` extracted as date portion |
| Duration | `duration_ms` | Parse `H:MM:SS` → milliseconds |
| Attributes | `attributes_raw`, `is_autoplayed`, `is_hidden` | Scan for "user action: None" → `is_autoplayed=1`; scan for "View was hidden" → `is_hidden=1` |
| Title | `raw_title`, then parsed for content type, series, season, episode | |
| Supplemental Video Type | — | Non-blank value → skip row (trailer, clip, etc.) |
| Device Type | `device_type` | Stored; informational only |
| Bookmark | `bookmark_ms` | Parse `H:MM:SS` → milliseconds; null if blank/missing |
| Latest Bookmark | `latest_bookmark_ms` | Parse `H:MM:SS` → milliseconds; null if blank or `"Not latest view"` |
| Country | — | Not stored in Phase 1 |

**Duration and Bookmark parsing:** Format is `H:MM:SS` or `HH:MM:SS`. Convert to milliseconds: `(hours × 3,600,000) + (minutes × 60,000) + (seconds × 1,000)`. A blank or unparseable value stores `null` rather than causing an import failure.

## 3.3 Import Validation and Failure Behavior

For both tiers:
- If the file header does not match expected columns → **reject the entire file**; do not import any rows; display a specific error message.
- If individual rows are malformed → **skip those rows**; continue with remaining rows; report the count of skipped rows in the import summary.
- If the import fails mid-way (e.g., crash, storage error) → **leave existing database unchanged**. The import is committed atomically using a database transaction: new records are staged and committed only on success.

## 3.4 Session Keys and File Fingerprints

Two distinct concepts govern how records are uniquely identified and how repeated imports are detected. These concepts must not be conflated.

---

### Session Key

A `session_key` is a stable, deterministic identifier for one viewing session from the source data. It is stored in `viewing_records.session_key` and is used to recognize the same session if the source file is imported again.

**Tier 2 — per-record session key:**

```
session_key = SHA-256(
  provider + "|" +
  (profile_name ?: "") + "|" +
  start_time_utc + "|" +
  raw_title
)
```

The UTC start timestamp is precise enough to distinguish multiple viewings of the same title on the same day; two sessions will have different `start_time_utc` values and therefore different session keys. Re-importing the same Tier 2 export produces identical session keys.

**Tier 1 — file-position session key:**

```
file_fingerprint = SHA-256(entire file content as bytes)
session_key      = SHA-256(file_fingerprint + "|" + str(row_index))
```

`row_index` is the 0-based position of the data row in the file (after the header). Tier 1 contains only Title and Date. There is no timestamp or other field that can distinguish two genuine viewings of the same title on the same date. The session key incorporates the file fingerprint and row position rather than record content. This approach:

- Makes every row in a given file uniquely identifiable;
- Preserves same-day repeat records as distinct rows — row 0 and row 1 have different session keys even when Title and Date are identical;
- Is reproducible — re-importing the same file produces the same session keys.

**Acknowledged limitation:** Because the Tier 1 session key is position-based rather than content-based, it cannot be used to match Tier 1 records to Tier 2 records by session identity. Tier 1 ↔ Tier 2 reconciliation uses normalized title and date as a matching signal instead (see §3.6).

---

### File Fingerprint

`file_fingerprint = SHA-256(entire file content as bytes)` is stored in `import_batches.file_fingerprint`. It is used only to detect whether the exact same file has been imported before. It is not used as a record identifier.

## 3.5 Import Idempotency

Importing the same source data more than once must not create duplicate records. The mechanism differs by tier.

**Tier 2 — record-level idempotency:**

Before inserting each Tier 2 record, look up its `session_key` in `viewing_records`.
- If found with `source_tier = 2`: already imported at this tier — skip.
- If found with `source_tier = 1`: a Tier 1 record for this session exists — upgrade it (reconciliation path; see §3.6).
- If not found: insert as new record.

This makes Tier 2 import idempotent at the individual-record level.

**Tier 1 — file-level idempotency:**

Before processing any rows, compute `file_fingerprint` and check `import_batches` for an existing batch with that fingerprint.
- If found: file already imported — inform the user and skip the entire import.
- If not found: proceed with import.

This makes Tier 1 re-import of the same file a no-op.

**Tier 1 updated file (new history downloaded from Netflix):**

An updated Tier 1 export has a different fingerprint from any prior batch. The recommended handling:

1. Delete all existing `viewing_records` rows where `source_tier = 1`. Rows with `source_tier = 2` are never touched.
2. Create a new import batch with the new fingerprint.
3. Import all rows from the new file using freshly computed session keys.

This replaces outdated Tier 1 data with the current export and avoids cumulative row growth across repeated full-history downloads. Tier 2 records from any prior reconciliation are unaffected.

When inserting new Tier 1 rows after a Tier 2 import exists: for each new Tier 1 row, count existing `source_tier = 2` records with the same `normalized_title` **and `view_date` within the ±1-day window defined in §3.6** (the Tier 1 date, one day before, and one day after). Insert the new Tier 1 row only if it would add a viewing occurrence not already represented by those Tier 2 records. If the count of Tier 2 records within that title and date window already equals or exceeds the count of Tier 1 rows for the same title and date window in the new file, all of those viewings are considered accounted for and no new rows are needed.

## 3.6 Tier 1 → Tier 2 Reconciliation

When a Tier 2 export is imported after a Tier 1 import, matching Tier 1 records are upgraded with the richer Tier 2 data. No duplicate records are created.

### Date-matching and the ±1-day window

Tier 1 records store a local-calendar date (the date the user's Netflix account recorded the viewing, in the user's time zone). Tier 2 records store a UTC start timestamp, from which only the UTC calendar date can be extracted. For users in negative UTC offsets (the Americas), an evening viewing may start on local date D but UTC date D+1; the two date strings will not match exactly.

Matching on exact UTC date therefore introduces a systematic error: sessions near UTC midnight produce a false new Tier 2 record instead of upgrading the corresponding Tier 1 record, inflating `viewingOccurrences` by one per affected session.

**The ±1-day window** addresses this: when no Tier 1 candidate exists for the Tier 2 session's exact UTC date, the algorithm widens the search to include Tier 1 records dated one calendar day before or after. Exact-date matches are always preferred; adjacent-date matches are a fallback of last resort.

### Reconciliation algorithm

```
For each Tier 2 record T2:

  t2_date = date portion of T2.start_time_utc  // YYYY-MM-DD

  // 1. Skip if this exact Tier 2 session is already stored
  if session_key(T2) exists in viewing_records:
    skip
    continue

  // 2. Try exact date match first
  exact_candidates = SELECT * FROM viewing_records
                     WHERE source_tier     = 1
                       AND normalized_title = T2.normalized_title
                       AND view_date        = t2_date
                     ORDER BY id ASC
                     // excluding rows already matched in this import run

  // 3. Fall back to ±1-day window only when exact match produces no candidates
  if exact_candidates is not empty:
    candidates = exact_candidates
  else:
    candidates = SELECT * FROM viewing_records
                 WHERE source_tier     = 1
                   AND normalized_title = T2.normalized_title
                   AND view_date IN (t2_date − 1 day, t2_date + 1 day)
                 ORDER BY id ASC
                 // excluding rows already matched in this import run

  // 4. Upgrade or insert
  if candidates is not empty:
    target = candidates.first()
    UPDATE target:
      set start_time_utc, duration_ms, bookmark_ms, latest_bookmark_ms,
          profile_name, is_autoplayed, is_hidden, attributes_raw, device_type,
          source_tier = 2,
          session_key = session_key(T2)
    mark target as matched for the remainder of this import run
  else:
    INSERT T2 as a new viewing_records row
```

By taking the first unmatched Tier 1 candidate in insertion order, multiple same-day viewings of the same title are reconciled one-to-one without creating duplicates.

**Never downgrade:** A record's `source_tier` is never decremented. A row at `source_tier = 2` is never overwritten by a Tier 1 import.

### Acknowledged limitations of the ±1-day window

The adjacent-date fallback assumes that an unmatched Tier 2 session on date D±1, combined with an unmatched Tier 1 record on date D, represents the same viewing session. This assumption is correct for the common UTC-midnight-boundary case but is occasionally wrong:

> **False-merge risk:** If a user genuinely watched the same title on two consecutive calendar days — once on date D (present in Tier 1) and once on date D+1 (present in Tier 2 starting at an hour whose UTC date equals D+1) — and the Tier 1 export contains only the date-D record, the algorithm will merge them into a single record.

This risk is limited to situations where the Tier 1 export is missing the date-D+1 record. When both dates are present in Tier 1, exact matching handles each independently. The consequence of a false merge is that `viewingOccurrences` is understated by one — which is preferable to the UTC-boundary alternative of `viewingOccurrences` being overstated by one.

---

### Concrete example: same-day repeat viewings across UTC midnight

*Tier 1 file imported (file_fingerprint = "fp_abc"):*

| row_index | Title | Date |
|---|---|---|
| 0 | The Irishman | 3/17/2021 |
| 1 | The Irishman | 3/17/2021 |

Two records stored:

| id | raw_title | view_date | source_tier | session_key |
|---|---|---|---|---|
| 1 | The Irishman | 2021-03-17 | 1 | SHA-256("fp_abc\|0") |
| 2 | The Irishman | 2021-03-17 | 1 | SHA-256("fp_abc\|1") |

`viewingOccurrences = 2`

*Tier 2 import arrives with two sessions:*

| Start Time (UTC) | Extracted UTC date | Title |
|---|---|---|
| 2021-03-17 22:00:00 | 2021-03-17 | The Irishman |
| 2021-03-18 01:30:00 | 2021-03-18 | The Irishman |

The second session started at 01:30 UTC on March 18, which is 9:30 PM ET on March 17 — the same local evening as the first session. Its UTC date (2021-03-18) does not exactly match the Tier 1 date (2021-03-17).

*Reconciliation:*

**Session A** (t2_date = 2021-03-17):
- Exact candidates: id=1 and id=2 (both view_date = 2021-03-17, both unmatched)
- Take id=1 → upgraded to Tier 2; mark matched

**Session B** (t2_date = 2021-03-18):
- Exact candidates: none (no Tier 1 record with view_date = 2021-03-18)
- Adjacent candidates (±1 day): view_date in {2021-03-17, 2021-03-19} → id=2 (view_date = 2021-03-17, still unmatched)
- Take id=2 → upgraded to Tier 2; mark matched

*Final state:*

| id | view_date | source_tier | start_time_utc |
|---|---|---|---|
| 1 | 2021-03-17 | 2 | 2021-03-17 22:00:00 |
| 2 | 2021-03-17 | 2 | 2021-03-18 01:30:00 |

**`viewingOccurrences = 2`** ✓

Both original Tier 1 records are accounted for. No false additional occurrence is created from the UTC midnight boundary. No Tier 1 record is left unmatched.

---

# 4. Title and Series Matching

## 4.1 Normalization Pipeline

All titles — whether from the database or from OCR/manual input — pass through the same normalization function before comparison. Normalization is deterministic and reversible to display form.

```
normalize(input: String): String
  1. Unicode NFC normalization
  2. Lowercase
  3. Strip leading/trailing whitespace
  4. Collapse runs of internal whitespace to single space
  5. Convert curly apostrophes/quotes → straight apostrophe/quote
  6. Convert em-dash and en-dash → hyphen-minus (-)
  7. Remove diacritics: decompose Unicode, then strip combining characters
     (e.g., é → e, ñ → n)
  8. Remove control characters
  9. Result: a clean ASCII-range-comparable string
```

Normalization does **not** remove punctuation entirely, remove leading articles ("the", "a", "an"), or strip subtitles. Removing articles risks collapsing "The Crown" and "Crown" into the same key; stripping subtitles risks losing discriminating information.

## 4.2 Series/Episode Parsing and Content Type

Netflix encodes episode records as: `Series Name: Season N: Episode Title`

The parser decomposes raw titles into series, season, and episode components, and assigns one of three content types.

**Content types:**

| Value | Meaning |
|---|---|
| `SERIES` | A recognized series/episode pattern was found; series name, season, and episode are extracted |
| `UNKNOWN` | No recognized series pattern was found; could be a movie, documentary, limited series, special, or any other content that does not use standard Netflix season/part indicators |
| `MOVIE` | Reserved; not assigned by the Phase 1 parser. May be used in future when a reliable explicit movie signal is available |

**Why UNKNOWN instead of MOVIE as the default:** The absence of a series pattern in a Netflix title string is not evidence that the title is a movie. It may be a movie, a standalone special, a limited series with an atypical format, or a documentary. Asserting MOVIE when the information is absent would create false certainty. UNKNOWN records are matched and displayed like MOVIE records (title lookup only; no series breakdown), but do not carry an incorrect classification.

**Recognized series patterns (applied in order):**

| Pattern to detect | Example |
|---|---|
| `: Season N:` | `Stranger Things: Season 1: Chapter One` |
| `: Part N:` | `Ozark: Part 1: ...` |
| `: Volume N:` | `Cobra Kai: Volume 1: ...` |
| `: Chapter N:` | (used by some limited series) |
| `: Book N:` | `Avatar: The Last Airbender: Book 1: ...` |
| `: Series N:` | (used by some UK originals) |
| `: Season N` (end of string) | `The Crown: Season 4` (no episode follows) |

**Parsing algorithm:**

```
parseTitle(rawTitle: String): ParsedTitle
  For each pattern in order:
    Find the pattern in rawTitle
    If found:
      seriesName   = rawTitle.substringBefore(patternMatch).trimEnd(':').trim()
      seasonLabel  = extract matched pattern text (e.g., "Season 1")
      seasonNumber = extract integer from seasonLabel
      episodeTitle = rawTitle.substringAfter(patternMatch + ":").trim()
                     (empty string if nothing follows the pattern)
      return ParsedTitle(
        contentType  = SERIES,
        seriesName   = seriesName,
        seasonLabel  = seasonLabel,
        seasonNumber = seasonNumber,
        episodeTitle = episodeTitle.ifEmpty { null }
      )

  // No recognized pattern found — content type is uncertain
  return ParsedTitle(
    contentType  = UNKNOWN,
    displayTitle = rawTitle,
    // all series-specific fields null
  )
```

**Edge cases:**

| Situation | Handling |
|---|---|
| Series name contains a colon (`Avatar: The Last Airbender: Book 1:...`) | Pattern match for `Book 1` correctly splits after the full series name |
| Episode title contains a colon | Take everything after the pattern match as episode title, including any colons |
| No season indicator (`When They See Us: Part 1`) | Matched by Part N pattern; `season_label = "Part 1"`, `season_number = 1` |
| Title with colon but no season/part indicator (`Knives Out: Glass Onion`) | No pattern matches → `UNKNOWN`; original title preserved |
| Non-English titles | Normalization handles diacritics; pattern matching operates on colon and numeric structure, not language |
| Parsing produces no episode title | `episode_title = null`; series name and season are still recorded |
| Parsing is ambiguous or uncertain | Return `UNKNOWN`; do not invent structure |

## 4.3 Matching Pipeline

The matching pipeline is shared by both camera-based and manual-search flows.

```
Input: queryText (from OCR extraction or user text entry)
│
▼
normalize(queryText) → normalizedQuery
│
▼
Short-title check (see below)
│
▼
Stage 1: FTS4 MATCH query
  Search: normalized_title MATCH normalizedQuery
       OR normalized_series_name MATCH normalizedQuery
  → ftsResults: List<ViewingRecord>
│
▼
Stage 2: Score FTS results with fuzzy ratio
  For each ftsResult:
    score = max(
      tokenSortRatio(normalizedQuery, ftsResult.normalized_title),
      tokenSortRatio(normalizedQuery, ftsResult.normalized_series_name ?: "")
    )
  → scoredResults: List<(ViewingRecord, score)>
│
▼
Stage 3: If ftsResults is empty, fuzzy fallback
  Retrieve all distinct normalized_title and normalized_series_name values from DB
  Run tokenSortRatio against each
  Keep candidates with score ≥ 55
  → scoredResults
│
▼
Stage 4: Threshold and classify
  best = scoredResults.maxByScore()
  if best == null or best.score < 55 → MatchResult.None
  if best.score ≥ 85              → MatchResult.Confident(best)
  if best.score in [55, 84]       → MatchResult.Ambiguous(top candidates)
```

**Thresholds:**
- **85 — Confident:** Sufficient similarity to proceed to history lookup without user confirmation.
- **55 — Possible:** Enough similarity to suggest candidates; user selects the correct one.
- **< 55 — No match:** Not close enough to present as a suggestion.

These thresholds are named constants (`CONFIDENCE_THRESHOLD_HIGH = 85`, `CONFIDENCE_THRESHOLD_POSSIBLE = 55`) and are revisable without changing the algorithm structure.

**Fuzzy algorithm:** Token sort ratio from fuzzywuzzy-kotlin. Token sort ratio is preferred over simple Levenshtein distance because it is insensitive to word-order differences that are common in OCR output (words detected out of order).

---

**Short-title handling:**

Very short queries (normalized length ≤ 3 characters) are treated differently because fuzzy matching against short strings is unreliable: a query of `"it"` would score high against almost any title that contains the word "it."

Rules for short queries:

1. **Only an exact normalized title match may produce a Confident result.** Fuzzy scoring and partial matching are not used to reach the Confident threshold for short queries.
2. **The Ambiguous threshold does not apply.** A short query that does not exactly match any record returns `MatchResult.None`, not `MatchResult.Ambiguous`.
3. **Multiple exact-title matches produce an Ambiguous result.** If the database contains two records whose `normalized_title` exactly equals the query (for example, two distinct Netflix catalog entries both exported under the identical title `"It"`, such as the 1990 TV miniseries and the 2017 film), they are presented to the user for selection.
4. **Longer titles containing the short word are not candidates.** A query of `"it"` does not match `"it chapter two"`, even though `"it"` is a substring and the fuzzy score would be high.

**Representative matching examples:**

| Query (OCR output) | Best DB match | Rule applied | Result |
|---|---|---|---|
| `"the irishman"` | `"the irishman"` | Standard fuzzy | Confident (score 100) |
| `"irshman"` (OCR error) | `"the irishman"` | Standard fuzzy | Ambiguous (score ~82) |
| `"stranger things"` | `"stranger things"` (series) | Standard fuzzy | Confident (score 100) |
| `"stranger thing"` (partial OCR) | `"stranger things"` | Standard fuzzy | Confident (score ~94) |
| `"it"` | `"it"` | Short-title exact | Confident (exact match) |
| `"it"` | `"it chapter two"` | Short-title exact | No match (not exact) |
| `"it"` | Two distinct Netflix entries both exported as `"It"` (e.g., the 1990 TV miniseries and the 2017 film, stored identically in the export) | Short-title exact | Ambiguous (two exact-title records) |
| `"up"` | `"up"` | Short-title exact | Confident (exact match) |
| `"the crown"` | `"the crown"` + `"the crowned"` | Standard fuzzy | Ambiguous (both score high) |

## 4.4 Series-Level Viewing Statistics

When a series match is found, three distinct values are computed from the matching records:

```
getSeriesStats(normalizedSeriesName: String, profileFilter: String?):
  records = db.getRecordsBySeriesName(normalizedSeriesName, profileFilter)
            WHERE content_type = 'SERIES'

  viewingOccurrences   = records.count()
  distinctEpisodes     = records.map { it.episode_title ?: it.raw_title }.distinct().count()
  seasonsRepresented   = records.mapNotNull { it.season_number }.distinct().count()
  mostRecentDate       = records.maxOf { it.view_date }
```

**Example:**

A history containing:
- 3 records for `Stranger Things S1E01 "Chapter One"`
- 1 record for `Stranger Things S1E02 "Chapter Two"`
- 1 record for `Stranger Things S2E01 "MADMAX"`

Yields: **viewingOccurrences = 5, distinctEpisodes = 3, seasonsRepresented = 2**

These three values are always presented separately (BR-009). They are never summed or conflated.

**UNKNOWN records:** UNKNOWN records are not included in series statistics. They are matched on `normalized_title` only and produce a basic "previously watched / not watched" result without a series breakdown. If an UNKNOWN record is later determined to be part of a series (through user correction or a future update), its content_type may be revised.

---

# 5. Camera and OCR

## 5.1 Camera Workflow

Phase 1 uses **discrete still-image capture**. The user frames the television and taps a button; there is no continuous frame scanning.

```
User opens camera screen
  → CameraX ImageCapture use case initialized
  → Preview rendered full-screen
  → Overlay guide rectangle displayed ("Frame the title")
  → User taps capture button
  → CameraX captures still image
  → Image passed to OCR pipeline
  → Loading indicator shown
  → Result displayed (or error state)
```

CameraX is used via the `ImageCapture` use case only. The live preview use case (`Preview`) renders the viewfinder. The `ImageAnalysis` use case is not used in Phase 1.

**Permissions:** `android.permission.CAMERA` is required. If denied, the app shows an explanation and link to Settings. It does not assume the user will retry.

**Image handling:**
- The captured image is processed in memory and then immediately discarded.
- No captured image is written to external storage or the gallery.
- No captured image is retained by the application after the OCR pipeline completes.

## 5.2 On-Device OCR (ML Kit)

**Library:** `com.google.mlkit:text-recognition:16.0.1` (bundled distribution)

Bundling the model adds ~4 MB to APK size but avoids the first-launch download delay and ensures fully offline operation after installation. This is the correct choice for a small application where offline reliability is a stated requirement.

**Input:** The full captured `Bitmap` from CameraX.

**Output:** A `Text` object containing `TextBlock` elements, each with text content, a bounding rectangle, and (for Latin script) a per-element confidence value.

**Failure modes:** If the ML Kit recognizer returns no text blocks, the result is treated as a recognition failure — not as "title not found." Error state is shown to the user with a prompt to retake the photograph.

## 5.3 Candidate Title Extraction

ML Kit will return all visible text from the Netflix screen: title, year, rating, runtime, cast, buttons, description, and menu items. The application must identify which text block is most likely the program title.

**Extraction heuristics (applied in order):**

1. **Filter obvious non-titles:**
   - Numeric-only strings: year, rating percentage, runtime
   - Very short strings (< 3 characters)
   - Strings matching known Netflix UI labels: "Play", "More Info", "+ My List", "Add to My List", "Preview", "Continue Watching"
   - Strings that look like elapsed time: `HH:MM` pattern

2. **Score remaining blocks by size:**
   - ML Kit provides a bounding rectangle for each block.
   - For single-line blocks, the bounding-box height is a proxy for font size.
   - Taller single-line blocks score higher.
   - Multi-line blocks (descriptions, cast) score lower than comparably sized single-line blocks.

3. **Return top 3 candidates** by score. All three are submitted to the matching pipeline; the best match across all candidates is used.

**Limitation acknowledged:** These heuristics are based on the Netflix UI layout as it appears on a standard TV display. Netflix may modify its UI layout. The heuristic approach means occasional misidentification of the title block is possible. The confidence and ambiguity handling in §4.3 provides a safety net: a misidentification will typically produce a low-confidence match, which will be surfaced to the user for confirmation rather than silently returning a wrong result.

---

# 6. Optional Network Recognition

## 6.1 TextRecognizer Interface

Both the ML Kit implementation and the Google Vision API implementation satisfy a common interface. No other component in the application holds a direct reference to either implementation.

```kotlin
interface TextRecognizer {
    val name: String
    val requiresNetwork: Boolean
    suspend fun recognize(imageBitmap: Bitmap): TextRecognizerOutput
}

data class TextRecognizerOutput(
    val rawText: String,
    val blocks: List<TextBlock>,
    val providerName: String
)
```

A `CompositeTextRecognizer` orchestrates the fallback:

```
recognize(imageBitmap):
  localResult = mlKitRecognizer.recognize(imageBitmap)
  candidateTitles = titleExtractor.extract(localResult)

  if candidateTitles.isEmpty() or bestMatchScore(candidateTitles) < ML_KIT_FALLBACK_THRESHOLD:
    if networkAvailable and visionFallbackEnabled:
      LOG "Invoking network-assisted recognition"
      fallbackResult = visionApiRecognizer.recognize(imageBitmap)
      return fallbackResult

  return localResult
```

`ML_KIT_FALLBACK_THRESHOLD` is a named constant, proposed initially at `60` (below the Confident threshold of 85). The fallback is only invoked when ML Kit cannot produce a confident candidate.

## 6.2 Google Vision API

**Endpoint:** `https://vision.googleapis.com/v1/images:annotate`  
**Feature:** `TEXT_DETECTION`  
**Image transmission:** Compressed JPEG (quality 80, max dimension 1920 px). No other data is transmitted.  
**Viewing history is never transmitted.** Only image data is sent.

**API key handling:**

The API key is stored in `local.properties` and injected via `BuildConfig` at build time. `local.properties` must be listed in `.gitignore` and must never be committed to version control.

**Important limitation of this approach:** An API key embedded in an Android application binary is not a true secret. A determined party can extract it from the APK. The `BuildConfig` mechanism is acceptable only as a limited Phase 1 / prototype approach for personal or closed-distribution use. It must not be treated as a secure credential management solution.

**Required mitigations for the Phase 1 key:**

- Restrict the key in the Google Cloud Console to the **Cloud Vision API only**. Do not use a general-purpose unrestricted key.
- Apply **Android application restrictions** in the Google Cloud Console using the app's package name and SHA-1 signing certificate fingerprint. This limits the key to requests originating from the legitimate signed APK.
- Configure a **budget alert** in the Google Cloud Console so that unexpected usage triggers a notification before significant cost is incurred.
- Monitor usage periodically via the Google Cloud Console to detect anomalies.

**Distribution limit:** If the application moves beyond personal or limited test distribution, the embedded-key approach is no longer appropriate. At that point, the network-recognition architecture should be reconsidered — likely introducing a lightweight server-side proxy that holds the key and makes Vision API calls on the client's behalf, so the key is never shipped in the APK. That change is out of scope for Phase 1 and would require separate specification and approval.

**First-time consent:** Before the first Vision API invocation, the application displays a one-time dialog explaining that the image will be sent to Google's servers. The user must confirm. If the user declines, the fallback is disabled permanently. This preference is stored in `SharedPreferences`.

**Failure behavior:**
- Network timeout (10 seconds): return the ML Kit result without informing the user of the fallback failure; log internally.
- API error (4xx, 5xx): same as timeout.
- API key missing or blank: disable fallback silently; proceed with ML Kit only.
- User has disabled fallback: skip entirely; ML Kit result is used regardless of confidence.

**Disabling fallback:** A toggle in Settings labeled "Enhanced recognition (sends image to Google)" allows the user to disable this feature permanently without affecting core functionality.

## 6.3 Cost Implications (OI-05 — Unresolved)

*See Section 8 for the open business decision. Technical estimates are provided there.*

---

# 7. Viewing Progress and Profiles

## 7.1 Viewing Progress (Tier 2 Only)

**Source fields:** `bookmark_ms` (last stop position in this session), `latest_bookmark_ms` (furthest position reached across sessions).

**Display preference:** `latest_bookmark_ms` is preferred when available and non-null, because it represents the furthest point the user reached rather than the last position in any single session. Fall back to `bookmark_ms` if `latest_bookmark_ms` is null.

**For repeated viewings:** Show the progress from the most recent viewing session (the row with the latest `view_date` for that title).

**Formatting:**

```
formatProgress(ms: Long): String
  hours = ms / 3_600_000
  minutes = (ms % 3_600_000) / 60_000
  return if (hours > 0) "Stopped at ${hours}h ${minutes}m"
         else           "Stopped at ${minutes}m"
```

**Not calculated:** Percentage completion. Total content runtime is not in the Netflix export. The application does not make any external call to retrieve it (BR-010).

**Tier 1 behavior:** Progress fields are null for all Tier 1 records. No progress display is shown. The absence of progress information is silent expected behavior, not an error.

## 7.2 Profile Selection (Tier 2 Only)

**Detection:** On Tier 2 import completion, query the distinct `profile_name` values present in the imported records.

**Single profile:** Use it automatically; no selection UI required.

**Multiple profiles:**
- After import, show a profile selection prompt.
- The selected profile name is stored in `SharedPreferences` as `"active_profile"`.
- All viewing-history queries include a `WHERE profile_name = :activeProfile` filter.
- A profile picker is accessible from Settings to switch profiles without re-importing.

**Tier 1 behavior:** Profile name is null for all Tier 1 records. No profile selection is presented. All records are treated as belonging to the single user.

---

# 8. Open Business Decisions

## 8.1 OI-03 — Minimum-Duration Threshold for Autoplayed Filtering

**Current state:** The threshold that determines whether a short or autoplayed session counts as viewing activity has not been approved (BR-001a). This remains a business decision requiring explicit approval before implementation.

**Technical analysis:**

Netflix's Tier 2 export contains an `Attributes` field that directly flags sessions as autoplayed with no user interaction (`"Autoplayed : user action: None"`). The `Duration` field records session length.

Based on published analyses of real Netflix `ViewingActivity.csv` data:
- Auto-played previews while browsing typically last 0–60 seconds.
- Short accidental plays typically last < 2 minutes.
- Deliberate viewings of even short content (trailers saved to history, short films) are typically > 2 minutes.

**Technical recommendation (advisory only):** Exclude a session when **both** of the following are true:
1. `Attributes` contains `"Autoplayed : user action: None"` (explicit Netflix flag)
2. `Duration` < 120 seconds (2 minutes)

This two-condition rule is more precise than a duration-only threshold: it does not exclude short films or episodes that the user actively started.

**Business approval required before any filtering is implemented.** Until approved, Tier 2 imports should include all records (`is_autoplayed` is stored as metadata but not used to exclude records from results).

## 8.2 OI-05 — Network OCR Cost Responsibility

**Current state:** Not decided who bears the cost of Google Cloud Vision API calls (BR-004b). This remains a business decision requiring explicit approval.

**Technical estimates:**

| Usage assumption | Requests/month/user | Estimated cost/user/month |
|---|---|---|
| Light use (1–2 fallbacks/week) | ~8 | $0.00 (within free tier) |
| Moderate use (1 fallback/day) | ~30 | $0.00 (within free tier) |
| Heavy use (5 fallbacks/day) | ~150 | ~$0.23 |

Google Cloud Vision pricing: first 1,000 requests/month free; $1.50 per 1,000 thereafter.

At the scale of a single user or small personal deployment, costs are negligible. Costs become material only at hundreds of daily-active users.

**Options for business consideration:**

| Option | Description | Trade-off |
|---|---|---|
| A — Developer pays | Single developer API key shared across installs | Simple UX; cost grows with users; key must be protected per §6.2 |
| B — User provides key | User enters their own Google Cloud API key in Settings | No developer cost; significant UX friction |
| C — Feature disabled by default | Network fallback ships as an off-by-default advanced setting | No cost until user actively enables; reduces fallback utility |

**Technical recommendation (advisory only):** Option A during Phase 1 (single developer, negligible scale). Revisit if user base grows. Option C is a reasonable conservative alternative.

**Business approval required before finalizing BR-004b implementation.**

---

# 9. Testing Approach

## 9.1 Principles

- Deterministic logic (parsing, normalization, matching, counting) is tested with unit tests. These cover the highest-risk functionality and must run without a device.
- Database and import behavior is tested with integration tests using an in-memory Room database.
- Camera and OCR behavior is tested using fixed image fixtures (PNG files of synthetic or representative TV screenshots) to avoid requiring a physical television for every test run.
- Physical device tests with a real TV are reserved for final acceptance validation.

## 9.2 Unit Tests (no Android dependencies)

| Area | Key test cases |
|---|---|
| Tier 1 CSV parser | Valid rows; blank rows; malformed rows; wrong header; date parsing (M/D/YYYY and ISO fallback); quoted commas in titles |
| Tier 2 CSV parser | All fields parsed; missing optional fields null-safe; Supplemental Video Type rows skipped; Attributes flags detected |
| Title normalizer | Diacritics removed; case lowered; whitespace collapsed; apostrophes normalized |
| Series parser | Standard "Season N" → SERIES; "Part N" → SERIES; colon in series name; title with colon but no season indicator → UNKNOWN (not MOVIE); no pattern found → UNKNOWN |
| Fuzzy matcher | Score ≥ 85 → Confident; score 55–84 → Ambiguous; score < 55 → None |
| Short-title matcher | Query "it" exact match → Confident; query "it" vs "it chapter two" → None; query "it" with two exact records in DB → Ambiguous; query "up" with no match → None |
| Viewing count calculator | Multiple records same episode → correct occurrences, distinct episodes, seasons; UNKNOWN records excluded from series stats |
| Session key — Tier 2 | Same input always produces same key; different start_time_utc → different key; same title different date → different key |
| Session key — Tier 1 | Same file_fingerprint + row_index → same key; same file content row 0 ≠ row 1 even when Title+Date identical |
| File fingerprint | Same file content → same fingerprint; one byte difference → different fingerprint |
| Bookmark formatter | Milliseconds → "Stopped at Xh Ym"; zero-hour case → "Stopped at Ym" |
| Reconciler | Tier 1 record upgraded to Tier 2; Tier 2 record not downgraded by later Tier 1; same-day repeat: both Tier 1 records survive Tier 2 reconciliation |

## 9.3 Integration Tests (Android, in-memory Room database)

| Area | Key test cases |
|---|---|
| Tier 1 import | CSV → database → query returns correct records |
| Tier 2 import | CSV → database → Tier 2 fields populated; session_key set correctly |
| Same-day repeat viewing | Two identical Tier 1 rows (same title, same date) → two records stored with distinct session_keys; viewingOccurrences = 2 |
| Tier 1 idempotency | Import same file twice (same fingerprint) → second import skipped; record count unchanged |
| Tier 1 updated file | Import file A, then file B (different fingerprint) → Tier 1 records replaced; Tier 2 records preserved |
| Reconciliation | Tier 1 import followed by Tier 2 import → Tier 2 data present; no extra records created for matched sessions |
| Same-day reconciliation (exact dates) | Two Tier 1 records + two Tier 2 records for same title+date (all dates match exactly) → both records upgraded; viewingOccurrences = 2; no duplicates |
| Same-day reconciliation (±1-day window) | Two Tier 1 records on date D + one Tier 2 session on date D + one Tier 2 session on date D+1 (UTC midnight boundary) → both Tier 1 records upgraded via exact match (first) then adjacent fallback (second); viewingOccurrences = 2, not 3 |
| Adjacent-date false-merge limitation | One Tier 1 record on date D, no Tier 1 on D+1; Tier 2 session on D+1 with no exact match → adjacent fallback merges with date-D record; viewingOccurrences = 1; test confirms the known limitation is understood, not treated as a bug |
| Tier 2 idempotency | Import same Tier 2 file twice → second import changes nothing |
| Tier 1 after Tier 2 | Tier 1 import after Tier 2 does not downgrade Tier 2 records or create duplicates |
| UNKNOWN records | Title with no series pattern → content_type = UNKNOWN; matched on normalized_title; no series breakdown in result |
| FTS search | Known title returns match; UNKNOWN-typed record returned by title search |
| Profile filtering | Records filtered correctly by profile_name |
| Data deletion | Clear all → all queries return empty |

## 9.4 Camera / OCR Tests (image fixtures)

A small set of PNG test images will be maintained in the repository representing synthetic Netflix-style screens:

- Clear title, clean background
- Title with secondary text (year, rating, description)
- Low-contrast / glare-simulated image
- Partial title (edge of screen)
- Non-title text dominant (description fills frame)

Tests use these images to verify:
- Candidate extraction returns the correct title as first candidate
- Non-title text (buttons, descriptions) is filtered
- Failure state is returned for an image with no parseable text

These tests do not require ML Kit or a real device; the extraction heuristics operate on strings returned by a mock recognizer.

## 9.5 Business Specification Success Criteria — Test Mapping

| SC # | Criterion | Test type |
|---|---|---|
| 1 | Import Tier 1 dataset | Integration |
| 2 | Manual search finds known title | Integration / UI |
| 3–5 | Photograph TV, identify title | Physical device |
| 6 | History lookup returns "Previously watched" | Integration |
| 7 | Viewing date displayed | Integration / UI |
| 8–9 | Unwatched title → "No previous viewing found" with correct wording | Integration / UI |
| 10 | Manual search works independently | Integration / UI |
| 11 | Series: three values shown separately | Unit + Integration + UI |
| 12 | Recognition failure ≠ not-watched result | Unit (error-path) |
| 13 | App functional with network disabled | Integration (mock network unavailable) |

---

# 10. Proposed Implementation Sequence

The sequence below builds deterministic, testable functionality first and defers camera and network complexity to later steps. Each step should result in a working, testable application increment.

| Step | Deliverable | Rationale |
|---|---|---|
| 1 | Data model + Room schema + FTS4 table | Foundation for all subsequent steps |
| 2 | Tier 1 CSV parser + import screen | Earliest point at which real Netflix data can be imported and queried |
| 3 | Manual title search + result screen | Validates the full normalization → FTS → display pipeline without camera complexity |
| 4 | Fuzzy matching + ambiguous-result flow | Handles titles that are not an exact FTS match |
| 5 | Series/episode parser + series result display | Validates three-value series statistics, UNKNOWN handling, and episode detail |
| 6 | Tier 2 CSV parser + Tier 2 import | Adds duration, progress, profiles, hidden records, precise session keys |
| 7 | Tier 1 → Tier 2 reconciliation | Validates the upgrade path and same-day repeat handling |
| 8 | Profile selection UI | Depends on Tier 2 multi-profile data |
| 9 | Camera capture screen + CameraX | Discrete capture only; no OCR yet |
| 10 | ML Kit OCR + candidate extraction | Attaches OCR pipeline to captured image |
| 11 | Confidence model + ambiguous recognition UI | Connects OCR output to matching pipeline; short-title rule applied here |
| 12 | Network fallback (Vision API) + consent | Implemented last; least critical to core function |
| 13 | End-to-end acceptance testing | Runs all BS success criteria against real device and data |

**Note:** Steps 1–5 can be developed and fully tested on an emulator without a physical phone, a real television, or any network access. The majority of the application's business logic lives in these steps.

---

*Technical Specification v1.2 — APPROVED TECHNICAL BASELINE*  
*Controlling document: Business Specification v1.2 — APPROVED BUSINESS BASELINE*  
*This document is presented for review. Implementation shall not begin until this specification is explicitly approved as the Technical Baseline.*
