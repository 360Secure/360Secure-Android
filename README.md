# 360Secure Android — buildable MVP source

## Build an APK on your Mac (Android Studio)
1. Unzip `360Secure-Android.zip`.
2. In Android Studio choose **Open** and select the extracted `360Secure-Android` folder.
3. Allow Gradle sync; install Android SDK Platform 35 if prompted. Java 17 is required (Android Studio bundled JDK works).
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Locate `app/build/outputs/apk/debug/app-debug.apk` and install it on your Android phone. Android may ask you to allow installing apps from this source.

## Features present in the app
- Dark themed login/register, camera dashboard, camera claiming by ID/password.
- WebRTC viewer compatible with the existing 360Secure signaling protocol, **using the existing viewer token**.
- Recordings tab with date picker, horizontally scrollable 24-hour timeline, colored footage intervals, tap-to-seek and MP4 player.
- Configurable HTTPS API endpoint, sign-out.

## Integration blockers — important
- **This ZIP contains source, not a compiled APK.** The build environment where it was generated lacks the Android SDK/Gradle; Android Studio on your Mac can compile it.
- Your Ubuntu API currently listens only on `127.0.0.1:3002`, so the Android app cannot reach it until you safely publish an HTTPS endpoint (e.g., authenticated Nginx proxy over Tailscale on GCP). Do not expose MongoDB or use HTTP.
- Expected API: POST `/api/auth/register` and `/api/auth/login` -> `{token}`; GET `/api/cameras` -> array or `{cameras:[...]}`; POST `/api/cameras/claim` -> camera; GET `/api/cameras/:cameraId/recordings?date=YYYY-MM-DD` -> `{recordings:[{id,startTime,endTime,playbackUrl}]}`; optionally GET `/api/cameras/:cameraId/recordings/:id/playback` -> `{url}`.
- Recordings endpoints and authenticated playback are **not implemented in the current Ubuntu backend**. The timeline UI is implemented but cannot show footage until the backend indexes MP4 segments and serves authorized playback URLs.
- The current GCP signaling service accepts a global viewer token, not the user's account JWT; this is a temporary compatibility path, **not secure per-camera authorization**. Do not publish a production app until server-side ownership checks are enforced for WebRTC.
- Existing test camera password `12345` must be replaced with a strong unique password before production.
- Android WebView WebRTC compatibility depends on Android System WebView and network ICE connectivity; test on your phone. A TURN server may be needed on some mobile networks.
- `appassets.androidplatform.net` is the app's local asset origin. The HTTPS API must permit that origin with CORS (or the app should be migrated to native networking).
- Video playback URLs must be short-lived and signed or otherwise securely authorized. The `<video>` element cannot attach the app's bearer header directly.
- Current auth token is kept in sessionStorage, not persistent encrypted storage. For a production release use Android Keystore-backed auth storage.

## Recommended next backend tasks
1. Expose the existing Ubuntu API via a secure HTTPS reverse proxy; verify `/health` from the phone.
2. Add owner-checked recording metadata and expiring playback URL endpoints.
3. Connect Pi H.264 feed to recorder and verify continuous segments before relying on the timeline.
4. Replace global signaling viewer token with JWT-based camera ownership authorization.
