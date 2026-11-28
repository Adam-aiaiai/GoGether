'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),R=require('./ratings'),D=require('./domain');
const now=10000,event={id:'e',organizerId:'host',status:'OPEN',startAt:4000,endAt:9000};
const regs=['host','a','b','private','deleted','absent'].map(userId=>({id:`r-${userId}`,eventId:'e',userId,status:'CONFIRMED',joinedAt:1000,queueOrder:1}));
const arrivals=regs.filter(r=>r.userId!=='absent').map(r=>({eventId:'e',userId:r.userId,checkedInAt:5000,registrationKey:D.registrationKey(r)}));
const review=(userId,rating=5)=>({publicId:userId,eventId:'e',userId,rating,createdAt:9500,status:'SUBMITTED',details:{visibility:'PUBLIC',anonymous:true}});
const summary=(reviews,deleted=[],e=event,r=regs,c=arrivals)=>R.summary(e,reviews,r,c,deleted,now);
test('aggregate contains only public valid participant reviews, including anonymous ones',()=>{
 assert.deepEqual(summary([review('a',5),review('b',3),review('host',1),{...review('private',1),details:{visibility:'ORGANIZER_ONLY'}},review('deleted',1),review('absent',1)],['deleted']),{ratingSum:8,reviewCount:2});
});
test('empty, cancelled, upcoming and other events cannot contribute ratings',()=>{
 for(const e of [event,{...event,status:'CANCELLED'},{...event,endAt:11000}])assert.deepEqual(summary([],[],e),{ratingSum:0,reviewCount:0});
 assert.equal(summary([review('a')],[],{...event,status:'CANCELLED'}).reviewCount,0);
 assert.equal(summary([review('a')],[],{...event,endAt:11000}).reviewCount,0);
 assert.equal(summary([{...review('a'),eventId:'other'}]).reviewCount,0);
});
test('registration cycle, old timestamps and inactive registrations cannot establish review eligibility',()=>{
 assert.equal(summary([{...review('a'),registrationKey:'old:1'}]).reviewCount,0);
 assert.equal(summary([review('a')],[],event,regs.map(r=>r.userId==='a'?{...r,queueOrder:2}:r)).reviewCount,0);
 for(const status of ['CANCELLED','REJECTED','WAITLISTED'])assert.equal(summary([review('a')],[],event,regs.map(r=>r.userId==='a'?{...r,status}:r)).reviewCount,0);
 const legacy=arrivals.map(c=>({...c,registrationKey:null}));assert.equal(summary([review('a')],[],event,regs,legacy).reviewCount,1);
 assert.equal(summary([{...review('a'),createdAt:4500}],[],event,regs,legacy).reviewCount,0);
});
test('latest current review wins before filtering, so deletion and privacy never resurrect old scores',()=>{
 const old={...review('a',5),publicId:'old',createdAt:9100},current=review('a',2);
 assert.deepEqual(summary([old,current]),{ratingSum:2,reviewCount:1});
 assert.equal(summary([old,current],['a']).reviewCount,0);
 assert.equal(summary([old,{...current,details:{visibility:'ORGANIZER_ONLY'}}]).reviewCount,0);
});
test('drafts, invalid ratings, unknown visibility, premature and future reviews are excluded',()=>{
 for(const change of [{rating:0},{rating:6},{rating:3.5},{status:'DRAFT'},{createdAt:10001},{createdAt:8999},{details:{visibility:'UNKNOWN'}}])assert.equal(summary([{...review('a'),...change}]).reviewCount,0);
});
test('legacy public review and explicit event completion keep verifiable scores',()=>{
 const f=review('a');delete f.details;delete f.status;assert.equal(summary([f]).reviewCount,1);
 assert.equal(summary([review('a')],[],{...event,status:'COMPLETED',endAt:11000}).reviewCount,1);
});
