# Push notification setup

The server and Android app are wired for Firebase Cloud Messaging (FCM), but sending push notifications stays disabled until Firebase credentials are configured.

1. In Firebase Console, create a Firebase project and add an Android app whose package name is exactly `com.example.olo`.
2. Download that app's `google-services.json` into `C:\frontend\app\google-services.json`.
3. In Firebase Console, generate a service-account private key. Keep this key private and copy it to `C:\Users\Стрелов Артем\Desktop\olo_messenger\messenger-backend\firebase\service-account.json`. This file is excluded from Git.
4. Enable the Firebase Cloud Messaging API for the Firebase project.
5. Rebuild/reinstall the Android app and restart the backend with `docker compose up -d --build app`.

The Android build applies the Google Services Gradle plugin only when `google-services.json` exists. The backend reads the service-account file from `/run/secrets/firebase/service-account.json`. If the file is missing, the backend starts normally and logs that FCM is disabled.

## iPhone web app (Web Push)

The web client uses the standard Web Push API. This is separate from Firebase/FCM and supports iPhone Home Screen web apps on iOS/iPadOS 16.4 or later. On iPhone, a user must add the HTTPS website to the Home Screen, open the installed web app, sign in, and tap the bell to grant notification permission. A normal Safari tab does not receive iPhone web push notifications.

1. Generate one VAPID pair from the backend directory with `./gradlew generateWebPushKeys` (PowerShell: `.\gradlew.bat generateWebPushKeys`).
2. Copy its public and private values to the backend server's `.env` as `WEB_PUSH_PUBLIC_KEY` and `WEB_PUSH_PRIVATE_KEY`. Set `WEB_PUSH_SUBJECT` to a real `mailto:` address or HTTPS contact URL. Keep the private key out of Git and keep the same pair after users subscribe.
3. Set `CORS_ALLOWED_ORIGINS` to the exact GitHub Pages origin. For this GitHub account the default is `https://dedu2ka.github.io`; include any development origin you use, separated by commas.
4. Recreate the backend container so it receives the new values and applies migration `V5__web_push_subscriptions.sql`.
5. Publish the `olo_web` files to a GitHub repository's `main` branch and choose **Settings → Pages → GitHub Actions**. The included workflow builds and deploys the static PWA.

The push payload contains the sender's display name and message preview so the lock-screen notification can show both. Push payloads are encrypted for the browser subscription, but the notification text is visible on the device lock screen.
