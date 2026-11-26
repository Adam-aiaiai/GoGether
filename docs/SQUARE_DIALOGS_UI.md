# Registration and review dialogs

Approved UI update on `feat/member-ui-polish`, 2026-11-26.

Only three dialog presentations use `SquareActionDialog`:

| Entry | Dialog | Actions |
| --- | --- | --- |
| A confirmed participant selects Cancel in event details | Cancel your registration? | Keep my place / Yes, cancel |
| A waitlisted participant selects Cancel in event details | Leave the waitlist? | Stay on list / Leave waitlist |
| Write a review → Visible to | Who can see this? | Public review / Organizer only; Done |

Panels are white with 6 dp corners. Registration cancellation and waitlist exit
fit their content, keeping a 24 dp gap before the buttons rather than reserving
empty height. The visibility chooser retains its approved minimum height of 86%
of its width, growing when its content needs it. Width fits the window with 16 dp
outer margins and is capped at 400 dp on large screens.
Long text can increase the height; short windows scroll rather than clip content.
Descriptions use neutral gray `#646870`. Titles and option names use `#26272B`.
Buttons and selected radio controls follow the shared primary, now orange
`#F45B2B`. The earlier purple validation below records the palette at that time.

Actions reuse the shared capsule button and its press animation. The paired keep
action is outlined; confirmation is filled. Done is centered at 80% of the dialog
width, constrained by its inner space. Full-page button proportions are unchanged.
Visibility choices are two plain rows with left radio controls, without colored
containers, decorative icons or illustrations. The full row remains selectable.

Selecting visibility continues to update the existing draft immediately. Done
only closes the chooser; it does not submit the review. Back/outside dismissal,
anonymous state, attachments, draft isolation and submission permissions retain
their existing behavior. Cancellation still invokes the existing registration
callback, including the existing promotion rules. Waitlist-specific wording is
presentation only. The shared legacy ConfirmDialog and all other dialogs remain
unchanged.

## Verification

Actual emulator captures and current build/test logs are saved locally under
`verification/square-dialogs-20261126/`. The registration journeys and review
editor tests use isolated test data, not a signed-in user's backend data.

Verified on the working tree based on `a6f7a19`:

- Final debug app and instrumentation APK builds passed (`final-build.log` and
  `final-test-build.log`).
- All 14 targeted emulator checks passed on the final layout (`final-device-tests.log`).
  These include the two registration journeys, six review editor cases, four
  privacy/photo cases and two draft cases.
- Keep/Stay and Back preserve the registration or queue. Outside taps dismiss;
  taps on blank space inside the panel do not dismiss. Confirmed cancellation
  still promotes the next waitlisted participant, and leaving the waitlist removes
  only the current participant.
- Visibility changes retain anonymous state and photos in the same draft. Done
  and Back close the chooser without submitting. Phone, tablet and 1.5x text
  checks retain access to controls; account/event isolation remains covered.
- Three full-screen, unedited emulator captures are in `screenshots/01-cancel-registration.png`,
  `02-leave-waitlist.png` and `03-review-visibility.png`. Pixel inspection confirmed
  the rendered primary `#746097`, description gray `#646870` and white panel fill.
- No commit or push was performed; earlier uncommitted button changes were retained.

### Content-sized confirmations (2026-11-26 follow-up)

The user approved the visibility chooser but requested less empty space in the
two short confirmations. Those two now opt into `compact = true`, removing their
minimum height. Their width, corners, colors, text, buttons and callbacks are
unchanged. The visibility chooser uses the same height and layout as before.

- Both APK builds passed (`verification/dialog-height-20261126/build.log`).
- All three focused registration/waitlist/visibility journeys passed on rerun
  (`recheck-tests.log` in that directory). The first batch had an Espresso root
  focus timing failure when sending Back; the same unchanged build passed all
  three on rerun. No application workaround was added for the test failure.
- Updated screenshots are in that directory's `screenshots` folder. A pixel
  comparison of the visibility dialog interior against the previous capture
  found no differences. Background event times are test data and may change.
- No commit or push was performed.
