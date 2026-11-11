# DressCast

Android app that sends you a notification every morning at 8:00 with the day's weather and what to wear.

Every day at 08:00 your phone gets a notification with today's weather and what to wear.
Weather comes from Open-Meteo (free, no account or API key needed). Default city: Athens. You can change it in the app.

## Get the APK (easiest way, no install needed)
1. Create a free GitHub account and a new repository.
2. Upload all the files in this folder, including the hidden `.github` folder.
3. Open the **Actions** tab. The "Build APK" job runs automatically (about 3–5 minutes).
4. Open the finished run and download **DressCast-apk** from the Artifacts section. It's a zip with the APK inside.

## Or use Android Studio
Open this folder, then choose Build > Build APK(s).

## On your phone
- Install the APK. You'll need to allow "Install unknown apps".
- Open the app once and allow notifications.
- Enter your city and tap the button. You get a test notification right away.
- If the 08:00 notification doesn't show up, set the app's battery usage to "Unrestricted" (Settings > Apps > DressCast > Battery). Xiaomi, Samsung and Huawei phones are the most aggressive about stopping background apps.
