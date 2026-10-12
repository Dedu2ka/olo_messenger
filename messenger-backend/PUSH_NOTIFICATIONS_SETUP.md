# Push notification setup

The server and Android app are wired for Firebase Cloud Messaging (FCM), but sending push notifications stays disabled until Firebase credentials are configured.

1. In Firebase Console, create a Firebase project and add an Android app whose package name is exactly `com.example.olo`.
2. Download that app's `google-services.json` into `C:\frontend\app\google-services.json`.
3. In Firebase Console, generate a service-account private key. Keep this key private and copy it to `C:\Users\Стрелов Артем\Desktop\olo_messenger\messenger-backend\firebase\service-account.json`. This file is excluded from Git.
4. Enable the Firebase Cloud Messaging API for the Firebase project.
5. Rebuild/reinstall the Android app and restart the backend with `docker compose up -d --build app`.

The Android build applies the Google Services Gradle plugin only when `google-services.json` exists. The backend reads the service-account file from `/run/secrets/firebase/service-account.json`. If the file is missing, the backend starts normally and logs that FCM is disabled.
