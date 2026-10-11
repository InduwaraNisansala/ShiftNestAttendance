# Activate notifications — version 1.3.7

These changes are supplied as code, not already deployed to your Firebase account. Firebase billing-enabled backend deployment and functioning employee/admin authentication remain prerequisites.

## 1. Deploy the server and database rules

From the extracted ShiftNestAttendance project folder, with Node.js 22 and Firebase CLI installed:

```powershell
firebase login
firebase use shiftnest-attendance
cd functions
npm install
cd ..
firebase deploy --only functions,firestore:rules,firestore:indexes
```

Confirm the intended project before deployment. The commands deploy the supplied project’s backend and restrictive rules/indexes. Do not replace rules with public read/write access.

Firebase Console -> Functions should include registerDevice, leaveAlerts and locationAlerts in asia-south1, alongside the existing lookupEmployee, employeeProfile, leaveOverview, applyLeave, decideLeave, createEmployee and updateEmployee. Firestore/Eventarc trigger deployment and Cloud Messaging API access must succeed; retain CLI errors if deployment fails. Do not change admin membership or delete data to resolve a deployment error.

## 2. Build and install

Open the inner project in Android Studio, sync the added firebase-messaging dependency and build/install the APK. BUILD-APK.bat copies ShiftNestAttendance-v1.3.7.apk only after a successful build. Install as an update using the same signing key where possible. Settings must show 1.3.7.

## 3. Register each signed-in phone

Open the administrator or employee dashboard. Device-token registration runs for the authenticated user. On Android 13+, allow phone notifications. If permission was denied, Settings -> Phone notification settings can enable it. Settings -> Retry notification registration shows the actual registration result. Notification permission affects notification-tray display; the in-app inbox remains available.

Active admin records determine the recipients. Employee decision notifications use the authenticated request owner/current registered employee identity. A token may be reassigned when a different account signs into the same device. Data-only pushes are filtered by signed-in UID/role and ignored after logout.

## 4. Two-phone pilot

1. With a configured allowance/calendar or eligible Sales balance, submit one leave request. Available drops by one; pending rises by one. Confirm admin inbox and pending count. Test foreground and background phone alerts.
2. Reject it: available returns by one, pending drops, used is unchanged. Confirm date release and employee message/inbox.
3. Submit another and approve: pending becomes used; available is not reduced twice. Confirm both phones’ records/messages.
4. For each department, check in at an assigned fixed site and record a fresh accurate GPS update outside. Confirm an admin inbox/push. Repeated outside readings must not repeatedly alert; return inside then exit to test the next alert. Missing/invalid site, stale fix and no-fixed work assignment must not produce a violation.
5. Test notification denial, offline devices, logout/account switch, refresh, read marking and navigation from notification taps. Push delivery depends on notification permission, connectivity and Android power/background restrictions; force-stopping an app can prevent delivery until it is reopened. The durable inbox is the reliable record.

Notifications here use Firebase Cloud Messaging, not additional SMS messages. Existing login OTP SMS billing remains separate. Backend reads/writes/triggers still contribute to cloud usage.

Official documentation:
- https://firebase.google.com/docs/cloud-messaging/android/get-started
- https://firebase.google.com/docs/cloud-messaging/android/receive-messages
- https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
- https://firebase.google.com/docs/functions/firestore-events
