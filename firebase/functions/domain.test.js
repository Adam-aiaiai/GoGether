'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const D=require('./domain');
const {visibleReview,createService}=require('./service');
const now=1800000000000;
const draft={title:'Friendly badminton',category:'SPORTS',description:'Everyone is welcome to our friendly game.',startAt:now+3600000,endAt:now+7200000,location:'Sports centre',city:'Suzhou',capacity:2,registrationDeadline:now+1800000};
test('event draft applies canonical defaults and preserves normal limits',()=>{const d=D.draft(draft,now,'alice');assert.equal(d.capacity,2);assert.equal(d.imageUri,'');assert.equal(d.meetingAt,null);assert.equal(d.beginnerGuidance,false);assert.equal(d.experienceRequirement,'NONE');});
test('event draft rejects bad dates capacity and unknown participation enums',()=>{for(const update of [{capacity:51},{capacity:1},{startAt:now},{endAt:draft.startAt},{endAt:draft.startAt+86400001},{registrationDeadline:draft.startAt+1},{category:'ADMIN'},{beginnerGuidance:'true'},{experienceRequirement:'ANY'}])assert.throws(()=>D.draft({...draft,...update},now,'alice'));});
test('editing may retain closed deadline but new event cannot',()=>{assert.throws(()=>D.draft({...draft,registrationDeadline:now-1},now,'alice'));assert.equal(D.draft({...draft,registrationDeadline:now-1},now,'alice',true).registrationDeadline,now-1);});
test('cover references are owner uploads only',()=>{assert.throws(()=>D.draft({...draft,imageUri:'content://local/image'},now,'alice'));assert.throws(()=>D.draft({...draft,imageUri:'https://example.com/image'},now,'alice'));assert.throws(()=>D.draft({...draft,imageUri:'covers/bob/12345678-1234-1234-1234-123456789012'},now,'alice'));assert.equal(D.draft({...draft,imageUri:'covers/alice/12345678-1234-1234-1234-123456789012'},now,'alice').imageUri,'covers/alice/12345678-1234-1234-1234-123456789012');});
test('profile identity and email come from trusted inputs; interests validated',()=>{const p=D.profile({name:' Alice ',interests:['SPORTS','SPORTS'],id:'bob',email:'forged'},'alice','real@example.test',now);assert.equal(p.id,'alice');assert.equal(p.email,'real@example.test');assert.deepEqual(p.interests,['SPORTS']);assert.throws(()=>D.profile({name:'A\nB',interests:[]},'a',null,now));assert.throws(()=>D.profile({name:'Alice',interests:['ADMIN']},'a',null,now));});
test('stable operation hash is key-order independent and payload-sensitive',()=>{assert.equal(D.hash({a:1,b:2}),D.hash({b:2,a:1}));assert.notEqual(D.hash({a:1}),D.hash({a:2}));assert.notEqual(D.operationId('a_b','c'),D.operationId('a','b_c'));});
test('attendance belongs to the current confirmed registration cycle',()=>{
 const e={status:'OPEN'},r={id:'event_bob',queueOrder:2,joinedAt:100,status:'CONFIRMED'};
 assert.equal(D.registrationKey(r),'event_bob:2');
 assert.equal(D.validCheckIn({registrationKey:'event_bob:2',checkedInAt:100},r,e),true);
 assert.equal(D.validCheckIn({registrationKey:'event_bob:1',checkedInAt:100},r,e),false);
 assert.equal(D.validCheckIn({checkedInAt:99},r,e),false);
 assert.equal(D.validCheckIn({checkedInAt:100},r,e),true);
 assert.equal(D.validCheckIn({registrationKey:'event_bob:2'}, {...r,status:'CANCELLED'},e),false);
 assert.equal(D.validCheckIn({registrationKey:'event_bob:2'},r,{status:'CANCELLED'}),false);
});
test('server check-in window uses activity time and closes at end',()=>{const e={status:'OPEN',startAt:now,endAt:now+600000};D.windowCheck(e,now-1800000);D.windowCheck(e,now+599999);assert.throws(()=>D.windowCheck(e,now-1800001));assert.throws(()=>D.windowCheck(e,e.endAt));assert.throws(()=>D.windowCheck({...e,status:'CANCELLED'},now));});
test('meeting binding invalidates for schedule and address edits but not announcements',()=>{const e={...draft,meetingAt:null,meetingLocation:null};assert.equal(D.bindingKey(e),D.bindingKey({...e,updatedAt:123,revision:99}));assert.notEqual(D.bindingKey(e),D.bindingKey({...e,startAt:e.startAt+1}));assert.notEqual(D.bindingKey(e),D.bindingKey({...e,location:'Other gym'}));});
test('feedback details enforce tag score visibility and photo ownership',()=>{assert.equal(D.feedbackDetails({},'a').visibility,'PUBLIC');for(const d of [{organization:6},{anonymous:'true'},{visibility:'EVERYONE'},{tags:['fake']},{tags:['good_pace','good_pace']},{photos:['reviews/bob/12345678-1234-1234-1234-123456789012']}])assert.throws(()=>D.feedbackDetails(d,'alice'));});
test('anonymous review projection hides identity including document identifier',()=>{const f={id:'event_secret-uid',publicId:'opaque-review',userId:'secret-uid',rating:4,comment:'Good',createdAt:now,details:{anonymous:true,visibility:'PUBLIC',photos:['opaque-media']}};const out=visibleReview(f,'viewer','organizer',[{id:'secret-uid',displayName:'Private Name'}]);assert.equal(out.id,'opaque-review');assert.equal(out.authorName,'Anonymous participant 01');assert.equal(out.own,false);assert.equal(JSON.stringify(out).includes('secret-uid'),false);assert.equal(JSON.stringify(out).includes('Private Name'),false);});
test('organizer-only review is never projected to a third-party account',()=>{const f={id:'private',publicId:'public',userId:'author',details:{anonymous:false,visibility:'ORGANIZER_ONLY'}};assert.equal(visibleReview(f,'stranger','host',[]),null);assert.ok(visibleReview(f,'author','host',[]));assert.ok(visibleReview(f,'host','host',[]));});
test('ordinary edits describe changed values and unchanged save creates no notice',()=>{assert.deepEqual(D.eventChanges(draft,{...draft,updatedAt:now+1}),[]);const changes=D.eventChanges(draft,{...draft,startAt:draft.startAt+1000,location:'East gate'});assert.equal(changes.length,2);assert.ok(changes.some(x=>x.includes('East gate')));assert.ok(changes.some(x=>x.includes('Starts:')));});
test('callable rejects absent auth forged identity and unknown actions before database access',async()=>{const call=createService({},null);await assert.rejects(call({data:{action:'snapshot'}}),{code:'unauthenticated'});await assert.rejects(call({auth:{uid:'a'},data:{action:'snapshot',userId:'b'}}),{code:'invalid-argument'});await assert.rejects(call({auth:{uid:'a'},data:{action:'grantAdmin'}}),{code:'invalid-argument'});});

test('committed request replays after event starts without touching storage or revalidating draft',async()=>{
 const body={action:'createEvent',draft:{...draft,startAt:2000,endAt:5000,registrationDeadline:1500,imageUri:'covers/alice/12345678-1234-1234-1234-123456789012'}};
 const saved={event:{id:'already-committed'}};
 let storageCalls=0,transactionCalls=0;
 const db={collection:()=>({doc:()=>({get:async()=>({exists:true,data:()=>({hash:D.hash(body),result:saved})})})}),runTransaction:()=>{transactionCalls++;throw Error('must not rerun');}};
 const bucket={file:()=>{storageCalls++;throw Error('storage is unavailable');}};
 assert.deepEqual(await createService(db,bucket,()=>3000)({auth:{uid:'alice'},data:{...body,requestId:'same-request-id'}}),saved);
 assert.equal(storageCalls,0);assert.equal(transactionCalls,0);
});
test('replay still rejects same request ID with changed content before accessing media',async()=>{
 const db={collection:()=>({doc:()=>({get:async()=>({exists:true,data:()=>({hash:'different',result:{ok:true}})})})})};
 await assert.rejects(createService(db,null)({auth:{uid:'alice'},data:{action:'createEvent',requestId:'same-request-id',draft}}),{code:'already-exists'});
});
test('concurrent duplicate recovers committed result after transaction contention',async()=>{
 const {runIdempotentTransaction}=require('./service');let calls=0;
 const result=await runIdempotentTransaction(async()=>{calls++;throw Object.assign(Error('Transaction is invalid or closed.'),{code:3});},async()=>({result:{registration:{id:'one-place'}}}),async()=>{});
 assert.deepEqual(result,{registration:{id:'one-place'}});assert.equal(calls,1);
});
test('transaction retries are bounded and preserve retryable error semantics',async()=>{
 const {runIdempotentTransaction}=require('./service');let calls=0;
 await assert.rejects(runIdempotentTransaction(async()=>{calls++;throw Object.assign(Error('Transaction lock timeout'),{code:3});},async()=>null,async()=>{}),{code:'unavailable'});
 assert.equal(calls,3);
});
test('business rejection is never retried as a database contention error',async()=>{
 const {runIdempotentTransaction}=require('./service');let calls=0;
 await assert.rejects(runIdempotentTransaction(async()=>{calls++;throw new D.DomainError('permission-denied','Organizer only');},async()=>null,async()=>{}),{code:'permission-denied'});
 assert.equal(calls,1);
});


test('committed feedback replay deletes a re-uploaded staging object on every success path',async()=>{
 const path='reviews/alice/12345678-1234-1234-1234-123456789012';
 const body={action:'submitFeedback',eventId:'event-one',rating:4,comment:'Good',details:{photos:[path]}};
 const result={feedback:{id:'saved-review'}};
 for(const mode of ['cached','transaction','contention']) {
  let reads=0,deletes=0,transactions=0;
  const record={exists:true,data:()=>({hash:D.hash(body),result})};
  const ref={get:async()=>{reads++;return mode==='cached'||reads>1?record:{exists:false};}};
  const db={collection:()=>({doc:()=>ref}),runTransaction:async fn=>{
   transactions++;
   if(mode==='contention')throw Object.assign(Error('Transaction is invalid or closed'),{code:3});
   return fn({get:async()=>record});
  }};
  const bucket={file:stored=>({exists:async()=>[true],getMetadata:async()=>[{size:10,contentType:'image/jpeg'}],delete:async options=>{
   assert.equal(stored,path);assert.equal(options.ignoreNotFound,true);deletes++;
  }})};
  assert.deepEqual(await createService(db,bucket)({auth:{uid:'alice'},data:{...body,requestId:'retry-feedback'}}),result);
  assert.equal(transactions,mode==='cached'?0:1);
  assert.equal(deletes,mode==='cached'?1:2);
 }
});

test('feedback replay keeps its committed result when cleanup needs a retry',async()=>{
 const path='reviews/alice/12345678-1234-1234-1234-123456789012';
 const body={action:'submitFeedback',eventId:'event-one',rating:4,details:{photos:[path]}};
 const saved={feedback:{id:'one-review'}};let unavailable=true;
 const db={collection:()=>({doc:()=>({get:async()=>({exists:true,data:()=>({hash:D.hash(body),result:saved})})})})};
 const bucket={file:()=>({delete:async()=>{if(unavailable)throw Error('Storage offline');}})};
 const call=createService(db,bucket),request={auth:{uid:'alice'},data:{...body,requestId:'retry-cleanup'}};
 await assert.rejects(call(request),{code:'unavailable'});
 unavailable=false;assert.deepEqual(await call(request),saved);
});

test('resolveRequest scopes committed results to the authenticated account',async()=>{
 const requestId='join-request';const result={registration:{id:'alice-registration'}};
 const records=new Map([[D.operationId('alice',requestId),{action:'join',result}]]);
 const db={collection:name=>{assert.equal(name,'requests');return {doc:key=>({get:async()=>({data:()=>records.get(key)})})};}};
 const call=createService(db,null);
 assert.deepEqual(await call({auth:{uid:'alice'},data:{action:'resolveRequest',requestIdToResolve:requestId}}),{status:'COMMITTED',action:'join',result});
 assert.deepEqual(await call({auth:{uid:'bob'},data:{action:'resolveRequest',requestIdToResolve:requestId}}),{status:'UNKNOWN'});
 await assert.rejects(call({auth:{uid:'alice'},data:{action:'resolveRequest',requestIdToResolve:'../bad'}}),{code:'invalid-argument'});
});

test('resolveRequest cleans committed review staging before reporting success',async()=>{
 const path='reviews/alice/12345678-1234-1234-1234-123456789012';let deleted=false;
 const result={feedback:{id:'saved-review'}};
 const db={collection:()=>({doc:()=>({get:async()=>({data:()=>({action:'submitFeedback',result,reviewUploads:[path]})})})})};
 const bucket={file:stored=>({delete:async()=>{assert.equal(stored,path);deleted=true;}})};
 const resolved=await createService(db,bucket)({auth:{uid:'alice'},data:{action:'resolveRequest',requestIdToResolve:'saved-review-request'}});
 assert.equal(deleted,true);assert.deepEqual(resolved,{status:'COMMITTED',action:'submitFeedback',result});
});


test('optional review comments accept empty and bounded text and keep existing visibility rules',()=>{
 const defaults=D.feedbackDetails({},'bob');
 assert.equal(defaults.positiveComment,'');assert.equal(defaults.improvementComment,'');
 const detail=D.feedbackDetails({positiveComment:'Friendly teammates.',improvementComment:'More warm-up time.',anonymous:true,visibility:'ORGANIZER_ONLY'},'bob');
 assert.equal(detail.positiveComment,'Friendly teammates.');assert.equal(detail.improvementComment,'More warm-up time.');
 for(const key of ['positiveComment','improvementComment']) {
  assert.equal(D.feedbackDetails({[key]:'x'.repeat(100)},'bob')[key].length,100);
  for(const value of ['x'.repeat(101),42,[],{}])assert.throws(()=>D.feedbackDetails({[key]:value},'bob'));
 }
 const review={id:'event_bob',publicId:'opaque',userId:'bob',details:detail};
 assert.equal(visibleReview(review,'carol','alice',[]),null);
 assert.equal(visibleReview(review,'alice','alice',[]).details.positiveComment,'Friendly teammates.');
 assert.equal(visibleReview(review,'bob','alice',[]).details.improvementComment,'More warm-up time.');
});
test('profile details preserve existing photos for legacy updates and honor gender privacy',()=>{
 const path='profiles/alice/12345678-1234-1234-1234-123456789012';
 const saved=D.profile({name:'Alice',interests:['SPORTS'],avatarUri:path,coverUri:path,gender:'NON_BINARY',showGender:false,signature:' See you outdoors! '},'alice','a@example.test',now);
 assert.equal(saved.signature,'See you outdoors!');
 const renamed=D.profile({name:'Alice Park',interests:['OUTDOOR']},'alice','a@example.test',now,saved);
 assert.equal(renamed.avatarUri,path);assert.equal(renamed.gender,'NON_BINARY');
 assert.equal(D.publicProfile(renamed,'bob').gender,'UNDISCLOSED');assert.equal(D.publicProfile(renamed,'bob').email,null);
 assert.equal(D.publicProfile(renamed,'alice').gender,'NON_BINARY');
 assert.equal(D.publicProfile({...renamed,showGender:true},'bob').gender,'NON_BINARY');
 assert.equal(D.profileDetails({gender:'UNDISCLOSED',showGender:true},'alice').showGender,false);
});
test('profile details reject foreign uploads external URLs invalid gender and oversized signature',()=>{
 for(const input of [{avatarUri:'profiles/bob/12345678-1234-1234-1234-123456789012'},{coverUri:'https://example.test/image.jpg'},
 {avatarUri:'file:///local.jpg'},{gender:'UNKNOWN'},{showGender:'false'},{signature:'a'.repeat(101)}])assert.throws(()=>D.profileDetails(input,'alice'));
});


test('all free-form event fields enforce the shared 100 character limit',()=>{
 for(const key of ['description','notes','requiredItems','spareEquipment','meetingNotes']) {
  assert.equal(D.draft({...draft,[key]:'字'.repeat(100)},now,'alice')[key].length,100);
  assert.throws(()=>D.draft({...draft,[key]:'字'.repeat(101)},now,'alice'));
 }
});
