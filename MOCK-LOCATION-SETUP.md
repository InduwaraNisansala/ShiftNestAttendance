# v1.3.9 — Mock GPS protection

Android-marked mock/test-provider locations are detected with AndroidX LocationCompat.isMock.
- Check-in: reject the reading before site lookup or attendance writes. Show an explicit blocked message, then attempt an authenticated admin report. A reporting failure does not unblock attendance and is shown to the employee.
- Background tracking: discard mock readings before location/site writes for all departments, including no-fixed-site staff. Show a local warning. Keep existing attendance intact; continue listening for a real GPS reading. Retry a failed report after 60 seconds while mock readings continue; successful reports are limited locally to once per 5 minutes.
- Backend reportMockLocation derives the employee from the signed-in phone number and active employee record. Ignore supplied employee IDs/names. Deduplicate reports per employee per server 5-minute bucket (up to two alerts near a bucket boundary).
- mockLocationAlerts writes each active admin's durable notification inbox and attempts an FCM push. Opening the alert opens Location Tracking. The inbox message identifies the employee, department and check-in/tracking source. It does not treat mock coordinates as the employee's actual position.
- Client writes to mockLocationReports are denied by Firestore rules.

## Required setup
Install/build this project, then deploy the updated backend and rules from the project root:

```sh
firebase use shiftnest-attendance
cd functions
npm install
cd ..
firebase deploy --only functions,firestore:rules,firestore:indexes
```

This includes new reportMockLocation and mockLocationAlerts functions in asia-south1. The new releaseAttendanceBlock callable must also be deployed. Existing Firebase billing/deployment prerequisites still apply. Allow notifications on the admin phone and register it using Settings > Retry notification registration. APK installation alone does not deploy the backend. See NOTIFICATIONS-SETUP.md.

## Two-phone acceptance check
1. With real GPS, verify the employee can check in as usual at the assigned site.
2. On a test employee phone, select a mock location provider in Android Developer Options. Try check-in with a marked mock reading: expect blocked message, no new attendance entry, then admin inbox and permitted push notification.
3. With an existing check-in and tracking active, enable mock GPS: expect fake readings discarded, existing check-in preserved and admin tracking alert. Repeated readings within the same bucket should not create duplicate inbox items.
4. Disable mock GPS and request a real reading: normal tracking resumes. Check-in requires admin release of a recorded block as well as real GPS. Missing GPS and ordinary inaccurate GPS alone must not generate a fake-location accusation.
5. Test without connectivity: check-in stays blocked; admin reporting failure is shown. Background retries while running and receiving mock readings. No promise of offline or force-stopped delivery.

## Validation and limits
46 backend tests pass, including authenticated identity, duplicates, inactive admins/employees, all departments and unchanged attendance. 33 Java files parse and XML/resource references resolve. Android compilation, real-phone detection, Firestore rules emulator, deployed functions and live push delivery were not run here.

This checks Android's mock marker. Rooted/modified devices may hide it, and custom clients may omit reports. This release is not remote device attestation or proof that all unmarked GPS coordinates are genuine. No detection is based merely on enabling Developer Options. No existing attendance or coordinates are erased automatically.

## Company approval / forgiveness
Successful mock reports set employees/{id}.attendanceBlocked=true. Firestore rules deny new attendance creation while this flag is true, even from another phone. Existing check-in and checkout records are retained. Existing attendance can still be checked out normally.

Admin flow: Location Tracking > Refresh locations > select employee row (red BLOCKED: FAKE GPS status) > Company approval: unblock selected employee > enter approval reason (3–500 characters) > Approve unblock. The button is available even when no GPS history exists. The backend verifies active admin access, clears the block and writes attendanceBlockReleases with employee ID, admin UID, reason and server time. Repeating a release does not duplicate the audit. Employee check-in displays the company-approval requirement until released. A fresh mock report immediately blocks again, including within the same alert deduplication bucket. Releasing never authorizes mock readings.

If the phone cannot reach the backend, that detected check-in remains locally blocked and the app reports that the admin alert could not be confirmed. Persistent cross-device blocking requires a successful backend report. There is no remote attestation against a modified client.
