# Easier v1.3.21 entry point

Read OPEN-ME-FIRST.txt and run START-DEMO.bat from the extracted project. It automates tool setup, backend startup/readiness, seeding and APK build when the SDK is available. The manual steps below remain an alternative. A local backend window must remain open; this is not a hosted company service.

# ShiftNest Attendance v1.3.21 - free local demo setup

This runs Firebase Authentication, Firestore, rules and Cloud Functions on YOUR PC. It does not enable billing or deploy to your real ShiftNest Attendance project. All demo commands explicitly use the non-existent project ID `demo-shiftnest-attendance`.

## 1. Extract and install prerequisites

Extract the combined ZIP to `F:\ShiftNestAttendance-v1.3.21`. Open its inner `ShiftNestAttendance` folder (the folder containing `settings.gradle.kts`) in Android Studio.

Install Node.js 22 LTS and Java 21. Android Studio's bundled `jbr` can be used if it is Java 21 or later and compatible with your Firebase CLI. In a fresh terminal, check `node --version` and `java -version`. If required set JAVA_HOME to the JDK folder, not its bin folder. The start helper checks common Android Studio install paths; if Firebase reports a Java version error, correct JAVA_HOME before retrying.

Run `DEMO-SETUP.bat` once. It installs Firebase CLI and the backend's Node dependencies using your internet connection. It does not run `firebase login`, `firebase init`, `firebase deploy` or upgrade a Firebase project. The first emulator startup also downloads local emulator components; allow free disk space on the PC.

## 2. Start the local backend

Run `DEMO-START.bat`. Keep that window open. Wait for ALL emulators to be ready:

- Authentication: 9099
- Firestore: 8180
- Functions: 5001
- Local Emulator UI on the PC: http://127.0.0.1:4000

Auth/Firestore/Functions bind to the local network so your phone can reach them. Use a trusted PRIVATE home/office network. Do not port-forward these ports or expose them to the Internet. Emulator authentication and admin interfaces are for testing only. Allow the relevant Java/Node processes through Windows Firewall on the private network if prompted; do not disable the entire firewall.

## 3. Create demo users

Once startup is ready, run `DEMO-SEED.bat` in a SECOND window. This creates only local test records, without removing your existing real Firebase data. It is safe to rerun: existing seeded records are not overwritten.

| Login | Demo credentials / role |
| --- | --- |
| Admin | `admin@shiftnest.test` / `ShiftNestDemo123!` (username `admin` also works) |
| Second admin for own leave decisions | `approver@shiftnest.test` / `ShiftNestDemo123!` (username `approver`) |
| Employee 1 | `0770000001` - Sales Test / Sales Officers |
| Employee 2 | `0770000002` - Office Test / Office Staff |
| Employee 3 | `0770000003` - Hotel Test / Hotel Staff |

These public passwords are LOCAL DEMO credentials only. Never create those accounts/passwords in a real project. All three employees start with No Fixed Location so you can test with actual phone GPS anywhere. Office/Hotel quota 20 is example test data, not confirmed company entitlement. Sales receives ten example completed work days to earn one leave. Current-year holiday coverage is example data with no weekly blocked days; add holidays to test refusal.

## 4. Build the correct Android APK

Android Studio > Build Variants > select `demoDebug` for app. Run on the connected phone, OR run `BUILD-DEMO-APK.bat` after Gradle sync/SDK setup.

The helper builds `:app:assembleDemoDebug`. APK output: `app\build\outputs\apk\demo\debug\app-demo-debug.apk`. It also copies `ShiftNestAttendance-Demo-v1.3.21.apk` into the project root.

The phone app label is **ShiftNest Attendance Demo** and its package is `com.shiftnest.attendance.demo`. It can coexist with the original app and does not share login preferences. Do not open the original ShiftNest Attendance icon for this local test.

Production variant remains `productionDebug` / `productionRelease`. `BUILD-APK.bat` now explicitly builds productionDebug. Demo release variants are disabled. Production still needs deployed live backend and SMS setup; emulator tokens/test passwords do not work there.

## 5. Connect the physical phone

Connect PC and phone to the SAME trusted Wi-Fi (do not use mobile data only). On Windows open CMD, run `ipconfig`, find IPv4 Address under the active Wi-Fi adapter, for example `192.168.1.5`. Do not use the default gateway, VPN, disconnected Ethernet or virtual adapter's address.

Open ShiftNest Attendance Demo, enter that PC address and tap **Connect & open demo login**. Enter only the numeric IP; no http:// prefix or port. This configures Auth, Firestore and Functions together; it does not bypass registration or OTP verification. For an Android virtual device, use `10.0.2.2` instead.

The banner always marks demo/local data. You can save a different PC IP with **Save new PC address (restart required)**, then Phone Settings > Apps > ShiftNest Attendance Demo > Force stop and reopen. Log out before switching servers; clear the demo app's storage when using a different test dataset. Settings has a Demo backend connection button.

## 6. Test employee phone login

Enter one seeded phone > SEND OTP. An active registration must be found before Verify OTP opens. Look in the running PC emulator terminal for the six-digit test code, or open the local Emulator UI's Authentication page. Enter the CURRENT code on the phone > VERIFY OTP.

No real SMS is sent. Do not expect one or use a hardcoded `123456`. Each new verification request can have another code. New employees added in the demo admin app use the same local verification flow.

## 7. Test admin and leave

Use ADMIN LOGIN with the local demo admin email/password. See the seeded employees, add/edit employees and sites, check reports, save department notices and test archive/restore.

For employee leave, submit a date/reason, then sign into the admin on another phone or switch accounts after logging out. Leave Sheet > Pending approvals > tap row > ask/record a DEMO Chairman decision > Approve/Reject. Return to employee and refresh My Leave; verify reserved balance and rejection refund. The local Firestore triggers create saved inbox notifications. Cloud push is disabled in demo, so use the dashboard/in-app Notifications.

For admin My Attendance, link the admin to one seeded employee's phone (for example Office Test). Own leave must be decided by the second local admin `approver@shiftnest.test`. This is test-only linking; do not link another person's real record in production.

For fixed-site GPS: Add Location using the phone's real GPS, enter a radius and save, then edit a seeded employee to that fixed site. Verify check-in inside and outside the radius. Real phone Location/permissions are still required. Google Map tiles are disabled in demo; coordinates, status tables and geofence checks remain available. Real FCM push and SMS delivery cannot be assessed with this test.

## 8. Stop and keep test data

Use Ctrl+C in DEMO-START.bat and wait for export to finish. Data is saved to local `demo-data` and imported on the next start. Force-closing the terminal or switching off the PC may lose changes not exported. The app disables Firestore disk persistence for demo to avoid stale records from an old emulator session.

To intentionally reset ONLY the local demo, stop the backend first and rename the project root's `demo-data` folder (for example `demo-data-backup`). Clear ShiftNest Attendance Demo app storage, start again and reseed. Do not delete real Firebase collections.

## Common problems

- Number/service unavailable: verify DEMO icon, all three emulators ready, exact PC IP, seed completion and private-network firewall permissions.
- Phone cannot connect: PC must stay on; ensure same network and that Wi-Fi guest/client isolation is off. Test the local Emulator UI on the PC first. Do not enable public port forwarding.
- Java error: current Firebase Firestore emulator needs Java 21. Check the Java shown in the startup terminal.
- Missing old cloud employees/admin: expected; this is a separate local database. Use the seeded test records or add records locally.
- No push/SMS/map tiles: expected demo limitations. In-app notifications, actual GPS and report/PDF actions are still testable.
- Build error: send the FIRST Android Studio Build error. Source checks are not an Android SDK build.

Official references: https://firebase.google.com/docs/emulator-suite/connect_auth ; https://firebase.google.com/docs/emulator-suite/connect_firestore ; https://firebase.google.com/docs/emulator-suite/connect_functions
