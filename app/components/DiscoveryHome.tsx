"use client";
import { Camera, ImagePlus, ChevronRight, ArrowDown, CloudUpload, Compass, MapPin } from "lucide-react";
import type { Observation } from "@/lib/types";
import { useEffect, useState } from "react";

type Props = { records: Observation[]; online: boolean; busy: boolean; onCamera: () => void; onGallery: () => void; onJournal: () => void; onOpen: (id: string) => void; onGuide: () => void };
function RecentPhoto({ record }: { record: Observation }) {
  const [url, setUrl] = useState("");
  useEffect(() => { const u = URL.createObjectURL(record.photo); setUrl(u); return () => URL.revokeObjectURL(u); }, [record.photo]);
  return url ? <img src={url} alt={record.name || "Your latest discovery"} loading="lazy" /> : null;
}
export default function DiscoveryHome({ records, online, busy, onCamera, onGallery, onJournal, onOpen, onGuide }: Props) {
  return <>
    <section className="discover-stage" aria-label="Capture a discovery">
      <img className="discover-backdrop" src="/woodland.jpg" alt="Sunlight over bluebells in a green woodland" fetchPriority="high" />
      <div className="discover-shade" />
      <div className="discover-caption"><span className="live-dot" /> {online ? "Ready for a little wonder" : "Offline · ready to save"}</div>
      <div className="discover-composition">
        <p className="discover-eyebrow">YOUR WORLD, A CLOSER LOOK</p>
        <h1>There’s a story<br />in the small things.</h1>
        <p className="discover-subtitle">See something? Keep the moment.</p>
        <div className="discover-controls">
          <button className="shutter-action" aria-label="Take a photo" onClick={onCamera} disabled={busy}><span className="shutter-icon"><Camera size={30} strokeWidth={1.8} /></span><span>Take a photo<small>Save now. Discover more later.</small></span><ChevronRight size={24} /></button>
          <button className="gallery-action" onClick={onGallery} disabled={busy}><ImagePlus size={24} /> Choose a photo</button>
        </div>
        <button className="below-fold" onClick={() => document.getElementById("discover-more")?.scrollIntoView({behavior:"smooth"})}>Your discoveries <ArrowDown size={18} /></button>
      </div>
    </section>
    <div id="discover-more" className="discover-below">
      <div className="section-heading"><div><p className="section-label">YOUR FIELD JOURNAL</p><h2>{records.length ? "Moments worth keeping" : "Your first discovery awaits"}</h2></div>{records.length > 0 && <button className="text-button" onClick={onJournal}>View all <ChevronRight size={20} /></button>}</div>
      {records.length ? <div className="recent-photos">{records.slice(0,3).map(record => <button key={record.id} className="recent-photo" onClick={() => onOpen(record.id)}><RecentPhoto record={record} /><div><strong>{record.name || "A little mystery"}</strong><span><MapPin size={14} /> {record.place || "A moment outdoors"}</span></div>{record.syncState !== "synced" && <CloudUpload className="recent-pending" size={20} />}</button>)}</div> : <p className="home-empty-note">A flower, a feather, a tiny visitor. One photo is all it takes to begin.</p>}
      <div className="section-heading inspiration-heading"><div><p className="section-label">LOOK A LITTLE CLOSER</p><h2>What will you spot?</h2></div><Compass size={28} /></div>
      <div className="inspiration-grid">
        {[{image:"/woodland.jpg",alt:"Bluebells beneath woodland trees",label:"Plants & flowers",hint:"Along the path"},{image:"/field-robin.jpg",alt:"A robin perched in the countryside",label:"Birds & bugs",hint:"Above and below"},{image:"/field-fox.jpg",alt:"A red fox in its natural surroundings",label:"Wildlife",hint:"A quiet encounter"}].map(item => <button key={item.label} className="inspiration-photo" onClick={onCamera}><img src={item.image} alt={item.alt} loading="lazy" /><span><small>{item.hint}</small><strong>{item.label}</strong></span><Camera size={22} /></button>)}
      </div>
      <button className="home-guide" onClick={onGuide}><Compass size={24} /><span>Field guide & photo credits<small>Sources, identification and offline help</small></span><ChevronRight size={22} /></button>
      <p className="home-credit">Woodland photograph by Rob Wingate · Unsplash</p>
    </div>
  </>;
}
