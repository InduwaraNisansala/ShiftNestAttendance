# v1.3.24 — demo Firestore port fix

Firestore demo now uses 8180 because the user's AgentService occupies 8080. This update changes the Android demo connection, startup checks, emulator config, seeding and ADB reverse together. Install the new demo APK; the old app still uses 8080. AgentService is not stopped or modified. Production Firebase endpoints and security policy are unchanged.

# v1.3.24 — server attendance and Play Integrity protection

Read SECURITY-SETUP-v1.3.24.md before live use. This is a coordinated APK + backend + rules update. Old APK attendance/GPS writes will be denied by the new rules. Deploying only the APK or only the rules is incomplete. Local demo remains isolated and is not proof of production device attestation.

# ShiftNest Attendance

Start with README.md and UPDATE-READ-ME.txt. This independent portfolio copy uses only fictional local demo data and has no company Firebase configuration.
