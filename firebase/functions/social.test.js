'use strict';
const {test}=require('node:test');const assert=require('node:assert/strict');const S=require('./social');
const users=['host','a','b','mod'].map(id=>({id,displayName:id}));const events=[{id:'e',organizerId:'host',status:'OPEN',endAt:100}];
const roots=[{id:'q',rootId:'q',targetId:'q',eventId:'e',authorId:'a',kind:'QUESTION',content:'Question',createdAt:1},{id:'f',rootId:'f',targetId:'f',eventId:'e',authorId:'a',kind:'REVIEW',content:'Review',anonymous:true,createdAt:2,rating:5}];
function engine(s,user,mod=false,r=roots){return S.engine(s,r,users,events,user,mod,10);}
test('conversation follows target IDs and deletion preserves descendants',()=>{const s=S.empty();engine(s,'host').mutate({operation:'reply',contentId:'q',content:'Answer'},'r1');engine(s,'a').mutate({operation:'reply',contentId:'r1',content:'Again'},'r2');assert.equal(s.replies[1].targetId,'r1');assert.throws(()=>engine(s,'b').mutate({operation:'reply',contentId:'q',content:'Other'},'x'));engine(s,'a').mutate({operation:'delete',contentId:'q'},'del');assert.equal(engine(s,'a').read({rootId:'q'}).items.length,3);assert.throws(()=>engine(s,'host').mutate({operation:'reply',contentId:'r1',content:'Closed'},'x'));});
test('private anonymous replies inherit visibility and stable alias without author IDs',()=>{const s=S.empty(),r=[{...roots[1],private:true}];engine(s,'a',false,r).mutate({operation:'reply',contentId:'f',content:'More'},'r');const rows=engine(s,'host',false,r).read({rootId:'f'}).items;assert.equal(rows[0].authorName,rows[1].authorName);assert.ok(rows.every(r=>!('authorId'in r)&&!('userId'in r)));assert.throws(()=>engine(s,'b',false,r).read({rootId:'f'}));assert.notEqual(S.alias(s,'e','a'),S.alias(s,'e','b'));});
test('likes are idempotent and block resolution stays opaque',()=>{const s=S.empty();for(let i=0;i<3;i++)engine(s,'b').mutate({operation:'like',contentId:'f',liked:true},'l');assert.equal(s.likes.length,1);engine(s,'b').mutate({operation:'blockUser',contentId:'f',kind:'PERSONAL'},'block');assert.ok(S.blocked(s,'a','b'));assert.throws(()=>engine(s,'b').mutate({operation:'like',contentId:'f',liked:true},'l'));const b=engine(s,'b').read({}).blocks[0];assert.ok(!('target'in b));assert.match(b.label,/Anonymous/);});
test('reports require maintainer claim and record outcome; organizer cannot remove review',()=>{const s=S.empty();engine(s,'b').mutate({operation:'report',contentId:'f',reason:'Abuse'},'rep');assert.equal(s.deleted.length,0);assert.throws(()=>engine(s,'host').mutate({operation:'resolveReport',reportId:'rep',resolution:'REMOVED',outcome:'Dislike'},'mod'));engine(s,'mod',true).mutate({operation:'resolveReport',reportId:'rep',resolution:'REMOVED',outcome:'Verified'},'mod');assert.equal(s.reports[0].moderator,'mod');assert.deepEqual(s.deleted,['f']);});
test('registration restriction does not create a personal block',()=>{const s=S.empty();engine(s,'host').mutate({operation:'blockUser',targetUserId:'a',kind:'REGISTRATION'},'block');assert.ok(S.registrationBlocked(s,'host','a'));assert.ok(!S.blocked(s,'host','a'));});
test('moderation includes optional review text without exposing author identifiers in the queue',()=>{const s=S.empty();const content=S.contents([],[{publicId:'full-review',eventId:'e',userId:'a',comment:'Summary',createdAt:1,rating:4,details:{anonymous:true,positiveComment:'Friendly',improvementComment:'More time'}}]);const reporter=engine(s,'b',false,content);reporter.mutate({operation:'report',contentId:'full-review',reason:'Review all fields'},'rep');const r=engine(s,'mod',true,content).read({}).reports[0];assert.match(r.content,/Friendly/);assert.match(r.content,/More time/);assert.ok(!('authorId'in r));assert.match(r.authorName,/Anonymous participant/);});
test('interaction recipients deduplicate targets and exclude actor, blocked accounts and private outsiders',()=>{
 const s=S.empty(),r={id:'r',rootId:'f',targetId:'f',eventId:'e',authorId:'b',kind:'REPLY'},next={...r,id:'n',targetId:'r',authorId:'mod'};
 assert.deepEqual(S.deliveries(s,next,roots[1],r,'host').map(n=>n.userId),['a','b']);
 assert.deepEqual(S.deliveries(s,r,roots[1],roots[1],'host').map(n=>n.userId),['a']);
 assert.deepEqual(S.deliveries(s,next,{...roots[1],private:true},r,'host').map(n=>n.userId),['a']);
 s.blocks.push({kind:'PERSONAL',owner:'a',target:'mod'});assert.deepEqual(S.deliveries(s,next,roots[1],r,'host').map(n=>n.userId),['b']);
 assert.equal(S.deliveries(s,{...roots[1],authorId:'host'},roots[1],roots[1],'host').length,0);
});
test('notice projection redacts anonymous identities and unavailable content but keeps activity changes',()=>{
 const s=S.empty(),n={userId:'host',eventId:'e',type:'REVIEW_POSTED',content:'quoted body',contentId:'f',rootId:'f'};
 const visible=S.projectNotice(n,s,roots,users,events);assert.match(visible.actorName,/Anonymous participant/);assert.equal(visible.actorAvatarUri,'');assert.ok(!('actorId' in visible));assert.ok(!('authorId' in visible));
 s.deleted.push('f');assert.match(S.projectNotice(n,s,roots,users,events).content,/deleted/);
 assert.match(S.projectNotice({...n,userId:'b'},S.empty(),[{...roots[1],private:true}],users,events).content,/no longer available/);
 s.blocks.push({kind:'PERSONAL',owner:'host',target:'a'});assert.equal(S.projectNotice(n,s,roots,users,events),null);
 const update={...n,type:'EVENT_CANCELLED'};assert.deepEqual(S.projectNotice(update,s,roots,users,events),update);
});

test('legacy answers migrate once and explicit answer history retains every stable ID',()=>{
 const s=S.empty(),q={id:'old-q',eventId:'e',authorId:'a',createdAt:1,answer:'First answer',answeredBy:'host',answeredAt:2};
 S.migrate(s,[q]);S.migrate(s,[q]);assert.equal(s.replies.length,1);
 const history=[...S.questionHistory(q),{id:'new-answer',authorId:'host',content:'Second answer',createdAt:3}];
 const updated={...q,answer:'Second answer',answeredAt:3,replyHistory:history};
 S.migrate(s,[updated]);S.migrate(s,[updated]);
 assert.deepEqual(s.replies.map(r=>[r.id,r.content]),[['answer-old-q','First answer'],['new-answer','Second answer']]);
 assert.equal(s.replies.every(r=>r.rootId===q.id&&r.targetId===q.id),true);
});
test('legacy review projection defaults absent details and keeps unknown visibility private',()=>{
 const {visibleReview}=require('./service');
 const f={id:'stored',publicId:'public',eventId:'e',userId:'a',rating:4,comment:'Old comment',createdAt:1};
 assert.equal(visibleReview(f,'b','host',users).details.visibility,'PUBLIC');
 assert.deepEqual(visibleReview(f,'b','host',users).details.photos,[]);
 const privateRecord={...f,details:{visibility:'FUTURE_PRIVATE',anonymous:true}};
 assert.equal(visibleReview(privateRecord,'b','host',users),null);
 assert.equal(S.contents([],[privateRecord])[0].private,true);
 assert.equal(visibleReview(privateRecord,'host','host',users).details.visibility,'ORGANIZER_ONLY');
 assert.match(visibleReview(privateRecord,'host','host',users).authorName,/Anonymous/);
 assert.equal(privateRecord.details.visibility,'FUTURE_PRIVATE');
});
test('question previews count only visible non-deleted organizer answers',()=>{
 const s=S.empty();engine(s,'a').mutate({operation:'reply',contentId:'q',content:'More context'},'author-r');
 const list=()=>engine(s,'a').read({eventId:'e'}).items.find(r=>r.id==='q');
 assert.equal(list().latestAnswer,null);
 engine(s,'host').mutate({operation:'reply',contentId:'q',content:'Organizer answer'},'host-r');
 assert.equal(list().latestAnswer.content,'Organizer answer');
 s.deleted.push('host-r');assert.equal(list().latestAnswer,null);
 s.deleted=[];s.blocks.push({owner:'a',target:'host',kind:'PERSONAL'});assert.equal(list().latestAnswer,null);
});
