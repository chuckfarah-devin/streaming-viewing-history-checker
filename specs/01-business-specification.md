# Business Specification
## Streaming Viewing History Checker
### Phase 1 — Netflix / Android

**Document Status:** v1.2 — APPROVED BUSINESS BASELINE  
**Development Method:** Specification-Driven Development (SDD)  
**Initial Platform:** Android / Samsung phone  
**Initial Streaming Service:** Netflix  
**Prepared:** 2026-08-23  
**Supersedes:** v1.1

---

## Revision History



| Version | Date | Change Summary |
|---|---|---|
| 1.0 | 2026-08-23 | Initial draft from business specification |
| 1.1 | 2026-08-23 | Incorporated product decisions: Netflix import tiers, TV series matching, camera recognition approach; added Assumptions, Constraints, Dependencies, and Open Issues section |
| 1.2 | 2026-08-23 | (1) Revised BR-009: series viewing occurrences, distinct episodes, and seasons represented are semantically distinct values. (2) Revised BR-001 / Section 6.1: Tier 2 after Tier 1 reconciles data rather than prescribing replacement. (3) Revised OI-03: autoplayed duration threshold is a business-rule decision requiring explicit approval, not a Technical Specification decision. (4) Revised BR-001c and resolved OI-02: hidden Netflix records are included in history lookups; the hidden flag is metadata, not an exclusion instruction. Consistency updates to Section 5, Section 6.1 cross-reference, and Section 13 criterion 11. |
| 1.3 (proposed) | 2026-08-26 | Version 1.1 UI/UX presentation only: result-screen language, Tier 1/Tier 2 display rules, profile prominence, prohibited wording, content-type chip rules, and camera-flow clarification. No functional business rules changed. See Version_1.1_Step14_Specification_Revisions.md for detailed deltas. |

---

# 1. Purpose

Develop a mobile application that allows a user to point an Android phone at a television displaying a movie or other streaming title and determine whether the user has previously watched that title.

The initial implementation will support Netflix.

The longer-term objective is to support multiple streaming services and potentially content accessible through devices such as Amazon Fire TV.

The application should answer a simple real-world question:

**"Have I watched this before?"**

When information is available, it should also answer:

- When did I watch it?
- Have I watched it more than once?
- How much of it did I watch?

The application should make answering these questions substantially easier than manually searching years of viewing history.

---

# 2. Problem Statement

Streaming services contain very large catalogs, and users frequently encounter movies or programs that look familiar but cannot remember whether they previously watched them.

This problem becomes more significant as viewing history accumulates over many years and across multiple streaming services.

Netflix provides users with historical viewing activity, but consulting that history manually while browsing television content is inconvenient.

The desired experience is:

1. A movie or program is displayed on the television.
2. The user opens the mobile application.
3. The user points the phone at the television and captures the displayed title.
4. The application identifies the movie or program.
5. The application searches the user's viewing history.
6. The application immediately reports whether the title was previously watched and any available viewing information.

---

# 3. Product Vision

The product should eventually provide a unified personal viewing-history lookup capability independent of the streaming service currently being browsed.

The user should not need to remember:

- which streaming service carried the title;
- approximately when the title was watched;
- whether it was watched more than once; or
- where the relevant viewing-history information is stored.

The mobile application should act as the user's personal **"Have I seen this?"** database.

Phase 1 will prove this concept using Netflix.

---

# 4. Phase 1 Scope

Phase 1 shall provide an Android application capable of:

1. Importing a user's Netflix viewing history in one of two supported formats (see BR-001).
2. Maintaining that history locally on the device for searching.
3. Using the phone camera to capture a television screen displaying Netflix content.
4. Determining the title being displayed through on-device text recognition, with optional network-based enhancement when recognition confidence is insufficient.
5. Matching the identified title against the imported Netflix viewing history.
6. Reporting whether the title appears in that history.
7. Reporting known viewing date or dates.
8. Reporting multiple viewing occurrences when present.
9. Reporting viewing progress or stop-position information when available from the imported data.
10. For television series, reporting at both the series level and with episode detail available on request.

The initial target device is a modern Samsung Android phone.

---

# 5. Primary User Scenario

The user is browsing Netflix on a television.

Netflix displays information for a movie that the user thinks may have been watched previously.

The user:

1. Opens the application.
2. Selects the camera/check function.
3. Points the phone at the television.
4. Captures the screen containing the movie title.
5. Waits for identification and lookup.

The application then displays a result such as:

**Title:** The Irishman

**Status:** Previously watched

**Last watched:** March 17, 2021

**Viewing occurrences:** 1

For a television series, the result indicates the series status first, with viewing counts expressed as three distinct values:

**Title:** Stranger Things

**Status:** Previously watched

**Last watched:** July 4, 2022

**Viewing occurrences:** 17  
**Distinct episodes watched:** 17  
**Seasons represented:** 2

*(Full episode detail — including which episodes appeared more than once, if any — is available for the user to review. It is not the primary display.)*

If the title cannot be found in the imported viewing history, the application displays:

**Status:** No previous viewing found

The wording must not imply that the user definitively never watched the title, because the available viewing-history data may be incomplete.

---

# 6. Functional Business Requirements

## BR-001 — Netflix Viewing History Import

The application shall provide a method for importing Netflix viewing-history information supplied by the user.

The application shall not depend on unsupported access to the user's Netflix account.

The application shall retain sufficient imported information to perform subsequent searches without requiring repeated access to Netflix.

### 6.1 Import Tiers

Phase 1 supports two Netflix export formats. The user imports whichever format they have available. The application shall detect which format has been provided and use the available data accordingly.

**Tier 1 — Simple Viewing Activity CSV (Phase 1 baseline)**

This is the primary supported format. It is immediately available to all Netflix users and requires no advance request.

- Obtained from: Netflix website → Account → Viewing Activity → Download All
- Contains: **Title** and **Date** for each viewing session
- Available: Immediately, within seconds of request
- Limitations: No session duration, no stop position, no profile name, no device information

The core Phase 1 features — camera recognition and history lookup — shall be fully operational using only the Tier 1 format. Features that require duration or stop-position data (see BR-010) are not available with this format.

**Tier 2 — Full Data Export (optional supplemental)**

This format provides richer data and unlocks additional features. It requires the user to submit a data request to Netflix and wait for delivery.

- Obtained from: Netflix website → Account → Privacy → Download your personal information
- Contains: Profile name, start time (UTC), session duration, title, device type, Bookmark (last stop position), Latest Bookmark (furthest position reached), viewing attributes, country
- Delivery time: Typically 24 hours; up to 30 days per Netflix policy
- Limitations: Not immediately available; requires advance planning

When the user imports a Tier 2 file, all features available with Tier 1 remain available, and additional features defined in BR-001a, BR-001b, BR-001c, and BR-010 become available or fully operational.

The application shall accept a Tier 2 export at any time, including after an initial Tier 1 import. When a Tier 2 export is imported after a Tier 1 import, the application shall incorporate the richer Tier 2 information without creating duplicate viewing-history records. The technical method used to reconcile the datasets — whether by replacement, merge, rebuild, or another approach — shall be defined in the Technical Specification.

---

### BR-001a — Record Filtering

The application shall not treat all records in the import as evidence of intentional viewing.

When a Tier 2 export is available, the application shall filter records that were autoplayed with no user interaction and whose session duration falls below a minimum threshold. Such records shall be excluded from search results by default.

When only a Tier 1 export is available, session duration and autoplayed status are not present in the data. The application shall include all Tier 1 records in search results without filtering. Users should be aware that this may include brief auto-played sessions.

The minimum duration threshold is not yet established and remains an open business decision (see OI-03).

---

### BR-001b — Multi-Profile Support (Tier 2 only)

Netflix accounts may have multiple profiles. The Tier 2 export includes a profile name for each viewing record.

When a Tier 2 export is imported and multiple profiles are detected, the application shall identify the profiles present and allow the user to select which profile's history to search. The application shall clearly indicate which profile's history is currently active.

This feature is not available when a Tier 1 export is used, because Tier 1 data does not include profile information.

---

### BR-001c — Hidden Records (Tier 2 only)

Netflix allows users to hide individual titles from their viewing history. In a Tier 2 export, such records carry a hidden flag. This flag indicates that the user chose to remove the title from view within Netflix. It does not mean the viewing did not occur.

The purpose of this application is to determine whether a title was historically watched. A viewing record that was subsequently hidden within Netflix is still evidence that the viewing took place. Hiding a title within Netflix and determining whether the user historically watched that title are different concepts and shall not be conflated.

Therefore:

- Hidden viewing records shall be included when determining whether a title was previously watched.
- The hidden status shall be preserved in the local data as metadata, available for potential use in future functionality.
- Phase 1 shall not provide a user-facing setting to include or exclude hidden records; the inclusion behavior is fixed.

This requirement applies only to Tier 2 imports, where the hidden flag is present in the data. Tier 1 exports do not carry hidden-status information.

---

## BR-002 — Historical Records

The application shall support viewing-history records extending back multiple years when those records are present in the imported Netflix data.

There shall be no arbitrary application-imposed limitation restricting searches to recent viewing history.

---

## BR-003 — Camera Capture

The application shall allow the user to capture an image of a television screen using the Android phone camera.

The captured image shall be analyzed to identify the movie or program being displayed.

---

## BR-004 — Title Recognition

The application shall attempt to determine the displayed content title from information visible in the captured image.

Recognition shall tolerate reasonable variations in:

- capitalization;
- punctuation;
- spacing;
- typographical presentation; and
- minor recognition errors.

The application shall not require the photographed title to exactly match the representation stored in viewing history.

### BR-004a — On-Device Recognition

The application shall perform title recognition on the device using an on-device model that does not require a network connection. This approach keeps the core workflow fast, private, and usable offline after initial installation.

### BR-004b — Optional Network-Assisted Recognition

When on-device recognition produces a result with insufficient confidence, and network access is available, the application may attempt enhanced recognition using a network-based service.

This enhancement is optional and not required for normal operation. The application shall remain functional when network-assisted recognition is unavailable or disabled.

**Any use of network-assisted recognition transmits image data to an external service.** This represents a privacy boundary. The application shall:

- Inform the user the first time image data is transmitted for recognition;
- Allow the user to disable network-assisted recognition in Settings; and
- Never transmit viewing-history data as part of this or any recognition request.

This requirement is a business-level privacy constraint. The specific service used and any associated cost implications are matters for the Technical Specification.

---

## BR-005 — Television Series Matching

### BR-005a — Series-Level Identification

When the user photographs a television series title, the application shall match at the series level. A viewing record for any episode of the series shall be treated as evidence that the series was watched.

Series-level match is the primary result presented to the user. The result shall clearly identify whether the **series** appears in the user's viewing history.

This ensures that photographing a title card for "Stranger Things" returns a useful answer even though Netflix stores individual episode records.

### BR-005b — Episode Detail

When a series match is found, the application shall make episode-level detail available to the user. This detail shall include the episodes and seasons present in the imported history, and the most recent viewing date for the series.

Episode detail is supplemental to the series-level result and shall not be the primary display. The user should be able to access it without navigating to a separate screen if practical.

---

## BR-006 — Viewing-History Search

Once a title has been identified, the application shall search the imported viewing history for corresponding records.

The application shall distinguish between:

- a confident match;
- a possible or ambiguous match; and
- no match.

The application shall not silently represent an uncertain match as certain.

When the match is ambiguous, the application shall present the candidate titles to the user for selection rather than proceeding to a result that may be incorrect. Correctness is more important than automatically producing an answer.

---

## BR-007 — Previously Watched Result

When a confident historical match exists, the application shall clearly indicate that the title appears in the user's viewing history.

---

## BR-008 — Viewing Date

When available, the application shall display the date on which the title was watched.

If multiple viewing records exist, the application shall make those occurrences available to the user.

At minimum, the most recent known viewing date shall be displayed.

---

## BR-009 — Multiple Viewings

The application shall determine when multiple viewing-history records exist for the same title and report that information with the following distinctions.

**For movies,** the result shall state the total number of viewing-history records for that title. Each record represents one viewing occurrence.

**For television series,** the result shall report three separately maintained values:

- **Viewing occurrences:** The total number of applicable viewing-history records for the series, counting every entry including repeated views of the same episode.
- **Distinct episodes watched:** The number of unique episodes represented across those records.
- **Seasons represented:** The number of distinct seasons represented across those records.

These three values have different meanings and shall not be conflated in the data model or the result.

*Example:* A viewing history containing three records for the same episode, plus four records for four other episodes across two seasons, yields: 7 viewing occurrences, 5 distinct episodes watched, 2 seasons represented.

The Technical Specification shall define how these values are displayed in the user interface. This Business Specification requires that the three values remain semantically distinct in the data model regardless of how they are presented.

---

## BR-010 — Viewing Progress

**Tier 2 import required.** This feature is not available when a Tier 1 (simple CSV) import is used.

When a Tier 2 export has been imported, the application shall display the last known stop position for a title when that information is present in the imported data.

The stop position shall be expressed as a time position (for example: "Stopped at 1h 23m") derived from the Bookmark or Latest Bookmark fields in the Tier 2 export.

**Percentage completion shall not be displayed.** The Netflix export does not include total content runtime. The application shall not manufacture, estimate, or retrieve a viewing percentage from an external source for this purpose. Displaying an inaccurate completion percentage would be misleading.

The absence of viewing progress information when a Tier 1 import is in use is expected behavior, not an error condition.

---

## BR-011 — No Historical Match

When no corresponding record exists in the available viewing history, the application shall state that no previous viewing was found.

The application shall avoid an absolute statement such as "You have never watched this movie" because the application's data may not represent every viewing circumstance.

Acceptable wording: "No previous viewing found in your imported Netflix history."

The result screen shall note that results reflect only the imported Netflix history.

---

## BR-012 — Manual Search

The viewing-history database shall also be searchable by manually entering a title.

Camera recognition shall therefore be a convenient input method rather than the only mechanism for querying viewing history.

Manual search shall use the same matching logic as camera-based recognition, beginning at the point where a candidate title string is available. The result format shall be identical.

This capability will also assist testing and diagnosis of title-recognition problems.

---

## BR-013 — Local Operation

After viewing history has been imported, normal viewing-history searches shall not require access to the user's Netflix account.

Core camera-based title recognition and history lookup shall operate without an Internet connection, relying on the on-device recognition model (see BR-004a).

Network-based recognition enhancement (BR-004b) is explicitly identified as the one feature that requires Internet access. It is optional and does not affect offline operation when unavailable.

Any future feature that requires Internet access shall be explicitly identified in the Technical Specification before implementation.

---

# 7. Data Ownership and Privacy

Viewing history is personal user data.

Phase 1 stores all imported viewing history locally on the user's device. Viewing history shall not be transmitted to any external server or service.

The application shall not transmit viewing-history information to an external service regardless of which recognition path is used.

The only data that may leave the device during normal Phase 1 operation is a captured image frame when network-assisted recognition (BR-004b) is invoked. This is image data, not viewing history. The user shall be informed before this occurs and may disable it.

The user shall retain control over all imported viewing-history data and shall be able to remove that data from the application at any time through a clearly accessible setting.

---

# 8. Accuracy and Confidence

The system shall distinguish between identification confidence and viewing-history results.

An example result structure:

**Recognized title:** The Irishman  
**Recognition confidence:** High  
**Viewing-history match:** Found — Last watched March 17, 2021

If recognition is uncertain, the application shall present candidate titles for the user to confirm or correct before proceeding to a history lookup. An incorrect result caused by an unconfirmed uncertain match is worse than asking the user to confirm.

A recognition failure shall be clearly distinguishable from a no-match result.

---

# 9. Error Handling

The application shall provide understandable results and messages when:

- no title can be identified from the image;
- multiple possible titles are suggested and await user confirmation;
- image quality is insufficient for recognition;
- no Netflix history has been imported yet;
- the imported history file cannot be read or does not match the expected format;
- a title is recognized but no corresponding record exists in the imported history;
- viewing-progress information is requested but was not available in the imported data format;
- network-assisted recognition is attempted but network access is unavailable; or
- required data is otherwise unavailable.

Errors shall not be represented as "not watched." An inability to recognize a title, read a file, or complete a network request does not constitute evidence that a title was not viewed.

---

# 10. Phase 1 User Experience Goal

The primary interaction should require very little effort.

The target experience is:

**Open app → photograph television → receive answer**

The result should normally be available within a few seconds.

The application should not require the user to navigate through multiple screens simply to perform a viewing-history check.

The import step is a one-time setup action. After setup, the core workflow requires no repeated configuration.

---

# 11. Out of Scope for Initial Phase

The following are explicitly outside the initial implementation unless subsequently added through specification revision:

- automatic login to Netflix;
- scraping Netflix private interfaces;
- modifying Netflix viewing history;
- controlling Netflix playback;
- controlling the television;
- controlling Fire TV;
- automatic synchronization with any streaming service;
- iPhone/iOS support;
- multi-user cloud synchronization;
- recommendation engines;
- social networking features;
- automatic identification of streaming provider from screen content;
- viewing-percentage computation requiring external runtime data; and
- support for streaming services other than Netflix.

These capabilities may be considered in later specifications.

---

# 12. Future Expansion

The architecture should not unnecessarily prevent later support for additional viewing-history sources.

Potential future sources include:

- Amazon Prime Video;
- Disney+;
- Hulu;
- Max;
- other streaming services; and
- Fire TV viewing/activity information when legitimately accessible.

Future versions may combine viewing records from multiple providers into a unified personal viewing-history database.

The business concept shall therefore distinguish between:

**content identification** (determining what title is on the screen)

and

**viewing-history source** (the service from which history was imported)

even though Phase 1 supports only Netflix.

The Phase 1 data model and import mechanism should be designed so that adding a second viewing-history source does not require replacing the storage or matching infrastructure.

---

# 13. Phase 1 Success Criteria

Phase 1 shall be considered successful when the following demonstration can be performed reliably:

1. Import a real Netflix viewing-history dataset (Tier 1 or Tier 2) containing multiple years of history.
2. Select a movie known to exist in that history.
3. Display that movie's Netflix information on a television.
4. Photograph the television using the Samsung Android application.
5. Correctly identify the title.
6. Correctly report that the title appears in viewing history.
7. Display the known viewing date or dates.
8. Repeat the test with a title that does not exist in the imported history.
9. Correctly report that no previous viewing was found, using the required wording.
10. Demonstrate manual title lookup independently of camera recognition.

In addition:

11. Repeat the series test: photograph a television series title and confirm that the series-level result is displayed first, that viewing occurrences, distinct episodes watched, and seasons represented are reported as three separate values, and that episode detail is accessible.
12. Confirm that a recognition failure (blurry or untargetable image) produces an error state distinct from a no-match result.
13. Confirm that the application remains functional with network access disabled (no crash, no incorrect result from a failed network call).

---

# 14. Assumptions, Constraints, Dependencies, and Open Issues

## 14.1 Assumptions

**A-01 — Netflix Tier 1 export stability**  
Netflix continues to provide the simple Viewing Activity CSV export at the Account → Viewing Activity location. The format (title and date columns) does not change without notice. This is the foundation of Phase 1; if Netflix removes or restructures this export, Phase 1 import must be revised.

**A-02 — Title string consistency**  
The title strings that appear in Netflix's exported viewing history are sufficiently close to the title text displayed on the television for fuzzy matching to succeed. If Netflix uses substantially different title representations in the export versus the display (for example, abbreviated titles, translated titles in a different language, or internal catalog names), matching accuracy will be affected.

**A-03 — Camera image quality**  
A modern Samsung Android phone in a typical home viewing environment can capture television screen text with sufficient clarity for on-device text recognition to function. Extreme glare, very low ambient light, motion blur, or highly stylized display fonts may reduce recognition accuracy.

**A-04 — Single-account import**  
Phase 1 assumes the user imports history from a single Netflix account. Managing history from multiple Netflix accounts is not in scope.

---

## 14.2 Constraints

**C-01 — Viewing progress requires Tier 2 import**  
Session duration and stop-position (Bookmark) data are not present in the Tier 1 simple CSV. BR-010 is only available to users who have obtained and imported the Tier 2 full data export. This is an inherent constraint of Netflix's data-export design, not an application limitation.

**C-02 — Viewing progress expressed as time, not percentage**  
The Netflix export does not include total content runtime. Percentage completion cannot be computed from the available data without an additional external API call. Phase 1 does not make such a call. Viewing progress is expressed as a time position only.

**C-03 — Multi-profile support requires Tier 2 import**  
Profile information is not present in the Tier 1 export. Multi-profile selection (BR-001b) is only available when a Tier 2 file is imported.

**C-04 — Autoplayed filtering requires Tier 2 import**  
Duration and autoplayed-status attributes are not present in the Tier 1 export. Tier 1 imports include all records without filtering. The minimum duration threshold for Tier 2 filtering remains an open business decision (see OI-03).

**C-05 — History reflects import date**  
The imported viewing history is a point-in-time snapshot. The application does not automatically synchronize with Netflix. If the user watches new content after importing, those titles will not appear in search results until the user re-imports.

**C-06 — Network-assisted recognition transmits image data**  
Network-assisted recognition (BR-004b) transmits a captured image frame outside the device. This is the only category of data that leaves the device during Phase 1 operation. This transmission is explicitly not of viewing history data, but it does cross the local privacy boundary and must be disclosed and controllable by the user.

---

## 14.3 Dependencies

**D-01 — User must perform a manual export step**  
Phase 1 requires the user to obtain their Netflix viewing history and import it. This is a one-time manual setup step. The application cannot perform this step automatically. The user must be guided through the Netflix export process.

**D-02 — Tier 2 export delivery time**  
If the user wishes to access Tier 2 features (progress, multi-profile), they must submit a data request to Netflix and wait. Netflix states up to 30 days; delivery in practice is often within 24 hours. Users who want Tier 2 features should be informed to request the export in advance.

**D-03 — On-device recognition model availability**  
On-device text recognition relies on a model that may require a one-time download on first use, depending on the distribution method chosen during implementation. The application shall handle the case where the model is not yet available and guide the user accordingly.

**D-04 — Network-assisted recognition API key**  
If network-assisted recognition is implemented, it requires a developer-provisioned API key. This is an administrative and potentially cost-bearing dependency. The business decision about whether and how to expose this cost to users is deferred to Technical Specification and a separate product decision if required.

---

## 14.4 Open Issues

**OI-01 — Netflix export format changes**  
Netflix may change its export formats without advance notice. The application should validate the format of any imported file and report a clear error if the format does not match expectations, rather than silently importing incorrect data. A strategy for handling format changes gracefully is a Technical Specification concern.

**OI-02 — Hidden records: user preference** *(Resolved — v1.2)*  
Resolved by revision of BR-001c in v1.2. Hidden viewing records shall be included in history lookups. The Netflix hidden flag is treated as metadata and does not constitute evidence that a viewing did not occur. No Phase 1 setting is required to include or exclude hidden records. This issue is closed.

**OI-03 — Minimum-duration threshold for autoplayed filtering** *(Unresolved — Business Decision Required)*  
The threshold below which a short or autoplayed session does not count as viewing activity (BR-001a) has not been established.

This threshold directly affects what the application treats as "previously watched." It is therefore a business-rule decision, not a decision to be made unilaterally in the Technical Specification.

During technical investigation, representative Tier 2 export data may be analyzed to inform a recommendation. The Technical Specification may propose a specific threshold value and provide supporting evidence from that analysis. However, the proposed threshold must be reviewed and explicitly approved as a business rule before any implementation that depends on it is considered compliant with this specification.

This issue remains open. The threshold shall not be selected by the implementation agent or defined solely within the Technical Specification.

**OI-04 — Series detection edge cases**  
Not all Netflix series follow the standard "Series: Season N: Episode" title format. Some use "Part N," "Volume N," "Chapter N," or no season indicator at all. The accuracy of series-level matching for non-standard formats is an implementation risk. Edge cases discovered during Technical Specification or testing should be raised for clarification.

**OI-05 — Cost responsibility for network-assisted recognition**  
Network-assisted recognition (BR-004b) may incur a per-use cost from the external service provider. The business has not decided whether this cost is absorbed by the developer or whether the feature requires the user to supply their own API credentials. This decision is required before the Technical Specification for BR-004b is finalized.

---

# 15. Specification-Driven Development Requirements

This project shall use Specification-Driven Development.

This Business Specification defines product intent and expected behavior.

Implementation choices shall not silently redefine these requirements.

Before substantial implementation begins, a Technical Specification shall be produced that maps the business requirements to:

- application architecture;
- Android components;
- data structures;
- viewing-history import mechanism and Tier 1 / Tier 2 handling;
- title-recognition mechanism and the interface isolating OCR providers;
- title-matching algorithm including series-level and episode-level matching;
- persistence;
- privacy boundaries, with explicit documentation of any external transmission;
- interfaces;
- error handling;
- testing strategy; and
- acceptance tests.

Requirements that cannot be implemented as written shall be identified explicitly rather than silently modified.

Ambiguities discovered during implementation shall be raised for specification clarification before being resolved through implementation choices.

---

# 16. Development-Agent Role

The implementation agent may:

- propose technical approaches;
- identify ambiguities;
- identify technical limitations;
- identify unsupported assumptions;
- recommend specification changes;
- implement approved requirements; and
- construct automated tests.

The implementation agent shall not make unrecorded product decisions where the Business Specification is ambiguous.

When implementation behavior differs from the specification, the agent shall determine whether the cause is:

1. an implementation defect;
2. an incorrect technical assumption;
3. unavailable or different source data;
4. an ambiguous specification; or
5. a required change in product intent.

The specification shall be updated deliberately when product intent changes. Unilateral resolution of ambiguities through implementation is not permitted.
