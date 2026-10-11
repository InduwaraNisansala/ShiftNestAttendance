# Department calendar notices: required deployment

Use the supplied Firebase project/source configuration. Deploy the updated functions and rules from the inner ShiftNestAttendance directory:

```
firebase deploy --only functions,firestore:rules
```

This includes publishDepartmentNotice (callable) and departmentNoticeAlerts (Firestore trigger) in asia-south1, and changed leaveOverview/applyLeave/decideLeave/createEmployee/updateEmployee behavior. The Android APK must also be rebuilt and installed. The backend and client must both be updated.

Cloud Functions deployment requires the appropriate Firebase billing plan; this source update neither enables billing nor deploys anything. Missing services/rules must be configured before a phone test can pass. See the existing phone/SMS and notification setup guides.

Private data paths:
- departmentCalendars/{Sales|Office|Hotel}/days/{yyyy-MM-dd}: latest saved day; authenticated read, server writes only.
- departmentNotices/{requestId}: admin-only readable outbox; server writes only.
- employeeNotices/{employeeId}/items/{requestId}: readable/mark-read only by the current active employee through phone ownership; server creates.
- Existing notifications/{authUid}/items contains leave decision alerts. Android combines both sources.

Employee-ID notice inboxes do not depend on whether the employee has signed in before. Push delivery requires a current phone-auth user, registered device and notification permission. Employee IDs must remain attached to the same person; edit an existing employee rather than reusing their ID for someone else.

Department holidays supplement global holidays and confirmed coverage. A Working day notice does not unblock a global holiday. Times are scheduled same-day times; calculated duration does not subtract breaks or alter GPS/attendance records. Notice delivery is asynchronous. A queued confirmation is not proof of delivery; verify the employee inbox and phone tray separately.
