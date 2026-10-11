'use strict';
const {onCall,HttpsError} = require('firebase-functions/v2/https');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore,FieldValue} = require('firebase-admin/firestore');
const policy = require('./leave-policy');
initializeApp();
const db = getFirestore();
const options = {region:'asia-south1',maxInstances:5};
const fail = (message,code='failed-precondition') => {throw new HttpsError(code,message);};
async function employee(request) {
  if(!request.auth)fail('Sign in to continue.','unauthenticated');
  const adm=await db.doc(`admins/${request.auth.uid}`).get();
  if(adm.get('active')===true){
    const id=adm.get('employeeId');if(!id)fail('Link your admin account to your employee record first.');
    const e=await db.doc(`employees/${id}`).get();
    if(!e.exists||e.get('active')!==true||e.get('archived')===true||e.get('adminUid')!==request.auth.uid)fail('Your linked employee record is unavailable.','permission-denied');
    return e;
  }
  const phone=request.auth.token?.phone_number;
  if (!phone) fail('Sign in with your employee phone number.','unauthenticated');
  const query = await db.collection('employees').where('phone','==',phone).where('active','==',true).get();
  if (query.size !== 1) fail('An active, unique employee record is required.','permission-denied');
  return query.docs[0];
}
function actorOwns(request,e,adm){
 return e.exists&&e.get('active')===true&&(adm.get('active')===true
  ?adm.get('employeeId')===e.id&&e.get('adminUid')===request.auth.uid
  :!!request.auth.token?.phone_number&&e.get('phone')===request.auth.token.phone_number);
}

async function admin(request) {
  if (!request.auth) fail('Admin login required.','unauthenticated');
  const doc = await db.doc(`admins/${request.auth.uid}`).get();
  if (!doc.exists || doc.get('active') !== true) fail('Admin access required.','permission-denied');
  return doc;
}
exports.lookupEmployee = onCall(options,async request => {
  const phone = String(request.data?.phone || '');
  if (!/^\+94\d{9}$/.test(phone)) fail('Enter a valid Sri Lankan phone number.','invalid-argument');
  const q = await db.collection('employees').where('phone','==',phone).where('active','==',true).get();
  if (q.size !== 1) fail('This phone number is not registered as an active employee.','not-found');
  // Registration check before OTP, without exposing names, roles or the employee directory.
  return {registered:true};
});
exports.resolveAdminUsername = onCall(options,async request => {
  const username = String(request.data?.username || '').trim().toLowerCase();
  if (!/^[a-z0-9_.-]{1,60}$/.test(username)) fail('Invalid username.','invalid-argument');
  const doc = await db.doc(`adminUsernames/${username}`).get();
  if (!doc.exists || doc.get('active') !== true) fail('Invalid admin login.','permission-denied');
  return {email:doc.get('email')};
});
exports.employeeProfile = onCall(options,async request => {
  const e = await employee(request);
  return {employeeId:e.id,employeeName:e.get('name') || '',employeeRole:e.get('role') || '',phone:e.get('phone')};
});
async function dataFor(e) {
  const [attendance,leaves,config,holidays,locks] = await Promise.all([
    db.collection('attendance').where('employeeId','==',e.id).get(),
    db.collection('leaveRequests').where('employeeId','==',e.id).get(),
    db.doc('settings/leavePolicy').get(),db.collection('holidays').get(),db.collection('leaveDateLocks').get()
  ]);
  return {attendance,leaves,config,holidays,locks};
}
exports.leaveOverview = onCall(options,async request => {
  const e = await employee(request), d = await dataFor(e);
  const sales = policy.department(e.get('role')) === 'Sales';
  const selected=policy.validDate(request.data?.date)?request.data.date:policy.today();
  const departmentDays=await db.collection(`departmentCalendars/${policy.department(e.get('role'))}/days`).get();
  const extraHolidays=departmentDays.docs.filter(x=>x.get('kind')==='holiday').map(x=>({date:x.id,name:x.get('reason'),blocked:true}));
  const b = policy.employeeBalance(e.data(),d.attendance.docs.map(x=>x.data()),d.leaves.docs.map(x=>x.data()),selected);
  return {sales,...b,quota:sales?b.earned:b.quota,currentDate:policy.today(),
    policy:d.config.data() || {},holidays:[...d.holidays.docs.map(x=>({date:x.id,...x.data()})),...extraHolidays],
    bookedDates:sales?d.locks.docs.map(x=>x.id):d.leaves.docs.filter(x=>['pending','approved'].includes(x.get('status'))).map(x=>x.get('date')),
    requests:d.leaves.docs.map(x=>({id:x.id,...x.data(),createdAt:x.get('createdAt')?.toDate().toISOString() || ''}))};
});
exports.applyLeave = onCall(options,async request => {
  const e = await employee(request);
  const date = String(request.data?.date || ''), reason = String(request.data?.reason || '').trim();
  if (!policy.validDate(date) || !reason || reason.length > 500) fail('Select a valid date and enter a reason (up to 500 characters).','invalid-argument');
  const reqRef = db.collection('leaveRequests').doc(), lockRef = db.doc(`leaveDateLocks/${date}`), accountRef = db.doc(`leaveAccounts/${e.id}`);
  await db.runTransaction(async t => {
    // All reads precede writes. Account serializes overlapping requests for the same employee.
    const account = await t.get(accountRef);
    const employeeDateRef=db.doc(`leaveEmployeeDates/${e.id}_${date}`);
    const employeeDate=await t.get(employeeDateRef);
    const currentEmployee = await t.get(e.ref);
    const actor=await t.get(db.doc(`admins/${request.auth.uid}`));
    const lock = await t.get(lockRef);
    const config = await t.get(db.doc('settings/leavePolicy'));
    const holiday = await t.get(db.doc(`holidays/${date}`));
    const attendance = await t.get(db.collection('attendance').where('employeeId','==',e.id));
    const leaves = await t.get(db.collection('leaveRequests').where('employeeId','==',e.id));
    if (!actorOwns(request,currentEmployee,actor)) fail('Employee is no longer eligible.','permission-denied');
    const sales=policy.department(currentEmployee.get('role'))==='Sales';
    const day=await t.get(db.doc(`departmentCalendars/${policy.department(currentEmployee.get('role'))}/days/${date}`));
    const blocked = policy.employeeBlockedReason(currentEmployee.get('role'),date,config.data(),holiday.data(),day.data());
    if (blocked) fail(blocked);
    if ((sales && lock.exists) || employeeDate.exists || leaves.docs.some(x=>x.get('date')===date && ['pending','approved'].includes(x.get('status')))) fail('This day is already booked.','already-exists');
    if (attendance.docs.some(x=>x.get('date')===date && x.get('checkIn'))) fail('Attendance is already recorded for this day.');
    const b = policy.employeeBalance(currentEmployee.data(),attendance.docs.map(x=>x.data()),leaves.docs.map(x=>x.data()),date);
    if(!b.configured)fail("Office/Hotel leave quota is awaiting company confirmation for this year.");
    if (b.remaining < 1) fail(sales?`Complete ${b.nextWorkDays} more work day(s) to earn your next leave.`:"No annual leave balance available.");
    t.set(reqRef,{employeeId:e.id,employeeAuthUid:request.auth.uid,employeePhoneAtApply:e.get('phone'),employeeName:e.get('name') || '',employeeNumber:e.get('employeeNumber') || e.id,department:policy.department(currentEmployee.get('role')),role:currentEmployee.get('role'),date,reason,status:'pending',workedDaysAtApply:b.worked,earnedAtApply:b.earned,usedAtApply:b.used,balanceAtApply:b.remaining,createdAt:FieldValue.serverTimestamp()});
    if(sales)t.set(lockRef,{requestId:reqRef.id,employeeId:e.id,status:'pending'});
    t.set(employeeDateRef,{requestId:reqRef.id,employeeId:e.id,status:'pending'});
    t.set(accountRef,{revision:(account.get('revision') || 0)+1});
  });
  return {id:reqRef.id,message:"Waiting for Chairman Sir's approval."};
});
exports.decideLeave = onCall(options,async request => {
  await admin(request);
  const id = String(request.data?.id || ''), status = request.data?.status, note = String(request.data?.note || '').trim();
  if (!/^[a-zA-Z0-9]{20}$/.test(id) || !['approved','rejected'].includes(status) || !note || note.length>500) fail('Choose a decision and enter the Chairman decision note.','invalid-argument');
  const ref = db.doc(`leaveRequests/${id}`);
  await db.runTransaction(async t => {
    const a = await t.get(db.doc(`admins/${request.auth.uid}`));
    const req = await t.get(ref);
    if (a.get('active') !== true) fail('Admin access required.','permission-denied');
    if (!req.exists || req.get('status') !== 'pending') fail('This request has already been decided.');
    const date = req.get('date'), employeeId = req.get('employeeId');
    if(a.get('employeeId')===employeeId)fail('Another administrator must record the Chairman decision for your own leave.','permission-denied');
    const lockRef = db.doc(`leaveDateLocks/${date}`), accountRef=db.doc(`leaveAccounts/${employeeId}`);
    const lock=await t.get(lockRef), account=await t.get(accountRef);
    const config=await t.get(db.doc('settings/leavePolicy')), holiday=await t.get(db.doc(`holidays/${date}`));
    const attendance=await t.get(db.collection('attendance').where('employeeId','==',employeeId));
    const leaves=await t.get(db.collection('leaveRequests').where('employeeId','==',employeeId));
    const emp=await t.get(db.doc(`employees/${employeeId}`));
    const sales=req.get("department")==="Sales";
    const employeeDateRef=db.doc(`leaveEmployeeDates/${employeeId}_${date}`);
    const employeeDate=await t.get(employeeDateRef);
    if (status==='approved') {
      const day=await t.get(db.doc(`departmentCalendars/${policy.department(emp.get('role'))}/days/${date}`));
      const blocked=policy.employeeBlockedReason(emp.get('role'),date,config.data(),holiday.data(),day.data());
      if (blocked) fail(blocked);
      if (emp.get('active')!==true) fail('Employee is inactive.');
      if(policy.department(emp.get('role'))!==req.get('department'))fail('Employee department changed. Reject this request and submit a new one.');
      if (attendance.docs.some(x=>x.get('date')===date && x.get('checkIn'))) fail('Attendance is already recorded for this day.');
      const b=policy.employeeBalance(emp.data(),attendance.docs.map(x=>x.data()),leaves.docs.map(x=>x.data()),date);
      if(!b.configured)fail("Annual leave quota is not configured for this year.");
      if (b.earned < b.used + b.pending) fail('The employee no longer has sufficient earned leave.');
      if ((sales && (!lock.exists || lock.get('requestId')!==id)) || (!sales && (!employeeDate.exists || employeeDate.get('requestId')!==id))) fail('The date reservation does not match this request.');
    }
    t.update(ref,{status,decisionNote:note,decidedBy:request.auth.uid,decidedAt:FieldValue.serverTimestamp()});
    if(sales){if (status==='approved') t.update(lockRef,{status});
    else if (lock.get('requestId')===id) t.delete(lockRef);}
    if(status==='approved')t.set(employeeDateRef,{requestId:id,employeeId,status});
    else if(employeeDate.get('requestId')===id)t.delete(employeeDateRef);
    t.set(accountRef,{revision:(account.get('revision') || 0)+1});
  });
  return {message:status==='approved'?'Your leave has been approved by Chairman Sir.':'Your leave has been rejected by Chairman Sir.'};
});

exports.createEmployee = onCall(options,async request => {
  await admin(request);
  const data=request.data || {},phone=policy.normalizePhone(data.phone),name=String(data.name || '').trim(),role=String(data.role || '');
  if (!/^\+94\d{9}$/.test(phone) || !name || name.length>120 || !['Sales Officer','Staff Officer','Hotel Officer'].includes(role)) fail('Enter valid employee details.','invalid-argument');
  if (data.workLocationType==='fixed' && !/^[A-Za-z0-9_-]{1,128}$/.test(String(data.locationId || ''))) fail('Choose a valid assigned site.','invalid-argument');
  const quota={};
  if(policy.department(role)!=='Sales' && data.annualLeaveAllowance != null){
    const total=data.annualLeaveAllowance,used=data.annualLeaveUsedBeforeApp ?? 0,year=data.annualLeaveYear;
    if(!Number.isInteger(total)||total<0||total>366||!Number.isInteger(used)||used<0||used>total||!Number.isInteger(year)||year<2000||year>2100)fail('Enter a valid annual allowance, used days and year.','invalid-argument');
    Object.assign(quota,{annualLeaveAllowance:total,annualLeaveUsedBeforeApp:used,annualLeaveYear:year});
  }
  const employeeRef=db.collection('employees').doc();
  await db.runTransaction(async t=>{
    const adminDoc=await t.get(db.doc(`admins/${request.auth.uid}`));
    const phoneRef=db.doc(`employeePhones/${phone}`),phoneDoc=await t.get(phoneRef);
    const existing=await t.get(db.collection('employees'));
    const site=data.workLocationType==='fixed'?await t.get(db.doc(`locations/${String(data.locationId || '')}`)):null;
    if(adminDoc.get('active')!==true)fail('Admin access required.','permission-denied');
    if(phoneDoc.exists || existing.docs.some(x=>policy.normalizePhone(x.get('phone'))===phone))fail('This phone number is already registered.','already-exists');
    if(data.workLocationType!=='fixed' && data.workLocationType!=='no_fixed')fail('Choose a work location type.');
    if(site && !site.exists)fail('Assigned site does not exist.');
    t.set(employeeRef,{name,phone,role,active:true,...quota,employeeNumber:String(data.employeeNumber || '').trim().slice(0,60),workLocationType:data.workLocationType,locationId:site?site.id:'',locationName:site?site.get('name'):'No Fixed Location',createdAt:FieldValue.serverTimestamp()});
    t.set(phoneRef,{employeeId:employeeRef.id});
  });
  return {employeeId:employeeRef.id};
});

// Update the same employee ID so attendance and leave history stay attached.
exports.updateEmployee = onCall(options,async request => {
  await admin(request);
  const data=request.data || {},id=String(data.employeeId || '');
  if(!/^[A-Za-z0-9_-]{1,128}$/.test(id))fail('Invalid employee ID.','invalid-argument');
  const name=String(data.name || '').trim(),phone=policy.normalizePhone(data.phone),role=String(data.role || '');
  if(!name || name.length>120 || !phone || !['Sales Officer','Staff Officer','Hotel Officer'].includes(role) || typeof data.active!=='boolean')fail('Enter valid employee details.','invalid-argument');
  if(!['fixed','no_fixed'].includes(data.workLocationType))fail('Choose a work location type.','invalid-argument');
  if(data.workLocationType==='fixed' && !/^[A-Za-z0-9_-]{1,128}$/.test(String(data.locationId || '')))fail('Choose an assigned site.','invalid-argument');
  const quota={};
  if(policy.department(role)!=='Sales'){
    const total=data.annualLeaveAllowance,used=data.annualLeaveUsedBeforeApp ?? 0,year=data.annualLeaveYear;
    if(total==null){Object.assign(quota,{annualLeaveAllowance:null,annualLeaveUsedBeforeApp:null,annualLeaveYear:null});}
    else{
      if(!Number.isInteger(total)||total<0||total>366||!Number.isInteger(used)||used<0||used>total||!Number.isInteger(year)||year<2000||year>2100)fail('Enter a valid annual allowance, opening used days and year.','invalid-argument');
      Object.assign(quota,{annualLeaveAllowance:total,annualLeaveUsedBeforeApp:used,annualLeaveYear:year});
    }
  }
  const ref=db.doc(`employees/${id}`);
  await db.runTransaction(async t=>{
    const a=await t.get(db.doc(`admins/${request.auth.uid}`)),employee=await t.get(ref);
    if(a.get('active')!==true)fail('Admin access required.','permission-denied');
    if(!employee.exists)fail('Employee not found.','not-found');
    const oldPhone=employee.get('phone'),newPhoneRef=db.doc(`employeePhones/${phone}`);
    const phoneDoc=await t.get(newPhoneRef);
    const matches=await t.get(db.collection('employees'));
    const leaves=await t.get(db.collection('leaveRequests').where('employeeId','==',id));
    const oldRef=typeof oldPhone==='string' && /^\+94\d{9}$/.test(oldPhone)?db.doc(`employeePhones/${oldPhone}`):null;
    const oldGuard=oldRef?await t.get(oldRef):null;
    const site=data.workLocationType==='fixed'?await t.get(db.doc(`locations/${data.locationId}`)):null;
    if((phoneDoc.exists && phoneDoc.get('employeeId')!==id)||matches.docs.some(x=>x.id!==id&&policy.normalizePhone(x.get('phone'))===phone))fail('This phone number is already registered to another employee.','already-exists');
    if(site && !site.exists)fail('Assigned site does not exist.','not-found');
    if(policy.department(employee.get('role'))!==policy.department(role) && leaves.docs.some(x=>x.get('status')==='pending'))fail('Reject or decide pending leave requests before changing department.');
    t.update(ref,{name,phone,role,active:data.active,archived:data.active?false:employee.get('archived')===true,employeeNumber:String(data.employeeNumber || '').trim().slice(0,60),workLocationType:data.workLocationType,locationId:site?site.id:'',locationName:site?site.get('name'):'No Fixed Location',...quota,updatedAt:FieldValue.serverTimestamp(),updatedBy:request.auth.uid});
    t.set(newPhoneRef,{employeeId:id});
    if(oldRef && oldPhone!==phone && oldGuard?.get('employeeId')===id)t.delete(oldRef);
  });
  return {employeeId:id,message:'Employee updated. Use the new phone number for the next OTP login.'};
});

// Register one device token with a server-verified role; clients cannot choose recipients.
exports.registerDevice = onCall(options,async request=>{
 if(!request.auth)fail('Login required.','unauthenticated');
 const token=String(request.data?.token||'');if(token.length<20||token.length>4096||/\s/.test(token))fail('Invalid notification token.','invalid-argument');
 const adm=await db.doc(`admins/${request.auth.uid}`).get();let role,employeeId='',phone='';
 if(adm.exists&&adm.get('active')===true)role='admin';
 else{const e=await employee(request);role='employee';employeeId=e.id;phone=e.get('phone');}
 const id=require('node:crypto').createHash('sha256').update(token).digest('hex');
 await db.doc(`deviceTokens/${id}`).set({uid:request.auth.uid,token,role,employeeId,phone,updatedAt:FieldValue.serverTimestamp()});
 return {registered:true};
});
// Derive identity from phone authentication, never from a supplied employee ID.
exports.reportMockLocation = onCall(options,async request=>{
  const e=await employee(request);
  const source=request.data?.source;
  if(!['check_in','tracking'].includes(source))fail('Invalid location report source.','invalid-argument');
  const bucket=Math.floor(Date.now()/300000);
  const id=require('node:crypto').createHash('sha256').update(`${e.id}_${bucket}`).digest('hex');
  const ref=db.doc(`mockLocationReports/${id}`);
  await db.runTransaction(async t=>{
    const existing=await t.get(ref),current=await t.get(e.ref),actor=await t.get(db.doc(`admins/${request.auth.uid}`));
    if(!actorOwns(request,current,actor))fail('Employee access changed.','permission-denied');
    t.update(e.ref,{attendanceBlocked:true,attendanceBlockReason:'Mock GPS detected',attendanceBlockedAt:FieldValue.serverTimestamp()});
    if(existing.exists)return;
    t.set(ref,{employeeId:e.id,employeeName:e.get('name')||'Employee',department:e.get('role')||'not set',source,reportingUid:request.auth.uid,createdAt:FieldValue.serverTimestamp()});
  });
  return {reported:true};
});
exports.releaseAttendanceBlock = onCall(options,async request=>{
  await admin(request);
  const id=String(request.data?.employeeId||''),note=String(request.data?.note||'').trim();
  if(!/^[A-Za-z0-9_-]{1,128}$/.test(id)||note.length<3||note.length>500)fail('Select an employee and enter an approval reason (3–500 characters).','invalid-argument');
  const ref=db.doc(`employees/${id}`),audit=db.collection('attendanceBlockReleases').doc();
  return db.runTransaction(async t=>{
    const current=await t.get(ref);
    if(!current.exists||current.get('active')!==true)fail('Active employee not found.','not-found');
    if(current.get('attendanceBlocked')!==true)return {released:false};
    t.update(ref,{attendanceBlocked:false,attendanceBlockReason:'',attendanceReleasedAt:FieldValue.serverTimestamp(),attendanceReleasedBy:request.auth.uid});
    t.set(audit,{employeeId:id,adminUid:request.auth.uid,note,createdAt:FieldValue.serverTimestamp()});
    return {released:true};
  });
});
exports.linkAdminEmployee=onCall(options,async request=>{
 await admin(request);const phone=policy.normalizePhone(request.data?.phone);
 if(!phone)fail('Enter a valid Sri Lankan phone number.','invalid-argument');
 const audit=db.collection('adminEmployeeLinks').doc();
 return db.runTransaction(async t=>{
  const admRef=db.doc(`admins/${request.auth.uid}`),a=await t.get(admRef);
  const employees=await t.get(db.collection('employees'));
  const matches=employees.docs.filter(e=>policy.normalizePhone(e.get('phone'))===phone);
  if(a.get('active')!==true)fail('Admin access required.','permission-denied');
  if(matches.length!==1||matches[0].get('active')!==true)fail('Add one active employee record with your phone number first.');
  const e=matches[0];
  if(a.get('employeeId')&&a.get('employeeId')!==e.id)fail('Your admin account is already linked. Contact the company administrator to change it.');
  if(e.get('adminUid')&&e.get('adminUid')!==request.auth.uid)fail('This employee record is linked to another administrator.','already-exists');
  if(a.get('employeeId')===e.id&&e.get('adminUid')===request.auth.uid)return {linked:true,employeeId:e.id};
  t.update(admRef,{employeeId:e.id});t.update(e.ref,{adminUid:request.auth.uid});
  t.set(audit,{adminUid:request.auth.uid,employeeId:e.id,createdAt:FieldValue.serverTimestamp()});
  return {linked:true,employeeId:e.id};
 });
});
// Remove from the working list without destroying attendance/leave history.
exports.removeEmployee=onCall(options,async request=>{
 await admin(request);const id=String(request.data?.employeeId||'');
 if(!/^[A-Za-z0-9_-]{1,128}$/.test(id))fail('Invalid employee ID.','invalid-argument');
 const ref=db.doc(`employees/${id}`),audit=db.collection('employeeRemovals').doc();
 return db.runTransaction(async t=>{
  const a=await t.get(db.doc(`admins/${request.auth.uid}`)),e=await t.get(ref);
  const pending=await t.get(db.collection('leaveRequests').where('employeeId','==',id).where('status','==','pending'));
  if(a.get('active')!==true)fail('Admin access required.','permission-denied');
  if(!e.exists)fail('Employee not found.','not-found');
  if(e.get('archived')===true)return {removed:false};
  if(!pending.empty)fail('Decide pending leave requests before removing this employee.');
  t.update(ref,{active:false,archived:true,removedAt:FieldValue.serverTimestamp(),removedBy:request.auth.uid});
  t.set(audit,{employeeId:id,adminUid:request.auth.uid,createdAt:FieldValue.serverTimestamp()});
  return {removed:true};
 });
});
// Server-validated schedule and durable department announcement outbox.
exports.publishDepartmentNotice=onCall(options,async request=>{
 await admin(request);
 const raw=request.data||{},id=String(raw.id||'');
 if(!/^[a-zA-Z0-9-]{20,60}$/.test(id))fail('Invalid notice request.','invalid-argument');
 let value;try{value=require('./schedule-policy').validate(raw);}catch(e){fail(e.message,'invalid-argument');}
 const hash=require('node:crypto').createHash('sha256').update(JSON.stringify(value)).digest('hex');
 const ref=db.doc(`departmentNotices/${id}`);
 await db.runTransaction(async t=>{
  const a=await t.get(db.doc(`admins/${request.auth.uid}`)),old=await t.get(ref);
  if(a.get('active')!==true)fail('Admin access required.','permission-denied');
  if(old.exists){if(old.get('adminUid')!==request.auth.uid||old.get('hash')!==hash)fail('Refresh before sending a changed notice.','already-exists');return;}
  t.set(db.doc(`departmentCalendars/${value.department}/days/${value.date}`),{...value,adminUid:request.auth.uid,updatedAt:FieldValue.serverTimestamp()});
  t.set(ref,{...value,hash,adminUid:request.auth.uid,createdAt:FieldValue.serverTimestamp()});
 });
 return {queued:true,id};
});
Object.assign(exports,require('./work-features')({db,employee,admin,actorOwns,fail,options,FieldValue}));
Object.assign(exports,require('./alerts'));

Object.assign(exports,require('./secure-attendance')({db,employee,actorOwns,fail,options,FieldValue}));
