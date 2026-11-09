# 360Secure — Premium Android App, Phase 1 ZIP

**Package created:** 2026-11-09  
**Source branch used for reference:** `360Secure/360Secure-Android` → `360secure-integrated`.  
**Deliverable:** Fully structured Android Studio source project (not a precompiled APK).

## What is inside

- `360Secure-Android/`: Complete Android Studio project with polished WebView UI, Java native app shell, notification/call handling, Firebase Cloud Messaging service, a secure token store, and the pre-existing WebRTC signaling protocol adapted to the new UI.
- `360Secure-Android/app/src/main/assets/`: UI — phone OTP login (no biometric login), home, live camera, recordings, events, persons, access, settings.
- `360Secure-Android/app/src/main/java/in/secure360/app/`: Android system UI insets, portrait lock, incoming doorbell activity and ringtone notification, accept/decline actions, encrypted session handling, Firebase token registration.
- `server-contract/`: Required API and push signal contracts before end-to-end operation.
- `PREVIEWS/`: Actual browser-rendered source UI screenshots on 360x780 and 393x852 viewports.

## Build (macOS with Android Studio)

1. Extract the ZIP; open the `360Secure-Android` subfolder in Android Studio.
2. Install **JDK 17**, Android SDK Platform **35**, build tools **35.0.0**, and **Gradle 8.9** if prompted.
3. Sync Gradle. If Android Studio asks for a Gradle distribution, choose a locally installed Gradle 8.9. The ZIP does **not** include the Gradle wrapper binary or an APK.
4. Run `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
5. Debug APK, after build: `360Secure-Android/app/build/outputs/apk/debug/app-debug.apk`.

**Alternative using Gradle 8.9 on a machine with Android SDK/JDK 17:** `cd 360Secure-Android && gradle assembleDebug`.

## Install and test

1. Install the debug APK. Verify the login screen has **no biometric button**.
2. Test portrait lock by rotating phone, and check on both 3-button and gesture navigation; content must not overlap status/navigation bars or camera cutout.
3. Phone OTP requires backend endpoints in `server-contract/API.md` to be live. **Do not expect the OTP to work solely by installing the APK.**
4. Enable notifications and, where Android allows, full-screen doorbell alerts under app notification and full-screen-intent settings.
5. Configure Firebase client details in `app/src/main/res/values/strings.xml` using your own Firebase project for package `in.secure360.app`. Configure the trusted server to register FCM tokens and issue doorbell pushes.
6. Verify live video with the existing camera before any Pi/server changes. The current GCP signaling token model and account authorization must be checked against this app's JWT.
7. If you add Pi mic/speaker and audio SDP support, test two-way talk. Until then, the app explicitly tells the user audio is unavailable rather than pretending it works.

## Current capabilities versus required integration

| Capability | Included in ZIP | Requires deployment / validation |
| --- | --- | --- |
| Polished UI, portrait, system-bar/cutout safe layout | Yes, native + CSS | Test on real devices |
| Phone OTP interface with no biometrics | Yes | Working `/api/auth/otp/request`, `/api/auth/otp/verify` and SMS sender |
| Persistent encrypted login tokens | Yes, Android Keystore AES-GCM | Backend refresh/revoke endpoints |
| WebRTC video viewer (compatible message shapes) | Yes, adapted from source | Camera, signaling and per-camera authorization; test actual connectivity |
| 24-hour recording browser | Yes, source adaptation | Authenticated recording index/playback API (not yet verified) |
| Background doorbell FCM receiver and call notification | Yes | Firebase configuration, registered device token, authorized server push and doorbell event |
| Custom incoming call, Accept/Decline | Yes, native | Notification permissions and Android full-screen restrictions |
| Video after accept | Yes, navigates to authorized camera viewer | Real WebRTC session, valid account/camera authorization |
| Visitor speaking / owner audio | Conditional SDP/audio track support | Pi USB microphone + I2S speaker, audio WebRTC negotiation and tests |
| Person detection/profiles, unlock | Screens marked unavailable | Future backend/hardware; no fake unlock or recognition |

## Important warnings

- **No credentials or real Firebase app config are included.** Do not commit API secrets, phone numbers, videos or face records.
- **No services were changed** on Pi, Ubuntu, DGX, or GCP.
- **No APK was compiled** here because Android SDK and Gradle are unavailable in this environment. Java/Gradle compilation must be verified in Android Studio or the included GitHub Actions workflow after adding source to GitHub.
- Real mobile calls depend on FCM, notification permission, Android full-screen eligibility, data connectivity and vendor battery policies. Force-stopping an app in Android Settings prevents delivery until it is reopened. DND rules are respected.
- This package is **a complete source project and integration foundation**, not a guaranteed fully deployed end-to-end doorbell system. Server-side OTP/FCM and Pi audio are separate milestones.
- Don't replace the working camera service, splitter, shared-memory source, or recording sender just to install this Android app. Roll back by uninstalling this preview APK and reinstalling your known-good existing Android APK.

## Repository plan

1. Apply this source on `feature/android-premium-doorbell-v1` in `360Secure/360Secure-Android` (the last known working branch is `360secure-integrated`).
2. Build in CI. Smoke-test on the exact Android phone with 3-button and gesture navigation.
3. Check live view regression against the existing APK; merge only after verified.
4. Add FCM project config and server push integration separately, then roll out to other family phones.

**The Android app source is in the ZIP. GitHub has not been automatically updated by this archive creation.**
