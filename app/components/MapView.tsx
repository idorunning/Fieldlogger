"use client";
import { useEffect, useRef } from "react";
import { categoryInfo, type Observation, type Category, validCoords } from "@/lib/types";
import { clusterPhotos, samePhotoSpot } from "@/lib/map-clusters";
import "leaflet/dist/leaflet.css";
export default function MapView({records,onSelect}:{records:Observation[];onSelect:(record:Observation)=>void}) {
  const ref=useRef<HTMLDivElement>(null);
  useEffect(()=>{
    let map:import('leaflet').Map|undefined,closed=false;const urls=new Map<string,string>();
    import('leaflet').then(L=>{
      if(closed||!ref.current)return;
      map=L.map(ref.current,{zoomControl:false}).setView([54.2,-2.4],6);L.control.zoom({position:'bottomright'}).addTo(map);
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors',maxZoom:19}).addTo(map);
      const photos=records.filter(record=>validCoords(record.latitude,record.longitude));for(const record of photos)urls.set(record.id,URL.createObjectURL(record.photo));
      const layer=L.layerGroup().addTo(map);
      function photoImage(record:Observation){const image=document.createElement('img');image.src=urls.get(record.id)||'';image.alt='';image.setAttribute('aria-hidden','true');return image;}
      function openStack(group:Observation[],latitude:number,longitude:number){const content=document.createElement('div');content.className='trail-map-stack';const heading=document.createElement('strong');heading.textContent=group.length+' discoveries here';content.appendChild(heading);for(const photo of group){const button=document.createElement('button');button.type='button';button.appendChild(photoImage(photo));const name=document.createElement('span');name.textContent=photo.name||'A little mystery';button.appendChild(name);button.addEventListener('click',()=>{map?.closePopup();onSelect(photo);});content.appendChild(button);}L.popup({maxWidth:300,maxHeight:320}).setLatLng([latitude,longitude]).setContent(content).openOn(map!);}
      function draw(){if(!map||closed)return;layer.clearLayers();const zoom=map.getZoom(),size=zoom<9?43:zoom<15?49:55;for(const group of clusterPhotos(photos,zoom,58,256)){const root=document.createElement('div');root.className='trail-map-photo'+(group.photos.length>1?' is-cluster':'');root.style.setProperty('--pin-colour',categoryInfo[group.category as Category]?.color||'#547058');root.appendChild(photoImage(group.representative));if(group.photos.length>1){const count=document.createElement('span');count.className='trail-map-count';count.textContent=String(group.photos.length);root.appendChild(count);}
        const marker=L.marker([group.latitude,group.longitude],{icon:L.divIcon({html:root,className:'trail-map-icon',iconSize:[size,size],iconAnchor:[size/2,size/2]}),keyboard:true,riseOnHover:true}).addTo(layer);const name=group.representative.name||'Discovery';const tooltip=document.createElement('span');tooltip.textContent=group.photos.length>1?`${group.photos.length} discoveries · zoom in to explore`:name;marker.bindTooltip(tooltip);
        marker.on('click',()=>{if(group.photos.length===1){onSelect(group.photos[0]);return;}if(samePhotoSpot(group.photos)||map!.getZoom()>=18){openStack(group.photos,group.latitude,group.longitude);return;}map!.fitBounds(L.latLngBounds(group.photos.map(photo=>[photo.latitude!,photo.longitude!] as [number,number])),{padding:[80,80],maxZoom:Math.min(19,map!.getZoom()+3)});});
        const element=marker.getElement();if(element){element.setAttribute('role','button');element.addEventListener('keydown',event=>{if(event.key===' '){event.preventDefault();marker.fire('click');}});element.setAttribute('aria-label',group.photos.length>1?`${group.photos.length} discoveries. Zoom in or open this photo stack.`:`${name}, ${categoryInfo[group.representative.category].label}`);}
      }}
      map.on('zoomend',draw);draw();if(photos.length)map.fitBounds(L.latLngBounds(photos.map(photo=>[photo.latitude!,photo.longitude!] as [number,number])),{padding:[55,55],maxZoom:14});
    });
    return()=>{closed=true;map?.remove();for(const url of urls.values())URL.revokeObjectURL(url);};
  },[records,onSelect]);
  return <div ref={ref} className="map-canvas" aria-label="Map of your discoveries. Photo pins merge and split as you zoom."/>;
}
