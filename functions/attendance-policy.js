'use strict';
const text=(v,label)=>{if(typeof v!=='string'||!v.trim()||v.trim().length>120)throw new Error('Enter '+label+' (1-120 characters).');return v.trim();};
function gps(d,now=Date.now()){
 if(d.mock!==false)throw new Error('Real GPS is required. Mock or unverified readings are blocked.');
 if(![d.latitude,d.longitude,d.accuracyMeters,d.fixTime].every(Number.isFinite)||Math.abs(d.latitude)>90||Math.abs(d.longitude)>180||d.accuracyMeters<=0||d.accuracyMeters>50)throw new Error('Accurate GPS is required (50 metres or better). Enable precise location and retry.');
 if(d.fixTime>now+10000||now-d.fixTime>60000)throw new Error('GPS reading is stale. Get a new location and retry.');
 return {latitude:d.latitude,longitude:d.longitude,accuracyMeters:d.accuracyMeters,fixTime:d.fixTime,mock:false};
}
function distance(a,b){const r=v=>v*Math.PI/180,x=r(b.latitude-a.latitude),y=r(b.longitude-a.longitude),q=Math.sin(x/2)**2+Math.cos(r(a.latitude))*Math.cos(r(b.latitude))*Math.sin(y/2)**2;return 6371000*2*Math.atan2(Math.sqrt(q),Math.sqrt(Math.max(0,1-q)));}
function site(employee,raw,fix,insideRequired){
 if(employee.workLocationType==='no_fixed')return {locationId:'',locationName:'No Fixed Location',distance:0,insideSite:'not_applicable',siteCheckStatus:'not_applicable'};
 if(employee.workLocationType!=='fixed'||!employee.locationId||!raw||![raw.latitude,raw.longitude,raw.allowedRadius].every(Number.isFinite)||Math.abs(raw.latitude)>90||Math.abs(raw.longitude)>180||raw.allowedRadius<=0)throw new Error('Ask admin to configure a valid assigned work site.');
 const metres=distance(fix,raw),certainInside=metres+fix.accuracyMeters<=raw.allowedRadius;
 if(insideRequired&&!certainInside)throw new Error('GPS does not confirm you are inside the assigned site. Move inside, get a precise fix, or contact admin.');
 return {locationId:employee.locationId,locationName:raw.name||'',distance:metres,insideSite:certainInside?true:metres-fix.accuracyMeters>raw.allowedRadius?false:'not_applicable',siteCheckStatus:'checked'};
}
function input(d){
 if(!d||!['in','out','tracking'].includes(d.action)||!/^\d{4}-\d{2}-\d{2}$/.test(d.expectedDate)||!/^[a-f0-9]{32}$/.test(d.requestId))throw new Error('Invalid attendance request. Reopen attendance and retry.');
 const allowed=['action','expectedDate','requestId','latitude','longitude','accuracyMeters','fixTime','mock','siteName','locationName'];
 if(Object.keys(d).some(k=>!allowed.includes(k)))throw new Error('Unexpected attendance fields. Use the official app.');
 return {...d,siteName:d.action==='tracking'?'':text(d.siteName,'site name'),locationName:d.action==='tracking'?'':text(d.locationName,'location')};
}
const localDemo=()=>process.env.FUNCTIONS_EMULATOR==='true'&&/^demo-/.test(process.env.GCLOUD_PROJECT||process.env.GOOGLE_CLOUD_PROJECT||'');
function trusted(request,fail){if(!localDemo()&&(!request.app?.appId||request.app.alreadyConsumed===true))fail('App verification failed. Use the official app on a verified phone.','unauthenticated');}
async function approved(request,db,fail){trusted(request,fail);if(!localDemo()){const cfg=await db.doc('settings/attendanceSecurity').get();if(!Array.isArray(cfg.get('allowedAppIds'))||!cfg.get('allowedAppIds').includes(request.app.appId))fail('This app is not approved for attendance. Contact the project owner.','permission-denied');}}
module.exports={gps,site,distance,input,localDemo,trusted,approved};
