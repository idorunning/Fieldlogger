"use client";
import { RefreshCw, ShieldCheck, X } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import PlaySetup from "./PlaySetup";

type Member = { id: string; email: string; name: string; username: string; role: "admin" | "member"; status: "active" | "suspended"; createdAt: string; photoCount: number; plan: string; subscriptionStatus: string; owner?:boolean };
type Stats = { users: number; admins: number; suspended: number; observations: number; publications: number; activeSubscriptions: number };
type Detail = { member: Member; billing: {plan: string; status: string; expiresAt: string | null}; usage: {used: number; limit: number; periodStart: string; periodEnd: string}; audit: {action: string; createdAt: string; actorId: string}[] };
type Change = { member: Member; kind: "role" | "status"; value: "admin" | "member" | "active" | "suspended" };
class ApiError extends Error { constructor(message: string, public status: number) { super(message); } }
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, { ...options, cache: "no-store", credentials: "same-origin" });
  const result = await response.json() as T & { error?: string };
  if (!response.ok) throw new ApiError(result.error || "The request could not be completed.", response.status);
  return result;
}
function date(value: string | null) { if (!value) return "—"; const parsed = new Date(value); return Number.isFinite(parsed.getTime()) ? parsed.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" }) : "—"; }
function planName(value?: string) { return ({ free: "Free", plus: "Plus", premium: "Premium", annual: "Premium yearly" } as Record<string, string>)[value || ""] || value || "Free"; }

export default function AdminConsole() {
  const [access, setAccess] = useState<"loading" | "login" | "forbidden" | "admin">("loading"), [busy, setBusy] = useState(false), [message, setMessage] = useState(""), [error, setError] = useState("");
  const [stats, setStats] = useState<Stats | null>(null), [members, setMembers] = useState<Member[]>([]), [search, setSearch] = useState(""), [cursor, setCursor] = useState<string | null>(null);
  const [detail, setDetail] = useState<Detail | null>(null), [change, setChange] = useState<Change | null>(null);
  const dialogRef = useRef<HTMLDialogElement>(null), activeQuery = useRef("");
  useEffect(() => { void refresh(); }, []);
  useEffect(() => { if (change && dialogRef.current && !dialogRef.current.open) dialogRef.current.showModal(); }, [change]);
  function failure(cause: unknown) {
    if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) { setAccess(cause.status === 401 ? "login" : "forbidden"); setMembers([]); setDetail(null); setStats(null); setChange(null); }
    setError(cause instanceof Error ? cause.message : "Connect to the internet and try again.");
  }
  async function refresh(query = search, next?: string) {
    setBusy(true); setError("");
    try {
      const params = new URLSearchParams(); if (query.trim()) params.set("query", query.trim()); if (next) params.set("cursor", next);
      const [newStats, list] = await Promise.all([request<Stats>("/api/admin/stats"), request<{ members: Member[]; nextCursor: string | null }>("/api/admin/members?" + params)]);
      if(!next)activeQuery.current=query;
      setStats(newStats); setMembers(current => next ? [...current, ...list.members.filter(member => !current.some(existing => existing.id === member.id))] : list.members); setCursor(list.nextCursor || null); setAccess("admin");
    } catch (cause) { failure(cause); } finally { setBusy(false); }
  }
  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(""); const form = event.currentTarget, values = new FormData(form); const email = String(values.get("email")), password = String(values.get("password")); form.reset();
    try { await request("/api/auth/login", { method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify({ email, password }) }); await refresh(); }
    catch (cause) { failure(cause); } finally { setBusy(false); }
  }
  async function signOut() { setBusy(true); setError(""); try { await request("/api/auth/logout", { method: "POST" }); setAccess("login"); setStats(null); setMembers([]); setDetail(null); setChange(null); setMessage(""); } catch (cause) { failure(cause); } finally { setBusy(false); } }
  async function openMember(id: string) { setBusy(true); setError(""); setDetail(null); try { setDetail(await request<Detail>("/api/admin/members/" + encodeURIComponent(id))); } catch (cause) { failure(cause); } finally { setBusy(false); } }
  async function applyChange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!change) return; const currentChange = change, form = event.currentTarget, password = String(new FormData(form).get("password")); form.reset(); setBusy(true); setError(""); setMessage("");
    try {
      await request("/api/admin/members/" + encodeURIComponent(currentChange.member.id) + "/" + currentChange.kind, { method: "POST", headers: {"Content-Type":"application/json"}, body: JSON.stringify({ [currentChange.kind]: currentChange.value, password }) });
      setChange(null); setMessage("Account updated. The administrative change has been recorded."); await refresh(); await openMember(currentChange.member.id);
    } catch (cause) { if(cause instanceof ApiError && cause.status === 401)setError(cause.message);else failure(cause); } finally { setBusy(false); }
  }
  return <section className="company-container company-admin">
    <div className="company-admin-heading"><div><p className="company-kicker"><ShieldCheck size={15}/> Authorised administrators only</p><h1>Member administration</h1><p>Account support, verified plan information and audited membership controls.</p></div>{(access === "admin" || access === "forbidden") && <button className="company-button company-button-secondary" disabled={busy} onClick={signOut}>Sign out</button>}</div>
    {error && !change && <p className="company-notice company-notice-error" role="alert">{error}</p>}{message && <p className="company-notice" role="status">{message}</p>}
    {access === "loading" && <p role="status">Checking administrator access…</p>}
    {access === "login" && <form className="company-admin-form" onSubmit={signIn}><h2 style={{fontSize:27}}>Administrator sign-in</h2><p className="small">Use an account that has already been granted administrator access.</p><label>Email<input type="email" name="email" autoComplete="username" required maxLength={254}/></label><label>Password<input type="password" name="password" autoComplete="current-password" required minLength={8} maxLength={256}/></label><button className="company-button company-button-primary" disabled={busy}>{busy ? "Checking…" : "Sign in"}</button></form>}
    {access === "forbidden" && <div className="company-notice"><p>This account does not have administrator access. An existing administrator must grant access to your registered account.</p><p><a href="/journal">Open your own journal</a> or sign out to use a different account.</p></div>}
    {access === "admin" && <>
      <PlaySetup/>
      {stats && <div className="company-admin-stats">{([ ["Members", stats.users], ["Administrators", stats.admins], ["Suspended", stats.suspended], ["Journal photos", stats.observations], ["Published photos", stats.publications], ["Active subscriptions", stats.activeSubscriptions] ] as [string, number][]).map(([label, value]) => <div className="company-admin-stat" key={label}><strong>{Number(value || 0).toLocaleString("en-GB")}</strong><span>{label}</span></div>)}</div>}
      <form className="company-admin-controls" onSubmit={event => { event.preventDefault(); setDetail(null); void refresh(); }}><label>Find a registered member<input value={search} onChange={event => setSearch(event.target.value)} maxLength={254} placeholder="Email, name or username" autoComplete="off"/></label><button className="company-button company-button-primary" disabled={busy}>Search</button><button type="button" className="company-button company-button-secondary" disabled={busy} onClick={() => void refresh()}><RefreshCw size={17}/>Refresh</button></form>
      <div className="company-admin-table-wrap"><table><caption className="sr-only">Registered members and account status</caption><thead><tr><th scope="col">Member</th><th scope="col">Email</th><th scope="col">Access</th><th scope="col">Status</th><th scope="col">Plan</th><th scope="col">Photos</th><th scope="col">Joined</th></tr></thead><tbody>{members.map(member => <tr key={member.id}><td><button onClick={() => void openMember(member.id)} disabled={busy}>{member.name || member.username || "Member"}</button><small>{member.username ? "@" + member.username : "No public username"}</small></td><td>{member.email}</td><td>{member.role === "admin" ? "Administrator" : "Member"}</td><td><span className={"company-admin-pill " + member.status}>{member.status}</span></td><td>{planName(member.plan)}<small>{member.subscriptionStatus === "free" ? "No paid entitlement" : member.subscriptionStatus}</small></td><td>{member.photoCount || 0}</td><td>{date(member.createdAt)}</td></tr>)}</tbody></table>{!members.length && <p className="company-admin-empty">{busy ? "Loading members…" : "No members match this search."}</p>}</div>
      {cursor && <div className="company-actions"><button className="company-button company-button-secondary" disabled={busy} onClick={() => void refresh(activeQuery.current, cursor)}>Load more members</button></div>}
      {detail && <article className="company-admin-member"><div className="company-admin-member-head"><div><h2>{detail.member.name || detail.member.username || "Member"}</h2><p className="company-muted">{detail.member.email} · {detail.member.id}</p></div><button className="company-button company-button-secondary" onClick={() => setDetail(null)} aria-label="Close member details"><X size={17}/>Close</button></div><dl><div><dt>Verified plan</dt><dd>{planName(detail.billing?.plan || detail.member.plan)}</dd></div><div><dt>Subscription expiry</dt><dd>{date(detail.billing?.expiresAt)}</dd></div><div><dt>Cloud photo usage this month</dt><dd>{detail.usage?.used || 0} / {detail.usage?.limit || 30}</dd></div><div><dt>Next monthly reset</dt><dd>{date(detail.usage?.periodEnd)}</dd></div></dl><p className="company-muted">Subscription state: {detail.billing?.status || detail.member.subscriptionStatus}. Plan access is verified by the service; role controls do not create a Google Play purchase.</p><div className="company-actions"><button className="company-button company-button-secondary" disabled={busy || detail?.member.owner === true} onClick={() => setChange({member:detail.member,kind:"role",value:detail.member.role === "admin" ? "member" : "admin"})}>{detail.member.role === "admin" ? "Remove administrator access" : "Grant administrator access"}</button><button className={"company-button " + (detail.member.status === "active" ? "company-button-danger" : "company-button-secondary")} disabled={busy || detail?.member.owner === true} onClick={() => setChange({member:detail.member,kind:"status",value:detail.member.status === "active" ? "suspended" : "active"})}>{detail.member.status === "active" ? "Suspend account" : "Restore account"}</button></div>{detail.member.owner && <p className="company-muted">The owner account is protected and cannot be demoted or suspended.</p>}<h3>Recent administrative changes</h3>{detail.audit?.length ? <ul className="company-admin-audit">{detail.audit.map((entry, index) => <li key={index}>{date(entry.createdAt)} · {entry.action} · administrator {entry.actorId}</li>)}</ul> : <p className="company-muted">No administrative changes recorded.</p>}</article>}
    </>}
    {change && <dialog className="company-dialog" ref={dialogRef} onCancel={event => { if (busy) event.preventDefault(); else setChange(null); }} aria-labelledby="company-confirm-title"><form onSubmit={applyChange}><h2 id="company-confirm-title">Confirm account change</h2>{error && <p className="company-notice company-notice-error" role="alert">{error}</p>}<p className="company-dialog-copy">Change {change.member.email} to <strong>{change.value === "admin" ? "administrator" : change.value}</strong>?</p><p className="company-dialog-copy">{change.kind === "role" ? "Administrators can access member account details and manage membership. Only grant this to someone you trust. Protected owner accounts cannot be disabled." : "Suspension restricts use of this account and can hide its published photos. This does not cancel a Google Play subscription or delete its data."}</p><label>Your administrator password<input name="password" type="password" required minLength={8} maxLength={256} autoComplete="current-password" autoFocus/></label><p className="company-dialog-copy">This change is authorised by the server and recorded in the administrative audit.</p><div className="company-actions"><button className="company-button company-button-primary" disabled={busy}>{busy ? "Applying…" : "Confirm change"}</button><button type="button" className="company-button company-button-secondary" disabled={busy} onClick={() => setChange(null)}>Cancel</button></div></form></dialog>}
  </section>;
}
