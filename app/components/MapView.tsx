"use client";
import { useEffect, useRef } from "react";
import { categoryInfo, type Observation, validCoords } from "@/lib/types";
import "leaflet/dist/leaflet.css";
export default function MapView({
  records,
  onSelect,
}: {
  records: Observation[];
  onSelect: (r: Observation) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    let map: import("leaflet").Map | undefined,
      closed = false;
    import("leaflet").then((L) => {
      if (closed || !ref.current) return;
      map = L.map(ref.current, { zoomControl: false }).setView([54.2, -2.4], 6);
      L.control.zoom({ position: "bottomright" }).addTo(map);
      L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution:
          '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors',
        maxZoom: 19,
      }).addTo(map);
      const points: import("leaflet").LatLngTuple[] = [];
      records
        .filter((r) => validCoords(r.latitude, r.longitude))
        .forEach((r) => {
          const point: [number, number] = [r.latitude!, r.longitude!];
          points.push(point);
          const marker = L.circleMarker(point, {
            radius: 10,
            color: "white",
            weight: 3,
            fillColor: categoryInfo[r.category].color,
            fillOpacity: 1,
          }).addTo(map!);
          const tip = document.createElement("div");
          tip.textContent = r.name || "New discovery";
          marker.bindTooltip(tip);
          marker.on("click", () => onSelect(r));
          const el = marker.getElement();
          if (el) {
            el.setAttribute("tabindex", "0");
            el.setAttribute("role", "button");
            el.setAttribute(
              "aria-label",
              `${r.name || "Discovery"}, ${categoryInfo[r.category].label}`,
            );
            el.addEventListener("keydown", (e: Event) => {
              if ((e as KeyboardEvent).key === "Enter") onSelect(r);
            });
          }
        });
      if (points.length)
        map.fitBounds(L.latLngBounds(points), {
          padding: [50, 50],
          maxZoom: 14,
        });
    });
    return () => {
      closed = true;
      map?.remove();
    };
  }, [records, onSelect]);
  return (
    <div
      ref={ref}
      className="map-canvas"
      aria-label="Map of your discoveries"
    />
  );
}
