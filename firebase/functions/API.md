# Gogether callable API v1

All requests use callable `gogether` in `us-central1`, with Firebase Auth credentials. Flat JSON envelope `{action,requestId,...payload}`. Mutations require an 8–128 character `[A-Za-z0-9_-]` requestId and are idempotent per authenticated UID plus payload. Reads do not need requestId. Client identity fields never authorize operations. Server time is milliseconds UTC. Keep one requestId when retrying an uncertain write.

- `snapshot` -> `{snapshot}` with AppSnapshot names; public profiles omit others' email, active public roster + own inactive registrations, own notifications/feedback and own or organizer-authorized checkIns, member-visible announcements, public questions. `serverNow` in response.
- `resolveRequest {requestIdToResolve}` -> `{status:"COMMITTED",action,result}` or `{status:"UNKNOWN"}`. The authenticated UID scopes the lookup; UNKNOWN may still be in flight and never authorizes a new retry ID. COMMITTED contains the exact original action result.
- `ensureProfile`, `updateProfile`: `{name,interests:[Category.name]}` -> `{user}`. ensureProfile returns existing profile without changing it. Email comes from Auth.
- `createEvent`: `{draft:EventDraft}` -> `{event}`; `updateEvent`: `{eventId,draft}` -> `{event}`.
- `join`: `{eventId}` -> `{registration}`; `cancelRegistration`, `cancelEvent`: `{eventId}` -> `{ok:true}`.
- `postAnnouncement`, `askQuestion`: `{eventId,content}` -> `{ok:true,id}`; `answerQuestion`: `{questionId,content}` -> `{ok:true}`.
- `updateMeeting`: `{eventId,meetingAt,location,notes}` -> `{ok:true}`.
- `markRead`: `{notificationId}`; `markAllRead`: `{}` -> `{ok:true}`.

## Attendance and feedback

`fix` is `{latitude,longitude,accuracyMeters,ageMillis,simulated:false}`. Android computes age from elapsedRealtime. Accuracy <=50m, age <=15s; check-in radius 200m. Auth, event times, registration, opaque code expiry and server-held meeting are validated on server. GPS coordinates remain client-reported; this is not hardware location attestation.

- `eventInfo {eventId}` -> `{meeting:null|{latitude,longitude,version,boundAt},serverNow,receipt:null|{sessionId:eventId,userId,checkedInAt},ownFeedback:null|fullFeedback}`.
- `bindMeeting {eventId,fix}` -> `{meeting,serverNow}`; organizer only, invalidates old credentials.
- `issueCheckInCode {eventId,fix}` -> `{code,expiresAt,serverNow}`. Opaque code `GOGETHER-CLOUD-1|...`; client renders it, never supplies trusted decoded claims. Issuer must be within 200m. Code lifetime max30s; check-in window start +/-30min, before event end.
- `submitCheckIn {eventId,code,fix}` -> `{receipt}`. requestId is persistent attempt ID.
- `resolveCheckIn {eventId,attemptId}` -> `{status:"SAVED"|"UNKNOWN",receipt?}`. UNKNOWN never authorizes resubmission with a new ID. If receipt already exists it is SAVED. Missing operation is UNKNOWN because a request might be in flight.
- `attendance {eventId}` -> `{attendees:[{userId,name,checkedInAt:null|number}]}`; organizer only.
- `reviews {eventId}` -> `{reviews:[{id,authorName,anonymous,own,rating,comment,createdAt,details}]}`. Anonymous rows omit author UID, including photo refs.
- `submitFeedback {eventId,rating,comment,details}` -> `{feedback}`; confirmed participant, ended event, once per user/event. details: organization/venue/atmosphere scores 0..5, tags, anonymous, visibility PUBLIC|ORGANIZER_ONLY, photos (max3 upload paths).
- `mediaRead {mediaId}` -> `{base64,contentType}`. Authenticated server checks current ownership/review visibility before reading a photo. No bearer download links.

## Media and subscriptions

Upload immutable images to Storage `covers/{uid}/{UUID}` or `reviews/{uid}/{UUID}`; max 2MiB, image/jpeg, image/png or image/webp. Covers may be read by signed-in users; review upload bytes only by owner and server. Firebase may create download tokens for staging uploads. On submission the server copies the bytes without token metadata into server-only storage and deletes staging, revoking its tokens; committed retries also clean up any staging re-upload. Stored private media never exposes bearer links. Event imageUri is the covers/... Storage path, not content:// or a local file path. Feedback input uses reviews/... upload paths; output replaces them with random opaque media IDs that do not contain UID.

Listen to Firestore `events` and `notifications where userId==auth.uid` to invalidate cached snapshot; writes use callable. Event documents include revision and server-managed meeting binding: snapshot projects Event fields only; direct event listener data must not be used as identity/authority. Switch accounts by stopping listeners and clearing state.

Small-group limits: capacity 2..50; at most 200 active registrations per event and bounded atomic writes. Snapshot rejects datasets exceeding explicit 1000-record per-collection limit rather than silently returning incomplete data. Intended course-sized deployment; paginate before larger deployment.

Submitted review uploads are copied with create-only generation preconditions to a deterministic server-only privateReviewMedia path, then the staging upload is deleted to revoke all old bearer download URLs. A failed submission can reuse its sealed object even when staging is absent. Clients never receive that path and must use mediaRead with opaque IDs.

A successful submitFeedback replay also deletes any re-uploaded original staging objects. If cleanup is temporarily unavailable, it returns unavailable while retaining the committed result; retry the same request ID. resolveRequest performs the same cleanup for a committed review before reporting COMMITTED. Internal requests records contain uid, action, eventId, hash, createdAt, result and, for reviews, reviewUploads; these records are never directly client-readable.
