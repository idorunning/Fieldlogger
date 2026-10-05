import {weatherKind} from './weather-policy';
import { z } from 'zod';
import { bindings,json } from './server';
const coordinate=z.object({latitude:z.coerce.number().min(-90).max(90),longitude:z.coerce.number().min(-180).max(180)});
async function provider(url:string){const r=await fetch(url,{headers:{'User-Agent':'MyTrailLog/2.3 (fieldlogger.co.uk; ntracey@gmail.com)'},signal:AbortSignal.timeout(10000)});if(!r.ok)throw new Error('Outdoor information is temporarily unavailable.');return r.json() as Promise<any>;}
export async function mapSearch(request:Request){
  const query=(new URL(request.url).searchParams.get('query')||'').trim().slice(0,100);
  if(query.length<2)return json({error:'Enter a postcode or place name.'},400);
  if(/^[A-Z]{1,2}\d[A-Z\d]?\s*\d[A-Z]{2}$/i.test(query)){
    const result=await provider('https://api.postcodes.io/postcodes/'+encodeURIComponent(query.replaceAll(' ','')));
    const p=result.result;if(p&&Number.isFinite(p.latitude)&&Number.isFinite(p.longitude))return json({places:[{name:[p.postcode,p.admin_district].filter(Boolean).join(' · '),latitude:p.latitude,longitude:p.longitude}],source:'Postcodes.io'});
  }
  const result=await provider('https://photon.komoot.io/api/?limit=6&q='+encodeURIComponent(query));
  const places=(result.features||[]).filter((f:any)=>Array.isArray(f.geometry?.coordinates)&&f.geometry.coordinates.length===2).map((f:any)=>({name:[f.properties.name,f.properties.city||f.properties.county,f.properties.country].filter(Boolean).join(' · '),latitude:f.geometry.coordinates[1],longitude:f.geometry.coordinates[0]}));
  return json({places,source:'OpenStreetMap / Photon'});
}
export async function weather(request:Request){
  const url=new URL(request.url);if(!url.searchParams.has('lat')||!url.searchParams.has('lon'))return json({error:'A map location is required.'},400);const p=coordinate.parse({latitude:url.searchParams.get('lat'),longitude:url.searchParams.get('lon')});
  // Coarse coordinates and a short cache avoid storing a person's location history.
  const lat=Math.round(p.latitude*10)/10,lon=Math.round(p.longitude*10)/10,key=`weather/v1/${lat},${lon}.json`;
  const cached=await bindings().BUCKET.get(key);
  if(cached){const v=await cached.json<any>();if(v.expires>Date.now())return json(v.value);}
  const r=await provider(`https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,is_day,weather_code,wind_speed_10m&daily=sunrise,sunset&timezone=auto&forecast_days=1`);
  if(!r.current||!Number.isFinite(r.current.weather_code))throw new Error('Weather is temporarily unavailable.');
  const value={condition:weatherKind(r.current.weather_code,r.current.wind_speed_10m||0),code:r.current.weather_code,temperature:r.current.temperature_2m,isDay:!!r.current.is_day,sunrise:r.daily?.sunrise?.[0]||'',sunset:r.daily?.sunset?.[0]||'',timezone:r.timezone,observedAt:new Date().toISOString(),source:'Open-Meteo · local area forecast'};
  await bindings().BUCKET.put(key,JSON.stringify({expires:Date.now()+15*60000,value}),{httpMetadata:{contentType:'application/json'}});return json(value);
}
