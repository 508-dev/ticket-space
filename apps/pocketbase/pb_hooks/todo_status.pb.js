/// <reference path="../pb_data/types.d.ts" />
// PocketBase v0.40.6
//
// Todo lifecycle hook from docs/architecture.md:
//
//   When a todo's `status` changes to `done`, set `done_by` to the requesting
//   user and `done_at` to now; when it changes back to `open`, clear both.
//
// Clients are never allowed to set `done_by`/`done_at` themselves: the hook
// overrides whatever they submit on every update.

onRecordUpdateRequest(
  (e) => {
    const newStatus = e.record.get("status")
    const original = e.record.original()

    if (newStatus === "done") {
      // done_by is a relation to the "users" collection. A superuser record id
      // is not a valid target, so in that (app-inapplicable) edge case we keep
      // the previously persisted value instead of erroring out.
      const requester = e.auth && !e.auth.isSuperuser() ? e.auth.id : original.get("done_by")
      e.record.set("done_by", requester)
      e.record.set("done_at", new DateTime())
    } else if (newStatus === "open") {
      e.record.set("done_by", "")
      e.record.set("done_at", "")
    } else {
      // status did not change (or is unrecognized): restore the persisted
      // values, ignoring any client-supplied done_by/done_at.
      e.record.set("done_by", original.get("done_by"))
      e.record.set("done_at", original.get("done_at"))
    }

    e.next()
  },
  "todos",
)
