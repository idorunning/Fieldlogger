export const categories = [
  "plants",
  "flowers",
  "bugs",
  "birds",
  "animals",
  "fungi",
  "landmarks",
  "other",
] as const;
export type Category = (typeof categories)[number];
export const categoryInfo: Record<Category, { label: string; color: string }> =
  {
    plants: { label: "Plants", color: "#40845b" },
    flowers: { label: "Flowers", color: "#b65091" },
    bugs: { label: "Bugs", color: "#b87717" },
    birds: { label: "Birds", color: "#397cb0" },
    animals: { label: "Animals", color: "#b5643a" },
    fungi: { label: "Fungi", color: "#8456a4" },
    landmarks: { label: "Landmarks", color: "#66788b" },
    other: { label: "Other", color: "#73776b" },
  };
export type Source = {
  name: string;
  url: string;
  detail: string;
  retrievedAt: string;
};
export type Identification = {
  name: string;
  scientificName: string;
  category: Category;
  confidence: "high" | "medium" | "low";
  summary: string;
  identifyingFeatures: string[];
  lookCloser: string;
  seasonalContext: string;
  alternatives: string[];
  sources: Source[];
  referenceText?: string;
  referenceTitle?: string;
  provider: string;
  analysedAt: string;
  taxonKey?: number;
  specialist?: { name: string; score: number };
};
export type Observation = {
  id: string;
  owner: string;
  capturedAt: string;
  localDate: string;
  localHour: number;
  timezone: string;
  latitude: number | null;
  longitude: number | null;
  accuracy: number | null;
  locationSource: "gps" | "exif" | "manual" | "none";
  place: string;
  note: string;
  category: Category;
  name: string;
  scientificName: string;
  confirmed: boolean;
  photo: Blob;
  identification: Identification | null;
  syncState: "pending" | "synced" | "error";
  analysisState: "pending" | "complete" | "error";
  archived?: boolean;
  error?: string;
  updatedAt: string;
  revision: number;
};
export type ObservationWire = Omit<
  Observation,
  "photo" | "owner" | "syncState" | "error"
>;
export type User = { id: string; email: string; name: string };
export function toWire(record: Observation): ObservationWire {
  const { photo, owner, syncState, error, ...rest } = record;
  void photo;
  void owner;
  void syncState;
  void error;
  return rest;
}
export function validCoords(lat: unknown, lng: unknown): boolean {
  return (
    typeof lat === "number" &&
    Number.isFinite(lat) &&
    lat >= -90 &&
    lat <= 90 &&
    typeof lng === "number" &&
    Number.isFinite(lng) &&
    lng >= -180 &&
    lng <= 180
  );
}
export function getStats(records: Observation[]) {
  const species = new Map<
    string,
    { name: string; scientificName: string; count: number; category: Category }
  >();
  const counts = Object.fromEntries(
    categories.map((c) => [c, records.filter((r) => r.category === c).length]),
  ) as Record<Category, number>;
  for (const r of records) {
    if (
      r.scientificName.trim().split(/\s+/).length < 2 ||
      /\b(sp|spp)\.?$/i.test(r.scientificName) ||
      (!r.confirmed && r.identification?.confidence === "low")
    )
      continue;
    const key = r.scientificName.trim().toLocaleLowerCase();
    const prior = species.get(key);
    species.set(key, {
      name: r.name,
      scientificName: r.scientificName,
      count: (prior?.count || 0) + 1,
      category: r.category,
    });
  }
  return {
    total: records.length,
    counts,
    species: [...species.values()].sort((a, b) => b.count - a.count),
    days: new Set(records.map((r) => r.localDate)).size,
    places: new Set(
      records
        .filter((r) => validCoords(r.latitude, r.longitude))
        .map((r) => `${r.latitude!.toFixed(2)},${r.longitude!.toFixed(2)}`),
    ).size,
    months: new Set(records.map((r) => r.localDate.slice(0, 7))).size,
  };
}
export function getAchievements(records: Observation[]) {
  const s = getStats(records),
    n = Object.values(s.counts).filter(Boolean).length;
  return [
    {
      name: "First wonder",
      description: "Save your very first discovery.",
      progress: Math.min(s.total, 1),
      goal: 1,
      icon: "sparkles",
    },
    {
      name: "A little of everything",
      description: "Notice four different kinds of things.",
      progress: Math.min(n, 4),
      goal: 4,
      icon: "compass",
    },
    {
      name: "Botanical beginnings",
      description: "Meet five different plant or flower species.",
      progress: Math.min(
        s.species.filter((x) => ["plants", "flowers"].includes(x.category))
          .length,
        5,
      ),
      goal: 5,
      icon: "leaf",
    },
    {
      name: "Small worlds",
      description: "Photograph five bugs or fungi.",
      progress: Math.min(s.counts.bugs + s.counts.fungi, 5),
      goal: 5,
      icon: "bug",
    },
    {
      name: "New corners",
      description: "Make discoveries in three different areas.",
      progress: Math.min(s.places, 3),
      goal: 3,
      icon: "map",
    },
    {
      name: "Through the seasons",
      description: "Collect discoveries in four different months.",
      progress: Math.min(s.months, 4),
      goal: 4,
      icon: "sun",
    },
    {
      name: "Early bird",
      description: "Save a discovery before 9 am.",
      progress: records.some((r) => r.localHour < 9) ? 1 : 0,
      goal: 1,
      icon: "bird",
    },
    {
      name: "Curious collector",
      description: "Get to know twenty different species.",
      progress: Math.min(s.species.length, 20),
      goal: 20,
      icon: "award",
    },
  ];
}
