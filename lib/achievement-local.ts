import { db, listLocal } from "./local";
import { earnAchievements, evaluateAchievements, mergeAchievementLedgers, validAchievementLedger, type AchievementLedger, type AchievementPhoto } from "./achievements";
export const achievementKey = (owner:string) => "achievements:"+owner;
export async function collectLocalAchievements(owner:string,records:readonly AchievementPhoto[]):Promise<AchievementLedger> {
  const database=await db(),tx=database.transaction('meta','readwrite');
  const prior=validAchievementLedger(await tx.store.get(achievementKey(owner))),earned=earnAchievements(prior,evaluateAchievements(records),new Date().toISOString());
  await tx.store.put(earned,achievementKey(owner));await tx.done;return earned;
}
export async function syncAchievementLedger(owner:string):Promise<AchievementLedger> {
  const own=await collectLocalAchievements(owner,await listLocal(owner));if(owner==='guest'||!navigator.onLine)return own;
  const session=await fetch('/api/auth/me',{cache:'no-store'});if(!session.ok)return own;const account=await session.json() as {user?:{id:string}|null};if(account.user?.id!==owner)return own;
  const response=await fetch('/api/social/achievements',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({achievements:Object.entries(own).map(([badge,earnedAt])=>({badge,earnedAt}))})});
  if(!response.ok)return own;
  const result=await response.json() as {achievements?:{badge:string;earnedAt:string}[]};if(!Array.isArray(result.achievements))return own;
  const remote=validAchievementLedger(Object.fromEntries(result.achievements.map(row=>[row.badge,row.earnedAt]))),database=await db(),tx=database.transaction('meta','readwrite');
  const latest=validAchievementLedger(await tx.store.get(achievementKey(owner))),merged=mergeAchievementLedgers(latest,remote);await tx.store.put(merged,achievementKey(owner));await tx.done;return merged;
}
