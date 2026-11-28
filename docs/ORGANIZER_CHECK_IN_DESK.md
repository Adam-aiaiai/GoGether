# Organizer check-in desk

> 本文记录此前集成／原型版本。当前签到、定位移除和评价资格以 [第一阶段规则](PHASE1_ATTENDANCE.md) 为准。

The connected organizer route now separates the QR desk from the full attendance list. The event-detail and management entry points still use `RepositoryCheckInScreen`, which routes connected repositories to `CloudCheckInScreen`.

## Interface

- The desk shows the current event, time, check-in state and centered QR. Extra actions, including reviews and any saved-attempt recovery, are in the top-right menu.
- `Hide code` replaces the QR with a centered `Show check-in code` action **inside the same reserved slot**. The slot depends on viewport width and text scale, not visibility, loading or token validity. The outer scroll state remains mounted. Attendance, Meeting point and Check-in rules retain their positions when the code is hidden or shown.
- Hiding is not a check-in closure. It stops automatic code requests and removes the displayed token. Previously issued tokens still follow the server's existing expiry rules.
- Attendance previews checked-in people in one horizontal row. On a phone, four people fit; with more people, a partial next item indicates horizontal scrolling. Counts come from the server's attendance response, not event capacity or a fabricated local count.
- `View all` opens the complete roster with search, All / Checked in / Not checked in filters, recorded date/time, refresh, loading and retry. A failed refresh retains the last successful roster. An initial failure is not displayed as zero attendance.
- `Meeting point` opens a sheet with meeting time, address, instructions, the existing maps address target and a GPS binding action. Merely opening or closing it does not update the meeting point.
- `Check-in rules` opens a read-only sheet describing the existing time window, distance, eligibility, refresh and duplicate restrictions. Done closes the sheet.
- Profile data currently provides a name but no avatar photo field. The circles therefore show real participant initials; no invented profile photos or extra profile fields are added.

## Boundaries

`CloudOrganizerCheckInContent`, `CloudOrganizerAttendance` and `CloudOrganizerSheets` are presentation components. `CloudCheckInScreen` continues to own permissions, GPS, code requests, saved-attempt recovery and server calls. The roster refresh is separately callable and keeps its own loading/error state. The existing five-second refresh and ten-second code request intervals remain in the caller.

No backend endpoints, eligibility rules, registration logic, scan direction, review privacy/drafts, shared theme tokens or bottom-navigation styles are changed by this work. Account/event keying remains in place. The organizer can return from the full roster to the desk, and from reviews to the screen that opened them. Direct review entry retains its external Back action. Participants cannot use an attendance deep link to see the organizer's roster.

The standalone local prototype is retained as a separate demo path. This redesign targets the connected organizer path used by the current backend build.

## Verification

`CloudOrganizerDeskUiTest` uses real application composables with an isolated backend fixture. It covers geometry at 400×860, 320×568 with 1.5× text, and 840×1100; horizontal avatar scrolling; sheets; invalid/expired/loading QR states; roster loading, refresh, errors and retry; account/event isolation; and navigation from the normal event entry, including code request cancellation while hidden.

Screenshot fixtures cannot be used to check in to a real event. The test suite does not submit an attendance record, review or GPS binding to a live account. Device screenshots and run logs are stored under `verification/organizer-desk-20261126/`. These checks do not replace a live two-device backend test.

Verified on 2026-11-26 on `feat/member-ui-polish`, working tree based on `946930e`:

- Emulator backend debug build and test APK compiled successfully; 217 JVM tests passed.
- 10 organizer device tests passed, plus 19 participant, cloud review and review-editor regression cases.
- The first new normal-entry test incorrectly looked for activities on Profile. The fixture was corrected to use My Events → Created and activity-linked registrations, then the complete organizer suite passed again.
- Lint completed with zero errors and 46 warnings. No commit or push was made.
- The actual-image gallery is `verification/organizer-desk-20261126/index.html`; `validation.json` identifies the checked APK and logs.
