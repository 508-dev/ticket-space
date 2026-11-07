// smoke_assert.mjs — run against a live Ticket Space PocketBase container.
//
// Invoked by smoke.sh (which builds the image, starts it, creates the
// superuser and bootstraps the test users). This script performs the API
// assertions described in apps/pocketbase/scripts/smoke.sh and
// docs/architecture.md. Exits non-zero on the first failure.
//
// Requires: bun. No external dependencies.

const PB_URL = process.env.PB_URL
const SUPERUSER_EMAIL = process.env.PB_SUPERUSER_EMAIL
const SUPERUSER_PASSWORD = process.env.PB_SUPERUSER_PASSWORD
const TICKETSPACE_USERS = process.env.TICKETSPACE_USERS

let failures = 0

function fail(msg) {
  failures++
  console.error(`  ✗ ${msg}`)
}

function check(cond, msg) {
  if (cond) {
    console.log(`  ✓ ${msg}`)
  } else {
    fail(msg)
  }
}

async function req(method, path, { token, json, form, query } = {}) {
  let url = `${PB_URL}${path}`
  if (query) {
    url += `?${new URLSearchParams(query)}`
  }
  const headers = {}
  if (token) headers.Authorization = token
  let body
  if (form !== undefined) {
    body = form // multipart; do not set Content-Type (boundary is added for us)
  } else if (json !== undefined) {
    headers["Content-Type"] = "application/json"
    body = JSON.stringify(json)
  }
  const res = await fetch(url, { method, headers, body })
  let data = null
  try {
    data = await res.json()
  } catch {
    // non-JSON body
  }
  return { status: res.status, ok: res.ok, data }
}

async function authUser(email, password) {
  const r = await req("POST", "/api/collections/users/auth-with-password", {
    json: { identity: email, password },
  })
  if (r.status !== 200) {
    throw new Error(`user auth failed for ${email}: ${r.status} ${JSON.stringify(r.data)}`)
  }
  return { token: r.data.token, record: r.data.record }
}

async function authSuperuser() {
  const r = await req("POST", "/api/collections/_superusers/auth-with-password", {
    json: { identity: SUPERUSER_EMAIL, password: SUPERUSER_PASSWORD },
  })
  if (r.status !== 200) {
    throw new Error(`superuser auth failed: ${r.status} ${JSON.stringify(r.data)}`)
  }
  return r.data.token
}

// Minimal 1x1 JPEG (valid JFIF), base64-encoded.
const JPEG_B64 =
  "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q=="

function jpegBlob() {
  const bin = atob(JPEG_B64)
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return new Blob([bytes], { type: "image/jpeg" })
}

async function checkRealtime() {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 8000)
  let gotConnect = false
  try {
    const res = await fetch(`${PB_URL}/api/realtime`, {
      headers: { Accept: "text/event-stream" },
      signal: controller.signal,
    })
    if (res.status !== 200) {
      fail(`/api/realtime returned ${res.status}`)
      return
    }
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ""
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      if (buf.includes("PB_CONNECT")) {
        gotConnect = true
        break
      }
      if (buf.length > 100000) break
    }
    check(gotConnect, "GET /api/realtime emits a PB_CONNECT event")
  } catch (e) {
    if (e.name !== "AbortError") {
      fail(`/api/realtime error: ${e}`)
    }
  } finally {
    clearTimeout(timer)
    controller.abort()
  }
}

async function main() {
  const users = TICKETSPACE_USERS.split(";")
    .filter(Boolean)
    .map((entry) => {
      const [email, password, name] = entry.split(":")
      return { email, password, name }
    })
  if (users.length < 2) {
    throw new Error("TICKETSPACE_USERS must contain at least two users")
  }
  const [user1, user2] = users

  console.log(`\nTarget: ${PB_URL}`)

  // --- superuser auth -------------------------------------------------
  console.log("\n[collections & rules]")
  const superToken = await authSuperuser()

  // 1. all collections exist
  {
    const r = await req("GET", "/api/collections", { token: superToken, query: { perPage: 200 } })
    const names = new Set((r.data?.items ?? []).map((c) => c.name))
    for (const n of ["users", "sites", "rooms", "room_anchors", "things", "todos"]) {
      check(names.has(n), `collection "${n}" exists`)
    }
  }

  // 2. unauthenticated todo listing returns nothing or is rejected
  {
    const r = await req("GET", "/api/collections/todos/records", {})
    const empty = r.ok && Array.isArray(r.data?.items) && r.data.items.length === 0
    const rejected = !r.ok
    check(empty || rejected, `unauthenticated todo list is empty (${r.status}) or rejected`)
  }

  // 3. public user signup is rejected
  {
    const r = await req("POST", "/api/collections/users/records", {
      json: {
        email: "intruder@example.com",
        password: "password123",
        passwordConfirm: "password123",
      },
    })
    check(!r.ok, `public user signup is rejected (${r.status})`)
  }

  // --- user auth ------------------------------------------------------
  console.log("\n[auth]")
  const s1 = await authUser(user1.email, user1.password)
  const s2 = await authUser(user2.email, user2.password)
  check(!!s1.token && !!s2.token, "both test users can authenticate")

  // --- site / room / thing / todo CRUD --------------------------------
  console.log("\n[CRUD + multipart upload]")
  const sites = await req("GET", "/api/collections/sites/records", { token: s1.token })
  const siteId = sites.data?.items?.[0]?.id
  check(!!siteId, `seeded "Home" site exists`)

  const roomRes = await req("POST", "/api/collections/rooms/records", {
    token: s1.token,
    json: { site: siteId, name: "Kitchen" },
  })
  const roomId = roomRes.data?.id
  check(roomRes.ok && !!roomId, "user can create a room")

  const thingRes = await req("POST", "/api/collections/things/records", {
    token: s1.token,
    json: {
      room: roomId,
      label: "Sink",
      pose_in_room: { t: [0, 0, 0], q: [0, 0, 0, 1] },
    },
  })
  const thingId = thingRes.data?.id
  check(thingRes.ok && !!thingId, "user can create a thing")

  // create a todo with a multipart JPEG photo
  let photoTodo
  {
    const form = new FormData()
    form.append("title", "Leaky tap")
    form.append("status", "open")
    form.append("priority", "2")
    form.append("room", roomId)
    form.append("thing", thingId)
    form.append("created_by", s1.record.id)
    form.append("photos", jpegBlob(), "photo.jpg")

    const r = await req("POST", "/api/collections/todos/records", {
      token: s1.token,
      form,
    })
    photoTodo = r.data
    const photosOk = Array.isArray(r.data?.photos) && r.data.photos.length === 1
    check(r.ok && !!r.data?.id && photosOk, "user can create a todo with a multipart JPEG upload")
  }

  // 5. creating a todo with someone else's created_by is rejected
  {
    const r = await req("POST", "/api/collections/todos/records", {
      token: s1.token,
      json: {
        title: "forged creator",
        status: "open",
        priority: 1,
        room: roomId,
        created_by: s2.record.id,
      },
    })
    check(!r.ok, `todo with someone else's created_by is rejected (${r.status})`)
  }

  // --- sort order -----------------------------------------------------
  console.log("\n[sort order]")
  {
    const specs = [
      ["sort-1a", 1],
      ["sort-1b", 1],
      ["sort-0", 0],
      ["sort-3", 3],
      ["sort-2", 2],
    ]
    for (const [title, priority] of specs) {
      const r = await req("POST", "/api/collections/todos/records", {
        token: s1.token,
        json: { title, status: "open", priority, room: roomId, created_by: s1.record.id },
      })
      if (!r.ok) {
        fail(`could not seed sort todo "${title}": ${r.status} ${JSON.stringify(r.data)}`)
      }
    }

    const r = await req("GET", "/api/collections/todos/records", {
      token: s1.token,
      query: {
        filter: "(title~'sort-')",
        sort: "-priority,created",
        perPage: 20,
      },
    })
    const titles = (r.data?.items ?? []).map((t) => t.title)
    const prios = (r.data?.items ?? []).map((t) => t.priority)
    const expected = ["sort-3", "sort-2", "sort-1a", "sort-1b", "sort-0"]
    const priorityDesc =
      prios.length === 5 && prios.every((p, i) => i === 0 || prios[i - 1] >= p)
    check(
      JSON.stringify(titles) === JSON.stringify(expected),
      `sort=-priority,created orders ${titles.join(", ") || "(none)"} correctly`,
    )
    check(priorityDesc, "priority is non-increasing across the sorted result")
  }

  // --- hook: done/open lifecycle + client-supplied done_by ignored ----
  console.log("\n[hook]")
  {
    // open -> done
    const created = await req("POST", "/api/collections/todos/records", {
      token: s1.token,
      json: { title: "hook-1", status: "open", priority: 1, room: roomId, created_by: s1.record.id },
    })
    const todoId = created.data?.id

    const done = await req("PATCH", `/api/collections/todos/records/${todoId}`, {
      token: s1.token,
      json: {
        status: "done",
        // a client tries to set these directly; the hook must override both
        done_by: s2.record.id,
        done_at: "2020-01-01 00:00:00.000Z",
      },
    })
    check(done.ok && done.data?.status === "done", "open -> done persists status")
    check(done.data?.done_by === s1.record.id, "done_by is set to the requesting user (client value ignored)")
    check(
      !!done.data?.done_at && done.data.done_at !== "2020-01-01 00:00:00.000Z",
      "done_at is set to now (client value ignored)",
    )

    // done -> open
    const reopened = await req("PATCH", `/api/collections/todos/records/${todoId}`, {
      token: s1.token,
      json: { status: "open" },
    })
    check(reopened.ok && reopened.data?.status === "open", "done -> open persists status")
    check(reopened.data?.done_by === "", "done_by is cleared on done -> open")
    check(reopened.data?.done_at === "", "done_at is cleared on done -> open")

    // client-supplied done_by while status is unchanged
    const other = await req("POST", "/api/collections/todos/records", {
      token: s1.token,
      json: { title: "hook-2", status: "open", priority: 1, room: roomId, created_by: s1.record.id },
    })
    const patched = await req("PATCH", `/api/collections/todos/records/${other.data?.id}`, {
      token: s1.token,
      json: { done_by: s2.record.id },
    })
    check(patched.data?.done_by === "", "client-supplied done_by is ignored while status is unchanged")
  }

  // --- realtime -------------------------------------------------------
  console.log("\n[realtime]")
  await checkRealtime()

  console.log(failures === 0 ? "\nAll assertions passed." : `\n${failures} assertion(s) failed.`)
  process.exit(failures === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error(`\nfatal: ${e.message ?? e}`)
  process.exit(1)
})
