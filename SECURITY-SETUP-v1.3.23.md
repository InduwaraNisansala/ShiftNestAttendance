# Secure attendance — v1.3.23

## What changed

Check-in, checkout and GPS tracking call recordAttendance. Direct client creates, edits and deletes of attendance/latestLocations/locationLogs are denied, including admin clients. Server derives employee identity, official assignment, Colombo date/time and report fields. It rereads active employee/admin link, attendance block, assigned site and leave locks in a transaction. Typed site/location labels remain descriptive and cannot override the official geofence.

Real current GPS is required: mock must be false, finite valid coordinates, accuracy in (0,50] metres, fix age at most 60 seconds and no more than 10 seconds ahead of server time. Fixed check-in requires distance + accuracy <= assigned radius. Checkout and tracking may occur outside the site so employees can record legitimate trips; an open check-in is still required. Site/identity/clock fields supplied by a modified request are rejected.

Production attendance and attendance-message callables enforce App Check and consume limited-use tokens. Missing/unapproved/already-consumed tokens are rejected. Each request also has a transactionally bound immutable receipt so retries cannot overwrite a check-in, checkout or GPS log. Work notice request IDs retain their existing immutable event history and deduplication. Production contains no App Check debug provider or client toggle to disable verification.

## Mandatory production setup (project owner)

1. Back up private records and retain the old app/backend/rules. Test the new deployment in a separate Firebase staging project before a coordinated company rollout. Existing records do not need to be deleted. All clients must upgrade together because the new rules deny the old direct-write clients.
2. Configure your own production google-services.json and signed production APK. Record the real release / Play app-signing SHA-256 fingerprint, not the demo signing key.
3. In Play Console link Play Integrity to the same Firebase/Google Cloud project. In Firebase Console > App Check register that Android app with Play Integrity and its SHA-256. Require PLAY_RECOGNIZED and MEETS_DEVICE_INTEGRITY. Configure distribution deliberately: default licensing requirements can block sideloads; do not relax recognition/device integrity merely to make a development emulator work. Use Play internal testing for staging real phones. No debug provider/tokens are supplied here. Revoke any existing App Check debug tokens for the production app before treating this as a trusted-app policy.
4. In Firestore Console create settings/attendanceSecurity. Add allowedAppIds as an ARRAY of STRINGs containing only approved Android Firebase App IDs (the 1:...:android:... App ID from Project settings > Your apps; not a package name). Keep test/other-platform app IDs out. Missing configuration fails closed. App clients cannot edit this document under the supplied rules.
5. Grant Firebase App Check Token Verifier to the actual runtime service account for second-generation functions so consumeAppCheckToken can verify/consume tokens. Keep service-account keys out of APKs and GitHub.
6. From the inner project, deploy supplied functions AND firestore rules/indexes: firebase deploy --only functions,firestore:rules,firestore:indexes. Run this only against the deliberately selected staging/production project. No deployment, billing changes or cloud configuration were made in this workspace.
7. Install the signed production build on approved physical phones; verify check-in/checkout/Add Location, background tracking and actual notifications. App Check configuration is part of deployment, not completed by extracting the ZIP. Monitor verified requests before enabling Firestore App Check enforcement in the Console; the write-denying rules and callable enforcement are required regardless. Unconfigured App Check will block production attendance instead of silently bypassing verification.
8. Test a re-signed APK and unverified emulator: attendance must fail. Test direct Firestore owner/admin attendance/GPS writes: denied. Test stale/mock/inaccurate/outside GPS, clock/employee/assignment manipulation, token reuse, repeated tap, network retry, inactive employee and revoked admin link. Require correct failures. Verify normal phones in foreground/background and next Colombo day.

## Local demo

Extract the full ZIP into G:\ShiftNestAttendance-v1.3.23 and open its inner ShiftNestAttendance project folder.

Only a Functions emulator with a demo-* project ID can skip attestation. The demo APK uses the existing demo-only Firebase project; no live fallback. Stop the old LOCAL BACKEND, preserve/copy same-brand demo-data when needed, then start the new project's START-DEMO.bat and install its new APK. Use CONNECT-EMULATOR.bat / 127.0.0.1 if required. Run one brand backend at a time. Keep LOCAL BACKEND open. Demo GPS is test data and must not be used as evidence that Play Integrity succeeded.

## Limits and validation

App Check verifies an app/device environment; it does not independently prove physical GPS coordinates. Root/hooking/token theft, GNSS spoofing and abuse from a legitimate device are residual risks. Strong physical-presence assurance needs an independently controlled site factor (for example a managed onsite device), beyond GPS alone. No claim of 100% spoof-proof attendance is made.

81 backend/policy/workflow tests pass per brand using an in-memory Firestore adapter, including actual secure handlers and malicious payload cases. 46 Java sources/resources/manifest checks, extracted client limited-token/GPS payload methods, buttons and reports/PDF checks pass. The updated demo smoke test can verify direct-write denial on real local Auth/Firestore/Functions emulators, but was not run here. Full Android APK build, rules emulator/network integration, live Play Integrity/token consumption and real device/background/push tests were not available. Device attestation requires the setup above and physical-phone tests before company use.

Official documentation:
https://firebase.google.com/docs/app-check/android/play-integrity-provider
https://firebase.google.com/docs/app-check/cloud-functions
