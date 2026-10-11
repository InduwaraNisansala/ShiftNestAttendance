'use strict';
function millis(value){return value && typeof value.toMillis==='function'?value.toMillis():value instanceof Date?value.getTime():Number(value)||0;}
function outside(employee,site,reading,now=Date.now()){
 if(!employee || employee.active!==true || employee.workLocationType!=='fixed' || !employee.locationId || !site || !reading || reading.locationId!==employee.locationId || reading.trackingStatus!=='Working')return null;
 const recorded=millis(reading.timestamp),fix=millis(reading.fixTimestamp);
 if(!recorded || !fix || now-recorded>300000 || now-fix>300000 || recorded>now+60000 || fix>now+60000)return null;
 const vals=[reading.latitude,reading.longitude,site.latitude,site.longitude,site.allowedRadius];
 if(!vals.every(Number.isFinite)||Math.abs(reading.latitude)>90||Math.abs(site.latitude)>90||Math.abs(reading.longitude)>180||Math.abs(site.longitude)>180||site.allowedRadius<=0)return null;
 const rad=x=>x*Math.PI/180,deltaLat=rad(site.latitude-reading.latitude),deltaLon=rad(site.longitude-reading.longitude);
 const a=Math.sin(deltaLat/2)**2+Math.cos(rad(reading.latitude))*Math.cos(rad(site.latitude))*Math.sin(deltaLon/2)**2;
 const distance=6371000*2*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0,1-a)));
 // A known uncertainty overlapping the site boundary is not a confirmed violation.
 if(!Number.isFinite(reading.accuracyMeters)||reading.accuracyMeters<0)return null;
 if(distance>site.allowedRadius && distance-reading.accuracyMeters<=site.allowedRadius)return null;
 return {outside:distance>site.allowedRadius,distance:Math.round(distance),sample:recorded};
}
function leaveEvent(before,after){
 if(!after)return null;
 if(!before&&after.status==='pending')return 'leave_pending';
 if(before&&before.status!==after.status&&['approved','rejected'].includes(after.status))return 'leave_'+after.status;
 return null;
}
module.exports={millis,outside,leaveEvent};
