/// <reference path="../pb_data/types.d.ts" />
// PocketBase v0.40.6
//
// Locks `todos.created_by` on update. The create rule already requires
// `@request.body.created_by = @request.auth.id`, but the update rule only
// checked `@request.auth.id != ""`, so any signed-in user could rewrite
// `created_by` and reassign a todo to someone else. This rejects any update
// that submits a `created_by` different from the stored value (omitting the
// field, or resending the same value, is still allowed).

migrate(
  (app) => {
    const todos = app.findCollectionByNameOrId("todos");
    todos.updateRule =
      '@request.auth.id != "" && (@request.body.created_by:isset = false || @request.body.created_by = created_by)';
    app.save(todos);
  },
  (app) => {
    const todos = app.findCollectionByNameOrId("todos");
    todos.updateRule = '@request.auth.id != ""';
    app.save(todos);
  },
);
