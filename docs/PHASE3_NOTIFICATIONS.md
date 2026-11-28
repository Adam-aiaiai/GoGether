# Phase 3 — interaction notifications and unread indicators

Implemented locally on `feat/member-ui-polish`, following the approved phase 1/2 rules. No push or Firebase deployment is part of this phase.

## Delivery rules

| Action | Recipients |
| --- | --- |
| New question | Event organizer |
| Question reply/follow-up | Question author, target reply author, organizer |
| New public or private review | Event organizer |
| Review reply | Root review author and target reply author |
| Like | No notification |

Recipients are deduplicated and the actor is excluded. Personal blocks suppress interaction delivery in either direction. Private review threads can notify only the reviewer and organizer. Important registration/activity changes remain available despite personal blocks. A direct reply to one's own review does not notify unrelated people.

## Unread and navigation

The My Events icon dot, Notifications count, individual row markers and Interactions tab marker derive from the same permission-filtered notification list. Opening My Events or the inbox does not mark notifications read. Clicking a notification marks that item read; Mark all as read applies to the current account. The other account's unread state is unaffected, and read state survives local restart.

A notification stores separate root and target content IDs. Clicking it opens the conversation and scrolls to the target question/review/reply, with a subtle background highlight. Legacy question notices continue to resolve; old answers without an independent target resolve to their migrated answer. Deleted content gives a placeholder, inaccessible/missing content gives an unavailable message. Personal blocks hide old interaction notices while active; unblocking can reveal their previous unread state. Mark all read also covers the current account's hidden notices.

Anonymous review/reply notices use the event's persisted anonymous alias and no personal avatar. Public notification DTOs do not contain an anonymous actor's user ID. Notification bodies describe the interaction rather than copying review/reply text. Private permissions, deletion and blocks are rechecked when snapshots/conversations are read.

## Persistence and backend

Demo mode merges existing repository notifications with durable delivery records. Reply content and deliveries commit together in `social_v1`; rich review content and delivery records commit together in `checkin_prototype_v1`. Read flags for these records persist in `social_v1`. Existing reviews/replies are not backfilled into new unread notifications. Existing question and activity notices retain their read state.

Snapshot schema 6 adds optional target/display metadata and reads versions 1–6. Legacy answer history remains supported. All unread UI uses `AppSnapshot.unreadNotificationCount` and the same projected list.

Connected mode creates notices in the existing authenticated, idempotent Firestore transaction, alongside the interaction. Request retries do not create duplicate notices. Snapshots recheck current permissions and resolve anonymous display fields; the client never needs to derive anonymous identity. Existing notification/global sync listeners refresh the snapshot. Cloud changes were tested only in local Auth/Firestore/Functions/Storage emulators and require a later authorized deployment to affect production.

The existing course deployment row/storage limits remain in place. These are in-app notifications; device push notifications are outside this phase.

## Verification and manual testing

Backups, build/lint logs, Node/Firebase results, Android test output and final verification metadata are under `../verification/phase3-20261128` in the workspace parent. The backup includes the complete phase 2 source, pre-phase-3 installed APK and simulator app data.

After automated testing, restore the original simulator data and explicitly run `PhaseThreeDemoSetup` to append two separate test activities:

- **Phase 3 - Conversations**: Bob's question, Alice's answer and Bob's follow-up.
- **Phase 3 - Review notifications**: Bob's anonymous public review, Carol's named public review, David's anonymous private review, and replies between Alice and Bob.

Start as Alice and tap My Events → Notifications → Interactions. Check the red dot before entry, the precise conversation destination, and Mark all as read. Switch to Bob to test question/review reply notices, or David for his private review. Existing phase 1/2 activities and user data are preserved.

Phase 4 aggregate stars and review counts in History remain the next stage.

Final local verification (2026-11-28): 242 Kotlin unit tests, 38 Node unit tests, 33 Firebase emulator integration tests and 37 unique Android instrumented tests passed. The final phase 3 run passed all 8 cases, including live badge refresh, precise scrolling, read-state persistence/account isolation and a successful reply followed by an offline snapshot refresh. One initial test assertion was corrected to select a notification by ID instead of assuming list order. Debug build and lint passed (0 errors; 60 non-blocking warnings). The installed APK hash matches the verified build. Original records and preference values were checked after restoration and fixture creation. The emulator is left as Alice on My Events with unread notices ready to inspect.
