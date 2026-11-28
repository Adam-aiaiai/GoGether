'use strict';
const D=require('./domain');
const empty=()=>({replies:[],deleted:[],likes:[],blocks:[],reports:[],aliases:[]});
const normalize=s=>({...empty(),...s});
const blocked=(s,a,b)=>s.blocks.some(x=>x.kind==='PERSONAL'&&((x.owner===a&&x.target===b)||(x.owner===b&&x.target===a)));
const registrationBlocked=(s,host,user)=>s.blocks.some(x=>x.kind==='REGISTRATION'&&x.owner===host&&x.target===user);
function alias(s,event,user){let a=s.aliases.find(a=>a.event===event&&a.user===user);if(!a){a={event,user,name:`Anonymous participant ${String(s.aliases.filter(a=>a.event===event).length+1).padStart(2,'0')}`};s.aliases.push(a);}return a.name;}
// Early reviews had no visibility field. Unknown explicit values must stay private.
const privateReview=f=>![undefined,null,'','PUBLIC'].includes(f.details?.visibility);
function contents(questions,feedback){return [...questions.map(q=>({id:q.id,eventId:q.eventId,authorId:q.authorId,content:q.content,createdAt:q.createdAt,kind:'QUESTION',rootId:q.id,targetId:q.id})),...feedback.map(f=>({id:f.publicId,eventId:f.eventId,authorId:f.userId,content:[f.comment,f.details?.positiveComment?`Positive: ${f.details.positiveComment}`:'',f.details?.improvementComment?`Could improve: ${f.details.improvementComment}`:''].filter(Boolean).join('\n'),createdAt:f.createdAt,kind:'REVIEW',rootId:f.publicId,targetId:f.publicId,anonymous:!!f.details?.anonymous,private:privateReview(f),rating:f.rating}))];}
function questionHistory(q){return q.replyHistory?.length?q.replyHistory:q.answer?[{id:`answer-${q.id}`,authorId:q.answeredBy,content:q.answer,createdAt:q.answeredAt??q.createdAt}]:[];}
function migrate(s,questions){for(const q of questions)for(const r of questionHistory(q))if(!s.replies.some(existing=>existing.id===r.id))s.replies.push({...r,eventId:q.eventId,kind:'REPLY',rootId:q.id,targetId:q.id});}
const interactionTypes=new Set(['QUESTION_ASKED','QUESTION_ANSWERED','QUESTION_REPLIED','REVIEW_POSTED','REVIEW_REPLIED']);
function interactionMessage(type){return ({QUESTION_ASKED:'Asked a question about your event.',QUESTION_ANSWERED:'Replied in an event question.',QUESTION_REPLIED:'Replied in an event question.',REVIEW_POSTED:'Reviewed your event.',REVIEW_REPLIED:'Replied in an event review.'})[type]??'Activity update.';}
function deliveries(s,c,r,target,organizer){
 const receivers=c.kind==='REPLY'?[r.authorId,target.authorId,...(r.kind==='QUESTION'?[organizer]:[])]:[organizer];
 const type=c.kind==='QUESTION'?'QUESTION_ASKED':c.kind==='REVIEW'?'REVIEW_POSTED':r.kind==='QUESTION'?'QUESTION_REPLIED':'REVIEW_REPLIED';
 return [...new Set(receivers)].filter(u=>u&&u!==c.authorId&&!blocked(s,u,c.authorId)&&!blocked(s,u,r.authorId)&&(!r.private||u===r.authorId||u===organizer))
  .map(userId=>({userId,eventId:c.eventId,type,content:interactionMessage(type),contentId:c.id,rootId:r.id,questionId:r.kind==='QUESTION'?r.id:null}));
}
function projectNotice(n,s,roots,users,events){
 if(!interactionTypes.has(n.type))return n;
 const all=[...roots,...s.replies],id=n.contentId??(n.questionId?(n.type==='QUESTION_ANSWERED'?`answer-${n.questionId}`:n.questionId):null);
 const c=all.find(c=>c.id===id),r=all.find(r=>r.id===(n.rootId??c?.rootId??n.questionId));
 const unavailable=content=>({...n,content,contentId:id,rootId:r?.id??n.rootId??n.questionId??null,actorName:'Activity conversation',actorAvatarUri:''});
 const host=events.find(e=>e.id===r?.eventId)?.organizerId;
 if(!c||!r||!host)return unavailable('This conversation is no longer available.');
 if(blocked(s,n.userId,c.authorId)||blocked(s,n.userId,r.authorId))return null;
 if(r.private&&![r.authorId,host].includes(n.userId))return unavailable('This conversation is no longer available to you.');
 if(s.deleted.includes(r.id)||s.deleted.includes(c.id))return unavailable('This content has been deleted. Existing replies may still be available.');
 const user=users.find(u=>u.id===c.authorId);
 return {...n,content:interactionMessage(n.type),contentId:id,rootId:r.id,actorName:c.anonymous?alias(s,c.eventId,c.authorId):(user?.displayName??'Participant'),actorAvatarUri:c.anonymous?'':(user?.avatarUri??'')};
}
function engine(s,roots,users,events,uid,maintainer,now){
 const all=()=>[...roots,...s.replies];
 const host=c=>events.find(e=>e.id===c.eventId)?.organizerId;
 const root=c=>all().find(r=>r.id===c.rootId);
 const accessible=c=>{const r=root(c);return r&&host(r)&&(!r.private||uid===r.authorId||uid===host(r))&&!blocked(s,uid,r.authorId)&&!blocked(s,uid,c.authorId);};
 const content=id=>{const c=all().find(c=>c.id===id&&accessible(c));D.requireThat(c,'Content unavailable','permission-denied');return c;};
 const label=c=>c.anonymous?alias(s,c.eventId,c.authorId):(users.find(u=>u.id===c.authorId)?.displayName??'Participant');
 const canReply=c=>{const r=root(c),e=events.find(e=>e.id===c.eventId);return !s.deleted.includes(c.id)&&!s.deleted.includes(r.id)&&accessible(c)&&(r.kind!=='QUESTION'||(D.isOpen(e,now)&&[r.authorId,host(r)].includes(uid)));};
 const visible=c=>{const deleted=s.deleted.includes(c.id),r=root(c),target=all().find(t=>t.id===c.targetId);return {id:c.id,rootId:c.rootId,eventId:c.eventId,authorName:label(c),anonymous:!!c.anonymous,own:c.authorId===uid,organizer:!c.anonymous&&c.authorId===host(c),content:deleted?'Content deleted':c.content,createdAt:c.createdAt,deleted,targetName:target?(s.deleted.includes(target.id)||!accessible(target)?'Content unavailable':label(target)):null,private:!!r.private,rating:deleted?0:(c.rating??0),kind:c.kind,canReply:canReply(c),likes:s.likes.filter(l=>l.id===c.id).length,liked:s.likes.some(l=>l.id===c.id&&l.user===uid)};};
 function read(data){
  if(data.rootId){const r=root(content(data.rootId));return {items:[r,...s.replies.filter(c=>c.rootId===r.id).sort((a,b)=>a.createdAt-b.createdAt)].filter(accessible).map(visible)};}
  if(data.eventId)return {items:roots.filter(r=>r.eventId===data.eventId&&accessible(r)).sort((a,b)=>b.createdAt-a.createdAt).map(c=>{
   const answer=c.kind==='QUESTION'&&!s.deleted.includes(c.id)?s.replies.filter(r=>r.rootId===c.id&&r.authorId===host(c)&&!s.deleted.includes(r.id)&&accessible(r)).sort((a,b)=>b.createdAt-a.createdAt)[0]:null;
   return {...visible(c),latestAnswer:answer?visible(answer):null};
  })};
  return {maintainer,blocks:s.blocks.filter(b=>b.owner===uid).map(b=>({id:b.id,kind:b.kind,label:b.label})),reports:s.reports.filter(r=>r.reporter===uid||maintainer).map(r=>{const c=all().find(c=>c.id===r.contentId);return {id:r.id,contentId:r.contentId,reason:r.reason,status:r.status,outcome:r.outcome,resolvedAt:r.resolvedAt??0,...(maintainer?{content:c?.content??'Content unavailable',authorName:c?label(c):'Unavailable'}:{})};})};
 }
 function mutate(data,id){
  const op=data.operation;
  if(op==='unblock'){const b=s.blocks.find(b=>b.id===data.blockId&&b.owner===uid);D.requireThat(b,'Block not found','permission-denied');s.blocks=s.blocks.filter(x=>x!==b);return {ok:true};}
  if(op==='resolveReport'){
   D.requireThat(maintainer,'Only project maintainers can handle reports','permission-denied');const r=s.reports.find(r=>r.id===data.reportId);D.requireThat(r&&r.status==='OPEN','Report unavailable');D.requireThat(['DISMISSED','REMOVED'].includes(data.resolution),'Invalid decision','invalid-argument');r.outcome=D.str(data.outcome,1,100,'Decision reason');r.status=data.resolution;r.moderator=uid;r.resolvedAt=now;if(r.status==='REMOVED'&&!s.deleted.includes(r.contentId))s.deleted.push(r.contentId);return {ok:true};
  }
  if(op==='blockUser'){
   D.requireThat(['PERSONAL','REGISTRATION'].includes(data.kind),'Invalid block type','invalid-argument');const c=data.contentId?content(data.contentId):null,target=c?.authorId??data.targetUserId;
   D.requireThat(target!==uid&&users.some(u=>u.id===target),'Choose another user');if(data.kind==='REGISTRATION')D.requireThat(events.some(e=>e.organizerId===uid),'Only organizers can manage registration restrictions','permission-denied');
   if(!s.blocks.some(b=>b.owner===uid&&b.target===target&&b.kind===data.kind))s.blocks.push({id,owner:uid,target,kind:data.kind,label:c?label(c):users.find(u=>u.id===target).displayName});return {ok:true};
  }
  const c=content(data.contentId),r=root(c);
  if(op==='reply'){
   D.requireThat(canReply(c),'You cannot reply to this content','permission-denied');const anonymous=uid!==host(c)&&(roots.some(r=>r.eventId===c.eventId&&r.authorId===uid&&r.anonymous)||data.anonymous===true);
   const reply={id,eventId:c.eventId,authorId:uid,content:D.str(data.content,1,100,'Reply'),createdAt:now,kind:'REPLY',rootId:r.id,targetId:c.id,anonymous,private:!!r.private};if(anonymous)alias(s,c.eventId,uid);s.replies.push(reply);return {id};
  }
  if(op==='like') {D.requireThat(c.kind==='REVIEW'&&!s.deleted.includes(c.id),'Only active reviews can be liked');D.requireThat(typeof data.liked==='boolean','Specify the desired like state','invalid-argument');s.likes=s.likes.filter(l=>!(l.id===c.id&&l.user===uid));if(data.liked)s.likes.push({id:c.id,user:uid});}
  else if(op==='delete'){D.requireThat(c.authorId===uid,'Only the author can delete this content','permission-denied');if(!s.deleted.includes(c.id))s.deleted.push(c.id);}
  else if(op==='report'){D.requireThat(c.authorId!==uid&&!s.deleted.includes(c.id),'Choose active content from another user');D.requireThat(!s.reports.some(r=>r.reporter===uid&&r.contentId===c.id&&r.status==='OPEN'),'Your report is already awaiting review','already-exists');s.reports.push({id,reporter:uid,contentId:c.id,reason:D.str(data.reason,1,100,'Report reason'),createdAt:now,status:'OPEN',outcome:'',moderator:'',resolvedAt:0});}
  else D.fail('Unknown social action','invalid-argument');return {ok:true};
 }
 return {read,mutate};
}
module.exports={deliveries,projectNotice,interactionMessage,empty,normalize,blocked,registrationBlocked,alias,contents,migrate,privateReview,questionHistory,engine};
