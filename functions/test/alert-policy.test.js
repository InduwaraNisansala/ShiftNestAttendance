const {test}=require('node:test');const assert=require('node:assert/strict');const p=require('../alert-policy');
const now=1000000,employee={active:true,workLocationType:'fixed',locationId:'s'},site={latitude:6.9,longitude:79.9,allowedRadius:100};
const reading=()=>({locationId:'s',trackingStatus:'Working',latitude:6.91,longitude:79.9,timestamp:new Date(now),fixTimestamp:new Date(now),accuracyMeters:5});
test('outside classification uses coordinates rather than a client-supplied false flag',()=>{assert.equal(p.outside(employee,site,{...reading(),latitude:6.9,insideSite:false},now).outside,false);assert.equal(p.outside(employee,site,reading(),now).outside,true);});
test('stale, future, missing, invalid or uncertain GPS does not produce a violation',()=>{
 for(const patch of [{fixTimestamp:new Date(now-300001)},{timestamp:new Date(now-300001)},{fixTimestamp:null},{timestamp:new Date(now+60001)},{latitude:NaN},{longitude:200},{accuracyMeters:2000},{accuracyMeters:null}])assert.equal(p.outside(employee,site,{...reading(),...patch},now),null);
});
test('no-fixed, inactive, changed-site and missing-site readings do not create location alerts',()=>{for(const e of [{...employee,active:false},{...employee,workLocationType:'no_fixed'},{...employee,locationId:'new'}])assert.equal(p.outside(e,site,reading(),now),null);assert.equal(p.outside(employee,null,reading(),now),null);});
test('only new pending leave and a real approved/rejected status transition create leave alerts',()=>{assert.equal(p.leaveEvent(null,{status:'pending'}),'leave_pending');assert.equal(p.leaveEvent({status:'pending'},{status:'rejected'}),'leave_rejected');assert.equal(p.leaveEvent({status:'approved'},{status:'approved'}),null);assert.equal(p.leaveEvent({status:'pending'},null),null);});
