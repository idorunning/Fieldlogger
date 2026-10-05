import test from 'node:test';
import assert from 'node:assert/strict';
import {serviceKeyOwner} from '../lib/key-maintenance-policy';

test('legacy service-key maintenance requires both configured trusted account IDs',()=>{
  assert.equal(serviceKeyOwner('operator',{SHARED_OPENAI_KEY_OWNER_ID:'operator',COMMUNITY_ADMIN_USER_ID:'operator'}),true);
  for(const config of [{},{SHARED_OPENAI_KEY_OWNER_ID:'operator'},{COMMUNITY_ADMIN_USER_ID:'operator'},{SHARED_OPENAI_KEY_OWNER_ID:'other',COMMUNITY_ADMIN_USER_ID:'operator'},{SHARED_OPENAI_KEY_OWNER_ID:'operator',COMMUNITY_ADMIN_USER_ID:'other'}])assert.equal(serviceKeyOwner('operator',config),false);
  assert.equal(serviceKeyOwner('new-account',{SHARED_OPENAI_KEY_OWNER_ID:'operator',COMMUNITY_ADMIN_USER_ID:'operator'}),false);
  assert.equal(serviceKeyOwner('',{SHARED_OPENAI_KEY_OWNER_ID:'',COMMUNITY_ADMIN_USER_ID:''}),false);
});
