exports.checkEnvironment=env=>{
 if(env.GCLOUD_PROJECT!=='demo-shiftnest-attendance'||env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:8180'||env.FIREBASE_AUTH_EMULATOR_HOST!=='127.0.0.1:9099')throw new Error('Seeding allowed only on the local demo Auth and Firestore emulators');
 return env.GCLOUD_PROJECT;
};
