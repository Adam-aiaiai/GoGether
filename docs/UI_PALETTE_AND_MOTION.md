# Palette and press feedback

Approved on 2026-11-26, on `feat/member-ui-polish`. UI-only changes; no changes to
repositories, endpoints, eligibility, submission rules or navigation destinations.

## Colours

- Primary purple: `#746097` for buttons, links, stars and active navigation icons/text.
  `AppUi.Primary` supplies Compose colours; the native date/time picker accent in
  `res/values/themes.xml` matches it. Do not restore the old vivid purple.
- Bottom navigation: option C, `#FAF5F9`; no selected-item pill.
- Selected filter/category/review chips: `#EADACB`, without borders or ticks.
- Light page surfaces remain white. Success green and error red keep their meaning.
- The check-in success illustration area uses near-white to `#ECD9ED`; the category
  thumbnail uses `#EADACB`. The redundant personal attendance sentence is removed;
  its space is retained so the approved card, curve and Done positions do not move.

## Navigation

Inactive icons are gray outlines; the selected icon is filled purple, enlarged to
112%, with a bold purple label. Create retains its familiar plus symbol. All four
destinations retain their original click handlers. Subpages keep the selected
parent destination highlighted; E check-in pages still have no bottom navigation.

## Press and release

`rememberPressScale` observes the existing control's `InteractionSource`; it adds
no extra pointer handler or click callback. Navigation items and filled
`PageActionButton` controls use it.

- Press: scale the rendered control to `0.94` in 80 ms and reduce its shadow.
- Release: spring back to `1`, stiffness `260`, physical damping `12`, unit mass.
  Compose accepts a **damping ratio**, so the supplied coefficient is converted:
  `12 / (2 * sqrt(260)) = 0.3721042`. It must not be passed as `dampingRatio = 12`.
- Fast taps still reach the pressed state before release. Animation never delays
  the action callback. A button that navigates away disappears normally; the app
  does not hold up navigation to finish its animation.
- Cancelled gestures spring back without clicking; disabling a pressed control
  resets its scale and removes its shadow. Repeated presses interrupt the old
  animation instead of stacking animations.
- The selected navigation icon's 112% emphasis is separate from press scaling;
  it remains selected and enlarged after the press effect returns to 1.
- Full-row button layout is still 80% of the viewport and normally 52 dp high.
  Scaling is a drawing transform, not a layout change. Compact Join Event/Manage
  controls and outlined Cancel buttons are not included in this motion change.

Android references: [spring animation](https://developer.android.com/develop/ui/compose/animation/customize)
and [interaction handling](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions).

## Validation

`PressBounceUiTest` exercises real touch down/up/cancel events and the Compose
animation clock. It checks 0.94 compression, overshoot, settling, fast taps,
disabled-state recovery, one click per gesture, exact palette pixels, navigation
selection/enlargement, and the check-in result's retained facts and Done action.
The existing `PageActionButtonLayoutTest`, `CloudParticipantCheckInUiTest`, and
`UiConsistencyJourneyTest` cover responsive widths and unchanged flows. Execution
results and native captures are stored locally under
`verification/nav-bounce-20261126/`.

Verified in this workspace on 2026-11-26:

- Debug app and test APK builds: passed, including the final native-picker accent.
- JVM unit tests: 217 passed, 0 failed, 0 skipped.
- Direct Android instrumentation: 21 passed (4 motion, 2 responsive-button,
  12 participant check-in, 3 UI journey tests) on emulator-5556.
- Lint: 0 errors, 46 warnings; a clean lint report is not claimed.
- The participant account-switch regression previously clicked an obsolete
  review-page Check-in tab. It now follows the page's Back action, checks the
  callback, reopens check-in, and checks that the waitlisted account still has no
  receipt or enabled scan action. Production navigation behavior was unchanged.
- Touch tests use controlled fixtures and the animation clock. Screenshots are
  native Compose captures; this is not a physical-device or live-backend audit.
- Work remains uncommitted on `feat/member-ui-polish`; existing workspace changes
  were preserved. The local screenshot gallery is
  `verification/nav-bounce-20261126/index.html`.
