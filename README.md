# Family Money for Android

A native Android app (Kotlin + Jetpack Compose) for your Family Money spreadsheet.
It works **offline**, syncs with your Google Apps Script backend, and adds things a web page cannot:
app lock, notifications, a home-screen widget, app shortcuts, camera receipts and a share target.

> **Status:** the whole project was written and statically checked (resources, manifest, imports, cross-file references,
> and the server JSON contract), and the backend was tested end to end, but this code has **not yet been compiled**
> because the environment it was written in has no Android SDK. If the first Gradle build reports an error, copy the
> message and it can be fixed quickly.

## 1. Get the APK (pick one)

### A. No Android Studio needed: build it in the cloud (GitHub Actions)
1. Create a free GitHub account and a **new private repository**.
2. Upload everything in this folder (drag and drop the files, keeping the folder structure; the hidden
   `.github` folder must be included).
3. Open the **Actions** tab, choose **Build Android APK** and press **Run workflow**
   (it also runs on every push). After ~6-10 minutes open the finished run and download the
   **FamilyMoney-apk** artifact (a zip containing `app-debug.apk`).
4. Copy `app-debug.apk` to the phone and open it. Allow "Install unknown apps" for your file manager or browser
   when Android asks.

### B. Android Studio
1. Install Android Studio (Koala or newer), choose **Open** and select this folder.
2. If asked about Gradle, use Gradle **8.9** (Studio offers to create the wrapper for you).
3. Let it sync, connect a phone with USB debugging (or use an emulator) and press **Run**.
   To produce an APK: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

## 2. Connect the app to your spreadsheet

The app needs its own backend door, because Google does not allow signing in inside an app's embedded browser.
You keep your normal web page exactly as it is.

1. Paste the new `Code.gs` and `Index.html` into your Apps Script project (they include the Android API).
2. **Deploy > New deployment > Web app > Execute as: Me, Who has access: Anyone.** Copy the URL that ends in `/exec`.
   (Keep your web page deployment as it was.)
3. Open the web page > **More > Android app > Pair a new phone**, paste that URL, name the phone.
4. Send the link it shows to the phone (WhatsApp, Telegram, e-mail yourself) and tap it. The app opens and connects.
   Or open the app and use **Paste pairing link**.

Every phone gets its own private key. Lost a phone: **More > Android app > Revoke**.

## 3. What the app does

| Area | Details |
|---|---|
| Offline-first | Last data is stored encrypted on the phone. Entries made offline are queued, shown immediately (with a clock), move balances, and are sent in order when you are online. Each has a request id, so a retry never double-posts. |
| Everything from the web app | Transactions (add/edit/delete + Undo), duplicate warning, receipts, credit cards, budgets (month/year, editor), bills (pay + record expense), reconciliation, investments, goals, recurring, quick-add templates, categories & people, trash/restore, CSV and PDF export, insights, net worth. |
| Security | Encrypted storage (Android Keystore), per-phone revocable key, optional fingerprint/face/PIN lock, screenshots and recent-apps preview blocked (can be turned off), HTTPS only, cloud backup disabled. |
| Android extras | Daily bill/budget/card notifications that work offline, background sync every 3 hours, home-screen widget, long-press shortcuts (Add expense / Add income), share a photo to Family Money to attach it to a new expense, camera and gallery receipts, tablet layout with navigation rail, dark mode. |

## 4. Project layout

```
app/src/main/java/com/familymoney/app/
  data/     Models, SecureStore (encrypted prefs + files), Repository (sync engine + offline queue), ApiClient
  logic/    Calc (totals, budgets, offline math, CSV), Fmt/Dates, ImageUtil
  work/     Notifications, SyncWorker, ReminderWorker, Scheduler
  widget/   Home-screen widget
  ui/       AppViewModel, screens (Home, History, Dashboard, Bills, More + panels), sheets, components
app/src/test/  Unit tests for Calc/Fmt/Dates
```

## 5. Release signing (optional)
Without a keystore the release build is signed with the debug key, which is fine for your own family.
For a proper key set the environment variables `FM_KEYSTORE`, `FM_KEYSTORE_PASSWORD`, `FM_KEY_ALIAS`, `FM_KEY_PASSWORD`.
`isMinifyEnabled` is off by default (safer); turn it on in `app/build.gradle.kts` once you have tested a shrunk build.

## 6. Troubleshooting
* **"Unexpected reply from the server"** - the API deployment must be *Execute as: Me* and *Anyone*, and you must use its `/exec` URL.
* **"Invalid or revoked device key"** - pair again with a new link.
* **No notifications** - allow notifications for Family Money in Android settings (Android 13+ asks once).
* After changing `Code.gs`, create a **new version** of the API deployment (Deploy > Manage deployments > Edit > New version).
