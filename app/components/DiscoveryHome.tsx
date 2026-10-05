"use client";
import { ImagePlus, Search, CloudUpload, MapPin, Camera } from "lucide-react";
import type { Observation } from "@/lib/types";
import { useEffect, useState } from "react";

type Props = { records: Observation[]; online: boolean; busy: boolean; onGallery: () => void; onJournal: () => void; onOpen: (id: string) => void };
function JournalPhoto({ record, first }: { record: Observation; first: boolean }) {
  const [url, setUrl] = useState("");
  useEffect(() => { const u = URL.createObjectURL(record.photo); setUrl(u); return () => URL.revokeObjectURL(u); }, [record.photo]);
  return url ? <img src={url} alt={record.name || "Your discovery"} loading={first ? "eager" : "lazy"} /> : null;
}
export default function DiscoveryHome({ records, online, busy, onGallery, onJournal, onOpen }: Props) {
  return <section className="journal-home" aria-label="Your field journal">
    <img className="discover-backdrop" src="/woodland.jpg" alt="" fetchPriority="high" />
    <div className="discover-shade" />
    <div className="journal-surface">
      <div className="journal-home-heading"><div><h1>Your field journal</h1><p>{records.length ? `${records.length} ${records.length === 1 ? "discovery" : "discoveries"}` : "A place for what catches your eye"}{!online && " · Saved offline"}</p></div>
        <div className="journal-home-tools"><button className="icon-button" aria-label="Search journal" onClick={onJournal}><Search size={23} /></button><button className="icon-button" aria-label="Add from gallery" onClick={onGallery} disabled={busy}><ImagePlus size={23} /></button></div>
      </div>
      {records.length ? <div className="journal-photo-grid">{records.map((record, index) => <button key={record.id} className="journal-photo" onClick={() => onOpen(record.id)}>
        <JournalPhoto record={record} first={index === 0} />
        <div className="journal-photo-caption"><strong>{record.name || "A little mystery"}</strong><span><MapPin size={14} /> {record.place || (record.latitude !== null ? "Place name pending" : "A moment outdoors")}</span><time dateTime={record.capturedAt}>{new Date(record.capturedAt).toLocaleDateString("en-GB", { day: "numeric", month: "short" })}</time></div>
        {record.syncState !== "synced" && <CloudUpload className="recent-pending" aria-label="Saved on this device" size={18} />}
      </button>)}</div> : <div className="journal-home-empty"><Camera size={36} /><h2>Your first discovery awaits</h2><p>Tap Camera below. Your photos will make this space yours.</p></div>}
    </div>
  </section>;
}
