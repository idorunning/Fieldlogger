"use client";
import { useState, type FormEvent } from "react";
import { clearLocalAccount } from "@/lib/local";
export default function DeleteAccount() {
 const [message,setMessage]=useState(""),[busy,setBusy]=useState(false),[done,setDone]=useState(false);
 async function remove(event: FormEvent<HTMLFormElement>) {
  event.preventDefault();setBusy(true);setMessage("");
  const form=event.currentTarget;const password=String(new FormData(form).get("password"));form.reset();
  try {
   const session=await fetch("/api/auth/me",{cache:"no-store"}).then(r=>r.json()) as {user?:{id:string}};
   if(!session.user)throw Error("Sign in to your My Trail Log account first, then return here.");
   const response=await fetch("/api/account",{method:"DELETE",headers:{"Content-Type":"application/json"},body:JSON.stringify({password})});
   const result=await response.json() as {error?:string};
   if(!response.ok)throw Error(result.error||"Deletion could not finish. Sign in and try again.");
   setDone(true);
   try { await clearLocalAccount(session.user.id); }
   catch { setMessage("Your online account is deleted. Clear My Trail Log browser storage on this device to remove its local copy."); }
  }catch(error){setMessage(error instanceof Error?error.message:"Connect to the internet and try again.");}finally{setBusy(false);}
 }
 return <main className="legal-page"><a href="/">← Back to My Trail Log</a><p className="eyebrow">YOUR ACCOUNT</p><h1>{done?"Your account is deleted":"Delete your account"}</h1>{done?<p>Your uploaded journal, saved API key and account have been removed. Remove any exports and copies on other devices separately.</p>:<><p>This permanently removes your account, uploaded photographs, locations, notes and saved API key. It clears this account's local discoveries in this browser. Exports and local copies on other devices remain there.</p><p>Export your journal from your account menu first if you want to keep a copy. Deletion does not revoke your key at OpenAI.</p><form className="form-stack" onSubmit={remove}><label>Confirm your password<input name="password" type="password" required minLength={8} maxLength={256} autoComplete="current-password"/></label><label className="checkbox"><input type="checkbox" required/>I understand that deletion is permanent.</label><button className="button danger full" disabled={busy}>{busy?"Deleting…":"Permanently delete my account"}</button></form></>}{message&&<p className="notice error" role="alert">{message}</p>}<p><a href="/privacy">Privacy & your data</a></p></main>;
}
