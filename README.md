# ShiftNest Attendance

![ShiftNest](branding/shiftnest-icon.png)

An independent portfolio attendance app built with Android Java/XML and Firebase. This is a demonstration project, not an official company application or a confirmed commercial deployment.

## Features

- Employee registration managed by administrators; phone login and OTP verification.
- Attendance with location permissions, assigned-site radius checks, history and reports.
- Check-in success disables Check In and enables Check Out; checkout success disables both for the day. Server state is restored when reopening.
- Sales Officers earn one leave day for every ten distinct completed attendance days. Pending requests reserve balance; rejected requests release it.
- Office/Hotel staff use the annual allowance and opening usage entered by an administrator for the selected year.
- Administrator approval records, notifications, department calendars and personal attendance.
- Separate local-demo and production build flavors.

## New attendance features

- Late explanations for employees and linked administrators, saved before check-in and displayed automatically when attendance/daily reports load; full daily PDF message appendix.
- Outside-destination GPS notices after check-in: server-selected red for fixed sites and green for no-fixed sites; repeat additions and duplicate prevention.
- Admin-only data budget screen with an explicitly labelled document-payload estimate and warning alerts. It does not measure actual cloud free space. No attendance cleanup is performed.

Read NEW-FEATURES-v1.3.21.txt for usage, protected retention, backup instructions, scheduled/manual storage checks and production setup. Existing location restrictions continue; outing messages do not approve travel.

## Run the local demo on Windows

1. Install Android Studio and open this inner ShiftNestAttendance folder. Use Java 21 for Gradle.
2. Start one Android emulator, then run START-DEMO.bat. It prepares the local backend, seeds fictional test accounts, builds and installs demoDebug. Keep LOCAL BACKEND open.
3. Run CONNECT-EMULATOR.bat. Open ShiftNest Attendance Demo and use 127.0.0.1. Save/force-stop/reopen if the app requests it, then Connect.
4. Admin: admin@shiftnest.test / ShiftNestDemo123!; second approver: approver@shiftnest.test / ShiftNestDemo123!.
5. Employee test phones: 0770000001 (Sales), 0770000002 (Office), 0770000003 (Hotel). OTP appears in the local terminal or emulator UI; no real SMS is sent.

After restarting the emulator, run CONNECT-EMULATOR.bat again. START-BACKEND-ONLY.bat starts the backend without rebuilding. Stop the company demo before starting this copy: both use ports 9099/8180/5001.

This copy uses com.shiftnest.attendance.demo and demo-shiftnest-attendance. It has its own app storage and new local test dataset. Do not copy the company's demo-data, credentials or employee data into this repository.

## Production / distribution

The original live Firebase configuration is omitted. To use production, create your own Firebase project, register com.shiftnest.attendance, add your own app/src/production/google-services.json, signing fingerprints and required services, configure your own Maps key, and deploy this backend/rules/indexes to that explicitly selected project. Configure real authentication, billing and release signing before testing a signed release. Local demo success is not a production release test.

Demo release variants are intentionally disabled; the local demo is for development. Publishing to GitHub is different from distributing an APK through a store. This archive prepares source for review; it does not publish a repository or an app store listing.

## Validation and limits

Policy/workflow tests, Java source/resource validation and extracted attendance-state scenarios were run when packaging. Full Android build/device testing was unavailable in the packaging environment; run the demo and production checks on your PC before distributing.

Suggested CV description: “Developed an Android attendance and leave-management portfolio app using Java, Firebase Authentication, Firestore, Cloud Functions, location checks and PDF reporting.” Describe it as a prototype unless a real deployment is confirmed.

The name, artwork and sample data here are independent. Rebranding does not establish ownership or permission to publish source: verify your rights to the original code and third-party assets before public release. No company endorsement is claimed. No software license is assumed; choose a license after confirming those rights.

Check-in/out require typed Site name and Location. Add Location supports repeated visits without a return requirement; each visit has its own preserved event and server-selected red/green admin notification.

Dashboard order: Check In > Add Location > Check Out. The return button is removed. Add Location enables only during open confirmed attendance. Daily charts include an employee site/location table and full PDF details.
