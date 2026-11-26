# Free-form comments and media controls

Working branch: `feat/member-ui-polish`, based on `946930e`. This change remains uncommitted.

## Scope

The user clarified that the shared text treatment is for free expression, not every input. `AppCommentField` applies to:

- Review main comment, What went well and What could improve.
- Questions, organizer answers and announcements.
- Event descriptions, items/equipment descriptions, additional notes and meeting notes.
- The older check-in review comment entry.

Account/password, profile name, search/filter, event title, location/city, capacity and date/time controls remain distinct from free-form comments. In the subsequent label-placement update, ordinary inputs use `AppOutlinedTextField` with a small grey label always on the top outline, including when empty or focused. Date/time selectors use `AppOutlinedPickerField` with the same outline label and keep opening their existing pickers. Search now also has an outline label. Free-form comments retain their heading above the box. Page colours, action-button rules and bottom navigation are unchanged.

The shared ordinary field keeps the real editable value, password transformation, keyboard options/actions, validation, disabled state and callbacks. Material 3 1.3.2's decoration is held in its minimized-label state; the editor and accessibility value never receive the decoration-only value. No library upgrade or data-layer change is needed.

The shared comment field has a white interior, 1 dp light-grey border (#BFC1C6), 8 dp corners, a purple focused border, and a red invalid state. Character counters, where already applicable, sit at the bottom right inside the field. The component reports invalid input to accessibility services. Callers retain validation and submission callbacks; `maxLength` displays the limit rather than silently trimming existing general comments.

## Review notes

- Overall labels remain: 1 Poor, 2 Not great, 3 Okay, 4 Good, 5 Excellent; no selection says Tap to rate.
- More details remains the sole optional-detail entry. Each tag section contains its own optional text field, so the main review page does not get longer.
- `positiveComment` and `improvementComment` default to empty and allow up to 500 characters each. The editor caps these two new fields at that limit; existing overall-comment validation is unchanged.
- Both local and cloud draft stores retain the notes per account/event. Local submitted records, cloud serialization and the server feedback validator preserve them. Saved-review rendering uses the same existing anonymity and visibility checks.
- Older records without these fields load with empty notes.
- Firebase `functions/domain.js` accepts and validates the new fields. Deploy this server update before using the new fields in live submissions: the previous server validator would omit them. No deployment is performed in this task.

## Media

`AppMediaAddTile` and `AppMediaThumbnail` provide the shared pale-grey dashed add tile, purple plus, 8 dp corners and corner remove control. The preview remains independently clickable from removal.

- Review photos keep the existing three-photo / 10 MB limit, private storage, scope checks and swipe/zoom preview.
- Event cover creation/editing/reuse keeps one image and its existing system picker and upload flow. The selected cover has a preview and remove control; Change cover retains replacement, and cancelling the picker keeps the current image and form contents.
- Existing image fetching is unchanged. `EventImage` accepts presentation-only shape/content-scale parameters, letting cover thumbnails match review photos and the new cover preview show the whole image.
- There is no existing video-upload control in the app; this change does not introduce video support.

## Verification

Results and actual emulator screenshots are recorded in `verification/shared-fields-20261126/`. Tests use isolated fixtures and do not submit reviews or publish events to live accounts.

### Final scoped verification — 26 Nov 2026

- `build-scoped.log`: debug app, test APK and 217 JVM tests passed (zero failures/errors).
- `backend-tests.log`: all 27 local Node tests passed, including optional-note validation, old-field defaults and visibility projection. No live deployment or remote-device claim.
- `device-scoped-tests.log`: all 39 emulator tests passed in the final scoped run. Coverage includes all five rating labels, empty optional notes, 500-character boundary, notes surviving return/reopen, account/event isolation, cloud retry/submitted rendering, local persistence, photo selection/preview/removal, large text/tablet layouts, normal account/profile flows, event drafts/edits and questions/answers.
- An earlier broader run had one new password-test assertion failure (a password keyboard semantic remains marked as a password even when visible). The user then excluded regular inputs; their original implementations were restored and the out-of-scope test removed. Existing connected-account and profile tests pass in the final run. No production authentication behavior was changed.
- Screenshot gallery: `verification/shared-fields-20261126/index.html` (nine actual emulator captures, all links checked). Tests used English-only isolated fixtures; no real review or event was submitted.
- No commit, push or deployment. Existing unrelated working-tree changes were preserved; HEAD remains `946930e` on `feat/member-ui-polish`.

### Outline-label follow-up — 26 Nov 2026

- Ordinary fields now keep a small grey name on the upper outline in empty, filled and focused states. All eight event date/time selectors use that placement too, while free-form fields retain their external headings.
- `verification/outline-labels-20261126/build-final.log`: debug app and instrumentation APK built successfully.
- `device-tests.log`: 18 emulator checks passed, covering account/profile input, search, event editing, date/time pickers, cover controls, review/attendance navigation and screenshots. These use isolated fixtures, not live submissions.
- Visual inspection caught picker outlines wrapping to their contents; the picker now passes its parent width constraints through, preserving full-row and side-by-side widths. After that adjustment, all nine relevant capture/event checks passed again (`device-final.log`).
- Complete current gallery: `verification/outline-labels-20261126/index.html`, 23 screenshots in eight groups. The free-form review and Q&A images are reused because those controls did not change in this follow-up. Empty login fields are included to demonstrate fixed label placement.
- No commit, push or deployment. No repository, backend or navigation behavior changed in this follow-up.
