// Uncompressed ZIP64 archives with data descriptors. Each source is consumed
// with backpressure; photo bytes and the complete journal never accumulate in
// memory. ZIP64 also supports a personal archive that grows beyond 4 GiB.
export type ZipEntry = {name:string;chunks:AsyncIterable<Uint8Array>;modifiedAt?:Date};
const utf8=new TextEncoder();
const crcTable=Uint32Array.from({length:256},(_,n)=>{let c=n;for(let k=0;k<8;k++)c=(c&1)?0xedb88320^(c>>>1):c>>>1;return c>>>0;});
export function crc32(bytes:Uint8Array,previous=0) {
  let crc=(previous^0xffffffff)>>>0;
  for(const byte of bytes)crc=crcTable[(crc^byte)&255]^(crc>>>8);
  return (crc^0xffffffff)>>>0;
}
function record(size:number){const bytes=new Uint8Array(size);return {bytes,view:new DataView(bytes.buffer)};}
export function safeZipName(name:string) {
  if(!name||name.length>220||name.startsWith('/')||name.includes('\\')||name.includes('\0')||name.split('/').some(p=>p==='..'||p==='.'||!p))
    throw new Error('Invalid archive filename.');
  return name;
}
function dosDate(date:Date) {
  const year=Math.min(2107,Math.max(1980,date.getUTCFullYear()||1980));
  return {time:(date.getUTCHours()<<11)|(date.getUTCMinutes()<<5)|(date.getUTCSeconds()>>1),
    date:((year-1980)<<9)|((date.getUTCMonth()+1)<<5)|date.getUTCDate()};
}
export async function* zipChunks(entries:AsyncIterable<ZipEntry>):AsyncGenerator<Uint8Array> {
  const central:{name:Uint8Array;size:bigint;crc:number;offset:bigint;date:number;time:number}[]=[];
  let offset=0n;
  for await(const entry of entries) {
    const name=utf8.encode(safeZipName(entry.name)),clock=dosDate(entry.modifiedAt||new Date());
    const start=offset,local=record(30+name.length+20),v=local.view;
    v.setUint32(0,0x04034b50,true);v.setUint16(4,45,true);v.setUint16(6,0x0808,true);
    v.setUint16(10,clock.time,true);v.setUint16(12,clock.date,true);
    v.setUint32(18,0xffffffff,true);v.setUint32(22,0xffffffff,true);
    v.setUint16(26,name.length,true);v.setUint16(28,20,true);local.bytes.set(name,30);
    v.setUint16(30+name.length,1,true);v.setUint16(32+name.length,16,true);
    offset+=BigInt(local.bytes.length);yield local.bytes;
    let size=0n,crc=0;
    for await(const chunk of entry.chunks) {
      if(!(chunk instanceof Uint8Array))throw new Error('Invalid archive data.');
      crc=crc32(chunk,crc);size+=BigInt(chunk.length);offset+=BigInt(chunk.length);yield chunk;
    }
    const descriptor=record(24);descriptor.view.setUint32(0,0x08074b50,true);
    descriptor.view.setUint32(4,crc,true);descriptor.view.setBigUint64(8,size,true);descriptor.view.setBigUint64(16,size,true);
    offset+=24n;yield descriptor.bytes;
    central.push({name,size,crc,offset:start,...clock});
  }
  const directoryOffset=offset;
  for(const entry of central) {
    const header=record(46+entry.name.length+28),v=header.view;
    v.setUint32(0,0x02014b50,true);v.setUint16(4,45,true);v.setUint16(6,45,true);v.setUint16(8,0x0808,true);
    v.setUint16(12,entry.time,true);v.setUint16(14,entry.date,true);v.setUint32(16,entry.crc,true);
    v.setUint32(20,0xffffffff,true);v.setUint32(24,0xffffffff,true);v.setUint16(28,entry.name.length,true);
    v.setUint16(30,28,true);v.setUint32(42,0xffffffff,true);header.bytes.set(entry.name,46);
    const extra=46+entry.name.length;v.setUint16(extra,1,true);v.setUint16(extra+2,24,true);
    v.setBigUint64(extra+4,entry.size,true);v.setBigUint64(extra+12,entry.size,true);v.setBigUint64(extra+20,entry.offset,true);
    offset+=BigInt(header.bytes.length);yield header.bytes;
  }
  const endOffset=offset,end=record(56);end.view.setUint32(0,0x06064b50,true);end.view.setBigUint64(4,44n,true);
  end.view.setUint16(12,45,true);end.view.setUint16(14,45,true);
  end.view.setBigUint64(24,BigInt(central.length),true);end.view.setBigUint64(32,BigInt(central.length),true);
  end.view.setBigUint64(40,offset-directoryOffset,true);end.view.setBigUint64(48,directoryOffset,true);yield end.bytes;
  const locator=record(20);locator.view.setUint32(0,0x07064b50,true);locator.view.setBigUint64(8,endOffset,true);locator.view.setUint32(16,1,true);yield locator.bytes;
  const classic=record(22);classic.view.setUint32(0,0x06054b50,true);classic.view.setUint16(8,0xffff,true);classic.view.setUint16(10,0xffff,true);
  classic.view.setUint32(12,0xffffffff,true);classic.view.setUint32(16,0xffffffff,true);yield classic.bytes;
}
export function zipStream(entries:AsyncIterable<ZipEntry>) {
  const iterator=zipChunks(entries);
  return new ReadableStream<Uint8Array>({
    async pull(controller){try{const {done,value}=await iterator.next();if(done)controller.close();else controller.enqueue(value);}catch(error){controller.error(error);await iterator.return(undefined);}},
    async cancel(){await iterator.return(undefined);},
  });
}
export async function* textChunks(value:string){yield utf8.encode(value);}
export async function* readableChunks(stream:ReadableStream<Uint8Array>) {
  const reader=stream.getReader();let complete=false;
  try{for(;;){const {done,value}=await reader.read();if(done){complete=true;return;}yield value;}}
  finally{if(!complete)await reader.cancel();reader.releaseLock();}
}
