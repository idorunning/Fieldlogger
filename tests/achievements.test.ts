import test from 'node:test';
import assert from 'node:assert/strict';
import catalogue from './fixtures/achievement-catalog.json';
import {achievementLevel,achievementLevels,easterDay,evaluateAchievements,earnAchievements,mergeAchievementLedgers,validAchievementLedger,type AchievementPhoto} from '../lib/achievements';
const photo=(changes:AchievementPhoto={}):AchievementPhoto=>({name:'European robin',category:'birds',scientificName:'Erithacus rubecula',confirmed:true,localDate:'2026-04-05',localHour:7,latitude:51.75,longitude:-1.25,place:'Oxford',...changes});
const byId=(records:AchievementPhoto[],id:string)=>evaluateAchievements(records).find(badge=>badge.id===id)!;
test('all 352 desktop badge IDs, names, descriptions and goals match the canonical native catalogue',()=>{
  const evaluated=evaluateAchievements([]),actual=evaluated.map(({progress,...badge})=>badge);
  assert.equal(actual.length,352);assert.equal(new Set(actual.map(badge=>badge.id)).size,352);assert.deepEqual(actual,catalogue);
});
test('material levels open from common to rare at the same permanent achievement thresholds',()=>{
  assert.deepEqual(achievementLevels.map(level=>level.name),['Soil','Clay','Flint','Quartz','Amber','Gold']);
  for(const [earned,level] of [[0,0],[11,0],[12,1],[29,1],[30,2],[59,2],[60,3],[99,3],[100,4],[149,4],[150,5],[352,5]])assert.equal(achievementLevel(earned),level);
});
test('local capture date earns Christmas and Easter only on the relevant date, including year boundaries',()=>{
  assert.equal(byId([photo({localDate:'2026-12-24',capturedAt:'2026-12-25T00:10:00Z'})],'calendar-12-25').progress,0);
  assert.equal(byId([photo({localDate:'2026-12-25',capturedAt:'2026-12-24T23:10:00Z'})],'calendar-12-25').progress,1);
  assert.equal(easterDay(2026),'2026-04-05');assert.equal(easterDay(2027),'2027-03-28');
  assert.equal(byId([photo()],'calendar-easter').progress,1);assert.equal(byId([photo({localDate:'2026-04-04'})],'calendar-easter').progress,0);
  assert.equal(byId([photo({localDate:'2028-02-29'})],'calendar-02-29').progress,1);assert.equal(byId([photo({localDate:'2026-02-29'})],'calendar-02-29').progress,0);
});
test('hours, weekday, GPS and place progress follow capture context and preserve valid zero coordinates',()=>{
  const records=[photo({localHour:4,latitude:0,longitude:0}),photo({localHour:5}),photo({localHour:7}),photo({localHour:8}),photo({localHour:17}),photo({localHour:20}),photo({localHour:21}),photo({localHour:24,latitude:91,place:''})];
  assert.equal(byId(records,'time-0-5').progress,4);assert.equal(byId(records,'time-1-5').progress,2);assert.equal(byId(records,'time-2-5').progress,2);assert.equal(byId(records,'time-3-5').progress,2);assert.equal(byId(records,'time-6-20').progress,7);assert.equal(byId(records,'time-7-20').progress,7);
  assert.equal(byId(records,'weekday-7').progress,5);assert.equal(byId(records,'all-week').progress,1);
});
test('species and named subjects reject uncertain taxonomy and partial-word matches',()=>{
  const records=[photo(),photo({name:'A rose',scientificName:'Rosa canina',confirmed:false,identification:{confidence:'low'}}),photo({name:'Primrose',scientificName:'Primula vulgaris'}),photo({name:'Some ash',scientificName:'Fraxinus species'}),photo({name:'Another robin',scientificName:'Erithacus rubecula'})];
  assert.equal(byId(records,'species-3').progress,2);assert.equal(byId(records,'subject-robin-5').progress,2);assert.equal(byId([photo({name:'Primrose',scientificName:''})],'subject-rose').progress,0);assert.equal(byId([photo({name:'Ashberry',scientificName:''})],'subject-ash').progress,0);
});
test('archive and deleted photos never revoke earned achievements, including unopened calendar layers',()=>{
  const first=earnAchievements({},evaluateAchievements([photo({localDate:'2026-12-25',archived:true})]),'2026-12-25T12:00:00.000Z');
  assert.ok(first['calendar-12-25']);assert.ok(first['photos-1']);assert.equal(byId([photo({archived:true})],'photos-1').progress,1);
  const afterDeletion=earnAchievements(first,evaluateAchievements([]),'2027-01-01T10:00:00.000Z');assert.deepEqual(afterDeletion,first);
  const merged=mergeAchievementLedgers(first,{'photos-1':'2026-12-24T12:00:00.000Z'});assert.equal(merged['photos-1'],'2026-12-24T12:00:00.000Z');assert.ok(merged['calendar-12-25']);
  assert.deepEqual(validAchievementLedger({'made-up-badge':'2026-10-05T00:00:00Z','photos-1':'bad'}),{});
});
