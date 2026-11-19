"use strict";
const {createHash} = require("node:crypto");
function validate(data, now) {
  if (!data || !/^[a-zA-Z0-9_-]{1,128}$/.test(data.eventId || "") ||
      !/^[a-zA-Z0-9_-]{8,128}$/.test(data.requestId || "")) throw Error("Invalid event or request ID");
  if (data.action === "announcement") {
    if (typeof data.content !== "string" || !data.content.trim() || data.content.trim().length > 500)
      throw Error("Use 1–500 characters");
    return {action: data.action, eventId: data.eventId, content: data.content.trim()};
  }
  if (data.action === "meeting") {
    if (!Number.isSafeInteger(data.meetingAt) || data.meetingAt <= now ||
        typeof data.location !== "string" || data.location.trim().length < 3 || data.location.trim().length > 150 ||
        typeof data.notes !== "string" || data.notes.length > 1000) throw Error("Invalid meeting information");
    return {action: data.action, eventId: data.eventId, meetingAt: data.meetingAt,
      location: data.location.trim(), notes: data.notes.trim()};
  }
  throw Error("Unknown action");
}
const fingerprint = value => createHash("sha256").update(JSON.stringify(value)).digest("hex");
module.exports = {validate, fingerprint};
