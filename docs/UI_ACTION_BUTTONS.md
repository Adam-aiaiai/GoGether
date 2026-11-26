# Full-row action buttons

The UI polish branch uses `PageActionButton`, `OutlinedPageActionButton` and
`TonalPageActionButton` for
standalone actions across A–E. Their reference is the check-in result's **Done**
button. This is a presentation change; click handlers, permissions, enabled states,
data sources and navigation remain with the existing screens.

- Center the button in its row. All full-row actions, including Done, use
  80% of the available app viewport width, leaving 10% on each side.
  Calculate the target width once in the theme, before individual page padding.
  Do not use a fixed width, fixed side margins or a phone-size maximum on tablets.
  A narrower pane or sheet may constrain a button to avoid overflowing its bounds.
- Use a capsule shape and a 52 dp minimum height. All ordinary labels use 16 sp,
  bold, centered text. Long labels wrap inside the same width; enlarged system
  text can increase height instead of clipping the action.
- Filled actions retain purple fill and white text. Outlined actions retain their
  outline and purple text. Disabled colors are shared within each style. The
  notifications action retains its original tonal colors with the shared geometry.
- Small actions alongside other content, including **Join Event**, **Join Waitlist**,
  **Manage** and registration **Cancel**, retain their existing layout. Text links,
  icon buttons, tabs, filters and photo tiles also retain their existing treatment.
- Light-theme page background and surface are white. Card colors and the
  `surfaceContainer` used by the bottom navigation are unchanged. The existing
  system dark-theme behavior is retained.

Use these components for future standalone actions rather than duplicating width,
shape or label settings in individual screens.

## Initial validation (2026-11-26)

- Debug application and instrumentation APK builds passed with the emulator backend.
- All 217 JVM tests passed. Android lint reported no errors and 44 warnings.
- 27 targeted device tests passed in two completed batches (13 and 14 tests).
  Coverage includes check-in results and navigation, equal button dimensions,
  account forms, profile editing, event creation and meeting changes, registration
  conflicts, questions and replies, reviews, small screens, landscape and large text.
- A first device run was interrupted when the emulator disconnected. Both final
  batches completed on the recovered emulator; the interrupted run is not used
  as the final test result.
- Native screenshots are under `verification/button-style-20261126/`; build and
  runner logs are under `verification/button-gallery-20261126/` (local artifacts).
  The device tests use test data and do not constitute live-backend or two-phone
  verification.

## Responsive proportion (2026-11-26)

The latest requirement supersedes the intermediate fixed-width version. Preserve
the shared 80% proportion on phones and tablets, with 52 dp normal text height:

| App viewport width (dp) | Action width (dp, rounded) | Width ratio |
| --- | --- | --- |
| 320 | 256.0 | 80% |
| 360 | 288.0 | 80% |
| 400 | 320.0 | 80% |
| 600 | 480.0 | 80% |
| 840 | 672.0 | 80% |
| 1024 | 819.2 | 80% |

`PageActionButtonLayoutTest` compares Done with filled, outlined, disabled and
tonal actions on real screen composables at these widths. It also checks on phones
and tablets that Join Event remains compact and right aligned, with the same size
and horizontal position before and after changing the full-row sizing rule within
each viewport. The entire `EventActionArea` function is unchanged from HEAD,
including Join Event, Join Waitlist, Manage and registration Cancel.

Before the 80% adjustment, both APK builds passed. The two responsive layout tests passed at the six widths
above; 20 other targeted UI regressions passed for check-in, profile saving,
reviews, registration conflicts and notifications. A first compact-width assertion
compared text measurements across different ForcedSize densities; the final test
compares old/new sizing in the same viewport, and both layout tests were rerun to
completion. The short action source remains unchanged.

The earlier comparison captures are under `verification/button-responsive-20261126/`.
Their layout runner report is
`verification/button-gallery-20261126/responsive-layout-final-tests.log`.
These are simulated phone/tablet viewports, not physical tablet testing.

After setting the ratio to 80%, both APK builds and all three focused device tests
passed: the six phone/tablet widths, compact Join Event isolation, and the
scan/result/Done navigation flow. See
`verification/button-gallery-20261126/button-80-device-tests.log`.
