const {test}=require('node:test');const assert=require('node:assert/strict');const p=require('../leave-policy');
const days=n=>Array.from({length:n},(_,i)=>({date:`2026-09-${String(i+1).padStart(2,'0')}`,checkIn:'08:00:00',checkOut:'17:00:00',workStatus:'Completed'}));
test('leave is earned only at ten distinct completed work days',()=>{assert.equal(p.balances(days(9),[],'2026-10-06').earned,0);assert.equal(p.balances(days(10),[],'2026-10-06').earned,1);assert.equal(p.balances([...days(10),...days(10)],[],'2026-10-06').earned,1);assert.equal(p.balances(days(20),[],'2026-10-06').earned,2);});
test('pending reserves balance; rejection releases it; approval consumes it',()=>{assert.equal(p.balances(days(10),[{status:'pending'}]).remaining,0);assert.equal(p.balances(days(10),[{status:'rejected'}]).remaining,1);assert.equal(p.balances(days(10),[{status:'approved'}]).remaining,0);});
test('missing checkout and future/invalid attendance do not earn leave',()=>{let rows=days(10);rows[0].checkOut='';rows.push({date:'2026-99-99',checkIn:'a',checkOut:'b',workStatus:'Completed'});assert.equal(p.balances(rows,[],'2026-10-06').earned,0);assert.equal(p.balances(days(10),[],'2026-09-09').earned,0);});
test('holiday calendar fails closed outside confirmed coverage',()=>{assert.match(p.blockedReason('2026-10-07',{},null,'2026-10-06'),/not confirmed/);assert.match(p.blockedReason('2026-10-07',{holidayCoverageStart:'2026-10-08',holidayCoverageEnd:'2026-12-31'},null,'2026-10-06'),/not confirmed/);});
test('all registered public/Poya/company holidays and confirmed weekly holidays block leave',()=>{const c={holidayCoverageStart:'2026-10-01',holidayCoverageEnd:'2026-12-31',blockedWeekdays:[0]};assert.match(p.blockedReason('2026-10-07',c,{name:'Poya'},'2026-10-06'),/Poya/);assert.match(p.blockedReason('2026-10-11',c,null,'2026-10-06'),/holiday/);assert.equal(p.blockedReason('2026-10-07',c,null,'2026-10-06'),'');});
test('invalid and past leave dates are rejected; role aliases resolve departments',()=>{assert.equal(p.validDate('2026-02-30'),false);assert.match(p.blockedReason('2026-10-05',{},null,'2026-10-06'),/Past/);assert.equal(p.department('Sales Officer'),'Sales');assert.equal(p.department('Staff Officer'),'Office');assert.equal(p.department('Hotel Staff'),'Hotel');});

test('Sri Lankan phone normalization validates formatting and rejects invalid digits',()=>{
 for(const phone of ['0770000001','+94770000001','94770000001','770000001','077 000 0001','(077)-000-0001'])assert.equal(p.normalizePhone(phone),'+94770000001');
 for(const phone of ['','+44770000001','077abc0001','123','+947700000011'])assert.equal(p.normalizePhone(phone),'');
});
test('today and future leave dates are allowed within a confirmed non-holiday calendar',()=>{
 const c={holidayCoverageStart:'2026-10-01',holidayCoverageEnd:'2027-01-31',blockedWeekdays:[]};
 assert.equal(p.blockedReason('2026-10-07',c,null,'2026-10-07'),'');
 assert.equal(p.blockedReason('2027-01-20',c,null,'2026-10-07'),'');
 assert.match(p.blockedReason('2026-10-06',c,null,'2026-10-07'),/Past/);
 assert.match(p.blockedReason('2027-02-01',c,null,'2026-10-07'),/not confirmed/);
});
test('annual reservations convert to used on approval and return on rejection without double deduction',()=>{
 const e={role:'Staff Officer',annualLeaveYear:2026,annualLeaveAllowance:20,annualLeaveUsedBeforeApp:3};
 const leave=status=>[{date:'2026-10-08',status}];
 assert.equal(p.allowance(e,leave('pending'),'2026-10-07').remaining,16);
 assert.equal(p.allowance(e,leave('approved'),'2026-10-07').remaining,16);
 assert.equal(p.allowance(e,leave('rejected'),'2026-10-07').remaining,17);
 assert.equal(p.allowance(e,[{date:'2027-01-01',status:'approved'}],'2026-10-07').remaining,17);
});
test('invalid annual quota data and a new year stay unconfigured rather than inventing balance',()=>{
 const e={role:'Hotel Officer',annualLeaveYear:2026,annualLeaveAllowance:20,annualLeaveUsedBeforeApp:3};
 for(const patch of [{annualLeaveAllowance:-1},{annualLeaveAllowance:367},{annualLeaveUsedBeforeApp:-1},{annualLeaveUsedBeforeApp:21},{annualLeaveYear:2025}])assert.equal(p.allowance({...e,...patch},[],'2026-10-07').configured,false);
 assert.equal(p.allowance(e,[],'2027-01-20').configured,false);
});
test('global and department holiday restrictions apply only to Sales, dates still validated for everyone',()=>{
 const today='2026-10-07',config={holidayCoverageStart:'2026-10-01',holidayCoverageEnd:'2026-12-31',blockedWeekdays:[]};
 assert.match(p.employeeBlockedReason('Sales Officer',today,config,{name:'Poya',blocked:true},null,today),/holiday/);
 assert.match(p.employeeBlockedReason('Sales Officer',today,config,null,{kind:'holiday'},today),/holiday/);
 assert.match(p.employeeBlockedReason('Sales Officer',today,{},null,null,today),/not confirmed/);
 for(const role of ['Staff Officer','Hotel Officer']){
  assert.equal(p.employeeBlockedReason(role,today,{}, {name:'Poya',blocked:true},{kind:'holiday'},today),'');
  assert.match(p.employeeBlockedReason(role,'2026-10-06',config,null,null,today),/Past/);
  assert.match(p.employeeBlockedReason(role,'2026-02-30',config,null,null,today),/valid/);
 }
});
