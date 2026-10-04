"use client";
// Adapted from SEBP Website Redesign, app/components/AuthProvider.tsx, version 13.
// Keeps its account/session flow; this personal app uses server-owned password
// verification and HttpOnly cookies instead of the unconfigured Supabase adapter.
import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { User } from "@/lib/types";
import { adoptGuest, getMeta, setMeta } from "@/lib/local";
type AuthValue = {
  user: User | null;
  loading: boolean;
  offlineSession: boolean;
  signIn: (email: string, password: string) => Promise<string | null>;
  signUp: (
    email: string,
    password: string,
    name: string,
  ) => Promise<string | null>;
  signOut: () => Promise<void>;
};
const Context = createContext<AuthValue | null>(null);
export default function AuthProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [user, setUser] = useState<User | null>(null),
    [loading, setLoading] = useState(true),
    [offlineSession, setOfflineSession] = useState(false);
  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const res = await fetch("/api/auth/me", { cache: "no-store" });
        if (!res.ok) throw Error();
        const data = (await res.json()) as { user: User; error?: string };
        if (alive) setUser(data.user);
        await setMeta("activeUser", data.user);
      } catch {
        const cached = await getMeta<User | null>("activeUser");
        if (alive) {
          setUser(cached || null);
          setOfflineSession(!!cached);
        }
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => {
      alive = false;
    };
  }, []);
  const value = useMemo<AuthValue>(
    () => ({
      user,
      loading,
      offlineSession,
      async signIn(email, password) {
        return submit("login", { email, password });
      },
      async signUp(email, password, name) {
        return submit("register", { email, password, name });
      },
      async signOut() {
        if (!navigator.onLine)
          throw Error("Connect briefly to sign out securely.");
        const r = await fetch("/api/auth/logout", { method: "POST" });
        if (!r.ok) throw Error("Could not sign out. Please try again.");
        await setMeta("activeUser", null);
        setUser(null);
        setOfflineSession(false);
      },
    }),
    [user, loading, offlineSession],
  );
  async function submit(action: string, body: Record<string, string>) {
    try {
      const res = await fetch("/api/auth/" + action, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      const data = (await res.json()) as { user: User; error?: string };
      if (!res.ok)
        return data.error || "Sign in is unavailable. Try again shortly.";
      await adoptGuest(data.user);
      await setMeta("activeUser", data.user);
      setUser(data.user);
      setOfflineSession(false);
      return null;
    } catch {
      return "Connect to the internet to sign in. Your local journal is still available.";
    }
  }
  return <Context.Provider value={value}>{children}</Context.Provider>;
}
export function useAuth() {
  const context = useContext(Context);
  if (!context) throw Error("AuthProvider required");
  return context;
}
