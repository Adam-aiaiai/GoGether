const {test} = require('node:test');
const assert = require('node:assert/strict');
const {validate, fingerprint} = require('./validation');
const base = {eventId: 'event-1', requestId: 'request-123'};
test('announcement validation trims content and rejects empty or oversized text', () => {
  assert.equal(validate({...base, action:'announcement', content:' hello '}, 1).content,'hello');
  for (const content of ['', '   ', 'a'.repeat(501), null])
    assert.throws(() => validate({...base, action:'announcement', content},1));
});
test('meeting time, location, notes and request identifiers are validated', () => {
  const data = {...base, action:'meeting', meetingAt:200, location:'East gate', notes:''};
  assert.equal(validate(data,100).meetingAt,200);
  for (const patch of [{meetingAt:100},{meetingAt:NaN},{location:'x'},{notes:'x'.repeat(1001)},{eventId:'a/b'},{requestId:''}])
    assert.throws(() => validate({...data,...patch},100));
});
test('idempotency fingerprint distinguishes changed content', () => {
  const a=validate({...base,action:'announcement',content:'hello'},1);
  const b=validate({...base,action:'announcement',content:'world'},1);
  assert.equal(fingerprint(a),fingerprint({...a})); assert.notEqual(fingerprint(a),fingerprint(b));
});
