const {test}=require('node:test'),assert=require('node:assert/strict');
const {checkEnvironment}=require('./seed-policy');
test('seed refuses production project and absent/remote emulator addresses',()=>{
 for(const env of [{},{GCLOUD_PROJECT:'shiftnest-attendance',FIRESTORE_EMULATOR_HOST:'127.0.0.1:8180',FIREBASE_AUTH_EMULATOR_HOST:'127.0.0.1:9099'},{GCLOUD_PROJECT:'demo-shiftnest-attendance',FIRESTORE_EMULATOR_HOST:'real.example:8180',FIREBASE_AUTH_EMULATOR_HOST:'127.0.0.1:9099'}])assert.throws(()=>checkEnvironment(env));
 assert.equal(checkEnvironment({GCLOUD_PROJECT:'demo-shiftnest-attendance',FIRESTORE_EMULATOR_HOST:'127.0.0.1:8180',FIREBASE_AUTH_EMULATOR_HOST:'127.0.0.1:9099'}),'demo-shiftnest-attendance');
});
