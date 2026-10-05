"use client";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from "react";
import {
  Leaf,
  Camera,
  Map as MapIcon,
  BookOpen,
  Award,
  Search,
  Plus,
  X,
  ChevronRight,
  Bird,
  Bug,
  Flower2,
  Mountain,
  PawPrint,
  Sprout,
  Cloud,
  CloudUpload,
  WifiOff,
  MapPin,
  CalendarDays,
  Sparkles,
  Compass,
  Sun,
  Download,
  Share2,
  LogIn,
  LogOut,
  Check,
  LoaderCircle,
  SlidersHorizontal,
  Info,
  RefreshCw,
  ImagePlus,
  CircleHelp,
  CheckCheck,
  KeyRound,
} from "lucide-react";
import {
  categories,
  categoryInfo,
  getStats,
  getAchievements,
  type Category,
  type Observation,
  validCoords,
} from "@/lib/types";
import {
  listLocal,
  saveLocal,
  syncRecords,
  requestPersistentStorage,
  getMeta,
  setMeta,
} from "@/lib/local";
import { preparePhoto, locate, localDate } from "@/lib/photo";
import { useAuth } from "./AuthProvider";
import MapView from "./MapView";
import ApiKeySettings from "./ApiKeySettings";
import DiscoveryHome from "./DiscoveryHome";
type View = "discover" | "journal" | "map" | "collection" | "achievements" | "sources";
const icons = {
  plants: Sprout,
  flowers: Flower2,
  bugs: Bug,
  birds: Bird,
  animals: PawPrint,
  fungi: Leaf,
  landmarks: Mountain,
  other: Compass,
};
const nav = [
  { id: "discover" as View, label: "Discover", icon: Compass },
  { id: "journal" as View, label: "Field journal", icon: BookOpen },
  { id: "map" as View, label: "Discovery map", icon: MapIcon },
  { id: "collection" as View, label: "My collection", icon: Leaf },
  { id: "achievements" as View, label: "Achievements", icon: Award },
];
function usePhoto(blob: Blob | undefined) {
  const [url, setUrl] = useState("");
  useEffect(() => {
    if (!blob) return;
    const next = URL.createObjectURL(blob);
    setUrl(next);
    return () => URL.revokeObjectURL(next);
  }, [blob]);
  return url;
}
function Photo({
  blob,
  alt,
  className,
}: {
  blob: Blob;
  alt: string;
  className?: string;
}) {
  const url = usePhoto(blob);
  return url ? (
    <img src={url} alt={alt} className={className} loading="lazy" />
  ) : null;
}
function CategoryIcon({
  category,
  size = 18,
}: {
  category: Category;
  size?: number;
}) {
  const Icon = icons[category];
  return <Icon size={size} />;
}
function dateLabel(value: string) {
  return new Date(value).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}
function Dialog({
  title,
  onClose,
  children,
  wide = false,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className={"dialog " + (wide ? "wide" : "")}
      onCancel={onClose}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
    >
      <div className="dialog-top">
        <h2>{title}</h2>
        <button className="icon-button" aria-label="Close" onClick={onClose}>
          <X size={22} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
function Login({ onClose }: { onClose: () => void }) {
  const { signIn, signUp } = useAuth();
  const [mode, setMode] = useState<"login" | "register">("register"),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    const f = new FormData(e.currentTarget);
    const result =
      mode === "login"
        ? await signIn(String(f.get("email")), String(f.get("password")))
        : await signUp(
            String(f.get("email")),
            String(f.get("password")),
            String(f.get("name")),
          );
    setBusy(false);
    if (result) setMessage(result);
    else onClose();
  }
  return (
    <Dialog
      title={
        mode === "register" ? "A home for your discoveries" : "Welcome back"
      }
      onClose={onClose}
    >
      <p className="muted">
        Save your journal across devices and identify your photos when you’re
        online.
      </p>
      <div className="segmented">
        <button
          className={mode === "register" ? "active" : ""}
          onClick={() => {
            setMode("register");
            setMessage("");
          }}
        >
          Create account
        </button>
        <button
          className={mode === "login" ? "active" : ""}
          onClick={() => {
            setMode("login");
            setMessage("");
          }}
        >
          Sign in
        </button>
      </div>
      <form onSubmit={submit} className="form-stack">
        {mode === "register" && (
          <label>
            Your name
            <input
              name="name"
              required
              maxLength={100}
              autoComplete="given-name"
            />
          </label>
        )}
        <label>
          Email
          <input name="email" type="email" required autoComplete="email" />
        </label>
        <label>
          Password
          <input
            aria-label="Password"
            name="password"
            type="password"
            required
            minLength={8}
            maxLength={256}
            autoComplete={
              mode === "login" ? "current-password" : "new-password"
            }
          />
          <small>At least 8 characters.</small>
        </label>
        <button className="button primary full" disabled={busy}>
          {busy ? (
            <LoaderCircle className="spin" size={18} />
          ) : (
            <LogIn size={18} />
          )}{" "}
          {busy
            ? "One moment…"
            : mode === "login"
              ? "Sign in"
              : "Create my account"}
        </button>
        {message && (
          <p className="notice error" role="alert">
            {message}
          </p>
        )}
      </form>
      <p className="small muted">
        Your photos stay private. Any discoveries already saved on this device
        will be added to your account.
      </p>
    </Dialog>
  );
}
type Draft = {
  photo: Blob;
  date: string;
  latitude: number | null;
  longitude: number | null;
  accuracy: number | null;
  locationSource: Observation["locationSource"];
  place: string;
  note: string;
  category: Category;
  name: string;
};
function Capture({
  draft,
  setDraft,
  onSave,
  onClose,
  busy,
}: {
  draft: Draft;
  setDraft: (d: Draft) => void;
  onSave: () => Promise<void>;
  onClose: () => void;
  busy: boolean;
}) {
  const [locating, setLocating] = useState(false),
    [message, setMessage] = useState("");
  async function gps() {
    setLocating(true);
    try {
      const p = await locate();
      setDraft({
        ...draft,
        latitude: p.coords.latitude,
        longitude: p.coords.longitude,
        accuracy: p.coords.accuracy,
        locationSource: "gps",
      });
      setMessage(
        "Current location added. Use this only if this is where the photo was taken.",
      );
    } catch {
      setMessage(
        "Location permission was unavailable. You can still save this discovery.",
      );
    } finally {
      setLocating(false);
    }
  }
  return (
    <Dialog title="A new discovery" onClose={onClose}>
      <Photo
        blob={draft.photo}
        alt="Your new discovery"
        className="capture-photo"
      />
      <div className="capture-save">        <button
          className="button primary full"
          onClick={onSave}
          disabled={busy || !draft.date}
        >
          {busy ? (
            <LoaderCircle className="spin" size={19} />
          ) : (
            <Plus size={19} />
          )}{" "}
          {busy ? "Saving…" : "Save discovery"}
        </button>
<p><MapPin size={17} />{validCoords(draft.latitude, draft.longitude) ? "Location saved with your photo" : "You can add a location below"}</p></div>
      <details className="capture-details"><summary>Add details <span>Optional</span><ChevronRight size={21} /></summary>
      <div className="form-stack">
        <label>
          A name, if you know it
          <input
            placeholder="Let’s find out what this is…"
            value={draft.name}
            maxLength={160}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          />
        </label>
        <div className="form-columns">
          <label>
            Kind
            <select
              aria-label="Kind"
              value={draft.category}
              onChange={(e) =>
                setDraft({ ...draft, category: e.target.value as Category })
              }
            >
              <option value="other">Let the app suggest</option>
              {categories
                .filter((x) => x !== "other")
                .map((x) => (
                  <option key={x} value={x}>
                    {categoryInfo[x].label}
                  </option>
                ))}
            </select>
          </label>
          <label>
            Spotted on
            <input
              type="datetime-local"
              value={draft.date}
              onChange={(e) => setDraft({ ...draft, date: e.target.value })}
              required
            />
          </label>
        </div>
        <label>
          Place
          <input
            value={draft.place}
            maxLength={160}
            placeholder="e.g. The woods by the river"
            onChange={(e) => setDraft({ ...draft, place: e.target.value })}
          />
        </label>
        <div className="location-row">
          <MapPin size={18} />
          <span>
            {validCoords(draft.latitude, draft.longitude)
              ? `${draft.latitude!.toFixed(5)}, ${draft.longitude!.toFixed(5)}${draft.accuracy ? ` · ±${Math.round(draft.accuracy)} m` : ""}`
              : "No location attached"}
          </span>
          <button className="text-button" onClick={gps} disabled={locating}>
            {locating ? "Locating…" : "Use GPS"}
          </button>
        </div>
        {message && (
          <p className="small muted" role="status">
            {message}
          </p>
        )}
        <label>
          A little field note
          <textarea
            value={draft.note}
            maxLength={3000}
            placeholder="What caught your eye? What was it doing?"
            onChange={(e) => setDraft({ ...draft, note: e.target.value })}
          />
        </label>
        <p className="small muted centered">
          Saved on this device first. Uploaded and identified when you’re signed
          in and connected.
        </p>
      </div>
      </details>
    </Dialog>
  );
}
function DiscoveryCard({
  record,
  onClick,
}: {
  record: Observation;
  onClick: () => void;
}) {
  return (
    <button className="discovery-card" onClick={onClick}>
      <div className="photo-wrap">
        <Photo blob={record.photo} alt={record.name || "Your discovery"} />
        <span
          className="photo-tag"
          style={{ background: categoryInfo[record.category].color }}
        >
          <CategoryIcon category={record.category} />
          {categoryInfo[record.category].label}
        </span>
        {record.syncState !== "synced" && (
          <span
            className="photo-sync"
            aria-label="Saved locally; upload pending"
          >
            <CloudUpload size={17} />
          </span>
        )}
      </div>
      <div className="card-body">
        <h3>{record.name || "A little mystery"}</h3>
        <p className="scientific">
          {record.scientificName ||
            (record.analysisState === "pending"
              ? "Waiting to be identified"
              : "Your discovery")}
        </p>
        <div className="card-meta">
          <span>
            <MapPin size={13} />
            {record.place ||
              (record.latitude !== null ? "Location saved" : "No location")}
          </span>
          <span>{dateLabel(record.capturedAt).replace(/ \d{4}$/, "")}</span>
        </div>
      </div>
    </button>
  );
}
function Detail({
  record,
  onClose,
  onUpdate,
  toast,
}: {
  record: Observation;
  onClose: () => void;
  onUpdate: (r: Observation) => Promise<void>;
  toast: (text: string) => void;
}) {
  const [editing, setEditing] = useState(false),
    [name, setName] = useState(record.name),
    [scientific, setScientific] = useState(record.scientificName),
    [category, setCategory] = useState(record.category),
    [note, setNote] = useState(record.note),
    [place, setPlace] = useState(record.place),
    [includeLocation, setIncludeLocation] = useState(false),
    [sharing, setSharing] = useState(false),
    [confirmed, setConfirmed] = useState(record.confirmed);
  const identification = record.identification;
  async function share(withInfo: boolean) {
    setSharing(true);
    try {
      let blob = record.photo;
      if (withInfo) {
        const bitmap = await createImageBitmap(blob),
          canvas = document.createElement("canvas");
        canvas.width = 1080;
        canvas.height = 1430;
        const ctx = canvas.getContext("2d")!;
        ctx.fillStyle = "#f6f7f2";
        ctx.fillRect(0, 0, 1080, 1430);
        const scale = Math.max(1080 / bitmap.width, 810 / bitmap.height);
        ctx.drawImage(
          bitmap,
          (1080 - bitmap.width * scale) / 2,
          (810 - bitmap.height * scale) / 2,
          bitmap.width * scale,
          bitmap.height * scale,
        );
        bitmap.close();
        ctx.fillStyle = "#173f35";
        ctx.font = "bold 24px sans-serif";
        ctx.fillText("FIELD LOGGER  /  A LITTLE DISCOVERY", 60, 874);
        ctx.font = "42px Georgia";
        wrap(ctx, record.name || "A little mystery", 60, 938, 960, 50, 2);
        ctx.font = "italic 26px Georgia";
        ctx.fillStyle = "#5d6e62";
        ctx.fillText(record.scientificName, 60, 1026);
        ctx.font = "24px sans-serif";
        ctx.fillText(
          dateLabel(record.capturedAt) +
            (includeLocation && record.place ? " · " + record.place : ""),
          60,
          1071,
        );
        ctx.font = "27px sans-serif";
        wrap(
          ctx,
          identification?.summary ||
            record.note ||
            "Something interesting from a day outdoors.",
          60,
          1130,
          960,
          38,
          5,
        );
        ctx.font = "20px sans-serif";
        ctx.fillStyle = "#667168";
        ctx.fillText(
          identification
            ? "AI identification suggestion · Field Logger"
            : "My countryside field journal · Field Logger",
          60,
          1375,
        );
        blob = await new Promise<Blob>((resolve, reject) =>
          canvas.toBlob((b) => (b ? resolve(b) : reject(Error())), "image/png"),
        );
      }
      const file = new File(
        [blob],
        `fieldlogger-${record.id}.${withInfo ? "png" : "jpg"}`,
        { type: blob.type },
      );
      if (navigator.canShare?.({ files: [file] })) {
        await navigator.share({
          files: [file],
          title: record.name || "My discovery",
        });
      } else {
        download(blob, file.name);
        toast("Your discovery has been downloaded, ready to share.");
      }
    } catch (e) {
      if ((e as Error).name !== "AbortError")
        toast("Sharing was unavailable. Please try again.");
    } finally {
      setSharing(false);
    }
  }
  return (
    <Dialog title="From your field journal" onClose={onClose} wide>
      <Photo
        blob={record.photo}
        alt={record.name || "A discovery"}
        className="detail-photo"
      />
      <div className="detail-content">
        <div className="detail-labels">
          <span
            className="category-label"
            style={{ color: categoryInfo[record.category].color }}
          >
            <CategoryIcon category={record.category} />
            {categoryInfo[record.category].label}
          </span>
          <span className="small muted">
            {record.syncState === "synced" ? (
              <>
                <Cloud size={16} /> Backed up
              </>
            ) : (
              <>
                <CloudUpload size={16} /> Saved on this device
              </>
            )}
          </span>
        </div>
        <h1>{record.name || "A little mystery"}</h1>
        {record.scientificName && (
          <p className="scientific large">{record.scientificName}</p>
        )}
        <div className="metadata">
          <span>
            <CalendarDays size={16} />
            {dateLabel(record.capturedAt)} ·{" "}
            {new Date(record.capturedAt).toLocaleTimeString("en-GB", {
              hour: "2-digit",
              minute: "2-digit",
              timeZone: record.timezone,
            })}
          </span>
          <span>
            <MapPin size={16} />
            {record.place || "Place not named"}
          </span>
        </div>
        {validCoords(record.latitude, record.longitude) && (
          <p className="small muted">
            {record.latitude!.toFixed(5)}, {record.longitude!.toFixed(5)}
            {record.accuracy
              ? ` · GPS accuracy ±${Math.round(record.accuracy)} m`
              : ""}{" "}
            · {record.locationSource.toUpperCase()}
          </p>
        )}
        {identification ? (
          <>
            <div className="ai-note">
              <div className="section-label">
                <Sparkles size={16} /> A field companion’s suggestion{" "}
                <span className={"confidence " + identification.confidence}>
                  {identification.confidence} confidence
                </span>
              </div>
              <p>{identification.summary}</p>
              <h3>Look for these clues</h3>
              <ul>
                {identification.identifyingFeatures.map((x, i) => (
                  <li key={i}>{x}</li>
                ))}
              </ul>
              {identification.alternatives.length > 0 && (
                <p className="small muted">
                  Could also be: {identification.alternatives.join(" · ")}
                </p>
              )}
            </div>
            <details className="field-details"><summary>More about this discovery <ChevronRight size={22} /></summary>
            <section className="context-note">
              <Sun size={22} />
              <div>
                <h3>Here, at this time of year</h3>
                <p>{identification.seasonalContext}</p>
              </div>
            </section>
            <section className="look-closer">
              <Compass size={24} />
              <div>
                <h3>A reason to pause</h3>
                <p>{identification.lookCloser}</p>
              </div>
            </section>
            {identification.referenceText && (
              <section className="reference-note">
                <div className="section-label">
                  <BookOpen size={17} /> From Wikipedia ·{" "}
                  {identification.referenceTitle}
                </div>
                <p>{identification.referenceText}</p>
                <small>
                  Wikipedia contributors · CC BY-SA 4.0 · Extract may be
                  shortened.
                </small>
              </section>
            )}
            <div className="sources-list">
              <h3>References & evidence</h3>
              {identification.sources.length ? (
                identification.sources.map((s) => (
                  <a key={s.url} href={s.url} target="_blank" rel="noreferrer">
                    <span>
                      <strong>{s.name}</strong>
                      <small>{s.detail}</small>
                    </span>
                    <ChevronRight size={18} />
                  </a>
                ))
              ) : (
                <p className="muted">
                  Reference sources could not be retrieved for this suggestion.
                </p>
              )}
              <p className="small muted">
                {identification.provider} ·{" "}
                {dateLabel(identification.analysedAt)}. Reference matches
                support background reading; they do not verify your photo.
              </p>
            </div>
            </details>
          </>
        ) : (
          <div className="notice">
            <Sparkles size={20} />
            <div>
              <strong>There’s a story here.</strong>
              <p>
                Saved and ready for identification when your account and the AI
                connection are available. You can add a name yourself below.
              </p>
            </div>
          </div>
        )}
        {record.error && <p className="notice error">{record.error}</p>}
        {record.note && (
          <section className="personal-note">
            <h3>Your field note</h3>
            <p>{record.note}</p>
          </section>
        )}
        <div className="detail-actions">
          <button
            className="button secondary"
            onClick={() => setEditing(!editing)}
          >
            {editing ? "Close editing" : "Add a note or correct the name"}
          </button>
          {record.confirmed && (
            <span className="small">
              <CheckCheck size={16} /> Named by you
            </span>
          )}
        </div>
        {editing && (
          <form
            className="form-stack edit-panel"
            onSubmit={async (e) => {
              e.preventDefault();
              await onUpdate({
                ...record,
                name,
                scientificName: scientific,
                category,
                note,
                place,
                confirmed,
                revision: record.revision + 1,
                syncState: "pending",
                updatedAt: new Date().toISOString(),
              });
              setEditing(false);
            }}
          >
            <label>
              Name
              <input
                value={name}
                maxLength={160}
                onChange={(e) => setName(e.target.value)}
              />
            </label>
            <label>
              Scientific name, if known
              <input
                value={scientific}
                maxLength={180}
                onChange={(e) => setScientific(e.target.value)}
              />
            </label>
            <label>
              Kind
              <select
                aria-label="Kind"
                value={category}
                onChange={(e) => setCategory(e.target.value as Category)}
              >
                {categories.map((x) => (
                  <option value={x} key={x}>
                    {categoryInfo[x].label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Place
              <input
                value={place}
                maxLength={160}
                onChange={(e) => setPlace(e.target.value)}
              />
            </label>
            <label>
              Your field note
              <textarea
                value={note}
                maxLength={3000}
                onChange={(e) => setNote(e.target.value)}
              />
            </label>
            <label className="checkbox">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
              />
              Confirm this identification
            </label>
            <button className="button primary" type="submit">
              <Check size={18} />
              Save changes
            </button>
          </form>
        )}
        <div className="share-panel">
          <h3>A discovery worth sharing</h3>
          <div className="button-row">
            <button
              className="button secondary"
              disabled={sharing}
              onClick={() => share(false)}
            >
              <Share2 size={17} />
              Photo only
            </button>
            <button
              className="button primary"
              disabled={sharing}
              onClick={() => share(true)}
            >
              <Share2 size={17} />
              Photo & story
            </button>
          </div>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={includeLocation}
              onChange={(e) => setIncludeLocation(e.target.checked)}
            />{" "}
            Include the place name on the story card
          </label>
          <p className="small muted">
            Exact GPS coordinates are always left off shared images.
          </p>
        </div>
        <p className="small muted">
          Visual identification is a suggestion. Never use it to decide what is
          safe to eat.
        </p>
      </div>
    </Dialog>
  );
}
function wrap(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  width: number,
  lineHeight: number,
  maxLines: number,
) {
  const words = text.split(/\s+/);
  let line = "",
    row = 0;
  for (const word of words) {
    const test = line + word + " ";
    if (ctx.measureText(test).width > width && line) {
      ctx.fillText(line.trim(), x, y + row * lineHeight);
      row++;
      line = "";
      if (row >= maxLines) return;
    }
    line += word + " ";
  }
  if (row < maxLines) ctx.fillText(line.trim(), x, y + row * lineHeight);
}
function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob),
    a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}

export default function Fieldnotes() {
  const { user, loading: authLoading, offlineSession, signOut } = useAuth();
  const owner = user?.id || "guest";
  const [view, setView] = useState<View>("discover"),
    [records, setRecords] = useState<Observation[]>([]),
    [category, setCategory] = useState<Category | "all">("all"),
    [query, setQuery] = useState(""),
    [from, setFrom] = useState(""),
    [to, setTo] = useState(""),
    [filters, setFilters] = useState(false),
    [login, setLogin] = useState(false),
    [accountOpen, setAccountOpen] = useState(false),
    [keySettings, setKeySettings] = useState(false),
    [selected, setSelected] = useState<string | null>(null),
    [draft, setDraft] = useState<Draft | null>(null),
    [busy, setBusy] = useState(false),
    [syncing, setSyncing] = useState(false),
    [online, setOnline] = useState(true),
    [notice, setNotice] = useState(""),
    [ready, setReady] = useState(false),
    [status, setStatus] = useState({
      storage: false,
      identification: false,
      plantnet: false,
      bioclip: false,
    }),
    [installEvent, setInstallEvent] = useState<any>(null);
  const cameraRef = useRef<HTMLInputElement>(null),
    galleryRef = useRef<HTMLInputElement>(null),
    syncLock = useRef(false),
    gpsRef = useRef<Promise<GeolocationPosition | null> | null>(null),
    timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const toast = useCallback((message: string) => {
    setNotice(message);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setNotice(""), 6500);
  }, []);
  const refresh = useCallback(async () => {
    try {
      setRecords(await listLocal(owner));
      setReady(true);
    } catch {
      toast(
        "Local storage is unavailable. Please allow storage in this browser before adding photos.",
      );
    }
  }, [owner, toast]);
  const sync = useCallback(async () => {
    if (syncLock.current || owner === "guest" || !navigator.onLine) return;
    syncLock.current = true;
    setSyncing(true);
    try {
      await syncRecords(owner, () => void refresh());
    } catch {
    } finally {
      syncLock.current = false;
      setSyncing(false);
    }
  }, [owner, refresh]);
  useEffect(() => {
    setReady(false);
    void refresh();
  }, [refresh]);
  useEffect(() => {
    if (authLoading) return;
    void sync();
    const update = () => {
      setOnline(navigator.onLine);
      if (navigator.onLine) void sync();
    };
    const visible = () => {
      if (!document.hidden) update();
    };
    setOnline(navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    document.addEventListener("visibilitychange", visible);
    const interval = setInterval(() => {
      if (!document.hidden) void sync();
    }, 45000);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
      document.removeEventListener("visibilitychange", visible);
      clearInterval(interval);
    };
  }, [sync, authLoading]);
  useEffect(() => {
    if (window.location.hash === "#api-key") setKeySettings(true);
    const launchView = new URLSearchParams(window.location.search).get("view");
    if (["discover", "journal", "map", "collection", "achievements", "sources"].includes(launchView || "")) setView(launchView as View);
  }, []);
  useEffect(() => {
    const url = new URL(window.location.href);
    if (view === "discover") url.searchParams.delete("view");
    else url.searchParams.set("view", view);
    window.history.replaceState(null, "", url.pathname + url.search + url.hash);
  }, [view]);
  const refreshStatus = useCallback(() => {
    fetch("/api/status", { cache: "no-store" })
      .then(
        (r) =>
          r.json() as Promise<{
            storage: boolean;
            identification: boolean;
            plantnet: boolean;
            bioclip: boolean;
          }>,
      )
      .then(setStatus)
      .catch(() => {});
  }, []);
  useEffect(() => {
    refreshStatus();
  }, [owner, online, refreshStatus]);
  useEffect(() => {
    fetch("/api/status")
      .then(
        (r) =>
          r.json() as Promise<{
            storage: boolean;
            identification: boolean;
            plantnet: boolean;
            bioclip: boolean;
          }>,
      )
      .then(setStatus)
      .catch(() => {});
    if ("serviceWorker" in navigator)
      navigator.serviceWorker.register("/sw.js").catch(() => {});
    const install = (e: Event) => {
      e.preventDefault();
      setInstallEvent(e);
    };
    window.addEventListener("beforeinstallprompt", install);
    return () => window.removeEventListener("beforeinstallprompt", install);
  }, []);
  useEffect(() => {
    const context = (document as any).modelContext;
    if (!context?.registerTool) return;
    const lifecycle = new AbortController();
    Promise.resolve(
      context.registerTool(
        {
          name: "filter_discoveries",
          description:
            "Filter the visible field journal by name, place or category. Does not create or edit discoveries.",
          inputSchema: {
            type: "object",
            properties: {
              query: { type: "string" },
              category: { type: "string", enum: ["all", ...categories] },
            },
            additionalProperties: false,
          },
          annotations: { readOnlyHint: false, untrustedContentHint: true },
          execute: async (input: any) => {
            if (
              !input ||
              typeof input !== "object" ||
              Object.keys(input).some(
                (k) => !["query", "category"].includes(k),
              ) ||
              (input.query !== undefined && typeof input.query !== "string") ||
              (input.category !== undefined &&
                !["all", ...categories].includes(input.category))
            )
              throw Error("Invalid filters");
            setQuery(input.query || "");
            setCategory(input.category || "all");
            setView("journal");
            await new Promise((resolve) =>
              requestAnimationFrame(() => requestAnimationFrame(resolve)),
            );
            return {
              view: "journal",
              query: input.query || "",
              category: input.category || "all",
            };
          },
        },
        { signal: lifecycle.signal },
      ),
    ).catch(() => {});
    return () => lifecycle.abort();
  }, []);
  const stats = useMemo(() => getStats(records), [records]),
    achievements = useMemo(() => getAchievements(records), [records]);
  const filtered = useMemo(
    () =>
      records.filter(
        (r) =>
          (category === "all" || r.category === category) &&
          (!from || r.localDate >= from) &&
          (!to || r.localDate <= to) &&
          `${r.name} ${r.scientificName} ${r.place} ${r.note} ${r.localDate}`
            .toLowerCase()
            .includes(query.toLowerCase()),
      ),
    [records, category, from, to, query],
  );
  const choose = useCallback((r: Observation) => setSelected(r.id), []),
    chosen = records.find((r) => r.id === selected);
  async function photoChosen(
    file: File | undefined,
    source: "camera" | "gallery",
  ) {
    if (!file) return;
    setBusy(true);
    try {
      const result = await preparePhoto(file),
        gps = source === "camera" ? await Promise.race([gpsRef.current, new Promise<null>(resolve => setTimeout(() => resolve(null), 1500))]) : null,
        date = result.exifDate || new Date();
      const lat = result.gps?.latitude ?? gps?.coords.latitude ?? null,
        lng = result.gps?.longitude ?? gps?.coords.longitude ?? null;
      setDraft({
        photo: result.photo,
        date: `${localDate(date)}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`,
        latitude: lat,
        longitude: lng,
        accuracy: result.gps ? null : (gps?.coords.accuracy ?? null),
        locationSource: result.gps ? "exif" : gps ? "gps" : "none",
        place: "",
        note: "",
        category: "other",
        name: "",
      });
    } catch (e) {
      toast((e as Error).message);
    } finally {
      setBusy(false);
      if (cameraRef.current) cameraRef.current.value = "";
      if (galleryRef.current) galleryRef.current.value = "";
    }
  }
  function openCamera() {
    gpsRef.current = locate().catch(() => null);
    cameraRef.current?.click();
  }
  async function save() {
    if (!draft) return;
    setBusy(true);
    try {
      const date = new Date(draft.date);
      if (isNaN(date.valueOf())) throw Error("Please enter a valid date.");
      const record: Observation = {
        id: crypto.randomUUID(),
        owner,
        capturedAt: date.toISOString(),
        localDate: localDate(date),
        localHour: date.getHours(),
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        latitude: draft.latitude,
        longitude: draft.longitude,
        accuracy: draft.accuracy,
        locationSource: draft.locationSource,
        place: draft.place,
        note: draft.note,
        category: draft.category,
        name: draft.name,
        scientificName: "",
        confirmed: !!draft.name,
        photo: draft.photo,
        identification: null,
        syncState: "pending",
        analysisState: "pending",
        updatedAt: new Date().toISOString(),
        revision: 1,
      };
      await saveLocal(record);
      void requestPersistentStorage();
      setDraft(null);
      setView("journal");
      setCategory("all");
      setQuery("");
      setFrom("");
      setTo("");
      await refresh();
      toast(
        records.length === 0
          ? "First wonder unlocked. Your discovery is saved!"
          : "Another little wonder, safely saved.",
      );
      if (user) {
        void sync();
        try {
          const reg = await navigator.serviceWorker?.ready;
          await (reg as any)?.sync?.register("fieldnotes-upload");
        } catch {}
      }
    } catch (e) {
      toast((e as Error).message || "Could not save. Please try again.");
    } finally {
      setBusy(false);
    }
  }
  async function update(r: Observation) {
    try {
      await saveLocal(r);
      await refresh();
      void sync();
      toast("Your field note has been updated.");
    } catch {
      toast("Could not save your changes. Please try again.");
    }
  }
  async function backup() {
    try {
      const out = await Promise.all(
        records.map(async (r) => ({
          ...r,
          photo: await new Promise<string>((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result as string);
            reader.onerror = reject;
            reader.readAsDataURL(r.photo);
          }),
        })),
      );
      download(
        new Blob(
          [
            JSON.stringify({
              format: "fieldnotes-backup-v1",
              exportedAt: new Date().toISOString(),
              observations: out,
            }),
          ],
          { type: "application/json" },
        ),
        `fieldlogger-${localDate(new Date())}.json`,
      );
      toast(
        "Journal backup downloaded, including photos and GPS. Keep it somewhere private.",
      );
    } catch {
      toast("The backup could not be created. Please try again.");
    }
  }
  const pending = records.filter((r) => r.syncState !== "synced").length;
  const title = {
    discover: "Discover",
    journal: "Your field journal",
    map: "Every discovery has a place",
    collection: "A world you’re getting to know",
    achievements: "Stay curious",
    sources: "A little evidence goes a long way",
  }[view];
  const subtitle = {
    discover: "",
    journal: "Small wonders. Good memories. All yours.",
    map: "Revisit the things that made you stop and look.",
    collection: "The variety of life, seen through your eyes.",
    achievements: "Little milestones for a curious mind.",
    sources: "Know where a suggestion comes from, and what it can tell you.",
  }[view];
  return (
    <div className={"app-shell view-" + view}>
      <aside className="sidebar">
        <a href="/" className="brand">
          <span className="brand-mark">
            <Leaf size={25} />
          </span>
          <span>
            Field Logger<small>A WORLD WORTH NOTICING</small>
          </span>
        </a>
        <p className="nav-label">YOUR EXPLORATIONS</p>
        <nav>
          {nav.map((item) => (
            <button
              key={item.id}
              onClick={() => setView(item.id)}
              className={view === item.id ? "nav-item active" : "nav-item"}
              aria-current={view === item.id ? "page" : undefined}
            >
              <item.icon size={20} />
              {item.label}
              {item.id === "journal" && records.length > 0 && (
                <span>{records.length}</span>
              )}
            </button>
          ))}
        </nav>
        <button
          className="button capture-button"
          onClick={openCamera}
          disabled={busy}
        >
          <Camera size={20} />
          New discovery
        </button>
        <div className="sidebar-bottom">
          <div className="curiosity">
            <Compass size={25} />
            <p>
              Take your time.
              <br />
              <strong>There’s plenty to notice.</strong>
            </p>
          </div>
          <button
            className={"nav-item " + (view === "sources" ? "active" : "")}
            onClick={() => setView("sources")}
          >
            <BookOpen size={18} />
            Sources & field guide
          </button>
          <button
            className="nav-item"
            onClick={backup}
            disabled={!records.length}
          >
            <Download size={18} />
            Export my journal
          </button>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <button onClick={() => setView("discover")} className="mobile-brand" aria-label="Discover home">
            <Leaf size={22} />
            Field Logger
          </button>
          <div className="breadcrumb">
            My little corner of the world <span>/</span>{" "}
            {nav.find((x) => x.id === view)?.label || "Field guide"}
          </div>
          <div className="topbar-actions">
            <button
              className="icon-button"
              aria-label="API key settings"
              title="API key settings"
              onClick={() => setKeySettings(true)}
            >
              <KeyRound size={20} />
            </button>
            <span className="sync-status">
              {!online ? (
                <>
                  <WifiOff size={16} />
                  Offline · saving locally
                </>
              ) : syncing ? (
                <>
                  <RefreshCw size={16} className="spin" />
                  Syncing…
                </>
              ) : pending ? (
                <>
                  <CloudUpload size={17} />
                  {pending} saved locally
                </>
              ) : (
                <>
                  <Cloud size={17} />
                  {user ? "Journal up to date" : "Device journal"}
                </>
              )}
            </span>
            <button
              className="profile-button"
              onClick={() =>
                user ? setAccountOpen(true) : setLogin(true)
              }
              title={user ? "Your account" : "Create account or sign in"}
            >
              {user ? (
                <span className="avatar">
                  {user.name.slice(0, 1).toUpperCase()}
                </span>
              ) : (
                <LogIn size={18} />
              )}
              <span>{user ? user.name.split(" ")[0] : "Sign in"}</span>

            </button>
          </div>
        </header>
        <main id="main" className="main-content">
          {view !== "discover" && <div className="page-heading">
            <div>
              <p className="eyebrow">
                {view === "journal"
                  ? new Date().toLocaleDateString("en-GB", {
                      weekday: "long",
                      day: "numeric",
                      month: "long",
                    })
                  : "FIELD LOGGER / YOUR EXPLORATIONS"}
              </p>
              <h1>{title}</h1>
              <p className="muted">{subtitle}</p>
            </div>
            <button
              className="button primary desktop-add"
              disabled={busy}
              onClick={openCamera}
            >
              <Camera size={19} />
              New discovery
            </button>
          </div>}
          {view === "discover" && <DiscoveryHome records={records} online={online} busy={busy} onCamera={openCamera} onGallery={() => galleryRef.current?.click()} onJournal={() => setView("journal")} onOpen={setSelected} onGuide={() => setView("sources")} />}
          {!online && view !== "discover" && (
            <div className="offline-banner">
              <WifiOff size={18} />
              <span>
                You’re offline. Your photos and field notes are safe here.
                Uploads resume when you reconnect
                {offlineSession ? " and your session is active" : ""}.
              </span>
            </div>
          )}
          {view === "journal" && (
            <>
              <section
                className="overview-stats"
                aria-label="Your discovery statistics"
              >
                <div>
                  <span className="stat-icon">
                    <Camera size={22} />
                  </span>
                  <p>
                    <strong>{stats.total}</strong>
                    <span>discoveries</span>
                  </p>
                </div>
                <div>
                  <span className="stat-icon ochre">
                    <Leaf size={22} />
                  </span>
                  <p>
                    <strong>{stats.species.length}</strong>
                    <span>different species</span>
                  </p>
                </div>
                <div>
                  <span className="stat-icon blue">
                    <MapPin size={22} />
                  </span>
                  <p>
                    <strong>{stats.places}</strong>
                    <span>areas explored</span>
                  </p>
                </div>
                <div className="desktop-stat">
                  <span className="stat-icon lavender">
                    <Award size={22} />
                  </span>
                  <p>
                    <strong>
                      {achievements.filter((a) => a.progress === a.goal).length}
                    </strong>
                    <span>little milestones</span>
                  </p>
                </div>
              </section>
              {!records.length && (
                <section className="welcome-card">
                  <img
                    src="/woodland.jpg"
                    alt="Bluebells beneath a green woodland canopy"
                  />
                  <div className="welcome-scrim" />
                  <div className="welcome-copy">
                    <span className="field-tag">
                      <Compass size={14} />
                      YOUR NEXT DISCOVERY IS OUT THERE
                    </span>
                    <h2>
                      Wonder starts
                      <br />
                      with a closer look.
                    </h2>
                    <p>
                      A flower in the hedge. A bird on a gate.
                      <br />
                      Make a little room for the unexpected.
                    </p>
                    <div className="button-row">
                      <button
                        className="button lime"
                        disabled={busy}
                        onClick={openCamera}
                      >
                        <Camera size={19} />
                        Take your first photo
                      </button>
                      <button
                        className="button glass"
                        disabled={busy}
                        onClick={() => galleryRef.current?.click()}
                      >
                        <ImagePlus size={18} />
                        Choose a photo
                      </button>
                    </div>
                  </div>
                  <a
                    className="photo-credit"
                    href="https://unsplash.com/photos/QF2kkrpmx34"
                    target="_blank"
                    rel="noreferrer"
                  >
                    Photo: Rob Wingate / Unsplash
                  </a>
                </section>
              )}
              {records.length > 0 && (
                <div className="journal-actions">
                  <p>
                    <span className="section-label">YOUR DISCOVERIES</span> A
                    collection of reasons to pause.
                  </p>
                  <button
                    className="text-button"
                    onClick={() => galleryRef.current?.click()}
                  >
                    <ImagePlus size={17} />
                    Add from gallery
                  </button>
                </div>
              )}
            </>
          )}
          {(view === "journal" || view === "map") && (
            <>
              <div className="filter-bar">
                <div className="search-box">
                  <Search size={18} />
                  <input
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    aria-label="Search discoveries"
                    placeholder="Find a discovery, species or place…"
                  />
                  {query && (
                    <button
                      className="icon-button"
                      aria-label="Clear search"
                      onClick={() => setQuery("")}
                    >
                      <X size={16} />
                    </button>
                  )}
                </div>
                <button
                  className={
                    "button filter-toggle " +
                    (filters || from || to ? "selected" : "")
                  }
                  onClick={() => setFilters(!filters)}
                >
                  <SlidersHorizontal size={17} />
                  Dates
                </button>
                <button
                  className="icon-button"
                  aria-label="Choose a photo from gallery"
                  onClick={() => galleryRef.current?.click()}
                >
                  <ImagePlus size={21} />
                </button>
              </div>
              {filters && (
                <div className="date-filters">
                  <label>
                    From
                    <input
                      type="date"
                      value={from}
                      onChange={(e) => setFrom(e.target.value)}
                    />
                  </label>
                  <label>
                    To
                    <input
                      type="date"
                      value={to}
                      min={from}
                      onChange={(e) => setTo(e.target.value)}
                    />
                  </label>
                  <button
                    className="text-button"
                    onClick={() => {
                      setFrom("");
                      setTo("");
                    }}
                  >
                    Clear dates
                  </button>
                </div>
              )}
              <div className="category-filters" aria-label="Filter by kind">
                <button
                  className={category === "all" ? "chip active" : "chip"}
                  onClick={() => setCategory("all")}
                >
                  All discoveries <span>{records.length}</span>
                </button>
                {categories
                  .filter((x) => x !== "other" || stats.counts.other > 0)
                  .map((x) => (
                    <button
                      key={x}
                      className={category === x ? "chip active" : "chip"}
                      onClick={() => setCategory(x)}
                    >
                      <span
                        className="category-dot"
                        style={{ background: categoryInfo[x].color }}
                      />
                      {categoryInfo[x].label}
                      {stats.counts[x] > 0 && <span>{stats.counts[x]}</span>}
                    </button>
                  ))}
              </div>
            </>
          )}
          {view === "journal" &&
            (filtered.length ? (
              <div className="discovery-grid">
                {filtered.map((r) => (
                  <DiscoveryCard
                    key={r.id}
                    record={r}
                    onClick={() => setSelected(r.id)}
                  />
                ))}
              </div>
            ) : records.length ? (
              <div className="empty-state">
                <Search size={32} />
                <h2>No discoveries in this little corner yet</h2>
                <p>Try a different name, place, kind or date.</p>
                <button
                  className="text-button"
                  onClick={() => {
                    setQuery("");
                    setCategory("all");
                    setFrom("");
                    setTo("");
                  }}
                >
                  Clear filters
                </button>
              </div>
            ) : (
              <section className="first-prompts">
                <div className="section-heading">
                  <h2>A few things to look for</h2>
                  <span className="small muted">Follow your curiosity</span>
                </div>
                <div className="prompt-grid">
                  <button onClick={openCamera}>
                    <Flower2 size={25} />
                    <div>
                      <h3>Something in bloom</h3>
                      <p>Look at the edges of a path.</p>
                    </div>
                    <ChevronRight size={18} />
                  </button>
                  <button onClick={openCamera}>
                    <Bird size={25} />
                    <div>
                      <h3>A familiar visitor</h3>
                      <p>Who’s watching from the hedgerow?</p>
                    </div>
                    <ChevronRight size={18} />
                  </button>
                  <button onClick={openCamera}>
                    <Bug size={25} />
                    <div>
                      <h3>A tiny world</h3>
                      <p>Pause. Let the small things appear.</p>
                    </div>
                    <ChevronRight size={18} />
                  </button>
                </div>
              </section>
            ))}
          {view === "map" && (
            <>
              <div className="map-panel">
                <MapView records={filtered} onSelect={choose} />
                {!filtered.some((r) =>
                  validCoords(r.latitude, r.longitude),
                ) && (
                  <div className="map-empty">
                    <MapPin size={26} />
                    <h3>Your discoveries will find their place here</h3>
                    <p>Save a photo with GPS to drop your first dot.</p>
                  </div>
                )}
                <div className="map-count">
                  {
                    filtered.filter((r) => validCoords(r.latitude, r.longitude))
                      .length
                  }{" "}
                  discoveries on the map
                </div>
              </div>
              <p className="small muted map-help">
                Tap a coloured dot to open a discovery.{" "}
                {
                  filtered.filter((r) => !validCoords(r.latitude, r.longitude))
                    .length
                }{" "}
                without GPS. Map tiles need a connection; your saved photos and
                locations are available offline.
              </p>
              {filtered.length > 0 && (
                <div className="map-accessible-list">
                  <h2>Discoveries in this view</h2>
                  {filtered.map((r) => (
                    <button key={r.id} onClick={() => choose(r)}>
                      <span
                        className="category-dot"
                        style={{ background: categoryInfo[r.category].color }}
                      />
                      {r.name || "A little mystery"}
                      <span>{r.place || "Place not named"}</span>
                      <ChevronRight size={17} />
                    </button>
                  ))}
                </div>
              )}
            </>
          )}
          {view === "collection" && (
            <>
              <div className="collection-intro">
                <div>
                  <span className="huge-number">{stats.species.length}</span>
                  <p>
                    different species
                    <br />
                    <span className="muted">
                      and counting, at your own pace.
                    </span>
                  </p>
                </div>
                <div className="collection-mini">
                  <strong>{stats.total}</strong> discoveries <span>·</span>{" "}
                  <strong>{stats.days}</strong> days of noticing
                </div>
              </div>
              <div className="section-heading">
                <h2>The things that catch your eye</h2>
                <span className="small muted">Every kind counts</span>
              </div>
              <div className="category-stats">
                {categories.map((c) => (
                  <button
                    key={c}
                    onClick={() => {
                      setCategory(c);
                      setView("journal");
                    }}
                  >
                    <span
                      style={{
                        color: categoryInfo[c].color,
                        background: categoryInfo[c].color + "15",
                      }}
                    >
                      <CategoryIcon category={c} size={25} />
                    </span>
                    <strong>{stats.counts[c]}</strong>
                    <p>{categoryInfo[c].label}</p>
                  </button>
                ))}
              </div>
              <section className="species-section">
                <div className="section-heading">
                  <h2>Your species collection</h2>
                  <span className="small muted">Named discoveries</span>
                </div>
                {stats.species.length ? (
                  <div className="species-list">
                    {stats.species.map((s) => (
                      <button
                        key={s.scientificName}
                        onClick={() => {
                          setQuery(s.scientificName);
                          setCategory("all");
                          setView("journal");
                        }}
                      >
                        <span
                          className="species-icon"
                          style={{ color: categoryInfo[s.category].color }}
                        >
                          <CategoryIcon category={s.category} />
                        </span>
                        <span>
                          <strong>{s.name}</strong>
                          <em>{s.scientificName}</em>
                        </span>
                        <small>
                          {s.count} sighting{s.count !== 1 ? "s" : ""}
                        </small>
                        <ChevronRight size={18} />
                      </button>
                    ))}
                  </div>
                ) : (
                  <div className="empty-state compact">
                    <Leaf size={32} />
                    <h3>Your life list starts with a little curiosity.</h3>
                    <p>
                      Scientific names from clear suggestions or your own
                      corrections appear here. Low-confidence suggestions stay
                      out until you confirm them.
                    </p>
                  </div>
                )}
              </section>
              <section className="season-chart">
                <h2>A year of noticing</h2>
                <div className="month-bars">
                  {Array.from({ length: 12 }, (_, i) => {
                    const count = records.filter(
                      (r) => Number(r.localDate.slice(5, 7)) === i + 1,
                    ).length;
                    const max = Math.max(
                      1,
                      ...Array.from(
                        { length: 12 },
                        (_, m) =>
                          records.filter(
                            (r) => Number(r.localDate.slice(5, 7)) === m + 1,
                          ).length,
                      ),
                    );
                    return (
                      <div key={i}>
                        <span className="bar-value">{count || ""}</span>
                        <div className="bar-track">
                          <i
                            style={{
                              height: `${count ? Math.max(5, (count / max) * 100) : 2}%`,
                            }}
                          />
                        </div>
                        <small>
                          {new Date(2024, i, 1).toLocaleString("en-GB", {
                            month: "short",
                          })}
                        </small>
                      </div>
                    );
                  })}
                </div>
                <p className="small muted">
                  Discoveries by capture month, across all years. Areas explored
                  groups GPS points into roughly 1 km cells.
                </p>
              </section>
            </>
          )}
          {view === "achievements" && (
            <>
              <div className="achievement-banner">
                <span>
                  <Award size={44} />
                </span>
                <div>
                  <p className="section-label">THE JOY IS IN THE NOTICING</p>
                  <h2>
                    {achievements.filter((a) => a.progress === a.goal).length}{" "}
                    little milestones. A world still to see.
                  </h2>
                  <p>
                    Collect new kinds of things, visit new corners, and see
                    familiar places at different times.
                  </p>
                </div>
              </div>
              <div className="achievement-grid">
                {achievements.map((a, i) => {
                  const Icon = [
                      Sparkles,
                      Compass,
                      Leaf,
                      Bug,
                      MapIcon,
                      Sun,
                      Bird,
                      Award,
                    ][i],
                    unlocked = a.progress === a.goal;
                  return (
                    <article
                      key={a.name}
                      className={"achievement " + (unlocked ? "unlocked" : "")}
                    >
                      <div className="badge-icon">
                        <Icon size={35} />
                        {unlocked && (
                          <span>
                            <Check size={12} />
                          </span>
                        )}
                      </div>
                      <span className="badge-state">
                        {unlocked ? "DISCOVERED" : "STILL TO DISCOVER"}
                      </span>
                      <h2>{a.name}</h2>
                      <p>{a.description}</p>
                      <div
                        className="progress-track"
                        role="progressbar"
                        aria-valuenow={a.progress}
                        aria-valuemin={0}
                        aria-valuemax={a.goal}
                        aria-label={a.name}
                      >
                        <i
                          style={{ width: `${(a.progress / a.goal) * 100}%` }}
                        />
                      </div>
                      <small>
                        {a.progress} of {a.goal}
                      </small>
                    </article>
                  );
                })}
              </div>
            </>
          )}
          {view === "sources" && (
            <div className="guide">
              <section className="guide-intro">
                <Compass size={34} />
                <div>
                  <h2>A companion to your curiosity</h2>
                  <p>
                    A photograph can suggest an identity. Reference sources help
                    explain it. Your own observations help settle it. Field Logger
                    keeps all three visible.
                  </p>
                </div>
              </section>
              {[
                {
                  name: "OpenAI image analysis",
                  role: "Looks at your photo",
                  text: "Suggests an identity, visible clues and alternatives, using capture time and approximate location. Confidence is qualitative. Your photo and nearby coordinates are sent only when you are signed in and connected.",
                  url: "https://platform.openai.com/docs/guides/images-vision",
                  state: status.identification
                    ? "Key saved · test in API key settings"
                    : "Add a key in API key settings",
                },
                {
                  name: "Wikipedia",
                  role: "Gives the story some context",
                  text: "Retrieves an article extract for the suggested name, with its source link, retrieval date and CC BY-SA attribution. An article match does not prove the photo identification.",
                  url: "https://www.mediawiki.org/wiki/API:Action_API",
                  state: "Public reference API",
                },
                {
                  name: "GBIF",
                  role: "Checks scientific names",
                  text: "Matches exact scientific names to the Global Biodiversity Information Facility taxonomy. Taxonomic validation is kept separate from visual identification.",
                  url: "https://techdocs.gbif.org/en/openapi/",
                  state: "Public reference API",
                },
                {
                  name: "Pl@ntNet",
                  role: "A specialist second look at plants",
                  text: "An optional independent plant-identification service. Returns ranked suggestions for flowers, leaves, bark and fruit. Requires its own API key; it is not bundled as an open-source model.",
                  url: "https://my.plantnet.org/doc/api/identify",
                  state: status.plantnet
                    ? "Connected"
                    : "Optional · not connected",
                },
                {
                  name: "BioCLIP 2",
                  role: "Open-source wildlife identification",
                  text: "An open model covering organisms across the tree of life, including plants, insects, birds and animals. Requires a separately hosted model endpoint; the mobile browser does not download its large model weights.",
                  url: "https://github.com/Imageomics/bioclip-2",
                  state: status.bioclip
                    ? "Connected"
                    : "Optional · not connected",
                },
                {
                  name: "iNaturalist",
                  role: "Community and biodiversity reference",
                  text: "Useful for comparing community observations. Its full classification model remains private; the public API and open-source server are not a free unlimited identification service. Field Logger does not publish your observations there.",
                  url: "https://github.com/inaturalist/inatVisionAPI",
                  state: "Research reference",
                },
                {
                  name: "OpenStreetMap",
                  role: "Puts discoveries in their place",
                  text: "Provides the map underneath your colour-coded dots. Standard map tiles need a connection and are not downloaded in bulk for offline use.",
                  url: "https://www.openstreetmap.org/copyright",
                  state: "© OpenStreetMap contributors",
                },
              ].map((s, i) => (
                <article className="source-card" key={s.name}>
                  <span className="source-number">0{i + 1}</span>
                  <div>
                    <div className="source-title">
                      <h2>{s.name}</h2>
                      <span className="source-state">{s.state}</span>
                    </div>
                    <strong className="source-role">{s.role}</strong>
                    <p>{s.text}</p>
                    <a href={s.url} target="_blank" rel="noreferrer">
                      Read the source <ChevronRight size={15} />
                    </a>
                  </div>
                </article>
              ))}
              <section className="guide-offline">
                <h2>Out of signal? Keep exploring.</h2>
                <p>
                  After your first online visit, the app can reopen offline.
                  Photos, notes, capture times and saved locations stay on this
                  device. Uploads resume while the app is open; Android Chrome
                  can also attempt background uploads when supported. Closing or
                  force-stopping the browser may delay sync.
                </p>
                <p>
                  Browser storage can be cleared or evicted. Keep a journal
                  backup, and check the cloud icon before removing photos or
                  clearing browser data.
                </p>
                <div className="button-row">
                  <button
                    className="button secondary"
                    onClick={backup}
                    disabled={!records.length}
                  >
                    <Download size={17} />
                    Export my journal
                  </button>
                  {installEvent && (
                    <button
                      className="button primary"
                      onClick={async () => {
                        await installEvent.prompt();
                        setInstallEvent(null);
                      }}
                    >
                      <Plus size={17} />
                      Install on this phone
                    </button>
                  )}
                </div>
                <p className="small muted">
                  On Android, you can also choose “Install app” or “Add to home
                  screen” from your browser menu.
                </p>
              </section>
            </div>
          )}
          {view === "sources" && <>
              <section className="guide-offline photo-credits"><h2>Photo credits</h2><p>The opening photographs are inspiration, separate from your own journal.</p><p><a href="https://unsplash.com/photos/QF2kkrpmx34" target="_blank" rel="noreferrer">Woodland · Rob Wingate</a></p><p><a href="https://unsplash.com/photos/7ToCy-Li1Q4" target="_blank" rel="noreferrer">Robin · Richard Bell</a></p><p><a href="https://unsplash.com/photos/BNR4sS2LA10" target="_blank" rel="noreferrer">Red fox · Charles Jackson</a></p><p>Photographs used under the <a href="https://unsplash.com/license" target="_blank" rel="noreferrer">Unsplash License</a>.</p></section>
          </>}
          {view === "journal" && !user && (
            <div className="account-nudge">
              <span className="nudge-icon">
                <CloudUpload size={24} />
              </span>
              <div>
                <strong>A safe home for your little wonders</strong>
                <p>
                  Keep exploring locally. Create an account when you’re ready to
                  back up and identify your discoveries.
                </p>
              </div>
              <button
                className="button secondary"
                onClick={() => setLogin(true)}
              >
                Create account
              </button>
            </div>
          )}
          {view !== "discover" && <footer className="app-footer">
            <span>
              <Leaf size={14} /> Made for wandering minds.
            </span>
            <button onClick={() => setKeySettings(true)}>
              API key settings <KeyRound size={14} />
            </button>
            <button onClick={() => setView("sources")}>
              Sources & offline help <CircleHelp size={14} />
            </button>
          </footer>}
        </main>
      </div>
      <nav className="mobile-nav" aria-label="Main navigation">
        <button
          className={view === "journal" ? "active" : ""}
          onClick={() => setView("journal")}
        >
          <BookOpen size={22} />
          <span>Journal</span>
        </button>
        <button
          className={view === "map" ? "active" : ""}
          onClick={() => setView("map")}
        >
          <MapIcon size={22} />
          <span>Map</span>
        </button>
        <button
          className="mobile-capture"
          aria-label="Take a discovery photo"
          onClick={openCamera}
          disabled={busy}
        >
          <span>
            <Camera size={25} />
          </span>
          <small>Discover</small>
        </button>
        <button
          className={view === "collection" ? "active" : ""}
          onClick={() => setView("collection")}
        >
          <Leaf size={22} />
          <span>Collection</span>
        </button>
        <button
          className={view === "achievements" ? "active" : ""}
          onClick={() => setView("achievements")}
        >
          <Award size={22} />
          <span>Milestones</span>
        </button>
      </nav>
      <input
        ref={cameraRef}
        type="file"
        accept="image/*"
        capture="environment"
        className="visually-hidden"
        onChange={(e) => photoChosen(e.target.files?.[0], "camera")}
      />
      <input
        ref={galleryRef}
        type="file"
        accept="image/*"
        className="visually-hidden"
        onChange={(e) => photoChosen(e.target.files?.[0], "gallery")}
      />
      {accountOpen && user && <Dialog title="Your journal" onClose={() => setAccountOpen(false)}>
        <h3>{user.name}</h3><p className="muted">{user.email}</p>
        <div className="account-actions form-stack">
          <button className="button primary full" onClick={() => { setAccountOpen(false); setKeySettings(true); }}><Sparkles size={22} />Photo identification settings</button>
          <button className="button secondary full" onClick={backup} disabled={!records.length}><Download size={22} />Export my journal</button>
          <a className="button secondary full" href="/privacy">Privacy & your data</a>
          <a className="button secondary full" href="/delete-account">Delete account</a>
          <button className="button secondary full" onClick={() => void signOut().then(() => setAccountOpen(false)).catch(e => toast(e.message))}><LogOut size={22} />Sign out</button>
        </div>
      </Dialog>}
      {login && <Login onClose={() => setLogin(false)} />}{" "}
      {keySettings && !login && (
        <Dialog title="API key settings" onClose={() => setKeySettings(false)}>
          {user ? (
            <ApiKeySettings
              online={online}
              onChanged={() => {
                refreshStatus();
                void sync();
              }}
            />
          ) : (
            <>
              <p className="muted">
                Sign in or create your private journal account before saving an
                API key.
              </p>
              <button
                className="button primary full"
                onClick={() => setLogin(true)}
              >
                Sign in or create account
              </button>
            </>
          )}
        </Dialog>
      )}
      {draft && (
        <Capture
          draft={draft}
          setDraft={setDraft}
          onSave={save}
          onClose={() => setDraft(null)}
          busy={busy}
        />
      )}{" "}
      {chosen && (
        <Detail
          record={chosen}
          onClose={() => setSelected(null)}
          onUpdate={update}
          toast={toast}
        />
      )}{" "}
      {notice && (
        <div className="toast" role="status">
          <Check size={18} />
          {notice}
          <button aria-label="Dismiss" onClick={() => setNotice("")}>
            <X size={15} />
          </button>
        </div>
      )}
      {busy && !draft && (
        <div className="processing" role="status">
          <LoaderCircle className="spin" />
          Getting your photo ready…
        </div>
      )}
      {!ready && authLoading && (
        <span className="visually-hidden" role="status">
          Opening your journal…
        </span>
      )}
    </div>
  );
}
