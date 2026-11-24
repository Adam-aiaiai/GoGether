"use strict";
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {validate, fingerprint} = require("./validation");
initializeApp();
exports.coordinate = onCall(async request => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  const uid = request.auth.uid, db = getFirestore();
  let input;
  // Check time inside the transaction, including retries.
  try { input = validate(request.data, 0); }
  catch (error) { throw new HttpsError("invalid-argument", error.message); }
  const hash = fingerprint(input);
  const operation = db.collection("coordinationRequests").doc(uid + "_" + request.data.requestId);
  const eventRef = db.collection("events").doc(input.eventId);
  return db.runTransaction(async tx => {
    const previous = await tx.get(operation);
    if (previous.exists) {
      if (previous.data().hash !== hash) throw new HttpsError("already-exists", "Request ID already used for different content");
      return {id: operation.id};
    }
    const eventDoc = await tx.get(eventRef), event = eventDoc.data(), now = Date.now();
    if (!event) throw new HttpsError("not-found", "Event not found");
    if (event.organizerId !== uid) throw new HttpsError("permission-denied", "Only the organizer can update this event");
    if (event.status !== "OPEN" || now >= event.endAt) throw new HttpsError("failed-precondition", "Event is closed");
    if (input.action === "meeting" && (input.meetingAt <= now || input.meetingAt > event.endAt))
      throw new HttpsError("invalid-argument", "Choose a future meeting time before the event ends");
    const roster = await tx.get(db.collection("registrations").where("eventId", "==", input.eventId));
    const recipients = [...new Set(roster.docs.map(d => d.data())
      .filter(r => ["CONFIRMED", "WAITLISTED"].includes(r.status)).map(r => r.userId))];
    if (recipients.length > 400) throw new HttpsError("resource-exhausted", "This delivery supports up to 400 recipients");
    if (input.action === "announcement") {
      tx.set(db.collection("announcements").doc(operation.id), {id: operation.id, eventId: input.eventId,
        authorId: uid, content: input.content, createdAt: now});
    } else {
      // Patch only D-owned fields; B's title, capacity and event schedule are preserved.
      tx.update(eventRef, {meetingAt: input.meetingAt, meetingLocation: input.location,
        meetingNotes: input.notes, updatedAt: now});
    }
    recipients.forEach(recipient => {
      const ref = db.collection("notifications").doc(operation.id + "_" + recipient);
      tx.set(ref, {id: ref.id, eventId: input.eventId, userId: recipient,
        type: input.action === "announcement" ? "ANNOUNCEMENT" : "MEETING_UPDATED",
        content: input.action === "announcement" ? input.content : `Meeting information updated: ${event.title}. Open the event for details.`,
        createdAt: now, isRead: false, announcementId: input.action === 'announcement' ? operation.id : null});
    });
    tx.set(operation, {hash, createdAt: now});
    return {id: operation.id};
  });
});
const {getStorage}=require('firebase-admin/storage');
const {createService}=require('./service');
const {DomainError}=require('./domain');
exports.gogether=onCall({region:'us-central1',maxInstances:10},async request=>{
 try {return await createService(getFirestore(),getStorage().bucket())(request);}
 catch(error){if(error instanceof DomainError)throw new HttpsError(error.code,error.message);if(error instanceof HttpsError)throw error;console.error('gogether request failed',error.code??error.name);throw new HttpsError('internal','The operation could not be completed. Please retry with the same request ID.');}
});
