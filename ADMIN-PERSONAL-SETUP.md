# Admin personal attendance — v1.3.15

1. Sign in as an active admin. In Add Employee, create your own employee record once (or edit the existing record): name, your unique phone number, correct role, fixed work location, and confirmed Office/Hotel annual allowance when relevant.
2. Open Admin Dashboard > My Attendance. If not linked, enter the phone number belonging to your own employee record and select Link my employee record > Confirm. Do not link another person's record.
3. After linking, My Attendance opens your personal dashboard without a second login or OTP. The server resolves your active employee record from your authenticated admin UID; client-supplied employee IDs cannot select another employee.
4. Use Check In, Check Out, My Leave and Attendance History under the normal employee attendance/location/leave checks. Back to Admin Panel returns to management without signing out. Settings remains available.
5. To record the Chairman decision on your own pending leave, another active administrator must use Leave Sheet > select request > Approve/Reject with the Chairman decision. Your own admin account cannot approve or reject its own request. Pending/approved/rejected messages and leave reservations follow the existing workflow.

The link is one admin UID to one active employee record and cannot be changed to another employee from this screen after linking. A company-authorized backend correction is needed for a mistaken link. Archived/inactive employees and inactive administrators cannot use personal access. Existing admin email/password login remains the login for both views.

## Deployment

In the Firebase project root, run: firebase deploy --only functions,firestore:rules

Deploy before opening My Attendance: employeeProfile, linkAdminEmployee, applyLeave, decideLeave and notification behavior have changed, as have the rules allowing the linked administrator's employee attendance writes. Existing Firebase billing, Phone Auth and messaging prerequisites remain. No live project was modified here.

## Holiday policy

Sales Officers: ten completed qualifying working days earn one leave, holiday/weekly holiday/coverage restrictions and Sales one-person-per-date reservation continue.
Office/Hotel: configured annual allowance, opening usage and pending/approved usage determine remaining leave. These employees are not blocked merely because the selected date is a company/department holiday. Department notices still reach their selected employees. Invalid/past leave dates and insufficient balance are rejected for everyone.

## Phone acceptance

Verify first link, reopen My Attendance, check-in/out at your fixed location, return to Admin Panel, submit a leave, reject own approval, then record the decision using a second admin. Check employee balance and notification after rejection/approval. Test Sales holiday refusal and Office/Hotel holiday leave within quota. Check actual Firebase rules and real push delivery in the deployed project.
