'use strict';
const D=require('./domain');
const S=require('./social');

// Internal records only. Public results contain totals, never author IDs or private scores.
function summary(event,feedback,registrations,checkIns,deleted,now){
 const empty={ratingSum:0,reviewCount:0};
 if(event.status==='CANCELLED'||!(event.status==='COMPLETED'||now>=event.endAt))return empty;
 const removed=new Set(deleted),seen=new Set();
 const current=feedback.filter(f=>f.eventId===event.id).sort((a,b)=>b.createdAt-a.createdAt||String(a.publicId??a.id).localeCompare(String(b.publicId??b.id)));
 const eligible=current.filter(f=>{
  if(seen.has(f.userId))return false;seen.add(f.userId);
  const r=registrations.find(r=>r.eventId===event.id&&r.userId===f.userId),c=checkIns.find(c=>c.eventId===event.id&&c.userId===f.userId&&D.validCheckIn(c,r,event));
  return f.userId!==event.organizerId&&!S.privateReview(f)&&!removed.has(f.publicId??f.id)&&
   (f.status??'SUBMITTED')==='SUBMITTED'&&Number.isInteger(f.rating)&&f.rating>=1&&f.rating<=5&&
   D.validCheckIn(c,r,event)&&c.checkedInAt>=r.joinedAt&&f.createdAt>=c.checkedInAt&&f.createdAt<=now&&
   (event.status==='COMPLETED'||f.createdAt>=event.endAt)&&(!f.registrationKey||f.registrationKey===D.registrationKey(r));
 });
 return {ratingSum:eligible.reduce((sum,f)=>sum+f.rating,0),reviewCount:eligible.length};
}
module.exports={summary};
