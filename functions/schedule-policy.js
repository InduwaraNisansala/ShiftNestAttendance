'use strict';
const leave=require('./leave-policy');
function validate(data,today=leave.today()){
 const department=data.department,date=String(data.date||''),reason=String(data.reason||'').trim(),kind=data.kind;
 if(!['Sales','Office','Hotel'].includes(department))throw new Error('Select a department.');
 if(!leave.validDate(date)||date<today)throw new Error('Select today or a future date.');
 if(!reason||reason.length>500)throw new Error('Enter a reason (up to 500 characters).');
 if(!['holiday','working'].includes(kind))throw new Error('Select Holiday or Working day.');
 const start=String(data.checkIn||''),end=String(data.checkOut||'');let workingMinutes=0;
 if(kind==='working'){
  if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(start)||!/^([01]\d|2[0-3]):[0-5]\d$/.test(end))throw new Error('Select valid check-in and check-out times.');
  const minutes=t=>Number(t.slice(0,2))*60+Number(t.slice(3));workingMinutes=minutes(end)-minutes(start);
  if(workingMinutes<=0)throw new Error('Check-out must be later than check-in on the same day.');
 }
 return {department,date,reason,kind,checkIn:kind==='working'?start:'',checkOut:kind==='working'?end:'',workingMinutes};
}
function body(s){return `Date: ${s.date}\nDepartment: ${s.department}\n${s.kind==='holiday'?'Holiday — no work scheduled':'Working day'}\nReason: ${s.reason}`+(s.kind==='working'?`\nCheck In: ${s.checkIn}\nCheck Out: ${s.checkOut}\nWorking hours: ${Math.floor(s.workingMinutes/60)} h ${s.workingMinutes%60} min`:'');}
module.exports={validate,body};
