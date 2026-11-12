# Mailbox, native Android app

A native Material 3 client (Kotlin, Jetpack Compose) for the mailbox server in the repo root. It talks to the same
REST API as the web app (`/api/...`, Firebase ID token as a Bearer token), so the web UI, the server and
`android-app/` (the Capacitor shell) stay as they are.

## What is in it

| Area | Native implementation |
| --- | --- |
| Theme | Graphite and teal palette as M3 colour roles, light and dark, optional Material You dynamic colour; Lexend typeface; avatars use the scheme's tonal pairs |
| Navigation | Modal drawer on phones, navigation rail on medium windows, rail plus list and message side by side on wide ones; badges on Inbox and Drafts |
| Search | M3 `SearchBar` with a full search view: recent searches and live matches |
| Motion | Container transform from a list row into the message, predictive back, collapsing large top bar in Settings |
| Sheets | Bottom sheets for attachment actions, forwarding, templates and the From address |
| List | Day headers, swipe to delete / mark read, long-press multi-select, pull to refresh, paging |
| Reader | Header that collapses while you read, attachment chips, HTML in a script-free WebView with a dark view (toggle in the menu) |
| Compose | Address chips, From alias, Cc/Bcc, attachments (15 MB each), templates, autosaved drafts, reply / reply all / forward |
| Push | FCM with Mark read / Delete / Reply notification buttons, grouped notifications |
| Intents | `mailto:` links and "Share" open the composer |
| Offline | Last lists and opened messages are cached on the device |

## Install over the Capacitor app

It uses the same application id (`dev.amit.mail`) and the same signing key and `google-services.json`, so it installs
over the existing app as an update. Its `versionCode` is `1000 + run number` to stay above the Capacitor builds.

## Build

The workflow `.github/workflows/android-native.yml` builds it (Actions tab, or push a tag `native-v*`). It needs the
same repository secrets as the Capacitor build: `GOOGLE_SERVICES_JSON`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`.
Google sign-in needs the release key's SHA-1 registered in the Firebase project (already true for the current app).

Locally: put `google-services.json` in `app/`, then `gradle :app:assembleDebug` (Android SDK 35, JDK 17).

## Layout

```
app/src/main/java/dev/amit/mail/
  MailApp.kt, MainActivity.kt
  data/   Api (OkHttp), Auth (Credential Manager + Firebase), Models, Cache, Prefs, Fmt
  push/   MailMessagingService, MailActionReceiver, MailBus
  ui/     MailViewModel, MailRoot, MailboxScreen, ReaderScreen, ComposeScreen, SettingsScreen, SignInScreen, Theme
```
