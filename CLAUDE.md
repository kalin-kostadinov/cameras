# Cameras project — home + studio video surveillance

> Project brief and build plan. Lives at the repo root as `CLAUDE.md` so Claude Code
> loads it automatically. Keep it updated when decisions change.

Owner: Kalin (Burgas, Bulgaria). Prefers Java / Spring Boot / Thymeleaf. Goal: **simple, cheap,
effective**, free/open-source software wherever possible.

---

## 1. Requirements

### Cameras (5 total)

| ID (stream path)  | Location | Placement                                   | Who can see it              |
|-------------------|----------|---------------------------------------------|-----------------------------|
| `home-entrance`   | Home     | Entrance of the apartment building          | Kalin + **neighbours**      |
| `home-parking`    | Home     | Balcony, overlooking the parking / the car  | Kalin + **neighbours**      |
| `home-door`       | Home     | Inside the apartment, facing the front door | Kalin only                  |
| `studio-inside`   | Studio   | Inside the studio                           | Kalin + **partners**        |
| `studio-door`     | Studio   | Outside the studio door                     | Kalin + **partners**        |

### Functional
- Everything recorded and stored **locally at home** (no paid cloud).
- **Live view** from anywhere (phone + desktop browser), via the owner's VPS.
- **Playback**: basic UI to pick a camera + date/time and watch past footage; download a clip.
- **Access control**: admin (Kalin) sees everything; groups get specific cameras
  (`neighbours` → entrance + parking, `partners` → studio-inside + studio-door).
- **Studio door display**: a small monitor inside the studio always shows `studio-door` live.
  Must work on the studio LAN even if the internet is down.

### Non-functional
- Cheap hardware, no subscriptions. Reliable 24/7 recording.
- No inbound ports opened at home or studio (Bulgarian ISPs often use CGNAT; also safer).
- HTTPS only on the public side. Cameras never directly exposed to the internet.

---

## 2. Architecture

```
 HOME LAN (e.g. 192.168.1.0/24)                       STUDIO LAN (must differ, e.g. 192.168.50.0/24)
 ┌───────────────┐                                    ┌────────────────┐
 │ home-entrance │──┐                                 │ studio-inside  │──┐
 │ home-parking  │──┼─RTSP─┐                          │ studio-door    │──┼─RTSP──┐
 │ home-door     │──┘      │                          └────────────────┘  │       │
 └───────────────┘         ▼                                              ▼       │
              ┌──────────────────────────┐              ┌────────────────────────┐│
              │ HOME SERVER (mini PC)    │              │ RASPBERRY PI (studio)  ││
              │ Ubuntu Server + Docker   │              │ - WireGuard peer       ││
              │ - MediaMTX: pulls RTSP,  │              │   + routes studio LAN  ││
              │   records fMP4 to HDD,   │◄── RTSP over ┤ - mpv fullscreen of    │◄┘
              │   serves HLS + playback  │    WireGuard │   studio-door → HDMI   │
              │ - WireGuard peer         │              │   monitor              │
              └────────────┬─────────────┘              └───────────┬────────────┘
                           │ WireGuard (outbound, keepalive)         │ WireGuard
                           ▼                                         ▼
                   ┌─────────────────────────────────────────────────────┐
                   │ VPS (public)                                         │
                   │ - WireGuard hub 10.10.0.1                            │
                   │ - Caddy: HTTPS (Let's Encrypt), forward_auth         │
                   │ - Java app (Spring Boot): users, groups, camera ACL, │
                   │   live grid, playback UI, admin, audit log           │
                   │ - PostgreSQL                                         │
                   └───────────────────────────┬─────────────────────────┘
                                               │ HTTPS
                                 phones / browsers (Kalin, neighbours, partners)
```

### Key decisions
1. **MediaMTX does all the video work** (free, single Go binary / Docker image): pulls RTSP from
   cameras, records to disk in fMP4 segments, deletes old footage automatically, serves live HLS
   (and WebRTC later), and exposes a **playback server** (`/list`, `/get`) that returns MP4
   for any time range. We do **not** process video in Java.
2. **The Java app owns identity and permissions** and the UI. It never touches video bytes.
3. **Caddy `forward_auth`** on the VPS: every request to `/live/*` or `/playback/*` is first
   checked by the Java app (session cookie + camera ACL parsed from the URL). If allowed, Caddy
   proxies the request over WireGuard to MediaMTX at home. MediaMTX only accepts reads from the
   VPS WireGuard IP.
4. **WireGuard with the VPS as hub.** Home server and studio Pi dial *out* to the VPS, so no
   port forwarding and CGNAT doesn't matter. Home server pulls studio camera streams through the
   tunnel (home → VPS → studio Pi → studio LAN). *Alternative:* Tailscale (free tier, even less
   setup, uses a third-party coordinator).
5. **Live view = HLS** (MediaMTX standard fMP4 HLS, ~3–6 s delay) because it proxies cleanly
   through Caddy over plain HTTPS. Not low-latency HLS: LL-HLS makes many more requests per
   viewer and every request passes through forward_auth. Live grid uses camera **sub-streams** (low bitrate);
   single-camera view uses the main stream. WebRTC is a later optimisation.
6. **Recording = continuous main stream**, 1-hour segments, retention **14 days** (tune to disk).
7. **Studio display reads the camera directly on the studio LAN**, so it keeps working when the
   internet or the home server is down.

### Studio recording — DECIDED: option A (studio 5G plan is unlimited)
- **A — Central recording (CHOSEN):** home MediaMTX pulls both studio
  main streams 24/7 over WireGuard. One disk, one MediaMTX. Costs ~**3 TB/month** of 5G data.
- **B — Record at the studio (fallback only, if the plan ever becomes capped):** the studio Pi also runs MediaMTX and
  records to its own USB disk; video leaves the studio only when someone is watching (live or
  playback). 5G usage drops to a few GB/month. Caddy routes `/live/studio-*` and
  `/playback/studio/*` to `10.10.0.3`, everything else to the home server; the app keeps a
  camera → MediaMTX-node mapping (`Camera.node = HOME|STUDIO`).

### Bandwidth / storage estimates
- ~4 Mbit/s per main stream (2K H.264) → ~43 GB/day/camera → **~215 GB/day for 5 cameras**.
  A 4 TB disk ≈ 18 days, so 14-day retention leaves headroom.
- Studio upload: ~8–10 Mbit/s continuous (2 main streams to home). Fine on fibre; check plan.
- Home upload: each remote viewer of a sub-stream ≈ 0.5–1 Mbit/s.

---

## 3. Hardware shopping list (Burgas, prices approximate, EUR, Nov 2026)

Check Technopolis, Technomarket, Ardes.bg, eMAG.bg, Ozone.bg, Desktop.bg, or local shops in Burgas.

| # | Item | Qty | ~Price | Notes |
|---|------|-----|--------|-------|
| 1 | **TP-Link Tapo C320WS** (outdoor, 2K, Wi-Fi **+ Ethernet port**, RTSP, IP66) | 3 | ~€45–55 each | `home-entrance`, `home-parking`, `studio-door`. Use the Ethernet port where a cable is possible. Needs mains power (12 V adapter included). |
| 2 | **TP-Link Tapo C110 / C120** (indoor 2K Wi-Fi, RTSP) | 2 | ~€25–35 each | `home-door`, `studio-inside`. |
| 3 | **Mini PC, Intel N100**, 16 GB RAM, 500 GB SSD (Beelink/GMKtec/etc.) | 1 | ~€150–180 | Home server. *Or reuse an old PC/laptop for €0.* No transcoding needed, so N100 is plenty. |
| 4 | **External 4 TB HDD (USB 3)**, or a WD Purple in a USB enclosure | 1 | ~€100–120 | Recordings. |
| 5 | **Raspberry Pi 4 (2 GB)** + official PSU + case + 32 GB microSD | 1 | ~€80–90 | Studio: WireGuard router + door display. |
| 6 | **Small HDMI monitor** (10–22", any cheap/used one) + micro-HDMI→HDMI cable | 1 | ~€50–90 | Studio door display. |
| 7 | Outdoor Cat6 cable, RJ45 plugs/crimper, cable clips, outdoor junction box, extension cords | – | ~€30–50 | Depends on runs. |
| 8 | *(Optional)* small UPS (~600 VA) for home router + server | 1 | ~€60 | Keeps recording through short power cuts. |

**Estimated total: ~€650–750** (≈ €500 if you reuse an old PC and monitor).

Why Tapo: cheap, available in every BG shop, standard **RTSP + ONVIF** after a one-time setup
in the Tapo app. *Upgrade path:* Reolink RLC-510A (PoE, metal housing, ~€55–65) + a 4-port PoE
switch (~€35) for any camera with an easy cable run — more robust than Wi-Fi.

Already owned:
- **VPS: Hostinger KVM 2** — Frankfurt, Ubuntu 24.04 LTS, 2 vCPU, 8 GB RAM, 100 GB disk,
  8 TB/month bandwidth. Far more than needed (app + Postgres + Caddy ≈ 1 GB RAM). Traffic
  relayed through the VPS counts in and out, but even heavy remote viewing stays well under 8 TB.
- A domain/subdomain for the app (e.g. `cams.<your-domain>`).
- **Possibly an old PC (at the villa)** for the home server → skip item 3. Requirements: 64-bit
  CPU, ≥4 GB RAM, wired Ethernet to the router, a free SATA bay → buy an internal **WD Purple
  4 TB** (~€100–110) instead of a USB disk. Trade-off: an old desktop draws ~40–60 W vs ~10 W
  for an N100, roughly €40–60/year more electricity.

Connectivity decisions:
- **Home cameras on Wi-Fi** (router signal is good at the building entrance and the balcony).
  Tapo cameras are 2.4 GHz only; the home server itself must be **wired** to the router.
- **Parking camera**: car is ~20 m away, from the 3rd floor. The C320WS wide lens is fine for
  "is my car OK / who is near it", but not for faces or number plates at that distance. If that
  detail matters, choose a narrower lens (e.g. 6 mm) or an optical-zoom model instead.
- **Studio internet is a 5G router** → almost certainly behind CGNAT (fine — WireGuard dials
  out) and possibly **data-capped** — see §2 "Studio recording options".

---

## 4. Legal / practical checklist (Bulgaria — not legal advice)
- **Building entrance** is common property: get a decision of the owners' general assembly
  (общо събрание по ЗУЕС) and agree with the building manager (управител). The same decision
  is a clean basis for sharing that feed with neighbours.
- **Parking camera**: frame it on your car/spot, not the street or public space. Use the Tapo
  **privacy mask** for windows of other buildings and pedestrian areas.
- **Inside the apartment** facing your own door: fine; don't capture neighbours' doors.
- **Studio**: inform partners/staff; put up "видеонаблюдение" signs at the entrance.
- GDPR: keep retention limited (14 days), restrict access, keep an **audit log** of who viewed
  what (built into the app).

---

## 5. Build phases

### Phase 0 — Prep
- [ ] Confirm power + cable/Wi-Fi at the building entrance; distance balcony → parking spot.
- [ ] Building approval for the entrance camera.
- [ ] Ensure **home and studio LANs use different subnets** (change studio router to
      `192.168.50.0/24` if both are `192.168.1.0/24`).
- [ ] Create GitHub repo `cameras` (monorepo, layout in §7).

### Phase 1 — Buy hardware (§3)

### Phase 2 — Bench-test cameras at home first
- [ ] Set up each camera in the Tapo app, set a **camera account** (Advanced → Camera Account)
      → enables RTSP: `rtsp://user:pass@IP:554/stream1` (main), `/stream2` (sub).
- [ ] Give every camera a DHCP reservation (fixed IP) in the router.
- [ ] Test each stream in VLC. Update firmware. Set time zone Europe/Sofia, NTP on.
- [ ] Optional: block cameras' internet access in the router after setup.

### Phase 3 — Home server
- [ ] Install Ubuntu Server 24.04 LTS, SSH key login, unattended-upgrades, ufw.
- [ ] Mount the 4 TB disk at `/srv/recordings` (ext4, via `/etc/fstab` with UUID, `nofail`).
- [ ] Install Docker + compose plugin. Deploy MediaMTX (`deploy/home/`).
- [ ] Verify recording files appear and `http://<server>:9996/list?path=home-door` works.

### Phase 4 — VPS + WireGuard
- [ ] VPS: WireGuard hub `10.10.0.1/24`, `net.ipv4.ip_forward=1`, open UDP 51820 only.
- [ ] Home server peer `10.10.0.2`, PersistentKeepalive 25.
- [ ] Studio Pi peer `10.10.0.3`, VPS `AllowedIPs` for it includes `192.168.50.0/24`.
- [ ] DNS `cams.<domain>` → VPS. Caddy + app + Postgres via docker compose (`deploy/vps/`).

### Phase 5 — Studio
- [ ] Raspberry Pi OS Lite (64-bit), WireGuard client, IP forwarding + NAT (masquerade) from
      `wg0` to the studio LAN so the home server can reach studio cameras.
- [ ] Door display: `mpv --fs --profile=low-latency --rtsp-transport=tcp
      rtsp://…/stream2` as a systemd service with auto-restart, HDMI monitor always on.
- [ ] Install studio cameras; add `studio-*` paths to MediaMTX at home.

### Phase 6 — Physical installation
- [ ] Outdoor cameras: drip loop on cables, seal connectors with the supplied waterproof kit,
      mount under an overhang if possible, angle slightly down, check night view before fixing.
- [ ] Indoor cameras: high corner, avoid pointing at windows (backlight).
- [ ] Configure privacy masks; verify every stream is recording.

### Phase 7 — App (§6), then hardening
- [ ] Backups: Postgres dump nightly (VPS), MediaMTX + configs in git (no secrets).
- [ ] Monitoring: app shows camera online/offline (MediaMTX API); optional alert via **ntfy**.
- [ ] fail2ban/rate-limit on login; strong passwords; 2FA (TOTP) for admin later.

---

## 6. The app — `app/`

### Stack
Java 21, Spring Boot 3.x, Thymeleaf, Spring Security (form login + remember-me), Spring Data JPA,
Flyway, PostgreSQL, **hls.js** for the player, minimal CSS (responsive; installable as PWA).
Build: Maven. Packaged as a Docker image.

### Domain model
- `User` (id, username, passwordHash, displayName, role `ADMIN|VIEWER`, enabled, createdAt)
- `Camera` (id, slug = MediaMTX path, name, location `HOME|STUDIO`, subStreamPath, sortOrder,
  enabled)
- `AccessGroup` (id, name — e.g. "Neighbours", "Partners")
- `GroupCamera` (group, camera, canLive, canPlayback)
- `GroupMember` (group, user)
- `AuditEvent` (id, user, camera, action `LIVE|PLAYBACK|DOWNLOAD|LOGIN`, at, ip)

`ADMIN` implicitly has access to everything.

### Endpoints / pages
- `GET /` — grid of cameras the user may see (sub-streams, HLS).
- `GET /cameras/{slug}` — single camera, main stream, fullscreen.
- `GET /cameras/{slug}/playback?date=YYYY-MM-DD` — day timeline built from MediaMTX
  `/list?path=…&start=…&end=…`; click → play via `/playback/get?path=…&start=…&duration=…`
  (MP4 in a `<video>` element); "download clip" button.
- `/admin/users`, `/admin/groups`, `/admin/cameras`, `/admin/audit`.
- `GET /internal/authz` — **Caddy forward_auth target**. Requires header `X-Authz-Secret`
  (= `AUTHZ_SECRET`, else 404), reads the session cookie and `X-Forwarded-Uri`; extracts the
  camera path from `/live/{path}/…` or the single `path=` query param of `/playback/list|get`;
  returns 200 if allowed (audit event, throttled to 1 per 10 min per user/camera/action),
  401 if not logged in, 403 otherwise. Caddy also answers 404 for `/internal/*` from outside.
- `GET /api/status` — camera online state from MediaMTX API `GET /v3/paths/list`.

### Status (Nov 2026)
Implemented in `app/` (package `bg.kostadinov.cameras`): entities + Flyway `V1__init.sql`
(seeds the 5 cameras and the Neighbours/Partners groups), form login + remember-me,
`AccessService`, `AuthzController` + `MediaPathParser`, live grid / single camera (hls.js),
playback day timeline + clip download (30-min clips), admin pages, audit log, first admin
from `APP_ADMIN_USERNAME`/`APP_ADMIN_PASSWORD`. UI is English only (Bulgarian TODO).

### Running locally
1. `cd deploy/dev && docker compose up -d` — Postgres, MediaMTX with 5 fake cameras
   (ffmpeg test patterns, 1-minute recording segments), Caddy on :8000.
2. Run the app with profile `dev` (`SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run` in `app/`,
   or an IntelliJ run config). Dev secrets/admin are in `application-dev.yml`.
3. Open **http://localhost:8000** (through Caddy, so /live and /playback work), log in as
   `admin` / `admin-dev-password`.

Required env vars in production: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `AUTHZ_SECRET` (≥24 chars,
same value in Caddy), `REMEMBER_ME_KEY` (≥24 chars), `APP_ADMIN_PASSWORD` (first start only),
`MEDIAMTX_API_URL` / `MEDIAMTX_PLAYBACK_URL` (e.g. `http://10.10.0.2:9997` / `:9996`).

### Conventions
- Package by feature: `camera`, `user`, `access`, `playback`, `audit`, `admin`, `security`.
- MediaMTX base URLs in `application.yml` (`mediamtx.api-url`, `mediamtx.playback-url`),
  secrets via env vars, never committed.
- Tests: JUnit 5; `MediaPathParserTest` and `AuthzControllerTest` cover the security boundary —
  extend them whenever URL handling changes. Testcontainers (Postgres) for repository tests later.

---

## 7. Repo layout

```
cameras/
├── CLAUDE.md                  ← this file
├── app/                       ← Spring Boot app
├── deploy/
│   ├── home/    docker-compose.yml, mediamtx.yml, wg0.conf.example
│   ├── vps/     docker-compose.yml, Caddyfile, wg0.conf.example
│   └── studio/  wg0.conf.example, door-display.service, nat.sh
└── docs/        install guide with photos, network diagram
```

### Config sketches (to be finalised in code)

`deploy/home/mediamtx.yml` (core parts):
```yaml
api: yes
apiAddress: :9997
hls: yes
hlsAddress: :8888
hlsVariant: fmp4
hlsSegmentDuration: 1s
playback: yes
playbackAddress: :9996

authMethod: internal
authInternalUsers:
  - user: any
    ips: ['10.10.0.1/32', '127.0.0.1/32']   # only the VPS (via WireGuard) and localhost
    permissions:
      - action: read
      - action: playback
      - action: api

pathDefaults:
  record: yes
  recordPath: /recordings/%path/%Y-%m-%d_%H-%M-%S-%f
  recordFormat: fmp4
  recordSegmentDuration: 1h
  recordDeleteAfter: 14d

paths:
  home-entrance:      { source: rtsp://${CAM_USER}:${CAM_PASS}@192.168.1.21:554/stream1 }
  home-entrance-sub:  { source: rtsp://${CAM_USER}:${CAM_PASS}@192.168.1.21:554/stream2, record: no }
  # … same for home-parking, home-door
  studio-door:        { source: rtsp://${CAM_USER}:${CAM_PASS}@192.168.50.21:554/stream1 }
  studio-door-sub:    { source: rtsp://${CAM_USER}:${CAM_PASS}@192.168.50.21:554/stream2, record: no }
  # … same for studio-inside
```

`deploy/vps/Caddyfile`:
```
cams.example.com {
    handle /internal/* {
        respond 404
    }
    @media path /live/* /playback/*
    handle @media {
        forward_auth localhost:8080 {
            uri /internal/authz
            header_up X-Authz-Secret {env.AUTHZ_SECRET}
        }
        handle_path /live/* {
            reverse_proxy 10.10.0.2:8888
        }
        handle_path /playback/* {
            reverse_proxy 10.10.0.2:9996
        }
    }
    reverse_proxy localhost:8080
}
```
(Run Caddy and the app with `network_mode: host`, or Caddy on the host, so they can reach the
WireGuard interface.)

---

## 8. Later / nice-to-have
- WebRTC live view (sub-second latency) — needs UDP forwarding through the VPS.
- Motion/person events (Frigate or camera ONVIF events) → timeline markers + ntfy push.
- TOTP 2FA for admin; per-user time-limited share links (e.g. for a delivery).
- Off-site copy of important clips to the VPS.

## 9. Open questions
- Resolved: Wi-Fi reaches the entrance + balcony; car ~20 m from 3rd-floor balcony;
  VPS = Hostinger KVM 2 (see §3); home ~330 Mbit/s down / **~100 Mbit/s up** (Wi-Fi, measured
  at roughly balcony/entrance distance → camera Wi-Fi is fine); studio 5G is **unlimited** →
  central recording (option A); power is available at the entrance spot (any extension in
  the common area should be done properly and covered by the building approval).
- Still open: old PC at the villa — CPU / RAM / free SATA bay? Otherwise buy the N100 mini PC.
