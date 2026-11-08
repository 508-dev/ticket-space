/// <reference path="../pb_data/types.d.ts" />
// PocketBase v0.40.6
//
// Creates the Ticket Space collections, fields, rules and indexes exactly as
// specified in docs/architecture.md -> "Backend: PocketBase".
//
// Notes on two places where the PocketBase field model cannot express the
// contract literally (see also the agent report):
//
//   * `priority` is NOT marked `required`, because PocketBase's `required` on
//     a number field means "non-zero" and would reject priority 0 (low). The
//     range (0..3) and integer constraint still apply.
//   * The `created`/`updated` autodate fields are added explicitly to the base
//     collections, because PocketBase v0.23+ no longer adds them by default.
//     The built-in `users` collection already carries them.
//   * PocketBase ships a default `users` auth collection, so it is modified
//     here (rules) rather than created.

const authed = '@request.auth.id != ""';

// The contract says `image/*` for the file fields, but PocketBase v0.40.6
// (mimetype v1.4.15) validates file MIME types by exact match only — the
// `image/*` wildcard matches nothing. Enumerate the concrete image types the
// app can actually upload instead.
const imageMimeTypes = [
  "image/jpeg",
  "image/png",
  "image/gif",
  "image/webp",
  "image/heic",
  "image/heif",
  "image/bmp",
  "image/tiff",
];

migrate(
  (app) => {
    // ------------------------------------------------------------------
    // users (auth)
    //
    // PocketBase ships a default "users" auth collection (with `name`,
    // `created`/`updated` autodate fields and password auth enabled). Instead
    // of recreating it, modify its rules to match the contract: password auth
    // only, public signup disabled (createRule null), update self.
    // ------------------------------------------------------------------
    const users = app.findCollectionByNameOrId("users");
    users.listRule = authed;
    users.viewRule = authed;
    users.createRule = null; // superuser only (public signup disabled)
    users.updateRule = "id = @request.auth.id";
    users.deleteRule = null; // superuser only
    app.save(users);

    // ------------------------------------------------------------------
    // sites
    // ------------------------------------------------------------------
    const sites = new Collection({
      type: "base",
      name: "sites",
      listRule: authed,
      viewRule: authed,
      createRule: null, // superuser only
      updateRule: null, // superuser only
      deleteRule: null, // superuser only
      fields: [
        { type: "text", name: "name", required: true, max: 255 },
        { type: "autodate", name: "created", onCreate: true, onUpdate: false },
        { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
      ],
    });
    app.save(sites);

    const sitesId = app.findCollectionByNameOrId("sites").id;

    // ------------------------------------------------------------------
    // rooms
    // ------------------------------------------------------------------
    const rooms = new Collection({
      type: "base",
      name: "rooms",
      listRule: authed,
      viewRule: authed,
      createRule: authed,
      updateRule: authed,
      deleteRule: authed,
      fields: [
        {
          type: "relation",
          name: "site",
          collectionId: sitesId,
          maxSelect: 1,
          required: true,
        },
        { type: "text", name: "name", required: true, max: 80 },
        {
          type: "file",
          name: "panorama",
          maxSelect: 1,
          maxSize: 10 * 1024 * 1024,
          mimeTypes: imageMimeTypes,
          thumbs: ["480x240"],
        },
        { type: "autodate", name: "created", onCreate: true, onUpdate: false },
        { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
      ],
    });
    app.save(rooms);

    const roomsId = app.findCollectionByNameOrId("rooms").id;

    // ------------------------------------------------------------------
    // room_anchors
    // ------------------------------------------------------------------
    const roomAnchors = new Collection({
      type: "base",
      name: "room_anchors",
      listRule: authed,
      viewRule: authed,
      createRule: authed,
      updateRule: authed,
      deleteRule: authed,
      fields: [
        {
          type: "relation",
          name: "room",
          collectionId: roomsId,
          maxSelect: 1,
          required: true,
          cascadeDelete: true,
        },
        {
          type: "select",
          name: "provider",
          values: ["local", "cloud"],
          maxSelect: 1,
          required: true,
        },
        { type: "text", name: "anchor_id", required: true },
        { type: "json", name: "pose_in_room", required: true },
        { type: "date", name: "expires_at" },
        { type: "autodate", name: "created", onCreate: true, onUpdate: false },
        { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
      ],
      indexes: ["CREATE INDEX idx_room_anchors_room ON room_anchors (room)"],
    });
    app.save(roomAnchors);

    // ------------------------------------------------------------------
    // things
    // ------------------------------------------------------------------
    const things = new Collection({
      type: "base",
      name: "things",
      listRule: authed,
      viewRule: authed,
      createRule: authed,
      updateRule: authed,
      deleteRule: authed,
      fields: [
        {
          type: "relation",
          name: "room",
          collectionId: roomsId,
          maxSelect: 1,
          required: true,
          cascadeDelete: true,
        },
        { type: "text", name: "label", max: 80 },
        { type: "json", name: "pose_in_room" }, // null = unplaced
        { type: "autodate", name: "created", onCreate: true, onUpdate: false },
        { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
      ],
      indexes: ["CREATE INDEX idx_things_room ON things (room)"],
    });
    app.save(things);

    const thingsId = app.findCollectionByNameOrId("things").id;
    const usersId = app.findCollectionByNameOrId("users").id;

    // ------------------------------------------------------------------
    // todos
    // ------------------------------------------------------------------
    const todos = new Collection({
      type: "base",
      name: "todos",
      listRule: authed,
      viewRule: authed,
      createRule: `${authed} && @request.body.created_by = @request.auth.id`,
      updateRule: authed,
      deleteRule: authed,
      fields: [
        { type: "text", name: "title", required: true, max: 200 },
        { type: "text", name: "description", max: 5000 },
        // Not `required`: PocketBase "required" on a number rejects 0, and
        // priority 0 (low) is a valid value per the contract.
        { type: "number", name: "priority", min: 0, max: 3, onlyInt: true },
        { type: "json", name: "tags" },
        {
          type: "select",
          name: "status",
          values: ["open", "done"],
          maxSelect: 1,
          required: true,
        },
        {
          type: "file",
          name: "photos",
          maxSelect: 3,
          maxSize: 10 * 1024 * 1024,
          mimeTypes: imageMimeTypes,
          thumbs: ["320x320"],
        },
        {
          type: "relation",
          name: "room",
          collectionId: roomsId,
          maxSelect: 1,
          required: true,
        },
        {
          type: "relation",
          name: "thing",
          collectionId: thingsId,
          maxSelect: 1,
        },
        {
          type: "relation",
          name: "created_by",
          collectionId: usersId,
          maxSelect: 1,
          required: true,
        },
        {
          type: "relation",
          name: "done_by",
          collectionId: usersId,
          maxSelect: 1,
        },
        { type: "date", name: "done_at" },
        { type: "autodate", name: "created", onCreate: true, onUpdate: false },
        { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
      ],
      indexes: [
        "CREATE INDEX idx_todos_status_priority ON todos (status, priority)",
        "CREATE INDEX idx_todos_room ON todos (room)",
      ],
    });
    app.save(todos);
  },
  (app) => {
    // down: delete in reverse dependency order. The `users` collection is
    // PocketBase's built-in default, so revert its rules instead of deleting it.
    for (const name of ["todos", "things", "room_anchors", "rooms", "sites"]) {
      try {
        const collection = app.findCollectionByNameOrId(name);
        app.delete(collection);
      } catch {
        // already gone
      }
    }

    try {
      const users = app.findCollectionByNameOrId("users");
      users.listRule = "id = @request.auth.id";
      users.viewRule = "id = @request.auth.id";
      users.createRule = "";
      users.updateRule = "id = @request.auth.id";
      users.deleteRule = "id = @request.auth.id";
      app.save(users);
    } catch {
      // already gone
    }
  },
);
