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
const fix = {latitude:31.274,longitude:120.74,accuracyMeters:5,ageMillis:10,simulated:false};
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
  c.call=async(action,payload={},requestId=randomUUID())=>(await httpsCallable(functions,'gogether')({action,requestId,...payload})).data;
  clients.push(c); await c.call('ensureProfile',{name,interests:['SPORTS']}); return c;
}
function draft(overrides={}) {
  const now=Date.now(); return {title:'Backend integration activity',category:'SPORTS',description:'A shared activity for authenticated backend testing.',
    startAt:now+3600000,endAt:now+7200000,registrationDeadline:now+1800000,location:'East sports centre',city:'Suzhou',capacity:3,
    notes:'Bring water',experienceRequirement:'NONE',beginnerGuidance:true,requiredItems:'Racket',spareEquipment:'Two rackets',imageUri:'',...overrides};
}
async function event(overrides={}) { return (await alice.call('createEvent',{draft:draft(overrides)})).event; }
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

test('server checks QR, location, eligibility, duplicate attempts and organizer roster',async()=>{
  const e=await event();await bob.call('join',{eventId:e.id});
  // Trusted fixture advances schedule; clients have no write access to server timestamps.
  await db.doc('events/'+e.id).update({startAt:Date.now()+60000,endAt:Date.now()+3600000});
  await alice.call('bindMeeting',{eventId:e.id,fix});
  const issued=await alice.call('issueCheckInCode',{eventId:e.id,fix});assert.ok(issued.code);
  await assert.rejects(finn.call('submitCheckIn',{eventId:e.id,code:issued.code,fix}));
  await assert.rejects(bob.call('submitCheckIn',{eventId:e.id,code:issued.code,fix:{...fix,latitude:40}}));
  await assert.rejects(bob.call('submitCheckIn',{eventId:e.id,code:'forged',fix}));
  const attemptId=randomUUID(); const first=await bob.call('submitCheckIn',{eventId:e.id,code:issued.code,fix},attemptId);
  const again=await bob.call('submitCheckIn',{eventId:e.id,code:issued.code,fix},attemptId);
  assert.deepEqual(first.receipt,again.receipt);
  assert.equal((await bob.call('resolveCheckIn',{eventId:e.id,attemptId})).status,'SAVED');
  assert.ok((await alice.call('attendance',{eventId:e.id})).attendees.find(x=>x.userId===bob.uid).checkedInAt);
  await expectCode(finn.call('attendance',{eventId:e.id}),'permission-denied');
  assert.equal((await snap(bob)).checkIns.filter(x=>x.eventId===e.id).length,1);
  assert.equal((await snap(alice)).checkIns.filter(x=>x.eventId===e.id&&x.userId===bob.uid).length,1);
  assert.equal((await snap(finn)).checkIns.filter(x=>x.eventId===e.id).length,0);
});

test('changed meeting invalidates issued QR and simulated/stale fixes are rejected',async()=>{
  const e=await event();await bob.call('join',{eventId:e.id});await db.doc('events/'+e.id).update({startAt:Date.now()+60000});
  await alice.call('bindMeeting',{eventId:e.id,fix});const old=await alice.call('issueCheckInCode',{eventId:e.id,fix});
  await alice.call('bindMeeting',{eventId:e.id,fix:{...fix,latitude:fix.latitude+0.0001}});
  await assert.rejects(bob.call('submitCheckIn',{eventId:e.id,code:old.code,fix}));
  await assert.rejects(alice.call('issueCheckInCode',{eventId:e.id,fix:{...fix,simulated:true}}));
  await assert.rejects(alice.call('issueCheckInCode',{eventId:e.id,fix:{...fix,ageMillis:60000}}));
});

test('private anonymous photo feedback is authorized server-side and cannot leak via raw storage',async()=>{
  const e=await event();await bob.call('join',{eventId:e.id});await db.doc('events/'+e.id).update({startAt:Date.now()-7200000,endAt:Date.now()-1000});
  const path='reviews/'+bob.uid+'/'+randomUUID();await uploadBytes(ref(bob.storage,path),png,{contentType:'image/png'});
  const oldDownloadUrl=await getDownloadURL(ref(bob.storage,path));
  const details={organization:5,venue:4,atmosphere:5,tags:['welcoming'],anonymous:true,visibility:'ORGANIZER_ONLY',photos:[path]};
  const feedbackRequest=randomUUID();
  await bob.call('submitFeedback',{eventId:e.id,rating:5,comment:'Private review',details},feedbackRequest);
  const rows=(await alice.call('reviews',{eventId:e.id})).reviews;assert.equal(rows.length,1);
  assert.equal(rows[0].authorName,'Anonymous participant');assert.ok(!JSON.stringify(rows).includes(bob.uid));
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
  await bob.call('join',{eventId:e.id});await db.doc('events/'+e.id).update({startAt:Date.now()-7200000,endAt:Date.now()-1000});
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
  await alice.call('bindMeeting',{eventId:e.id,fix});const issued=await alice.call('issueCheckInCode',{eventId:e.id,fix});
  const domain=require('./functions/domain');
  await db.doc('checkInCodes/'+domain.hash(issued.code)).update({expiresAt:Date.now()-1});
  await assert.rejects(bob.call('submitCheckIn',{eventId:e.id,code:issued.code,fix}));
  assert.equal((await bob.call('resolveCheckIn',{eventId:e.id,attemptId:randomUUID()})).status,'UNKNOWN');
  await db.doc('events/'+e.id).update({startAt:Date.now()-7200000,endAt:Date.now()-1});
  await assert.rejects(alice.call('issueCheckInCode',{eventId:e.id,fix}));
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
