"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { KeyRound, LoaderCircle, ShieldCheck, Trash2 } from "lucide-react";
type Status = {
  hasKey: boolean;
  serverKey: boolean;
  canSave: boolean;
  updatedAt: string | null;
};
export default function ApiKeySettings({
  online,
  onChanged,
}: {
  online: boolean;
  onChanged: () => void;
}) {
  const [status, setStatus] = useState<Status | null>(null);
  const [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [error, setError] = useState("");
  const [remove, setRemove] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  async function refresh() {
    const response = await fetch("/api/settings/openai-key", {
      cache: "no-store",
    });
    const result = (await response.json()) as Status & {
      error?: string;
      message?: string;
    };
    if (!response.ok)
      throw Error(result.error || "Sign in again to manage your key.");
    setStatus(result);
  }
  useEffect(() => {
    if (online) void refresh().catch((e) => setError(e.message));
  }, [online]);
  async function request(method: string, body?: string, action = "") {
    const response = await fetch("/api/settings/openai-key" + action, {
      method,
      headers: { "Content-Type": "application/json" },
      body,
      cache: "no-store",
    });
    const result = (await response.json()) as {
      error?: string;
      message?: string;
    };
    if (!response.ok) throw Error(result.error || "Please try again.");
    return result;
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    const body = JSON.stringify({ apiKey: input.current?.value.trim() });
    // The field is cleared before the request. Never use localStorage, IndexedDB,
    // analytics, URL parameters, or error logging for the key.
    if (input.current) input.current.value = "";
    try {
      await request("PUT", body);
      await refresh();
      setRemove(false);
      setMessage(
        "Key saved securely. Test the connection below. Pending photos will retry automatically.",
      );
      onChanged();
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "The key could not be saved. Please try again.",
      );
    } finally {
      setBusy(false);
    }
  }
  async function test() {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await request("POST", "{}", "/test");
      setMessage(result.message || "Connection tested.");
      onChanged();
    } catch (e) {
      setError(e instanceof Error ? e.message : "The connection test failed.");
    } finally {
      setBusy(false);
    }
  }
  async function forget() {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await request("DELETE");
      await refresh();
      setRemove(false);
      setMessage(
        "Your saved key was removed. Your photos and discoveries are unchanged.",
      );
      onChanged();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "The key could not be removed.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="key-settings">
      <p className="muted">
        Connect OpenAI to identify your countryside discoveries. Paste your key
        here, outside the chat, and replace it whenever you need to.
      </p>
      <div className="key-security">
        <ShieldCheck size={23} />
        <p>
          Encrypted on the server and linked to your account. Your saved key is
          never displayed or included in offline storage or journal exports.
        </p>
      </div>
      {!online && (
        <p className="notice" role="status">
          Connect to the internet to manage your key. Your offline journal still
          works.
        </p>
      )}
      {status && (
        <p className="small muted">
          {status.hasKey
            ? `A key is saved · updated ${new Date(status.updatedAt!).toLocaleString("en-GB")}.`
            : status.serverKey
              ? "A server connection is already configured. Your own key can override it."
              : "No API key saved yet."}{" "}
          Saving a key does not verify its permissions or expiry.
        </p>
      )}
      {online && status && !status.canSave && (
        <p className="notice error" role="alert">
          Secure key storage is not available yet. Please try later.
        </p>
      )}
      <form className="form-stack" onSubmit={save} autoComplete="off">
        <label>
          OpenAI API key
          <input
            ref={input}
            name="openai-secret"
            type="password"
            placeholder="Paste your API key"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            minLength={19}
            maxLength={1003}
            required
            disabled={!online || busy || !status?.canSave}
          />
        </label>
        <p className="small muted">
          Saving enables automatic identification of pending photos. OpenAI
          charges your API account for analysis and the small connection test. A
          20-minute key stops working when it expires; you can return here with
          a replacement.
        </p>
        <button
          className="button primary full"
          disabled={!online || busy || !status?.canSave}
        >
          {busy ? (
            <LoaderCircle className="spin" size={18} />
          ) : (
            <KeyRound size={18} />
          )}{" "}
          {status?.hasKey ? "Replace saved key" : "Save key"}
        </button>
      </form>
      <div className="key-actions">
        <button
          type="button"
          className="button secondary"
          onClick={test}
          disabled={!online || busy || !(status?.hasKey || status?.serverKey)}
        >
          Test connection
        </button>
        {status?.hasKey && (
          <button
            type="button"
            className="button secondary"
            onClick={() => setRemove(true)}
            disabled={!online || busy}
          >
            <Trash2 size={17} /> Remove key
          </button>
        )}
      </div>
      {remove && (
        <div className="notice">
          <p>
            Remove your saved key? This does not revoke it at OpenAI.{" "}
            {status?.serverKey
              ? "The app will use its server connection instead."
              : "Identification will pause until you add another key."}
          </p>
          <button
            className="button secondary"
            onClick={() => setRemove(false)}
            disabled={busy}
          >
            Keep key
          </button>{" "}
          <button
            className="button primary"
            onClick={forget}
            disabled={busy || !online}
          >
            Confirm removal
          </button>
        </div>
      )}
      {message && (
        <p className="notice" role="status">
          {message}
        </p>
      )}
      {error && (
        <p className="notice error" role="alert">
          {error}
        </p>
      )}
      <p className="small muted">
        <a
          href="https://platform.openai.com/api-keys"
          target="_blank"
          rel="noreferrer"
        >
          Create or revoke a key at OpenAI ↗
        </a>
      </p>
    </div>
  );
}
