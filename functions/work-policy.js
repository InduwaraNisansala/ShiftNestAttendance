'use strict';
function text(value,name,max){const s=typeof value==='string'?value.trim():'';if(!s||s.length>max)throw new Error(`Enter ${name} (1-${max} characters).`);return s;}
function eventInput(data){
 const type=data?.type;if(!['late','outing','return'].includes(type))throw new Error('Invalid attendance message type.');
 const id=text(data.requestId,'request ID',64);if(!/^[a-f0-9]{32}$/.test(id))throw new Error('Invalid request ID.');
 const reason=text(data.reason,'a reason',500);
 const siteName=type==='outing'?text(data.siteName,'site name',120):'';
 const destination=type==='outing'?text(data.destination,'destination',120):'';
 const latitude=type==='outing'?data.latitude:null,longitude=type==='outing'?data.longitude:null;
 if(type==='outing'&&(!Number.isFinite(latitude)||!Number.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180))throw new Error('A valid current location is required.');
 const quality=type==='outing'?require('./attendance-policy').gps(data,data.fixTime):{};
 return {type,id,reason,siteName,destination,latitude,longitude,...quality};
}
function outingColor(employee){if(employee.workLocationType==='fixed')return 'red';if(employee.workLocationType==='no_fixed')return 'green';throw new Error('Ask admin to configure your work location type.');}
function storageBudget(raw){
 const mib=raw?.budgetMiB,warn=raw?.warnPercent;
 if(!Number.isSafeInteger(mib)||mib<1||mib>1000000000||!Number.isInteger(warn)||warn<50||warn>95)throw new Error('Enter a budget in MiB (1-1000000000) and warning percent (50-95).');
 return {budgetMiB:mib,warnPercent:warn,budgetBytes:mib*1048576};
}
function documentBytes(path,data){return Buffer.byteLength(path,'utf8')+Buffer.byteLength(JSON.stringify(data),'utf8')+32;}
function capacity(bytes,budget,complete){return complete&&!!budget&&bytes>=budget.budgetBytes*budget.warnPercent/100;}
module.exports={eventInput,outingColor,storageBudget,documentBytes,capacity};
