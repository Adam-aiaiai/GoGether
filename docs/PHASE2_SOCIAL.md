# Phase 2 — conversations, privacy and moderation

Local implementation on `feat/member-ui-polish`, based on `10d07ac` plus the completed phase 1 work. No remote deployment is part of this stage.

## User flows

- Event → Questions: the question author and organizer can exchange multiple replies while the event is open. Every reply has its own ID and target. Threads use two visual levels.
- Event review → Replies & actions: reply to a review or reply, like/unlike the review, delete your own content, report another author's content, or block its author. A confirmation precedes deletion and blocking. Text is limited to 100 characters.
- Profile → account menu → Privacy & reports: manage personal blocks and registration restrictions separately; inspect reports and outcomes.
- Demo profile `Project maintainer (Demo)` has a report queue in the same page. A maintainer records a decision and a reason: dismiss or remove. Organizers have no authority to delete other users' reviews.

## Permissions and data

Question roots and review roots remain separate. Replies inherit the root's event and visibility. Private review conversations are available only to the reviewer and organizer. A report does not remove content by itself. Maintainers can inspect the reported text in their queue; decisions retain the moderator ID, timestamp and outcome internally.

Personal blocks hide content and restrict direct replies/likes in both directions. Registration restrictions apply to all activities owned by that organizer, including joining a waitlist and both cancellation/capacity-driven promotions. Existing confirmed registrations and historical attendance remain intact. Unblocking does not reinstate registrations or trigger a promotion.

The UI passes opaque content IDs for anonymous author blocks and reports. Public conversation DTOs contain no user IDs. Anonymous names are allocated and persisted per event/user, remain consistent in review replies and survive deletion/review replacement. Organizer reviews are named and marked Organizer. Historical anonymous organizer reviews retain anonymity; the existing phase 1 restriction prevents new ones.

Deletes are tombstones. Existing replies remain readable; deleted roots remain discoverable in the review list, and replies to a deleted root are closed. Deleted reviews no longer count as the user's active review. A fresh review uses a new public ID and the same anonymous alias. Its predecessor remains archived for replies and reports. Published images from the predecessor are inaccessible to other viewers, including after replacement.

Local social changes use one committed SharedPreferences document; local review bodies remain in their existing privacy store. Legacy question answers migrate once to independent replies. Snapshot version 5 also preserves answers posted through the older repository API. Snapshot versions 1–4 remain readable. Deleting a legacy core review is respected by the attendance/history bridge.

Firebase social changes run in the existing authenticated idempotent callable transaction. `social/state` and `feedbackHistory` are denied to direct clients by the default Firestore rule. Social state is bounded to 800 KB for this course deployment; larger deployments need partitioning/pagination. No Firebase rules or functions have been deployed during this task.

## Maintainer authorization

Connected mode uses the trusted Firebase Auth custom claim `maintainer: true`, verified by the callable service. Ordinary profile fields, client payloads and organizer status cannot grant this role. The production project owner must assign this claim using an Admin SDK environment they control; no production account is granted the claim here. Emulator integration tests provision a disposable local test maintainer.

## Stage boundaries

Phase 1 attendance/review eligibility remains enforced. New reply/review notifications and unified unread indicators are phase 3. Public aggregate scores, excluding organizer/private/deleted reviews, and History stars/counts are phase 4. General migration and full regression remain phase 5; migrations required for phase 2 are included now.

## Local verification

See `../verification/phase2-20261128` in the workspace parent for source/APK/data backups and test logs. Validation covers Kotlin policy tests, Firebase callable tests across authenticated users, emulator UI flows, persistence and previous attendance/privacy behavior. The final counts are recorded in `verification.json` after the checks finish.

Final checks (2026-11-28): 235 Kotlin unit tests, 36 Node unit tests, 29 Firebase emulator integration tests, and 37 unique Android instrumented tests passed. Debug build and lint passed; lint retains existing non-blocking warnings. The 37-test result merges the main run with focused reruns recorded in `verification.json`. The old activity-switch test was updated to use the current organizer summary and More-menu attendance entry. No code was pushed and no backend was deployed.

Manual fixtures (added after restoring the original simulator data):

- `Phase 2 - Questions`: Bob's question, Alice's earlier answer, and a Bob follow-up. Alice/Bob/Carol are confirmed; David is waitlisted.
- `Phase 2 - Reviews`: ended, checked-in activity with Bob's anonymous review, Carol's named review, and Alice's organizer review. Use Replies & actions.
- `Phase 2 - Private feedback`: Carol's anonymous review, visible only to Carol and Alice, including its replies.

The simulator starts with Bob. Switch profiles through Profile → account menu. `Project maintainer (Demo)` has a pending fixture report to inspect under Privacy & reports. These fixtures do not alter existing activities or registrations.
