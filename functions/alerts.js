'use strict';
const {onDocumentWritten}=require('firebase-functions/v2/firestore');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const {getMessaging}=require('firebase-admin/messaging');
const {getAuth}=require('firebase-admin/auth');
const {createHash}=require('node:crypto');
const policy=require('./alert-policy');
const db=getFirestore(),options={region:'asia-south1',maxInstances:5,retry:true};
const key=value=>createHash('sha256').update(String(value)).digest('hex');
async function push(uid,role,id,alert,employee){
 if(process.env.FUNCTIONS_EMULATOR==='true')return; // no FCM emulator; keep durable inbox only
 const q=await db.collection('deviceTokens').where('uid','==',uid).get();
 const docs=q.docs.filter(d=>d.get('role')===role&&(!employee||d.get('employeeId')===employee.id&&d.get('phone')===employee.get('phone')));
 for(let i=0;i<docs.length;i+=500){
  const batch=docs.slice(i,i+500);if(!batch.length)continue;
  const result=await getMessaging().sendEachForMulticast({tokens:batch.map(d=>d.get('token')),data:{recipientUid:uid,role,notificationId:id,type:alert.type,scope:alert.scope||'',title:alert.title,body:alert.body,color:alert.color||''},android:{priority:'high',ttl:3600000}});
  await Promise.all(result.responses.map((r,j)=>!r.success&&['messaging/registration-token-not-registered','messaging/invalid-registration-token'].includes(r.error?.code)?batch[j].ref.delete():Promise.resolve()));
 }
}
async function deliver(uid,role,id,alert,employee){
 const ref=db.doc(`notifications/${uid}/items/${id}`);
 const fresh=await db.runTransaction(async t=>{const existing=await t.get(ref);if(existing.exists)return false;t.set(ref,{...alert,read:false,createdAt:FieldValue.serverTimestamp()});return true;});
 if(!fresh)return;
 // The durable inbox is authoritative. A push failure must not roll back leave/location state.
 try{await push(uid,role,id,alert,employee);}catch(e){console.warn('Notification saved; push could not be delivered',e.code||e.message);}
}
async function administrators(id,alert){
 const admins=await db.collection('admins').where('active','==',true).get();
 await Promise.all(admins.docs.filter(d=>alert.type!=='leave_pending'||d.get('employeeId')!==alert.employeeId).map(d=>deliver(d.id,'admin',id,alert)));
}
exports.leaveAlerts=onDocumentWritten({...options,document:'leaveRequests/{requestId}'},async event=>{
 const before=event.data?.before.exists?event.data.before.data():null,after=event.data?.after.exists?event.data.after.data():null;
 const type=policy.leaveEvent(before,after);if(!type)return;
 const id=key(`${event.params.requestId}_${type}`);
 if(type==='leave_pending'){
  const current=await db.doc(`leaveRequests/${event.params.requestId}`).get();if(!current.exists||current.get('status')!=='pending')return;
  await administrators(id,{type,requestId:event.params.requestId,employeeId:after.employeeId,title:'New leave request',body:`${after.employeeName||'Employee'} (${after.department||'Department'}) requested leave on ${after.date}. Waiting for Chairman Sir's approval.`});
 }else{
  const employee=await db.doc(`employees/${after.employeeId}`).get();if(!employee.exists||employee.get('active')!==true)return;
  const tokens=await db.collection('deviceTokens').where('employeeId','==',after.employeeId).get();
  const recipients=tokens.docs.filter(d=>d.get('role')==='employee'&&d.get('phone')===employee.get('phone')).map(d=>d.get('uid'));
  if(after.employeeAuthUid&&after.employeePhoneAtApply===employee.get('phone'))recipients.push(after.employeeAuthUid);
  const uids=[...new Set(recipients)];
  const alert={type,requestId:event.params.requestId,employeeId:after.employeeId,title:type==='leave_approved'?'Leave approved':'Leave rejected',body:`Your leave on ${after.date} has been ${after.status} by Chairman Sir.${after.status==='rejected'?' The reserved leave has been released.':''}`};
  await Promise.all(uids.map(async uid=>{const adm=await db.doc(`admins/${uid}`).get();const own=adm.get('active')===true&&adm.get('employeeId')===employee.id&&employee.get('adminUid')===uid;await deliver(uid,own?'admin':'employee',id,own?{...alert,scope:'self'}:alert,own?null:employee);}));
 }
});
exports.locationAlerts=onDocumentWritten({...options,document:'latestLocations/{employeeId}'},async event=>{
 if(!event.data?.after.exists)return;
 const alertId=key(`${event.params.employeeId}_${event.id}`),outboxRef=db.doc(`alertOutbox/${alertId}`);
 const existing=await outboxRef.get();if(existing.exists){await administrators(alertId,existing.get('alert'));return;}
 const reading=event.data.after.data(),employee=await db.doc(`employees/${event.params.employeeId}`).get();
 if(!employee.exists)return;
 const site=employee.get('locationId')?await db.doc(`locations/${employee.get('locationId')}`).get():null;
 const result=policy.outside(employee.data(),site?.data(),reading);
 if(!result)return;
 const stateRef=db.doc(`locationAlertState/${employee.id}`);
 const alert={type:'location_outside',employeeId:employee.id,eventAt:new Date(result.sample),title:'Employee outside assigned location',body:`${employee.get('name')||'Employee'} was reported outside ${site.get('name')||'the assigned work site'} (about ${result.distance} m from site). Department: ${employee.get('role')||'not set'}.`};
 const transitioned=await db.runTransaction(async t=>{
  const previous=await t.get(stateRef),outbox=await t.get(outboxRef);
  if(outbox.exists)return true;if(previous.exists&&Number(previous.get('sample'))>=result.sample)return false;
  const shouldAlert=result.outside&&(!previous.exists||previous.get('outside')!==true||previous.get('locationId')!==employee.get('locationId'));
  t.set(stateRef,{outside:result.outside,locationId:employee.get('locationId'),sample:result.sample});
  if(shouldAlert)t.set(outboxRef,{employeeId:employee.id,alert,createdAt:FieldValue.serverTimestamp()});
  return shouldAlert;
 });
 if(transitioned)await administrators(alertId,alert);
});

exports.mockLocationAlerts=onDocumentWritten({...options,document:'mockLocationReports/{reportId}'},async event=>{
 if(!event.data?.after.exists)return;
 const report=event.data.after.data();
 if(!report.employeeId||!['check_in','tracking'].includes(report.source))return;
 await administrators(key(`mock_${event.params.reportId}`),{
  type:'location_mock',employeeId:report.employeeId,title:'Fake location detected',
  body:`${report.employeeName||'Employee'} (${report.department||'Department'}) reported an Android-marked mock GPS reading during ${report.source==='check_in'?'check-in':'location tracking'}. ${report.source==='check_in'?'Check-in was blocked. Company approval is required to unblock future check-ins.':'The fake GPS reading was discarded; any existing check-in remains recorded. Company approval is required to unblock future check-ins.'}`
 });
});

exports.departmentNoticeAlerts=onDocumentWritten({...options,document:'departmentNotices/{noticeId}'},async event=>{
 if(!event.data?.after.exists)return;
 const notice=event.data.after.data();
 const employees=await db.collection('employees').where('active','==',true).get();
 const alert={type:'admin_schedule',title:'Department calendar update',body:require('./schedule-policy').body(notice),department:notice.department,date:notice.date,adminUid:notice.adminUid};
 for(const e of employees.docs){
  if(require('./leave-policy').department(e.get('role'))!==notice.department)continue;
  const ref=db.doc(`employeeNotices/${e.id}/items/${event.params.noticeId}`);
  const fresh=await db.runTransaction(async t=>{const old=await t.get(ref),current=await t.get(e.ref);if(old.exists||current.get('active')!==true||require('./leave-policy').department(current.get('role'))!==notice.department)return false;t.set(ref,{...alert,employeeId:e.id,read:false,createdAt:FieldValue.serverTimestamp()});return true;});
  if(!fresh)continue;
  // The employee-ID inbox survives absent push tokens and first phone login.
  try{const adminUid=e.get('adminUid');if(adminUid){const adm=await db.doc(`admins/${adminUid}`).get();if(adm.get('active')===true&&adm.get('employeeId')===e.id)await push(adminUid,'admin',event.params.noticeId,alert,null);}const user=await getAuth().getUserByPhoneNumber(e.get('phone'));if(user.uid!==adminUid)await push(user.uid,'employee',event.params.noticeId,alert,e);}
  catch(error){if(error.code!=='auth/user-not-found')console.warn('Department inbox saved; push unavailable',error.code||'unknown');}
 }
});

exports.workEventAlerts=onDocumentWritten({...options,document:'workEvents/{eventId}'},async event=>{
 if(!event.data?.after.exists)return;const v=event.data.after.data();if(!['late','outing','return'].includes(v.type))return;
 const heading=v.type==='late'?'Late check-in message':v.type==='outing'?'Location added':'Employee returned';
 const detail=v.type==='late'?v.reason:v.type==='outing'?`Site: ${v.siteName||"--"}. Location: ${v.locationName||v.destination}. Reason: ${v.reason}. Recorded GPS: ${v.latitude}, ${v.longitude}. This is a notice, not travel approval.`:`Return note: ${v.reason}`;
 await administrators(key(`work_${event.params.eventId}`),{type:'work_'+v.type,employeeId:v.employeeId,date:v.date,color:v.color||'',title:heading,body:`${v.employeeName||'Employee'} (${v.employeeRole||'Employee'}) • ${v.date}\n${detail}`});
});
exports.systemStorageAlerts=onDocumentWritten({...options,document:'systemAlerts/{alertId}'},async event=>{
 if(!event.data?.after.exists)return;const alert=event.data.after.data();if(alert.type!=='storage_budget')return;
 await administrators(key('system_'+event.params.alertId),alert);
});
