# Phone screenshot errors — what to check

## 1. Registration check: NOT_FOUND

In Firebase Console, select the same project named in `app/google-services.json` (the supplied project is shiftnest-attendance). Check Functions: `lookupEmployee`, `employeeProfile`, `leaveOverview`, `applyLeave`, `decideLeave`, `createEmployee` and `updateEmployee` must be deployed in asia-south1. Deployment needs a billing-enabled Blaze project. Enabling the Phone sign-in provider alone does not deploy them.

If the functions exist, check the employee in Firestore: phone must match the app-normalized +94 format, active must be boolean true, and exactly one active employee must match. Do not delete existing employee documents or recreate IDs to “fix” login: history is linked to the employee ID.

Follow README-START-HERE.md for the existing backend deployment steps. Publishing this Java update does not publish Firebase functions/rules/indexes.

## 2. Client offline / admin authentication timeout

Try the phone on mobile data, then a different Wi-Fi connection. Check app mobile-data/background-data restrictions and whether VPN, Private DNS, captive portal or network policy is blocking Firebase. A working browser or cached history alone does not confirm Firebase connectivity. Try a fresh admin login, wait for the complete popup and retain the exact error.

The supplied Firebase rules allow an authenticated admin to read their own admins/{UID} document. The account must exist in Authentication with Email/Password enabled, and admins/{UID}.active must be boolean true. Use the supplied restrictive rules; do not replace them with public allow-read/write rules.

## 3. GPS unavailable

Enable phone Location and allow location access for this app; use Precise location for fixed sites. Try an open area and wait for a current fix. No-fix does not create a successful check-in. Site creation can use manual latitude/longitude without visiting the site.

## 4. Leave disabled

An Office/Hotel employee whose allowance remains # cannot be given a calculated entitlement. Admin must configure the correct year/allowance/opening used count. Admin also confirms coverage in Settings -> Company holiday calendar. Sales leave still needs 10 eligible completed days per entitlement. Keep these restrictions; fix configuration instead of bypassing eligibility.

## 5. Real SMS

Phone sign-in must be enabled and the required billing, Sri Lanka SMS region policy and installed APK SHA-1/SHA-256 fingerprints configured. Test phone numbers use a configured test code, not real SMS. See the official links in README-START-HERE.md.
