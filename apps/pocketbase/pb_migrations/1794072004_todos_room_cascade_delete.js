/// <reference path="../pb_data/types.d.ts" />
// PocketBase v0.40.6
//
// Enables cascade delete on `todos.room` so that deleting a room removes its
// todos together with the room. `things.room` and `room_anchors.room` already
// cascade (see 1794072000_initial_collections.js); this brings `todos` in line
// so a room delete is fully self-contained.

migrate(
  (app) => {
    const todos = app.findCollectionByNameOrId("todos");
    const room = todos.fields.getByName("room");
    room.cascadeDelete = true;
    app.save(todos);
  },
  (app) => {
    const todos = app.findCollectionByNameOrId("todos");
    const room = todos.fields.getByName("room");
    room.cascadeDelete = false;
    app.save(todos);
  },
);
