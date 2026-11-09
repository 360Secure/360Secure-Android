# 360Secure Android — Premium Doorbell (v0.2 preview)

![build status](https://github.com/360Secure/360Secure-Android/actions/workflows/build-apk.yml/badge.svg)

A portrait-only Android security app: polished dark interface, phone OTP screen **without biometric login**, camera dashboard, WebRTC viewer, recordings timeline and a native **Smart Door Lock** incoming-call interface. Uses existing 360Secure camera signaling protocol.

## Download the APK — no Android Studio needed

1. Open **Actions → Build 360Secure Premium Android APK**.
2. Open the latest **successful** run on the `feature/android-premium-doorbell-v1` branch (after review/merge, use the main branch).
3. Under **Artifacts**, download **360Secure-Premium-Android-Debug-APK**.
4. Extract the downloaded ZIP to obtain `app-debug.apk` and install it on your Android phone. Allow installation from that source if Android asks.

This is a **debug APK** intended for testing. GitHub signs it with a temporary debug certificate; use a consistent release signing key and explicit release pipeline before production rollout.

## Project and integration notes

See [Build & integration guide](docs/BUILD_AND_INTEGRATION.md) and [server API contract](docs/API_CONTRACT.md). Android UI and camera signaling code are included; OTP API, Firebase/FCM project configuration, secure server push dispatch, WebRTC audio on the Pi and per-camera ownership authorization need backend setup and real-device tests before feature claims.

Do not deploy server changes or alter working camera streaming/recording services just to build this APK. An APK build proves compilation, not end-to-end doorbell operation.
