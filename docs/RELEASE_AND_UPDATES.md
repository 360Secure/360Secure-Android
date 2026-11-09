# 360Secure — Permanent App Identity & Updates

The package name is **`in.secure360.app`** and should not change. Build `versionCode` strictly upward for each future release. The same app icon, session and local encrypted preferences are retained when Android accepts an in-place update signed by the **same signing certificate**.

## One-time stable release signing setup

The current GitHub Actions test APK is **debug-signed** by a CI runner. Runner-generated debug certificates may change between builds and cannot reliably update one another. This makes the debug builds useful for testing, **not** a permanent installed app.

To establish a permanent install, create and privately back up a long-lived Android keystore. Configure these **GitHub Actions repository secrets** in the Android repository:
- `ANDROID_KEYSTORE_BASE64`: base64 of the keystore's raw binary bytes (one line).
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Example Mac/Linux setup **run locally, do not paste your private keys or passwords in ChatGPT**:

```bash
keytool -genkeypair -v -keystore 360secure-release.jks -alias secure360 -keyalg RSA -keysize 3072 -validity 10000
base64 < 360secure-release.jks | tr -d '\n'
keytool -list -v -keystore 360secure-release.jks -alias secure360
```

Copy the base64 output directly into GitHub Actions Secrets through the GitHub UI; do not commit the file. Back up the original `.jks` securely offline; losing the private key prevents future signed updates. The SHA256 certificate fingerprint from `keytool -list` (public information) is used for `APP_SIGNING_SHA256` on the server so SMS event App Links open the correct Android package.

The workflow always produces a test debug APK and **only if all signing secrets are configured** also produces `360Secure-Signed-Update-APK` (app-release.apk). Install the **stable signed release**, then future versions with the same package + signing key and higher `versionCode` install **over** it. Existing test APKs that were signed by a different temporary debug key **may require one final one-time uninstall/migration**; that cannot be bypassed safely by an app.

## Firebase configuration (no keystore/key files in source)

Set GitHub **Actions variables** `FCM_APPLICATION_ID`, `FCM_API_KEY`, `FCM_PROJECT_ID`, `FCM_SENDER_ID` from your Firebase Android app settings. The workflow injects these into `strings.xml` during build. The server separately needs Firebase Admin credentials; Android config alone cannot send alerts.

## Automatic updates versus reinstall

Installing an APK update over an existing app is possible with the same package/signature and a greater versionCode. Android normally requires user confirmation when installing updates outside the Play Store, so **truly automatic background updates** require an authorized distribution mechanism such as Google Play managed release/internal testing, or device management under appropriate policy. Future feature changes still require an app update unless delivered entirely as server-side content; they do not require creating a new app/account.

**Never embed server OTP SMS key, Firebase Admin service account, or Android signing key in a public Android repository or APK.**
