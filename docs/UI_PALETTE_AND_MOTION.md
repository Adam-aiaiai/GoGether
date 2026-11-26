# Palette and press feedback

Approved on 2026-11-26, on `feat/member-ui-polish`. UI-only changes; no changes to
repositories, endpoints, eligibility, submission rules or navigation destinations.

## Colours

- Primary orange: `#F45B2B` for buttons, links, stars and active navigation icons/text.
  `AppUi.Primary` supplies Compose colours; the native date/time picker accent in
  `res/values/themes.xml` and launcher background match it. The approved orange
  comparison replaces the earlier purple palette; the icon shape is unchanged.
- Bottom navigation: white `#FFFFFF`; no selected-item pill.
- Selected filter/category/review chips: `#FFE5D4`, with `#8B3A17` text,
  without borders or ticks.
- Soft accent surfaces: `#FFF1E7`, with deeper orange `#C94B1B` text where a
  foreground is needed on a tinted surface. Neutral Material surface roles are
  explicit so dialogs, menus and dark mode do not inherit default purple tones.
- Light page surfaces remain white. Success green and error red keep their meaning.
- The check-in success backdrop uses `#FFFCFA` to `#FFF1E7`; the category
  thumbnail uses `#FFE5D4`. The redundant personal attendance sentence is removed;
  its space is retained so the approved card, curve and Done positions do not move.

## Navigation

Inactive icons are gray outlines; the selected icon is filled orange, enlarged to
140% (24 dp to 33.6 dp), with a bold orange label. Label size remains 12 sp with
16 sp line height; navigation height and spacing are unchanged. The 40% size
replaces the earlier 12% emphasis after preview approval. Create retains its
familiar plus symbol. All four
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
- The selected navigation icon's 140% emphasis is separate from press scaling;
  it remains selected and enlarged after the press effect returns to 1.
- Full-row button layout is still 80% of the viewport and normally 52 dp high.
  Scaling is a drawing transform, not a layout change. Compact Join Event/Manage
  controls and outlined Cancel buttons are not included in this motion change.

Android references: [spring animation](https://developer.android.com/develop/ui/compose/animation/customize)
and [interaction handling](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions).

## Orange palette validation (2026-11-26)

- On `feat/member-ui-polish`, based on `5f9c1f2`; presentation changes only.
  No repositories, models, callbacks, layout dimensions or motion settings changed.
- Debug application and test APK builds passed with `-Pbackend=emulator`.
- 17 existing Android checks were exercised: 4 press/navigation, 2 responsive
  button, 3 cross-page UI, 6 review editor/draft/privacy and 2 registration dialogs.
  The first batch passed 16/17. The cancellation test hit Espresso's known
  `RootViewWithoutFocusException` while sending Back at `MvpFlowTest.kt:162`.
  The unchanged-build isolated rerun passed, including cancellation/promotion.
  This is 17 distinct checks passing across the batch and rerun, not one clean
  initial batch. No test failure was hidden by changing business behavior.
- Exact pixels verify orange `#F45B2B` for the action fill and white for bottom
  navigation. Existing 0.94 press/rebound and selected navigation enlargement
  remain covered, as do 80% widths on phones/tablets and compact Join Event.
- Native screenshots were reviewed for Explore, review editor, check-in result,
  shared form/navigation screens and dialogs. Isolated fixtures were used, with
  no real review submission. The app was installed on emulator-5556.
- Logs and the actual screenshot gallery are under
  `verification/orange-palette-20261126/` (locally ignored).
- These palette checks were completed before the later approved 40% navigation
  change and publication. This is a UI check, not a renewed physical-device or
  live-backend verification.

## Approved 40% navigation validation (2026-11-26)

- The production selected-icon scale is now 1.40: a 24 dp icon renders at 33.6 dp
  in both dimensions. Unselected icons stay at 24 dp. Label styling is unchanged.
- Debug app and test APK builds passed with `-Pbackend=emulator`.
- All four `PressBounceUiTest` checks passed on emulator-5556. Navigation checks
  cycle through all four destinations and verify the selected scale, restoration
  of inactive icons, single-click behavior and existing 0.94 press/rebound.
- Additional assertions verify every navigation label remains 12 sp with 16 sp
  line height in the initial state and after each selection. Action colors,
  80%-width buttons at 400 dp and the check-in success receipt remain covered by
  the existing checks in the same class.
- This build uses the production navigation component, with no temporary preview
  source injected. Build logs, test output and native captures are stored under
  `verification/navigation-40-final-20261126/` (locally ignored).
- The approved orange palette, this navigation change, tests and documentation
  are included together for publication on `feat/member-ui-polish`.

## Validation before the orange palette change

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
- At the time of this earlier validation, work was uncommitted on
  `feat/member-ui-polish`; existing workspace changes were preserved. Its gallery is
  `verification/nav-bounce-20261126/index.html`.
