# Required server contracts for 360Secure Android premium app

This is an **integration contract**, not a claim the endpoints exist. The current Ubuntu API is known to have `/api/auth/login`, `/api/auth/register` (email/password) on an older build; OTP endpoints are new and require implementation. Do not remove working endpoints before client migration.

Base URL in source: `https://camera.360secure.in`. Change centrally in `SecureApp.API_BASE` (native token registration) and `app.js` (`API`) if needed. HTTPS only.

## Phone OTP

`POST /api/auth/otp/request`:
```json
{ "phone": "+919876543210" }
```
Response example:
```json
{ "requestId": "random-challenge-id", "expiresInSeconds": 300 }
```

`POST /api/auth/otp/verify`:
```json
{ "phone": "+919876543210", "otp": "123456", "requestId": "random-challenge-id" }
```
Response:
```json
{ "accessToken": "JWT-access", "refreshToken": "opaque-refresh", "user": { "phone": "+919876543210" } }
```

`POST /api/auth/refresh`: `{ "refreshToken": "..." }` -> `{ "accessToken": "...", "refreshToken": "..." }`.

**Mandatory protections:** E.164 validation, high-entropy OTP via crypto RNG, hashed OTP/challenge in DB, short expiry, maximum attempts, rate limits per phone/IP/device, no account enumeration, single-use verification, refresh token rotation/revocation, auditable event. Existing SIMCom Python gateway `from sms_to_phone import SEND_SMS` can deliver via Cloudflare Worker; never put its client API key in Android.

## Camera & recordings

- `GET /api/cameras`: `[{"cameraId":"...","name":"Front Door"}]` or `{ "cameras": [...] }` for authenticated user.
- `POST /api/cameras/claim`: `{ "cameraId": "...", "password": "...", "name": "..." }`; server checks authorization, stores irreversible/verifiable credentials and limits brute force.
- `GET /api/events?date=YYYY-MM-DD`: `{ "events": [{ "type":"visitor_detected", "timestamp":"2026-11-09T08:00:00Z", "title":"Person at door" }] }`.
- `GET /api/cameras/:cameraId/recordings?date=YYYY-MM-DD`: `{ "recordings": [{"id":"abc", "startTime":"...", "endTime":"...", "playbackUrl":"https://.../expiring-mp4"}] }`.
- `GET /api/cameras/:cameraId/recordings/:id/playback`: `{ "url":"https://.../expiring-mp4" }` (fallback).
- **All endpoints enforce authenticated camera ownership.** No plain public MP4 URLs; provide expiring URL with range support. UTC timestamps, user local time display.

## FCM registration

`POST /api/devices/push-token` with Bearer access token:
```json
{ "token": "firebase_registration_token", "platform": "android", "deviceId": "device_specific_id" }
```

Bind each token to authenticated account, retain multiple family devices, rotate/revoke on logout, remove invalid tokens. Return 204 or 200. App attempts registration on session restore, successful login, and FCM token rotation. Explicit server-side token removal on logout should be added before production.

## Doorbell call notification

When a **real doorbell button press** is verified, backend resolves authorized accounts associated with the camera and sends each registered Android token an FCM v1 **HIGH priority data message** (from trusted Firebase Admin service account, never from Android itself). Example message body sent through Firebase Admin API:
```json
{
  "message": {
    "token": "DEVICE_FCM_TOKEN",
    "android": { "priority": "HIGH", "ttl": "45s" },
    "data": {
      "type": "doorbell",
      "cameraId": "360S-A955B0CD3D9C",
      "callId": "call_a7b6c5d4e3f2g1"
    }
  }
}
```

Send only to authenticated, owner-linked devices. Validate call expiration and user/camera permissions on both signaling and media, not merely on client. Implement server-side deduplication/cooldown, acknowledge accept/decline, and prevent stale calls being answered after timeout. One notification is displayed for each incoming event; Android's notification channel controls ringtone and DND. Android 14+ may deny full-screen intent unless permitted; a heads-up notification still appears where allowed.

## WebRTC intercom

The Android code follows current camera signaling messages: connect over `wss://camera.360secure.in/ws?role=viewer&cameraId=...&token=...`; handle `offer`, `candidate`, `camera-online`, `camera-offline`, `connected`, `error`; respond with `answer` and ICE candidate. **Existing server historically accepts a global viewer token**, so before releasing a new auth client, replace with short-lived, per-camera viewer authorization or adapt server to validate the user's account JWT. Keep existing H.264/GStreamer source unchanged.

For true two-way talk, Pi must negotiate an audio m-line and support sending microphone audio and rendering received audio through USB mic/MAX98357A I2S speaker; STUN/TURN, echo cancellation, acoustic tests and permissions required. Client requests mic only when SDP offers audio. Without audio m-line client remains video-only and tells user the limitation.
