# Keepsake

Keepsake is a private Android photo and video journal. Posts, captions, and comments are stored on your device. The app does not request network access or create an account.

## Android app

Open the project in Android Studio, or build it with a JDK and Android SDK:

```powershell
.\gradlew.bat assembleDebug
```

The installable debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

The debug APK is for testing, not public production releases. Do not distribute it: it uses the local debug signing key. A release APK is signed only when all four publisher-owned environment variables are set: `KEEPSAKE_STORE_FILE`, `KEEPSAKE_STORE_PASSWORD`, `KEEPSAKE_KEY_ALIAS`, and `KEEPSAKE_KEY_PASSWORD`. Keep the keystore and passwords outside source control. Without these values, `assembleRelease` produces an unsigned APK.

## Download website

```powershell
Set-Location web
npm install
npm run dev
```

Only after configuring the publisher's release key and verifying the signed release APK, copy it to `web\public\Keepsake.apk` to enable the download buttons. The website intentionally does not offer a download until a production APK is available. Build the static React website with `npm run build`.

## Local data

Images and videos are copied into app-private storage. Captured media is removed from the shared media library after it is saved in the app. Uninstalling the app removes its private posts and comments.
