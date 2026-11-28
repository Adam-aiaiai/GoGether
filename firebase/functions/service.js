"use strict";
const D=require('./domain');
const S=require('./social');
const R=require('./ratings');
const {randomBytes}=require('node:crypto');
const READ_ACTIONS=new Set(['socialRead','snapshot','eventInfo','attendance','reviews','resolveCheckIn','resolveRequest','mediaRead']);
const MUTATIONS=new Set(['socialMutate','ensureProfile','updateProfile','createEvent','updateEvent','join','cancelRegistration','cancelEvent','postAnnouncement','askQuestion','answerQuestion','updateMeeting','markRead','markAllRead','issueCheckInCode','submitCheckIn','submitFeedback']);
const rows=s=>s.docs.map(d=>d.data());
const MAX_ROWS=1000,MAX_ROSTER=200;
function bounded(s){D.requireThat(s.size<=MAX_ROWS,'Dataset exceeds this course deployment limit; add pagination before growing it','resource-exhausted');return rows(s);}
function receipt(c){return c?{sessionId:c.eventId,userId:c.userId,checkedInAt:c.checkedInAt,registrationKey:c.registrationKey??null,organizer:c.status==='HOST_CONFIRMED'}:null;}
function visibleReview(f,uid,organizer,profiles,social=S.empty()){
 if(social.deleted.includes(f.publicId)||S.blocked(social,uid,f.userId))return null;
 if(S.privateReview(f)&&uid!==f.userId&&uid!==organizer)return null;
 const details={...f.details,anonymous:!!f.details?.anonymous,visibility:S.privateReview(f)?'ORGANIZER_ONLY':'PUBLIC',photos:f.details?.photos??[]};
 const author=profiles.find(p=>p.id===f.userId);
 return {id:f.publicId,authorName:details.anonymous?S.alias(social,f.eventId,f.userId):(author?.displayName??'Participant'),avatarUri:details.anonymous?'':(author?.avatarUri??''),anonymous:details.anonymous,organizer:!details.anonymous&&f.userId===organizer,own:f.userId===uid,rating:f.rating,comment:f.comment,createdAt:f.createdAt,details};
}
function isTransactionRetryable(error) {
 return [4,10,14].includes(error?.code) || (error?.code===3 && /transaction.*(?:invalid|closed|lock)|lock timeout/i.test(error.message??''));
}
async function runIdempotentTransaction(run,replay,pause=ms=>new Promise(r=>setTimeout(r,ms))) {
 for(let attempt=0;attempt<3;attempt++) {
  try { return await run(); }
  catch(error) {
   const saved=await replay(); if(saved) return saved.result;
   if(!isTransactionRetryable(error)) throw error;
   if(attempt===2) throw new D.DomainError('unavailable','The request is still being confirmed. Retry with the same request ID.');
   await pause(50*(attempt+1));
  }
 }
}
function createService(db,bucket,clock=Date.now){
 const collection=name=>db.collection(name);
 async function currentCheckIn(uid,eventId){
  const [c,r,e]=await Promise.all(['checkIns','registrations','events'].map(n=>collection(n).doc(n==='events'?eventId:`${eventId}_${uid}`).get()));
  return D.validCheckIn(c.data(),r.data(),e.data())?c.data():null;
 }

 async function all(name,tx,query){const q=(query??collection(name)).limit(MAX_ROWS+1);return bounded(await(tx?tx.get(q):q.get()));}
 async function fileMetadata(path){D.requireThat(bucket,'Media storage is unavailable','failed-precondition');try {const [m]=await bucket.file(path).getMetadata();D.requireThat(Number(m.size)<=2*1024*1024&&['image/jpeg','image/png','image/webp'].includes(m.contentType),'Invalid uploaded image','invalid-argument');return m;}catch(e){if(e instanceof D.DomainError)throw e;throw new D.DomainError('not-found','Uploaded image not found');}}
 async function sealReview(path,uid) {
  // Published media lives in a server-only namespace. Deleting the staging object
  // invalidates every bearer URL, including emulator tokens not represented as metadata.
  const sealedPath=`privateReviewMedia/${D.hash([uid,path])}`;
  const source=bucket.file(path),sealed=bucket.file(sealedPath);
  const [exists]=await sealed.exists();
  if(!exists) {
   const metadata=await fileMetadata(path); const [bytes]=await source.download();
   try { await sealed.save(bytes,{resumable:false,preconditionOpts:{ifGenerationMatch:0},metadata:{contentType:metadata.contentType,metadata:{}}}); }
   catch(error) { if(error.code!==412) throw error; }
  }
  await fileMetadata(sealedPath);
  await source.delete({ignoreNotFound:true});
  return sealedPath;
 }
 async function cleanupReviewUploads(paths) {
  if(!paths?.length)return;
  D.requireThat(bucket,'Media storage is unavailable','unavailable');
  try { await Promise.all(paths.map(path=>bucket.file(path).delete({ignoreNotFound:true}))); }
  catch { throw new D.DomainError('unavailable','The review is saved, but photo cleanup is pending. Retry with the same request ID.'); }
 }
 async function activeReviewResult(result){
  const f=result?.feedback;if(!f?.publicId)return true;
  const [current,stored]=await Promise.all([collection('feedback').doc(f.id).get(),collection('social').doc('state').get()]);
  return current.data()?.publicId===f.publicId&&!S.normalize(stored.data()).deleted.includes(f.publicId);
 }
 async function read(uid,data,maintainer=false){const now=clock();const action=data.action;
  if(action==='resolveRequest') {
   D.requireThat(typeof data.requestIdToResolve==='string'&&/^[A-Za-z0-9_-]{8,128}$/.test(data.requestIdToResolve),'Invalid request ID','invalid-argument');
   const op=(await collection('requests').doc(D.operationId(uid,data.requestIdToResolve)).get()).data();
   if(!op)return {status:'UNKNOWN'};
   if(op.action==='submitCheckIn'){const current=await currentCheckIn(uid,op.eventId);if(!current||current.registrationKey!==op.result?.receipt?.registrationKey)return {status:'INVALIDATED',action:op.action};}
   if(op.action==='submitFeedback'){if(!await activeReviewResult(op.result))return {status:'INVALIDATED',action:op.action};await cleanupReviewUploads(op.reviewUploads);}
   return {status:'COMMITTED',action:op.action,result:op.result};
  }
  const social=await db.runTransaction(async tx=>{
   const ref=collection('social').doc('state'),previous=(await tx.get(ref)).data(),s=S.normalize(previous);
   const feedback=await all('feedback',tx);const questions=await all('questions',tx);
   S.migrate(s,questions);for(const f of feedback)if(f.details?.anonymous)S.alias(s,f.eventId,f.userId);
   if(JSON.stringify(s)!==JSON.stringify(previous))tx.set(ref,s);return s;
  });
  if(action==='socialRead'){
   const [questions,feedback,archive,users,events]=await Promise.all(['questions','feedback','feedbackHistory','users','events'].map(n=>all(n)));
   return S.engine(social,S.contents(questions,[...feedback,...archive]),users,events,uid,maintainer,now).read(data);
  }
  if(action==='snapshot')return db.runTransaction(async tx=>{
   const names=['users','events','registrations','announcements','questions'];
   const [users,events,registrations,announcements,questions,notifications,checkIns,feedback,archive,socialDoc]=await Promise.all([...names.map(n=>all(n,tx)),all('notifications',tx,collection('notifications').where('userId','==',uid)),all('checkIns',tx),all('feedback',tx),all('feedbackHistory',tx),tx.get(collection('social').doc('state'))]);
   const latestSocial=S.normalize(socialDoc.data());S.migrate(latestSocial,questions);
   const roots=S.contents(questions,[...feedback,...archive]);
   const visibleNotifications=notifications.map(n=>S.projectNotice(n,latestSocial,roots,users,events)).filter(Boolean);
   const mine=registrations.filter(r=>r.userId===uid);const memberships=new Set(mine.filter(D.active).map(r=>r.eventId));const organized=new Set(events.filter(e=>e.organizerId===uid).map(e=>e.id));
   const roster=registrations.filter(r=>r.userId===uid||D.active(r)).map(r=>({id:r.id,eventId:r.eventId,userId:r.userId,status:r.status,joinedAt:r.joinedAt,updatedAt:r.updatedAt,queueOrder:r.queueOrder}));
   return {serverNow:now,snapshot:{users:users.map(u=>D.publicProfile(u,uid)),currentUserId:users.some(u=>u.id===uid)?uid:null,events:events.map(D.eventFields),registrations:roster,announcements:announcements.filter(a=>memberships.has(a.eventId)||organized.has(a.eventId)),questions,eventRatings:events.map(e=>({eventId:e.id,...R.summary(e,feedback,registrations,checkIns,latestSocial.deleted,now)})),notifications:visibleNotifications,checkIns:checkIns.filter(c=>(c.userId===uid||organized.has(c.eventId))&&D.validCheckIn(c,registrations.find(r=>r.eventId===c.eventId&&r.userId===c.userId),events.find(e=>e.id===c.eventId))),feedback:feedback.filter(f=>f.userId===uid&&!latestSocial.deleted.includes(f.publicId)).map(f=>({id:f.id,eventId:f.eventId,userId:uid,rating:f.rating,comment:'',createdAt:f.createdAt,status:'SUBMITTED'})),sessionRestoring:false,sessionError:null}};
  },{readOnly:true});
  if(action==='mediaRead'){
   const mediaId=D.id(data.mediaId,'media ID');const m=(await collection('media').doc(mediaId).get()).data();D.requireThat(m,'Image not found','not-found');const [fr,er]=await Promise.all([collection('feedback').doc(m.reviewId).get(),collection('events').doc(m.eventId).get()]);const f=fr.data(),e=er.data();D.requireThat(f&&e&&!social.deleted.includes(f.publicId)&&(f.details?.photos??[]).includes(mediaId)&&!S.blocked(social,uid,f.userId)&&(!S.privateReview(f)||uid===f.userId||uid===e.organizerId),'You cannot view this image','permission-denied');const meta=await fileMetadata(m.path);const [bytes]=await bucket.file(m.path).download();return {base64:bytes.toString('base64'),contentType:meta.contentType};
  }
  const eventId=D.id(data.eventId,'event ID'),event=(await collection('events').doc(eventId).get()).data();D.requireThat(event,'Event not found','not-found');
  if(action==='eventInfo'){
   const [c,f,p,host]=await Promise.all([currentCheckIn(uid,eventId),collection('feedback').doc(`${eventId}_${uid}`).get(),collection('users').doc(uid).get(),currentCheckIn(event.organizerId,eventId)]);
   return {serverNow:now,checkInReady:!!host,receipt:receipt(c),replacesReviewId:f.exists&&social.deleted.includes(f.data().publicId)?f.data().publicId:'',ownFeedback:f.exists&&!social.deleted.includes(f.data().publicId)?{...f.data(),authorName:f.data().details?.anonymous?S.alias(social,eventId,uid):(p.data()?.displayName??'Participant'),avatarUri:f.data().details?.anonymous?'':(p.data()?.avatarUri??'')}:null};
  }
  if(action==='resolveCheckIn'){
   D.id(data.attemptId,'attempt ID');const r=(await collection('registrations').doc(`${eventId}_${uid}`).get()).data();
   if(!r||r.status!=='CONFIRMED'||data.registrationKey!==D.registrationKey(r)||event.status==='CANCELLED')return {status:'NOT_SUBMITTED'};
   const c=await currentCheckIn(uid,eventId);
   return c&&c.registrationKey===data.registrationKey?{status:'SAVED',receipt:receipt(c)}:{status:'UNKNOWN'};
  }
  if(action==='attendance'){
   D.requireThat(event.organizerId===uid,'Only the organizer can view attendance','permission-denied');
   const [regs,checked,users]=await Promise.all([all('registrations',null,collection('registrations').where('eventId','==',eventId)),all('checkIns',null,collection('checkIns').where('eventId','==',eventId)),all('users')]);
   return {attendees:regs.filter(r=>r.status==='CONFIRMED').map(r=>{const c=checked.find(c=>c.userId===r.userId&&D.validCheckIn(c,r,event));const u=users.find(u=>u.id===r.userId);return {userId:r.userId,name:u?.displayName??'Participant',avatarUri:u?.avatarUri??'',checkedInAt:c?.checkedInAt??null,organizer:r.userId===event.organizerId};})};
  }
  if(action==='reviews'){
   return db.runTransaction(async tx=>{
    const [eventDoc,feedback,users,regs,checked,socialDoc]=await Promise.all([tx.get(collection('events').doc(eventId)),all('feedback',tx,collection('feedback').where('eventId','==',eventId)),all('users',tx),all('registrations',tx,collection('registrations').where('eventId','==',eventId)),all('checkIns',tx,collection('checkIns').where('eventId','==',eventId)),tx.get(collection('social').doc('state'))]);
    const currentEvent=eventDoc.data();D.requireThat(currentEvent,'Event not found','not-found');const currentSocial=S.normalize(socialDoc.data());
    return {ratingSummary:R.summary(currentEvent,feedback,regs,checked,currentSocial.deleted,clock()),reviews:feedback.map(f=>visibleReview(f,uid,currentEvent.organizerId,users,currentSocial)).filter(Boolean).sort((a,b)=>b.createdAt-a.createdAt)};
   },{readOnly:true});
  }
  D.fail('Unknown read action');
 }
 async function service(request){
  D.requireThat(request.auth?.uid,'Sign in first','unauthenticated');const uid=request.auth.uid,data=request.data;D.requireThat(data&&typeof data==='object'&&!Array.isArray(data),'Request must be an object','invalid-argument');const action=data.action;D.requireThat(typeof action==='string'&&(READ_ACTIONS.has(action)||MUTATIONS.has(action)),'Unknown action','invalid-argument');D.requireThat(JSON.stringify(data).length<=30000,'Request too large','invalid-argument');
  for(const field of ['uid','userId','actor','organizerId','serverNow','now','createdAt','updatedAt','status'])D.requireThat(!(field in data),'Identity and server-owned fields cannot be supplied','invalid-argument');
  if(READ_ACTIONS.has(action))return read(uid,data,request.auth.token?.maintainer===true);
  D.requireThat(typeof data.requestId==='string'&&/^[A-Za-z0-9_-]{8,128}$/.test(data.requestId),'Invalid request ID','invalid-argument');
  const {requestId,...body}=data,opId=D.operationId(uid,requestId),opRef=collection('requests').doc(opId),fingerprint=D.hash(body);
  const replay=async()=>{const previous=await opRef.get();if(!previous.exists)return null;D.requireThat(previous.data().hash===fingerprint,'Request ID was already used for different content','already-exists');return {result:previous.data().result};};
  // A committed request replays independently of current time, account profile, or media availability.
  const finish=async result=>{
   if(action==='submitCheckIn'){
    const current=await currentCheckIn(uid,data.eventId);
    D.requireThat(current&&current.registrationKey===data.registrationKey,'This check-in belongs to an earlier registration. Scan again.');
    return {...result,receipt:receipt(current)};
   }
   if(action==='submitFeedback'){D.requireThat(await activeReviewResult(result),'This review has been deleted or replaced. Refresh and submit a new review.');await cleanupReviewUploads(D.feedbackDetails(data.details,uid).photos);}
   return result;
  };
  const saved=await replay(); if(saved)return finish(saved.result);
  const generatedId=D.randomUUID(),codeRaw=`GOGETHER-CLOUD-2|${randomBytes(32).toString('base64url')}`;
  const sealedPhotos=new Map();
  if(['ensureProfile','updateProfile'].includes(action)){const details=D.profileDetails(data,uid);await Promise.all([details.avatarUri,details.coverUri].filter(Boolean).map(fileMetadata));}
  if(['createEvent','updateEvent'].includes(action)&&data.draft?.imageUri){D.draft(data.draft,clock(),uid,action==='updateEvent');await fileMetadata(data.draft.imageUri);}
  if(action==='submitFeedback'){const details=D.feedbackDetails(data.details,uid);await Promise.all(details.photos.map(async path=>sealedPhotos.set(path,await sealReview(path,uid))));}
  const run=()=>db.runTransaction(async tx=>{
   const previous=await tx.get(opRef);if(previous.exists){D.requireThat(previous.data().hash===fingerprint,'Request ID was already used for different content','already-exists');return previous.data().result;}
   const now=clock();const ownRef=collection('users').doc(uid),own=(await tx.get(ownRef)).data();
   const social=S.normalize((await tx.get(collection('social').doc('state'))).data());
   const writes=new Map();let result={ok:true};let notificationCount=0;
   const put=(name,key,value)=>writes.set(`${name}/${key}`,value);
   const notify=(userId,eventId,type,content,extra={})=>{const nid=`${opId}_${notificationCount++}`;put('notifications',nid,{id:nid,userId,eventId,type,content,createdAt:now,isRead:false,announcementId:null,questionId:null,...extra});};
   if(action==='ensureProfile'||action==='updateProfile'){
    if(action==='ensureProfile'&&own)result={user:own};else {D.requireThat(action==='ensureProfile'||own,'Create your profile first','failed-precondition');const user=D.profile(data,uid,request.auth.token?.email,own?.createdAt??now,own);put('users',uid,user);result={user};}
   }else{
    D.requireThat(own,'Create your profile first','failed-precondition');
    if(action==='socialMutate'){
     const [questions,feedback,archive,users,events]=await Promise.all(['questions','feedback','feedbackHistory','users','events'].map(n=>all(n,tx)));
     S.migrate(social,questions);const roots=S.contents(questions,[...feedback,...archive]);
     result=S.engine(social,roots,users,events,uid,request.auth.token?.maintainer===true,now).mutate(data,generatedId);
     if(data.operation==='reply'){
      const c=social.replies.find(c=>c.id===result.id),r=roots.find(r=>r.id===c.rootId),target=[...roots,...social.replies].find(t=>t.id===c.targetId),host=events.find(e=>e.id===c.eventId).organizerId;
      for(const n of S.deliveries(social,c,r,target,host)){const safe=S.projectNotice(n,social,roots,users,events);notify(n.userId,n.eventId,n.type,n.content,{contentId:n.contentId,rootId:n.rootId,questionId:n.questionId,actorName:safe.actorName,actorAvatarUri:safe.actorAvatarUri});}
     }
     D.requireThat(JSON.stringify(social).length<800000,'Social storage limit reached; archive or paginate before growing this deployment','resource-exhausted');
     put('social','state',social);
    }else if(action==='markRead'||action==='markAllRead'){
     const docs=action==='markRead'?[await tx.get(collection('notifications').doc(D.id(data.notificationId))) ]:(await tx.get(collection('notifications').where('userId','==',uid).where('isRead','==',false).limit(401))).docs;
     D.requireThat(docs.length<=400,'Read notifications in smaller batches','resource-exhausted');for(const doc of docs){const n=doc.data();D.requireThat(n&&n.userId===uid,'Notification not found','permission-denied');put('notifications',doc.id,{...n,isRead:true});}
    }else if(action==='createEvent'){
     const d=D.draft(data.draft,now,uid);D.requireThat(d.meetingAt===null||d.meetingAt>now,'Choose a future meeting time');const event={id:generatedId,organizerId:uid,...d,createdAt:now,updatedAt:now,status:'OPEN',demoCheckInOpen:false,nextOrder:1,revision:1};const rid=`${event.id}_${uid}`;put('events',event.id,event);put('registrations',rid,{id:rid,eventId:event.id,userId:uid,status:'CONFIRMED',joinedAt:now,updatedAt:now,queueOrder:1});result={event:D.eventFields(event)};
    }else{
     let question=null;
     if(action==='answerQuestion'){question=(await tx.get(collection('questions').doc(D.id(data.questionId)))).data();D.requireThat(question,'Question not found','not-found');}
     const eventId=question?.eventId??D.id(data.eventId,'event ID'),event=(await tx.get(collection('events').doc(eventId))).data();D.requireThat(event,'Event not found','not-found');
     const regs=await all('registrations',tx,collection('registrations').where('eventId','==',eventId));const active=regs.filter(D.active);D.requireThat(active.length<=MAX_ROSTER,'This event exceeds the active registration limit','resource-exhausted');const confirmed=regs.filter(r=>r.status==='CONFIRMED'),waitlist=regs.filter(r=>r.status==='WAITLISTED'&&!S.registrationBlocked(social,event.organizerId,r.userId)).sort((a,b)=>a.queueOrder-b.queueOrder),mine=regs.find(r=>r.userId===uid);let updated={...event,updatedAt:now,revision:(event.revision??0)+1};
     const organizer=()=>D.requireThat(event.organizerId===uid,'Only the organizer can do this','permission-denied');
     const editable=()=>D.requireThat(event.status==='OPEN'&&now<event.startAt,'Only upcoming open events can be edited');
     const open=()=>D.requireThat(D.isOpen(event,now),'This event is closed');
     const allNotice=(type,text,extra={})=>[...new Set(active.map(r=>r.userId))].forEach(u=>notify(u,eventId,type,text,extra));
     const regPut=r=>put('registrations',r.id,r);
     if(action==='updateEvent'){
      organizer();editable();const d=D.draft(data.draft,now,uid,true);d.meetingAt=d.meetingAt??event.meetingAt??null;D.requireThat(d.meetingAt===null||d.meetingAt<=d.endAt,'Meeting time is after the new end time');D.requireThat(d.meetingAt===event.meetingAt||d.meetingAt===null||d.meetingAt>now,'Choose a future meeting time');D.requireThat(d.capacity>=confirmed.length,'Capacity cannot be lower than confirmed participants');updated={...updated,...d,meetingLocation:event.meetingLocation??null,meetingNotes:event.meetingNotes??''};const changes=D.eventChanges(event,updated);if(changes.length)allNotice(event.meetingAt!==updated.meetingAt?'MEETING_UPDATED':'EVENT_UPDATED',`Activity updated: ${updated.title}\n${changes.join('\n')}`);
      if(D.accepts(updated,now))for(const r of waitlist.slice(0,d.capacity-confirmed.length)){regPut({...r,status:'CONFIRMED',updatedAt:now});notify(r.userId,eventId,'PROMOTED',`You're in! More places are available: ${updated.title}`);}result={event:D.eventFields(updated)};
     }else if(action==='join'){
      D.requireThat(!S.registrationBlocked(social,event.organizerId,uid),'The organizer has restricted new registrations from this account','permission-denied');D.requireThat(D.accepts(event,now),'Registration has closed or this event has started');D.requireThat(!mine||!D.active(mine),'You already have a place or a waitlist entry','already-exists');D.requireThat(active.length<MAX_ROSTER,'This event has reached its 200-person registration and waitlist limit','resource-exhausted');const order=(event.nextOrder??Math.max(0,...regs.map(r=>r.queueOrder)))+1;updated.nextOrder=order;const rid=`${eventId}_${uid}`,registration={id:rid,eventId,userId:uid,status:confirmed.length<event.capacity?'CONFIRMED':'WAITLISTED',joinedAt:now,updatedAt:now,queueOrder:order};regPut(registration);result={registration};
     }else if(action==='cancelRegistration'){
      D.requireThat(uid!==event.organizerId,'Organizer must cancel the event instead');editable();D.requireThat(mine&&D.active(mine),'No active registration to cancel');regPut({...mine,status:'CANCELLED',updatedAt:now});if(mine.status==='CONFIRMED'&&D.accepts(event,now)&&waitlist.length){const promoted=waitlist[0];regPut({...promoted,status:'CONFIRMED',updatedAt:now});notify(promoted.userId,eventId,'PROMOTED',`You're in! A place opened up: ${event.title}`);}
     }else if(action==='cancelEvent'){
      organizer();editable();updated.status='CANCELLED';for(const r of active)regPut({...r,status:'CANCELLED',updatedAt:now});allNotice('EVENT_CANCELLED',`${event.title} has been cancelled by the organizer.`);
     }else if(action==='postAnnouncement'){
      organizer();open();const content=D.str(data.content,1,100,'Announcement');put('announcements',generatedId,{id:generatedId,eventId,authorId:uid,content,createdAt:now});allNotice('ANNOUNCEMENT',content,{announcementId:generatedId});result={ok:true,id:generatedId};
     }else if(action==='askQuestion'){
      open();D.requireThat(!S.blocked(social,uid,event.organizerId),'This interaction is blocked','permission-denied');D.requireThat(uid!==event.organizerId,'Use an announcement as the organizer');const content=D.str(data.content,1,100,'Question');const questions=await all('questions',tx,collection('questions').where('eventId','==',eventId));D.requireThat(!questions.some(q=>q.authorId===uid&&q.content===content),'You have already posted this question','already-exists');put('questions',generatedId,{id:generatedId,eventId,authorId:uid,content,createdAt:now,answer:null,answeredBy:null,answeredAt:null});notify(event.organizerId,eventId,'QUESTION_ASKED',S.interactionMessage('QUESTION_ASKED'),{questionId:generatedId,contentId:generatedId,rootId:generatedId,actorName:own.displayName,actorAvatarUri:own.avatarUri??''});result={ok:true,id:generatedId};
     }else if(action==='answerQuestion'){
      organizer();open();S.migrate(social,[question]);
      const roots=S.contents([question],[]),users=[own,{id:question.authorId,displayName:'Participant'}];
      result=S.engine(social,roots,users,[event],uid,false,now).mutate({operation:'reply',contentId:question.id,content:data.content},generatedId);
      const reply=social.replies.find(r=>r.id===result.id);
      // Keep the old snapshot contract while preserving every answer in the new thread.
      put('questions',question.id,{...question,answer:reply.content,answeredBy:uid,answeredAt:now,
       replyHistory:[...S.questionHistory(question),{id:reply.id,authorId:uid,content:reply.content,createdAt:now}]});
      put('social','state',social);
      notify(question.authorId,eventId,'QUESTION_ANSWERED',S.interactionMessage('QUESTION_ANSWERED'),{questionId:question.id,contentId:result.id,rootId:question.id,actorName:own.displayName,actorAvatarUri:own.avatarUri??''});

     }else if(action==='updateMeeting'){
      organizer();open();const meetingAt=D.integer(data.meetingAt,now+1,event.endAt,'meeting time'),meetingLocation=D.str(data.location,3,150,'Meeting location'),meetingNotes=D.str(data.notes??'',0,100,'Meeting notes');updated={...updated,meetingAt,meetingLocation,meetingNotes};if(event.meetingAt!==meetingAt||event.meetingLocation!==meetingLocation||event.meetingNotes!==meetingNotes)allNotice('MEETING_UPDATED',`Meeting information updated: ${event.title}. Open the event for the latest time and location.`);
     }else if(action==='issueCheckInCode'){
      organizer();D.windowCheck(event,now);
      D.requireThat(mine?.status==='CONFIRMED','Your organizer registration is unavailable');
      const cid=`${eventId}_${uid}`,old=(await tx.get(collection('checkIns').doc(cid))).data();
      const expiresAt=Math.min(now+30000,event.endAt,event.startAt+1800001);
      const code={eventId,eventKey:D.bindingKey(event),issuedAt:now,expiresAt};
      put('checkInCodes',D.hash(codeRaw),code);
      let host=old;
      if(!D.validCheckIn(old,mine,event)){
       D.requireThat(data.confirmArrival===true,'Confirm your arrival before showing the code');
       if(old)put('checkInHistory',D.hash([cid,old.registrationKey,old.checkedInAt]),old);
       host={id:cid,eventId,userId:uid,checkedInAt:now,status:'HOST_CONFIRMED',registrationKey:D.registrationKey(mine)};
       put('checkIns',cid,host);
      }
      result={code:codeRaw,expiresAt,serverNow:now,receipt:receipt(host)};
     }else if(action==='submitCheckIn'){
      D.requireThat(uid!==event.organizerId,'Confirm your arrival when displaying the code');
      D.requireThat(event.status!=='CANCELLED'&&mine?.status==='CONFIRMED','Only confirmed participants can check in','permission-denied');
      D.requireThat(data.registrationKey===D.registrationKey(mine),'Your registration changed. Scan again.');
      const cid=`${eventId}_${uid}`,existing=(await tx.get(collection('checkIns').doc(cid))).data();
      if(D.validCheckIn(existing,mine,event)){result={receipt:receipt(existing)};}else{
       D.windowCheck(event,now);const raw=D.str(data.code,20,512,'Check-in code');
       D.requireThat(raw.startsWith('GOGETHER-CLOUD-2|'),'Invalid or old check-in code','invalid-argument');
       const code=(await tx.get(collection('checkInCodes').doc(D.hash(raw)))).data();
       D.requireThat(code&&code.eventId===eventId&&code.eventKey===D.bindingKey(event),'Invalid code or event details have changed');
       D.requireThat(code.issuedAt<=now&&now<code.expiresAt,'This code has expired');
       if(existing)put('checkInHistory',D.hash([cid,existing.registrationKey,existing.checkedInAt]),existing);
       const checkIn={id:cid,eventId,userId:uid,checkedInAt:now,status:'QR_RECORDED',registrationKey:D.registrationKey(mine)};
       put('checkIns',cid,checkIn);result={receipt:receipt(checkIn)};
      }
     }else if(action==='submitFeedback'){
      D.requireThat(event.status==='COMPLETED'||(event.status==='OPEN'&&now>=event.endAt),'Feedback opens after the event ends');D.requireThat(mine?.status==='CONFIRMED','Only confirmed participants can leave feedback','permission-denied');const attendance=(await tx.get(collection('checkIns').doc(`${eventId}_${uid}`))).data();D.requireThat(D.validCheckIn(attendance,mine,event),'Check in at this event before leaving a review','permission-denied');const fid=`${eventId}_${uid}`,previousFeedback=(await tx.get(collection('feedback').doc(fid))).data();D.requireThat(!previousFeedback||social.deleted.includes(previousFeedback.publicId),'Feedback has already been submitted','already-exists');const details=D.feedbackDetails(data.details,uid);D.requireThat(uid!==event.organizerId||!details.anonymous,'Organizers must use a named review');const mediaIds=details.photos.map(path=>{const mediaId=D.randomUUID();put('media',mediaId,{id:mediaId,path:sealedPhotos.get(path),ownerId:uid,eventId,reviewId:fid});return mediaId;});const feedback={id:fid,publicId:generatedId,eventId,userId:uid,rating:D.integer(data.rating,1,5,'rating'),comment:D.str(data.comment??'',0,100,'Comment'),createdAt:now,status:'SUBMITTED',eligibilityVersion:2,registrationKey:D.registrationKey(mine),organizerReview:uid===event.organizerId,details:{...details,photos:mediaIds}};if(previousFeedback)put('feedbackHistory',previousFeedback.publicId,previousFeedback);if(details.anonymous)S.alias(social,eventId,uid);put('social','state',social);put('feedback',fid,feedback);
      const root=S.contents([],[feedback])[0];for(const n of S.deliveries(social,root,root,root,event.organizerId))notify(n.userId,eventId,n.type,n.content,{contentId:root.id,rootId:root.id,actorName:details.anonymous?S.alias(social,eventId,uid):own.displayName,actorAvatarUri:details.anonymous?'':(own.avatarUri??'')});
      result={feedback};
     }else D.fail('Unknown action');
     put('events',eventId,updated);
    }
   }
   D.requireThat(writes.size<490,'Operation is too large','resource-exhausted');for(const[path,value]of writes)tx.set(db.doc(path),value);
   tx.set(opRef,{uid,action,eventId:data.eventId??null,hash:fingerprint,createdAt:now,result,...(action==='submitFeedback'?{reviewUploads:D.feedbackDetails(data.details,uid).photos}:{})});tx.set(db.doc('sync/global'),{updatedAt:now,nonce:opId});return result;
  },{maxAttempts:8});
  return finish(await runIdempotentTransaction(run,replay));
 }
 return service;
}
module.exports={createService,visibleReview,READ_ACTIONS,MUTATIONS,isTransactionRetryable,runIdempotentTransaction};
