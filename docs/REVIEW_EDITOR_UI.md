# Review editor presentation

The Member E review editor is shared by the cloud and local adapters. Its reference is the two-screen Write a review / Review details mockup approved on 2026-11-26.

- Main: event cover/title, organizer and start time, overall stars, one More details / Optional entry, comment/count, up to three photos, anonymous switch and visibility chooser.
- The visibility chooser now uses the approved white, near-square dialog with gray explanation, left radio controls and a filled capsule Done. See [dialog presentation](SQUARE_DIALOGS_UI.md). Its draft and visibility behavior is unchanged.
- More details appears directly below the overall rating. It opens the shared detail screen from the top on every visit: activity summary, Specific ratings, What went well, then What could improve. The former separate entry below the comment is removed. Organization, Venue and Atmosphere each occupy a single row. Existing positive and improvement tag IDs and choices are retained; selected chips use the existing borderless pale-orange style. Each tag group now has an optional free-form comment field below it (up to 500 characters).
- Done and Back return from details to the main form without submitting. Draft edits continue to be saved by the existing account/event-scoped adapter. The detail screen has no submit action.
- Detail Done uses the shared centered 80%-of-viewport capsule action (52 dp minimum height), including its standard label and press animation. Submit review remains compact beside draft status, retains its 14 sp bold label, and shares the capsule styling within that existing row. The former content-width, small-corner exception is superseded.
- Main and detail scroll positions are independent. The footer remains reachable above the keyboard; narrow screens and larger fonts scroll naturally.
- The menu retains access to event reviews and clearing optional ratings. Existing local organizer attendance and unresolved-check-in actions remain available in that menu.
- Score range, eligibility, review visibility, uniqueness rules and backend routing are unchanged. Optional `positiveComment` and `improvementComment` fields extend draft/submitted-review storage and the cloud payload, with empty defaults for older records. The editor is English-only.
- Cloud drafts now reuse the existing private local-photo strip/preview instead of the old separate image dialog. Submitted cloud images still use the protected media endpoint.

## Verification

Run ReviewEditorNavigationTest, FeedbackLayoutTest, FeedbackDraftUiTest, FeedbackPrivacyUiTest, RepositoryFeedbackIntegrationTest and CloudAttendanceUiTest. Photo and navigation regression tests cover adjacent E paths. All backend submissions in these tests use isolated fakes. The visual fixture is a licensed stock photo in androidTest/assets; it is not a production asset or a real GoGether event photo.

Verified on 2026-11-26 on `feat/member-ui-polish`, working tree based on `946930e`:

- Debug app and instrumentation APK builds passed.
- JVM unit suite: 217 tests, zero failures/errors.
- Final emulator run: 29 tests passed (the six classes above plus FeedbackPhotoPreviewTest, AttendanceEntryNavigationTest and UiConsistencyJourneyTest). This covers both optional entries, same-row dimension labels/stars, return without submission, image preview/removal, privacy choices, draft restoration, account/event isolation, small-screen/large-text access, attendance routing and unchanged check-in Done behavior.
- During adaptation, an old title assertion and a helper that tried to click a prefetched, unplaced lazy-list node failed. The assertions/navigation helper were corrected; the full final run passed. No production privacy or eligibility rules were changed to make tests pass.
- Screenshot gallery: `verification/review-two-level-20261126/index.html`; raw images in its `screenshots` directory. These are emulator-rendered app views with isolated test data, not generated mockups.
- Final device log: `verification/review-two-level-verified-tests.log`. Build log: `verification/review-two-level-build.log`. Unit XML: `app/build/test-results/testDebugUnitTest/`.
- This verification does not claim live-server or two-device validation. No real user review was submitted, and no commit or push was performed.

### Earlier two-entry positions check (superseded by the single entry)

- Each entry requests its own scroll position before the detail screen is laid out. The final tags section has a viewport-sized minimum height, so it can align at the top on both phone and tablet without adding a separate empty trailing item. Longer tags content still scrolls normally.
- Both APK builds passed. Twelve relevant UI cases were exercised across ReviewEditorNavigationTest, FeedbackLayoutTest, FeedbackDraftUiTest and FeedbackPrivacyUiTest. One existing screenshot step used a now-obsolete list index; it was changed to find the named section. The other eleven cases passed on the first run, and all three FeedbackLayoutTest cases passed on the targeted rerun.
- Coverage includes distinct starting positions, reopening either entry after scrolling, shared selections, Done/system Back without submission, draft restoration, account/event isolation, photos/privacy, tablet and 320 dp large-text layouts.
- Logs: `verification/review-optional-anchors-build.log`, `verification/review-optional-anchors-tests.log`, `verification/review-optional-anchors-layout-recheck.log`.
- Updated screenshot comparison: `verification/review-optional-anchors-20261126/index.html`.

### Single Optional entry (current, 2026-11-26)

- The user approved replacing the two entries with one More details / Optional row below the overall rating. The row below the comment is removed. Every visit opens the full detail page at the top; its previous section-specific minimum-height padding is no longer needed and was removed.
- Debug and instrumentation APK builds passed. All 12 tests in ReviewEditorNavigationTest, FeedbackLayoutTest, FeedbackDraftUiTest and FeedbackPrivacyUiTest passed in one final run.
- The navigation test checks that exactly one Optional entry exists between the overall stars and comment, all dimension/tag choices remain accessible, returning/reopening retains edits, no review is submitted by Done/Back, and phone/tablet/large-text layouts work. Draft isolation, photos and privacy regression cases passed as well.
- Logs: `verification/review-single-optional-build.log` and `verification/review-single-optional-tests.log`.
- Current emulator screenshot gallery: `verification/review-single-optional-20261126/index.html`. No commit or push was performed.

### Optional free-form notes and shared media controls (current)

What went well and What could improve now each include an optional 500-character field beneath their tags. The main page keeps one Optional entry and its previous length. Draft and submitted storage, cloud payload decoding and server validation include both fields; old records default to empty. See `COMMENT_AND_MEDIA_CONTROLS.md` for the narrowed free-form-only scope, server deployment prerequisite and final verification (217 JVM, 27 local backend and 39 emulator tests passed). Updated actual screenshots: `verification/shared-fields-20261126/index.html`.
