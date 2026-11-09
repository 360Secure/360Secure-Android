# 360Secure Visitor Notification UX

**Implemented on branch** `feature/visitor-alerts-deep-links`; **not yet deployed or end-to-end verified.**

## Product behavior
1. A trusted detector submits a real person event and a selected JPEG/PNG photo to the authorized private visitor API.
2. Backend records every event and deduplicates by a unique external event key, before enforcing per-camera, per-subject, per-user limits.
3. Backend delivers high-priority FCM **data** message with `type=visitor_event`, `eventId` and `cameraId` only.
4. When a verified Android user is logged in, native Android obtains the photograph from `GET /api/events/:id/photo` with an access token (refreshes an expired token where possible). The notification uses BigPictureStyle, privately visible on unlocked devices, with a generic lock-screen public version. Network or login failures yield a text-only notification rather than exposing a public image.
5. Tap the notification or SMS **View Visitor Photo** event URL `https://camera.360secure.in/e/<eventId>` to open Events and that exact photo. If logged out, the user must authenticate by phone OTP before fetching any event data. Photo API enforces account ownership regardless of app UI state.
6. SMS is plain text with the label **View Visitor Photo:** plus an event app URL. It is **not** a photo attachment, publicly visible image URL, or clickable custom SMS button.
7. There is no automatic unlock; recognition is not a credential.

## Spam control
Backend records all events but suppresses repeat push for same camera for 90 s, same visitor for 10 min, max 8 push notifications/hr and 30/day/account. SMS is limited to per-camera 90 s, same visitor 15 min, 3/hr and 10/day/account. Android also suppresses duplicate event IDs and repeat camera notifications within 90 s. Do Not Disturb, notification permission, offline devices and Android OEM background policies still govern delivery.

## Android App Links
The app manifest claims `https://camera.360secure.in/e/`. The HTTPS host must serve `/.well-known/assetlinks.json` using the **stable release signing SHA-256 fingerprint**. The private visitor backend contains this route driven by `APP_SIGNING_SHA256`. Without the association, the link can open a browser fallback page rather than the app. No event photograph is returned by the app-link route.

## Environment dependencies
- Stable Android app installation with package `in.secure360.app`.
- Verified OTP auth API, camera ownership, permitted event/photo API and reverse proxy.
- Firebase Android project configured via repo variables; server credentials supplied privately on Ubuntu.
- An actual Pi/Ubuntu person detector capturing photo crops and calling trusted ingestion endpoint.
- Real-device checks on phone lock screen, user logout, cooldowns, Android 13+ notifications, FCM high priority and photo fetch with expired JWT.

**IMPORTANT:** This implementation preserves the existing WebRTC signaling/video source; the two-way Pi audio capability remains separately unfinished.
