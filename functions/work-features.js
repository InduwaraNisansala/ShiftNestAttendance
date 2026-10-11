'use strict';
const {onCall}=require('firebase-functions/v2/https');
const p=require('./work-policy'),leave=require('./leave-policy'),{createHash}=require('node:crypto');
module.exports=({db,employee,admin,actorOwns,fail,options,FieldValue})=>{
 const secure=require('./attendance-policy');
 const submitWorkNotice=onCall({...options,enforceAppCheck:!secure.localDemo(),consumeAppCheckToken:!secure.localDemo()},async request=>{
  await secure.approved(request,db,fail);
  const e=await employee(request);let value;try{value=p.eventInput(request.data);}catch(error){fail(error.message,'invalid-argument');}
  const date=leave.today(),key=e.id+'_'+date;
  const ref=db.doc('workEvents/'+value.id),notes=db.doc('attendanceNotices/'+key),attendance=db.doc('attendance/'+key),outing=db.doc('attendanceOutings/'+key);
  const hash=createHash('sha256').update(JSON.stringify(value)).digest('hex');
  await db.runTransaction(async t=>{
   if(date!==leave.today())fail('Date changed. Reopen the attendance message form.');
   const current=await t.get(e.ref),actor=await t.get(db.doc('admins/'+request.auth.uid)),old=await t.get(ref),a=await t.get(attendance),previous=await t.get(notes),trip=await t.get(outing);
   if(!actorOwns(request,current,actor))fail('Your employee account is unavailable.','permission-denied');
   if(old.exists){if(old.get('date')!==date)fail('This request belongs to a previous day. Reopen the message form.');if(old.get('employeeId')!==e.id||old.get('hash')!==hash)fail('Request ID was already used for another message.','already-exists');return;}
   if(value.type==='outing'){try{secure.gps(request.data);}catch(error){fail(error.message,'invalid-argument');}if(current.get('attendanceBlocked')===true)fail('Attendance is blocked after a fake GPS report. Ask admin for approval.');}
   if(a.get('checkOut'))fail('Today\'s attendance is already completed.');
   if(value.type!=='late'&&!a.get('checkIn'))fail('Check in before adding an outside destination or returning.');
   if(value.type==='return'&&trip.get('active')!==true)fail('There is no active outing to mark returned.');
   let color='';try{if(value.type!=='late')color=p.outingColor(current.data());}catch(error){fail(error.message);}
   const event={employeeId:e.id,employeeName:current.get('name')||'',employeeRole:current.get('role')||'',employeeNumber:current.get('employeeNumber')||e.id,date,type:value.type,reason:value.reason,siteName:value.siteName,locationName:value.destination,destination:value.destination,latitude:value.latitude,longitude:value.longitude,accuracyMeters:value.accuracyMeters||null,fixTime:value.fixTime||null,color,hash,actorUid:request.auth.uid,createdAt:FieldValue.serverTimestamp()};
   // Immutable event audit plus the latest late explanation for this employee/day.
   t.set(ref,event);
   if(value.type==='late')t.set(notes,{employeeId:e.id,employeeName:event.employeeName,employeeRole:event.employeeRole,date,lateMessage:value.reason,requestId:value.id,revision:(previous.get('revision')||0)+1,updatedAt:FieldValue.serverTimestamp()});
   if(value.type==='outing')t.set(outing,{...event,active:true,outingId:value.id});
   if(value.type==='return')t.set(outing,{...trip.data(),active:false,returnReason:value.reason,returnId:value.id,returnedAt:FieldValue.serverTimestamp()});
  });
  return {saved:true,id:value.id,message:'Saved. The administrator alert is queued.'};
 });
 return {submitWorkNotice,...require('./storage-health')({db,admin,fail,options,FieldValue})};
};
