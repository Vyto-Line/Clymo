# DressCast

Android app that sends you a notification every morning at 8:00 with the day's weather and what to wear.

What's inside:
- Today: an animated weather picture, what to wear (tick items off), and the day in three parts
- Tap the picture for details: hour-by-hour chart, why each item was chosen, wind, UV, humidity, sunrise/sunset, rain, air quality, compared with yesterday
- Week ahead, a home screen widget, a rain warning an hour before it starts, and an optional 21:00 preview of tomorrow
- Learns from "too cold / too warm" answers, and only counts the hours you're usually outside
- Works offline with the last forecast; English and Greek; light and dark mode

Weather data from Open-Meteo (free, no account or API key). Fonts: Inter and Space Grotesk (SIL Open Font License), downloaded during the build.

## Get the APK
On your phone, open https://github.com/Vyto-Line/dresscast/releases/latest and tap **DressCast.apk**.
Each change to the code builds a new version there automatically. Install it over the old one and your settings stay.

## Or use Android Studio
Open this folder, then choose Build > Build APK(s).

## On your phone
- Install the APK. You'll need to allow "Install unknown apps".
- Open the app once and allow notifications.
- Enter your city and tap the button. You get a test notification right away.
- If the 08:00 notification doesn't show up, set the app's battery usage to "Unrestricted" (Settings > Apps > DressCast > Battery). Xiaomi, Samsung and Huawei phones are the most aggressive about stopping background apps.
