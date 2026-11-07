/// <reference path="../pb_data/types.d.ts" />
// PocketBase v0.40.6
//
// Initializes the app settings required by docs/architecture.md:
//   - app name "Ticket Space"
//   - app URL https://ticketspace.508.dev
//   - scheduled backups daily at 03:00, keeping the last 7

migrate(
  (app) => {
    const settings = app.settings()

    settings.meta.appName = "Ticket Space"
    settings.meta.appURL = "https://ticketspace.508.dev"

    settings.backups.cron = "0 3 * * *"
    settings.backups.cronMaxKeep = 7

    app.save(settings)
  },
  (app) => {
    const settings = app.settings()

    settings.meta.appName = "Acme"
    settings.meta.appURL = "http://localhost:8090"

    settings.backups.cron = ""
    settings.backups.cronMaxKeep = 3

    app.save(settings)
  },
)
