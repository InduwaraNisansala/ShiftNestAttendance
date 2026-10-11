'use strict';
const {onCall}=require('firebase-functions/v2/https');
const p=require('./attendance-policy'),leave=require('./leave-policy'),{createHash}=require('node:crypto');
module.exports=({db,employee,actorOwns,fail,options,FieldValue})=>({
 recordAttendance:onCall({...options,enforceAppCheck:!p.localDemo(),consumeAppCheckToken:!p.localDemo()},async request=>{
  await p.approved(request,db,fail);const e=await employee(request);let v;try{v=p.input(request.data);}catch(error){fail(error.message,'invalid-argument');}
  const date=leave.today();if(v.expectedDate!==date)fail('Date changed. Refresh attendance and retry.');
  const ref=db.doc('attendance/'+e.id+'_'+date),receipt=db.doc('attendanceReceipts/'+v.requestId),latest=db.doc('latestLocations/'+e.id),log=db.doc('locationLogs/'+v.requestId);
  const hash=createHash('sha256').update(JSON.stringify(Object.keys(v).sort().map(k=>[k,v[k]]))).digest('hex');
  return db.runTransaction(async t=>{
   const current=await t.get(e.ref),adm=await t.get(db.doc('admins/'+request.auth.uid)),a=await t.get(ref),old=await t.get(receipt);
   if(!actorOwns(request,current,adm)||current.get('archived')===true)fail('Your employee account is unavailable.','permission-denied');
   if(old.exists){if(old.get('employeeId')!==e.id||old.get('hash')!==hash)fail('Request ID belongs to another attendance request.','already-exists');return old.get('result');}
   if(current.get('attendanceBlocked')===true)fail('Attendance is blocked after a fake GPS report. Ask admin for company approval.');
   const now=new Date();if(date!==leave.today())fail('Date changed. Refresh attendance and retry.');
   let fix,assignment;try{fix=p.gps(v,now.getTime());}catch(error){fail(error.message,'invalid-argument');}
   const site=current.get('workLocationType')==='fixed'&&current.get('locationId')?await t.get(db.doc('locations/'+current.get('locationId'))):null;
   try{assignment=p.site(current.data(),site?.data(),fix,v.action==='in');}catch(error){fail(error.message);}
   const personal=await t.get(db.doc('leaveEmployeeDates/'+e.id+'_'+date)),sales=await t.get(db.doc('leaveDateLocks/'+date));
   if(v.action==='in'){
    if(a.exists)fail('You already checked in today. Refresh attendance.','already-exists');
    if(personal.get('status')==='approved'||sales.get('status')==='approved'&&sales.get('employeeId')===e.id)fail('You have approved leave today.');
   }else if(!a.get('checkIn')||a.get('checkOut'))fail('An open check-in is required. Refresh attendance.');
   const clock=new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Colombo',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).format(now);
   const result={saved:true,date,checkIn:v.action==='in'?clock:a.get('checkIn'),checkOut:v.action==='out'?clock:a.get('checkOut')||'',locationId:assignment.locationId};
   if(v.action==='in')t.set(ref,{employeeId:e.id,employeeName:current.get('name')||'',employeeRole:current.get('role')||'',phone:current.get('phone')||'',date,checkIn:clock,checkOut:'',status:'Present',workStatus:'Working',checkInSiteName:v.siteName,checkInLocationName:v.locationName,checkInLatitude:fix.latitude,checkInLongitude:fix.longitude,checkInDistance:assignment.distance,checkInAccuracyMeters:fix.accuracyMeters,locationId:assignment.locationId,locationName:assignment.locationName,createdAt:FieldValue.serverTimestamp(),actorUid:request.auth.uid,verification:p.localDemo()?'local_demo':'app_check',appId:request.app?.appId||''});
   if(v.action==='out')t.update(ref,{checkOut:clock,workStatus:'Completed',checkOutSiteName:v.siteName,checkOutLocationName:v.locationName,checkOutLatitude:fix.latitude,checkOutLongitude:fix.longitude,checkedOutAt:FieldValue.serverTimestamp()});
   const reading={employeeId:e.id,locationId:assignment.locationId,locationName:assignment.locationName,latitude:fix.latitude,longitude:fix.longitude,accuracyMeters:fix.accuracyMeters,fixTimestamp:new Date(fix.fixTime),timestamp:FieldValue.serverTimestamp(),trackingStatus:v.action==='out'?'Completed':'Working',insideSite:assignment.insideSite,siteCheckStatus:assignment.siteCheckStatus,verification:p.localDemo()?'local_demo':'app_check'};
   t.set(log,reading);t.set(latest,reading);t.set(receipt,{employeeId:e.id,hash,result,actorUid:request.auth.uid,createdAt:FieldValue.serverTimestamp()});return result;
  });
 })
});
