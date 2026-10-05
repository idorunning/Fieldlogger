import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {spawnSync} from 'node:child_process';
import {deflateSync} from 'node:zlib';
import jpeg from 'jpeg-js';
import {AVATAR_RESERVATION_SQL,avatarAllowance,avatarObjectKey,avatarStyleDecision,generatedAvatar,readAvatarDescriptor} from '../lib/avatar-policy';
import {sanitiseAvatarPhoto,sanitiseGeneratedPng} from '../lib/avatar-images';
import {crc32,safeZipName,textChunks,zipChunks,zipStream} from '../lib/zip-stream';

const owner='45bccbf6-6db0-491b-8aa8-dcf6c774ff3c',revision='55bccbf6-6db0-491b-8aa8-dcf6c774ff3c';
test('generated avatar URLs are server-derived and private retry IDs are never exposed',()=>{
  const stored={kind:'generated',revision,requestId:owner};
  assert.deepEqual(readAvatarDescriptor(JSON.stringify(stored),owner,true),{kind:'generated',revision,imageUrl:'/api/avatar/image?revision='+revision});
  const publicAvatar=readAvatarDescriptor(stored,owner,false);assert.ok('imageUrl' in publicAvatar);
  assert.equal(publicAvatar.imageUrl,`/api/social/users/${owner}/avatar/image?revision=${revision}`);
  for(const value of [{kind:'generated',revision:'../original.jpg'},{kind:'generated',revision,imageUrl:'https://attacker.test/source.jpg'},{kind:'generated',revision,photoKey:'another-owner/private.jpg'}]){
    assert.equal(generatedAvatar(value),null);assert.equal('imageUrl' in readAvatarDescriptor(value,owner),false);
  }
  assert.equal(avatarObjectKey(owner,revision),`${owner}/avatar/${revision}.png`);
  assert.throws(()=>avatarObjectKey('../victim',revision));
});
test('avatar allowance has a permanent free period and one renewed paid UTC month',()=>{
  assert.deepEqual(avatarAllowance('free',new Date('2026-12-31T23:59:59Z')),{limit:1,period:'free-lifetime',paid:false});
  assert.equal(avatarAllowance('free',new Date('2027-01-01T00:00:01Z')).period,'free-lifetime');
  assert.deepEqual(avatarAllowance('premium',new Date('2027-01-01T00:00:01Z')),{limit:1,period:'2027-01',paid:true});
});
test('atomic SQLite reservation caps repeated attempts without a quota reset at lease expiry',()=>{
  const db=new DatabaseSync(':memory:');db.exec('CREATE TABLE avatar_generations(user_id TEXT,period TEXT,used INTEGER,locked_until INTEGER,token TEXT,PRIMARY KEY(user_id,period))');
  const statement=db.prepare(AVATAR_RESERVATION_SQL),reserve=(period:string,token:string,now=1000)=>statement.get(owner,period,now+180000,token,1,now,token);
  assert.equal(reserve('free-lifetime','initial')?.used,1);
  for(let n=0;n<100;n++)assert.equal(reserve('free-lifetime','retry-'+n,500000),undefined);
  assert.equal(reserve('2026-10','paid-october')?.used,1);
  assert.equal(reserve('2026-10','paid-october',999999),undefined);
  assert.equal(reserve('2026-11','paid-november')?.used,1);
  assert.equal(db.prepare('SELECT SUM(used) AS count FROM avatar_generations').get()?.count,3);db.close();
});
test('avatar style check fails closed for realistic faces, unsafe or uncertain generated output',()=>{
  const good={illustratedAvatar:true,photographicPerson:false,unsafe:false,uncertain:false};
  assert.equal(avatarStyleDecision(good),true);
  for(const flag of ['photographicPerson','unsafe','uncertain'])assert.equal(avatarStyleDecision({...good,[flag]:true}),false);
  for(const input of [null,{},'cartoon',{...good,illustratedAvatar:false},{...good,unsafe:'false'}])assert.equal(avatarStyleDecision(input),false);
});
function sourcePhoto() {
  const data=new Uint8Array(64*64*4);for(let i=0;i<data.length;i+=4){data[i]=180;data[i+1]=100;data[i+2]=50;data[i+3]=255;}
  return new Uint8Array(jpeg.encode({width:64,height:64,data},85).data);
}
test('avatar source is actually decoded and re-encoded, excluding EXIF/GPS/trailing payloads',()=>{
  const photo=sourcePhoto(),privateExif=Buffer.from('Exif\0\0GPS-SECRET-DO-NOT-SHARE'),metadata=Buffer.alloc(privateExif.length+4);
  metadata[0]=255;metadata[1]=225;metadata.writeUInt16BE(privateExif.length+2,2);metadata.set(privateExif,4);
  const input=Buffer.concat([photo.slice(0,2),metadata,photo.slice(2),Buffer.from('SECRET-TRAILER')]),safe=sanitiseAvatarPhoto(input);
  assert.equal(Buffer.from(safe).includes(Buffer.from('GPS-SECRET')),false);assert.equal(Buffer.from(safe).includes(Buffer.from('SECRET-TRAILER')),false);
  const decoded=jpeg.decode(safe,{useTArray:true});assert.equal(decoded.width,64);assert.equal(decoded.height,64);
  assert.throws(()=>sanitiseAvatarPhoto(new Uint8Array([255,216,255,217])));
  assert.throws(()=>sanitiseAvatarPhoto(new Uint8Array(2*1024*1024+1)));
});
function pngChunk(type:string,data:Uint8Array) {
  const bytes=Buffer.alloc(data.length+12);bytes.writeUInt32BE(data.length,0);bytes.write(type,4);bytes.set(data,8);bytes.writeUInt32BE(crc32(bytes.subarray(4,data.length+8)),data.length+8);return bytes;
}
function pngFixture(width=1024) {
  const header=Buffer.alloc(13);header.writeUInt32BE(width,0);header.writeUInt32BE(1024,4);header[8]=8;header[9]=6;
  const rows=Buffer.alloc((width*4+1)*1024,255);for(let y=0;y<1024;y++)rows[y*(width*4+1)]=0;
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),pngChunk('IHDR',header),pngChunk('tEXt',Buffer.from('Description\0PRIVATE-SOURCE-NAME')),pngChunk('IDAT',deflateSync(rows)),pngChunk('IEND',new Uint8Array())]);
}
test('generated PNG drops metadata and rejects invalid CRC, wrong dimensions and trailers',()=>{
  const original=pngFixture(),safe=sanitiseGeneratedPng(original);
  assert.equal(Buffer.from(safe).includes(Buffer.from('PRIVATE-SOURCE-NAME')),false);assert.ok(safe.length<original.length);
  const corrupt=Buffer.from(original);corrupt[20]^=1;assert.throws(()=>sanitiseGeneratedPng(corrupt));
  assert.throws(()=>sanitiseGeneratedPng(pngFixture(512)));
  assert.throws(()=>sanitiseGeneratedPng(Buffer.concat([original,Buffer.from('trailer')])));
});
test('ZIP64 archive is readable by an independent ZIP reader and preserves CRC, unicode and empty files',async()=>{
  const entries=async function*(){yield {name:'journal.json',chunks:textChunks('{"private":true,"place":"Oxford"}')};yield {name:'photos/flower.jpg',chunks:textChunks('chunk-onechunk-two')};yield {name:'notes/été.txt',chunks:textChunks('')};};
  const chunks=[];for await(const chunk of zipChunks(entries()))chunks.push(chunk);const bytes=Buffer.concat(chunks);
  const verifier=spawnSync('python3',['-c','import sys,io,zipfile,json; z=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())); assert z.testzip() is None; print(json.dumps({name:z.read(name).decode() for name in z.namelist()}))'],{input:bytes,encoding:'utf8'});
  assert.equal(verifier.status,0,verifier.stderr);assert.deepEqual(JSON.parse(verifier.stdout),{'journal.json':'{"private":true,"place":"Oxford"}','photos/flower.jpg':'chunk-onechunk-two','notes/été.txt':''});
  assert.equal(bytes.readUInt32LE(18),0xffffffff);assert.notEqual(bytes.indexOf(Buffer.from([0x50,0x4b,0x06,0x06])),-1);
  const known=Buffer.from('123456789');assert.equal(crc32(known),0xcbf43926);assert.equal(crc32(known.subarray(4),crc32(known.subarray(0,4))),0xcbf43926);
});
test('ZIP streaming cancellation closes the source instead of draining all photo data',async()=>{
  let closed=false,count=0;
  const photo=async function*(){try{for(let i=0;i<100;i++){count++;yield new Uint8Array(16384);}}finally{closed=true;}};
  const entries=async function*(){yield {name:'photos/one.jpg',chunks:photo()};};
  const reader=zipStream(entries()).getReader();await reader.read();await reader.read();await reader.cancel();
  assert.equal(closed,true);assert.ok(count<100);
});
test('ZIP filenames cannot escape an extraction directory',()=>{
  for(const value of ['../private.jpg','/absolute.jpg','photos/../../secret','photos\\private.jpg','photos//bad.jpg','photos/\0bad'])assert.throws(()=>safeZipName(value));
  assert.equal(safeZipName('photos/flower.jpg'),'photos/flower.jpg');
});
