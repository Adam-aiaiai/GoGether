// End-to-end tests against local Auth + Functions + Firestore + Storage emulators.
// These calls cross the actual authenticated callable HTTP boundary (no .run() mocks).
const {before, after, test} = require('node:test');
const assert = require('node:assert/strict');
const {randomUUID} = require('node:crypto');
const {initializeApp, deleteApp} = require('firebase/app');
const {getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signOut, signInWithEmailAndPassword} = require('firebase/auth');
const {getFunctions, connectFunctionsEmulator, httpsCallable} = require('firebase/functions');
const {getFirestore, connectFirestoreEmulator, doc, getDoc, setDoc, collection, query, where, onSnapshot, terminate} = require('firebase/firestore');
const {getStorage, connectStorageEmulator, ref, uploadBytes, getBytes, getDownloadURL} = require('firebase/storage');
const backendRequire = require('node:module').createRequire(require('node:path').resolve(__dirname,'functions/package.json'));
const adminApp = backendRequire('firebase-admin/app');
const adminFirestore = backendRequire('firebase-admin/firestore');
const projectId = 'demo-gogether';
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
process.env.FIREBASE_STORAGE_EMULATOR_HOST = '127.0.0.1:9199';
const clients = []; let admin, db, alice, bob, carol, finn;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aVUsAAAAASUVORK5CYII=', 'base64');
async function client(name, existing) {
  const app = initializeApp({projectId,apiKey:'fake-emulator-key',appId:'1:123456789:android:local',storageBucket:projectId+'.appspot.com'}, name+'-'+randomUUID());
  const auth=getAuth(app); connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});
  const fs=getFirestore(app); connectFirestoreEmulator(fs,'127.0.0.1',8080);
  const functions=getFunctions(app); connectFunctionsEmulator(functions,'127.0.0.1',5001);
  const storage=getStorage(app); connectStorageEmulator(storage,'127.0.0.1',9199);
  const email=existing?.email || name+'-'+randomUUID()+'@example.test';
  const password='LocalTest123!';
  const user=(existing ? await signInWithEmailAndPassword(auth,email,password) : await createUserWithEmailAndPassword(auth,email,password)).user;
  const c={app,auth,fs,functions,storage,email,password,uid:user.uid};
  c.call=async(action,payload={},requestId=randomUUID())=>{
    const body={...payload};
    if(['submitCheckIn','resolveCheckIn'].includes(action)&&body.registrationKey===undefined){
      const r=(await db.doc('registrations/'+body.eventId+'_'+c.uid).get()).data();
      body.registrationKey=r?require('./functions/domain').registrationKey(r):'missing';
    }
    if(action==='issueCheckInCode'&&body.confirmArrival===undefined)body.confirmArrival=true;
    return (await httpsCallable(functions,'gogether')({action,requestId,...body})).data;
  };
  clients.push(c); await c.call('ensureProfile',{name,interests:['SPORTS']}); return c;
}
function draft(overrides={}) {
  const now=Date.now(); return {title:'Backend integration activity',category:'SPORTS',description:'A shared activity for authenticated backend testing.',
    startAt:now+3600000,endAt:now+7200000,registrationDeadline:now+1800000,location:'East sports centre',city:'Suzhou',capacity:3,
    notes:'Bring water',experienceRequirement:'NONE',beginnerGuidance:true,requiredItems:'Racket',spareEquipment:'Two rackets',imageUri:'',...overrides};
}
async function event(overrides={}) { return (await alice.call('createEvent',{draft:draft(overrides)})).event; }
async function finishAfterCheckIn(e,people=[bob]) {
  await db.doc('events/'+e.id).update({startAt:Date.now()+600000,endAt:Date.now()+3600000});
  const issued=await alice.call('issueCheckInCode',{eventId:e.id});
  for(const person of people)await person.call('submitCheckIn',{eventId:e.id,code:issued.code});
  await db.doc('events/'+e.id).update({startAt:Date.now()-7200000,endAt:Date.now()-1000});
}
async function snap(c) {return (await c.call('snapshot')).snapshot;}
async function expectCode(promise,code) {await assert.rejects(promise,e=>e.code==='functions/'+code || e.code===code);}
before(async()=>{
  assert.match(process.env.FIRESTORE_EMULATOR_HOST,/127\.0\.0\.1/);
  admin=adminApp.initializeApp({projectId,storageBucket:projectId+'.appspot.com'},'integration-admin'); db=adminFirestore.getFirestore(admin);
  alice=await client('Alice'); bob=await client('Bob'); carol=await client('Carol'); finn=await client('Finn');
});
after(async()=>{for(const c of clients){await terminate(c.fs);await signOut(c.auth);await deleteApp(c.app);}await adminApp.deleteApp(admin);});

test('real Auth identity and private profile data are preserved across independent clients',async()=>{
  const other=await client('Alice phone 2',alice);
  await alice.call('updateProfile',{name:'Alice Updated',interests:['STUDY']});
  assert.equal((await snap(other)).currentUserId,alice.uid);
  assert.equal((await snap(other)).users.find(u=>u.id===alice.uid).displayName,'Alice Updated');
  const publicAlice=(await snap(bob)).users.find(u=>u.id===alice.uid);
  assert.ok(!publicAlice.email); assert.equal(publicAlice.status,'ACCOUNT');
});

test('activity create/edit reaches other account and organizer ownership cannot be forged',async()=>{
  const e=await event(); assert.equal(e.organizerId,alice.uid);
  assert.equal((await snap(bob)).events.find(x=>x.id===e.id).title,e.title);
  await expectCode(bob.call('updateEvent',{eventId:e.id,draft:draft({title:'Forged event'}),userId:alice.uid}),'invalid-argument');
  await expectCode(bob.call('updateEvent',{eventId:e.id,draft:draft({title:'Forged event'})}),'permission-denied');
  const changed=(await alice.call('updateEvent',{eventId:e.id,draft:{...e,location:'North entrance'}})).event;
  assert.equal(changed.location,'North entrance');
  await assert.rejects(setDoc(doc(bob.fs,'events',e.id),{organizerId:bob.uid}));
});

test('two concurrent clients cannot both take the last place and cancellation promotes FIFO once',async()=>{
  const e=await event({capacity:2});
  const results=await Promise.all([bob.call('join',{eventId:e.id}),carol.call('join',{eventId:e.id})]);
  assert.deepEqual(results.map(x=>x.registration.status).sort(),['CONFIRMED','WAITLISTED']);
  const winner=results[0].registration.status==='CONFIRMED'?bob:carol;
  const waiting=winner===bob?carol:bob;
  await finn.call('join',{eventId:e.id});
  const cancelId=randomUUID(); await Promise.all([winner.call('cancelRegistration',{eventId:e.id},cancelId),winner.call('cancelRegistration',{eventId:e.id},cancelId)]);
  const s=await snap(waiting);
  assert.equal(s.registrations.find(r=>r.eventId===e.id&&r.userId===waiting.uid).status,'CONFIRMED');
  assert.equal(s.notifications.filter(n=>n.eventId===e.id&&n.type==='PROMOTED').length,1);
  assert.equal((await snap(finn)).registrations.find(r=>r.eventId===e.id&&r.userId===finn.uid).status,'WAITLISTED');
});

test('duplicate join requests occupy one place; request IDs cannot be reused for changed content',async()=>{
  const e=await event(); const requestId=randomUUID();
  await Promise.all([bob.call('join',{eventId:e.id},requestId),bob.call('join',{eventId:e.id},requestId)]);
  assert.equal((await snap(bob)).registrations.filter(r=>r.eventId===e.id&&r.userId===bob.uid).length,1);
  const other=await event(); await expectCode(bob.call('join',{eventId:other.id},requestId),'already-exists');
});

test('announcements deliver once, carry references, and notify through a real Firestore listener',async()=>{
  const e=await event(); await bob.call('join',{eventId:e.id});
  const content='Meet at the red sign '+randomUUID();
  let stop; const observed=new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{stop?.();reject(new Error('Notification listener timed out'));},15000);
    stop=onSnapshot(query(collection(bob.fs,'notifications'),where('userId','==',bob.uid)),s=>{
      if(s.docs.some(d=>d.data().content===content)){clearTimeout(timer);stop();resolve();}
    },e=>{clearTimeout(timer);reject(e);});
  });
  const requestId=randomUUID(); await Promise.all([alice.call('postAnnouncement',{eventId:e.id,content},requestId),alice.call('postAnnouncement',{eventId:e.id,content},requestId)]);
  await observed;
  const s=await snap(bob); const ann=s.announcements.find(a=>a.eventId===e.id);
  const notices=s.notifications.filter(n=>n.eventId===e.id&&n.type==='ANNOUNCEMENT');
  assert.equal(notices.length,1); assert.equal(notices[0].announcementId,ann.id);
  assert.equal((await snap(finn)).announcements.filter(a=>a.eventId===e.id).length,0);
  await expectCode(bob.call('postAnnouncement',{eventId:e.id,content:'Unauthorized'}),'permission-denied');
});

test('prospective participants ask; organizer replies; read state syncs and remains owner scoped',async()=>{
  const e=await event(); await finn.call('askQuestion',{eventId:e.id,content:'Can beginners join?'});
  const q=(await snap(alice)).questions.find(q=>q.eventId===e.id);
  await expectCode(bob.call('answerQuestion',{questionId:q.id,content:'Forged answer'}),'permission-denied');
  await alice.call('answerQuestion',{questionId:q.id,content:'Yes, guidance is available.'});
  const n=(await snap(finn)).notifications.find(n=>n.questionId===q.id&&n.type==='QUESTION_ANSWERED');
  assert.ok(n); await expectCode(bob.call('markRead',{notificationId:n.id}),'permission-denied');
  await assert.rejects(getDoc(doc(bob.fs,'notifications',n.id)));
  await finn.call('markRead',{notificationId:n.id});
  const other=await client('Finn phone 2',finn);
  assert.equal((await snap(other)).notifications.find(x=>x.id===n.id).isRead,true);
});

test('ordinary time/location edits notify each active member once and a no-op edit does not',async()=>{
  const e=await event(); await bob.call('join',{eventId:e.id});
  const changed={...e,startAt:e.startAt+60000,endAt:e.endAt+60000,location:'South gate'};
  await alice.call('updateEvent',{eventId:e.id,draft:changed});
  const s=await snap(bob);const notices=s.notifications.filter(n=>n.eventId===e.id&&n.type==='EVENT_UPDATED');
  assert.equal(notices.length,1);assert.match(notices[0].content,/South gate/);
  await alice.call('updateEvent',{eventId:e.id,draft:changed});
  assert.equal((await snap(bob)).notifications.filter(n=>n.eventId===e.id&&n.type==='EVENT_UPDATED').length,1);
});

test('cancellation stops joins and new questions while preserving historical records',async()=>{
  const e=await event();await bob.call('join',{eventId:e.id});await alice.call('cancelEvent',{eventId:e.id});
  await assert.rejects(carol.call('join',{eventId:e.id}));
  await assert.rejects(finn.call('askQuestion',{eventId:e.id,content:'Too late'}));
  assert.equal((await snap(bob)).registrations.find(r=>r.eventId===e.id&&r.userId===bob.uid).status,'CANCELLED');
  assert.equal((await snap(bob)).notifications.filter(n=>n.eventId===e.id&&n.type==='EVENT_CANCELLED').length,1);
});

test('server checks QR, registration cycle, duplicate attempts and organizer roster',async()=>{
  const e=await event();await bob.call('join',{eventId:e.id});
  // Trusted fixture advances schedule; clients have no write access to server timestamps.
  await db.doc('events/'+e.id).update({startAt:Date.now()+60000,endAt:Date.now()+3600000});

  const issued=await alice.call('issueCheckInCode',{eventId:e.id});assert.ok(issued.code);
  await assert.rejects(finn.call('submitCheckIn',{eventId:e.id,code:issued.code}));
  await assert.rejects(bob.call('submitCheckIn',{eventId:e.id,code:issued.code,registrationKey:'stale:1'}));
  await assert.rejects(bob.call('submitCheckIn',{eventId:e.id,code:'forged'}));
  const attemptId=randomUUID(); const first=await bob.call('submitCheckIn',{eventId:e.id,code:issued.code},attemptId);
  const again=await bob.call('submitCheckIn',{eventId:e.id,code:issued.code},attemptId);
  assert.deepEqual(first.receipt,again.receipt);
  assert.equal((await bob.call('resolveCheckIn',{eventId:e.id,attemptId})).status,'SAVED');
  assert.ok((await alice.call('attendance',{eventId:e.id})).attendees.find(x=>x.userId===bob.uid).checkedInAt);
  await expectCode(finn.call('attendance',{eventId:e.id}),'permission-denied');
  assert.equal((await snap(bob)).checkIns.filter(x=>x.eventId===e.id).length,1);
  assert.equal((await snap(alice)).checkIns.filter(x=>x.eventId===e.id&&x.userId===bob.uid).length,1);
  assert.equal((await snap(finn)).checkIns.filter(x=>x.eventId===e.id).length,0);
});

test('address edits invalidate issued QR; old QR versions and removed binding API are rejected',async()=>{
  const e=await event();await bob.call('join',{eventId:e.id});await db.doc('events/'+e.id).update({startAt:Date.now()+600000});
  const old=await alice.call('issueCheckInCode',{eventId:e.id});
  await alice.call('updateMeeting',{eventId:e.id,meetingAt:Date.now()+300000,location:'New entrance',notes:'Meet here'});
  await assert.rejects(bob.call('submitCheckIn',{eventId:e.id,code:old.code}));
  await assert.rejects(bob.call('submitCheckIn',{eventId:e.id,code:old.code.replace('CLOUD-2','CLOUD-1')}));
  await expectCode(alice.call('bindMeeting',{eventId:e.id}),'invalid-argument');
  const fresh=await alice.call('issueCheckInCode',{eventId:e.id});
  assert.ok((await bob.call('submitCheckIn',{eventId:e.id,code:fresh.code})).receipt);
});

test('withdrawal and rejoin invalidate receipts, pending attempts and committed request replay',async()=>{
  const e=await event({startAt:Date.now()+600000,registrationDeadline:Date.now()+300000});await bob.call('join',{eventId:e.id});
  const issued=await alice.call('issueCheckInCode',{eventId:e.id});
  const attemptId=randomUUID();
  const r=(await db.doc('registrations/'+e.id+'_'+bob.uid).get()).data();
  const oldKey=require('./functions/domain').registrationKey(r);
  const payload={eventId:e.id,code:issued.code,registrationKey:oldKey};
  await bob.call('submitCheckIn',payload,attemptId);
  await bob.call('cancelRegistration',{eventId:e.id});
  assert.equal((await bob.call('eventInfo',{eventId:e.id})).receipt,null);
  await bob.call('join',{eventId:e.id});
  assert.equal((await bob.call('eventInfo',{eventId:e.id})).receipt,null);
  assert.equal((await bob.call('resolveCheckIn',{eventId:e.id,attemptId,registrationKey:oldKey})).status,'NOT_SUBMITTED');
  await assert.rejects(bob.call('submitCheckIn',payload,attemptId));
  await assert.rejects(bob.call('submitCheckIn',payload));
  assert.equal((await bob.call('resolveRequest',{requestIdToResolve:attemptId})).status,'INVALIDATED');
  const fresh=await bob.call('submitCheckIn',{eventId:e.id,code:issued.code});
  assert.notEqual(fresh.receipt.registrationKey,oldKey);
  assert.equal((await db.collection('checkInHistory').where('eventId','==',e.id).where('userId','==',bob.uid).get()).size,1);
});

test('organizer arrival requires confirmation and successful issuance; refresh preserves attendance',async()=>{
  const e=await event();
  await assert.rejects(alice.call('issueCheckInCode',{eventId:e.id}));
  assert.equal((await alice.call('eventInfo',{eventId:e.id})).receipt,null);
  await db.doc('events/'+e.id).update({startAt:Date.now()+600000});
  await assert.rejects(alice.call('issueCheckInCode',{eventId:e.id,confirmArrival:false}));
  assert.equal((await alice.call('eventInfo',{eventId:e.id})).receipt,null);
  const first=await alice.call('issueCheckInCode',{eventId:e.id});
  const refreshed=await alice.call('issueCheckInCode',{eventId:e.id,confirmArrival:false});
  assert.deepEqual(first.receipt,refreshed.receipt);assert.equal(first.receipt.organizer,true);
  const roster=(await alice.call('attendance',{eventId:e.id})).attendees;
  assert.equal(roster.length,1);assert.equal(roster[0].organizer,true);assert.ok(roster[0].checkedInAt);
  await assert.rejects(alice.call('submitCheckIn',{eventId:e.id,code:first.code}));
});

test('only checked-in attendees may review; organizer review is named and marked',async()=>{
  const e=await event();await bob.call('join',{eventId:e.id});await carol.call('join',{eventId:e.id});
  await finishAfterCheckIn(e,[bob]);
  await expectCode(carol.call('submitFeedback',{eventId:e.id,rating:5}),'permission-denied');
  await assert.rejects(alice.call('submitFeedback',{eventId:e.id,rating:5,details:{anonymous:true}}));
  const host=(await alice.call('submitFeedback',{eventId:e.id,rating:5})).feedback;
  assert.equal(host.organizerReview,true);assert.equal(host.eligibilityVersion,2);
  await bob.call('submitFeedback',{eventId:e.id,rating:4});
  await assert.rejects(bob.call('submitFeedback',{eventId:e.id,rating:5}));
});

test('private anonymous photo feedback is authorized server-side and cannot leak via raw storage',async()=>{
  const e=await event();await bob.call('join',{eventId:e.id});await finishAfterCheckIn(e);
  const path='reviews/'+bob.uid+'/'+randomUUID();await uploadBytes(ref(bob.storage,path),png,{contentType:'image/png'});
  const oldDownloadUrl=await getDownloadURL(ref(bob.storage,path));
  const details={organization:5,venue:4,atmosphere:5,tags:['welcoming'],anonymous:true,visibility:'ORGANIZER_ONLY',photos:[path]};
  const feedbackRequest=randomUUID();
  await bob.call('submitFeedback',{eventId:e.id,rating:5,comment:'Private review',details},feedbackRequest);
  const rows=(await alice.call('reviews',{eventId:e.id})).reviews;assert.equal(rows.length,1);
  assert.equal(rows[0].authorName,'Anonymous participant 01');assert.ok(!JSON.stringify(rows).includes(bob.uid));
  assert.equal((await finn.call('reviews',{eventId:e.id})).reviews.length,0);
  const mediaId=rows[0].details.photos[0];
  const bucket=backendRequire('firebase-admin/storage').getStorage(admin).bucket();
  assert.equal((await bucket.file(path).exists())[0],false,'Staging upload must be removed to revoke old bearer URLs');
  assert.equal((await fetch(oldDownloadUrl)).ok,false,'A previously issued download URL must no longer retrieve private media');
  const sealed=(await db.doc('media/'+mediaId).get()).data();
  assert.ok(sealed.path.startsWith('privateReviewMedia/'));
  const metadata=(await bucket.file(sealed.path).getMetadata())[0];
  assert.ok(!metadata.metadata?.firebaseStorageDownloadTokens,'Sealed private media must not carry a download token');
  await assert.rejects(getBytes(ref(bob.storage,sealed.path)));
  await uploadBytes(ref(bob.storage,path),png,{contentType:'image/png'});
  const retriedUrl=await getDownloadURL(ref(bob.storage,path));
  await bob.call('submitFeedback',{eventId:e.id,rating:5,comment:'Private review',details},feedbackRequest);
  assert.equal((await fetch(retriedUrl)).ok,false,'An idempotent retry must also revoke a reuploaded staging URL');
  assert.equal((await alice.call('reviews',{eventId:e.id})).reviews.length,1);
  assert.ok((await alice.call('mediaRead',{mediaId})).base64);
  await expectCode(finn.call('mediaRead',{mediaId}),'permission-denied');
  await assert.rejects(getBytes(ref(finn.storage,path)));
  await assert.rejects(bob.call('submitFeedback',{eventId:e.id,rating:4,comment:'Duplicate',details}));
});

test('public anonymous reviews remain anonymous and covers are readable by a different signed-in client',async()=>{
  const path='covers/'+alice.uid+'/'+randomUUID();await uploadBytes(ref(alice.storage,path),png,{contentType:'image/png'});
  const e=await event({imageUri:path});assert.equal((await getBytes(ref(bob.storage,path))).byteLength,png.length);
  await bob.call('join',{eventId:e.id});await finishAfterCheckIn(e);
  await bob.call('submitFeedback',{eventId:e.id,rating:4,comment:'Public anonymous',details:{anonymous:true,visibility:'PUBLIC',photos:[],tags:[],organization:4,venue:4,atmosphere:4}});
  const rows=(await finn.call('reviews',{eventId:e.id})).reviews;assert.equal(rows.length,1);assert.ok(!JSON.stringify(rows).includes(bob.uid));
  await assert.rejects(uploadBytes(ref(finn.storage,'covers/'+alice.uid+'/'+randomUUID()),png,{contentType:'image/png'}));
});

test('missing authentication is rejected by the callable HTTP boundary',async()=>{
  const response=await fetch('http://127.0.0.1:5001/'+projectId+'/us-central1/gogether',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({data:{action:'snapshot'}})});
  const body=await response.json();assert.equal(body.error.status,'UNAUTHENTICATED');
});


test('registration deadline blocks joining and cancellation after deadline does not promote',async()=>{
  const e=await event({capacity:2});await bob.call('join',{eventId:e.id});await carol.call('join',{eventId:e.id});
  await db.doc('events/'+e.id).update({registrationDeadline:Date.now()-1});
  await assert.rejects(finn.call('join',{eventId:e.id}));
  await bob.call('cancelRegistration',{eventId:e.id});
  const s=await snap(carol);assert.equal(s.registrations.find(r=>r.eventId===e.id&&r.userId===carol.uid).status,'WAITLISTED');
  assert.equal(s.notifications.filter(n=>n.eventId===e.id&&n.type==='PROMOTED').length,0);
});

test('capacity cannot shrink below confirmed count and failed edits leave the event unchanged',async()=>{
  const e=await event({capacity:3});await bob.call('join',{eventId:e.id});await carol.call('join',{eventId:e.id});
  await assert.rejects(alice.call('updateEvent',{eventId:e.id,draft:{...e,capacity:2}}));
  assert.equal((await snap(bob)).events.find(x=>x.id===e.id).capacity,3);
});

test('expired QR and late check-in fail; unresolved attempt remains unknown until final',async()=>{
  const e=await event();await bob.call('join',{eventId:e.id});await db.doc('events/'+e.id).update({startAt:Date.now()+60000});
  const issued=await alice.call('issueCheckInCode',{eventId:e.id});
  const domain=require('./functions/domain');
  await db.doc('checkInCodes/'+domain.hash(issued.code)).update({expiresAt:Date.now()-1});
  await assert.rejects(bob.call('submitCheckIn',{eventId:e.id,code:issued.code}));
  assert.equal((await bob.call('resolveCheckIn',{eventId:e.id,attemptId:randomUUID()})).status,'UNKNOWN');
  await db.doc('events/'+e.id).update({startAt:Date.now()-7200000,endAt:Date.now()-1});
  await assert.rejects(alice.call('issueCheckInCode',{eventId:e.id}));
  await assert.rejects(finn.call('submitFeedback',{eventId:e.id,rating:5,comment:'Not registered',details:{}}));
});

test('request resolution is authenticated-owner scoped and retains original committed result',async()=>{
  const requestId=randomUUID(),e=await event();
  const joined=await bob.call('join',{eventId:e.id},requestId);
  const resolved=await bob.call('resolveRequest',{requestIdToResolve:requestId});
  assert.equal(resolved.status,'COMMITTED');assert.equal(resolved.action,'join');
  assert.deepEqual(resolved.result,joined);
  assert.equal((await alice.call('resolveRequest',{requestIdToResolve:requestId})).status,'UNKNOWN');
  assert.equal((await bob.call('resolveRequest',{requestIdToResolve:randomUUID()})).status,'UNKNOWN');
});

test('profile images upload with owner checks, survive account reload and respect hidden gender',async()=>{
 const path='profiles/'+alice.uid+'/'+randomUUID();
 await uploadBytes(ref(alice.storage,path),png,{contentType:'image/png'});
 const saved=(await alice.call('updateProfile',{name:'Alice with photos',interests:['SPORTS','READING'],
   avatarUri:path,coverUri:path,signature:'See you outdoors!',gender:'NON_BINARY',showGender:false})).user;
 assert.equal(saved.avatarUri,path);assert.equal(saved.gender,'NON_BINARY');
 const own=(await snap(alice)).users.find(u=>u.id===alice.uid);
 assert.equal(own.signature,'See you outdoors!');assert.equal(own.coverUri,path);assert.equal(own.gender,'NON_BINARY');
 const publicUser=(await snap(bob)).users.find(u=>u.id===alice.uid);
 assert.equal(publicUser.gender,'UNDISCLOSED');assert.equal(publicUser.showGender,false);assert.equal(publicUser.email,null);
 assert.deepEqual(Buffer.from(await getBytes(ref(bob.storage,path))),png);
 await expectCode(bob.call('updateProfile',{name:'Bob',interests:[],avatarUri:path}),'invalid-argument');
 await assert.rejects(uploadBytes(ref(bob.storage,'profiles/'+alice.uid+'/'+randomUUID()),png,{contentType:'image/png'}));
 await expectCode(alice.call('updateProfile',{name:'Alice',interests:[],avatarUri:'profiles/'+alice.uid+'/'+randomUUID()}),'not-found');
 await alice.call('updateProfile',{name:'Alice with photos',interests:['SPORTS'],gender:'NON_BINARY',showGender:true});
 const visible=(await snap(bob)).users.find(u=>u.id===alice.uid);
 assert.equal(visible.gender,'NON_BINARY');assert.equal(visible.avatarUri,path);
 await alice.call('updateProfile',{name:'Alice with photos',interests:[],avatarUri:'',coverUri:'',signature:'',gender:'UNDISCLOSED',showGender:false});
});

test('current profile avatars reach reviews and attendance while anonymous reviews redact them',async()=>{
 const first='profiles/'+bob.uid+'/'+randomUUID(),second='profiles/'+bob.uid+'/'+randomUUID();
 await uploadBytes(ref(bob.storage,first),png,{contentType:'image/png'});
 await uploadBytes(ref(bob.storage,second),png,{contentType:'image/png'});
 await bob.call('updateProfile',{name:'Bob',interests:[],avatarUri:first});
 const named=await event(),anon=await event();
 for(const e of [named,anon]){
  await bob.call('join',{eventId:e.id});
  await finishAfterCheckIn(e);
  await bob.call('submitFeedback',{eventId:e.id,rating:4,comment:'Friendly group',details:{anonymous:e.id===anon.id,visibility:'PUBLIC',photos:[],tags:[],organization:4,venue:4,atmosphere:4}});
 }
 async function verify(path){
  const roster=(await alice.call('attendance',{eventId:named.id})).attendees;
  assert.equal(roster.find(p=>p.userId===bob.uid).avatarUri,path);
  for(const viewer of [alice,bob,finn]){
   assert.equal((await viewer.call('reviews',{eventId:named.id})).reviews[0].avatarUri,path);
   const hidden=(await viewer.call('reviews',{eventId:anon.id})).reviews[0];
   assert.equal(hidden.avatarUri,'');assert.equal(hidden.authorName,'Anonymous participant 01');
   assert.ok(!JSON.stringify(hidden).includes(bob.uid));
  }
  assert.equal((await bob.call('eventInfo',{eventId:named.id})).ownFeedback.avatarUri,path);
  assert.equal((await bob.call('eventInfo',{eventId:anon.id})).ownFeedback.avatarUri,'');
 }
 await verify(first);
 await bob.call('updateProfile',{name:'Bob',interests:[],avatarUri:second});
 await verify(second);
 await expectCode(finn.call('attendance',{eventId:named.id}),'permission-denied');
 await bob.call('updateProfile',{name:'Bob',interests:[],avatarUri:''});
});


test('new free-form submissions accept 100 characters and reject 101 at the server',async()=>{
 const e=await event(),limit='x'.repeat(100),over='x'.repeat(101);
 await bob.call('join',{eventId:e.id});
 await expectCode(alice.call('postAnnouncement',{eventId:e.id,content:over}),'invalid-argument');
 await alice.call('postAnnouncement',{eventId:e.id,content:limit});
 await expectCode(bob.call('askQuestion',{eventId:e.id,content:over}),'invalid-argument');
 const question=await bob.call('askQuestion',{eventId:e.id,content:limit});
 await expectCode(alice.call('answerQuestion',{eventId:e.id,questionId:question.id,content:over}),'invalid-argument');
 await alice.call('answerQuestion',{eventId:e.id,questionId:question.id,content:limit});
 await expectCode(alice.call('updateMeeting',{eventId:e.id,meetingAt:e.startAt,location:e.location,notes:over}),'invalid-argument');
 await alice.call('updateMeeting',{eventId:e.id,meetingAt:e.startAt,location:e.location,notes:limit});
 await finishAfterCheckIn(e);
 await expectCode(bob.call('submitFeedback',{eventId:e.id,rating:4,comment:over}),'invalid-argument');
 await expectCode(bob.call('submitFeedback',{eventId:e.id,rating:4,comment:limit,details:{positiveComment:over}}),'invalid-argument');
 const saved=(await bob.call('submitFeedback',{eventId:e.id,rating:4,comment:limit,details:{positiveComment:limit,improvementComment:limit}})).feedback;
 assert.equal(saved.comment.length,100);assert.equal(saved.details.positiveComment.length,100);
});

test('phase 2: multi-round Q&A, legacy answer migration, ownership and soft deletion',async()=>{
 const e=await event();const q=await bob.call('askQuestion',{eventId:e.id,content:'Can I bring a friend?'});
 // Simulate a saved answer from the previous application version.
 await db.doc('questions/'+q.id).update({answer:'One registration each.',answeredBy:alice.uid,answeredAt:Date.now()});
 let rows=(await bob.call('socialRead',{rootId:q.id})).items;assert.equal(rows.length,2);
 const reply=await bob.call('socialMutate',{operation:'reply',contentId:rows[1].id,content:'Can they join the waitlist?'});
 await alice.call('socialMutate',{operation:'reply',contentId:reply.id,content:'Yes, before the deadline.'});
 await expectCode(carol.call('socialMutate',{operation:'reply',contentId:q.id,content:'Hijack'}),'permission-denied');
 await expectCode(alice.call('socialMutate',{operation:'delete',contentId:q.id}),'permission-denied');
 await bob.call('socialMutate',{operation:'delete',contentId:q.id});rows=(await alice.call('socialRead',{rootId:q.id})).items;
 assert.equal(rows.length,4);assert.equal(rows[0].content,'Content deleted');assert.equal(rows[0].canReply,false);
 await expectCode(bob.call('socialMutate',{operation:'reply',contentId:reply.id,content:'Again'}),'permission-denied');
});

test('phase 2: private review threads and anonymous replies never reveal identity',async()=>{
 const e=await event();await bob.call('join',{eventId:e.id});await finishAfterCheckIn(e);
 const f=(await bob.call('submitFeedback',{eventId:e.id,rating:4,comment:'Private feedback',details:{anonymous:true,visibility:'ORGANIZER_ONLY'}})).feedback;
 await expectCode(carol.call('socialRead',{rootId:f.publicId}),'permission-denied');
 await expectCode(carol.call('socialMutate',{operation:'report',contentId:f.publicId,reason:'Guess'}),'permission-denied');
 const reply=await alice.call('socialMutate',{operation:'reply',contentId:f.publicId,content:'Thank you for sharing.'});
 await bob.call('socialMutate',{operation:'reply',contentId:reply.id,content:'More details here.'});
 const rows=(await alice.call('socialRead',{rootId:f.publicId})).items;assert.equal(rows.length,3);assert.equal(rows[0].authorName,rows[2].authorName);
 assert.ok(!JSON.stringify(rows).includes(bob.uid));assert.ok(rows.every(r=>r.private));assert.ok(rows[1].organizer);
});

test('phase 2: likes retry safely; deleting a review revokes media and permits a new review with the same alias',async()=>{
 const e=await event();await bob.call('join',{eventId:e.id});await finishAfterCheckIn(e);
 const path='reviews/'+bob.uid+'/'+randomUUID();await uploadBytes(ref(bob.storage,path),png,{contentType:'image/png'});
 const firstRequest=randomUUID(),firstPayload={eventId:e.id,rating:5,comment:'First',details:{anonymous:true,photos:[path]}};
 const first=(await bob.call('submitFeedback',firstPayload,firstRequest)).feedback;
 const rid=randomUUID();await carol.call('socialMutate',{operation:'like',contentId:first.publicId,liked:true},rid);await carol.call('socialMutate',{operation:'like',contentId:first.publicId,liked:true},rid);
 assert.equal((await carol.call('socialRead',{rootId:first.publicId})).items[0].likes,1);
 await alice.call('socialMutate',{operation:'reply',contentId:first.publicId,content:'Thanks'});
 const name=(await alice.call('reviews',{eventId:e.id})).reviews[0].authorName;
 await bob.call('socialMutate',{operation:'delete',contentId:first.publicId});
 assert.equal((await bob.call('eventInfo',{eventId:e.id})).ownFeedback,null);
 assert.equal((await bob.call('eventInfo',{eventId:e.id})).replacesReviewId,first.publicId);
 assert.equal((await bob.call('resolveRequest',{requestIdToResolve:firstRequest})).status,'INVALIDATED');
 await expectCode(bob.call('submitFeedback',firstPayload,firstRequest),'failed-precondition');
 await expectCode(alice.call('mediaRead',{mediaId:first.details.photos[0]}),'permission-denied');
 const second=(await bob.call('submitFeedback',{eventId:e.id,rating:4,comment:'Revised',details:{anonymous:true}})).feedback;
 assert.notEqual(second.publicId,first.publicId);assert.equal((await alice.call('reviews',{eventId:e.id})).reviews[0].authorName,name);
 assert.equal((await alice.call('socialRead',{rootId:first.publicId})).items.length,2);
 await expectCode(alice.call('mediaRead',{mediaId:first.details.photos[0]}),'permission-denied');
});

test('phase 2: opaque personal blocking, direct-interaction denial and unblock',async()=>{
 const e=await event();await bob.call('join',{eventId:e.id});await finishAfterCheckIn(e);
 const f=(await bob.call('submitFeedback',{eventId:e.id,rating:3,comment:'Anonymous',details:{anonymous:true}})).feedback;
 await carol.call('socialMutate',{operation:'blockUser',contentId:f.publicId,kind:'PERSONAL'});
 assert.equal((await carol.call('reviews',{eventId:e.id})).reviews.length,0);
 await expectCode(carol.call('socialMutate',{operation:'like',contentId:f.publicId,liked:true}),'permission-denied');
 const b=(await carol.call('socialRead')).blocks.find(b=>b.kind==='PERSONAL');assert.ok(b.label.startsWith('Anonymous participant'));assert.ok(!JSON.stringify(b).includes(bob.uid));
 await expectCode(alice.call('socialMutate',{operation:'unblock',blockId:b.id}),'permission-denied');
 await carol.call('socialMutate',{operation:'unblock',blockId:b.id});assert.equal((await carol.call('reviews',{eventId:e.id})).reviews.length,1);
});

test('phase 2: registration blacklist skips waitlist promotions and preserves existing places',async()=>{
 const e=await event({capacity:2});await bob.call('join',{eventId:e.id});await carol.call('join',{eventId:e.id});await finn.call('join',{eventId:e.id});
 await alice.call('socialMutate',{operation:'blockUser',targetUserId:carol.uid,kind:'REGISTRATION'});
 await alice.call('socialMutate',{operation:'blockUser',targetUserId:bob.uid,kind:'REGISTRATION'});
 assert.equal((await snap(bob)).registrations.find(r=>r.eventId===e.id&&r.userId===bob.uid).status,'CONFIRMED');
 await bob.call('cancelRegistration',{eventId:e.id});
 let s=await snap(alice);assert.equal(s.registrations.find(r=>r.eventId===e.id&&r.userId===finn.uid).status,'CONFIRMED');assert.equal(s.registrations.find(r=>r.eventId===e.id&&r.userId===carol.uid).status,'WAITLISTED');
 await expectCode(bob.call('join',{eventId:e.id}),'permission-denied');
 for(const b of (await alice.call('socialRead')).blocks.filter(b=>b.kind==='REGISTRATION'))await alice.call('socialMutate',{operation:'unblock',blockId:b.id});
 s=await snap(carol);assert.equal(s.registrations.find(r=>r.eventId===e.id&&r.userId===carol.uid).status,'WAITLISTED');
});

test('phase 2: only project-maintainer claim can handle reports and all raw social documents stay private',async()=>{
 const e=await event();const q=await bob.call('askQuestion',{eventId:e.id,content:'Reported question'});
 await carol.call('socialMutate',{operation:'report',contentId:q.id,reason:'Please check this content'});
 const report=(await carol.call('socialRead')).reports.find(r=>r.contentId===q.id);
 assert.equal((await alice.call('socialRead')).maintainer,false);
 await expectCode(alice.call('socialMutate',{operation:'resolveReport',reportId:report.id,resolution:'REMOVED',outcome:'I am the organizer'}),'permission-denied');
 const mod=await client('Project maintainer');const {getAuth:adminAuth}=backendRequire('firebase-admin/auth');await adminAuth(admin).setCustomUserClaims(mod.uid,{maintainer:true});await mod.auth.currentUser.getIdToken(true);
 let queue=await mod.call('socialRead');assert.equal(queue.maintainer,true);assert.ok(queue.reports.some(r=>r.id===report.id&&r.content==='Reported question'));
 await mod.call('socialMutate',{operation:'resolveReport',reportId:report.id,resolution:'REMOVED',outcome:'Reviewed and removed'});
 assert.equal((await carol.call('socialRead')).reports.find(r=>r.id===report.id).status,'REMOVED');
 assert.equal((await bob.call('socialRead',{rootId:q.id})).items[0].content,'Content deleted');
 await assert.rejects(getDoc(doc(bob.fs,'social','state')));await assert.rejects(setDoc(doc(alice.fs,'social','state'),{maintainer:true}));
});

test('phase 3: question reply notifications are exact, deduplicated, idempotent and account-scoped',async()=>{
 const e=await event(),q=await bob.call('askQuestion',{eventId:e.id,content:'Question notifications'});
 let notices=(await snap(alice)).notifications.filter(n=>n.eventId===e.id);assert.equal(notices.length,1);assert.equal(notices[0].contentId,q.id);
 const request=randomUUID(),args={operation:'reply',contentId:q.id,content:'First answer'};
 const r=await alice.call('socialMutate',args,request);assert.equal((await alice.call('socialMutate',args,request)).id,r.id);
 notices=(await snap(bob)).notifications.filter(n=>n.eventId===e.id);assert.equal(notices.length,1);assert.equal(notices[0].contentId,r.id);assert.equal(notices[0].rootId,q.id);assert.equal(notices[0].type,'QUESTION_REPLIED');assert.equal(notices[0].isRead,false);
 const follow=await bob.call('socialMutate',{operation:'reply',contentId:r.id,content:'Follow-up'});
 const hostNotices=(await snap(alice)).notifications.filter(n=>n.contentId===follow.id);assert.equal(hostNotices.length,1);
 await expectCode(carol.call('markRead',{notificationId:notices[0].id}),'permission-denied');
 await bob.call('markRead',{notificationId:notices[0].id});assert.equal((await snap(bob)).notifications.find(n=>n.id===notices[0].id).isRead,true);
 assert.equal((await snap(alice)).notifications.find(n=>n.contentId===follow.id).isRead,false);
 await alice.call('markAllRead');assert.ok((await snap(alice)).notifications.every(n=>n.isRead));
});

test('phase 3: private anonymous reviews and replies notify only authorized people with stable safe aliases',async()=>{
 const e=await event();await bob.call('join',{eventId:e.id});await finishAfterCheckIn(e);
 const f=(await bob.call('submitFeedback',{eventId:e.id,rating:4,comment:'Private content',details:{anonymous:true,visibility:'ORGANIZER_ONLY'}})).feedback;
 const n=(await snap(alice)).notifications.find(n=>n.contentId===f.publicId);assert.ok(n);assert.equal(n.type,'REVIEW_POSTED');assert.match(n.actorName,/Anonymous participant/);assert.equal(n.actorAvatarUri,'');assert.ok(!JSON.stringify(n).includes(bob.uid));
 const raw=(await getDoc(doc(alice.fs,'notifications',n.id))).data();assert.ok(!JSON.stringify(raw).includes(bob.uid));assert.ok(!JSON.stringify(raw).includes('Private content'));
 const hostReply=await alice.call('socialMutate',{operation:'reply',contentId:f.publicId,content:'Thanks for your review'});
 assert.equal((await snap(bob)).notifications.filter(n=>n.contentId===hostReply.id).length,1);
 const r=await bob.call('socialMutate',{operation:'reply',contentId:hostReply.id,content:'Further private feedback'});
 const answer=(await snap(alice)).notifications.find(n=>n.contentId===r.id);assert.equal(answer.actorName,n.actorName);assert.ok(!JSON.stringify(answer).includes(bob.uid));
 assert.equal((await snap(carol)).notifications.filter(n=>n.eventId===e.id&&n.type.startsWith('REVIEW')).length,0);
 await expectCode(carol.call('socialRead',{rootId:r.id}),'permission-denied');
 const own=(await alice.call('submitFeedback',{eventId:e.id,rating:5,comment:'Organizer review',details:{}})).feedback;
 assert.equal((await snap(alice)).notifications.filter(n=>n.contentId===own.publicId).length,0);
});

test('phase 3: public reply delivers once to root and target authors; deleted target is redacted',async()=>{
 const e=await event();await bob.call('join',{eventId:e.id});await finishAfterCheckIn(e);
 const f=(await bob.call('submitFeedback',{eventId:e.id,rating:4,comment:'Public review',details:{anonymous:true}})).feedback;
 const first=await carol.call('socialMutate',{operation:'reply',contentId:f.publicId,content:'A comment'});
 const r=await finn.call('socialMutate',{operation:'reply',contentId:first.id,content:'Reply to the comment'});
 for(const c of [bob,carol])assert.equal((await snap(c)).notifications.filter(n=>n.contentId===r.id).length,1);
 assert.equal((await snap(alice)).notifications.filter(n=>n.contentId===r.id).length,0);
 assert.equal((await snap(finn)).notifications.filter(n=>n.contentId===r.id).length,0);
 await finn.call('socialMutate',{operation:'delete',contentId:r.id});const deleted=(await snap(carol)).notifications.find(n=>n.contentId===r.id);assert.match(deleted.content,/deleted/);assert.equal(deleted.actorName,'Activity conversation');
 assert.equal((await carol.call('socialRead',{rootId:r.id})).items.find(i=>i.id===r.id).content,'Content deleted');
});

test('phase 3: personal block hides interactions while important cancellation stays visible',async()=>{
 const e=await event();await bob.call('join',{eventId:e.id});const q=await bob.call('askQuestion',{eventId:e.id,content:'Block notification test'});
 const r=await alice.call('socialMutate',{operation:'reply',contentId:q.id,content:'Answer before block'});
 assert.ok((await snap(bob)).notifications.some(n=>n.contentId===r.id));
 await bob.call('socialMutate',{operation:'blockUser',targetUserId:alice.uid,kind:'PERSONAL'});
 try {
  assert.ok(!(await snap(bob)).notifications.some(n=>n.contentId===r.id));
  await alice.call('cancelEvent',{eventId:e.id});assert.ok((await snap(bob)).notifications.some(n=>n.eventId===e.id&&n.type==='EVENT_CANCELLED'));
 } finally {for(const b of (await bob.call('socialRead')).blocks.filter(b=>b.kind==='PERSONAL'))await bob.call('socialMutate',{operation:'unblock',blockId:b.id});}
 assert.ok((await snap(bob)).notifications.some(n=>n.contentId===r.id&&!n.isRead));
});

test('phase 4: public participant aggregates match across viewers, privacy, blocks and both read endpoints',async()=>{
 const e=await event({capacity:6});for(const c of [bob,carol,finn])await c.call('join',{eventId:e.id});await finishAfterCheckIn(e,[bob,carol,finn]);
 const b=(await bob.call('submitFeedback',{eventId:e.id,rating:5,comment:'Public anonymous',details:{anonymous:true}})).feedback;
 await carol.call('submitFeedback',{eventId:e.id,rating:3,comment:'Public named',details:{}});
 await finn.call('submitFeedback',{eventId:e.id,rating:1,comment:'Private must not count',details:{visibility:'ORGANIZER_ONLY'}});
 await alice.call('submitFeedback',{eventId:e.id,rating:1,comment:'Host must not count',details:{}});
 for(const c of [alice,bob,carol,finn]) {
  const total=(await snap(c)).eventRatings.find(r=>r.eventId===e.id);assert.deepEqual(total,{eventId:e.id,ratingSum:8,reviewCount:2});
  assert.deepEqual((await c.call('reviews',{eventId:e.id})).ratingSummary,{ratingSum:8,reviewCount:2});
  assert.ok(!JSON.stringify(total).includes(bob.uid));
 }
 await bob.call('socialMutate',{operation:'blockUser',targetUserId:carol.uid,kind:'PERSONAL'});
 try {const result=await bob.call('reviews',{eventId:e.id});assert.deepEqual(result.ratingSummary,{ratingSum:8,reviewCount:2});assert.ok(result.reviews.every(r=>r.comment!=='Public named'));}
 finally {for(const block of (await bob.call('socialRead')).blocks.filter(b=>b.kind==='PERSONAL'))await bob.call('socialMutate',{operation:'unblock',blockId:block.id});}
 const afterLike=(await snap(alice)).eventRatings.find(r=>r.eventId===e.id);await carol.call('socialMutate',{operation:'like',contentId:b.publicId,liked:true});
 assert.deepEqual((await snap(alice)).eventRatings.find(r=>r.eventId===e.id),afterLike);
});

test('phase 4: deletion, moderator removal and idempotent replacement immediately recalculate totals',async()=>{
 const e=await event();await bob.call('join',{eventId:e.id});await carol.call('join',{eventId:e.id});await finishAfterCheckIn(e,[bob,carol]);
 const old=(await bob.call('submitFeedback',{eventId:e.id,rating:5,comment:'Old review',details:{anonymous:true}})).feedback;
 const c=(await carol.call('submitFeedback',{eventId:e.id,rating:3,comment:'Another review',details:{}})).feedback;
 await bob.call('socialMutate',{operation:'delete',contentId:old.publicId});assert.deepEqual((await bob.call('reviews',{eventId:e.id})).ratingSummary,{ratingSum:3,reviewCount:1});
 const rid=randomUUID(),args={eventId:e.id,rating:2,comment:'Replacement',details:{anonymous:true},replacesReviewId:old.publicId};
 const fresh=(await bob.call('submitFeedback',args,rid)).feedback;await bob.call('submitFeedback',args,rid);assert.notEqual(fresh.publicId,old.publicId);
 assert.deepEqual((await alice.call('reviews',{eventId:e.id})).ratingSummary,{ratingSum:5,reviewCount:2});
 await bob.call('socialMutate',{operation:'report',contentId:c.publicId,reason:'Moderator test'});
 const report=(await bob.call('socialRead')).reports.find(r=>r.contentId===c.publicId);
 const mod=await client('Rating moderator');await backendRequire('firebase-admin/auth').getAuth(admin).setCustomUserClaims(mod.uid,{maintainer:true});await mod.auth.currentUser.getIdToken(true);
 await mod.call('socialMutate',{operation:'resolveReport',reportId:report.id,resolution:'REMOVED',outcome:'Verified report'});
 assert.deepEqual((await alice.call('reviews',{eventId:e.id})).ratingSummary,{ratingSum:2,reviewCount:1});
 await bob.call('socialMutate',{operation:'delete',contentId:fresh.publicId});assert.deepEqual((await alice.call('reviews',{eventId:e.id})).ratingSummary,{ratingSum:0,reviewCount:0});
});

test('phase 4: old reviews require attendance evidence and totals ignore forged or stale registration data',async()=>{
 const e=await event({capacity:4});await bob.call('join',{eventId:e.id});await carol.call('join',{eventId:e.id});await finishAfterCheckIn(e,[bob]);
 const legacy=(userId,rating)=>({id:`${e.id}_${userId}`,publicId:randomUUID(),eventId:e.id,userId,rating,comment:'Legacy record',createdAt:Date.now(),status:'SUBMITTED',details:{anonymous:false,visibility:'PUBLIC',photos:[]}});
 await db.doc(`feedback/${e.id}_${bob.uid}`).set(legacy(bob.uid,4));await db.doc(`feedback/${e.id}_${carol.uid}`).set(legacy(carol.uid,1));
 assert.deepEqual((await alice.call('reviews',{eventId:e.id})).ratingSummary,{ratingSum:4,reviewCount:1});
 await db.doc(`registrations/${e.id}_${bob.uid}`).update({queueOrder:999});
 assert.deepEqual((await alice.call('reviews',{eventId:e.id})).ratingSummary,{ratingSum:0,reviewCount:0});
 await assert.rejects(setDoc(doc(bob.fs,'events',e.id),{ratingSum:100,reviewCount:20},{merge:true}));
 const cancelled=await event();await alice.call('cancelEvent',{eventId:cancelled.id});assert.deepEqual((await snap(alice)).eventRatings.find(r=>r.eventId===cancelled.id),{eventId:cancelled.id,ratingSum:0,reviewCount:0});
});

test('old single-answer records upgrade through the legacy endpoint without duplicate replies or notices',async()=>{
 const e=await event();const qid='legacy-'+randomUUID();
 await db.doc('questions/'+qid).set({id:qid,eventId:e.id,authorId:bob.uid,content:'Legacy question',createdAt:Date.now()-3000,
  answer:'Original answer',answeredBy:alice.uid,answeredAt:Date.now()-2000});
 const before=(await snap(bob)).notifications.length;
 const request=randomUUID();
 await alice.call('answerQuestion',{questionId:qid,content:'Second answer'},request);
 await alice.call('answerQuestion',{questionId:qid,content:'Second answer'},request);
 await alice.call('answerQuestion',{questionId:qid,content:'Third answer'});
 for(let i=0;i<2;i++){
  const s=await snap(bob),q=s.questions.find(q=>q.id===qid);
  assert.equal(q.answer,'Third answer');assert.equal(q.answeredBy,alice.uid);
  assert.deepEqual(q.replyHistory.map(r=>r.content),['Original answer','Second answer','Third answer']);
  const thread=await bob.call('socialRead',{rootId:qid});
  assert.deepEqual(thread.items.slice(1).map(r=>r.content),['Original answer','Second answer','Third answer']);
  assert.equal(new Set(thread.items.map(r=>r.id)).size,4);
  const notices=s.notifications.filter(n=>n.questionId===qid&&n.type==='QUESTION_ANSWERED');
  assert.equal(notices.length,2);assert.equal(s.notifications.length,before+2);
  assert.deepEqual(new Set(notices.map(n=>n.contentId)),new Set(q.replyHistory.slice(1).map(r=>r.id)));
 }
});
test('legacy optional review fields remain readable while unknown privacy never becomes public',async()=>{
 const e=await event();await bob.call('join',{eventId:e.id});await finishAfterCheckIn(e);
 const id=e.id+'_'+bob.uid,publicId='legacy-review-'+randomUUID();
 const original={id,publicId,eventId:e.id,userId:bob.uid,rating:4,comment:'Old review without details',createdAt:Date.now()};
 await db.doc('feedback/'+id).set(original);
 const publicRows=await carol.call('reviews',{eventId:e.id});
 assert.equal(publicRows.reviews[0].comment,original.comment);assert.deepEqual(publicRows.reviews[0].details.photos,[]);
 assert.deepEqual(publicRows.ratingSummary,{ratingSum:4,reviewCount:1});
 await db.doc('feedback/'+id).update({details:{visibility:'FUTURE_PRIVATE',anonymous:true}});
 const hidden=await carol.call('reviews',{eventId:e.id});assert.equal(hidden.reviews.length,0);
 assert.deepEqual(hidden.ratingSummary,{ratingSum:0,reviewCount:0});
 await expectCode(carol.call('socialRead',{rootId:publicId}),'permission-denied');
 const host=(await alice.call('reviews',{eventId:e.id})).reviews[0];
 assert.match(host.authorName,/Anonymous participant/);assert.equal(host.details.visibility,'ORGANIZER_ONLY');
 assert.ok(!('userId'in host)&&!('authorId'in host));
 assert.equal((await bob.call('socialRead',{rootId:publicId})).items.length,1);
 assert.equal((await db.doc('feedback/'+id).get()).data().details.visibility,'FUTURE_PRIVATE');
});
