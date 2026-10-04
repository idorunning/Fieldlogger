import test from "node:test";
import assert from "node:assert/strict";
import {
  getStats,
  getAchievements,
  validCoords,
  toWire,
  type Observation,
} from "../lib/types";
const make = (change: Partial<Observation> = {}): Observation => ({
  id: crypto.randomUUID(),
  owner: "guest",
  capturedAt: "2026-04-05T07:30:00.000Z",
  localDate: "2026-04-05",
  localHour: 7,
  timezone: "Europe/London",
  latitude: 51.5,
  longitude: -0.12,
  accuracy: 8,
  locationSource: "gps",
  place: "River path",
  note: "",
  category: "birds",
  name: "Robin",
  scientificName: "Erithacus rubecula",
  confirmed: true,
  photo: new Blob(["photo"]),
  identification: null,
  syncState: "pending",
  analysisState: "pending",
  updatedAt: "2026-04-05T07:30:00.000Z",
  revision: 1,
  ...change,
});
test("species are deduplicated by scientific name across repeat sightings", () => {
  const stats = getStats([
    make(),
    make({ scientificName: " erithacus RUBECULA " }),
    make({ name: "Unknown", scientificName: "" }),
  ]);
  assert.equal(stats.total, 3);
  assert.equal(stats.species.length, 1);
  assert.equal(stats.species[0].count, 2);
});
test("low-confidence unconfirmed labels do not count as known species", () => {
  const low = make({
    confirmed: false,
    identification: {
      name: "Robin",
      scientificName: "Erithacus rubecula",
      category: "birds",
      confidence: "low",
      summary: "",
      identifyingFeatures: [],
      lookCloser: "",
      seasonalContext: "",
      alternatives: [],
      sources: [],
      provider: "test",
      analysedAt: "2026-04-05T07:30:00Z",
    },
  });
  assert.equal(getStats([low]).species.length, 0);
  assert.equal(getStats([{ ...low, confirmed: true }]).species.length, 1);
});
test("missing GPS is not counted as an explored area, equator and zero longitude work", () => {
  assert.equal(
    getStats([
      make({ latitude: null, longitude: null }),
      make({ latitude: 0, longitude: 0 }),
    ]).places,
    1,
  );
  assert.equal(validCoords(0, 0), true);
  assert.equal(validCoords(null, 0), false);
  assert.equal(validCoords(NaN, 1), false);
  assert.equal(validCoords(91, 1), false);
});
test("milestones reward kinds, times and areas rather than distances", () => {
  const a = getAchievements([make()]);
  assert.equal(a.find((x) => x.name === "First wonder")?.progress, 1);
  assert.equal(a.find((x) => x.name === "Early bird")?.progress, 1);
  assert.equal(a.find((x) => x.name === "A little of everything")?.progress, 1);
  assert.equal(
    a.some((x) => /distance|steps|miles/.test(x.description)),
    false,
  );
});
test("upload payload excludes local owner, photo blob and error", () => {
  const wire = toWire(make({ error: "retry" }));
  assert.equal("owner" in wire, false);
  assert.equal("photo" in wire, false);
  assert.equal("syncState" in wire, false);
  assert.equal("error" in wire, false);
  assert.equal(wire.latitude, 51.5);
});

test("genus-only names and unspecified species do not inflate species totals", () => {
  assert.equal(
    getStats([
      make({ scientificName: "Quercus" }),
      make({ scientificName: "Quercus sp." }),
    ]).species.length,
    0,
  );
});
