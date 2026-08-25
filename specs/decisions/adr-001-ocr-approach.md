# ADR-001 — Camera Recognition Approach

**Status:** Decided  
**Date:** 2026-08-23  
**Deciders:** Product owner  
**Relates to:** BR-004, BR-004a, BR-004b

---

## Context

The application requires a mechanism to extract a title string from a photograph of a television screen. Two viable approaches were evaluated for Phase 1:

**Option A — On-device recognition only**  
Use a bundled on-device text-recognition model. Fast, private, offline-capable, no per-use cost. Lower accuracy on stylized or partially obscured text.

**Option B — Cloud recognition only**  
Send the captured image to a cloud text-recognition API. Higher accuracy, especially for stylized or low-contrast text. Requires internet access on every camera lookup. Introduces a privacy boundary and potential per-use cost.

**Option C — On-device primary, cloud fallback**  
Use on-device recognition for all captures. When the on-device result has insufficient confidence and internet is available, optionally attempt cloud-based recognition for a better result.

---

## Decision

**Option C was selected.**

On-device recognition is the primary path. It is invoked for every camera capture regardless of network availability. It is fast, private, and keeps the core workflow independent of internet access.

Cloud-based recognition is a secondary, optional fallback. It is only attempted when:
1. On-device recognition produces a result below a defined confidence threshold, and
2. Network access is available at the time of capture.

The user is informed the first time image data is transmitted to an external service. The fallback can be disabled in Settings.

---

## Business Rationale

- The core use case ("open app, photograph TV, get answer") must work offline and without configuration. On-device recognition satisfies this.
- TV screen text is sometimes stylized, low-contrast, or partially obscured by UI elements. Some captures will genuinely benefit from higher-accuracy cloud recognition. Making the fallback available improves the overall experience without requiring it.
- The interface isolating OCR providers is a business-directed requirement: the recognition mechanism may change in future phases (different services, improved on-device models) without restructuring the product.
- Any external transmission of captured image data represents a privacy boundary and must be disclosed to the user. This disclosure obligation is recorded in BR-004b and Section 7 of the Business Specification.

---

## Constraints and Obligations Recorded in the Business Specification

- **BR-004a:** On-device recognition is the primary mechanism; it must work without network access.
- **BR-004b:** Network-assisted recognition is optional; the user must be informed on first use; it can be disabled; viewing-history data is never transmitted.
- **Section 7:** Only image data (not viewing history) may be transmitted externally during recognition.
- **OI-05 (open issue):** Cost responsibility for cloud recognition API usage is not yet decided and must be resolved before the Technical Specification for BR-004b is finalized.

---

## Consequences

- The Technical Specification shall define an interface that abstracts the OCR provider, allowing the on-device and cloud implementations to be swapped or extended independently.
- The Technical Specification shall document the confidence threshold below which the fallback is triggered.
- The Technical Specification shall document the specific service used for cloud recognition, the data transmitted, and any cost implications.
- Future phases may replace either implementation without changing the business-level requirements.
