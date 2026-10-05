export type Audience = 'everyone' | 'local' | 'people';
export function distanceKm(a: number, b: number, c: number, d: number) {
  const rad = Math.PI / 180;
  const x = Math.sin((c-a)*rad/2)**2 + Math.cos(a*rad)*Math.cos(c*rad)*Math.sin((d-b)*rad/2)**2;
  return 6371 * 2 * Math.atan2(Math.sqrt(Math.min(1,x)),Math.sqrt(Math.max(0,1-x)));
}
export function canReadPublication(p: {owner_id:string;status:string;audience:string;centre_lat:number|null;centre_lon:number|null;radius_km:number|null}, viewer: string,
  access: {blocked:boolean;invited:boolean;lat:number|null;lon:number|null}) {
  if (p.status !== 'published') return false;
  if (p.owner_id === viewer) return true;
  if (access.blocked) return false;
  if (p.audience === 'everyone') return true;
  if (p.audience === 'people') return access.invited;
  return p.audience === 'local' && access.lat !== null && access.lon !== null && p.centre_lat !== null && p.centre_lon !== null && p.radius_km !== null
    && distanceKm(access.lat,access.lon,p.centre_lat,p.centre_lon) <= p.radius_km;
}
export type PublishingCheck = {contains_people:boolean;unsafe:boolean;personal_information:boolean;uncertain:boolean;reason:string};
export function publishingDecision(value: unknown): {allowed:boolean;reason:string} {
  const v = value as Partial<PublishingCheck> | null;
  if (!v || ['contains_people','unsafe','personal_information','uncertain'].some(k => typeof v[k as keyof PublishingCheck] !== 'boolean'))
    return {allowed:false,reason:'The photo could not be checked reliably. It stays in your private journal.'};
  if (v.contains_people) return {allowed:false,reason:'Photos containing people stay in your private journal.'};
  if (v.unsafe || v.personal_information) return {allowed:false,reason:'This photo is not suitable for the shared nature journal. It stays private.'};
  if (v.uncertain) return {allowed:false,reason:'The publishing check was uncertain. This photo stays private.'};
  return {allowed:true,reason:''};
}
// Strip EXIF, XMP, IPTC and comments from the immutable copy used for publishing.
export function stripJpegMetadata(input: Uint8Array): Uint8Array {
  if(input[0]!==255 || input[1]!==216) throw Error('Invalid JPEG');
  const chunks: Uint8Array[]=[input.slice(0,2)]; let offset=2;
  while(offset<input.length) {
    const start=offset;
    if(input[offset++]!==255) throw Error('Invalid JPEG segment');
    while(input[offset]===255) offset++;
    const marker=input[offset++];
    if(marker===0xda || marker===0xd9) {chunks.push(input.slice(start));break;}
    if(marker===0x01 || (marker>=0xd0 && marker<=0xd7)) {chunks.push(input.slice(start,offset));continue;}
    if(offset+2>input.length) throw Error('Invalid JPEG length');
    const length=(input[offset]<<8)|input[offset+1];
    if(length<2 || offset+length>input.length) throw Error('Invalid JPEG length');
    offset+=length;
    if(!((marker>=0xe0&&marker<=0xef)||marker===0xfe)) chunks.push(input.slice(start,offset));
  }
  const result=new Uint8Array(chunks.reduce((n,c)=>n+c.length,0));let pos=0;
  for(const chunk of chunks){result.set(chunk,pos);pos+=chunk.length;}return result;
}
export function publicSnapshot(data: any) {
  const ai=data.identification;
  return {
    name:String(data.name||'A little discovery').slice(0,160),scientificName:String(data.scientificName||'').slice(0,180),
    category:String(data.category||'other'),place:String(data.place||'').slice(0,160),
    capturedAt:String(data.capturedAt),localDate:String(data.localDate),timezone:String(data.timezone),
    latitude:typeof data.latitude==='number'?data.latitude:null,longitude:typeof data.longitude==='number'?data.longitude:null,
    summary:String(ai?.summary||'A moment outdoors, saved in a trail journal.').slice(0,500),
    interestingInfo:String(ai?.seasonalContext||ai?.lookCloser||'').slice(0,500),
    confidence:ai?.confidence||null,
  };
}
