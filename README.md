# CityFix

Android app (Kotlin, Jetpack Compose) for reporting and following problems in the public space of a municipality.
Citizens report incidents with photos or videos and a location; municipal operators validate citizens, manage
categories and move each ticket through its lifecycle; anonymous visitors near Coimbra see public statistics and
public incidents.

Practical work for **Mobile Architectures** (ISEC, 2026/27) by Musa Baba Akanya and Artem Snigur.
The technical report is in [`docs/TECHNICAL_REPORT.pdf`](docs/TECHNICAL_REPORT.pdf).

## Build and run

- Android Studio with JDK 17+; open the project folder and run the `app` configuration, or
  `./gradlew assembleDebug`.
- Android 7.0 (API 24) or newer. The app uses the live Firebase project (`app/google-services.json`), so it needs
  internet for the first sign-in; afterwards it also works offline.
- Tests: `./gradlew testDebugUnitTest` (unit) and `./gradlew connectedDebugAndroidTest` (needs a device; uses the
  real Firebase project).

## Demo accounts

| Role | Email |
|---|---|
| Operator | `operator@cm-coimbra.pt` |
| Citizen (active) | `rui.demo@example.com` |
| Citizen (pending) | `joana.demo@example.com` |

The password is given at the defense. Demo addresses skip the email confirmation and the sign-in code; new accounts
need a real mailbox.

## Project layout

| Path | Content |
|---|---|
| `app/src/main/java/.../ui` | Compose screens and ViewModels, navigation (`ui/navigation/AppShell.kt`) |
| `app/src/main/java/.../data` | Models, repository interfaces, Firebase and in-memory implementations |
| `app/src/main/java/.../worker` | WorkManager jobs: media upload, periodic ticket check |
| `app/src/main/java/.../push`, `notifications`, `monitoring` | Push messages, local notifications, impact detection and operator geofence |
| `server/relay` | Google Apps Script web app used instead of paid Firebase services: push, sign-in codes, videos in Google Drive |
| `firestore.rules` | Firestore security rules |
| `docs` | Technical report and screenshots |
