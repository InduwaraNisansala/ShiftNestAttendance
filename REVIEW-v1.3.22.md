# Attendance and notification review — v1.3.22

Fixed phone-notification navigation: location additions, late messages, storage warnings and department calendar announcements now open Notifications rather than the leave screen. Leave requests/decisions still open leave screens; outside/fake GPS alerts still open admin tracking. Existing data and backend handlers are preserved.

Checked: check-in/add-location/check-out enable states, pending requests and date rollover; typed in/out site/location in attendance and daily report/PDF; full late messages before and after attendance; repeated location additions and fixed RED / non-fixed GREEN admin inbox alerts; leave earned every 10 completed Sales workdays and configured annual Office/Hotel quotas; reservations, rejection release and approvals; admin personal leave decision by another admin; removed employee restoration; unique phones; notification account filtering and trigger retry deduplication.

Demo notifications are durable in-app inbox items. Demo does not send real SMS or phone push. Production requires the supplied backend/rules deployment and configured Authentication/FCM; actual delivery and permissions must be checked on real phones.

Attendance has no automatic expiry or client deletion. Storage warnings require an admin-configured budget; the document payload estimate is not actual Firebase free space. Keep private backups and check real usage/billing separately. There is no automatic backup implemented here.

Security limit: ordinary app GPS/radius and mock checks do not prove that a location from a modified client is genuine. Client attendance creates and GPS readings remain client-submitted under ownership/time rules. A stronger production guarantee requires server validation of attendance fields/GPS policy and device attestation; no guarantee of spoof-proof attendance is made.

Manual device checks still needed: first check-in and repeated tap, 3 added locations, checkout then reopening/next day; real phone foreground/background notifications and tap destinations; PDF export; Android location permission/GPS and background tracking; offline/retry; fresh production SMS. No full Android build, live Firebase/rules emulator, real phone push or Windows launcher test was available in this review.

Extract company into F:\EmpireAttendance-v1.3.22, portfolio into G:\ShiftNestAttendance-v1.3.22. Open the inner project. Stop the old local backend, keep a backup of its demo-data if needed and copy it only to the same brand's new project, run START-DEMO.bat to build/install this version. Run one brand backend at a time. If the emulator requires reverse ports, run CONNECT-EMULATOR.bat and use 127.0.0.1. Keep LOCAL BACKEND open.
