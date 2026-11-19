const {before,after,beforeEach,test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,updateDoc,collection,query,where,getDocs,onSnapshot,disableNetwork,enableNetwork}=require('firebase/firestore');
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-gogether',firestore:{host:'127.0.0.1',port:8080,rules:fs.readFileSync('firestore.rules','utf8')}})});
after(async()=>{await env?.cleanup()});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async ctx=>{
  const db=ctx.firestore();
  await setDoc(doc(db,'events/e1'),{organizerId:'alice',status:'OPEN'});
  await setDoc(doc(db,'registrations/e1_bob'),{eventId:'e1',userId:'bob',status:'CONFIRMED'});
  await setDoc(doc(db,'announcements/a1'),{eventId:'e1',authorId:'alice',content:'East gate'});
  await setDoc(doc(db,'notifications/n1'),{userId:'bob',eventId:'e1',content:'East gate',isRead:false});
 });
});
test('only recipient can read and mark a notification; contents and recipient cannot be changed',async()=>{
 const bob=env.authenticatedContext('bob').firestore(), eve=env.authenticatedContext('eve').firestore();
 await assertSucceeds(getDoc(doc(bob,'notifications/n1')));
 await assertSucceeds(updateDoc(doc(bob,'notifications/n1'),{isRead:true}));
 await assertFails(getDoc(doc(eve,'notifications/n1')));
 await assertFails(updateDoc(doc(eve,'notifications/n1'),{isRead:true}));
 await assertFails(updateDoc(doc(bob,'notifications/n1'),{content:'forged'}));
 await assertFails(updateDoc(doc(bob,'notifications/n1'),{userId:'eve'}));
 await assertFails(setDoc(doc(bob,'notifications/forged'),{userId:'bob',isRead:false}));
});
test('announcements are visible to organizer and participant, not outsiders or anonymous users',async()=>{
 for(const uid of ['alice','bob']) await assertSucceeds(getDocs(query(collection(env.authenticatedContext(uid).firestore(),'announcements'),where('eventId','==','e1'))));
 await assertFails(getDoc(doc(env.authenticatedContext('eve').firestore(),'announcements/a1')));
 await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'announcements/a1')));
});
test('registration lists require own-user or organizer query and client writes are blocked',async()=>{
 const bob=env.authenticatedContext('bob').firestore(), alice=env.authenticatedContext('alice').firestore();
 await assertSucceeds(getDocs(query(collection(bob,'registrations'),where('userId','==','bob'))));
 await assertSucceeds(getDocs(query(collection(alice,'registrations'),where('eventId','==','e1'))));
 await assertFails(getDocs(collection(bob,'registrations')));
 await assertFails(updateDoc(doc(alice,'events/e1'),{meetingLocation:'forged'}));
});
test('two independent clients receive updates and read state survives reconnect',async()=>{
 const first=env.authenticatedContext('bob').firestore(), second=env.authenticatedContext('bob').firestore();
 const waitFor=(predicate)=>new Promise((resolve,reject)=>{
  const timer=setTimeout(()=>{stop();reject(Error('Timed out waiting for sync'))},15000);
  const stop=onSnapshot(doc(second,'notifications/n1'),snap=>{
   if(predicate(snap)){clearTimeout(timer);stop();resolve(snap)}
  },e=>{clearTimeout(timer);reject(e)});
 });
 await getDoc(doc(second,'notifications/n1'));
 await disableNetwork(second);
 await updateDoc(doc(first,'notifications/n1'),{isRead:true});
 const synced=waitFor(s=>s.data()?.isRead===true && !s.metadata.fromCache);
 await enableNetwork(second); await synced;
 const third=env.authenticatedContext('bob').firestore();
 assert.equal((await getDoc(doc(third,'notifications/n1'))).data().isRead,true);
});

test('server atomically publishes one announcement and notifications for an idempotent request',async()=>{
 const {coordinate}=require('./functions/index');
 await env.withSecurityRulesDisabled(async ctx=>{
  await updateDoc(doc(ctx.firestore(),'events/e1'),{endAt:Date.now()+3600000,title:'Test event',startAt:Date.now()+1800000});
  await setDoc(doc(ctx.firestore(),'registrations/e1_emma'),{eventId:'e1',userId:'emma',status:'WAITLISTED'});
 });
 const request={auth:{uid:'alice'},data:{action:'announcement',eventId:'e1',requestId:'request-1234',content:'Meet at south gate'}};
 await Promise.all([coordinate.run(request),coordinate.run(request)]);
 await env.withSecurityRulesDisabled(async ctx=>{
  const announcements=await getDocs(query(collection(ctx.firestore(),'announcements'),where('content','==','Meet at south gate')));
  assert.equal(announcements.size,1);
  const notices=await getDocs(query(collection(ctx.firestore(),'notifications'),where('content','==','Meet at south gate')));
  assert.deepEqual(notices.docs.map(d=>d.data().userId).sort(),['bob','emma']);
 });
 await assert.rejects(coordinate.run({...request,data:{...request.data,content:'Different'}}),e=>e.code==='already-exists');
 await assert.rejects(coordinate.run({...request,auth:{uid:'bob'},data:{...request.data,requestId:'request-5678'}}),e=>e.code==='permission-denied');
 await assert.rejects(coordinate.run({...request,auth:null}),e=>e.code==='unauthenticated');
});
test('server patches meeting fields, preserves event schedule and rejects invalid time',async()=>{
 const {coordinate}=require('./functions/index');
 const start=Date.now()+1800000,end=start+3600000;
 await env.withSecurityRulesDisabled(async ctx=>updateDoc(doc(ctx.firestore(),'events/e1'),{startAt:start,endAt:end,title:'Test event'}));
 const request={auth:{uid:'alice'},data:{action:'meeting',eventId:'e1',requestId:'meeting-1234',meetingAt:start-60000,location:'South gate',notes:'Bring water'}};
 await coordinate.run(request);
 await env.withSecurityRulesDisabled(async ctx=>{
  const event=(await getDoc(doc(ctx.firestore(),'events/e1'))).data();
  assert.equal(event.startAt,start); assert.equal(event.meetingLocation,'South gate');
  const notices=await getDocs(query(collection(ctx.firestore(),'notifications'),where('type','==','MEETING_UPDATED')));
  assert.equal(notices.size,1);
 });
 await assert.rejects(coordinate.run({...request,data:{...request.data,requestId:'meeting-5678',meetingAt:end+1}}),e=>e.code==='invalid-argument');
});
