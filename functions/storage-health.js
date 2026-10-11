'use strict';
const {onCall}=require('firebase-functions/v2/https');
const {onSchedule}=require('firebase-functions/v2/scheduler');
const p=require('./work-policy'),leave=require('./leave-policy');
// Budget monitoring uses an explicitly labelled document-payload estimate, never a fabricated cloud quota.
const scopes=['attendance','employees','locations','locationLogs','latestLocations','leaveRequests','leaveAccounts','leaveEmployeeDates','leaveDateLocks','holidays','departmentNotices','attendanceNotices','attendanceOutings','workEvents','admins','employeePhones','deviceTokens','attendanceReceipts'];
module.exports=({db,admin,fail,options,FieldValue})=>{
 const status=db.doc('systemHealth/storage'),config=db.doc('settings/storageBudget');
 async function audit(){
  const cfg=await config.get();if(!cfg.exists){await status.set({state:'unconfigured',complete:false,checkedAt:FieldValue.serverTimestamp()});return {state:'unconfigured',complete:false};}
  let budget;try{budget=p.storageBudget(cfg.data());}catch(e){fail(e.message);}
  const lockRef=db.doc('systemHealth/storageScan');const owner=require('node:crypto').randomUUID(),now=Date.now();
  const locked=await db.runTransaction(async t=>{const old=await t.get(lockRef);if(Number(old.get('until'))>now)return false;t.set(lockRef,{owner,until:now+540000});return true;});
  if(!locked)fail('A storage check is already running. Refresh in a few minutes.','aborted');
  let bytes=0,count=0,complete=true;
  try{
   const queries=[...scopes.map(name=>db.collection(name)),db.collectionGroup('items'),db.collectionGroup('days')];
   for(const base of queries){
    let cursor=null;
    while(true){let query=base.orderBy('__name__').limit(500);if(cursor)query=query.startAfter(cursor);const page=await query.get();
     for(const doc of page.docs){if(count>=100000){complete=false;break;}bytes+=p.documentBytes(doc.ref.path,doc.data());count++;}
     if(!complete||page.docs.length<500)break;cursor=page.docs[page.docs.length-1];
    }
    if(!complete)break;
   }
   const warning=p.capacity(bytes,budget,complete),value={state:!complete?'incomplete':warning?'warning':'ok',complete,estimatedBytes:bytes,documents:count,budgetMiB:budget.budgetMiB,warnPercent:budget.warnPercent,measurement:'document JSON payload estimate; excludes indexes, backups, unknown collections and cloud overhead',checkedAt:FieldValue.serverTimestamp()};
   await status.set(value);
   if(warning||!complete){const id='storage-'+leave.today();await db.doc('systemAlerts/'+id).set({type:'storage_budget',title:warning?'Data budget warning':'Storage check incomplete',body:warning?`Estimated application document data: ${(bytes/1048576).toFixed(1)} MiB, reaching your ${budget.warnPercent}% warning level for a ${budget.budgetMiB} MiB budget. This is not a cloud free-space reading. Keep records: verify Firebase Usage/Billing, export a private backup, then review budget/billing with the project owner. Do not delete attendance to clear this warning.`:'The scan reached 100000 documents. Space remaining is unknown. Review Firebase Usage/Billing and configure Cloud Monitoring alerts with the project owner. Keep private backups; do not delete attendance.',date:leave.today(),createdAt:FieldValue.serverTimestamp()});}
   return {...value,checkedAt:new Date().toISOString()};
  }catch(error){await status.set({state:'error',complete:false,error:'Storage check failed. Check backend permissions and retry.',checkedAt:FieldValue.serverTimestamp()});throw error;}
  finally{await db.runTransaction(async t=>{const old=await t.get(lockRef);if(old.get('owner')===owner)t.set(lockRef,{owner,until:0});});}
 }
 return {
  saveStorageBudget:onCall(options,async request=>{await admin(request);let value;try{value=p.storageBudget(request.data);}catch(e){fail(e.message,'invalid-argument');}await db.runTransaction(async t=>{const a=await t.get(db.doc('admins/'+request.auth.uid));if(a.get('active')!==true)fail('Admin access required.','permission-denied');t.set(config,{...value,updatedBy:request.auth.uid,updatedAt:FieldValue.serverTimestamp()});});return {saved:true};}),
  getStorageHealth:onCall(options,async request=>{await admin(request);const [c,s]=await Promise.all([config.get(),status.get()]);return {budget:c.exists?c.data():null,status:s.exists?{...s.data(),checkedAt:s.get('checkedAt')?.toDate().toISOString()||''}:null};}),
  checkStorageHealth:onCall({...options,timeoutSeconds:540,memory:'512MiB'},async request=>{await admin(request);const recent=await status.get(),cfg=await config.get();if(['ok','warning'].includes(recent.get('state'))&&recent.get('budgetMiB')===cfg.get('budgetMiB')&&recent.get('warnPercent')===cfg.get('warnPercent')&&recent.get('checkedAt')?.toDate().getTime()>Date.now()-300000)return {cached:true,...recent.data(),checkedAt:recent.get('checkedAt').toDate().toISOString()};return audit();}),
  dailyStorageHealth:onSchedule({region:'asia-south1',schedule:'every day 06:00',timeZone:'Asia/Colombo',timeoutSeconds:540,memory:'512MiB',retryCount:1},audit)
 };
};
