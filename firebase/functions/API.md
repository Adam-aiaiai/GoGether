# Gogether callable API — attendance v2

All requests use callable `gogether` in `us-central1`, with Firebase Auth credentials. Flat JSON envelope `{action,requestId,...payload}`. Mutations require an 8–128 character `[A-Za-z0-9_-]` requestId and are idempotent per authenticated UID plus payload. Reads do not need requestId. Client identity fields never authorize operations. Server time is milliseconds UTC. Keep one requestId when retrying an uncertain write.

- `snapshot` -> `{snapshot}` with AppSnapshot names; public profiles omit others' email, active public roster + own inactive registrations, own notifications/feedback and own or organizer-authorized checkIns, member-visible announcements, public questions. `serverNow` in response.
- `resolveRequest {requestIdToResolve}` -> `{status:"COMMITTED",action,result}` or `{status:"UNKNOWN"}`. The authenticated UID scopes the lookup; UNKNOWN may still be in flight and never authorizes a new retry ID. COMMITTED contains the original action result. An invalidated prior-cycle check-in returns `{status:"INVALIDATED"}`; retries of that submit request are rejected.
- `ensureProfile`, `updateProfile`: `{name,interests:[Category.name]}` -> `{user}`. ensureProfile returns existing profile without changing it. Email comes from Auth.
- `createEvent`: `{draft:EventDraft}` -> `{event}`; `updateEvent`: `{eventId,draft}` -> `{event}`.
- `join`: `{eventId}` -> `{registration}`; `cancelRegistration`, `cancelEvent`: `{eventId}` -> `{ok:true}`.
- `postAnnouncement`, `askQuestion`: `{eventId,content}` -> `{ok:true,id}`; `answerQuestion`: `{questionId,content}` -> `{ok:true}`.
- `updateMeeting`: `{eventId,meetingAt,location,notes}` -> `{ok:true}`.
- `markRead`: `{notificationId}`; `markAllRead`: `{}` -> `{ok:true}`.

## Attendance and feedback

Attendance uses authenticated identity, the current confirmed registration cycle, event status, server time and a short-lived QR token. Device coordinates and the `bindMeeting` action have been removed. Text city/address/meeting notes remain.

- `eventInfo {eventId}` -> `{checkInReady,serverNow,receipt,ownFeedback}`. A receipt contains `{sessionId:eventId,userId,checkedInAt,registrationKey,organizer}`. Inactive or previous-cycle attendance is returned as null.
- `issueCheckInCode {eventId,confirmArrival:true}` -> `{code,expiresAt,serverNow,receipt}`. The first successful issue atomically records the organizer's arrival. Refreshes preserve the original receipt; confirmation can then be omitted. Opaque code format is `GOGETHER-CLOUD-2|...`; old versions are rejected. Maximum TTL is 30 seconds, within start ±30 minutes and before event end.
- `submitCheckIn {eventId,code,registrationKey}` -> `{receipt}`. `registrationKey` is the current confirmed registration's `id + ":" + queueOrder`, captured before scanning. A cancelled/rejoined registration requires a fresh scan. Organizer arrival uses code issuance instead of scanning.
- `resolveCheckIn {eventId,attemptId,registrationKey}` -> `{status:"SAVED",receipt}` / `{status:"UNKNOWN"}` / `{status:"NOT_SUBMITTED"}`. An old or missing cycle is invalidated. UNKNOWN never grants permission to submit a new request.
- `attendance {eventId}` -> `{attendees:[{userId,name,avatarUri,checkedInAt,organizer}]}`; organizer only. Lists current confirmed registrations and current-cycle receipts. Display the organizer separately and exclude that row from participant pending/attendance counts.
- `submitFeedback {eventId,rating,comment,details}` -> `{feedback}`. After event end (or explicit completion), only current confirmed registrations with valid check-ins may review once. Cancelled events cannot be reviewed. Organizer reviews must be named. New reviews carry `eligibilityVersion:2`, `registrationKey`, and `organizerReview`; public aggregates exclude organizer reviews, private/deleted reviews and unverifiable attendance.

- `reviews {eventId}` -> `{reviews:[{id,authorName,anonymous,own,rating,comment,createdAt,details}]}`. Anonymous rows omit author UID, including photo refs.
- `mediaRead {mediaId}` -> `{base64,contentType}`. Authenticated server checks current ownership/review visibility before reading a photo. No bearer download links.

## Media and subscriptions

Upload immutable images to Storage `covers/{uid}/{UUID}` or `reviews/{uid}/{UUID}`; max 2MiB, image/jpeg, image/png or image/webp. Covers may be read by signed-in users; review upload bytes only by owner and server. Firebase may create download tokens for staging uploads. On submission the server copies the bytes without token metadata into server-only storage and deletes staging, revoking its tokens; committed retries also clean up any staging re-upload. Stored private media never exposes bearer links. Event imageUri is the covers/... Storage path, not content:// or a local file path. Feedback input uses reviews/... upload paths; output replaces them with random opaque media IDs that do not contain UID.

Listen to Firestore `events` and `notifications where userId==auth.uid` to invalidate cached snapshot; writes use callable. Event documents include revision and text meeting information: snapshot projects Event fields only; direct event listener data must not be used as identity/authority. Switch accounts by stopping listeners and clearing state.

Small-group limits: capacity 2..50; at most 200 active registrations per event and bounded atomic writes. Snapshot rejects datasets exceeding explicit 1000-record per-collection limit rather than silently returning incomplete data. Intended course-sized deployment; paginate before larger deployment.

Submitted review uploads are copied with create-only generation preconditions to a deterministic server-only privateReviewMedia path, then the staging upload is deleted to revoke all old bearer download URLs. A failed submission can reuse its sealed object even when staging is absent. Clients never receive that path and must use mediaRead with opaque IDs.

A successful submitFeedback replay also deletes any re-uploaded original staging objects. If cleanup is temporarily unavailable, it returns unavailable while retaining the committed result; retry the same request ID. resolveRequest performs the same cleanup for a committed review before reporting COMMITTED. Internal requests records contain uid, action, eventId, hash, createdAt, result and, for reviews, reviewUploads; these records are never directly client-readable.

## Phase 2 social actions

- `socialRead {eventId? , rootId?}`: event roots, a complete two-level conversation, or (without either ID) the caller's blacklist/report dashboard. DTOs use opaque content IDs, anonymous labels and permission booleans; they omit author IDs. Deleted content has a placeholder. A private root's access rule also applies to every reply.
- `socialMutate {operation, ...}` requires the normal stable `requestId` and supports:
  - `reply {contentId,content}` — creates a new reply referencing the chosen target and root.
  - `like {contentId,liked:boolean}` — desired state, not a toggle, so retries cannot add duplicate likes.
  - `delete {contentId}` — author-only tombstone.
  - `report {contentId,reason}` — one pending report per caller/content.
  - `blockUser {contentId|targetUserId,kind:PERSONAL|REGISTRATION}` — anonymous targets resolve on the server. Only an organizer can own registration restrictions.
  - `unblock {blockId}` — owner-only removal; no automatic registration changes.
  - `resolveReport {reportId,resolution:DISMISSED|REMOVED,outcome}` — requires the trusted Auth custom claim `maintainer:true`; records an audit result.

The older `answerQuestion` endpoint now appends independent replies. Existing stored single answers migrate on read. A deleted review may be replaced with a new public ID; the old thread stays archived and its media cannot be fetched by other viewers. Review reads return a stable event-local anonymous name and an `organizer` flag. Direct reads/writes of internal social records remain denied.

## Phase 3 interaction notifications

`Notification` adds optional `contentId`, `rootId`, `actorName`, and `actorAvatarUri`. Interaction types are `QUESTION_ASKED`, legacy `QUESTION_ANSWERED`, `QUESTION_REPLIED`, `REVIEW_POSTED`, and `REVIEW_REPLIED`. The display metadata contains no anonymous actor ID. Clients must render the provided display name/avatar and navigate using the opaque content ID, rather than looking up a real identity from a question or review.

New question/review notices go to the organizer; question replies go to the question author, target author and organizer; review replies go to the review author and target author. The actor is excluded and recipients are deduplicated. Private-root permissions and personal blocks apply to both delivery and snapshot projection. Activity cancellation/changes remain available. Likes do not notify.

Notices are committed in the same idempotent transaction as the mutation. `snapshot` returns only the caller's permission-filtered notices, with safe placeholders for missing/deleted/inaccessible targets; it retains read state. `markRead` checks ownership. `markAllRead` covers the caller's stored notices, including currently hidden interactions, subject to the existing 400-row mutation bound. Opening a screen is not a read operation.


## Phase 4 public rating totals

`snapshot.eventRatings` is an array of `{eventId,ratingSum,reviewCount}`. `reviews` also returns `ratingSummary: {ratingSum,reviewCount}` alongside the caller's access-filtered reviews. The client displays `ratingSum / reviewCount` to one decimal; a zero count has no average. A missing summary is unknown, not zero.

The server uses current public participant reviews from completed/ended, non-cancelled events. It checks current confirmed registration, valid check-in, review/check-in timestamps and the review registration key when present. Organizer/private/deleted/moderator-removed/unverifiable/invalid reviews are excluded. Anonymous public reviews count. Only the latest current review per user contributes; historical archives, replies, likes, drafts and dimension ratings do not contribute. Personal blocking does not change public totals.

Both read endpoints calculate within a Firestore read transaction using the same policy; no public score can be supplied by a client. Existing global sync invalidation refreshes totals after submission, deletion, replacement or moderation. The snapshot response carries aggregate data without reviewer identities or private scores.


## Legacy question and review compatibility

`answerQuestion` retains `answer`, `answeredBy`, and `answeredAt` in the question snapshot and appends an entry to `replyHistory` (`id`, `authorId`, `content`, `createdAt`). The reply, question history, and notice commit together. Replaying the same request does not add another reply or notice. Single-answer records migrate with a stable `answer-<questionId>` ID.

Question items returned by event-scoped `socialRead` include an optional `latestAnswer` safe reply DTO. Only active organizer replies visible to the requester qualify; deleted and blocked content is excluded. Use this projection for unanswered counts and answer previews, and the thread response for complete conversations.

Older reviews with absent optional details remain readable. An absent visibility field keeps the historical public default; an unrecognized explicit value is private to the author and organizer and is excluded from public ratings. The same check protects review conversations and media. Read compatibility does not rewrite the original review.
