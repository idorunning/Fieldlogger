/** Same stable Web Mercator grouping as nativeapp/MapClusters.java, in display pixels. */
export type MapPhoto={id:string;latitude:number|null;longitude:number|null;category?:string;acorns?:number;acornCount?:number};
export type PhotoCluster<T extends MapPhoto>={photos:T[];latitude:number;longitude:number;category:string;representative:T};
function valid(point:MapPhoto){return typeof point.latitude==='number'&&typeof point.longitude==='number'&&Number.isFinite(point.latitude)&&Number.isFinite(point.longitude)&&Math.abs(point.latitude)<=90&&Math.abs(point.longitude)<=180;}
function mod(n:number,m:number){return (n%m+m)%m;}
function y(latitude:number,world:number){const lat=Math.max(-85.05112878,Math.min(85.05112878,latitude))*Math.PI/180;return (1-Math.log(Math.tan(lat)+1/Math.cos(lat))/Math.PI)/2*world;}
export function clusterPhotos<T extends MapPhoto>(source:readonly T[],zoom:number,radiusPixels=58,tilePixels=256):PhotoCluster<T>[] {
  if(![zoom,radiusPixels,tilePixels].every(Number.isFinite)||radiusPixels<=0||tilePixels<=0)throw Error('Invalid map scale.');
  const world=tilePixels*2**Math.max(0,Math.min(24,zoom)),cells=Math.max(1,Math.floor(world/radiusPixels)),cellWidth=world/cells;
  type Pending={photos:T[];sin:number;cos:number;y:number;x:number;key:string};const grid=new Map<string,Pending[]>(),all:Pending[]=[];
  for(const photo of source.filter(valid).sort((a,b)=>a.id.localeCompare(b.id))) {
    const px=mod((photo.longitude!+180)/360*world,world),py=y(photo.latitude!,world),cx=Math.floor(px/cellWidth),cy=Math.floor(py/cellWidth);let nearest:Pending|undefined,distance=radiusPixels**2;
    for(let dx=-1;dx<=1;dx++)for(let dy=-1;dy<=1;dy++)for(const candidate of grid.get(mod(cx+dx,cells)+':'+(cy+dy))||[]) {const diff=Math.abs(px-candidate.x),deltaX=Math.min(diff,world-diff),deltaY=py-candidate.y/candidate.photos.length,squared=deltaX**2+deltaY**2;if(squared<=distance){nearest=candidate;distance=squared;}}
    if(!nearest){nearest={photos:[],sin:0,cos:0,y:0,x:0,key:''};all.push(nearest);}else {const bin=grid.get(nearest.key)!;bin.splice(bin.indexOf(nearest),1);}
    const angle=photo.longitude!*Math.PI/180;nearest.sin+=Math.sin(angle);nearest.cos+=Math.cos(angle);nearest.y+=py;nearest.photos.push(photo);nearest.x=(Math.atan2(nearest.sin,nearest.cos)*180/Math.PI+180)/360*world;nearest.key=mod(Math.floor(nearest.x/cellWidth),cells)+':'+Math.floor(nearest.y/nearest.photos.length/cellWidth);if(!grid.has(nearest.key))grid.set(nearest.key,[]);grid.get(nearest.key)!.push(nearest);
  }
  return all.map(group=>{const counts=new Map<string,number>();for(const photo of group.photos){const cat=photo.category||'other';counts.set(cat,(counts.get(cat)||0)+1);}let category='other',most=0;for(const [cat,n] of [...counts.entries()].sort((a,b)=>a[0].localeCompare(b[0])))if(n>most){category=cat;most=n;}let representative=group.photos[0];for(const photo of group.photos)if((photo.category||'other')===category&&((representative.category||'other')!==category||(photo.acornCount??photo.acorns??0)>(representative.acornCount??representative.acorns??0)))representative=photo;
    return {photos:group.photos,latitude:Math.atan(Math.sinh(Math.PI*(1-2*group.y/group.photos.length/world)))*180/Math.PI,longitude:Math.atan2(group.sin,group.cos)*180/Math.PI,category,representative};});
}
export function samePhotoSpot(photos:readonly MapPhoto[]) {if(!photos.length)return true;const first=photos[0];return photos.every(photo=>{const lon=Math.abs(photo.longitude!-first.longitude!);return Math.abs(photo.latitude!-first.latitude!)<=.000001&&Math.min(lon,360-lon)<=.000001;});}
