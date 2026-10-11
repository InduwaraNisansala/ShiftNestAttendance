'use strict';
// Full local emulator workflow; fails closed before any network request to a real project.
require('./seed-policy').checkEnvironment(process.env);
const assert=require('node:assert/strict');
const project='demo-shiftnest-attendance',authBase='http://127.0.0.1:9099',storeBase=`http://127.0.0.1:8180/v1/projects/${project}/databases/(default)/documents`;
async function request(url,body,token,method=body?'POST':'GET'){
 const r=await fetch(url,{method,headers:{'content-type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(30000)});
 const data=await r.json();if(!r.ok)throw new Error(JSON.stringify(data));return data;
}
const direct=process.env.SHIFTNEST_DIRECT_FUNCTION_TEST==='1';
const handlers=direct?require('../functions/index'):null;
const adminRequire=require('node:module').createRequire(require.resolve('../functions/package.json'));
async function call(name,data={},token){
 if(!direct){const r=await request(`http://127.0.0.1:5001/${project}/asia-south1/${name}`,{data},token);if(r.error)throw new Error(JSON.stringify(r.error));return r.result;}
 // Direct-handler mode uses real Auth/Firestore emulators, but manually invokes saved-record triggers.
 const auth=token?await adminRequire('firebase-admin/auth').getAuth().verifyIdToken(token):null;
 const db=adminRequire('firebase-admin/firestore').getFirestore();
 const before=name==='decideLeave'?await db.doc('leaveRequests/'+data.id).get():null;
 const result=await handlers[name].run({data,auth:auth?{uid:auth.uid,token:auth}:null});
 if(name==='applyLeave'||name==='decideLeave'){
  const id=name==='applyLeave'?result.id:data.id;const after=await db.doc('leaveRequests/'+id).get();
  await handlers.leaveAlerts.run({params:{requestId:id},data:{before:before||{exists:false},after}});
 }
 if(name==='publishDepartmentNotice'){
  const after=await db.doc('departmentNotices/'+data.id).get();
  await handlers.departmentNoticeAlerts.run({params:{noticeId:data.id},data:{before:{exists:false},after}});
 }
 return result;
}
async function password(email){return (await request(authBase+'/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo',{email,password:'ShiftNestDemo123!',returnSecureToken:true})).idToken;}
async function phone(number){
 const r=await request(authBase+'/identitytoolkit.googleapis.com/v1/accounts:sendVerificationCode?key=demo',{phoneNumber:number});
 const codes=await request(authBase+`/emulator/v1/projects/${project}/verificationCodes`);
 const code=codes.verificationCodes.find(c=>c.sessionInfo===r.sessionInfo);assert.ok(code,'Emulator OTP exists');
 return (await request(authBase+'/identitytoolkit.googleapis.com/v1/accounts:signInWithPhoneNumber?key=demo',{sessionInfo:r.sessionInfo,code:code.code})).idToken;
}
const fields=obj=>Object.fromEntries(Object.entries(obj).map(([k,v])=>[k,{stringValue:v}]));
async function inbox(uid,token){for(let i=0;i<30;i++){const r=await request(storeBase+'/notifications/'+uid+'/items',null,token);if(r.documents?.length)return r.documents;await new Promise(resolve=>setTimeout(resolve,500));}throw new Error('No saved inbox alert from emulator trigger');}
async function main(){
 assert.deepEqual(await call('lookupEmployee',{phone:'+94770000001'}),{registered:true});
 await assert.rejects(call('lookupEmployee',{phone:'+94770000000'}),/not registered/);
 const admin=await password('admin@shiftnest.test'),other=await password('approver@shiftnest.test');
 const sales=await phone('+94770000001'),office=await phone('+94770000002');
 assert.equal((await call('employeeProfile',{},sales)).employeeId,'demo-sales');
 const today=require('../functions/leave-policy').today();
 const future=new Date(Date.parse(today+'T12:00:00Z')+86400000).toISOString().slice(0,10);
 // Actual rules reject even the owner's direct attendance/GPS writes; server callable preserves the workflow.
 const now=new Date().toISOString();
 const own={fields:{...fields({employeeId:'demo-sales',date:today,checkIn:'08:00:00',checkOut:''}),createdAt:{timestampValue:now}}};
 await assert.rejects(request(storeBase+'/attendance?documentId=demo-sales_'+today,own,sales),/PERMISSION_DENIED/);
 const fix={expectedDate:today,siteName:'Urban Lux',locationName:'Kandana',latitude:6.9,longitude:79.8,accuracyMeters:5,fixTime:Date.now(),mock:false};
 const rid=()=>require('node:crypto').randomBytes(16).toString('hex');
 await call('recordAttendance',{...fix,action:'in',requestId:rid()},sales);
 await assert.rejects(request(storeBase+'/attendance/demo-sales_'+today+'?updateMask.fieldPaths=checkOut',{fields:fields({checkOut:'17:00:00'})},sales,'PATCH'),/PERMISSION_DENIED/);
 await assert.rejects(request(storeBase+'/latestLocations?documentId=demo-sales',own,sales),/PERMISSION_DENIED/);
 await assert.rejects(request(storeBase+'/locationLogs',own,sales),/PERMISSION_DENIED/);
 await call('recordAttendance',{...fix,action:'out',requestId:rid(),fixTime:Date.now()},sales);
 await assert.rejects(request(storeBase+'/attendance?documentId=someoneElse_'+today,own,sales),/PERMISSION_DENIED/);
 const before=await call('leaveOverview',{date:future},sales);assert.equal(before.remaining,1);
 const pending=await call('applyLeave',{date:future,reason:'LOCAL smoke test'},sales);
 assert.equal((await call('leaveOverview',{date:future},sales)).remaining,0);
 await inbox('demo-admin',admin);
 await call('decideLeave',{id:pending.id,status:'rejected',note:'Demo Chairman rejected'},admin);
 assert.equal((await call('leaveOverview',{date:future},sales)).remaining,1);
 await inbox('demo-sales',sales);
 // Admin personal flow and second-admin decision protection.
 await call('linkAdminEmployee',{phone:'+94770000002'},admin);
 assert.equal((await call('employeeProfile',{},admin)).employeeId,'demo-office');
 const own=await call('applyLeave',{date:future,reason:'Own admin demo leave'},admin);
 await assert.rejects(call('decideLeave',{id:own.id,status:'approved',note:'Self approval'},admin),/Another administrator/);
 await call('decideLeave',{id:own.id,status:'approved',note:'Demo Chairman approved'},other);
 assert.equal((await call('leaveOverview',{date:future},office)).remaining,19);
 await call('publishDepartmentNotice',{id:'smoke-notice-000000000001',department:'Office',date:future,kind:'working',reason:'Demo department notice',checkIn:'08:00',checkOut:'17:00'},admin);
 let notice=false;for(let i=0;i<30;i++){const d=await request(storeBase+'/employeeNotices/demo-office/items',null,office);if(d.documents?.length){notice=true;break;}await new Promise(resolve=>setTimeout(resolve,500));}assert.ok(notice,'Department inbox trigger saved notice');
 console.log((direct?'DIRECT HANDLERS + REAL AUTH/FIRESTORE (manual triggers): ':'FULL EMULATOR HTTP: ')+'PASS: actual local emulator registration, unknown-number refusal, phone OTP, profile, attendance rules, unauthorized write refusal, leave reservation/refund, inbox triggers, linked admin self-decision refusal, second-admin approval and department notice.');
}
main().then(()=>process.exit(0)).catch(e=>{console.error(e);process.exit(1);});
