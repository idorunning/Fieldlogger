import jpeg from 'jpeg-js';
import {AVATAR_DIMENSION_LIMIT,AVATAR_IMAGE_LIMIT} from './avatar-policy';
import {crc32} from './zip-stream';
export const AVATAR_OUTPUT_LIMIT=3*1024*1024;

// A real pixel decode/re-encode is deliberate: JPEG magic bytes and MIME alone
// do not prove the input is an image or remove all EXIF/location/trailer data.
export function sanitiseAvatarPhoto(input:Uint8Array) {
  if(input.length<4||input.length>AVATAR_IMAGE_LIMIT||input[0]!==255||input[1]!==216)
    throw new Error('Choose a JPEG photo up to 2 MB.');
  const decoded=jpeg.decode(input,{useTArray:true,tolerantDecoding:false,maxResolutionInMP:1.1,maxMemoryUsageInMB:30});
  let safe:Uint8Array;
  try{
    if(decoded.width<16||decoded.height<16||decoded.width>AVATAR_DIMENSION_LIMIT||decoded.height>AVATAR_DIMENSION_LIMIT)
      throw new Error('Use a photo no larger than 1024 pixels on either side.');
    safe=new Uint8Array(jpeg.encode({width:decoded.width,height:decoded.height,data:decoded.data},85).data);
  }finally{decoded.data.fill(0);}
  if(safe.length>AVATAR_IMAGE_LIMIT)throw new Error('That photo is too large. Choose a smaller photo.');
  return safe;
}

export function sanitiseGeneratedPng(bytes:Uint8Array) {
  const signature=[137,80,78,71,13,10,26,10];
  if(bytes.length>AVATAR_OUTPUT_LIMIT||!signature.every((n,i)=>bytes[i]===n))throw new Error('The avatar could not be created.');
  const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength),chunks=[bytes.slice(0,8)];
  let offset=8,header=false,pixels=false,end=false;
  while(offset<bytes.length) {
    if(offset+12>bytes.length)throw new Error('The avatar could not be created.');
    const length=view.getUint32(offset),finish=offset+12+length;
    if(finish>bytes.length)throw new Error('The avatar could not be created.');
    const type=String.fromCharCode(...bytes.subarray(offset+4,offset+8));
    if(crc32(bytes.subarray(offset+4,offset+8+length))!==view.getUint32(offset+8+length))throw new Error('The avatar could not be created.');
    if(!header&&type!=='IHDR')throw new Error('The avatar could not be created.');
    if(type==='IHDR') {
      if(header||length!==13||view.getUint32(offset+8)!==1024||view.getUint32(offset+12)!==1024)throw new Error('The avatar could not be created.');
      header=true;
    }
    if(type==='IDAT')pixels=true;
    if(type==='IEND') {if(length||!pixels||finish!==bytes.length)throw new Error('The avatar could not be created.');end=true;}
    // Discard all textual, EXIF and unknown ancillary chunks from the cartoon.
    if(['IHDR','PLTE','tRNS','IDAT','IEND'].includes(type))chunks.push(bytes.slice(offset,finish));
    else if(type[0]===type[0].toUpperCase())throw new Error('The avatar could not be created.');
    offset=finish;
  }
  if(!header||!pixels||!end)throw new Error('The avatar could not be created.');
  const output=new Uint8Array(chunks.reduce((total,chunk)=>total+chunk.length,0));let at=0;
  for(const chunk of chunks){output.set(chunk,at);at+=chunk.length;}return output;
}
