'use strict';
// Never accepts arbitrary cloud project IDs or remote hosts.
const {checkEnvironment}=require('../demo/seed-policy');
const projectId=checkEnvironment(process.env);
const {initializeApp}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const policy=require('./leave-policy');
initializeApp({projectId});const auth=getAuth(),db=getFirestore();
async function user(uid,fields){try{return await auth.getUser(uid);}catch(e){if(e.code!=='auth/user-not-found')throw e;return auth.createUser({uid,...fields});}}
async function once(path,value){const ref=db.doc(path);await db.runTransaction(async t=>{const doc=await t.get(ref);if(!doc.exists)t.set(ref,value);});}
async function main(){
 for(const [uid,email] of [['demo-admin','admin@shiftnest.test'],['demo-chairman-recorder','approver@shiftnest.test']]){
  await user(uid,{email,password:'ShiftNestDemo123!',displayName:'Demo '+uid});
  await once('admins/'+uid,{active:true});
  await once('adminUsernames/'+(uid==='demo-admin'?'admin':'approver'),{active:true,email});
 }
 const year=Number(policy.today().slice(0,4));
 for(const [id,name,phone,role] of [['demo-sales','Sales Test','+94770000001','Sales Officer'],['demo-office','Office Test','+94770000002','Staff Officer'],['demo-hotel','Hotel Test','+94770000003','Hotel Officer']]){
  await user(id,{phoneNumber:phone,displayName:name});
  const quota=role==='Sales Officer'?{}:{annualLeaveYear:year,annualLeaveAllowance:20,annualLeaveUsedBeforeApp:0};
  await once('employees/'+id,{name,phone,role,active:true,archived:false,employeeNumber:id,workLocationType:'no_fixed',locationId:'',locationName:'No Fixed Location',...quota});
  await once('employeePhones/'+phone,{employeeId:id,phone});
 }
 await once('settings/leavePolicy',{holidayCoverageStart:year+'-01-01',holidayCoverageEnd:year+'-12-31',blockedWeekdays:[]});
 // Demo-only historic records earn one Sales leave. Keep this idempotent after user edits.
 const marker=db.doc('demoSetup/initialAttendance');
 if(!(await marker.get()).exists){
  const batch=db.batch();
  for(let i=1;i<=10;i++){const date=new Date(Date.parse(policy.today()+'T12:00:00Z')-i*86400000).toISOString().slice(0,10);batch.set(db.doc('attendance/demo-sales_'+date),{employeeId:'demo-sales',employeeName:'Sales Test',employeeRole:'Sales Officer',phone:'+94770000001',date,checkIn:'08:00:00',checkOut:'17:00:00',status:'Present',workStatus:'Completed',locationId:'',locationName:'DEMO historical work day',createdAt:FieldValue.serverTimestamp()});}
  batch.set(marker,{seeded:true});await batch.commit();
 }
 console.log('LOCAL DEMO SEEDED. No live project was accessed.');
 console.log('Admin: admin@shiftnest.test / ShiftNestDemo123!');
 console.log('Other decision recorder: approver@shiftnest.test / ShiftNestDemo123!');
 console.log('Employee phones: 0770000001, 0770000002, 0770000003');
 console.log('Office/Hotel allowance 20 and Sales work history are example TEST values only.');
 console.log('OTP: PC emulator terminal, or http://127.0.0.1:4000/auth');
}
main().then(()=>process.exit(0)).catch(e=>{console.error('Demo seed failed:',e.code||e.message);process.exit(1);});
