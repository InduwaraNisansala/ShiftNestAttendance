// Executes real callable handlers against a serializable in-memory Firestore adapter.
// This tests workflow logic, not Firestore SDK/rules/network integration.
const {test}=require('node:test');const assert=require('node:assert/strict');const Module=require('node:module');const p=require('../leave-policy');
class HttpsError extends Error {constructor(code,message){super(message);this.code=code;}}
function fixture(){
 const data=new Map(),pushes=[];let next=0,queue=Promise.resolve();
 const snap=path=>({id:path.split('/').pop(),ref:ref(path),exists:data.has(path),get:k=>data.get(path)?.[k],data:()=>data.get(path)});
 const query=(collection,filters=[],max=Infinity,after='',group=false)=>({
 where:(key,op,value)=>query(collection,[...filters,[key,value]],max,after,group),
 orderBy:()=>query(collection,filters,max,after,group),limit:n=>query(collection,filters,n,after,group),startAfter:doc=>query(collection,filters,max,doc.ref.path,group),
 get:async()=>{const docs=[...data.keys()].filter(path=>(group?path.split('/').slice(-2,-1)[0]===collection:path.split('/').slice(0,-1).join('/')===collection)&&path>after&&filters.every(([k,v])=>data.get(path)[k]===v)).sort().slice(0,max).map(snap);return{docs,size:docs.length,empty:!docs.length};}
 });
 const ref=path=>({path,id:path.split('/').pop(),get:async()=>snap(path),set:async value=>{data.set(path,value);},delete:async()=>{data.delete(path);}});
 const db={doc:ref,collectionGroup:name=>query(name,[],Infinity,'',true),collection:name=>({...query(name),doc:id=>ref(name+'/'+(id||String(++next).padStart(20,'0')))}),runTransaction:async callback=>{let unlock;const previous=queue;queue=new Promise(r=>unlock=r);await previous;let wrote=false;const writes=[];try{const t={get:async r=>{assert.equal(wrote,false,'transaction reads must precede writes');return r.get();},set:(r,v)=>{wrote=true;writes.push(()=>data.set(r.path,v));},update:(r,v)=>{wrote=true;writes.push(()=>data.set(r.path,{...data.get(r.path),...v}));},delete:r=>{wrote=true;writes.push(()=>data.delete(r.path));}};const result=await callback(t);writes.forEach(w=>w());return result;}finally{unlock();}}};
 const original=Module._load;Module._load=function(name,parent,main){if(name==='firebase-functions/v2/https')return{onCall:(o,f)=>{const handler=request=>f({...request,app:request.app===undefined?{appId:'approved-test-app'}:request.app});handler.options=o;return handler;},HttpsError};if(name==='firebase-functions/v2/scheduler')return{onSchedule:(_o,f)=>f};if(name==='firebase-functions/v2/firestore')return{onDocumentWritten:(_o,f)=>f};if(name==='firebase-admin/messaging')return{getMessaging:()=>({sendEachForMulticast:async message=>{pushes.push(message);return {responses:message.tokens.map(()=>({success:true}))};}})};if(name==='firebase-admin/auth')return{getAuth:()=>({getUserByPhoneNumber:async phone=>{const e=[...data.entries()].find(([path,value])=>path.startsWith('employees/')&&value.phone===phone&&value.authReady!==false);if(!e){const error=new Error('No auth user');error.code='auth/user-not-found';throw error;}return{uid:e[1].authUid||e[0].split('/')[1]};}})};if(name==='firebase-admin/app')return{initializeApp:()=>{}};if(name==='firebase-admin/firestore')return{getFirestore:()=>db,FieldValue:{serverTimestamp:()=>({toDate:()=>new Date()})}};return original.call(this,name,parent,main);};delete require.cache[require.resolve('../index')];delete require.cache[require.resolve('../alerts')];let functions;try{functions=require('../index');}finally{Module._load=original;}
 const today=p.today();data.set('settings/leavePolicy',{holidayCoverageStart:'2020-01-01',holidayCoverageEnd:'2099-12-31',blockedWeekdays:[]});data.set('admins/admin',{active:true});data.set('settings/attendanceSecurity',{allowedAppIds:['approved-test-app']});
 const add=(id,phone,worked=10)=>{data.set('employees/'+id,{name:id,phone,role:'Sales Officer',active:true});for(let i=0;i<worked;i++){let date=new Date(Date.parse(today+'T00:00:00Z')-(i+1)*86400000).toISOString().slice(0,10);data.set('attendance/'+id+'_'+date,{employeeId:id,date,checkIn:'08:00:00',checkOut:'17:00:00',workStatus:'Completed'});}return{auth:{uid:id,token:{phone_number:phone}},data:{date:today,reason:'Personal leave'}};};return{data,functions,today,add,pushes};
}
test('two employees cannot reserve the same date concurrently',async()=>{const f=fixture(),a=f.add('a','+94770000001'),b=f.add('b','+94770000002');const results=await Promise.allSettled([f.functions.applyLeave(a),f.functions.applyLeave(b)]);assert.equal(results.filter(r=>r.status==='fulfilled').length,1);assert.match(results.find(r=>r.status==='rejected').reason.message,/already booked/);assert.equal([...f.data.keys()].filter(k=>k.startsWith('leaveRequests/')).length,1);});
test('pending reservation blocks spending the same earned day on a different date',async()=>{const f=fixture(),a=f.add('a','+94770000001');await f.functions.applyLeave(a);a.data.date=new Date(Date.parse(f.today+'T00:00:00Z')+86400000).toISOString().slice(0,10);await assert.rejects(f.functions.applyLeave(a),/more work day/);});
test('only admin can decide; rejection unlocks date and balance',async()=>{const f=fixture(),a=f.add('a','+94770000001'),b=f.add('b','+94770000002');const r=await f.functions.applyLeave(a);await assert.rejects(f.functions.decideLeave({...a,data:{id:r.id,status:'approved',note:'Approved by Chairman'}}),/Admin access/);await f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'rejected',note:'Rejected by Chairman'}});assert.equal(f.data.has('leaveDateLocks/'+f.today),false);await f.functions.applyLeave(b);assert.equal(f.data.get('leaveDateLocks/'+f.today).employeeId,'b');});
test('approval consumes earned leave, preserves lock and cannot be decided twice',async()=>{const f=fixture(),a=f.add('a','+94770000001');const r=await f.functions.applyLeave(a);await f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'approved',note:'Approved by Chairman'}});assert.equal(f.data.get('leaveRequests/'+r.id).status,'approved');assert.equal(f.data.get('leaveDateLocks/'+f.today).status,'approved');await assert.rejects(f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'rejected',note:'Changed'}}),/already been decided/);});
test('holiday checks are enforced again when admin approves',async()=>{const f=fixture(),a=f.add('a','+94770000001');const r=await f.functions.applyLeave(a);f.data.set('holidays/'+f.today,{name:'Company holiday',blocked:true});await assert.rejects(f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'approved',note:'Approved'}}),/holiday/);assert.equal(f.data.get('leaveRequests/'+r.id).status,'pending');});
test('Office/Hotel quotas are not invented and unauthenticated callers are denied',async()=>{const f=fixture(),a=f.add('a','+94770000001');f.data.get('employees/a').role='Hotel Officer';await assert.rejects(f.functions.applyLeave(a),/company confirmation/);await assert.rejects(f.functions.applyLeave({data:a.data}),/Sign in/);});
test('attendance already present prevents booking a full-day leave',async()=>{const f=fixture(),a=f.add('a','+94770000001');f.data.set('attendance/a_today',{employeeId:'a',date:f.today,checkIn:'08:00:00'});await assert.rejects(f.functions.applyLeave(a),/Attendance is already recorded/);});
test('inactive or revoked admin cannot approve',async()=>{const f=fixture(),a=f.add('a','+94770000001');const r=await f.functions.applyLeave(a);f.data.set('admins/admin',{active:false});await assert.rejects(f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'approved',note:'Approved'}}),/Admin access/);});
test('creating an employee rejects duplicate phone numbers transactionally',async()=>{const f=fixture();const data={name:'New employee',phone:'+94770000005',role:'Staff Officer',workLocationType:'no_fixed'};const input={auth:{uid:'admin'},data};const results=await Promise.allSettled([f.functions.createEmployee(input),f.functions.createEmployee(input)]);assert.equal(results.filter(r=>r.status==='fulfilled').length,1);assert.match(results.find(r=>r.status==='rejected').reason.message,/already registered/);});

test('admin-created staff quota includes opening used days and is not assigned to Sales',async()=>{
 const f=fixture(),base={name:'Staff',phone:'+94770000009',role:'Staff Officer',workLocationType:'no_fixed',annualLeaveAllowance:20,annualLeaveUsedBeforeApp:5,annualLeaveYear:Number(f.today.slice(0,4))};
 const result=await f.functions.createEmployee({auth:{uid:'admin'},data:base});
 const employee=f.data.get('employees/'+result.employeeId);
 assert.equal(employee.annualLeaveAllowance,20);assert.equal(employee.annualLeaveUsedBeforeApp,5);
 const overview=await f.functions.leaveOverview({auth:{uid:'staff',token:{phone_number:base.phone}}});
 assert.equal(overview.remaining,15);assert.equal(overview.used,5);assert.equal(overview.configured,true);
 const sales=await f.functions.createEmployee({auth:{uid:'admin'},data:{...base,phone:'+94770000008',role:'Sales Officer'}});
 assert.equal(f.data.get('employees/'+sales.employeeId).annualLeaveAllowance,undefined);
});
test('invalid staff allowance cannot be saved',async()=>{
 const f=fixture(),data={name:'Staff',phone:'+94770000009',role:'Staff Officer',workLocationType:'no_fixed',annualLeaveAllowance:20,annualLeaveUsedBeforeApp:21,annualLeaveYear:Number(f.today.slice(0,4))};
 await assert.rejects(f.functions.createEmployee({auth:{uid:'admin'},data}),/valid annual allowance/);
 await assert.rejects(f.functions.createEmployee({auth:{uid:'admin'},data:{...data,annualLeaveUsedBeforeApp:0,annualLeaveAllowance:1.5}}),/valid annual allowance/);
});
test('two staff can book same date while same employee cannot double-book',async()=>{
 const f=fixture(),a=f.add('a','+94770000001'),b=f.add('b','+94770000002');
 for(const id of ['a','b'])Object.assign(f.data.get('employees/'+id),{role:'Hotel Officer',annualLeaveAllowance:20,annualLeaveUsedBeforeApp:5,annualLeaveYear:Number(f.today.slice(0,4))});
 const results=await Promise.all([f.functions.applyLeave(a),f.functions.applyLeave(b)]);
 assert.equal(results.length,2);assert.equal(f.data.has('leaveDateLocks/'+f.today),false);
 await assert.rejects(f.functions.applyLeave(a),/already booked/);
 let overview=await f.functions.leaveOverview(a);assert.equal(overview.remaining,14);assert.equal(overview.pending,1);
 await f.functions.decideLeave({auth:{uid:'admin'},data:{id:results[0].id,status:'approved',note:'Chairman approved'}});
 overview=await f.functions.leaveOverview(a);assert.equal(overview.used,6);assert.equal(overview.pending,0);assert.equal(overview.remaining,14);
 assert.equal(f.data.get('leaveEmployeeDates/a_'+f.today).status,'approved');
});
test('staff rejection releases both annual balance and own date',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');Object.assign(f.data.get('employees/a'),{role:'Staff Officer',annualLeaveAllowance:1,annualLeaveUsedBeforeApp:0,annualLeaveYear:Number(f.today.slice(0,4))});
 const r=await f.functions.applyLeave(a);
 await f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'rejected',note:'Chairman rejected'}});
 assert.equal(f.data.has('leaveEmployeeDates/a_'+f.today),false);
 assert.equal((await f.functions.leaveOverview(a)).remaining,1);
 await f.functions.applyLeave(a);
});
test('annual quota rechecks at approval and never carries to another year',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');Object.assign(f.data.get('employees/a'),{role:'Hotel Officer',annualLeaveAllowance:1,annualLeaveUsedBeforeApp:0,annualLeaveYear:Number(f.today.slice(0,4))});
 const r=await f.functions.applyLeave(a);f.data.get('employees/a').annualLeaveUsedBeforeApp=1;
 await assert.rejects(f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'approved',note:'Approved'}}),/sufficient earned leave/);
 a.data.date=String(Number(f.today.slice(0,4))+1)+'-01-02';
 assert.equal((await f.functions.leaveOverview(a)).configured,false);
 await assert.rejects(f.functions.applyLeave(a),/company confirmation/);
});
test('staff may request holiday leave under annual quota but cannot spend pending balance twice',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');Object.assign(f.data.get('employees/a'),{role:'Staff Officer',annualLeaveAllowance:1,annualLeaveUsedBeforeApp:0,annualLeaveYear:Number(f.today.slice(0,4))});
 f.data.set('holidays/'+f.today,{name:'Poya',blocked:true});
 await f.functions.applyLeave(a);a.data.date=new Date(Date.parse(f.today+'T00:00:00Z')+86400000).toISOString().slice(0,10);
 await assert.rejects(f.functions.applyLeave(a),/No annual leave balance/);
});

test('employee update preserves employee ID and attendance history and transfers phone guard',async()=>{
 const f=fixture();f.add('a','+94770000001');f.data.set('employeePhones/+94770000001',{employeeId:'a'});
 const input={auth:{uid:'admin'},data:{employeeId:'a',name:'Updated Name',employeeNumber:'EMP-01',phone:'077 000 0003',role:'Staff Officer',active:true,workLocationType:'no_fixed',annualLeaveAllowance:20,annualLeaveUsedBeforeApp:4,annualLeaveYear:Number(f.today.slice(0,4))}};
 await f.functions.updateEmployee(input);const e=f.data.get('employees/a');assert.equal(e.name,'Updated Name');assert.equal(e.phone,'+94770000003');assert.equal(e.annualLeaveAllowance,20);
 assert.equal(f.data.has('employeePhones/+94770000001'),false);assert.equal(f.data.get('employeePhones/+94770000003').employeeId,'a');
 assert.equal([...f.data.keys()].filter(k=>k.startsWith('attendance/a_')).length,10);
 await assert.rejects(f.functions.employeeProfile({auth:{uid:'old',token:{phone_number:'+94770000001'}}}),/employee record/);
 assert.equal((await f.functions.employeeProfile({auth:{uid:'new',token:{phone_number:'+94770000003'}}})).employeeId,'a');
});
test('employee edit rejects duplicates, inactive admins and unregistered sites',async()=>{
 const f=fixture();const a=f.add('a','+94770000001');f.add('b','+94770000002');
 const data={employeeId:'a',name:'A',phone:'+94770000002',role:'Sales Officer',active:true,workLocationType:'no_fixed'};
 await assert.rejects(f.functions.updateEmployee({auth:{uid:'admin'},data}),/already registered/);
 await assert.rejects(f.functions.updateEmployee({...a,data}),/Admin access/);
 await assert.rejects(f.functions.updateEmployee({auth:{uid:'admin'},data:{...data,phone:'+94770000001',workLocationType:'fixed',locationId:'missing'}}),/site does not exist/);
 assert.equal(f.data.get('employees/a').phone,'+94770000001');
});
test('pending leave prevents department edit but phone and name edits still work',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');await f.functions.applyLeave(a);
 const data={employeeId:'a',name:'A',phone:'+94770000001',role:'Hotel Officer',active:true,workLocationType:'no_fixed'};
 await assert.rejects(f.functions.updateEmployee({auth:{uid:'admin'},data}),/pending leave/);
 await f.functions.updateEmployee({auth:{uid:'admin'},data:{...data,role:'Sales Officer',name:'Renamed'}});assert.equal(f.data.get('employees/a').name,'Renamed');
});
test('concurrent employee phone edits cannot claim the same phone number',async()=>{
 const f=fixture();f.add('a','+94770000001');f.add('b','+94770000002');
 const request=id=>({auth:{uid:'admin'},data:{employeeId:id,name:id,phone:'+94770000009',role:'Sales Officer',active:true,workLocationType:'no_fixed'}});
 const results=await Promise.allSettled([f.functions.updateEmployee(request('a')),f.functions.updateEmployee(request('b'))]);assert.equal(results.filter(x=>x.status==='fulfilled').length,1);assert.match(results.find(x=>x.status==='rejected').reason.message,/already registered/);
});
test('employee editing resolves assigned site and can remove unknown annual allowance',async()=>{
 const f=fixture();f.add('a','+94770000001');f.data.set('locations/site',{name:'Correct site'});
 await f.functions.updateEmployee({auth:{uid:'admin'},data:{employeeId:'a',name:'Staff',phone:'+94770000001',role:'Staff Officer',active:true,workLocationType:'fixed',locationId:'site',locationName:'Forged label',annualLeaveAllowance:null}});
 const employee=f.data.get('employees/a');assert.equal(employee.locationName,'Correct site');assert.equal(employee.annualLeaveAllowance,null);assert.equal((await f.functions.leaveOverview({auth:{uid:'a',token:{phone_number:'+94770000001'}}})).configured,false);
});
test('registration checks use the updated active phone record',async()=>{
 const f=fixture();f.add('a','+94770000001');assert.equal((await f.functions.lookupEmployee({data:{phone:'+94770000001'}})).registered,true);
 await f.functions.updateEmployee({auth:{uid:'admin'},data:{employeeId:'a',name:'A',phone:'+94770000003',role:'Sales Officer',active:false,workLocationType:'no_fixed'}});
 await assert.rejects(f.functions.lookupEmployee({data:{phone:'+94770000003'}}),/not registered/);
 await assert.rejects(f.functions.lookupEmployee({data:{phone:'+94770000001'}}),/not registered/);
});

const eventFor=(f,path,id,before=null)=>({id,params:path.startsWith('leaveRequests/')?{requestId:path.split('/')[1]}:{employeeId:path.split('/')[1]},data:{before:{exists:!!before,data:()=>before},after:{exists:f.data.has(path),data:()=>f.data.get(path)}}});
test('Sales balance and messages follow pending -> rejection -> reapply -> approval',async()=>{
 const f=fixture(),a=f.add('a','+94770000001',20);let b=(await f.functions.leaveOverview(a));assert.equal(b.remaining,2);
 const r=await f.functions.applyLeave(a);assert.match(r.message,/Waiting for Chairman/);b=await f.functions.leaveOverview(a);assert.equal(b.remaining,1);assert.equal(b.pending,1);assert.equal(b.used,0);
 const rejection=await f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'rejected',note:'Chairman rejected'}});assert.match(rejection.message,/rejected by Chairman/);b=await f.functions.leaveOverview(a);assert.equal(b.remaining,2);assert.equal(b.pending,0);assert.equal(b.used,0);
 const r2=await f.functions.applyLeave(a);await f.functions.decideLeave({auth:{uid:'admin'},data:{id:r2.id,status:'approved',note:'Chairman approved'}});b=await f.functions.leaveOverview(a);assert.equal(b.remaining,1);assert.equal(b.pending,0);assert.equal(b.used,1);
});
for(const role of ['Staff Officer','Hotel Officer'])test(role+' balance releases exactly one pending day on rejection',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');Object.assign(f.data.get('employees/a'),{role,annualLeaveAllowance:20,annualLeaveUsedBeforeApp:5,annualLeaveYear:Number(f.today.slice(0,4))});
 assert.equal((await f.functions.leaveOverview(a)).remaining,15);const r=await f.functions.applyLeave(a);assert.equal((await f.functions.leaveOverview(a)).remaining,14);
 await f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'rejected',note:'Chairman rejected'}});const b=await f.functions.leaveOverview(a);assert.equal(b.remaining,15);assert.equal(b.pending,0);assert.equal(b.used,5);
});
test('new leave creates one durable notification per active admin and deduplicates trigger retries',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');f.data.set('admins/second',{active:true});f.data.set('admins/disabled',{active:false});f.data.set('deviceTokens/admin-token',{uid:'admin',role:'admin',token:'a'.repeat(30)});
 const r=await f.functions.applyLeave(a),event=eventFor(f,'leaveRequests/'+r.id,'leave-event');await f.functions.leaveAlerts(event);await f.functions.leaveAlerts(event);
 const alerts=[...f.data.keys()].filter(k=>k.startsWith('notifications/'));assert.equal(alerts.length,2);assert.equal(alerts.some(k=>k.startsWith('notifications/disabled/')),false);assert.equal(f.pushes.length,1);assert.equal(f.pushes[0].data.role,'admin');assert.equal(f.pushes[0].notification,undefined);
});
test('employee rejection alert targets only devices registered with the current phone',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');f.data.set('deviceTokens/current',{uid:'a',role:'employee',employeeId:'a',phone:'+94770000001',token:'a'.repeat(30)});f.data.set('deviceTokens/old',{uid:'old',role:'employee',employeeId:'a',phone:'+94770000009',token:'b'.repeat(30)});
 const r=await f.functions.applyLeave(a),path='leaveRequests/'+r.id,before={...f.data.get(path)};await f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'rejected',note:'Chairman rejected'}});await f.functions.leaveAlerts(eventFor(f,path,'decision-event',before));
 assert.equal(f.pushes.length,1);assert.equal(f.pushes[0].data.recipientUid,'a');assert.match(f.pushes[0].data.body,/reserved leave has been released/);assert.equal([...f.data.keys()].some(k=>k.startsWith('notifications/old/')),false);
});
test('device registration derives role and identity from auth, not client-supplied role',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');await f.functions.registerDevice({...a,data:{token:'token'.repeat(8),role:'admin',uid:'admin'}});const token=[...f.data.entries()].find(([k])=>k.startsWith('deviceTokens/'))[1];assert.equal(token.role,'employee');assert.equal(token.uid,'a');assert.equal(token.employeeId,'a');
 await assert.rejects(f.functions.registerDevice({data:{token:'token'.repeat(8)}}),/Login required/);
});
test('outside alerts cover fixed-site Sales/Office/Hotel and fire once per exit, not each GPS update',async()=>{
 for(const role of ['Sales Officer','Staff Officer','Hotel Officer']){
  const f=fixture(),a=f.add('a','+94770000001');Object.assign(f.data.get('employees/a'),{role,workLocationType:'fixed',locationId:'site'});f.data.set('locations/site',{name:'Office',latitude:6.9,longitude:79.9,allowedRadius:100});const path='latestLocations/a',stamp=Date.now();
  const reading=(offset,lat)=>({locationId:'site',trackingStatus:'Working',latitude:lat,longitude:79.9,accuracyMeters:5,timestamp:new Date(stamp+offset),fixTimestamp:new Date(stamp+offset)});
  f.data.set(path,reading(0,6.91));await f.functions.locationAlerts(eventFor(f,path,'exit-one'));await f.functions.locationAlerts(eventFor(f,path,'exit-one'));assert.equal([...f.data.keys()].filter(k=>k.startsWith('notifications/')).length,1);
  f.data.set(path,reading(1,6.91));await f.functions.locationAlerts(eventFor(f,path,'outside-again'));assert.equal([...f.data.keys()].filter(k=>k.startsWith('notifications/')).length,1);
  f.data.set(path,reading(2,6.9));await f.functions.locationAlerts(eventFor(f,path,'inside'));f.data.set(path,reading(3,6.91));await f.functions.locationAlerts(eventFor(f,path,'exit-two'));assert.equal([...f.data.keys()].filter(k=>k.startsWith('notifications/')).length,2);
 }
});
test('employee gets a durable decision inbox item even without a push device token',async()=>{
 const f=fixture(),a=f.add('a','+94770000001'),r=await f.functions.applyLeave(a),path='leaveRequests/'+r.id,before={...f.data.get(path)};
 await f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'approved',note:'Chairman approved'}});await f.functions.leaveAlerts(eventFor(f,path,'decision-without-device',before));
 assert.equal([...f.data.keys()].filter(k=>k.startsWith('notifications/a/items/')).length,1);assert.equal(f.pushes.length,0);
});
test('a late pending trigger does not announce a request already decided',async()=>{
 const f=fixture(),a=f.add('a','+94770000001'),r=await f.functions.applyLeave(a),path='leaveRequests/'+r.id,pending={...f.data.get(path)};
 await f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'rejected',note:'Chairman rejected'}});
 const event=eventFor(f,path,'late-pending');event.data.after.data=()=>pending;await f.functions.leaveAlerts(event);
 assert.equal([...f.data.keys()].filter(k=>k.startsWith('notifications/admin/items/')).length,0);
});

test('mock reports use authenticated identity, deduplicate and notify active admins only',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');f.data.set('admins/revoked',{active:false});f.data.set('deviceTokens/admin',{uid:'admin',role:'admin',token:'admin-token'});
 const input={...a,data:{source:'check_in',employeeId:'victim',employeeName:'Forged'}};
 await Promise.all([f.functions.reportMockLocation(input),f.functions.reportMockLocation(input)]);
 const reports=[...f.data.entries()].filter(([k])=>k.startsWith('mockLocationReports/'));assert.equal(reports.length,1);
 const [path,report]=reports[0];assert.equal(report.employeeId,'a');assert.equal(report.employeeName,'a');
 const event={params:{reportId:path.split('/')[1]},data:{after:{exists:true,data:()=>report}}};
 await f.functions.mockLocationAlerts(event);await f.functions.mockLocationAlerts(event);
 const inbox=[...f.data.entries()].filter(([k])=>k.startsWith('notifications/'));assert.equal(inbox.length,1);assert.match(inbox[0][0],/^notifications\/admin\//);assert.equal(inbox[0][1].type,'location_mock');assert.match(inbox[0][1].body,/Check-in was blocked/);assert.equal(f.pushes.length,1);assert.equal(f.pushes[0].data.type,'location_mock');
 assert.equal([...f.data.keys()].filter(k=>k.startsWith('attendance/')).length,10);
});
test('mock reporting rejects anonymous, inactive employees and invalid sources',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');await assert.rejects(f.functions.reportMockLocation({data:{source:'tracking'}}),/Sign in/);await assert.rejects(f.functions.reportMockLocation({...a,data:{source:'invented'}}),/Invalid location/);f.data.get('employees/a').active=false;await assert.rejects(f.functions.reportMockLocation({...a,data:{source:'tracking'}}),/employee record/);assert.equal([...f.data.keys()].filter(k=>k.startsWith('mockLocationReports/')).length,0);
});
test('all departments including no-fixed-site staff report mock tracking without removing attendance',async()=>{
 for(const role of ['Sales Officer','Staff Officer','Hotel Officer']){
 const f=fixture(),a=f.add('a','+94770000001');Object.assign(f.data.get('employees/a'),{role,workLocationType:'no_fixed'});await f.functions.reportMockLocation({...a,data:{source:'tracking'}});
 const [path,report]=[...f.data.entries()].find(([k])=>k.startsWith('mockLocationReports/'));await f.functions.mockLocationAlerts({params:{reportId:path.split('/')[1]},data:{after:{exists:true,data:()=>report}}});
 const [,alert]=[...f.data.entries()].find(([k])=>k.startsWith('notifications/admin/'));assert.match(alert.body,/fake GPS reading was discarded/);assert.match(alert.body,/existing check-in remains recorded/);assert.equal(report.department,role);
 }
});

test('admin approval releases persistent attendance block and saves an audit reason',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');await f.functions.reportMockLocation({...a,data:{source:'check_in'}});assert.equal(f.data.get('employees/a').attendanceBlocked,true);
 await assert.rejects(f.functions.releaseAttendanceBlock({...a,data:{employeeId:'a',note:'Forgiven'}}),/Admin access/);
 await assert.rejects(f.functions.releaseAttendanceBlock({auth:{uid:'admin'},data:{employeeId:'a',note:''}}),/approval reason/);
 const result=await f.functions.releaseAttendanceBlock({auth:{uid:'admin'},data:{employeeId:'a',note:'Company approved after review'}});assert.equal(result.released,true);assert.equal(f.data.get('employees/a').attendanceBlocked,false);assert.equal(f.data.get('employees/a').attendanceReleasedBy,'admin');
 const audits=[...f.data.entries()].filter(([k])=>k.startsWith('attendanceBlockReleases/'));assert.equal(audits.length,1);assert.equal(audits[0][1].note,'Company approved after review');
 assert.equal((await f.functions.releaseAttendanceBlock({auth:{uid:'admin'},data:{employeeId:'a',note:'Repeat'}})).released,false);
 assert.equal([...f.data.keys()].filter(k=>k.startsWith('attendanceBlockReleases/')).length,1);
 // New mock readings immediately re-block even when the five-minute alert was already deduplicated.
 await f.functions.reportMockLocation({...a,data:{source:'tracking'}});assert.equal(f.data.get('employees/a').attendanceBlocked,true);
});
test('revoked admin cannot forgive a mock GPS block',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');await f.functions.reportMockLocation({...a,data:{source:'tracking'}});f.data.get('admins/admin').active=false;
 await assert.rejects(f.functions.releaseAttendanceBlock({auth:{uid:'admin'},data:{employeeId:'a',note:'Company approval'}}),/Admin access/);assert.equal(f.data.get('employees/a').attendanceBlocked,true);
});
const noticeRequest=(date,patch={})=>({auth:{uid:'admin'},data:{id:'notice-00000000000000001',department:'Sales',date,kind:'working',reason:'Department meeting',checkIn:'09:00',checkOut:'17:30',...patch}});
test('only active admin publishes schedules and the same request does not duplicate a notice',async()=>{
 const f=fixture(),r=noticeRequest(f.today);await f.functions.publishDepartmentNotice(r);await f.functions.publishDepartmentNotice(r);
 assert.equal([...f.data.keys()].filter(k=>k.startsWith('departmentNotices/')).length,1);assert.equal(f.data.get('departmentCalendars/Sales/days/'+f.today).workingMinutes,510);
 await assert.rejects(f.functions.publishDepartmentNotice({...r,auth:{uid:'other'}}),/Admin access/);
 await assert.rejects(f.functions.publishDepartmentNotice({...r,data:{...r.data,reason:'Changed'}}),/changed notice/);
});
test('department notice targets only active matching staff, has durable inbox without auth/token, and deduplicates',async()=>{
 const f=fixture();f.add('sales','+94770000001');f.add('office','+94770000002');f.data.get('employees/office').role='Staff Officer';f.add('inactive','+94770000003');f.data.get('employees/inactive').active=false;f.add('neverSignedIn','+94770000004');f.data.get('employees/neverSignedIn').authReady=false;
 f.data.set('deviceTokens/sales',{uid:'sales',role:'employee',employeeId:'sales',phone:'+94770000001',token:'real-sales-token'});
 const r=noticeRequest(f.today);await f.functions.publishDepartmentNotice(r);const body=f.data.get('departmentNotices/'+r.data.id);const event={params:{noticeId:r.data.id},data:{after:{exists:true,data:()=>body}}};
 await f.functions.departmentNoticeAlerts(event);await f.functions.departmentNoticeAlerts(event);
 assert.equal(f.data.has('employeeNotices/sales/items/'+r.data.id),true);assert.equal(f.data.has('employeeNotices/neverSignedIn/items/'+r.data.id),true);assert.equal(f.data.has('employeeNotices/office/items/'+r.data.id),false);assert.equal(f.data.has('employeeNotices/inactive/items/'+r.data.id),false);assert.equal(f.pushes.length,1);
});
test('department holiday blocks only that department leave and approval, global holidays still apply',async()=>{
 const f=fixture(),sales=f.add('sales','+94770000001'),office=f.add('office','+94770000002');f.data.get('employees/office').role='Staff Officer';Object.assign(f.data.get('employees/office'),{annualLeaveYear:Number(f.today.slice(0,4)),annualLeaveAllowance:20,annualLeaveUsedBeforeApp:0});
 const r=await f.functions.applyLeave(sales);await f.functions.publishDepartmentNotice(noticeRequest(f.today,{kind:'holiday',reason:'Sales off day'}));
 await assert.rejects(f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'approved',note:'Approved'}}),/company holiday/);
 await f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'rejected',note:'Holiday; choose another day'}});
 await assert.rejects(f.functions.applyLeave(sales),/company holiday/);await f.functions.applyLeave(office);
 const overview=await f.functions.leaveOverview(sales);assert.equal(overview.holidays.some(x=>x.date===f.today&&x.blocked===true),true);
});
test('legacy formatted and inactive employee numbers cannot be duplicated by create or edit',async()=>{
 const f=fixture();f.add('legacy','077 000 0001');f.data.get('employees/legacy').active=false;f.add('other','+94770000002');
 await assert.rejects(f.functions.createEmployee({auth:{uid:'admin'},data:{name:'Duplicate',phone:'+94770000001',role:'Sales Officer',workLocationType:'no_fixed'}}),/already registered/);
 await assert.rejects(f.functions.updateEmployee({auth:{uid:'admin'},data:{employeeId:'other',name:'Other',phone:'0770000001',role:'Sales Officer',active:true,workLocationType:'no_fixed'}}),/already registered/);
 assert.equal(f.data.get('employees/other').phone,'+94770000002');
});
test('remove employee archives and disables login while retaining history and phone uniqueness',async()=>{
 const f=fixture();f.add('a','+94770000001');const history=[...f.data.keys()].filter(k=>k.startsWith('attendance/a_'));
 await assert.rejects(f.functions.removeEmployee({auth:{uid:'unknown'},data:{employeeId:'a'}}),/Admin access/);
 assert.equal((await f.functions.removeEmployee({auth:{uid:'admin'},data:{employeeId:'a'}})).removed,true);
 assert.equal(f.data.get('employees/a').active,false);assert.equal(f.data.get('employees/a').archived,true);assert.equal(history.every(k=>f.data.has(k)),true);
 await assert.rejects(f.functions.lookupEmployee({data:{phone:'+94770000001'}}),/not registered/);
 await assert.rejects(f.functions.createEmployee({auth:{uid:'admin'},data:{name:'Duplicate',phone:'0770000001',role:'Sales Officer',workLocationType:'no_fixed'}}),/already registered/);
 assert.equal((await f.functions.removeEmployee({auth:{uid:'admin'},data:{employeeId:'a'}})).removed,false);assert.equal([...f.data.keys()].filter(k=>k.startsWith('employeeRemovals/')).length,1);
});
test('pending leave must be decided before removal and same employee can be restored',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');const leave=await f.functions.applyLeave(a);
 await assert.rejects(f.functions.removeEmployee({auth:{uid:'admin'},data:{employeeId:'a'}}),/pending leave/);assert.equal(f.data.get('employees/a').active,true);
 await f.functions.decideLeave({auth:{uid:'admin'},data:{id:leave.id,status:'rejected',note:'Test request closed'}});
 await f.functions.removeEmployee({auth:{uid:'admin'},data:{employeeId:'a'}});
 await f.functions.updateEmployee({auth:{uid:'admin'},data:{employeeId:'a',name:'Restored',phone:'+94770000001',role:'Sales Officer',active:true,workLocationType:'no_fixed'}});
 assert.equal(f.data.get('employees/a').archived,false);assert.equal(f.data.get('employees/a').active,true);assert.equal(f.data.has('leaveRequests/'+leave.id),true);
});
test('admin employee link is server-owned, unique, idempotent and preserves ordinary employee identity',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');f.add('b','+94770000002');
 await assert.rejects(f.functions.employeeProfile({auth:{uid:'admin',token:{}}}),/Link your admin/);
 await assert.rejects(f.functions.linkAdminEmployee({auth:{uid:'a',token:a.auth.token},data:{phone:'+94770000001'}}),/Admin access/);
 await f.functions.linkAdminEmployee({auth:{uid:'admin'},data:{phone:'0770000001',employeeId:'b'}});
 await f.functions.linkAdminEmployee({auth:{uid:'admin'},data:{phone:'+94770000001'}});
 assert.equal(f.data.get('admins/admin').employeeId,'a');assert.equal(f.data.get('employees/a').adminUid,'admin');assert.equal([...f.data.keys()].filter(k=>k.startsWith('adminEmployeeLinks/')).length,1);
 const own=await f.functions.employeeProfile({auth:{uid:'admin',token:{}},data:{employeeId:'b'}});assert.equal(own.employeeId,'a');
 assert.equal((await f.functions.employeeProfile(a)).employeeId,'a');
 f.data.set('admins/other',{active:true});await assert.rejects(f.functions.linkAdminEmployee({auth:{uid:'other'},data:{phone:'+94770000001'}}),/another administrator/);
 await assert.rejects(f.functions.linkAdminEmployee({auth:{uid:'admin'},data:{phone:'+94770000002'}}),/already linked/);
});
test('admin can apply own leave and report fake GPS, but another admin must record own Chairman decision',async()=>{
 const f=fixture();f.add('a','+94770000001');await f.functions.linkAdminEmployee({auth:{uid:'admin'},data:{phone:'+94770000001'}});
 const actor={auth:{uid:'admin',token:{}},data:{date:f.today,reason:'Own leave',employeeId:'someoneElse'}};const r=await f.functions.applyLeave(actor);
 assert.equal(f.data.get('leaveRequests/'+r.id).employeeId,'a');assert.equal(f.data.get('leaveRequests/'+r.id).employeeAuthUid,'admin');
 await assert.rejects(f.functions.decideLeave({auth:{uid:'admin'},data:{id:r.id,status:'approved',note:'Approved'}}),/Another administrator/);
 f.data.set('admins/chairman',{active:true});await f.functions.decideLeave({auth:{uid:'chairman'},data:{id:r.id,status:'rejected',note:'Chairman declined'}});
 await f.functions.reportMockLocation({auth:{uid:'admin',token:{}},data:{source:'check_in',employeeId:'someoneElse'}});assert.equal(f.data.get('employees/a').attendanceBlocked,true);
 f.data.get('admins/admin').active=false;await assert.rejects(f.functions.employeeProfile({auth:{uid:'admin',token:{}}}),/Sign in/);
});
test('own admin leave decision reaches admin session inbox and push with personal scope',async()=>{
 const f=fixture();f.add('a','+94770000001');await f.functions.linkAdminEmployee({auth:{uid:'admin'},data:{phone:'+94770000001'}});
 f.data.set('deviceTokens/admin',{uid:'admin',role:'admin',token:'admin-token'});f.data.set('admins/chairman',{active:true});
 const r=await f.functions.applyLeave({auth:{uid:'admin',token:{}},data:{date:f.today,reason:'Own leave'}});const before={...f.data.get('leaveRequests/'+r.id)};
 await f.functions.decideLeave({auth:{uid:'chairman'},data:{id:r.id,status:'approved',note:'Chairman approved'}});const after=f.data.get('leaveRequests/'+r.id);
 await f.functions.leaveAlerts({params:{requestId:r.id},data:{before:{exists:true,data:()=>before},after:{exists:true,data:()=>after}}});
 const notices=[...f.data.entries()].filter(([path])=>path.startsWith('notifications/admin/items/'));assert.equal(notices.length,1);assert.equal(notices[0][1].scope,'self');assert.equal(f.pushes[0].data.role,'admin');assert.equal(f.pushes[0].data.scope,'self');
});
test('late messages before check-in derive employee/date, stay outside attendance and retry idempotently',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');a.data={type:'late',requestId:'a'.repeat(32),reason:'Bus delayed',employeeId:'another-person',date:'2000-01-01'};
 await f.functions.submitWorkNotice(a);await f.functions.submitWorkNotice(a);
 assert.equal(f.data.has('attendance/a_'+f.today),false);assert.equal(f.data.get('attendanceNotices/a_'+f.today).lateMessage,'Bus delayed');assert.equal(f.data.get('attendanceNotices/a_'+f.today).revision,1);
 assert.equal(f.data.get('workEvents/'+'a'.repeat(32)).employeeId,'a');assert.equal(f.data.get('workEvents/'+'a'.repeat(32)).date,f.today);
 a.data.reason='Changed after save';await assert.rejects(f.functions.submitWorkNotice(a),/already used/);
});
test('late notice correction preserves immutable history and report uses latest explanation',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');a.data={type:'late',requestId:'a'.repeat(32),reason:'Bus delayed'};await f.functions.submitWorkNotice(a);
 a.data={...a.data,requestId:'b'.repeat(32),reason:'Updated arrival: 09:30'};await f.functions.submitWorkNotice(a);
 assert.equal(f.data.get('attendanceNotices/a_'+f.today).revision,2);assert.equal(f.data.get('workEvents/'+'a'.repeat(32)).reason,'Bus delayed');assert.equal(f.data.get('attendanceNotices/a_'+f.today).lateMessage,'Updated arrival: 09:30');
});
test('linked admin My Attendance can send late notice; unlinked, anonymous and inactive users cannot',async()=>{
 const f=fixture();f.add('office','+94770000002');Object.assign(f.data.get('employees/office'),{adminUid:'admin',role:'Staff Officer'});f.data.get('admins/admin').employeeId='office';
 const request={auth:{uid:'admin'},data:{type:'late',requestId:'c'.repeat(32),reason:'Traffic'}};await f.functions.submitWorkNotice(request);assert.equal(f.data.get('attendanceNotices/office_'+f.today).employeeId,'office');
 await assert.rejects(f.functions.submitWorkNotice({data:request.data}),/Sign in/);delete f.data.get('admins/admin').employeeId;await assert.rejects(f.functions.submitWorkNotice(request),/Link/);
 const a={auth:{uid:'office',token:{phone_number:'+94770000002'}},data:request.data};f.data.get('employees/office').active=false;await assert.rejects(f.functions.submitWorkNotice(a),/active/);
});
test('outing needs open check-in, color is server-derived and repeated locations retain history without requiring return',async()=>{
 for(const [kind,color] of [['fixed','red'],['no_fixed','green']]){
  const f=fixture(),a=f.add('a','+94770000001');f.data.get('employees/a').workLocationType=kind;a.data={type:'outing',requestId:'d'.repeat(32),reason:'Customer meeting',siteName:'Urban Lux',destination:'Kandana',latitude:6.9,longitude:79.8,accuracyMeters:5,fixTime:Date.now(),mock:false,color:'green'};
  await assert.rejects(f.functions.submitWorkNotice(a),/Check in/);f.data.set('attendance/a_'+f.today,{employeeId:'a',date:f.today,checkIn:'08:00:00',checkOut:''});await f.functions.submitWorkNotice(a);
  assert.equal(f.data.get('workEvents/'+'d'.repeat(32)).color,color);assert.equal(f.data.get('attendanceOutings/a_'+f.today).active,true);for(let i=1;i<=100;i++){a.data.requestId=i.toString(16).padStart(32,'0');a.data.siteName='Site '+i;a.data.destination='Location '+i;await f.functions.submitWorkNotice(a);await f.functions.submitWorkNotice(a);assert.equal(f.data.get('workEvents/'+a.data.requestId).color,color);}assert.equal([...f.data.keys()].filter(k=>k.startsWith('workEvents/')).length,101);assert.equal(f.data.get('workEvents/'+'d'.repeat(32)).siteName,'Urban Lux');assert.equal(f.data.get('workEvents/'+'d'.repeat(32)).locationName,'Kandana');assert.equal(f.data.get('attendanceOutings/a_'+f.today).siteName,'Site 100');
  a.data={type:'return',requestId:'e'.repeat(32),reason:'Returned to work'};await f.functions.submitWorkNotice(a);assert.equal(f.data.get('attendanceOutings/a_'+f.today).active,false);assert.ok(f.data.get('workEvents/'+'d'.repeat(32)));
  f.data.get('attendance/a_'+f.today).checkOut='17:00:00';a.data={type:'late',requestId:'f'.repeat(32),reason:'Late'};await assert.rejects(f.functions.submitWorkNotice(a),/completed/);
 }
});
test('notice validation rejects oversized reasons, invalid IDs, empty destinations and invalid GPS',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');
 for(const patch of [{requestId:'invalid'},{reason:'x'.repeat(501)},{type:'outing',destination:''},{type:'outing',siteName:'Branch',destination:'Town',latitude:1000,longitude:79},{type:'outing',siteName:'',destination:'Town',latitude:6,longitude:79},{type:'outing',siteName:'x'.repeat(121),destination:'Town',latitude:6,longitude:79},{type:'outing',siteName:'Site',destination:'x'.repeat(121),latitude:6,longitude:79},{type:'other'}]){a.data={type:'late',requestId:'a'.repeat(32),reason:'A valid reason',...patch};await assert.rejects(f.functions.submitWorkNotice(a),/Invalid|Enter|valid/);}
});
test('colored outing alerts reach only active admins, own-admin notice included, trigger retry deduplicates',async()=>{
 const f=fixture();f.data.set('admins/inactive',{active:false});const id='a'.repeat(32),v={employeeId:'a',employeeName:'Office member',employeeRole:'Staff Officer',date:f.today,type:'outing',reason:'Meeting',siteName:'Urban Lux',locationName:'Kandana',destination:'Branch',latitude:6.9,longitude:79.8,accuracyMeters:5,fixTime:Date.now(),mock:false,color:'red'};
 const event={params:{eventId:id},data:{after:{exists:true,data:()=>v}}};await f.functions.workEventAlerts(event);await f.functions.workEventAlerts(event);
 const inbox=[...f.data.keys()].filter(k=>k.startsWith('notifications/'));assert.equal(inbox.length,1);assert.ok(inbox[0].startsWith('notifications/admin/'));assert.equal(f.data.get(inbox[0]).color,'red');assert.match(f.data.get(inbox[0]).body,/Urban Lux/);assert.match(f.data.get(inbox[0]).body,/Kandana/);
});
test('storage budget is admin-only, validates values and does not delete attendance',async()=>{
 const f=fixture(),a=f.add('a','+94770000001');await assert.rejects(f.functions.saveStorageBudget({...a,data:{budgetMiB:1,warnPercent:80}}),/Admin access/);await assert.rejects(f.functions.getStorageHealth(a),/Admin access/);
 for(const data of [{budgetMiB:0,warnPercent:80},{budgetMiB:1,warnPercent:99},{budgetMiB:NaN,warnPercent:80}])await assert.rejects(f.functions.saveStorageBudget({auth:{uid:'admin'},data}),/budget/);
 await f.functions.saveStorageBudget({auth:{uid:'admin'},data:{budgetMiB:1,warnPercent:80}});assert.equal(f.data.get('settings/storageBudget').budgetBytes,1048576);assert.equal([...f.data.keys()].filter(k=>k.startsWith('attendance/')).length,10);
});
test('storage scan warns against configured budget, deduplicates daily alert and changed budgets bypass cache',async()=>{
 const f=fixture(),request={auth:{uid:'admin'},data:{budgetMiB:1,warnPercent:80}};await f.functions.saveStorageBudget(request);f.data.set('attendance/large-record',{employeeId:'a',date:f.today,payload:'x'.repeat(900000)});
 let result=await f.functions.checkStorageHealth(request);assert.equal(result.state,'warning');assert.equal(result.complete,true);assert.ok(result.estimatedBytes>900000);assert.equal(f.data.has('attendance/large-record'),true);
 const id='storage-'+f.today,alert=f.data.get('systemAlerts/'+id);assert.match(alert.body,/not a cloud free-space/);assert.match(alert.body,/private backup/);
 const event={params:{alertId:id},data:{after:{exists:true,data:()=>alert}}};await f.functions.systemStorageAlerts(event);await f.functions.systemStorageAlerts(event);assert.equal([...f.data.keys()].filter(k=>k.startsWith('notifications/admin/')).length,1);
 await f.functions.saveStorageBudget({auth:{uid:'admin'},data:{budgetMiB:2,warnPercent:80}});result=await f.functions.checkStorageHealth(request);assert.equal(result.state,'ok');assert.equal(result.budgetMiB,2);
});
test('unconfigured storage is unknown, incomplete samples cannot announce sufficient space',async()=>{
 const f=fixture();const value=await f.functions.checkStorageHealth({auth:{uid:'admin'}});assert.equal(value.state,'unconfigured');assert.equal(value.complete,false);
 const p=require('../work-policy');assert.equal(p.capacity(1000000,{budgetBytes:1048576,warnPercent:80},false),false);assert.equal(p.capacity(1000000,{budgetBytes:1048576,warnPercent:80},true),true);
});
test('saved repeated location additions notify active admin with site/location and correct color across departments',async()=>{
 for(const role of ['Sales Officer','Staff Officer','Hotel Staff'])for(const [kind,color] of [['fixed','red'],['no_fixed','green']]){
  const f=fixture(),a=f.add('visitor','+94770000009');Object.assign(f.data.get('employees/visitor'),{role,workLocationType:kind});
  f.data.set('attendance/visitor_'+f.today,{employeeId:'visitor',date:f.today,checkIn:'08:00:00',checkOut:''});
  for(let n=1;n<=3;n++){
   const id=n.toString(16).padStart(32,'0');a.data={type:'outing',requestId:id,reason:'Customer visit',siteName:'Urban Lux '+n,destination:'Kandana '+n,latitude:6.9,longitude:79.8,accuracyMeters:5,fixTime:Date.now(),mock:false,color:color==='red'?'green':'red'};
   await f.functions.submitWorkNotice(a);await f.functions.submitWorkNotice(a);
   const stored=f.data.get('workEvents/'+id);const event={params:{eventId:id},data:{after:{exists:true,data:()=>stored}}};await f.functions.workEventAlerts(event);await f.functions.workEventAlerts(event);
   const alerts=[...f.data.entries()].filter(([key])=>key.startsWith('notifications/admin/items/'));
   assert.equal(alerts.length,n);const alert=alerts.find(([,v])=>v.body.includes('Urban Lux '+n))[1];assert.equal(alert.color,color);assert.match(alert.body,new RegExp('Kandana '+n));assert.equal(alert.type,'work_outing');
  }
  assert.equal([...f.data.keys()].filter(k=>k.startsWith('workEvents/')).length,3);
  f.data.get('attendance/visitor_'+f.today).checkOut='17:00:00';a.data.requestId='f'.repeat(32);await assert.rejects(f.functions.submitWorkNotice(a),/completed/);assert.equal([...f.data.keys()].filter(k=>k.startsWith('workEvents/')).length,3);
 }
});

function secureEmployee(f,kind='no_fixed',role='Staff Officer'){
 const r=f.add('secure','+94770000088',0);Object.assign(f.data.get('employees/secure'),{workLocationType:kind,role,locationId:kind==='fixed'?'site':''});f.data.set('locations/site',{name:'Official site',latitude:6.9,longitude:79.8,allowedRadius:100});
 r.data={action:'in',expectedDate:f.today,requestId:'1'.repeat(32),siteName:'Urban Lux',locationName:'Kandana',latitude:6.9,longitude:79.8,accuracyMeters:5,fixTime:Date.now(),mock:false};return r;
}
test('secure attendance: all departments and both site types preserve reports, tracking and checkout with server identity/time',async()=>{
 for(const role of ['Sales Officer','Staff Officer','Hotel Officer'])for(const kind of ['fixed','no_fixed']){
  const f=fixture(),r=secureEmployee(f,kind,role),result=await f.functions.recordAttendance(r),a=f.data.get('attendance/secure_'+f.today);
  assert.equal(a.employeeName,'secure');assert.equal(a.employeeRole,role);assert.equal(a.checkIn,result.checkIn);assert.equal(a.date,f.today);assert.equal(a.createdAt.toDate() instanceof Date,true);assert.equal(a.verification,'app_check');assert.equal(a.checkInSiteName,'Urban Lux');assert.equal(a.locationName,kind==='fixed'?'Official site':'No Fixed Location');
  r.data={...r.data,action:'tracking',requestId:'2'.repeat(32)};await f.functions.recordAttendance(r);assert.equal(f.data.get('latestLocations/secure').trackingStatus,'Working');
  r.data={...r.data,action:'out',requestId:'3'.repeat(32),siteName:'Branch',locationName:'Colombo'};await f.functions.recordAttendance(r);assert.equal(f.data.get('attendance/secure_'+f.today).checkOutLocationName,'Colombo');assert.equal(f.data.get('latestLocations/secure').trackingStatus,'Completed');assert.equal([...f.data.keys()].filter(k=>k.startsWith('locationLogs/')).length,3);
 }
});
test('secure attendance rejects missing, reused and unapproved app verification; production cannot use emulator flag alone',async()=>{
 for(const app of [null,{appId:'approved-test-app',alreadyConsumed:true},{appId:'other-app'}]){const f=fixture(),r=secureEmployee(f);await assert.rejects(f.functions.recordAttendance({...r,app}),/verification|approved/);assert.equal(f.data.has('attendance/secure_'+f.today),false);}
 const f=fixture(),r=secureEmployee(f);f.data.delete('settings/attendanceSecurity');await assert.rejects(f.functions.recordAttendance(r),/approved/);
 const policy=require('../attendance-policy'),oldE=process.env.FUNCTIONS_EMULATOR,oldP=process.env.GCLOUD_PROJECT;
 try{process.env.FUNCTIONS_EMULATOR='true';process.env.GCLOUD_PROJECT='production-project';assert.equal(policy.localDemo(),false);process.env.GCLOUD_PROJECT='demo-test';assert.equal(policy.localDemo(),true);}finally{if(oldE===undefined)delete process.env.FUNCTIONS_EMULATOR;else process.env.FUNCTIONS_EMULATOR=oldE;if(oldP===undefined)delete process.env.GCLOUD_PROJECT;else process.env.GCLOUD_PROJECT=oldP;}
 assert.equal(f.functions.recordAttendance.options.enforceAppCheck,true);assert.equal(f.functions.recordAttendance.options.consumeAppCheckToken,true);assert.equal(f.functions.submitWorkNotice.options.enforceAppCheck,true);assert.equal(f.functions.submitWorkNotice.options.consumeAppCheckToken,true);
});
test('secure attendance rejects fake/stale/future/imprecise/invalid GPS and forged attendance fields before any save',async()=>{
 for(const patch of [{mock:true},{mock:undefined},{fixTime:Date.now()-70000},{fixTime:Date.now()+30000},{accuracyMeters:51},{accuracyMeters:0},{latitude:91},{longitude:NaN},{employeeId:'victim'},{checkIn:'08:00:00'},{employeeRole:'Admin'},{locationId:'fake'},{expectedDate:'2000-01-01'},{siteName:' '},{requestId:'bad'}]){
  const f=fixture(),r=secureEmployee(f);await assert.rejects(f.functions.recordAttendance({...r,data:{...r.data,...patch}}));assert.equal(f.data.has('attendance/secure_'+f.today),false);assert.equal([...f.data.keys()].some(k=>k.startsWith('locationLogs/')),false);
 }
});
test('fixed check-in recomputes assigned radius including GPS uncertainty and fails closed for missing site',async()=>{
 for(const change of ['outside','uncertain','missing','missingId','invalid']){const f=fixture(),r=secureEmployee(f,'fixed');if(change==='outside')r.data.latitude=7.0;if(change==='uncertain')f.data.get('locations/site').allowedRadius=4;if(change==='missing')f.data.delete('locations/site');if(change==='missingId')f.data.get('employees/secure').locationId='';if(change==='invalid')f.data.get('locations/site').allowedRadius=-1;await assert.rejects(f.functions.recordAttendance(r),/site|GPS/);}
});
test('server receipt deduplicates retries, rejects altered ID reuse, concurrent check-in creates one record',async()=>{
 const f=fixture(),r=secureEmployee(f);const values=await Promise.all([f.functions.recordAttendance(r),f.functions.recordAttendance(r)]);assert.deepEqual(values[0],values[1]);assert.equal([...f.data.keys()].filter(k=>k.startsWith('locationLogs/')).length,1);
 await assert.rejects(f.functions.recordAttendance({...r,data:{...r.data,siteName:'changed'}}),/another attendance/);
 const g=fixture(),s=secureEmployee(g);const all=await Promise.allSettled([g.functions.recordAttendance(s),g.functions.recordAttendance({...s,data:{...s.data,requestId:'a'.repeat(32)}})]);assert.equal(all.filter(x=>x.status==='fulfilled').length,1);
});
test('secure checkout/tracking require an open day and cannot reopen or overwrite completed attendance',async()=>{
 const f=fixture(),r=secureEmployee(f);await assert.rejects(f.functions.recordAttendance({...r,data:{...r.data,action:'out'}}),/open check-in/);await assert.rejects(f.functions.recordAttendance({...r,data:{...r.data,action:'tracking'}}),/open check-in/);
 await f.functions.recordAttendance(r);const out={...r,data:{...r.data,action:'out',requestId:'2'.repeat(32)}};await f.functions.recordAttendance(out);await f.functions.recordAttendance(out);
 for(const action of ['in','out','tracking'])await assert.rejects(f.functions.recordAttendance({...r,data:{...r.data,action,requestId:'3'.repeat(32)}}),/open check-in|already checked in/);
});
test('secure attendance enforces revocation, GPS block, own admin link and approved leave before check-in',async()=>{
 for(const scenario of ['inactive','blocked','personalLeave','salesLeave','unlinkedAdmin']){const f=fixture(),r=secureEmployee(f);if(scenario==='inactive')f.data.get('employees/secure').active=false;if(scenario==='blocked')f.data.get('employees/secure').attendanceBlocked=true;if(scenario==='personalLeave')f.data.set('leaveEmployeeDates/secure_'+f.today,{status:'approved'});if(scenario==='salesLeave')f.data.set('leaveDateLocks/'+f.today,{status:'approved',employeeId:'secure'});if(scenario==='unlinkedAdmin')r.auth={uid:'admin'};await assert.rejects(f.functions.recordAttendance(r));}
 const f=fixture(),r=secureEmployee(f);f.data.get('admins/admin').employeeId='secure';f.data.get('employees/secure').adminUid='admin';await f.functions.recordAttendance({...r,auth:{uid:'admin'}});assert.equal(f.data.get('attendance/secure_'+f.today).actorUid,'admin');
});
test('location notice requires fresh accurate GPS and verified app, preserves idempotent retry after GPS ages',async()=>{
 const f=fixture(),r=secureEmployee(f);await f.functions.recordAttendance(r);r.data={type:'outing',requestId:'a'.repeat(32),reason:'Visit',siteName:'Branch',destination:'Town',latitude:6.9,longitude:79.8,accuracyMeters:5,fixTime:Date.now(),mock:false};
 for(const patch of [{mock:true},{accuracyMeters:500},{fixTime:Date.now()-90000}])await assert.rejects(f.functions.submitWorkNotice({...r,data:{...r.data,...patch}}));
 await assert.rejects(f.functions.submitWorkNotice({...r,app:null}),/verification/);await f.functions.submitWorkNotice(r);await f.functions.submitWorkNotice(r);assert.equal(f.data.get('workEvents/'+r.data.requestId).accuracyMeters,5);
});
