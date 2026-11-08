/// <reference path="../pb_data/types.d.ts" />
// PocketBase v0.40.6
//
// Todo lifecycle hook from docs/architecture.md:
//
//   When a todo's `status` changes to `done`, set `done_by` to the requesting
//   user and `done_at` to now; when it changes back to `open`, clear both.
//
// `done_by`/`done_at` are only touched on an actual status change. Editing an
// already-done todo (title, description, etc.) preserves the existing values.
// Clients can never set these fields themselves — every create/update path
// overrides whatever they submit.

// On create: a todo created already-"done" records the requester + now;
// otherwise both fields are forced empty (client values always ignored).
onRecordCreateRequest((e) => {
  // done_by is a relation to the "users" collection; a superuser record id is
  // not a valid target, so that app-inapplicable edge case falls back to "".
  const requester = e.auth && !e.auth.isSuperuser() ? e.auth.id : "";

  if (e.record.get("status") === "done") {
    e.record.set("done_by", requester);
    e.record.set("done_at", new DateTime());
  } else {
    e.record.set("done_by", "");
    e.record.set("done_at", "");
  }

  e.next();
}, "todos");

// On update: only set/clear on a real status change.
onRecordUpdateRequest((e) => {
  const oldStatus = e.record.original().get("status");
  const newStatus = e.record.get("status");

  if (newStatus === "done" && oldStatus !== "done") {
    // open -> done: record the requester and now. Keep the previous done_by
    // only in the superuser edge case (a superuser id is not a valid target).
    const requester =
      e.auth && !e.auth.isSuperuser() ? e.auth.id : e.record.original().get("done_by");
    e.record.set("done_by", requester);
    e.record.set("done_at", new DateTime());
  } else if (newStatus === "open") {
    // done -> open (or still open): clear both.
    e.record.set("done_by", "");
    e.record.set("done_at", "");
  } else {
    // still done (title/description edit) or any other no-transition case:
    // restore the persisted values, ignoring client-supplied done_by/done_at.
    e.record.set("done_by", e.record.original().get("done_by"));
    e.record.set("done_at", e.record.original().get("done_at"));
  }

  e.next();
}, "todos");
