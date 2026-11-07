/// <reference path="../pb_data/types.d.ts" />
// PocketBase v0.40.6
//
// Seeds the single "Home" site record required by docs/architecture.md.

migrate(
  (app) => {
    const sites = app.findCollectionByNameOrId("sites")

    // idempotent guard: don't seed a second "Home" if one already exists.
    let existing = null
    try {
      existing = app.findFirstRecordByFilter("sites", "name = 'Home'")
    } catch {
      existing = null
    }

    if (existing) {
      return
    }

    const record = new Record(sites)
    record.set("name", "Home")
    app.save(record)
  },
  (app) => {
    try {
      const record = app.findFirstRecordByFilter("sites", "name = 'Home'")
      if (record) {
        app.delete(record)
      }
    } catch {
      // already gone
    }
  },
)
